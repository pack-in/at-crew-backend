-- 결제 성공 분석 이벤트에 실을 누적 횟수 카운터 — plans/261005-posthog-analytics-events PA-09.
--
-- subscription_renewed의 renewal_number(몇 번째 정기 갱신인지)와 subscription_payment_recovered의
-- recovery_attempt_number(결제 실패 후 몇 번째 복구인지)는 Stripe invoice에 그대로 있는 값이 아니라
-- 구독 단위 누적 횟수다. invoice 이력을 Stripe API로 되짚으려면 billing에 지금 없는 능동 호출 코드가
-- 필요하고 그쪽이 더 큰 작업이라, 미러 테이블에 카운터를 들고 웹훅을 처리할 때 올린다.
--
-- 기존 행은 0에서 시작한다 — 과거 갱신·복구 횟수는 복원하지 않는다. 분석 집계는 배포 이후 구간만
-- 유효하며, 임의의 추정값을 넣으면 그 구간의 숫자도 믿을 수 없게 된다.
ALTER TABLE billing_subscriptions
    ADD COLUMN renewal_number          INT NOT NULL DEFAULT 0 AFTER cancel_at_period_end,
    ADD COLUMN recovery_attempt_number INT NOT NULL DEFAULT 0 AFTER renewal_number;
