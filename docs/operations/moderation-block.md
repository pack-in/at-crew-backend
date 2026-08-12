# 운영 차단(모더레이션) 절차

저작권 침해 신고에 따른 노출 중단, 운영자 강제삭제, 불법 콘텐츠 판정 등 **운영 정책·법적 조치**로
작품과 그 스냅샷의 외부 노출을 즉시 중단하는 절차다(기능 명세 마이페이지_작가-R39·R41·R42·R46,
휴지통-R04).

관리자 Role·콘솔 API는 로드맵 8번(다음 마일스톤) 범위라 **현재는 API가 없다**. 차단은 DB 직접
UPDATE로 수행하고, 아래 재색인 호출을 반드시 함께 실행한다.

---

## 1. 차단 대상과 컬럼

| 대상 | 컬럼 | 비고 |
|---|---|---|
| 원본 작품 | `artworks.blocked_at` | NULL이면 정상, 값이 있으면 차단 |
| 고정형 스냅샷 | `portfolio_item_snapshots.blocked_at` | 원본 역조회가 아니라 행마다 비정규화 — 원본을 영구 삭제해도 차단 근거가 남는다 |

`portfolios.blocked_at`은 **회원 탈퇴** 차단용이라 별개 축이다. 운영 차단으로 재사용하지 않는다.

차단 상태의 효과:

- 원본 상세 조회 — 제3자 410 `ARTWORK_BLOCKED`, 작성자 본인은 열람 허용(응답 `blocked=true`로 배지 노출)
- 홈 작품 피드·검색 색인·북마크 목록에서 제외
- 포트폴리오 작품 선택·복제 자동 선택 대상에서 제외(400 `ARTWORK_BLOCKED`)
- 차단된 스냅샷은 고정형 포트폴리오의 카드·커버 썸네일·작품 개수·스냅샷 상세에서 모두 제외
  (차단은 "현재 상태 고정"보다 우선한다)

## 2. 차단 SQL

원본과 그 스냅샷을 **항상 함께** 차단한다. 원본만 막으면 이미 배포된 고정형 포트폴리오 링크로
스냅샷이 계속 노출된다.

```sql
-- 1) 원본 작품 차단
UPDATE artworks
   SET blocked_at = UTC_TIMESTAMP(6)
 WHERE id = :artworkId
   AND blocked_at IS NULL;

-- 2) 해당 원본에서 만들어진 모든 고정형 스냅샷 차단
UPDATE portfolio_item_snapshots
   SET blocked_at = UTC_TIMESTAMP(6)
 WHERE source_artwork_id = :artworkId
   AND blocked_at IS NULL;
```

해제(이의제기 인용 등)는 같은 두 문장을 `SET blocked_at = NULL`로 실행한다.

## 3. 차단 직후 실행할 재색인

DB 직접 UPDATE는 `ArtworkChangedEvent`를 발행하지 않는다. 따라서 아래가 **자동으로 갱신되지 않는다**.

| 파생 상태 | 회수 경로 |
|---|---|
| Elasticsearch 작품 색인 | 아래 전체 재색인 API를 즉시 호출 |
| `portfolios.item_count` 캐시(최신 반영형) | `PortfolioMembershipReconcileScheduler` 6시간 주기 보정 |
| 고정형 포트폴리오 작품 개수 | 조회 시점에 스냅샷 행을 직접 세므로 즉시 반영(별도 조치 불필요) |

```bash
# 작품 인덱스 전체 재색인 (운영 차단 SQL 실행 직후 1회)
curl -X POST https://<host>/internal/search/reindex
```

`item_count` 즉시 반영이 필요하면 재색인 후 해당 포트폴리오에 [수정하기] 또는 작품 추가/제거
API가 한 번 호출되기를 기다리지 말고 6시간 배치를 기다린다 — 캐시 값이 실제 노출 목록보다 크게
보일 뿐, 차단된 작품 자체는 목록·커버에서 이미 빠진다.

## 4. 보관 정책

외부 노출이 중단된 스냅샷은 신고 처리·이의제기·분쟁 대응에 필요한 범위에서만 내부적으로 보관하고,
보관 기간 종료 후 삭제한다(마이페이지_작가-R39). 차단만으로 R2 원본 파일을 삭제하지 않는다 —
`SnapshotRetainedMediaKeyProvider`의 보존 판정은 차단 여부를 보지 않으므로 차단된 스냅샷이 참조하는
이미지도 그대로 남는다.

## 5. 실행 주체·승인 절차

`plans/260812-portfolio-snapshot-gap/PLAN-HUMAN.md` PH-09에서 확정한다(코드 밖 운영 정책).
