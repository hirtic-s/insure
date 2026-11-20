package com.example.insurance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String policyNumber;

    @Column(nullable = false)
    private String claimantName;

    @Column(length = 2000)
    private String incidentDescription;

    @Column(nullable = false)
    private BigDecimal claimAmount;

    @Column(nullable = false)
    private Instant policyStartDate;

    @Column(nullable = false)
    private Instant incidentDate;

    @Column(nullable = false)
    private Instant submittedAt;

    private String documentS3Key;

    @Column(nullable = false)
    private Integer driverAge;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private String maritalStatus;

    @Column(nullable = false)
    private String accidentSite;

    @Column(nullable = false)
    private Boolean witnessPresent;

    @Column(nullable = false)
    private Boolean policeReportFiled;

    private String vehicleColor;

    private BigDecimal injuryClaim;

    /** Set when the claim arrived through a file upload. */
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    public enum ClaimStatus{
        SUBMITTED, SCORED, UNSCORED, UNDER_REVIEW, APPROVED, ESCALATED, DENIED
    }

    public Claim() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getIncidentDescription() {
        return incidentDescription;
    }

    public void setIncidentDescription(String incidentDescription) {
        this.incidentDescription = incidentDescription;
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

    public String getDocumentS3Key() {
        return documentS3Key;
    }

    public void setDocumentS3Key(String documentS3Key) {
        this.documentS3Key = documentS3Key;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public Integer getDriverAge() {
        return driverAge;
    }

    public void setDriverAge(Integer driverAge) {
        this.driverAge = driverAge;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getAccidentSite() {
        return accidentSite;
    }

    public void setAccidentSite(String accidentSite) {
        this.accidentSite = accidentSite;
    }

    public Boolean getWitnessPresent() {
        return witnessPresent;
    }

    public void setWitnessPresent(Boolean witnessPresent) {
        this.witnessPresent = witnessPresent;
    }

    public Boolean getPoliceReportFiled() {
        return policeReportFiled;
    }

    public void setPoliceReportFiled(Boolean policeReportFiled) {
        this.policeReportFiled = policeReportFiled;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public void setVehicleColor(String vehicleColor) {
        this.vehicleColor = vehicleColor;
    }

    public BigDecimal getInjuryClaim() {
        return injuryClaim;
    }

    public void setInjuryClaim(BigDecimal injuryClaim) {
        this.injuryClaim = injuryClaim;
    }
}
