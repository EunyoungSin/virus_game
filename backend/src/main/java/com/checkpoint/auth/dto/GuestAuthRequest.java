package com.checkpoint.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GuestAuthRequest(@NotBlank String deviceId) {}
