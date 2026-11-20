package com.example.insurance.dto;

import java.util.UUID;

/** One scored claim, for plotting the score distribution. */
public record ScorePoint(UUID id, double riskScore, UUID batchId) {
}
