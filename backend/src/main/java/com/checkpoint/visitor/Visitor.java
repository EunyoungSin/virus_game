package com.checkpoint.visitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "visitors")
@Getter
@Setter
@NoArgsConstructor
public class Visitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "archetype_id", nullable = false)
    private Long archetypeId;

    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    @Column(name = "order_in_day", nullable = false)
    private Integer orderInDay;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(name = "job_claimed", length = 100)
    private String jobClaimed;

    @Column(name = "origin_city", length = 100)
    private String originCity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "travel_history", columnDefinition = "jsonb")
    private List<Map<String, Object>> travelHistory;

    @Column(nullable = false)
    private boolean infected;

    @Enumerated(EnumType.STRING)
    @Column(name = "infection_stage", length = 20)
    private InfectionStage infectionStage;

    @Column(name = "exposure_point", length = 255)
    private String exposurePoint;

    @Column(name = "has_unrelated_lie", nullable = false)
    private boolean hasUnrelatedLie = false;

    @Column(name = "lie_reason", length = 100)
    private String lieReason;

    @Column(name = "lie_detail")
    private String lieDetail;

    @Column(name = "has_symptom", nullable = false)
    private boolean hasSymptom = false;

    @Column(name = "symptom_type", length = 50)
    private String symptomType;

    @Column(name = "symptom_reason", length = 100)
    private String symptomReason;

    @Column(name = "personality_trait", length = 50)
    private String personalityTrait;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Decision decision;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
