package com.example.insurance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="claim_risk_scores")
public class ClaimRiskScore {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false)
    private double riskScore;

    @Column(length = 1000)
    private String topFactors;

    private Instant scoredAt = Instant.now();

    public ClaimRiskScore() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id){
        this.id = id;
    }

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

    public Instant getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(Instant scoredAt) {
        this.scoredAt = scoredAt;
    }





}
