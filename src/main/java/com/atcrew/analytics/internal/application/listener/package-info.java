// 도메인 이벤트를 PostHog 분석 이벤트로 옮기는 리스너(plans/261005-posthog-analytics-events PA-02).
//
// 방향: 도메인 모듈은 자기 이벤트만 발행하고, analytics가 그것을 구독해 전송한다. analytics는 속성
// 조립을 위해 도메인 모듈의 공개 API를 조회하므로(analytics → 도메인), 도메인 모듈이 반대로
// AnalyticsEventPublisher를 직접 호출하면 순환 의존이 된다 — 분석 전송은 전부 이 패키지에서만 한다.
//
// 클래스는 구독 대상 모듈당 하나로 모은다 — MemberAnalyticsListener, ArtworkAnalyticsListener처럼
// "{구독 대상 모듈}AnalyticsListener"로 짓고, 그 모듈이 내는 이벤트를 메서드로 나열한다. 모듈당 파일이
// 하나뿐이라 PA-03~09가 서로 다른 파일만 건드린다.
//
// 성공 이벤트는 @ApplicationModuleListener(propagation = NOT_SUPPORTED)를 쓴다. 커밋 후(AFTER_COMMIT) 별도
// 스레드에서 실행되고 Spring Modulith가 EVENT_PUBLICATION 레지스트리에 미완료 건을 남긴다. 트랜잭션을 열지
// 않는 이유: 기본값(REQUIRES_NEW)이면 PostHog HTTP를 기다리는 동안 DB 커넥션을 쥐고 있게 된다(풀 10개,
// docs/operations/baseline/2026-09-02-real-data-load-test.md §3에서 주 병목). 속성 조회는 각 서비스의 짧은
// 트랜잭션에서 끝난다.
//
// AnalyticsEventPublisher는 PostHog에 동기로 보내고 실패하면 예외를 던진다(plans/261019-posthog-server-delivery).
// 그래서 전송이 실패한 이벤트는 기록이 남아 재발행 대상이 된다. 단 재발행은 상시 백그라운드 재시도가 아니라
// 앱 재기동 시점에만 일어난다. 같은 사건이 두 번 전송될 수 있으니 insertId는 반드시 AnalyticsInsertId로 사건을
// 특정하는 고정 문자열로 만들고, 사건 발생 시각이 있으면 occurredAt으로 넘긴다 — 둘로 만든 uuid·timestamp가
// 같아야 PostHog가 한 건으로 합친다.
// 발생 시각을 싣지 않는 이벤트(artwork_published, auth_signup_succeeded, account_deleted, portfolio_artwork_*,
// subscription_refunded)는 전송 시각을 쓰므로 재발행되면 중복으로 남는다 — 이벤트 record에 시각을 더할 때 고친다.
//
// 전송 실패는 비동기 예외 처리기가 ERROR로 남겨 Sentry 이슈가 된다. PostHog 장애 동안에는 이벤트마다 한 건씩
// 쌓이므로, 장애 감지 자체는 atcrew_modulith_incomplete_events(P2) 알람으로 본다.
//
// 실패 이벤트는 @Async + @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)를 쓴다.
// 롤백된 트랜잭션에는 레지스트리에 완료 표시를 남길 커밋이 없어 @ApplicationModuleListener를 쓸 수 없다.
// 이쪽은 레지스트리를 거치지 않고 재시도도 없다 — 분석 유실을 감수한다. 전송이 동기라 요청 스레드에서
// 보내면 실패 응답이 PostHog 왕복만큼 늦어지므로 @Async로 분리한다.
package com.atcrew.analytics.internal.application.listener;
