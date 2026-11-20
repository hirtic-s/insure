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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final List<String> REQUIRED =
            List.of("age_of_driver", "gender", "accident_site", "claim_date", "total_claim");
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

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

        List<String> missing = REQUIRED.stream().filter(c -> !table.header().contains(c)).toList();
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

        List<Claim> saved = claimRepository.saveAll(claims);
        batch.setAcceptedRows(saved.size());
        batch.setRejectedRows(rejected);
        batch.setFirstRejection(firstRejection);
        batchRepository.save(batch);

        for (Claim c : saved) {
            ClaimSubmittedEvent event = new ClaimSubmittedEvent();
            event.setClaimId(c.getId());
            event.setPolicyNumber(c.getPolicyNumber());
            event.setClaimAmount(c.getClaimAmount());
            event.setPolicyStartDate(c.getPolicyStartDate());
            event.setIncidentDate(c.getIncidentDate());
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
        Instant incident = parseDate(row.get("claim_date"));
        Claim c = new Claim();
        c.setBatchId(batchId);
        // The file carries no identity fields, so label rows by file position.
        c.setPolicyNumber("UP-" + batchId.toString().substring(0, 6).toUpperCase() + "-" + line);
        c.setClaimantName("Claim " + line);
        c.setClaimAmount(positive(row.get("total_claim"), "total_claim"));
        c.setIncidentDate(incident);
        // Not in the file; the incident date minus a year is a placeholder to satisfy the schema.
        c.setPolicyStartDate(incident.minusSeconds(365L * 24 * 3600));
        c.setDriverAge((int) Math.round(number(row.get("age_of_driver"), "age_of_driver")));
        c.setGender(row.get("gender").toUpperCase());
        if (!c.getGender().equals("M") && !c.getGender().equals("F")) {
            throw new IllegalArgumentException("gender must be M or F");
        }
        String marital = row.getOrDefault("marital_status", "");
        c.setMaritalStatus(marital.isBlank() ? "0" : marital.replace(".0", ""));
        if (row.get("accident_site").isBlank()) throw new IllegalArgumentException("accident_site is empty");
        c.setAccidentSite(row.get("accident_site"));
        c.setWitnessPresent(flag(row.get("witness_present")));
        c.setPoliceReportFiled(flag(row.get("police_report")));
        String color = row.getOrDefault("vehicle_color", "");
        c.setVehicleColor(color.isBlank() ? null : color);
        // Optional: blank or a placeholder such as "*" means not reported.
        c.setInjuryClaim(optionalAmount(row.get("injury_claim")));
        c.setSubmittedAt(Instant.now());
        c.setStatus(Claim.ClaimStatus.SUBMITTED);
        return c;
    }

    private static Instant parseDate(String s) {
        try {
            LocalDate d = s.contains("/") ? LocalDate.parse(s, US_DATE) : LocalDate.parse(s);
            return d.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("claim_date '" + s + "' is not M/d/yyyy or yyyy-MM-dd");
        }
    }

    private static double number(String s, String col) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException(col + " '" + s + "' is not a number");
        }
    }

    private static BigDecimal optionalAmount(String s) {
        try {
            return s == null || s.isBlank() ? null : BigDecimal.valueOf(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal positive(String s, String col) {
        double v = number(s, col);
        if (v <= 0) throw new IllegalArgumentException(col + " must be positive");
        return BigDecimal.valueOf(v);
    }

    private static boolean flag(String s) {
        return s != null && !s.isBlank() && number(s, "flag") != 0;
    }
}
