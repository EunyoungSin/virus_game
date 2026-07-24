package com.checkpoint.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveSlotRequest(@NotNull @Min(1) @Max(5) Integer slotNo) {}
