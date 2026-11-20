package com.example.insurance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One uploaded claims file. */
@Entity
@Table(name = "claim_batches")
public class ClaimBatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private int acceptedRows;

    @Column(nullable = false)
    private int rejectedRows;

    @Column(length = 1000)
    private String firstRejection;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public int getAcceptedRows() { return acceptedRows; }
    public void setAcceptedRows(int acceptedRows) { this.acceptedRows = acceptedRows; }
    public int getRejectedRows() { return rejectedRows; }
    public void setRejectedRows(int rejectedRows) { this.rejectedRows = rejectedRows; }
    public String getFirstRejection() { return firstRejection; }
    public void setFirstRejection(String firstRejection) { this.firstRejection = firstRejection; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
