package com.example.insurance.service;

import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-attempts scoring for claims parked during an ML-service outage. */
@Component
public class UnscoredClaimRetryJob {

    private final ClaimRepository claimRepository;
    private final ClaimScoringService claimScoringService;

    @Autowired
    public UnscoredClaimRetryJob(ClaimRepository claimRepository,
                                 ClaimScoringService claimScoringService) {
        this.claimRepository = claimRepository;
        this.claimScoringService = claimScoringService;
    }

    @Scheduled(fixedDelayString = "${scoring.retry-interval-ms:30000}")
    public void retryUnscored() {
        claimRepository.findTop50ByStatusOrderBySubmittedAtAsc(Claim.ClaimStatus.UNSCORED)
                .forEach(c -> claimScoringService.scoreClaim(c.getId()));
    }
}
