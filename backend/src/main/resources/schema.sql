CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE NOT NULL, -- 클라이언트가 생성한 UUID v4, 게스트 식별자
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_device_id ON users(device_id);

CREATE TABLE IF NOT EXISTS visitor_archetypes (
    id BIGSERIAL PRIMARY KEY,
    archetype_name VARCHAR(100) NOT NULL UNIQUE,
    job_pool JSONB NOT NULL,               -- ["농부","축산업자"]
    age_min INT NOT NULL,
    age_max INT NOT NULL,
    plausible_lie_reasons JSONB NOT NULL,  -- ["감염은폐","신분위조"]
    personality_pool JSONB NOT NULL,       -- ["침착","불안"]
    red_herring_type BOOLEAN NOT NULL DEFAULT false
);

-- MVP 아키타입 1~8번
INSERT INTO visitor_archetypes
    (archetype_name, job_pool, age_min, age_max, plausible_lie_reasons, personality_pool, red_herring_type)
VALUES
    ('이주 농민', '["농부","축산업자","과수원 일꾼"]', 20, 55, '["감염은폐","신분위조"]', '["침착","불안"]', false),
    ('도시 상인', '["잡화상","노점상","행상"]', 25, 55, '["밀수","세금회피","감염은폐"]', '["뻔뻔"]', false),
    ('가족 동반(부모)', '["회사원","교사","자영업자","공무원"]', 25, 45, '["감염은폐","겁먹음"]', '["불안"]', false),
    ('파견 군인/경비', '["군인","경비원"]', 20, 35, '["탈영","명령위반","감염은폐"]', '["침착"]', false),
    ('의료·연구 관계자', '["연구원","간호사"]', 25, 50, '["신분위조","감염은폐"]', '["뻔뻔","침착"]', false),
    ('계절 이주노동자', '["공사장 인부","계절공"]', 18, 45, '["밀입국","감염은폐"]', '["불안"]', false),
    ('상류층 인사', '["사업가","관료"]', 30, 60, '["특권남용","뇌물시도","감염은폐"]', '["뻔뻔"]', false),
    ('노약자', '["은퇴자"]', 60, 80, '["겁먹음","감염은폐"]', '["불안","솔직"]', false)
ON CONFLICT (archetype_name) DO NOTHING;

-- 아키타입 9~12번. red_herring_type=true(수배자/언론·기자/밀입국 브로커 동행자)은 감염과 무관한
-- 사유로 거짓말을 하는 쪽으로 치우친 아키타입일 뿐, 감염 여부 자체는 다른 아키타입과 동일하게
-- 매 게임 무작위로 배정된다(감염자 고정 없음). 성직자/구호단체는 plausible_lie_reasons가
-- "감염은폐" 하나뿐이라 감염과 무관한 거짓말이 사실상 발생하지 않는 "솔직" 캐릭터다.
INSERT INTO visitor_archetypes
    (archetype_name, job_pool, age_min, age_max, plausible_lie_reasons, personality_pool, red_herring_type)
VALUES
    ('수배자', '["일용직 노동자","행상인","막노동꾼"]', 25, 50, '["수배","신분위조","감염은폐"]', '["침착"]', true),
    ('언론/기자', '["기자","방송인"]', 25, 45, '["취재목적 잠입","감염은폐"]', '["뻔뻔"]', true),
    ('성직자/구호단체', '["성직자","구호요원"]', 30, 60, '["감염은폐"]', '["솔직"]', false),
    ('밀입국 브로커 동행자', '["가정부","농장 일꾼","공장 노동자"]', 18, 40, '["밀입국","신분위조","감염은폐"]', '["불안","뻔뻔"]', true)
ON CONFLICT (archetype_name) DO NOTHING;

