package com.example.insurance.controller;

import com.example.insurance.dto.ClaimSubmissionRequest;
import com.example.insurance.dto.ClaimView;
import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.service.BriefingService;
import com.example.insurance.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
@CrossOrigin(origins = "*")
public class ClaimController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ClaimService claimService;
    private final ClaimRepository claimRepository;
    private final BriefingService briefingService;

    @Autowired
    public ClaimController(ClaimService claimService, ClaimRepository claimRepository,
                           BriefingService briefingService) {
        this.claimService = claimService;
        this.claimRepository = claimRepository;
        this.briefingService = briefingService;
    }

    @PostMapping
    public ResponseEntity<Claim> submitClaim(@Valid @RequestBody ClaimSubmissionRequest request) {
        Claim claim = claimService.submitClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(claim);
    }

    /** Risk-ranked claims for the reviewer dashboard. */
    @GetMapping
    public List<ClaimView> rankedClaims(@RequestParam(defaultValue = "100") int limit) {
        return claimRepository.findRanked(PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE_SIZE))));
    }

    /** Counts per risk band for the dashboard header. */
    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "total", claimRepository.count(),
                "high", claimRepository.countInBand(0.6, 2.0),
                "medium", claimRepository.countInBand(0.35, 0.6),
                "low", claimRepository.countInBand(0.0, 0.35),
                "unscored", claimRepository.countWithoutScore());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimView> getClaim(@PathVariable UUID id) {
        return claimRepository.findViewById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/briefing")
    public ResponseEntity<Map<String, Object>> briefing(@PathVariable UUID id) {
        try {
            return briefingService.briefing(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Briefing service unavailable"));
        }
    }
}
