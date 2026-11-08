package com.example.insurance.config;

import com.example.insurance.dto.ClaimScoredEvent;
import com.example.insurance.dto.ClaimSubmittedEvent;
import com.example.insurance.entity.Claim;
import com.example.insurance.entity.ClaimRiskScore;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.repository.ClaimRiskScoreRepository;
import com.example.insurance.service.FraudScoringLogic;
import com.example.insurance.service.ScoringResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ClaimEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventConsumer.class);

    private final FraudScoringLogic fraudScoringLogic;
    private final ClaimRiskScoreRepository claimRiskScoreRepository;
    private final ClaimRepository claimRepository;
    private final ClaimEventProducer claimEventProducer;

    @Autowired
    public ClaimEventConsumer(FraudScoringLogic fraudScoringLogic,
                              ClaimRiskScoreRepository claimRiskScoreRepository,
                              ClaimRepository claimRepository,
                              ClaimEventProducer claimEventProducer) {
        this.fraudScoringLogic = fraudScoringLogic;
        this.claimRiskScoreRepository = claimRiskScoreRepository;
        this.claimRepository = claimRepository;
        this.claimEventProducer = claimEventProducer;
    }

    @KafkaListener(topics = "claims-submitted", groupId = "insurance-group")
    public void onClaimSubmitted(ClaimSubmittedEvent event) {
        log.info("Received claim submitted event for claim {}", event.getClaimId());

        ScoringResult result = fraudScoringLogic.computeScore(event);

        // Persist the risk score
        ClaimRiskScore riskScore = new ClaimRiskScore();
        riskScore.setClaimId(event.getClaimId());
        riskScore.setRiskScore(result.score());
        riskScore.setTopFactors(String.join("; ", result.factors()));
        riskScore.setScoredAt(Instant.now());
        claimRiskScoreRepository.save(riskScore);

        // Update claim status to SCORED
        claimRepository.findById(event.getClaimId()).ifPresent(claim -> {
            claim.setStatus(Claim.ClaimStatus.SCORED);
            claimRepository.save(claim);
        });

        // Publish scored event
        ClaimScoredEvent scoredEvent = new ClaimScoredEvent();
        scoredEvent.setClaimId(event.getClaimId());
        scoredEvent.setRiskScore(result.score());
        scoredEvent.setTopFactors(String.join("; ", result.factors()));
        claimEventProducer.publishClaimScored(scoredEvent);

        log.info("Claim {} scored: risk={}, factors={}", event.getClaimId(),
                result.score(), result.factors());
    }
}
