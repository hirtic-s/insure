package com.example.insurance.dto;

import java.util.UUID;

public class ClaimScoredEvent {

    private UUID claimId;

    private double riskScore;

    private String topFactors;


    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public String getTopFactors() {
        return topFactors;
    }

    public void setTopFactors(String topFactors) {
        this.topFactors = topFactors;
    }
}
