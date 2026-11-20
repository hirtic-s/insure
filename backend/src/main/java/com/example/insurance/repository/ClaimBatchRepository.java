package com.example.insurance.repository;

import com.example.insurance.entity.ClaimBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimBatchRepository extends JpaRepository<ClaimBatch, UUID> {
}
