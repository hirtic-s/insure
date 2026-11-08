package com.example.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public class ClaimSubmissionRequest {

    @NotBlank
    private String policyNumber;

    @NotBlank
    private String claimantName;

    private String incidentDescription;

    @NotNull
    @Positive
    private BigDecimal claimAmount;

    @NotNull
    private Instant policyStartDate;

    @NotNull
    private Instant incidentDate;

    private String documentS3Key;

    @NotNull
    @Positive
    private Integer driverAge;

    @NotBlank
    private String gender;

    @NotBlank
    private String maritalStatus;

    @NotBlank
    private String accidentSite;

    @NotNull
    private Boolean witnessPresent;

    @NotNull
    private Boolean policeReportFiled;

    private String vehicleColor;

    @Positive
    private BigDecimal injuryClaim;

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

    public String getDocumentS3Key() {
        return documentS3Key;
    }

    public void setDocumentS3Key(String documentS3Key) {
        this.documentS3Key = documentS3Key;
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
