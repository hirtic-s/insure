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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FraudScoringLogic {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringLogic.class);

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

        // The stored features are exactly the model's input columns.
        Map<String, Object> payload = claim.getFeatures();

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

            String band = (String) response.get("risk_band");

            return new ScoringResult(riskScore, band, topFactors, true);

        } catch (Exception e) {
            // ML service outage must NOT crash claim submission
            log.error("ML service call failed for claim {}: {}", claim.getId(), e.getMessage());
            return ScoringResult.unscored("Unable to score: ML service unavailable");
        }
    }
}
