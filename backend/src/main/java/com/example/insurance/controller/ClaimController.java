package com.example.insurance.controller;

import com.example.insurance.dto.ClaimSubmissionRequest;
import com.example.insurance.dto.ClaimView;
import com.example.insurance.dto.ScorePoint;
import com.example.insurance.entity.Claim;
import com.example.insurance.repository.ClaimRepository;
import com.example.insurance.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public ClaimController(ClaimService claimService, ClaimRepository claimRepository) {
        this.claimService = claimService;
        this.claimRepository = claimRepository;
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
}
