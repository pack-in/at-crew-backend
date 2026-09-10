// 서버(media 모듈 R2StorageAdapter.triggerWorker)가 이 Worker를 호출하는 트리거 payload와
// 서버(MediaInternalController)가 기대하는 콜백 payload는 docs/design/media-module-design.md §6~7 참고.

const THUMB_WIDTH = 294;
const THUMB_HEIGHT = 392; // 3:4 비율 (294 * 4 / 3)
const ADULT_BLUR = 20;
const AVIF_QUALITY = 80; // 썸네일 품질 — 카드 화질은 플랜 차등 대상이 아니라 등급과 무관하게 고정이다

// 변환 화질 등급별 원본 파라미터 (요금제-R03·R04, 서버의 MediaQualityTier와 값이 일치해야 한다).
// 상한은 "긴 변"이 아니라 가로 폭 기준이다 — 웹툰 원고는 세로로 길어서 긴 변으로 제한하면 원고가 뭉개진다.
// fit: "scale-down"이라 상한보다 작은 원본은 확대하지 않고 그대로 둔다.
//
// ORIGINAL(프로)은 maxWidth가 없다 — 변환 결과가 raw 원본을 대체하고 원본은 변환 성공 후 삭제되므로,
// 여기서 축소하면 그 해상도를 영영 되돌릴 수 없다. 요금제-R04의 "선명한 원본 화질"과도 이제 실제로 맞는다.
// quality 100은 AVIF에서 비트 단위 무손실을 보장하지는 않지만(Cloudflare가 무손실을 명시한 건 WebP뿐),
// 실측상 6MB PNG 원고가 약 330KB로 원본의 5% 수준이라 화질과 용량이 모두 납득할 범위다.
const QUALITY_TIERS = {
  WEB: { maxWidth: 1280, quality: 72 },
  ORIGINAL: { maxWidth: null, quality: 100 },
};
const DEFAULT_TIER = "ORIGINAL";

// 업로드 원본 용량 상한 20MB — Cloudflare Images 바인딩이 받는 입력 한계다(우리가 고른 정책값이 아니다).
// 기획(업로드-R04)은 플랜 무관 "용량 제한 없음"이므로 이 상한은 기술 제약을 그대로 옮긴 것이고,
// 넘는 파일은 클라이언트가 업로드 전에 줄여야 한다.
//
// Presigned PUT은 크기를 강제할 수 없어(서명에 Content-Length 조건이 없다) 변환 직전 R2 객체 크기로
// 검사하고, 초과분은 FAILED 콜백으로 돌려보낸다. 서버도 presign 발급 시 같은 값으로 미리 거른다 —
// MediaConstraints.MAX_ORIGINAL_BYTES와 반드시 같아야 한다. 여기는 클라이언트가 신고한 크기를 믿지 않는
// 최종 방어선이라, 서버 검증이 있어도 남겨둔다.
const MAX_ORIGINAL_BYTES = 20 * 1024 * 1024;

export default {
  async fetch(request, env, ctx) {
    if (request.method !== "POST") {
      return new Response("Method Not Allowed", { status: 405 });
    }

    const callbackSecret = request.headers.get("X-Callback-Secret");
    if (!callbackSecret || callbackSecret !== env.CALLBACK_SECRET) {
      return new Response("Unauthorized", { status: 401 });
    }

    let payload;
    try {
      payload = await request.json();
    } catch {
      return new Response("Invalid JSON", { status: 400 });
    }

    const { ownerType, ownerId, imageKeys, variantProfile, qualityTier } = payload;
    if (!ownerType || !ownerId || !Array.isArray(imageKeys) || imageKeys.length === 0) {
      return new Response("Invalid payload", { status: 400 });
    }

    // 서버는 이 응답을 기다리지 않는다(@Async 트리거) — 실제 변환은 백그라운드에서 진행하고 즉시 202를 반환한다.
    ctx.waitUntil(processAll(env, ownerType, ownerId, imageKeys, variantProfile, qualityTier));
    return new Response(null, { status: 202 });
  },
};

async function processAll(env, ownerType, ownerId, imageKeys, variantProfile, qualityTier) {
  await Promise.all(imageKeys.map((key) => processOne(env, ownerType, ownerId, key, variantProfile, qualityTier)));
}

