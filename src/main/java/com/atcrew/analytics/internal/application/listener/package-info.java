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
// 성공 이벤트는 @ApplicationModuleListener를 쓴다. 커밋 후(AFTER_COMMIT) 실행되고 Spring Modulith가
// EVENT_PUBLICATION 레지스트리에 미완료 건을 남기므로, 리스너가 예외로 끝나면 기록이 남아 재발행 대상이
// 된다. 단 재발행은 상시 백그라운드 재시도가 아니라 앱 재배포(기동) 시점에만 일어난다. 같은 사건이 두 번
// 전송될 수 있으니 $insert_id는 반드시 AnalyticsInsertId로 사건을 특정하는 고정 문자열로 만든다.
//
// 실패 이벤트는 @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)를 쓴다. 롤백된
// 트랜잭션에는 레지스트리에 완료 표시를 남길 커밋이 없어 @ApplicationModuleListener를 쓸 수 없다. 이쪽은
// 레지스트리를 거치지 않는 동기 실행이고 재시도도 없다 — 분석 유실을 감수하는 대신 실패 응답을 늦추지
// 않는다(전송 자체는 PostHog SDK가 비동기로 처리한다).
package com.atcrew.analytics.internal.application.listener;
