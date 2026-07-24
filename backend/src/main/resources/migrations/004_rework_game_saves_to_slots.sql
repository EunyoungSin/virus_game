-- game_results: 이제 한 게임이 여러 번 완료될 수 있으므로(슬롯으로 되돌린 뒤 재도전) UNIQUE(game_id)를
-- 제거하고 조회용 인덱스만 유지한다.
ALTER TABLE game_results DROP CONSTRAINT IF EXISTS game_results_game_id_key;
CREATE INDEX IF NOT EXISTS idx_game_results_game_id ON game_results(game_id);

-- game_saves: "게임당 체크포인트 1개" 방식에서 "유저 전역 슬롯 5개 + 완전 스냅샷" 방식으로 전면 재설계.
-- 로컬 개발 DB라 기존 저장 데이터는 보존하지 않고 새 구조로 다시 만든다.
DROP TABLE IF EXISTS game_saves;
CREATE TABLE game_saves (
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
CREATE INDEX idx_game_saves_game_id ON game_saves(game_id);
