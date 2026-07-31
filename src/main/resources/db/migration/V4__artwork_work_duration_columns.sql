-- V1의 artworks.work_duration(단일 VARCHAR(30))은 WorkDuration(months/days/hours/minutes 4개 nullable Integer)
-- 값객체를 표현하기에 맞지 않아, members.terms_* 컬럼과 동일한 방식(값객체를 여러 컬럼으로 펼침)으로 교체한다.
-- Flyway 마이그레이션은 한 번 적용된 뒤에는 수정하지 않고 새 버전으로 고친다.

ALTER TABLE artworks DROP COLUMN work_duration;

ALTER TABLE artworks
    ADD COLUMN work_duration_months INT NULL,
    ADD COLUMN work_duration_days INT NULL,
    ADD COLUMN work_duration_hours INT NULL,
    ADD COLUMN work_duration_minutes INT NULL;
