-- 설정 화면 "성인 콘텐츠 표시" 토글 (Figma UI개편_설정 — 개인/창작자 5266:39308, 기업 5397:39510).
-- 순수 표시 설정만 담는 필드다. 본인 인증 완료 여부에 따른 실제 콘텐츠 접근 게이팅
-- (정책 설정-R10: OFF / ON+미인증 blur / ON+인증 원본)은 PASS 본인인증(로드맵 1번) 스코프이며 여기서는 다루지 않는다.
-- 기존 회원은 기본값 OFF(0)로 채워진다.

ALTER TABLE members
    ADD COLUMN adult_content_visible TINYINT(1) NOT NULL DEFAULT 0 AFTER country_code;
