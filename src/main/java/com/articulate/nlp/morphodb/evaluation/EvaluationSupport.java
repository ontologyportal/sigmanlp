package com.articulate.nlp.morphodb.evaluation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Shared helpers for evaluation-oriented command-line tools.
 */
final class EvaluationSupport {

    static class ConfidenceInterval {
        final Double lower;
        final Double upper;

        ConfidenceInterval(Double lower, Double upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    private EvaluationSupport() {
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                result.put(key, args[++i]);
            } else {
                result.put(key, "true");
            }
        }
        return result;
    }

    static List<ModelMetadata> scanModelDirectories(String inputDir) throws IOException {
        List<ModelMetadata> result = new ArrayList<>();
        File dir = new File(inputDir);
        if (!dir.isDirectory()) {
            throw new IOException("Input directory not found: " + inputDir);
        }
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return result;
        }
        for (File subdir : subdirs) {
            if (!ModelMetadata.shouldIncludeAsModelDirectory(subdir.getName())) {
                continue;
            }
            result.add(ModelMetadata.fromDirName(subdir.getName()));
        }
        result.sort(ModelMetadata::compareForDisplay);
        return result;
    }

    static double percentile(List<Double> sortedValues, double quantile) {
        if (sortedValues.isEmpty()) {
            return Double.NaN;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double position = quantile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double weight = position - lower;
        return sortedValues.get(lower) * (1.0 - weight) + sortedValues.get(upper) * weight;
    }

    static ConfidenceInterval bootstrapBinaryCI(List<Boolean> outcomes,
                                                int bootstrapResamples,
                                                long seed) {
        if (outcomes == null || outcomes.isEmpty() || bootstrapResamples <= 0) {
            return new ConfidenceInterval(null, null);
        }

        Random random = new Random(seed);
        List<Double> estimates = new ArrayList<>();
        int sampleSize = outcomes.size();
        for (int sample = 0; sample < bootstrapResamples; sample++) {
            int matches = 0;
            for (int i = 0; i < sampleSize; i++) {
                if (Boolean.TRUE.equals(outcomes.get(random.nextInt(sampleSize)))) {
                    matches++;
                }
            }
            estimates.add((double) matches / sampleSize);
        }

        Collections.sort(estimates);
        return new ConfidenceInterval(percentile(estimates, 0.025), percentile(estimates, 0.975));
    }

    static int countTrue(List<Boolean> values) {
        int count = 0;
        for (Boolean value : values) {
            if (Boolean.TRUE.equals(value)) {
                count++;
            }
        }
        return count;
    }

    static Double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return Double.NaN;
        }
        return (double) numerator / denominator;
    }

    static String formatMaybeDouble(Double value) {
        if (value == null || Double.isNaN(value)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    static int parseInt(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(raw.trim());
    }

    static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Long.parseLong(raw.trim());
    }
}
