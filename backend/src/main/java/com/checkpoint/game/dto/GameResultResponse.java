package com.checkpoint.game.dto;

import com.checkpoint.game.EndingReason;
import com.checkpoint.game.EndingType;
import java.time.Instant;

public record GameResultResponse(
        Long gameId,
        EndingType endingType,
        EndingReason endingReason,
        Integer infectedAdmitted,
        Integer innocentRejected,
        Integer totalProcessed,
        Integer finalTrustScore,
        Instant createdAt) {}
