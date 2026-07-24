package com.checkpoint.game.dto;

import com.checkpoint.game.EndingReason;
import com.checkpoint.game.EndingType;
import com.checkpoint.game.GameStatus;
import java.time.Instant;

public record SaveSlotResponse(
        Integer slotNo,
        Boolean occupied,
        Long gameId,
        Integer day,
        Integer trustScore,
        Instant savedAt,
        GameStatus gameStatus,
        EndingType endingType,
        EndingReason endingReason) {

    public static SaveSlotResponse empty(int slotNo) {
        return new SaveSlotResponse(slotNo, false, null, null, null, null, null, null, null);
    }
}
