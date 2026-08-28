# media 모듈 설계

> 작성일: 2026-08-03
> 상태: 설계 확정, 구현 전
> 배경: recruit 게시글 이미지 업로드(§7 게이팅 스텁 해소) 작업 중, artwork 모듈에 내장된
> "Presigned URL 발급 → R2 업로드 → Cloudflare Worker 비동기 변환 → webhook 콜백 → 재시도" 파이프라인을
> recruit이 필요로 하면서 나온 결정. artwork의 `internal` 서브패키지(Worker 트리거, webhook, 재시도
> 스케줄러, 고아파일 정리)는 spring-modulith 경계상 recruit이 직접 재사용할 수 없고, 그렇다고 도메인마다
> 복제하면 인프라 코드가 모듈 수만큼 증식한다 — 그래서 이 인프라를 `media` 모듈로 추출한다.
> 관련: [[project_media_module_extraction]], `docs/design/artwork-module-design.md` §5·§6.7·§10,
> `docs/design/recruit-module-design.md` §7.

---

## 0. 설계 요약 (TL;DR)

- **무엇을 추출하는가**: artwork의 `internal.infra.storage`(R2 presign/삭제), `internal.application.ImageProcessingWorker`
  (@Async Worker 트리거), `internal.web.ArtworkInternalController`(webhook 수신), `internal.application.ImageRetryScheduler`
  (재시도), `internal.application.OrphanImageCleanupScheduler`(고아파일 정리) — 이 다섯 개를 도메인 무관한 형태로
  `media` 모듈에 새로 만든다.
- **무엇을 남기는가**: `Artwork`/`ArtworkImage` 도메인 로직(성인 blur 여부 판단, `Artwork.status` 전이, 대표 이미지
  인덱스 등)은 artwork에 그대로 둔다. artwork는 media의 **첫 번째 소비자**로 리팩터링되고, recruit은 **두 번째
  소비자**로 처음부터 media 위에서 시작한다.
- **연결 방식**: 이 프로젝트에 이미 확립된 이벤트 기반 로컬 읽기 모델 패턴(`ArtworkChangedEvent`를 `search`/`recruit`가
  `@ApplicationModuleListener`로 구독하는 것과 동일한 방식)을 그대로 따른다. `media`가 처리 완료를
  `MediaAssetProcessedEvent`로 발행하면, artwork/recruit은 각자의 로컬 테이블(`artwork_images`,
  `job_posting_images` 등)에 처리 상태를 반영한다 — 조회 시 `media`를 역참조하지 않는다.
- **외부 의존성 경고**: Cloudflare Worker 트리거 페이로드가 `{"artworkId": ...}`에서 `{"ownerType": ...,
  "ownerId": ...}`로 바뀌므로, **이 리포지토리 밖에 있는 Cloudflare Worker 스크립트도 함께 수정해야 한다**(§9).
  이건 Orca 코딩 워커가 이 레포 안에서 끝낼 수 있는 작업이 아니다 — 별도로 조율 필요.

---

## 1. 모듈 분리 결정 근거

### 1.1 왜 도메인 모듈에 남기지 않는가

presign 발급·Worker 트리거·webhook 수신·재시도·고아파일 정리는 "이미지를 업로드하고 비동기로 변환한다"는
저장소/인프라 관심사이지, artwork나 recruit의 도메인 규칙이 아니다. 두 가지 실패 패턴을 피하기 위해 분리한다.

- **재사용 시도의 실패**: artwork의 이 다섯 컴포넌트는 전부 `internal` 서브패키지에 있고, `ModularStructureTests`가
  CI에서 spring-modulith `ApplicationModules.verify()`로 이 경계를 강제한다. recruit이 직접 참조하면 빌드가
  깨진다 — 우회하려면 캡슐화를 풀어야 하는데, 그러면 애초에 모듈을 나눈 의미가 없어진다.
