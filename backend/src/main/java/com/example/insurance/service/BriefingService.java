package com.example.insurance.service;

import com.example.insurance.dto.ClaimView;
import com.example.insurance.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Asks the RAG service for a cited investigator briefing on a claim. */
@Service
public class BriefingService {

    private final ClaimRepository claimRepository;
    private final RestClient ragServiceRestClient;

    @Autowired
    public BriefingService(ClaimRepository claimRepository,
                           @Qualifier("ragServiceRestClient") RestClient ragServiceRestClient) {
        this.claimRepository = claimRepository;
        this.ragServiceRestClient = ragServiceRestClient;
    }

    public Optional<Map<String, Object>> briefing(UUID claimId) {
        return claimRepository.findViewById(claimId).map(this::requestBriefing);
    }

    private Map<String, Object> requestBriefing(ClaimView claim) {
        return ragServiceRestClient.post()
                .uri("/briefing")
                .contentType(MediaType.APPLICATION_JSON)
                .body(claim)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
