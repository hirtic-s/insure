package com.example.insurance.config;

import com.example.insurance.dto.ClaimSubmittedEvent;
import com.example.insurance.service.ClaimScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClaimEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventConsumer.class);

    private final ClaimScoringService claimScoringService;

    @Autowired
    public ClaimEventConsumer(ClaimScoringService claimScoringService) {
        this.claimScoringService = claimScoringService;
    }

    // One consumer thread per partition of the 3-partition topic.
    @KafkaListener(topics = "claims-submitted", groupId = "insurance-group", concurrency = "3")
    public void onClaimSubmitted(ClaimSubmittedEvent event) {
        log.info("Received claim submitted event for claim {}", event.getClaimId());
        claimScoringService.scoreClaim(event.getClaimId());
    }
}