async function processOne(env, ownerType, ownerId, imageKey, variantProfile, qualityTier) {
  const baseName = imageKey.split("/").pop().replace(/\.[^/.]+$/, "");
  const originalAvifKey = `original/${baseName}.avif`;
  const thumbKey = `thumb/${baseName}.avif`;
  const thumbAdultKey = variantProfile === "STANDARD_WITH_ADULT_BLUR" ? `thumb-adult/${baseName}.avif` : null;

  try {
    const object = await env.MEDIA_BUCKET.get(imageKey);
    if (!object) throw new Error(`R2에서 원본을 찾을 수 없음: ${imageKey}`);
    if (object.size > MAX_ORIGINAL_BYTES) {
      throw new Error(`원본 용량 상한 초과: ${imageKey} ${object.size}바이트 > ${MAX_ORIGINAL_BYTES}바이트`);
    }
    const bytes = await object.arrayBuffer();

    const [originalRes, thumbRes, thumbAdultRes] = await Promise.all([
      encodeOriginal(env, bytes, qualityTier),
      encodeThumb(env, bytes, { blur: false }),
      thumbAdultKey ? encodeThumb(env, bytes, { blur: true }) : Promise.resolve(null),
    ]);

    await Promise.all([
      env.MEDIA_BUCKET.put(originalAvifKey, originalRes.body, { httpMetadata: { contentType: "image/avif" } }),
      env.MEDIA_BUCKET.put(thumbKey, thumbRes.body, { httpMetadata: { contentType: "image/avif" } }),
      thumbAdultRes
        ? env.MEDIA_BUCKET.put(thumbAdultKey, thumbAdultRes.body, { httpMetadata: { contentType: "image/avif" } })
        : Promise.resolve(),
    ]);

    const delivered = await callback(env, {
      ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, status: "DONE",
    });

    // 원본은 변환 결과가 대체하므로 여기서 지운다 — 저장량의 대부분이 raw다(실측 99.5%).
    //
    // 콜백이 서버에 닿은 뒤에만 지운다. 콜백이 유실되면 서버는 계속 PENDING으로 보고 ImageRetryScheduler가
    // 재시도를 거는데, 그때 원본이 없으면 "R2에서 원본을 찾을 수 없음"으로 영구 FAILED가 된다.
    // 삭제 자체가 실패해도 데이터는 온전하다 — 고아로 남을 뿐이라 콜백 결과를 되돌리지 않는다.
    if (delivered) {
      try {
        await env.MEDIA_BUCKET.delete(imageKey);
      } catch (err) {
        console.error(`원본 삭제 실패(고아로 남음): imageKey=${imageKey} ${err}`);
      }
    } else {
      console.error(`콜백 미도달 — 재시도를 위해 원본을 남긴다: imageKey=${imageKey}`);
    }
  } catch (err) {
    console.error(`이미지 처리 실패: ownerType=${ownerType} ownerId=${ownerId} imageKey=${imageKey} ${err}`);
    await callback(env, {
      ownerType,
      ownerId,
      imageKey,
      thumbKey: null,
      thumbAdultKey: null,
      originalAvifKey: null,
      status: "FAILED",
    });
  }
}

// Images 바인딩의 input()은 ReadableStream을 받는다. ArrayBuffer를 그대로 넘기면 output() 시점에
// 소스 직렬화가 텍스트 경로로 잘못 들어가 "Cannot read properties of undefined (reading 'font')"로 죽는다
// (2026-08-28 실측 — 변환 옵션과 무관하게 전부 실패, 스트림으로 바꾸면 전부 성공).
// 스트림은 한 번만 읽히므로 변환마다 새로 만든다 — 그래야 세 변환을 병렬로 돌릴 수 있다.
function toStream(bytes) {
  return new Response(bytes).body;
}

async function encodeOriginal(env, bytes, qualityTier) {
  const tier = QUALITY_TIERS[qualityTier] ?? QUALITY_TIERS[DEFAULT_TIER];
  // maxWidth가 없는 등급은 리사이즈를 아예 걸지 않는다 — width: null을 넘기면 변환이 거부된다.
  let pipeline = env.IMAGES.input(toStream(bytes));
  if (tier.maxWidth) {
    pipeline = pipeline.transform({ width: tier.maxWidth, fit: "scale-down" });
  }
  const result = await pipeline.output({ format: "image/avif", quality: tier.quality });
  return result.response();
}

async function encodeThumb(env, bytes, { blur }) {
  let pipeline = env.IMAGES.input(toStream(bytes)).transform({
    width: THUMB_WIDTH,
    height: THUMB_HEIGHT,
    fit: "cover",
    gravity: "auto",
  });
  if (blur) pipeline = pipeline.transform({ blur: ADULT_BLUR });
  const result = await pipeline.output({ format: "image/avif", quality: AVIF_QUALITY });
  return result.response();
}

// 서버가 실제로 받았는지를 돌려준다 — 원본 삭제 여부가 이 결과에 달려 있다.
// 네트워크 오류로 던지지 않고 false를 돌려준다: 콜백 실패가 변환 성공까지 되돌릴 이유는 없고,
// 실패로 처리하면 이미 만들어 둔 AVIF가 버려진 채 FAILED 콜백이 한 번 더 나간다.
async function callback(env, body) {
  try {
    const res = await fetch(env.SERVER_CALLBACK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Internal-Secret": env.INTERNAL_SECRET },
      body: JSON.stringify(body),
    });
    if (!res.ok) console.error(`콜백 응답 오류: status=${res.status} imageKey=${body.imageKey}`);
    return res.ok;
  } catch (err) {
    console.error(`콜백 전송 실패: imageKey=${body.imageKey} ${err}`);
    return false;
  }
}
