package com.checkpoint.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.IN_PROGRESS;

    @Column(name = "current_day", nullable = false)
    private Integer currentDay = 1;

    @Column(name = "current_visitor_index", nullable = false)
    private Integer currentVisitorIndex = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resources_left", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> resourcesLeft = new HashMap<>(Map.of("testKit", 3));

    @Column(name = "trust_score", nullable = false)
    private Integer trustScore = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "ending_type", length = 20)
    private EndingType endingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ending_reason", length = 30)
    private EndingReason endingReason;

    @Column(name = "last_action_at")
    private Instant lastActionAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Game(Long userId) {
        this.userId = userId;
    }

    public int getTestKitsRemaining() {
        Object value = resourcesLeft.get("testKit");
        return value instanceof Number number ? number.intValue() : 0;
    }

    public void touchAction(Instant now) {
        this.lastActionAt = now;
        this.lastHeartbeatAt = now;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
