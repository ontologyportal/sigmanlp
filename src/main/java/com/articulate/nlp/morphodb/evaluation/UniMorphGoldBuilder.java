package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds normalized UniMorph gold files from the local English dataset.
 */
public class UniMorphGoldBuilder {

    static class BuildResult {
        final Path inputPath;
        final Path outputDir;
        final int totalRows;
        final int malformedRows;
        final int nounRowsSeen;
        final int verbRowsSeen;
        final int nounRecordsWritten;
        final int nounRecordsSkippedNoPlural;
        final int verbRecordsWritten;
        final int verbRecordsSkippedIncomplete;

        BuildResult(Path inputPath,
                    Path outputDir,
                    int totalRows,
                    int malformedRows,
                    int nounRowsSeen,
                    int verbRowsSeen,
                    int nounRecordsWritten,
                    int nounRecordsSkippedNoPlural,
                    int verbRecordsWritten,
                    int verbRecordsSkippedIncomplete) {
            this.inputPath = inputPath;
            this.outputDir = outputDir;
            this.totalRows = totalRows;
            this.malformedRows = malformedRows;
            this.nounRowsSeen = nounRowsSeen;
            this.verbRowsSeen = verbRowsSeen;
            this.nounRecordsWritten = nounRecordsWritten;
            this.nounRecordsSkippedNoPlural = nounRecordsSkippedNoPlural;
            this.verbRecordsWritten = verbRecordsWritten;
            this.verbRecordsSkippedIncomplete = verbRecordsSkippedIncomplete;
        }
    }

    private static class UniMorphRow {
        final String lemma;
        final String form;
        final String featureBundle;
        final Set<String> features;

        UniMorphRow(String lemma, String form, String featureBundle, Set<String> features) {
            this.lemma = lemma;
            this.form = form;
            this.featureBundle = featureBundle;
            this.features = features;
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length > 0) {
                System.err.println("Usage: java ... UniMorphGoldBuilder");
                System.exit(1);
            }
            BuildResult result = build(UniMorphGoldUtils.defaultSourcePath(), Paths.get("").toAbsolutePath().normalize());
            System.out.println("UniMorph gold build complete.");
            System.out.println("  source: " + result.inputPath);
            System.out.println("  output: " + result.outputDir);
            System.out.println("  noun records: " + result.nounRecordsWritten);
            System.out.println("  verb records: " + result.verbRecordsWritten);
        } catch (Exception e) {
            System.err.println("Error building UniMorph gold: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static BuildResult build(Path inputPath, Path outputDir) throws IOException {
        if (!Files.exists(inputPath)) {
            throw new IOException("UniMorph source file not found: " + inputPath);
        }

        Files.createDirectories(outputDir);

        Map<String, UniMorphGoldUtils.NounGoldRecord> nounRecords = new LinkedHashMap<>();
        Map<String, UniMorphGoldUtils.VerbGoldRecord> verbRecords = new LinkedHashMap<>();

        int totalRows = 0;
        int malformedRows = 0;
        int nounRowsSeen = 0;
        int verbRowsSeen = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                UniMorphRow row = parseRow(line);
                if (row == null) {
                    malformedRows++;
                    continue;
                }

                if (isNounRow(row.features)) {
                    nounRowsSeen++;
                    addNounEvidence(nounRecords, row);
                }
                if (isVerbCandidate(row.features)) {
                    verbRowsSeen++;
                    addVerbEvidence(verbRecords, row);
                }
            }
        }

        Map<String, UniMorphGoldUtils.NounGoldRecord> finalNouns = new LinkedHashMap<>();
        int nounRecordsSkippedNoPlural = 0;
        for (Map.Entry<String, UniMorphGoldUtils.NounGoldRecord> entry : nounRecords.entrySet()) {
            if (entry.getValue().pluralVariants.isEmpty()) {
                nounRecordsSkippedNoPlural++;
                continue;
            }
            finalNouns.put(entry.getKey(), entry.getValue());
        }

        Map<String, UniMorphGoldUtils.VerbGoldRecord> finalVerbs = new LinkedHashMap<>();
        int verbRecordsSkippedIncomplete = 0;
        for (Map.Entry<String, UniMorphGoldUtils.VerbGoldRecord> entry : verbRecords.entrySet()) {
            if (!hasAllVerbSlots(entry.getValue())) {
                verbRecordsSkippedIncomplete++;
                continue;
            }
            finalVerbs.put(entry.getKey(), entry.getValue());
        }

        UniMorphGoldUtils.writeNounGold(outputDir.resolve(UniMorphGoldUtils.NOUN_OUTPUT_FILE), finalNouns.values());
        UniMorphGoldUtils.writeVerbGold(outputDir.resolve(UniMorphGoldUtils.VERB_OUTPUT_FILE), finalVerbs.values());
        writeManifest(outputDir.resolve(UniMorphGoldUtils.MANIFEST_OUTPUT_FILE),
                inputPath,
                outputDir,
                totalRows,
                malformedRows,
                nounRowsSeen,
                verbRowsSeen,
                finalNouns.size(),
                nounRecordsSkippedNoPlural,
                finalVerbs.size(),
                verbRecordsSkippedIncomplete);

