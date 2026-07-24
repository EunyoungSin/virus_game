package com.checkpoint.game;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    // 같은 게임이 여러 번 완료될 수 있으므로(슬롯으로 되돌린 뒤 재도전) 가장 최근 결과 1건만 조회한다.
    Optional<GameResult> findFirstByGameIdOrderByCreatedAtDesc(Long gameId);

    @Query(
            "SELECT r FROM GameResult r WHERE r.gameId IN (SELECT g.id FROM Game g WHERE g.userId = :userId) "
                    + "ORDER BY r.createdAt DESC")
    List<GameResult> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
