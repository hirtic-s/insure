package com.example.insurance.service;

import com.example.insurance.config.ClaimEventProducer;
import com.example.insurance.dto.BatchStatus;
import com.example.insurance.dto.ClaimSubmittedEvent;
import com.example.insurance.entity.Claim;
import com.example.insurance.entity.ClaimBatch;
import com.example.insurance.repository.ClaimBatchRepository;
import com.example.insurance.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns an uploaded claims CSV into submitted claims. Each accepted row is saved
 * and published to Kafka exactly like a claim submitted through the API, so
 * scoring stays asynchronous and goes through the same pipeline.
 */
@Service
public class BatchService {

    public static final int MAX_ROWS = 25_000;
    // CSV headers arrive lower-cased from the parser; map them back to the model's column names.
    private static final Map<String, String> CANONICAL = ClaimFeatures.ALL.stream()
            .collect(Collectors.toMap(c -> c.toLowerCase(Locale.ROOT), c -> c));

    private final ClaimRepository claimRepository;
    private final ClaimBatchRepository batchRepository;
    private final ClaimEventProducer producer;

    @Autowired
    public BatchService(ClaimRepository claimRepository, ClaimBatchRepository batchRepository,
                        ClaimEventProducer producer) {
        this.claimRepository = claimRepository;
        this.batchRepository = batchRepository;
        this.producer = producer;
    }

    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) { super(message); }
    }

    public ClaimBatch ingest(MultipartFile file) throws IOException {
        ClaimCsvParser.Table table = ClaimCsvParser.parse(file.getInputStream());

        List<String> missing = ClaimFeatures.ALL.stream()
                .filter(c -> !table.header().contains(c.toLowerCase(Locale.ROOT))).toList();
        if (!missing.isEmpty()) {
            throw new InvalidFileException("Missing required column(s): " + String.join(", ", missing));
        }
        if (table.rows().isEmpty()) {
            throw new InvalidFileException("The file has a header but no claim rows.");
        }
        if (table.rows().size() > MAX_ROWS) {
            throw new InvalidFileException("Too many rows (" + table.rows().size() + "); the limit is " + MAX_ROWS + ".");
        }

        ClaimBatch batch = new ClaimBatch();
        batch.setFilename(file.getOriginalFilename() == null ? "claims.csv" : file.getOriginalFilename());
        batch = batchRepository.save(batch);

        List<Claim> claims = new ArrayList<>();
        int rejected = 0;
        String firstRejection = null;
        int line = 1;
        for (Map<String, String> row : table.rows()) {
            line++;
            try {
                claims.add(toClaim(row, batch.getId(), line));
            } catch (IllegalArgumentException e) {
                rejected++;
                if (firstRejection == null) firstRejection = "Line " + line + ": " + e.getMessage();
            }
        }

        return persist(batch, claims, rejected, firstRejection);
    }

    public static final int MAX_MANUAL_CLAIMS = 100;

    /** One manually entered claim: an optional label plus the model inputs. */
    public record ManualClaim(String claimantName, Map<String, Object> features) {}

    public ClaimBatch ingestManual(List<ManualClaim> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new InvalidFileException("Add at least one claim.");
        }
        if (entries.size() > MAX_MANUAL_CLAIMS) {
            throw new InvalidFileException("Too many claims (" + entries.size() + "); the limit is " + MAX_MANUAL_CLAIMS + ".");
        }

        ClaimBatch batch = new ClaimBatch();
        batch.setFilename("Manual entry (" + entries.size() + (entries.size() == 1 ? " claim)" : " claims)"));
        batch = batchRepository.save(batch);

        List<Claim> claims = new ArrayList<>();
        int rejected = 0;
        String firstRejection = null;
        int n = 0;
        for (ManualClaim e : entries) {
            n++;
            try {
                Claim c = new Claim();
                c.setBatchId(batch.getId());
                c.setPolicyNumber("MN-" + batch.getId().toString().substring(0, 6).toUpperCase() + "-" + n);
                String name = e.claimantName() == null ? "" : e.claimantName().trim();
                c.setClaimantName(name.isEmpty() ? "Manual claim " + n : name);
                c.setFeatures(ClaimFeatures.normalise(e.features() == null ? Map.of() : e.features()));
                c.setSubmittedAt(Instant.now());
                c.setStatus(Claim.ClaimStatus.SUBMITTED);
                claims.add(c);
            } catch (IllegalArgumentException ex) {
                rejected++;
                if (firstRejection == null) firstRejection = "Claim " + n + ": " + ex.getMessage();
            }
        }
        return persist(batch, claims, rejected, firstRejection);
    }

    private ClaimBatch persist(ClaimBatch batch, List<Claim> claims, int rejected, String firstRejection) {
        List<Claim> saved = claimRepository.saveAll(claims);
        batch.setAcceptedRows(saved.size());
        batch.setRejectedRows(rejected);
        batch.setFirstRejection(firstRejection);
        batchRepository.save(batch);

        for (Claim c : saved) {
            ClaimSubmittedEvent event = new ClaimSubmittedEvent();
            event.setClaimId(c.getId());
            event.setPolicyNumber(c.getPolicyNumber());
            event.setSubmittedAt(c.getSubmittedAt());
            producer.publishClaimSubmitted(event);
        }
        return batch;
    }


    public Optional<BatchStatus> status(UUID batchId) {
        return batchRepository.findById(batchId).map(b -> {
            long scored = claimRepository.countByBatchIdAndStatus(b.getId(), Claim.ClaimStatus.SCORED);
            long unscored = claimRepository.countByBatchIdAndStatus(b.getId(), Claim.ClaimStatus.UNSCORED);
            List<Long> latencies = new ArrayList<>(claimRepository.findScoringLatencies(b.getId()));
            Collections.sort(latencies);
            Long median = latencies.isEmpty() ? null : latencies.get(latencies.size() / 2);
            return new BatchStatus(b.getId(), b.getFilename(), b.getAcceptedRows(), b.getRejectedRows(),
                    b.getFirstRejection(), scored, unscored, median, b.getCreatedAt());
        });
    }

    private Claim toClaim(Map<String, String> row, UUID batchId, int line) {
        Map<String, Object> raw = new HashMap<>();
        row.forEach((header, value) -> {
            String canonical = CANONICAL.get(header);
            if (canonical != null) raw.put(canonical, value);
        });

        Claim c = new Claim();
        c.setBatchId(batchId);
        // The file carries no identity fields, so label rows by file position.
        c.setPolicyNumber("UP-" + batchId.toString().substring(0, 6).toUpperCase() + "-" + line);
        c.setClaimantName("Claim " + line);
        c.setFeatures(ClaimFeatures.normalise(raw));
        c.setSubmittedAt(Instant.now());
        c.setStatus(Claim.ClaimStatus.SUBMITTED);
        return c;
    }
}
