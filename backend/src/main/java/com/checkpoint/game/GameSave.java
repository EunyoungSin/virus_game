package com.checkpoint.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 저장 슬롯은 게임이 아니라 유저 전역 자원이다(유저당 최대 5개). 슬롯마다 어느 게임의 어느
// 시점인지(gameId)와 그 시점의 완전한 스냅샷(판정/대화 포함)을 독립적으로 들고 있다.
@Entity
@Table(name = "game_saves")
@Getter
@Setter
@NoArgsConstructor
public class GameSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "slot_no", nullable = false)
    private Integer slotNo;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "current_day", nullable = false)
    private Integer currentDay;

    @Column(name = "current_visitor_index", nullable = false)
    private Integer currentVisitorIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resources_left", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resourcesLeft;

    @Column(name = "trust_score", nullable = false)
    private Integer trustScore;

    // [{"visitorId":101,"decision":"ADMIT","decidedAt":"..."}, ...] 저장 시점까지의 전체 판정 내역
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visitor_decisions_snapshot", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> visitorDecisionsSnapshot;

    // [{"visitorId":101,"turnNo":1,"topicTag":"...","question":"...","answer":"...","createdAt":"..."}, ...]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversations_snapshot", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> conversationsSnapshot;
}