- **복제의 실패**: recruit에 같은 다섯 컴포넌트를 새로 짜면 당장은 동작하지만, 다음에 community 배너나 company
  로고가 같은 걸 필요로 할 때 세 번째 복제가 생긴다. Worker 프로토콜이 바뀌거나 webhook 시크릿 검증에 버그가
  생기면 N개 모듈에서 각각 고쳐야 한다.

### 1.2 왜 지금 하는가 (비용 대비)

지금은 artwork(운영 중) 리팩터링이 포함돼 recruit 단독 기능 추가보다 비용이 크다. 하지만 recruit이 이미
두 번째 필요 사례이고, community/company 로고·배너도 로드맵상 조만간 필요해질 가능성이 높다 — 세 번째
복제가 생기기 전에, 이미 갈라진 두 벌(artwork+recruit)을 통합하는 것보다 지금 한 번에 뽑아내는 비용이 더
싸다.

---

## 2. 도메인 모델

### 2.1 MediaAsset (테이블: `media_assets`)

`ArtworkImage`를 도메인 무관하게 일반화한 것. 소유자를 `(ownerType, ownerId)` 쌍으로 표현한다 — FK 대신
문자열 참조를 쓰는 이유는 media가 artwork/recruit 테이블을 직접 참조하면 모듈 의존 방향이 반대로 꼬이기
때문이다(다른 모듈 간 참조도 전부 이 프로젝트에서 ID 문자열 참조 + 이벤트 방식을 쓴다).

| 필드 | 타입 | 설명 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT | 순수 내부 행, 외부 미노출 |
| ownerType | VARCHAR(30) | `MediaOwnerType` — ARTWORK, JOB_POSTING, TEAM_POSTING, JOB_SEEKING_POST |
| ownerId | VARCHAR(36) | 소유자 도메인의 ID (FK 아님, 문자열 참조) |
| ordinal | INT | 소유자 내 이미지 순서 |
| originalKey | VARCHAR(500) | R2에 업로드된 원본 key |
| thumbKey | VARCHAR(500) NULL | Worker 처리 후 채워짐 |
| thumbAdultKey | VARCHAR(500) NULL | `variantProfile=STANDARD_WITH_ADULT_BLUR`일 때만 채워짐 |
| originalAvifKey | VARCHAR(500) NULL | Worker 처리 후 채워짐 |
| variantProfile | VARCHAR(30) | `MediaVariantProfile` — STANDARD, STANDARD_WITH_ADULT_BLUR |
| qualityTier | VARCHAR(20) | `MediaQualityTier` — WEB, ORIGINAL. 컬럼 추가 이전 행은 ORIGINAL(V33 기본값) |
| processingStatus | VARCHAR(30) | PENDING / DONE / FAILED |
| createdAt / updatedAt | DATETIME(6) | 재시도 스케줄러가 `updatedAt` 기준으로 스캔(§7) |

인덱스: `idx_ma_owner (owner_type, owner_id, ordinal)`, `idx_ma_retry (processing_status, updated_at)`.

기존 `artwork_images`의 `uk_ai_order (artwork_id, ordinal)` 유니크 제약과 동일하게
`uk_ma_owner_order (owner_type, owner_id, ordinal)`을 둔다 — artwork의 이미지 교체 시 2단계 detach/attach
패턴(`docs/design/mariadb-migration-design.md` §3.3.2 계열 함정, `ArtworkServiceImpl.replaceImages` 주석
참고)도 media로 그대로 옮겨온다.

### 2.2 OrphanedMediaKey (테이블: `orphaned_media_keys`)

기존 `OrphanedImageKey`를 그대로 이관 — 소유자와 무관하게 "삭제 예정 R2 key 목록"만 담는 정리 큐이므로
일반화에 변경이 필요 없다.

---

## 3. Enum 정의

```java
public enum MediaOwnerType { ARTWORK, JOB_POSTING, TEAM_POSTING, JOB_SEEKING_POST }

public enum MediaVariantProfile { STANDARD, STANDARD_WITH_ADULT_BLUR }

public enum MediaQualityTier { WEB, ORIGINAL }

public enum MediaProcessingStatus { PENDING, DONE, FAILED }
```

