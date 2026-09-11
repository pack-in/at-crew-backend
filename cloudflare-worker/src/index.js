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
//
// quality 95인 이유: Cloudflare는 100에서만 무손실 모드로 넘어가고 거기서 크기가 급격히 뛴다.
// 6MB PNG 원고(2480x3508) 실측 — q90 481KB(7.9%) / q95 788KB(13.0%) / q99 1,068KB(17.6%) /
// q100 3,923KB(64.6%). 100은 99의 3.7배다. 원본을 지우는 목적이 저장량 절감인데 100을 쓰면 그 효과가
// 대부분 사라지고, 우리는 작품 원본 보관 서비스가 아니라 비트 단위 동일까지 지킬 이유가 없다.
//
// 위 수치는 축소 없이 변환한 값이라 실제 출력은 AVIF가 아니라 WebP다(아래 putVariant 주석 참고).
// 등급을 고르는 기준으로는 그대로 유효하다 — 어느 포맷으로 떨어지든 q100만 무손실로 튄다.
const QUALITY_TIERS = {
  WEB: { maxWidth: 1280, quality: 72 },
  ORIGINAL: { maxWidth: null, quality: 95 },
};
const DEFAULT_TIER = "ORIGINAL";

// 업로드 원본 용량 상한 100MB — Cloudflare Images의 원격 변환 입력 한계다(우리가 고른 정책값이 아니다).
// 기획(업로드-R04)은 플랜 무관 "용량 제한 없음"이므로 이 상한은 기술 제약을 그대로 옮긴 것이다.
//
// 예전에는 20MB였다. R2 바인딩으로 원본 바이트를 읽어 env.IMAGES.input()에 넘겼는데 그 경로의 입력
// 한계가 20MB이기 때문이다(Workers 메모리가 128MB라 원본과 변환 결과를 모두 얹을 수 없다). 라이트
// 실데이터 14,759건을 조사하니 20MB 초과가 426건(2.89%)이라 무시할 수 없어, 바이트를 메모리로
// 가져오지 않는 fetch(cf.image) 경로로 바꿨다. 남은 100MB 초과는 라이트 전체에서 2건(0.01%)이고
// Images 자체의 한계라 어떤 방식으로도 처리할 수 없다.
//
// Presigned PUT은 크기를 강제할 수 없어(서명에 Content-Length 조건이 없다) 변환 직전 R2 객체 크기로
// 검사하고, 초과분은 FAILED 콜백으로 돌려보낸다. 서버도 presign 발급 시 같은 값으로 미리 거른다 —
// MediaConstraints.MAX_ORIGINAL_BYTES와 반드시 같아야 한다. 여기는 클라이언트가 신고한 크기를 믿지 않는
// 최종 방어선이라, 서버 검증이 있어도 남겨둔다.
const MAX_ORIGINAL_BYTES = 100 * 1024 * 1024;

// 실패 사유 문자열 상한. 서버가 로그 한 줄로 남기므로 스택까지 실어 보낼 이유가 없다.
const FAILURE_REASON_MAX = 300;

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

    const { ownerType, ownerId, imageKeys, sourceUrls, variantProfile, qualityTier } = payload;
    if (!ownerType || !ownerId || !Array.isArray(imageKeys) || imageKeys.length === 0) {
      return new Response("Invalid payload", { status: 400 });
    }
    // sourceUrls는 imageKeys와 같은 순서의 읽기용 서명 URL이다(R2StorageAdapter.triggerWorker).
    if (!Array.isArray(sourceUrls) || sourceUrls.length !== imageKeys.length) {
      return new Response("sourceUrls must match imageKeys", { status: 400 });
    }

    // 서버는 이 응답을 기다리지 않는다(@Async 트리거) — 실제 변환은 백그라운드에서 진행하고 즉시 202를 반환한다.
    ctx.waitUntil(processAll(env, ownerType, ownerId, imageKeys, sourceUrls, variantProfile, qualityTier));
    return new Response(null, { status: 202 });
  },
};

async function processAll(env, ownerType, ownerId, imageKeys, sourceUrls, variantProfile, qualityTier) {
  await Promise.all(imageKeys.map((key, i) =>
    processOne(env, ownerType, ownerId, key, sourceUrls[i], variantProfile, qualityTier)));
}

