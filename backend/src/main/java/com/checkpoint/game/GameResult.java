package com.checkpoint.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_results")
@Getter
@Setter
@NoArgsConstructor
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이제 UNIQUE가 아니다: 같은 게임이 슬롯으로 되돌아간 뒤 재도전해 여러 번 완료될 수 있다.
    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ending_type", nullable = false, length = 20)
    private EndingType endingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ending_reason", length = 30)
    private EndingReason endingReason;

    @Column(name = "infected_admitted", nullable = false)
    private Integer infectedAdmitted;

    @Column(name = "innocent_rejected", nullable = false)
    private Integer innocentRejected;

    @Column(name = "total_processed", nullable = false)
    private Integer totalProcessed;

    @Column(name = "final_trust_score", nullable = false)
    private Integer finalTrustScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GameResult(
            Long gameId,
            EndingType endingType,
            EndingReason endingReason,
            Integer infectedAdmitted,
            Integer innocentRejected,
            Integer totalProcessed,
            Integer finalTrustScore) {
        this.gameId = gameId;
        this.endingType = endingType;
        this.endingReason = endingReason;
        this.infectedAdmitted = infectedAdmitted;
        this.innocentRejected = innocentRejected;
        this.totalProcessed = totalProcessed;
        this.finalTrustScore = finalTrustScore;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
