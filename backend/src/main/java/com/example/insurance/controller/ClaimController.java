package com.example.insurance.controller;

import com.example.insurance.dto.ClaimSubmissionRequest;
import com.example.insurance.dto.ClaimView;
import com.example.insurance.dto.ScorePoint;
import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.service.BriefingService;
import com.example.insurance.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

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
    public ClaimController(ClaimService claimService, ClaimRepository claimRepository, BriefingService briefingService) {
        this.claimService = claimService;
        this.claimRepository = claimRepository;
        this.briefingService = briefingService;
    }

    @PostMapping
    public ResponseEntity<Claim> submitClaim(@Valid @RequestBody ClaimSubmissionRequest request) {
        Claim claim = claimService.submitClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(claim);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badFeatures(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Risk-ranked claims for the reviewer dashboard. */
    @GetMapping
    public List<ClaimView> rankedClaims(@RequestParam(defaultValue = "100") int limit,
                                        @RequestParam(required = false) UUID batch) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE_SIZE)));
        return batch == null ? claimRepository.findRanked(page) : claimRepository.findRankedByBatch(batch, page);
    }

    /** Counts per risk band, plus the lowest score in each band (the cutoffs in effect). */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("total", claimRepository.count());
        out.put("high", claimRepository.countByRiskBand("HIGH"));
        out.put("medium", claimRepository.countByRiskBand("MEDIUM"));
        out.put("low", claimRepository.countByRiskBand("LOW"));
        out.put("unscored", claimRepository.countWithoutScore());
        out.put("highMin", claimRepository.minScoreInBand("HIGH"));
        out.put("mediumMin", claimRepository.minScoreInBand("MEDIUM"));
        return out;
    }

    /** Scores of every scored claim (up to 5000, highest first) for the distribution chart. */
    @GetMapping("/scores")
    public List<ScorePoint> scores() {
        return claimRepository.findScorePoints(PageRequest.of(0, 5000));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimView> getClaim(@PathVariable UUID id) {
        return claimRepository.findViewById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** A cited briefing drafted by the RAG service; cached there per claim, so refresh=true regenerates. */
    @PostMapping("/{id}/briefing")
    public ResponseEntity<Map<String, Object>> briefing(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "false") boolean refresh) {
        return briefingService.briefing(id, refresh)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Passes the RAG service's own message through (rate limit, missing key); unreachable is a 503. */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<Map<String, String>> ragRejected(HttpStatusCodeException e) {
        Map<String, Object> body = e.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {});
        Object detail = body == null ? null : body.get("detail");
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", detail == null ? "The briefing service returned an error." : detail.toString()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, String>> ragUnreachable(ResourceAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "The briefing service is unavailable or timed out. Try again shortly."));
    }
}
