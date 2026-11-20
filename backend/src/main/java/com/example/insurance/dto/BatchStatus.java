package com.example.insurance.dto;

import java.time.Instant;
import java.util.UUID;

public record BatchStatus(
        UUID id,
        String filename,
        int accepted,
        int rejected,
        String firstRejection,
        long scored,
        long unscored,
        Long medianScoringMs,
        Instant createdAt) {
}
