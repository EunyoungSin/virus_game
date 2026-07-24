package com.checkpoint.game;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSaveRepository extends JpaRepository<GameSave, Long> {

    List<GameSave> findByUserId(Long userId);

    Optional<GameSave> findByUserIdAndSlotNo(Long userId, Integer slotNo);

    // 같은 게임이 여러 슬롯을 차지할 수 있으므로 Optional이 아니라 List.
    List<GameSave> findByGameId(Long gameId);

    void deleteByGameId(Long gameId);
}