CREATE TABLE IF NOT EXISTS games (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    code VARCHAR(20) UNIQUE,
    status VARCHAR(20) NOT NULL, -- IN_PROGRESS, PAUSED, FINISHED
    current_day INT NOT NULL DEFAULT 1,
    current_visitor_index INT NOT NULL DEFAULT 0,
    resources_left JSONB NOT NULL DEFAULT '{"testKit": 3}',
    trust_score INT NOT NULL DEFAULT 100,
    ending_type VARCHAR(20), -- BEST, NORMAL, BAD (nullable, 종료 시에만)
    ending_reason VARCHAR(30), -- INFECTION_SPREAD, TRUST_COLLAPSE, IDLE_TIMEOUT (BAD 계열에서만)
    last_action_at TIMESTAMP, -- 마지막 실질 행동(대화/판정/검사키트/저장/불러오기) 시각
    last_heartbeat_at TIMESTAMP, -- 마지막 하트비트 수신 시각 (게임 화면이 열려있었는지 판단용)
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    finished_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_games_user_status ON games(user_id, status);

CREATE TABLE IF NOT EXISTS visitors (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    archetype_id BIGINT NOT NULL REFERENCES visitor_archetypes(id),
    day_index INT NOT NULL,
    order_in_day INT NOT NULL,

    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    job_claimed VARCHAR(100),
    origin_city VARCHAR(100),
    travel_history JSONB, -- [{"city":"...", "date":"..."}]

    -- 숨겨진 진실 (API 응답에서 절대 노출 금지)
    infected BOOLEAN NOT NULL,
    infection_stage VARCHAR(20), -- NONE, EARLY, INCUBATION, LATE
    exposure_point VARCHAR(255),
    has_unrelated_lie BOOLEAN NOT NULL DEFAULT false,
    lie_reason VARCHAR(100),
    lie_detail TEXT,

    -- 증상: 감염 여부와 별도 확률로 생성 시점에 확정, 대화 내내 이 값을 그대로 연기한다
    has_symptom BOOLEAN NOT NULL DEFAULT false,
    symptom_type VARCHAR(50),      -- 'COUGH' 또는 'FEVER'만 허용 (감염 증상 3종 중 물리적 2종)
    symptom_reason VARCHAR(100),   -- 감염 관련 또는 무관 사유(알레르기/흡연/과로/긴장 등)

    personality_trait VARCHAR(50),

    decision VARCHAR(10), -- ADMIT, REJECT (nullable, 판정 전 null)
    decided_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    visitor_id BIGINT NOT NULL REFERENCES visitors(id),
    turn_no INT NOT NULL,
    topic_tag VARCHAR(20), -- TRAVEL, JOB, CONTACT, SYMPTOM, OTHER
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_conversations_game_visitor ON conversations(game_id, visitor_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_game_visitor_turn
    ON conversations(game_id, visitor_id, turn_no);

-- game_id는 UNIQUE가 아니다: 슬롯으로 되돌린 뒤 재도전하면 같은 게임이 여러 번
-- 완료될 수 있고, 그때마다 새 row가 쌓인다(덮어쓰지 않음).
CREATE TABLE IF NOT EXISTS game_results (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL REFERENCES games(id),
    ending_type VARCHAR(20) NOT NULL,
    ending_reason VARCHAR(30), -- INFECTION_SPREAD, TRUST_COLLAPSE, IDLE_TIMEOUT (BAD 계열에서만)
    infected_admitted INT NOT NULL,
    innocent_rejected INT NOT NULL,
    total_processed INT NOT NULL,
    final_trust_score INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_game_results_game_id ON game_results(game_id);

-- 저장 슬롯은 게임 단위가 아니라 유저 전역 자원(최대 5개)이다. 슬롯마다 어느 게임의
-- 어느 시점인지(game_id)와 그 시점의 완전한 스냅샷(판정/대화 포함)을 독립적으로 보관한다.
-- 같은 게임이 슬롯을 여러 개 차지할 수도, 서로 다른 게임이 슬롯을 나눠 가질 수도 있다.
CREATE TABLE IF NOT EXISTS game_saves (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    slot_no INT NOT NULL,
    game_id BIGINT NOT NULL REFERENCES games(id),
    saved_at TIMESTAMP NOT NULL DEFAULT now(),
    current_day INT NOT NULL,
    current_visitor_index INT NOT NULL,
    resources_left JSONB NOT NULL,
    trust_score INT NOT NULL,
    visitor_decisions_snapshot JSONB NOT NULL,
    conversations_snapshot JSONB NOT NULL,
    UNIQUE (user_id, slot_no)
);
CREATE INDEX IF NOT EXISTS idx_game_saves_game_id ON game_saves(game_id);
