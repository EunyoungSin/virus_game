-- 이미 구동 중인 DB에 증상 관련 컬럼을 추가하는 마이그레이션.
-- schema.sql은 CREATE TABLE IF NOT EXISTS만 적용하므로 기존 테이블에는 컬럼을 추가해주지 않는다 —
-- 신규 설치는 schema.sql만으로 충분하지만, 이미 visitors 테이블이 있는 환경은 이 스크립트를
-- 수동으로 실행해야 한다. 전부 IF NOT EXISTS라 여러 번 실행해도 안전(idempotent)하다.
--
-- 실행 예시:
--   psql "$DATABASE_URL" -f backend/src/main/resources/migrations/002_add_symptom_columns.sql

ALTER TABLE visitors
    ADD COLUMN IF NOT EXISTS has_symptom BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS symptom_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS symptom_reason VARCHAR(100);
