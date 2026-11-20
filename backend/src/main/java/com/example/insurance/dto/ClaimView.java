package com.example.insurance.dto;

import com.example.insurance.entity.Claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A claim joined with its fraud score (null until scored). */
public record ClaimView(
        UUID id,
        String policyNumber,
        String claimantName,
        String incidentDescription,
        BigDecimal claimAmount,
        Instant incidentDate,
        Instant submittedAt,
        Claim.ClaimStatus status,
        Integer driverAge,
        String gender,
        String maritalStatus,
        String accidentSite,
        Boolean witnessPresent,
        Boolean policeReportFiled,
        BigDecimal injuryClaim,
        Double riskScore,
        String topFactors,
        Long scoringLatencyMs) {
}
