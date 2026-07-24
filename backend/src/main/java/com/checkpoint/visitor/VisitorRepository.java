package com.checkpoint.visitor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    long countByGameIdAndDayIndexAndDecisionIsNotNull(Long gameId, Integer dayIndex);

    Optional<Visitor> findFirstByGameIdAndDecisionIsNullOrderByDayIndexAscOrderInDayAsc(Long gameId);

    List<Visitor> findByGameId(Long gameId);

    long countByGameIdAndInfectedTrueAndDecision(Long gameId, Decision decision);

    void deleteByGameId(Long gameId);
}
