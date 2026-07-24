package com.checkpoint.visitor.dto;

import com.checkpoint.game.EndingType;
import com.checkpoint.game.dto.GameSummaryResponse;
import com.checkpoint.visitor.Decision;

public record DecisionResponse(
        Long visitorId,
        Decision decision,
        boolean correct,
        GameSummaryResponse game,
        EndingType endingType) {}
