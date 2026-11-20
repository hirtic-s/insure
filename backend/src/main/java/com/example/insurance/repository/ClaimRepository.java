package com.example.insurance.repository;


import com.example.insurance.dto.ClaimView;
import com.example.insurance.dto.ScorePoint;
import com.example.insurance.entity.Claim;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim,UUID>{

    long countByBatchId(UUID batchId);

    long countByBatchIdAndStatus(UUID batchId, Claim.ClaimStatus status);

    @Query("select s.scoringLatencyMs from Claim c join ClaimRiskScore s on s.claimId = c.id "
            + "where c.batchId = :batchId and s.scoringLatencyMs is not null")
    List<Long> findScoringLatencies(UUID batchId);

    @Query("select count(c) from Claim c join ClaimRiskScore s on s.claimId = c.id where s.riskBand = :band")
    long countByRiskBand(String band);

    @Query("select count(c) from Claim c left join ClaimRiskScore s on s.claimId = c.id where s.id is null")
    long countWithoutScore();

    List<Claim> findTop50ByStatusOrderBySubmittedAtAsc(Claim.ClaimStatus status);

    String VIEW_SELECT = """
            select new com.example.insurance.dto.ClaimView(
                c.id, c.policyNumber, c.claimantName, c.incidentDescription, c.submittedAt,
                c.status, c.features, s.riskScore, s.riskBand, s.topFactors, s.scoringLatencyMs, c.batchId)
            from Claim c left join ClaimRiskScore s on s.claimId = c.id
            """;

    /** Highest risk first; unscored claims sort last. */
    @Query(VIEW_SELECT + " order by s.riskScore desc nulls last, c.submittedAt desc")
    List<ClaimView> findRanked(Pageable pageable);

    @Query("select new com.example.insurance.dto.ScorePoint(c.id, s.riskScore, c.batchId) "
            + "from Claim c join ClaimRiskScore s on s.claimId = c.id order by s.riskScore desc")
    List<ScorePoint> findScorePoints(Pageable pageable);

    @Query("select min(s.riskScore) from ClaimRiskScore s where s.riskBand = :band")
    Double minScoreInBand(String band);

    @Query(VIEW_SELECT + " where c.batchId = :batchId order by s.riskScore desc nulls last, c.submittedAt desc")
    List<ClaimView> findRankedByBatch(UUID batchId, Pageable pageable);

    @Query(VIEW_SELECT + " where c.id = :id")
    Optional<ClaimView> findViewById(UUID id);
}
