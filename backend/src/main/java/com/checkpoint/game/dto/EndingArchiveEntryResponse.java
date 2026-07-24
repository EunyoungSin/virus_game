package com.checkpoint.game.dto;

import com.checkpoint.game.EndingReason;
import com.checkpoint.game.EndingType;
import java.time.Instant;

public record EndingArchiveEntryResponse(
        Long gameId,
        EndingType endingType,
        EndingReason endingReason,
        Integer totalProcessed,
        Integer infectedAdmitted,
        Integer innocentRejected,
        Integer finalTrustScore,
        Instant finishedAt) {}