`MediaVariantProfile`은 Worker에게 "성인물 blur 썸네일까지 만들지"를 알려주는 파라미터다. 현재는
`ARTWORK`만 `STANDARD_WITH_ADULT_BLUR`를 쓴다 — recruit 콘텐츠는 성인 게이팅 대상이 아니라는 기존 결정
(`recruit-module-design.md` §7)을 그대로 따라 `STANDARD`만 쓴다.

`MediaQualityTier`(2026-08-27 추가)는 요금제-R03("웹 감상에 적합한 화질")·R04("선명한 원본 화질")의
플랜 차등을 담는다. 성인 blur 여부와는 직교하는 축이라 `MediaVariantProfile`에 값을 늘리지 않고 별도
파라미터로 둔다 — 합치면 조합만큼 값이 늘어난다.

| 등급 | 대상 | 원본 변환 파라미터 |
|---|---|---|
| `WEB` | 스타터 플랜 작품, recruit 이미지 전부 | 가로 폭 1280px 상한(`fit: scale-down`), AVIF q72 |
| `ORIGINAL` | 프로 플랜 작품 | 가로 폭 2560px 상한, AVIF q85 |

- 상한은 **긴 변이 아니라 가로 폭** 기준이다. 웹툰 원고는 세로로 길어서 긴 변으로 제한하면 원고가 뭉개진다.
- `fit: scale-down`이라 상한보다 작은 원본은 확대하지 않는다.
- 썸네일(294×392)은 플랜과 무관하게 q80으로 동일하다 — 카드 화질은 차등 대상이 아니다.
- **등급은 업로드 시점 플랜으로 확정되고 변환은 1회뿐이다.** 프로 → 스타터 다운그레이드로 기존 이미지
  화질이 내려가지 않고(요금제-R01), 스타터 → 프로 전환으로 기존 이미지가 선명해지지도 않는다.
  재시도(`ImageRetryScheduler`)가 최초와 같은 결과를 내도록 `media_assets.quality_tier`에 함께 보관한다.
- 업로드 원본 용량 상한은 **5MB**다. Presigned PUT은 서명에 Content-Length 조건을 넣을 수 없어 크기를
  강제하지 못하므로, Worker가 변환 직전 R2 객체 크기를 검사해 초과분을 FAILED 콜백으로 돌려보낸다.

---

## 4. 공개 API (`com.atcrew.media`, 모듈 밖에서 참조 가능)

```java
public interface MediaService {

    List<PresignedUrlInfo> generatePresignedUrls(int count, List<String> contentTypes);

    // artwork.uploadArtwork()/updateArtwork()가 imageProcessingWorker.triggerAsync를 직접 호출하던 자리를 대체.
    // MediaAsset PENDING 행을 저장하고 Worker를 비동기 트리거한다.
    void registerAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
                                       List<String> imageKeys, MediaVariantProfile variantProfile,
                                       MediaQualityTier qualityTier);

    // 소유자가 이미지 목록을 교체할 때(artwork replaceImages와 동일 패턴) 기존 행을 고아 처리하고 새로 등록.
    void replaceAndTriggerProcessing(MediaOwnerType ownerType, String ownerId,
                                      List<String> newImageKeys, MediaVariantProfile variantProfile,
                                      MediaQualityTier qualityTier);

    List<MediaAssetInfo> getAssets(MediaOwnerType ownerType, String ownerId);

    // 영구 삭제 시 즉시 R2 파일 제거를 시도하고, 실패하면 호출자가 markOrphaned로 정리 큐에 적재한다.
    // ArtworkEventListener.onPermanentlyDeleted가 지금 ArtworkStoragePort.deleteFiles를 직접 호출하는
    // 자리를 대체 — QA에서 발견: 최초 초안은 이 소비자를 놓쳐 deleteFiles를 공개 API에서 빠뜨렸었다.
    void deleteFiles(List<String> keys);

    void markOrphaned(List<String> keys);
}

public record PresignedUrlInfo(String key, String uploadUrl);

public record MediaAssetInfo(String originalKey, String thumbKey, String thumbAdultKey,
                              String originalAvifKey, MediaProcessingStatus status);

public record MediaAssetProcessedEvent(MediaOwnerType ownerType, String ownerId, String imageKey,
                                        String thumbKey, String thumbAdultKey, String originalAvifKey,
                                        MediaProcessingStatus status);
```

