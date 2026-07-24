package com.checkpoint.visitor.dto;

import com.checkpoint.visitor.Decision;
import jakarta.validation.constraints.NotNull;

public record DecisionRequest(@NotNull Decision decision) {}