async function processOne(env, ownerType, ownerId, imageKey, sourceUrl, variantProfile, qualityTier) {
  const baseName = imageKey.split("/").pop().replace(/\.[^/.]+$/, "");
  const originalAvifKey = `original/${baseName}.avif`;
  const thumbKey = `thumb/${baseName}.avif`;
  const thumbAdultKey = variantProfile === "STANDARD_WITH_ADULT_BLUR" ? `thumb-adult/${baseName}.avif` : null;

  try {
    // 크기 검사는 여전히 R2 메타데이터로 한다 — head 한 번이면 되고 바이트를 읽지 않는다.
    const head = await env.MEDIA_BUCKET.head(imageKey);
    if (!head) throw new Error(`R2에서 원본을 찾을 수 없음: ${imageKey}`);
    if (head.size > MAX_ORIGINAL_BYTES) {
      throw new Error(`원본 용량 상한 초과: ${imageKey} ${head.size}바이트 > ${MAX_ORIGINAL_BYTES}바이트`);
    }

    // 변환 셋을 병렬로 돌린다. 각 fetch가 원본을 따로 가져가지만 Cloudflare 내부 경로라 저렴하고,
    // 무엇보다 원본 바이트가 Worker 메모리를 거치지 않아 100MB짜리도 다룰 수 있다.
    const [originalRes, thumbRes, thumbAdultRes] = await Promise.all([
      transform(sourceUrl, originalOptions(qualityTier)),
      transform(sourceUrl, thumbOptions({ blur: false })),
      thumbAdultKey ? transform(sourceUrl, thumbOptions({ blur: true })) : Promise.resolve(null),
    ]);

    // content-type은 하드코딩하지 않고 변환 결과가 실제로 무엇인지 그대로 따른다.
    // Cloudflare는 format을 "가능하면" 지킨다 — 면적이 큰 이미지는 AVIF 인코딩이 느려 WebP로 폴백한다
    // (2026-09-10 실측: 2480x3508 원본을 축소 없이 요청하면 WebP, 1280px로 줄이면 AVIF).
    // 여기서 image/avif로 못 박으면 실제 WebP 바이트에 avif 헤더가 붙어 브라우저가 디코드에 실패한다.
    await Promise.all([
      putVariant(env, originalAvifKey, originalRes),
      putVariant(env, thumbKey, thumbRes),
      thumbAdultRes ? putVariant(env, thumbAdultKey, thumbAdultRes) : Promise.resolve(),
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
      // 서버 로그에 남길 실패 사유. Worker 로그는 tail 중이 아니면 흘러가 버려서, 나중에 "왜 실패했나"를
      // 되짚을 수 있는 곳이 서버 로그뿐이다(용량 초과·면적 초과·원본 없음 등이 여기서 갈린다).
      failureReason: String(err && err.message ? err.message : err).slice(0, FAILURE_REASON_MAX),
    });
  }
}

// 원본을 Worker로 끌어오지 않고 Cloudflare가 소스 URL에서 직접 가져가 변환하게 한다.
// 실패는 예외로 올려 processOne의 catch가 FAILED 콜백을 보내게 한다 — 변환이 조용히 실패한 응답을
// 그대로 R2에 쓰면 깨진 파일이 저장된다.
async function transform(sourceUrl, imageOptions) {
  const res = await fetch(sourceUrl, { cf: { image: imageOptions } });
  if (!res.ok) {
    // Images는 실패 사유를 이 헤더에 담아준다(형식 미지원, 면적 100MP 초과 등).
    // 헤더가 비어 있는 경우를 대비해 본문 앞부분도 함께 싣는다 — 어떤 변환이 죽었는지 구분하려면
    // 요청한 옵션도 필요하다.
    const reason = res.headers.get("cf-resized") || res.headers.get("cf-images-error") || "";
    let body = "";
    try {
      body = (await res.text()).slice(0, 200);
    } catch {
      /* 본문을 못 읽어도 사유 없이 진행한다 */
    }
    throw new Error(`변환 실패: status=${res.status} opts=${JSON.stringify(imageOptions)} ${reason} ${body}`.trim());
  }
  return res;
}

// 키 확장자는 .avif로 고정돼 있지만 내용은 WebP일 수 있다. 키는 식별자일 뿐이고 브라우저는
// content-type을 보므로 동작에 문제가 없다 — 확장자를 내용에 맞추려면 서버 DB의 key 컬럼까지
// 바꿔야 해서 얻는 것에 비해 파장이 크다.
async function putVariant(env, key, res) {
  const contentType = res.headers.get("content-type") || "image/avif";
  await env.MEDIA_BUCKET.put(key, res.body, { httpMetadata: { contentType } });
}

function originalOptions(qualityTier) {
  const tier = QUALITY_TIERS[qualityTier] ?? QUALITY_TIERS[DEFAULT_TIER];
  const options = { format: "avif", quality: tier.quality };
  // maxWidth가 없는 등급은 리사이즈를 걸지 않는다 — 원본 해상도를 그대로 인코딩만 바꾼다.
  if (tier.maxWidth) {
    options.width = tier.maxWidth;
    options.fit = "scale-down";
  }
  return options;
}

// 리사이즈와 블러를 한 객체에 함께 준다 — cf.image는 바인딩과 달리 파이프라인을 체이닝하지 않고
// 옵션 집합을 한 번에 적용한다.
function thumbOptions({ blur }) {
  const options = {
    width: THUMB_WIDTH,
    height: THUMB_HEIGHT,
    fit: "cover",
    gravity: "auto",
    format: "avif",
    quality: AVIF_QUALITY,
  };
  if (blur) options.blur = ADULT_BLUR;
  return options;
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
