package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import com.articulate.nlp.morphodb.MorphoCategoricalSchema;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * JSONL loaders for MorphoDB evaluation files.
 */
public class MorphoDBLoader {

    /**
     * Classification result keyed by normalized lemma.
     */
    public static class LoadResult {
        public final Map<String, String> classifications;
        public final int totalRecords;
        public final int errorRecords;
        public final int parseFailures;

        public LoadResult(Map<String, String> classifications, int totalRecords, int errorRecords, int parseFailures) {
            this.classifications = classifications;
            this.totalRecords = totalRecords;
            this.errorRecords = errorRecords;
            this.parseFailures = parseFailures;
        }
    }

    /**
     * Raw JSON objects preserved for generative evaluation and audit building.
     */
    public static class JsonRecordLoadResult {
        public final List<JSONObject> records;
        public final int totalRecords;
        public final int errorRecords;
        public final int parseFailures;

        public JsonRecordLoadResult(List<JSONObject> records, int totalRecords, int errorRecords, int parseFailures) {
            this.records = records;
            this.totalRecords = totalRecords;
            this.errorRecords = errorRecords;
            this.parseFailures = parseFailures;
        }
    }

    @FunctionalInterface
    public interface ClassificationExtractor {
        String apply(JSONObject jsonObject);
    }

    public static LoadResult loadClassificationsWithStats(String filePath, String fieldName) throws IOException {
        return loadClassificationsWithStats(filePath, fieldName, null);
    }

    public static LoadResult loadClassificationsWithStats(String filePath,
                                                          String fieldName,
                                                          String invalidCategorizationFieldName) throws IOException {
        return loadClassificationsWithStats(filePath, json -> {
            String value = json.optString(fieldName, "");
            return value.isEmpty() ? null : value;
        }, invalidCategorizationFieldName);
    }

    public static LoadResult loadClassificationsWithStats(String filePath,
                                                          ClassificationExtractor extractor) throws IOException {
        return loadClassificationsWithStats(filePath, extractor, null);
    }

    public static LoadResult loadClassificationsWithStats(String filePath,
                                                          ClassificationExtractor extractor,
                                                          String invalidCategorizationFieldName) throws IOException {
        Map<String, String> classifications = new LinkedHashMap<>();
        int totalRecords = 0;
        int errorRecords = 0;
        int parseFailures = 0;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalRecords++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    if (isNonSuccessRecord(json)) {
                        errorRecords++;
                        continue;
                    }
                    if (isInvalidCategorizationForField(json, invalidCategorizationFieldName)) {
                        errorRecords++;
                        continue;
                    }

                    String normalizedLemma = extractNormalizedLemma(json);
                    if (normalizedLemma.isEmpty()) {
                        parseFailures++;
                        continue;
                    }

                    String classificationValue = extractor.apply(json);
                    if (classificationValue == null || classificationValue.trim().isEmpty()) {
                        parseFailures++;
                        continue;
                    }

                    if ("__UNDONE__".equals(classificationValue)) {
                        classifications.remove(normalizedLemma);
                    } else {
                        classifications.put(normalizedLemma, classificationValue);
                    }
                } catch (Exception e) {
                    parseFailures++;
                }
            }
        }

        return new LoadResult(classifications, totalRecords, errorRecords, parseFailures);
    }

    public static JsonRecordLoadResult countJsonRecords(String filePath) throws IOException {
        return countJsonRecords(filePath, null);
    }

    public static JsonRecordLoadResult countJsonRecords(String filePath,
                                                        String invalidCategorizationFieldName) throws IOException {
        int totalRecords = 0;
        int errorRecords = 0;
        int parseFailures = 0;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalRecords++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    if (isNonSuccessRecord(json) || isInvalidCategorizationForField(json, invalidCategorizationFieldName)) {
                        errorRecords++;
                    }
                } catch (Exception e) {
                    parseFailures++;
                }
            }
        }

        return new JsonRecordLoadResult(new ArrayList<>(), totalRecords, errorRecords, parseFailures);
    }

    public static JsonRecordLoadResult loadJsonRecords(String filePath) throws IOException {
        List<JSONObject> records = new ArrayList<>();
        int totalRecords = 0;
        int errorRecords = 0;
        int parseFailures = 0;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalRecords++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    if (isNonSuccessRecord(json)) {
                        errorRecords++;
                        continue;
                    }
                    records.add(json);
                } catch (Exception e) {
                    parseFailures++;
                }
            }
        }

        return new JsonRecordLoadResult(records, totalRecords, errorRecords, parseFailures);
    }

    public static String extractNormalizedLemma(JSONObject json) {
        String lemma = json.optString("lemma", "");
        return GenMorphoUtils.normalizeLemma(lemma);
    }

    public static boolean isErrorRecord(JSONObject json) {
        return "error".equalsIgnoreCase(json.optString("status", ""));
    }

    public static boolean isInvalidCategorizationForField(JSONObject json, String expectedFieldName) {
        if (json == null || expectedFieldName == null || expectedFieldName.trim().isEmpty()) {
            return false;
        }
        return MorphoCategoricalSchema.isInvalidCategorizationForField(
                json.optString(MorphoCategoricalSchema.CATEGORIZATION_STATUS_FIELD, ""),
                json.optString(MorphoCategoricalSchema.INVALID_CATEGORIZATION_FIELD_FIELD, ""),
                expectedFieldName
        );
    }

    public static boolean isNonSuccessRecord(JSONObject json) {
        String status = json.optString("status", "").toLowerCase();
        return status.equals("error") || status.equals("refused") || status.equals("garbled");
    }
}
