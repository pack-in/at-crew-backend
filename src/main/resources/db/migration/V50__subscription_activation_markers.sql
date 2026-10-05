-- 구독 "최초 활성화" 시각과 "최초 실결제" 시각 — plans/261005-posthog-analytics-events code-review(2026-10-06).
--
-- 결제 거절로 incomplete 상태로 시작한 구독이 나중에 정상화돼도 subscription_activated가 영구
-- 유실되는 결함(한 번도 activated_at이 안 찍혀서)과, 결제 실패→복구 판정이 미러의 현재 status에
-- 의존해 웹훅 도착 순서에 따라 결과가 바뀌는 결함을 고치는 데 쓴다. activated_at은 상태가 처음
-- ACTIVE가 된 시각, first_payment_at은 금액이 0보다 큰 결제가 처음 성공한 시각이다. 둘 다 한 번
-- 세팅되면 끝(null → 값, 역방향 없음)이라 널이면 "아직 없음"으로 취급해도 안전하다.
--
-- V49의 카운터(renewal_number 등)는 과거 횟수를 복원하지 않고 0에서 시작하지만, 이 두 컬럼은
-- 성격이 다르다 — 값이 비어 있으면 숫자가 아니라 이벤트 종류 자체가 틀리게 나간다(복구를
-- "최초 활성화"로 잘못 분류). 그래서 기존 행은 이미 미러에 올라와 있다는 사실 자체를
-- "활성화·결제 이력이 있다"는 증거로 보고 created_at으로 채운다.
ALTER TABLE billing_subscriptions
    ADD COLUMN activated_at    TIMESTAMP(6) NULL AFTER recovery_attempt_number,
    ADD COLUMN first_payment_at TIMESTAMP(6) NULL AFTER activated_at;

UPDATE billing_subscriptions
SET activated_at = created_at,
    first_payment_at = created_at;
