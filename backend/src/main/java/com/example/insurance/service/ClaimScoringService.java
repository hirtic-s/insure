package com.example.insurance.service;

import com.example.insurance.config.ClaimEventProducer;
import com.example.insurance.dto.ClaimScoredEvent;
import com.example.insurance.entity.Claim;
import com.example.insurance.entity.ClaimRiskScore;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.repository.ClaimRiskScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Scores a claim and records the outcome. Shared by the Kafka consumer and the
 * rescore job so the ML-outage fallback behaves identically on both paths.
 */
@Service
public class ClaimScoringService {

    private static final Logger log = LoggerFactory.getLogger(ClaimScoringService.class);

    private final FraudScoringLogic fraudScoringLogic;
    private final ClaimRiskScoreRepository claimRiskScoreRepository;
    private final ClaimRepository claimRepository;
    private final ClaimEventProducer claimEventProducer;

    @Autowired
    public ClaimScoringService(FraudScoringLogic fraudScoringLogic,
                               ClaimRiskScoreRepository claimRiskScoreRepository,
                               ClaimRepository claimRepository,
                               ClaimEventProducer claimEventProducer) {
        this.fraudScoringLogic = fraudScoringLogic;
        this.claimRiskScoreRepository = claimRiskScoreRepository;
        this.claimRepository = claimRepository;
        this.claimEventProducer = claimEventProducer;
    }

    public void scoreClaim(UUID claimId) {
        Claim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null) {
            log.warn("Claim {} not found, skipping scoring", claimId);
            return;
        }

        ScoringResult result = fraudScoringLogic.computeScore(claimId);

        if (!result.scored()) {
            // Fallback: no fabricated score. The claim is parked as UNSCORED so
            // it surfaces for manual review and the rescore job retries it.
            claim.setStatus(Claim.ClaimStatus.UNSCORED);
            claimRepository.save(claim);
            log.warn("Claim {} left UNSCORED: {}", claimId, result.factors());
            return;
        }

        Instant now = Instant.now();
        ClaimRiskScore riskScore = claimRiskScoreRepository.findByClaimId(claimId)
                .orElseGet(ClaimRiskScore::new);
        riskScore.setClaimId(claimId);
        riskScore.setRiskScore(result.score());
        riskScore.setRiskBand(result.band());
        riskScore.setTopFactors(String.join("; ", result.factors()));
        riskScore.setScoredAt(now);
        riskScore.setScoringLatencyMs(Duration.between(claim.getSubmittedAt(), now).toMillis());
        claimRiskScoreRepository.save(riskScore);

        claim.setStatus(Claim.ClaimStatus.SCORED);
        claimRepository.save(claim);

        ClaimScoredEvent scoredEvent = new ClaimScoredEvent();
        scoredEvent.setClaimId(claimId);
        scoredEvent.setRiskScore(result.score());
        scoredEvent.setTopFactors(String.join("; ", result.factors()));
        claimEventProducer.publishClaimScored(scoredEvent);

        log.info("Claim {} scored: risk={} in {}ms", claimId, result.score(),
                riskScore.getScoringLatencyMs());
    }
}
