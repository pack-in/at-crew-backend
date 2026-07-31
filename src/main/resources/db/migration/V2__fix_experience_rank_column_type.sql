-- V1에서 TINYINT로 잘못 선언한 members.experience_rank를 Member.experienceRank(Java int) 타입에 맞게 수정.
-- Flyway 마이그레이션은 한 번 적용된 뒤에는 수정하지 않고 새 버전으로 고친다.
ALTER TABLE members MODIFY COLUMN experience_rank INT NOT NULL DEFAULT -1;