`generatePresignedUrls`의 count(1~30)/contentType 화이트리스트(jpeg/png/webp) 검증은 `ArtworkServiceImpl`에서
그대로 옮겨온다 — 도메인과 무관한 범용 검증이라 변경 없음.

---

## 5. 이벤트 계약

`MediaAssetProcessedEvent`는 artwork의 `ArtworkChangedEvent`와 동일하게 top-level(`com.atcrew.media`
패키지)에 두고, 소비자는 `@ApplicationModuleListener`로 구독한다(이 프로젝트 기존 패턴 —
`search.internal.application.ArtworkSearchIndexer`, `recruit.internal.application.ArtistProfileViewListener`
참고).

- **artwork 소비**: 자신의 `artwork_images` 행 중 `originalKey`가 일치하는 것을 찾아
  `thumbKey`/`thumbAdultKey`/`originalAvifKey`/`processingStatus`를 갱신한다. `Artwork.status`를
  `PROCESSING → READY`로 전환하는 조건은 **"모든 이미지 DONE"이 아니라** `Artwork.markImageProcessed`의
  기존 규칙 그대로 **"PENDING이 하나도 없고(재시도 여지 없음) DONE이 하나 이상"** — 부분 실패를 허용한다
  (QA에서 발견: 최초 초안이 "모든 이미지 DONE"으로 잘못 적어, 그대로 구현했다면 이미지 하나라도 FAILED면
  영원히 READY로 못 넘어가는 회귀가 생겼을 것). 이 로직은 `Artwork` 애그리게잇에 그대로 남고, 리스너는
  이벤트를 받아 `Artwork`에 위임만 한다 — 부분 실패 허용 여부 판단 자체는 도메인 로직이라 media로 옮기지
  않는다.
- **recruit 소비**(신규): 자신의 `job_posting_images`/`team_posting_images`/`job_seeking_post_images` 행을
  갱신하고, 소유 게시글의 `imageProcessingStatus`를 갱신(§10). READY 전환 조건은 artwork와 동일하게
  "PENDING 없음 + DONE 1개 이상"을 기본값으로 따른다 — 다르게 할 이유가 없으면 두 도메인 규칙을 갈라놓지
  않는다.

---

## 6. 내부 Webhook (Cloudflare Worker → 서버)

```
POST /internal/media/images/processed
Header: X-Internal-Secret
Body: {
  "ownerType": "ARTWORK" | "JOB_POSTING" | "TEAM_POSTING" | "JOB_SEEKING_POST",
  "ownerId": "...",
  "imageKey": "raw/....jpg",
  "thumbKey": "thumb/....avif",
  "thumbAdultKey": "thumb-adult/....avif",   // variantProfile=STANDARD_WITH_ADULT_BLUR일 때만
  "originalAvifKey": "original/....avif",
  "status": "DONE" | "FAILED"
}
```

기존 `ArtworkInternalController`의 `X-Internal-Secret` 상수시간 비교(`MessageDigest.isEqual`) 검증 로직을
그대로 옮긴다. 엔드포인트 하나로 모든 owner 타입의 콜백을 받는다 — artwork용/recruit용을 나누지 않는다
(Worker 스크립트가 ownerType을 그대로 echo하기만 하면 되므로 외부 스크립트 변경을 최소화).

---

## 7. Worker 트리거 / 재시도 / 정리

### 7.1 Worker 트리거

```java
@Async
void triggerAsync(MediaOwnerType ownerType, String ownerId, List<String> imageKeys,
                   MediaVariantProfile variantProfile, MediaQualityTier qualityTier) {
    storagePort.triggerWorker(ownerType, ownerId, imageKeys, variantProfile, qualityTier);
}
```

