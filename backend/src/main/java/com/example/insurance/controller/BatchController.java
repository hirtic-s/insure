package com.example.insurance.controller;

import com.example.insurance.dto.BatchStatus;
import com.example.insurance.entity.ClaimBatch;
import com.example.insurance.service.BatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/batches")
@CrossOrigin(origins = "*")
public class BatchController {

    private final BatchService batchService;

    @Autowired
    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    /** Accepts a claims CSV and returns immediately; scoring continues asynchronously. */
    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        try {
            ClaimBatch batch = batchService.ingest(file);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(batchService.status(batch.getId()).orElseThrow());
        } catch (BatchService.InvalidFileException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record ManualRequest(List<BatchService.ManualClaim> claims) {}

    /** Scores claims entered by hand; same pipeline and progress tracking as a file upload. */
    @PostMapping("/manual")
    public ResponseEntity<?> manual(@RequestBody ManualRequest request) {
        try {
            ClaimBatch batch = batchService.ingestManual(request.claims());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(batchService.status(batch.getId()).orElseThrow());
        } catch (BatchService.InvalidFileException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchStatus> status(@PathVariable UUID id) {
        return batchService.status(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
