package com.example.insurance.service;

import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FraudScoringLogic {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringLogic.class);

    // Training-data median defaults for fields not yet available from real
    // policy/driver profile data sources.  These come from
    // ml-service/model/column_metadata.json → numeric_medians.
    // Replace with real lookups once policy and driver profile tables exist.
    private static final double DEFAULT_SAFETY_RATING = 76.0;
    private static final double DEFAULT_ANNUAL_INCOME = 60899.2;
    private static final int DEFAULT_HIGH_EDUCATION = 1;
    private static final int DEFAULT_ADDRESS_CHANGE = 1;
    private static final String DEFAULT_PROPERTY_STATUS = "Own";
    private static final int DEFAULT_PAST_NUM_OF_CLAIMS = 0;
    private static final double DEFAULT_AGE_OF_VEHICLE = 5.0;
    private static final String DEFAULT_VEHICLE_CATEGORY = "Medium";
    private static final double DEFAULT_VEHICLE_PRICE = 20642.03;
    private static final double DEFAULT_POLICY_DEDUCTIBLE = 1000.0;
    private static final double DEFAULT_ANNUAL_PREMIUM = 1239.06;
    private static final String DEFAULT_CHANNEL = "Online";

    private final RestClient mlServiceRestClient;
    private final ClaimRepository claimRepository;

    @Autowired
    public FraudScoringLogic(RestClient mlServiceRestClient, ClaimRepository claimRepository) {
        this.mlServiceRestClient = mlServiceRestClient;
        this.claimRepository = claimRepository;
    }

    public ScoringResult computeScore(UUID claimId) {
        // The event is a thin notification — look up the full Claim entity.
        Claim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) {
            log.warn("Claim {} not found in DB, cannot score", claimId);
            return ScoringResult.unscored("Unable to score: claim not found");
        }

        Map<String, Object> payload = buildPayload(claim);

        try {
            Map<String, Object> response = mlServiceRestClient.post()
                    .uri("/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null) {
                log.warn("ML service returned null response for claim {}", claim.getId());
                return ScoringResult.unscored("Unable to score: empty response");
            }

            double riskScore = ((Number) response.get("risk_score")).doubleValue();

            @SuppressWarnings("unchecked")
            List<String> topFactors = (List<String>) response.get("top_factors");

            return new ScoringResult(riskScore, topFactors, true);

        } catch (Exception e) {
            // ML service outage must NOT crash claim submission
            log.error("ML service call failed for claim {}: {}", claim.getId(), e.getMessage());
            return ScoringResult.unscored("Unable to score: ML service unavailable");
        }
    }

    private Map<String, Object> buildPayload(Claim claim) {
        Map<String, Object> payload = new HashMap<>();

        // Fields available from the Claim entity (claimant-provided)
        payload.put("age_of_driver", claim.getDriverAge());
        payload.put("gender", claim.getGender());
        payload.put("marital_status", claim.getMaritalStatus());
        payload.put("accident_site", claim.getAccidentSite());
        payload.put("witness_present", claim.getWitnessPresent() ? 1 : 0);
        payload.put("police_report", claim.getPoliceReportFiled() ? 1 : 0);
        payload.put("vehicle_color", claim.getVehicleColor() != null
                ? claim.getVehicleColor() : "other");
        payload.put("total_claim", claim.getClaimAmount().doubleValue());
        payload.put("injury_claim", claim.getInjuryClaim() != null
                ? claim.getInjuryClaim().doubleValue() : 0.0);

        // Derived from incident date
        String dayOfWeek = claim.getIncidentDate()
                .atZone(ZoneOffset.UTC)
                .getDayOfWeek()
                .name();
        // ML model expects title case (e.g. "Monday"), DayOfWeek gives "MONDAY"
        payload.put("claim_day_of_week",
                dayOfWeek.substring(0, 1) + dayOfWeek.substring(1).toLowerCase());

        // Training-data median defaults — replace with real data source lookups
        payload.put("safety_rating", DEFAULT_SAFETY_RATING);
        payload.put("annual_income", DEFAULT_ANNUAL_INCOME);
        payload.put("high_education", DEFAULT_HIGH_EDUCATION);
        payload.put("address_change", DEFAULT_ADDRESS_CHANGE);
        payload.put("property_status", DEFAULT_PROPERTY_STATUS);
        payload.put("past_num_of_claims", DEFAULT_PAST_NUM_OF_CLAIMS);
        payload.put("age_of_vehicle", DEFAULT_AGE_OF_VEHICLE);
        payload.put("vehicle_category", DEFAULT_VEHICLE_CATEGORY);
        payload.put("vehicle_price", DEFAULT_VEHICLE_PRICE);
        payload.put("channel", DEFAULT_CHANNEL);

        // Keys with spaces — must match the FastAPI pydantic aliases exactly
        payload.put("policy deductible", DEFAULT_POLICY_DEDUCTIBLE);
        payload.put("annual premium", DEFAULT_ANNUAL_PREMIUM);

        return payload;
    }
}
