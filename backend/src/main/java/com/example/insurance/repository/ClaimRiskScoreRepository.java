package com.example.insurance.repository;

import com.example.insurance.entity.Claim;
import com.example.insurance.entity.ClaimRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRiskScoreRepository extends JpaRepository<ClaimRiskScore, UUID> {
    Optional<ClaimRiskScore> findByClaimId(UUID claimId);

    List<ClaimRiskScore> findByRiskScoreGreaterThanEqualOrderByRiskScoreDesc(double threshold);
}
