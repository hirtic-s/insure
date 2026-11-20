package com.example.insurance.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/** Minimal RFC-4180-ish CSV reader: quoted fields, doubled quotes, no multiline cells. */
final class ClaimCsvParser {

    record Table(List<String> header, List<Map<String, String>> rows) {}

    private ClaimCsvParser() {}

    static Table parse(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = r.readLine();
            if (headerLine == null) {
                return new Table(List.of(), List.of());
            }
            if (headerLine.startsWith("﻿")) {
                headerLine = headerLine.substring(1);
            }
            List<String> header = split(headerLine).stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = split(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), i < cells.size() ? cells.get(i).trim() : "");
                }
                rows.add(row);
            }
            return new Table(header, rows);
        }
    }

    private static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
