package com.example.insurance.controller;

import com.example.insurance.dto.ClaimSubmissionRequest;
import com.example.insurance.entity.Claim;
import com.example.insurance.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
@CrossOrigin(origins = "*")
public class ClaimController {

    private final ClaimService claimService;

    @Autowired
    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<Claim> submitClaim(@Valid @RequestBody ClaimSubmissionRequest request) {
        Claim claim = claimService.submitClaim(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(claim);
    }
}