`R2StorageAdapter.triggerWorker`의 요청 바디가 `{"artworkId":..., "imageKeys":[...]}`에서
`{"ownerType":..., "ownerId":..., "imageKeys":[...], "variantProfile":..., "qualityTier":...}`로 바뀐다 —
**이건 외부 Cloudflare Worker 스크립트도 같이 바뀌어야 함을 뜻한다(§9)**. `qualityTier`가 없거나 모르는
값이면 Worker는 `ORIGINAL`로 폴백한다 — 서버 배포와 Worker 배포 사이의 시차에 기존 동작을 유지하기 위해서다.

### 7.2 재시도 — 기존보다 단순해짐

기존 `ImageRetryScheduler`는 `Artwork.status == PROCESSING`인 작품을 찾아 그 안의 PENDING 이미지를
재시도하는 간접 방식이었다(재시도 대상을 소유자의 상태 필드로 우회 조회). `media`는 `MediaAsset` 테이블을
직접 갖고 있으므로 더 단순하게 스캔한다:

```java
@Scheduled(fixedDelay = 300_000)
void retryStuckAssets() {
    Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
    var stuck = mediaAssetRepository.findByProcessingStatusAndUpdatedAtBefore(
            MediaProcessingStatus.PENDING, threshold);
    // ownerType+ownerId로 그룹핑 후 재트리거
}
```

owner 상태를 거치지 않고 자산 테이블 자체를 스캔하므로, 앞으로 owner 타입이 늘어나도 재시도 로직 변경이
필요 없다.

### 7.3 고아파일 정리

`OrphanImageCleanupScheduler`를 그대로 이관 — `OrphanedMediaKey`를 1시간마다 배치로 스캔해 R2에서 삭제.
변경 없음.

---

## 8. 모듈 경계

```
media (공개: MediaService, MediaAssetProcessedEvent, PresignedUrlInfo, MediaAssetInfo, MediaOwnerType, MediaVariantProfile)
  ├─ artwork (소비자 1) — MediaService.registerAndTriggerProcessing 호출, MediaAssetProcessedEvent 구독
  └─ recruit (소비자 2) — 동일

media → artwork/recruit 방향 참조 없음 (ownerId는 불투명 문자열, FK 아님)
```

`ArtworkStoragePort`/`R2StorageAdapter`/`R2Properties`는 media의 `internal.infra.storage`로 이동한다.
`cloudflare.r2.*` 설정 프로퍼티도 media 모듈 소유로 옮긴다(`application.yml` 키 이름은 유지 — 인프라
설정값이지 도메인 값이 아니므로 굳이 `media.*`로 바꿀 필요 없음).

---

## 9. 마이그레이션 계획

### 9.1 리포지토리 내부 작업 (Orca 워커 가능)

1. `media` 모듈 생성 — 엔티티/리포지토리/서비스/이벤트/컨트롤러/스케줄러를 §2~§7대로 신규 작성.
2. Flyway `V10__media_assets.sql` — `media_assets`, `orphaned_media_keys` 테이블 생성.
3. artwork 리팩터링: `ArtworkServiceImpl`이 `MediaService`를 주입받아 `generatePresignedUrls`/
   `registerAndTriggerProcessing`/`replaceAndTriggerProcessing` 위임으로 교체. `ArtworkImage`는 도메인 필드
   (ordinal, 대표이미지 판단)만 남기고 처리 상태 갱신은 `MediaAssetProcessedEvent` 리스너로 이동.
4. 기존 `artwork_images.processing_status`/`thumb_key`/`thumb_adult_key`/`original_avif_key` 컬럼은
   당분간 유지(읽기 캐시 역할) — 즉시 제거하지 않는다. 데이터는 `media_assets`에서 이벤트로 채워진다.