        return new BuildResult(
                inputPath,
                outputDir,
                totalRows,
                malformedRows,
                nounRowsSeen,
                verbRowsSeen,
                finalNouns.size(),
                nounRecordsSkippedNoPlural,
                finalVerbs.size(),
                verbRecordsSkippedIncomplete
        );
    }

    private static UniMorphRow parseRow(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("\t");
        if (parts.length != 3) {
            return null;
        }

        String lemma = GenMorphoUtils.normalizeLemma(parts[0]);
        String form = parts[1].trim();
        String featureBundle = parts[2].trim();
        if (lemma.isEmpty() || form.isEmpty() || featureBundle.isEmpty()) {
            return null;
        }

        Set<String> features = new LinkedHashSet<>();
        for (String rawFeature : featureBundle.split(";")) {
            String feature = rawFeature.trim();
            if (!feature.isEmpty()) {
                features.add(feature);
            }
        }
        if (features.isEmpty()) {
            return null;
        }

        return new UniMorphRow(lemma, form, featureBundle, features);
    }

    private static boolean isNounRow(Set<String> features) {
        return features.contains("N") && (features.contains("PL") || features.contains("SG"));
    }

    private static boolean isVerbCandidate(Set<String> features) {
        return slotForFeatures(features) != null;
    }

    private static void addNounEvidence(Map<String, UniMorphGoldUtils.NounGoldRecord> nounRecords, UniMorphRow row) {
        UniMorphGoldUtils.NounGoldRecord record = nounRecords.computeIfAbsent(
                row.lemma,
                UniMorphGoldUtils.NounGoldRecord::new
        );

        String defaultSingular = GenerativeEvalUtils.normalizePluralForm(row.lemma);
        if (!defaultSingular.isEmpty()) {
            record.singularVariants.add(defaultSingular);
        }

        if (row.features.contains("SG")) {
            String singular = GenerativeEvalUtils.normalizePluralForm(row.form);
            if (!singular.isEmpty()) {
                record.singularVariants.add(singular);
            }
        }
        if (row.features.contains("PL")) {
            String plural = GenerativeEvalUtils.normalizePluralForm(row.form);
            if (!plural.isEmpty()) {
                record.pluralVariants.add(plural);
            }
        }

        JSONObject evidence = new JSONObject();
        evidence.put("form", row.form);
        evidence.put("features", row.featureBundle);
        record.rawEvidence.add(evidence);
    }

    private static void addVerbEvidence(Map<String, UniMorphGoldUtils.VerbGoldRecord> verbRecords, UniMorphRow row) {
        String slot = slotForFeatures(row.features);
        if (slot == null) {
            return;
        }

        String normalized = GenerativeEvalUtils.normalizeVerbSurface(row.form, slot, row.lemma);
        if (normalized.isEmpty()) {
            return;
        }

        UniMorphGoldUtils.VerbGoldRecord record = verbRecords.computeIfAbsent(
                row.lemma,
                UniMorphGoldUtils.VerbGoldRecord::new
        );
        record.slotVariants.get(slot).add(normalized);

        JSONObject evidence = new JSONObject();
        evidence.put("slot", slot);
        evidence.put("form", row.form);
        evidence.put("features", row.featureBundle);
        record.rawEvidence.add(evidence);
    }

    private static boolean hasAllVerbSlots(UniMorphGoldUtils.VerbGoldRecord record) {
        for (String slot : UniMorphGoldUtils.VERB_SLOTS) {
            if (record.slotVariants.get(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static String slotForFeatures(Set<String> features) {
        if (!features.contains("V")) {
            return null;
        }
        if (features.contains("NFIN")) {
            return GenerativeEvalUtils.SLOT_INFINITIVE;
        }
        if (features.contains("V.PTCP") && features.contains("PST")) {
            return GenerativeEvalUtils.SLOT_PAST_PARTICIPLE;
        }
        if (features.contains("V.PTCP") && features.contains("PRS")) {
            return GenerativeEvalUtils.SLOT_GERUND;
        }
        if (!features.contains("V.PTCP") && features.contains("PRS") && features.contains("3") && features.contains("SG")) {
            return GenerativeEvalUtils.SLOT_PRESENT_3SG;
        }
        if (!features.contains("V.PTCP") && features.contains("PST")) {
            return GenerativeEvalUtils.SLOT_SIMPLE_PAST;
        }
        return null;
    }

    private static void writeManifest(Path path,
                                      Path inputPath,
                                      Path outputDir,
                                      int totalRows,
                                      int malformedRows,
                                      int nounRowsSeen,
                                      int verbRowsSeen,
                                      int nounRecordsWritten,
                                      int nounRecordsSkippedNoPlural,
                                      int verbRecordsWritten,
                                      int verbRecordsSkippedIncomplete) throws IOException {
        JSONObject manifest = new JSONObject();
        manifest.put("source_path", inputPath.toAbsolutePath().toString());
        manifest.put("source_relative_default", UniMorphGoldUtils.DEFAULT_SOURCE_RELATIVE_PATH);
        manifest.put("output_dir", outputDir.toAbsolutePath().toString());
        manifest.put("noun_output_file", UniMorphGoldUtils.NOUN_OUTPUT_FILE);
        manifest.put("verb_output_file", UniMorphGoldUtils.VERB_OUTPUT_FILE);
        manifest.put("built_at_epoch_ms", System.currentTimeMillis());
        manifest.put("total_rows", totalRows);
        manifest.put("malformed_rows", malformedRows);
        manifest.put("noun_rows_seen", nounRowsSeen);
        manifest.put("verb_rows_seen", verbRowsSeen);
        manifest.put("noun_records_written", nounRecordsWritten);
        manifest.put("noun_records_skipped_no_plural", nounRecordsSkippedNoPlural);
        manifest.put("verb_records_written", verbRecordsWritten);
        manifest.put("verb_records_skipped_incomplete", verbRecordsSkippedIncomplete);
        Files.write(path, manifest.toString(2).getBytes(StandardCharsets.UTF_8));
    }
}
