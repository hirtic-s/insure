package com.example.insurance.service;

import com.example.insurance.dto.ClaimSubmissionRequest;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.config.ClaimEventProducer;
import com.example.insurance.dto.ClaimScoredEvent;
import com.example.insurance.dto.ClaimSubmittedEvent;
import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimEventProducer claimEventProducer;

    @Autowired
    public ClaimService(ClaimRepository claimRepository, ClaimEventProducer claimEventProducer){
        this.claimRepository = claimRepository;
        this.claimEventProducer = claimEventProducer;
    }

    public Claim submitClaim(ClaimSubmissionRequest request){

        Claim claim = new Claim();
        claim.setPolicyNumber(request.getPolicyNumber());
        claim.setClaimantName(request.getClaimantName());
        claim.setIncidentDescription(request.getIncidentDescription());
        claim.setFeatures(ClaimFeatures.normalise(request.getFeatures()));
        claim.setSubmittedAt(Instant.now());
        claim.setStatus(Claim.ClaimStatus.SUBMITTED);

        Claim savedClaim = claimRepository.save(claim);

        ClaimSubmittedEvent event = new ClaimSubmittedEvent();
        event.setClaimId(savedClaim.getId());
        event.setPolicyNumber(savedClaim.getPolicyNumber());
        event.setSubmittedAt(savedClaim.getSubmittedAt());

        claimEventProducer.publishClaimSubmitted(event);

        return savedClaim;
    }

}
