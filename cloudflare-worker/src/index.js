// 서버(media 모듈 R2StorageAdapter.triggerWorker)가 이 Worker를 호출하는 트리거 payload와
// 서버(MediaInternalController)가 기대하는 콜백 payload는 docs/design/media-module-design.md §6~7 참고.

const THUMB_WIDTH = 294;
const THUMB_HEIGHT = 392; // 3:4 비율 (294 * 4 / 3)
const ADULT_BLUR = 20;
const AVIF_QUALITY = 80; // 썸네일 품질 — 카드 화질은 플랜 차등 대상이 아니라 등급과 무관하게 고정이다

// 변환 화질 등급별 원본 파라미터 (요금제-R03·R04, 서버의 MediaQualityTier와 값이 일치해야 한다).
// 상한은 "긴 변"이 아니라 가로 폭 기준이다 — 웹툰 원고는 세로로 길어서 긴 변으로 제한하면 원고가 뭉개진다.
// fit: "scale-down"이라 상한보다 작은 원본은 확대하지 않고 그대로 둔다.
const QUALITY_TIERS = {
  WEB: { maxWidth: 1280, quality: 72 },
  ORIGINAL: { maxWidth: 2560, quality: 85 },
};
const DEFAULT_TIER = "ORIGINAL";

// 업로드 원본 용량 상한 5MB. Presigned PUT은 크기를 강제할 수 없어(서명에 Content-Length 조건이 없다)
// 변환 직전 R2 객체 크기로 검사하고, 초과분은 FAILED 콜백으로 돌려보낸다.
const MAX_ORIGINAL_BYTES = 5 * 1024 * 1024;

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

    await callback(env, { ownerType, ownerId, imageKey, thumbKey, thumbAdultKey, originalAvifKey, status: "DONE" });
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

async function encodeOriginal(env, bytes, qualityTier) {
  const tier = QUALITY_TIERS[qualityTier] ?? QUALITY_TIERS[DEFAULT_TIER];
  const result = await env.IMAGES.input(bytes)
    .transform({ width: tier.maxWidth, fit: "scale-down" })
    .output({ format: "image/avif", quality: tier.quality });
  return result.response();
}

async function encodeThumb(env, bytes, { blur }) {
  let pipeline = env.IMAGES.input(bytes).transform({
    width: THUMB_WIDTH,
    height: THUMB_HEIGHT,
    fit: "cover",
    gravity: "auto",
  });
  if (blur) pipeline = pipeline.transform({ blur: ADULT_BLUR });
  const result = await pipeline.output({ format: "image/avif", quality: AVIF_QUALITY });
  return result.response();
}

async function callback(env, body) {
  await fetch(env.SERVER_CALLBACK_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Internal-Secret": env.INTERNAL_SECRET },
    body: JSON.stringify(body),
  });
}
