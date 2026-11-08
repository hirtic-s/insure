package com.example.insurance.repository;

import com.example.insurance.entity.Claim;
import com.example.insurance.entity.ClaimRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimRiskScoreRepository extends JpaRepository<ClaimRiskScore, UUID> {
    List<ClaimRiskScore> findByRiskScoreGreaterThanEqualOrderByRiskScoreDesc(double threshold);
}
