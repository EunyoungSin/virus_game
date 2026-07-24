package com.checkpoint.game.dto;

import com.checkpoint.game.GameStatus;
import java.time.Instant;

public record GameSummaryResponse(
        Long gameId,
        GameStatus status,
        Integer currentDay,
        Integer processedToday,
        Integer totalProcessed,
        Integer trustScore,
        Integer testKitsRemaining,
        Integer infectedAdmittedSoFar,
        Instant createdAt,
        Instant updatedAt) {}
