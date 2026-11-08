package com.example.insurance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ClaimSubmittedEvent {

    private UUID claimId;
    private String policyNumber;
    private BigDecimal claimAmount;
    private Instant policyStartDate;
    private Instant incidentDate;
    private Instant submittedAt;

    public UUID getClaimId() {
        return claimId;
    }

    public void setClaimId(UUID claimId) {
        this.claimId = claimId;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public BigDecimal getClaimAmount() {
        return claimAmount;
    }

    public void setClaimAmount(BigDecimal claimAmount) {
        this.claimAmount = claimAmount;
    }

    public Instant getPolicyStartDate() {
        return policyStartDate;
    }

    public void setPolicyStartDate(Instant policyStartDate) {
        this.policyStartDate = policyStartDate;
    }

    public Instant getIncidentDate() {
        return incidentDate;
    }

    public void setIncidentDate(Instant incidentDate) {
        this.incidentDate = incidentDate;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

}