5. artwork의 `internal.infra.storage.*`, `ImageProcessingWorker`, `ArtworkInternalController`,
   `ImageRetryScheduler`, `OrphanImageCleanupScheduler`, `OrphanedImageKey`(+Repository) 삭제. 이때
   `ArtworkEventListener.onPermanentlyDeleted`가 `ArtworkStoragePort.deleteFiles`를 직접 호출하던 것도
   `MediaService.deleteFiles`/`markOrphaned` 호출로 같이 바꿔야 한다(§4 — QA에서 발견한 누락 소비자).
6. `SecurityConfig.java`의 `.requestMatchers(HttpMethod.POST, "/internal/artwork/images/processed").permitAll()`
   을 `/internal/media/images/processed`로 교체(§9.2 전환 기간에는 두 경로 모두 유지 — 아래 참고).
   `SearchAdminController`의 "artwork의 내부 콜백 인증 패턴(`ArtworkInternalController`)과 동일" 주석도
   클래스가 사라지므로 갱신.
7. recruit에 §10 계획대로 자식 테이블·리스너 신설.
8. `ModularStructureTests`(spring-modulith `verify()`)가 새 모듈 그래프에서 그린인지 확인 — media가
   순환 의존을 만들지 않는지가 핵심 검증 포인트.
9. **테스트 커버리지**: 현재 `ImageProcessingWorker`/`ArtworkInternalController`/`R2StorageAdapter`/
   `ImageRetryScheduler`/`OrphanImageCleanupScheduler`를 직접 검증하는 테스트가 하나도 없다(QA에서 확인 —
   회귀 안전망 없이 리팩터링하는 셈). media 모듈 신설과 함께 최소한 (a) presign 발급 검증 로직, (b) webhook
   콜백 → 이벤트 발행 → artwork/recruit 리스너가 각자 상태를 갱신하는 통합 테스트, (c) 부분 실패 허용
   READY 전환 조건(§5)을 검증하는 테스트를 추가한다.

### 9.2 리포지토리 밖 작업 (Orca 워커가 끝낼 수 없음, 별도 조율 필요)

Cloudflare Worker 스크립트(이 레포에 없음, `cloudflare.r2.workerTriggerUrl`이 가리키는 외부 배포물)가
현재 트리거 요청의 `artworkId` 필드와 콜백 응답의 `artworkId` 필드를 가정하고 있다. `ownerType`/`ownerId`로
바뀌면 Worker 스크립트도 함께 바뀌어야 한다. **recruit 이미지 처리는 Worker가 새 형식을 받아들이기 전까지는
아예 작동할 수 없다** — recruit은 Worker 입장에서 완전히 새로운 owner 타입이라, 우회할 방법 자체가 없다.
즉 진짜 문제는 "artwork를 안 깨뜨리면서" Worker 업데이트 시점을 recruit 출시 시점과 맞추는 것이다.

**QA에서 잡은 순서 오류**: 최초 초안은 "Worker 먼저 배포"를 제안했는데, 이게 실제로 위험하다는 걸 코드로
확인했다. `application.yml`에 `spring.jackson.deserialization.fail-on-unknown-properties`를 끄는 설정이
없고, 프로젝트 전역에 `ObjectMapper` 커스터마이징도 없다 — 즉 Jackson 기본값(모르는 필드가 오면
역직렬화 예외)이 그대로 적용된다. `ImageProcessedCallbackRequest`(현재 DTO)에 없는 `ownerType`/`ownerId`
필드를 Worker가 콜백에 추가해서 보내면, **구버전 서버가 그 요청을 400으로 거부한다** — "하위 호환 처리는
Worker가 먼저 하면 된다"는 가정이 틀렸다. 서버가 새 필드를 받아들일 준비가 되기 전에 Worker가 새 필드를
보내기 시작하면 기존 artwork 콜백이 끊긴다.

