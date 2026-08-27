-- 변환 화질 등급(요금제-R03 "웹 감상에 적합한 화질" / R04 "선명한 원본 화질")을 media_assets에 추가한다.
-- 재시도(ImageRetryScheduler)가 최초 업로드와 같은 화질로 다시 처리하도록 등급을 함께 보관한다.
--
-- 기본값을 ORIGINAL로 두는 이유: 이 컬럼이 생기기 전에 올라온 이미지는 전부 해상도 축소 없이
-- AVIF q80으로 변환됐다. 기존 행을 WEB으로 채우면 재시도 시 축소본으로 바뀌어 이미 서비스 중인
-- 이미지의 화질이 소급해서 내려간다 — 화질은 업로드 시점 기준이라는 정책과 어긋난다.
ALTER TABLE media_assets
    ADD COLUMN quality_tier VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL' AFTER variant_profile;
