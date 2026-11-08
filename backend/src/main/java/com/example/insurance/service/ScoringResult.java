package com.example.insurance.service;

import java.util.List;

public record ScoringResult(double score, List<String> factors) {
}