**정정된 롤아웃 순서**:
1. **서버를 먼저, 관대하게 배포한다.** `media` 모듈의 새 엔드포인트 `/internal/media/images/processed`를
   추가하되, 콜백 DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)`를 붙여 모르는 필드를 무시하게 한다.
   동시에 **기존 `/internal/artwork/images/processed`도 당분간 남겨두고**, 내부적으로 `artworkId`를
   `ownerType=ARTWORK`로 변환해 동일한 `MediaAssetProcessedEvent`를 발행하는 얇은 shim으로 만든다
   (`SecurityConfig`도 두 경로 모두 permitAll 유지, §9.1-6). 이 배포 시점에는 Worker가 아직 옛 형식만 보내도
   전혀 문제없다.
2. **Worker 스크립트를 그 다음에 배포한다.** 새 트리거 요청(`ownerType`/`ownerId`)을 보내고, 콜백에도
   `ownerType`/`ownerId`를 채워서 `/internal/media/images/processed`로 보내도록 바꾼다. 이 시점부터
   recruit 이미지 처리가 실제로 동작하기 시작한다.
3. Worker가 완전히 전환된 걸 확인한 뒤(며칠 관찰), 다음 배포에서 `/internal/artwork/images/processed`
   shim과 `SecurityConfig`의 옛 경로 permitAll을 제거한다.

이 순서 조율(특히 2번, Worker 스크립트 자체의 코드 변경과 배포)은 이 레포 안에서 Orca 워커가 끝낼 수
없다 — Worker가 어디서 관리되는지(Cloudflare dashboard 수동 편집인지 별도 저장소의 wrangler CI인지)부터
사용자 확인이 필요하다.

---

## 10. recruit 적용 계획

### 10.1 신규 테이블

`job_posting_images` / `team_posting_images` / `job_seeking_post_images` — 각각 기존
`thumbnail_image`(단일 VARCHAR)와 `reference_images`(JSON 리스트)를 대체하는 자식 테이블:

| 필드 | 타입 |
|---|---|
| id | BIGINT AUTO_INCREMENT |
| postingId | VARCHAR(36) |
| role | VARCHAR(20) — THUMBNAIL / REFERENCE |
| ordinal | INT |
| originalKey | VARCHAR(500) |
| thumbKey / originalAvifKey | VARCHAR(500) NULL |
| processingStatus | VARCHAR(30) |

`thumbAdultKey`는 recruit에 없음(§3, `variantProfile=STANDARD` 고정이므로 컬럼 자체를 만들지 않는다).

### 10.2 posting 레벨 상태

기존 `JobPostingStatus`(DRAFT/PENDING/PUBLISHED/CLOSED/DELETED)에 PROCESSING을 끼워 넣지 않는다.
별도 `imageProcessingStatus`(PENDING/READY) 필드를 posting에 추가해 발행 상태와 이미지 처리 상태를
독립 축으로 관리한다 — artwork는 이미지가 곧 콘텐츠라 상태를 합쳤지만, recruit은 텍스트 게시글에 이미지가
부속이라 두 흐름을 분리하는 편이 상태 전이 로직을 단순하게 유지한다.

### 10.3 API

- `POST /api/recruit/images/presign` — `MediaService.generatePresignedUrls` 위임.
- `JobPosting`/`TeamPosting`/`JobSeekingPost` 생성·수정 시 `MediaService.registerAndTriggerProcessing(
  MediaOwnerType.JOB_POSTING, postingId, imageKeys, MediaVariantProfile.STANDARD)` 호출.
- `RecruitMediaEventListener`(신규) — `MediaAssetProcessedEvent` 구독, `ownerType`으로 세 자식 테이블 중
  하나를 갱신. posting의 `imageProcessingStatus = READY` 전환 조건은 §5와 동일하게 "PENDING 없음 + DONE
  1개 이상"(부분 실패 허용) — "전부 DONE"이 아니다.

### 10.4 기존 REST 계약과의 호환성

recruit은 이미 main에 병합·운영 중인 모듈이라(`[[project_recruit_module]]`), `CreateJobPostingRequest`/
`JobPostingInfo` 등 DTO의 `thumbnailImage: String`/`referenceImages: List<String>` 필드 자체는 그대로
유지한다 — 자식 테이블 도입은 내부 영속 구조 변경일 뿐, 클라이언트가 보내고 받는 필드 이름·타입을 바꾸는
API 브레이킹 체인지가 아니다. 클라이언트가 요청에 넣는 값은 이제 raw 파일 URL이 아니라 presign으로 발급받은
`key`가 되고(§10.3 presign 흐름을 거쳐야 함 — 기존에는 클라이언트가 아무 URL이나 넣을 수 있었던 것과
차이), 응답 시 `thumbnailImage`/`referenceImages`는 자식 테이블에서 `originalAvifKey`(처리 완료 시) 또는
`originalKey`(처리 중일 때 폴백)로 조립해 채운다. 이 부분은 실제 구현 Task에서 정확한 필드 매핑을
확정해야 한다 — 이번 설계 문서는 저장 구조·이벤트 계약까지만 다루고 API 응답 조립 세부는 열어둔다(§11에
추가).

---

## 11. 열린 이슈

- Worker 스크립트 롤아웃(§9.2)은 사용자 확인 필요 — 실제 배포 방식(수동 dashboard vs wrangler CI)을
  아직 조사하지 않음. 2단계(서버 선배포)만으로는 부족하고 Worker 쪽 코드 변경·배포 자체가 이 레포 밖의
  별도 작업이라는 점을 실행 전 사용자가 확인해야 한다.
- `artwork_images`의 캐시 컬럼(§9.1-4)을 언제 완전히 제거할지는 이번 스코프 밖 — 당분간 이중 저장 유지.
- recruit 응답 DTO의 `thumbnailImage`/`referenceImages` 필드를 자식 테이블에서 어떻게 조립할지(§10.4)
  세부 매핑 미확정 — 구현 Task 스펙 작성 시 확정 필요.
- (QA 완료, 2026-08-03) 아래 항목은 이번 QA에서 발견해 본문에 반영 완료: `MediaService.deleteFiles` 누락
  (§4), READY 전환 조건 오기술(§5·§10.3), `SecurityConfig`/`SearchAdminController` 갱신 누락(§9.1-6),
  Worker 롤아웃 순서 오류(§9.2), 테스트 커버리지 전무(§9.1-9), API 호환성 미명시(§10.4).
- community 배너/company 로고가 실제로 이 파이프라인을 필요로 하는 시점은 아직 로드맵에 없음 — 그때
  세 번째 소비자로 §8 구조에 추가하면 된다(신규 `MediaOwnerType` 값만 늘리면 됨).
- (해결, 2026-08-04) Task B·C 구현 과정에서 발견된 3건을 §4에 `MediaService.deleteAssetsForOwner`를
  신설해 해결: ①영구삭제 후 `media_assets` 행 잔존 → `ArtworkEventListener.onPermanentlyDeleted`가 R2
  삭제 성패와 무관하게 호출 ②recruit 전체 이미지 삭제 시 정리가 다음 등록까지 미뤄지던 것 →
  `RecruitImageService.replace()`의 `NO_IMAGES` 분기에서 즉시 호출. ③배포 시점에 이미 PROCESSING
  중이던 작품이 신·구 webhook 어느 경로로도 콜백을 매칭 못 하던 문제 → `V12__backfill_media_assets_
  from_pending_artwork_images.sql`로 `artwork_images.processing_status='PENDING'` 행을 media_assets에
  백필(빈 DB에서는 no-op).
- (범위 밖으로 확정, 2026-08-04) 동일 게시글/작품에 대한 webhook 2건이 동시에 처리되면 READY 전환을
  둘 다 놓칠 수 있는 경쟁 조건(read-then-write, 락 없음)이 artwork·recruit 리스너 모두에 있다. 이건
  media 모듈 도입으로 생긴 회귀가 아니라 원래 artwork 콜백 처리부터 있던 노출 수준과 동일하다 —
  이미지 처리 전반의 락킹 재설계가 필요한 별도 과제라 이번 스코프에서 다루지 않는다.
