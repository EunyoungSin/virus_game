package com.checkpoint.visitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "visitor_archetypes")
@Getter
@Setter
@NoArgsConstructor
public class VisitorArchetype {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "archetype_name", nullable = false, unique = true, length = 100)
    private String archetypeName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_pool", nullable = false, columnDefinition = "jsonb")
    private List<String> jobPool;

    @Column(name = "age_min", nullable = false)
    private Integer ageMin;

    @Column(name = "age_max", nullable = false)
    private Integer ageMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plausible_lie_reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> plausibleLieReasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personality_pool", nullable = false, columnDefinition = "jsonb")
    private List<String> personalityPool;

    @Column(name = "red_herring_type", nullable = false)
    private boolean redHerringType;
}
