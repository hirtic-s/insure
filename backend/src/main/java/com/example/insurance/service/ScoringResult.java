package com.example.insurance.service;

import java.util.List;

/**
 * Outcome of a scoring attempt. {@code scored} is false when the ML service
 * could not be reached; callers must not persist {@code score} in that case.
 */
public record ScoringResult(double score, List<String> factors, boolean scored) {

    public static ScoringResult unscored(String reason) {
        return new ScoringResult(0.0, List.of(reason), false);
    }
}
