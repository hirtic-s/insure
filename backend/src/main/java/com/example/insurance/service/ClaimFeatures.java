package com.example.insurance.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model's input columns. Must match numeric_cols and categorical_cols in
 * ml-service/model/column_metadata.json (also served by the ML service's /schema).
 */
public final class ClaimFeatures {

    public static final List<String> NUMERIC =
            List.of("WeekOfMonth", "WeekOfMonthClaimed", "Age", "Deductible", "DriverRating");

    public static final List<String> CATEGORICAL = List.of(
            "Month", "DayOfWeek", "Make", "AccidentArea", "DayOfWeekClaimed", "MonthClaimed",
            "Sex", "MaritalStatus", "Fault", "PolicyType", "VehicleCategory", "VehiclePrice",
            "Days_Policy_Accident", "Days_Policy_Claim", "PastNumberOfClaims", "AgeOfVehicle",
            "AgeOfPolicyHolder", "PoliceReportFiled", "WitnessPresent", "AgentType",
            "AddressChange_Claim", "NumberOfCars", "BasePolicy");

    public static final List<String> ALL;

    static {
        List<String> all = new ArrayList<>(NUMERIC);
        all.addAll(CATEGORICAL);
        ALL = List.copyOf(all);
    }

    private ClaimFeatures() {}

    /**
     * Validates and normalises raw values into model inputs: numeric columns become
     * numbers, the rest trimmed strings. Throws IllegalArgumentException naming the problem.
     */
    public static Map<String, Object> normalise(Map<String, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String col : ALL) {
            Object v = raw.get(col);
            String s = v == null ? "" : v.toString().trim();
            if (s.isEmpty()) {
                throw new IllegalArgumentException(col + " is empty");
            }
            if (NUMERIC.contains(col)) {
                try {
                    out.put(col, Double.parseDouble(s));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(col + " '" + s + "' is not a number");
                }
            } else {
                out.put(col, s);
            }
        }
        return out;
    }
}
