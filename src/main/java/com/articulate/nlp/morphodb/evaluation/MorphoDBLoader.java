package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loader for morphological database in JSONL format.
 * Primary key is lemma (normalized lowercase word).
 */
public class MorphoDBLoader {

    /**
     * Result object containing classification mappings and statistics.
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

    private static final String[] CLASSIFICATION_FIELDS = {
        "human_label",
        "countability", "humanness", "agentivity", "classification",
        "collective_status", "article", "causativity", "reflexive",
        "reciprocal", "aspect", "valence", "regularity", "category",
        "semanticField", "plural_pattern"
    };

    public static String extractClassificationValue(JSONObject jsonObject) {
        for (String field : CLASSIFICATION_FIELDS) {
            if (jsonObject.has(field)) {
                String value = jsonObject.optString(field, "");
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Simple backward-compatible method to load database.
     * Returns a map of normalized lemma to classification.
     *
     * @param filePath path to the JSONL database file
     * @return map of lemma to classification
     * @throws IOException if file cannot be read
     */
    public static Map<String, String> loadDatabase(String filePath) throws IOException {
        LoadResult result = loadDatabaseWithStats(filePath);
        return result.classifications;
    }

    /**
     * Loads database with full statistics.
     * Returns a LoadResult containing classifications and statistics.
     *
     * @param filePath path to the JSONL database file
     * @return LoadResult with classifications and statistics
     * @throws IOException if file cannot be read
     */
    public static LoadResult loadDatabaseWithStats(String filePath) throws IOException {
        Map<String, String> classifications = new LinkedHashMap<>();
        int totalRecords = 0;
        int errorRecords = 0;
        int parseFailures = 0;

        try (java.io.BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalRecords++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(line);

                    // Skip error records
                    if (json.has("status") && "error".equals(json.getString("status"))) {
                        errorRecords++;
                        continue;
                    }

                    // Extract lemma and normalize it
                    if (!json.has("lemma")) {
                        parseFailures++;
                        continue;
                    }

                    String lemma = json.getString("lemma");
                    String normalizedLemma = GenMorphoUtils.normalizeLemma(lemma);

                    if (normalizedLemma.isEmpty()) {
                        parseFailures++;
                        continue;
                    }

                    // Extract classification value
                    String classificationValue = extractClassificationValue(json);

                    if (classificationValue != null && !classificationValue.isEmpty()) {
                        if ("__UNDONE__".equals(classificationValue)) {
                            // Undo marker from HumanAnnotationTool: remove any prior classification
                            classifications.remove(normalizedLemma);
                        } else {
                            classifications.put(normalizedLemma, classificationValue);
                        }
                    } else {
                        parseFailures++;
                    }

                } catch (Exception e) {
                    parseFailures++;
                }
            }
        }

        return new LoadResult(classifications, totalRecords, errorRecords, parseFailures);
    }

}
