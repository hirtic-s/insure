package com.example.insurance.dto;

import com.example.insurance.entity.Claim;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A claim joined with its fraud score (null until scored). */
public record ClaimView(
        UUID id,
        String policyNumber,
        String claimantName,
        String incidentDescription,
        Instant submittedAt,
        Claim.ClaimStatus status,
        Map<String, Object> features,
        Double riskScore,
        String riskBand,
        String topFactors,
        Long scoringLatencyMs,
        UUID batchId) {
}
