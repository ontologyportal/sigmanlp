package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import com.articulate.nlp.morphodb.VerbRegularityUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Benchmarks stored and live-derived verb regularity labels against human gold.
 */
public class VerbRegularityBenchmarkRunner {

    private static final String OUTPUT_DIR_NAME = "VerbRegularityBenchmark";
    private static final String SUMMARY_FILE_NAME = "summary_verb_regularity.txt";
    private static final String CSV_FILE_NAME = "verb_regularity_summary.csv";
    private static final String MISMATCH_DIR_NAME = "mismatches";
    private static final String AUDIT_DIR_NAME = "audit";
    private static final int MAX_MISMATCH_SAMPLES = 10;

    static final class GoldLabel {
        final String synsetId;
        final String lemma;
        final String label;

        GoldLabel(String synsetId, String lemma, String label) {
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.label = label;
        }
    }

    static final class ConfusionStats {
        int tp;
        int fp;
        int fn;
        int tn;

        int total() {
            return tp + fp + fn + tn;
        }

        Double accuracy() {
            return EvaluationSupport.rate(tp + tn, total());
        }

        Double precision() {
            return EvaluationSupport.rate(tp, tp + fp);
        }

        Double recall() {
            return EvaluationSupport.rate(tp, tp + fn);
        }

        Double f1() {
            Double precision = precision();
            Double recall = recall();
            if (precision == null || recall == null || Double.isNaN(precision) || Double.isNaN(recall)
                    || precision + recall == 0.0d) {
                return Double.NaN;
            }
            return (2.0d * precision * recall) / (precision + recall);
        }
    }

    static final class MismatchGroup {
        final List<String> sampleLines = new ArrayList<>();
        int totalCount;
        int singleWordCount;
        int multiwordCount;
        int hyphenatedCount;

        void add(GoldLabel goldLabel, String predictedLabel) {
            if (goldLabel == null) {
                return;
            }
            totalCount++;
            if (goldLabel.lemma.contains("-")) {
                hyphenatedCount++;
            }
            if (goldLabel.lemma.contains(" ")) {
                multiwordCount++;
            } else if (!goldLabel.lemma.contains("-")) {
                singleWordCount++;
            }
            if (sampleLines.size() < MAX_MISMATCH_SAMPLES) {
                sampleLines.add(goldLabel.synsetId + " | " + goldLabel.lemma
                        + " | gold=" + goldLabel.label + " | predicted=" + predictedLabel);
            }
        }
    }

    static final class AuditRow {
        final String synsetId;
        final String lemma;
        final String goldLabel;
        final String storedLabel;
        final String liveDerivedLabel;
        final boolean matchedRecord;
        final String normalizedSimplePast;
        final String normalizedPastParticiple;
        final boolean multiword;
        final boolean hyphenated;

        AuditRow(String synsetId,
                 String lemma,
                 String goldLabel,
                 String storedLabel,
                 String liveDerivedLabel,
                 boolean matchedRecord,
                 String normalizedSimplePast,
                 String normalizedPastParticiple,
                 boolean multiword,
                 boolean hyphenated) {
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.goldLabel = goldLabel;
            this.storedLabel = storedLabel;
            this.liveDerivedLabel = liveDerivedLabel;
            this.matchedRecord = matchedRecord;
            this.normalizedSimplePast = normalizedSimplePast;
            this.normalizedPastParticiple = normalizedPastParticiple;
            this.multiword = multiword;
            this.hyphenated = hyphenated;
        }
    }

    static final class ModelResult {
        final String model;
        final int goldTotal;
        final int matchedRecords;
        final int missingRecords;
        final ConfusionStats storedStats;
        final ConfusionStats derivedStats;
        final MismatchGroup storedGoldIrregularPredRegular;
        final MismatchGroup storedGoldRegularPredIrregular;
        final MismatchGroup derivedGoldIrregularPredRegular;
        final MismatchGroup derivedGoldRegularPredIrregular;
        final List<AuditRow> auditRows;

        ModelResult(String model,
                    int goldTotal,
                    int matchedRecords,
                    int missingRecords,
                    ConfusionStats storedStats,
                    ConfusionStats derivedStats,
                    MismatchGroup storedGoldIrregularPredRegular,
                    MismatchGroup storedGoldRegularPredIrregular,
                    MismatchGroup derivedGoldIrregularPredRegular,
                    MismatchGroup derivedGoldRegularPredIrregular,
                    List<AuditRow> auditRows) {
            this.model = model;
            this.goldTotal = goldTotal;
            this.matchedRecords = matchedRecords;
            this.missingRecords = missingRecords;
            this.storedStats = storedStats;
            this.derivedStats = derivedStats;
            this.storedGoldIrregularPredRegular = storedGoldIrregularPredRegular;
            this.storedGoldRegularPredIrregular = storedGoldRegularPredIrregular;
            this.derivedGoldIrregularPredRegular = derivedGoldIrregularPredRegular;
            this.derivedGoldRegularPredIrregular = derivedGoldRegularPredIrregular;
            this.auditRows = auditRows;
        }
    }

    static final class BenchmarkResult {
        final Path outputRoot;
        final Path summaryFile;
        final Path csvFile;
        final Path mismatchDir;
        final Path auditDir;
        final int goldTotal;
        final int goldDuplicatesSkipped;
        final List<ModelResult> modelResults;

        BenchmarkResult(Path outputRoot,
                        Path summaryFile,
                        Path csvFile,
                        Path mismatchDir,
                        Path auditDir,
                        int goldTotal,
                        int goldDuplicatesSkipped,
                        List<ModelResult> modelResults) {
            this.outputRoot = outputRoot;
            this.summaryFile = summaryFile;
            this.csvFile = csvFile;
            this.mismatchDir = mismatchDir;
            this.auditDir = auditDir;
            this.goldTotal = goldTotal;
            this.goldDuplicatesSkipped = goldDuplicatesSkipped;
            this.modelResults = modelResults;
        }
    }

    private static final class GoldLoadResult {
        final List<GoldLabel> labels;
        final int duplicatesSkipped;

        GoldLoadResult(List<GoldLabel> labels, int duplicatesSkipped) {
            this.labels = labels;
            this.duplicatesSkipped = duplicatesSkipped;
        }
    }

    public static void main(String[] args) {
        try {
            Map<String, String> cli = EvaluationSupport.parseArgs(args);
            String inputDir = cli.get("input");
            String outputDir = cli.getOrDefault("output", ".");
            String goldFile = cli.get("gold-file");
            if (inputDir == null || inputDir.trim().isEmpty()) {
                System.err.println("Usage: java ... VerbRegularityBenchmarkRunner --input <path> [--gold-file <path>] [--output <path>]");
                System.exit(1);
            }

            Path resolvedGoldPath = goldFile == null || goldFile.trim().isEmpty()
                    ? defaultGoldPath()
                    : Paths.get(goldFile);
            BenchmarkResult result = run(Paths.get(inputDir), resolvedGoldPath, Paths.get(outputDir));
            System.out.println("Verb regularity benchmark complete.");
            System.out.println("  gold total: " + result.goldTotal);
            System.out.println("  output: " + result.outputRoot.toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("Error during verb regularity benchmark: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static BenchmarkResult run(Path inputDir, Path goldPath, Path outputDir) throws IOException {
        if (!Files.exists(goldPath)) {
            throw new IOException("Verb regularity gold file not found: " + goldPath);
        }

        List<ModelMetadata> models = EvaluationSupport.scanModelDirectories(inputDir.toString());
        if (models.isEmpty()) {
            throw new IOException("No model directories found under " + inputDir);
        }

        GoldLoadResult goldLoadResult = loadGoldLabels(goldPath);
        Path outputRoot = outputDir.resolve(OUTPUT_DIR_NAME);
        Path mismatchDir = outputRoot.resolve(MISMATCH_DIR_NAME);
        Path auditDir = outputRoot.resolve(AUDIT_DIR_NAME);
        Path summaryFile = outputRoot.resolve(SUMMARY_FILE_NAME);
        Path csvFile = outputRoot.resolve(CSV_FILE_NAME);
        Files.createDirectories(mismatchDir);
        Files.createDirectories(auditDir);

        List<ModelResult> modelResults = new ArrayList<>();
        for (ModelMetadata model : models) {
            modelResults.add(evaluateModel(
                    inputDir.resolve(model.getDirectoryName()),
                    model.getName(),
                    goldLoadResult.labels,
                    mismatchDir,
                    auditDir
            ));
        }

        writeSummary(summaryFile, goldPath, mismatchDir, auditDir, goldLoadResult, modelResults);
        writeCsv(csvFile, modelResults);
        return new BenchmarkResult(outputRoot, summaryFile, csvFile, mismatchDir, auditDir,
                goldLoadResult.labels.size(), goldLoadResult.duplicatesSkipped, modelResults);
    }

    private static Path defaultGoldPath() {
        return Paths.get(System.getProperty("user.home"), ".sigmanlp", "Gold", "gold", "conjugation_regularity.jsonl");
    }

    private static GoldLoadResult loadGoldLabels(Path goldPath) throws IOException {
        Map<String, GoldLabel> bySynset = new LinkedHashMap<>();
        int duplicatesSkipped = 0;
        try (BufferedReader reader = Files.newBufferedReader(goldPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JSONObject json = new JSONObject(trimmed);
                String synsetId = json.optString("synsetId", "").trim();
                String lemma = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
                if (synsetId.isEmpty() || lemma.isEmpty()) {
                    continue;
                }

                String humanLabel = json.optString("human_label", "").trim();
                if ("__UNDONE__".equals(humanLabel)) {
                    if (bySynset.remove(synsetId) != null) {
                        duplicatesSkipped++;
                    }
                    continue;
                }

                String label = normalizeBinaryRegularity(humanLabel);
                if (label.isEmpty()) {
                    continue;
                }
                if (bySynset.containsKey(synsetId)) {
                    duplicatesSkipped++;
                }
                bySynset.put(synsetId, new GoldLabel(synsetId, lemma, label));
            }
        }
        return new GoldLoadResult(new ArrayList<>(bySynset.values()), duplicatesSkipped);
    }

    private static ModelResult evaluateModel(Path modelDir,
                                             String modelName,
                                             List<GoldLabel> goldLabels,
                                             Path mismatchDir,
                                             Path auditDir) throws IOException {
        Path recordPath = modelDir.resolve("verb").resolve("VerbConjugations.txt");
        Map<String, JSONObject> recordsBySynset = loadRecordsBySynset(recordPath);

        ConfusionStats storedStats = new ConfusionStats();
        ConfusionStats derivedStats = new ConfusionStats();
        MismatchGroup storedGoldIrregularPredRegular = new MismatchGroup();
        MismatchGroup storedGoldRegularPredIrregular = new MismatchGroup();
        MismatchGroup derivedGoldIrregularPredRegular = new MismatchGroup();
        MismatchGroup derivedGoldRegularPredIrregular = new MismatchGroup();
        List<AuditRow> auditRows = new ArrayList<>();

        int matchedRecords = 0;
        int missingRecords = 0;
        for (GoldLabel goldLabel : goldLabels) {
            JSONObject json = recordsBySynset.get(goldLabel.synsetId);
            boolean matchedRecord = json != null;
            if (matchedRecord) {
                matchedRecords++;
            } else {
                missingRecords++;
            }

            String storedLabel = matchedRecord
                    ? normalizeBinaryRegularity(json.optString("regularity", ""))
                    : "";
            Map<String, String> normalizedForms = matchedRecord
                    ? GenerativeEvalUtils.extractNormalizedConjugationForms(json)
                    : null;
            String liveDerivedLabel = normalizeBinaryRegularity(
                    VerbRegularityUtils.classifyRegularity(goldLabel.lemma, normalizedForms)
            );

            updateStats(storedStats, goldLabel, storedLabel, storedGoldIrregularPredRegular, storedGoldRegularPredIrregular);
            updateStats(derivedStats, goldLabel, liveDerivedLabel, derivedGoldIrregularPredRegular, derivedGoldRegularPredIrregular);

            auditRows.add(new AuditRow(
                    goldLabel.synsetId,
                    goldLabel.lemma,
                    goldLabel.label,
                    storedLabel,
                    liveDerivedLabel,
                    matchedRecord,
                    normalizedForms == null ? "" : normalizedForms.getOrDefault(GenerativeEvalUtils.SLOT_SIMPLE_PAST, ""),
                    normalizedForms == null ? "" : normalizedForms.getOrDefault(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE, ""),
                    goldLabel.lemma.contains(" "),
                    goldLabel.lemma.contains("-")
            ));
        }

        ModelResult result = new ModelResult(
                modelName,
                goldLabels.size(),
                matchedRecords,
                missingRecords,
                storedStats,
                derivedStats,
                storedGoldIrregularPredRegular,
                storedGoldRegularPredIrregular,
                derivedGoldIrregularPredRegular,
                derivedGoldRegularPredIrregular,
                auditRows
        );
        writeMismatchFile(mismatchDir.resolve(modelName + "_mismatches.txt"), result);
        writeAuditCsv(auditDir.resolve(modelName + "_audit.csv"), modelName, auditRows);
        return result;
    }

    private static Map<String, JSONObject> loadRecordsBySynset(Path recordPath) throws IOException {
        Map<String, JSONObject> recordsBySynset = new LinkedHashMap<>();
        if (!Files.exists(recordPath)) {
            return recordsBySynset;
        }
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(recordPath.toString());
        for (JSONObject json : loadResult.records) {
            String synsetId = json.optString("synsetId", "").trim();
            if (synsetId.isEmpty() || recordsBySynset.containsKey(synsetId)) {
                continue;
            }
            recordsBySynset.put(synsetId, json);
        }
        return recordsBySynset;
    }

    private static void updateStats(ConfusionStats stats,
                                    GoldLabel goldLabel,
                                    String predictedLabel,
                                    MismatchGroup goldIrregularPredRegular,
                                    MismatchGroup goldRegularPredIrregular) {
        if (stats == null || goldLabel == null) {
            return;
        }
        if (!"Regular".equals(predictedLabel) && !"Irregular".equals(predictedLabel)) {
            return;
        }

        if ("Irregular".equals(goldLabel.label) && "Irregular".equals(predictedLabel)) {
            stats.tp++;
            return;
        }
        if ("Regular".equals(goldLabel.label) && "Irregular".equals(predictedLabel)) {
            stats.fp++;
            goldRegularPredIrregular.add(goldLabel, predictedLabel);
            return;
        }
        if ("Irregular".equals(goldLabel.label)) {
            stats.fn++;
            goldIrregularPredRegular.add(goldLabel, predictedLabel);
            return;
        }
        stats.tn++;
    }

    private static void writeSummary(Path path,
                                     Path goldPath,
                                     Path mismatchDir,
                                     Path auditDir,
                                     GoldLoadResult goldLoadResult,
                                     List<ModelResult> modelResults) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("VERB REGULARITY BENCHMARK\n");
        sb.append("=========================\n\n");
        sb.append("Gold file: ").append(goldPath.toAbsolutePath().normalize()).append("\n");
        sb.append("Gold unique synsets: ").append(goldLoadResult.labels.size()).append("\n");
        sb.append("Gold duplicates skipped: ").append(goldLoadResult.duplicatesSkipped).append("\n");
        sb.append("Mismatch directory: ").append(mismatchDir.toAbsolutePath().normalize()).append("\n");
        sb.append("Audit directory: ").append(auditDir.toAbsolutePath().normalize()).append("\n");
        sb.append("Derived labels: recomputed live from normalized simple-past and past-participle forms.\n\n");
        sb.append("Summary table\n");
        sb.append("model | gold_n | matched_n | missing_n | stored_acc | stored_irregular_precision | stored_irregular_recall | stored_irregular_f1 | derived_acc | derived_irregular_precision | derived_irregular_recall | derived_irregular_f1\n");
        for (ModelResult result : modelResults) {
            sb.append(result.model).append(" | ")
                    .append(result.goldTotal).append(" | ")
                    .append(result.matchedRecords).append(" | ")
                    .append(result.missingRecords).append(" | ")
                    .append(formatRate(result.storedStats.accuracy())).append(" | ")
                    .append(formatRate(result.storedStats.precision())).append(" | ")
                    .append(formatRate(result.storedStats.recall())).append(" | ")
                    .append(formatRate(result.storedStats.f1())).append(" | ")
                    .append(formatRate(result.derivedStats.accuracy())).append(" | ")
                    .append(formatRate(result.derivedStats.precision())).append(" | ")
                    .append(formatRate(result.derivedStats.recall())).append(" | ")
                    .append(formatRate(result.derivedStats.f1())).append("\n");
        }
        sb.append("\n");
        for (ModelResult result : modelResults) {
            sb.append("MODEL ").append(result.model).append("\n");
            sb.append("  gold_n: ").append(result.goldTotal).append("\n");
            sb.append("  matched_n: ").append(result.matchedRecords).append("\n");
            sb.append("  missing_n: ").append(result.missingRecords).append("\n");
            appendStats(sb, "stored", result.storedStats);
            appendStats(sb, "derived_live", result.derivedStats);
            appendMismatchSummary(sb, "stored", "gold irregular -> predicted regular", result.storedGoldIrregularPredRegular);
            appendMismatchSummary(sb, "stored", "gold regular -> predicted irregular", result.storedGoldRegularPredIrregular);
            appendMismatchSummary(sb, "derived_live", "gold irregular -> predicted regular", result.derivedGoldIrregularPredRegular);
            appendMismatchSummary(sb, "derived_live", "gold regular -> predicted irregular", result.derivedGoldRegularPredIrregular);
            sb.append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendStats(StringBuilder sb, String label, ConfusionStats stats) {
        sb.append("  ").append(label).append(": total=").append(stats.total())
                .append(", tp=").append(stats.tp)
                .append(", fp=").append(stats.fp)
                .append(", fn=").append(stats.fn)
                .append(", tn=").append(stats.tn)
                .append(", accuracy=").append(formatRate(stats.accuracy()))
                .append(", irregular_precision=").append(formatRate(stats.precision()))
                .append(", irregular_recall=").append(formatRate(stats.recall()))
                .append(", irregular_f1=").append(formatRate(stats.f1()))
                .append("\n");
    }

    private static void appendMismatchSummary(StringBuilder sb,
                                              String label,
                                              String title,
                                              MismatchGroup group) {
        sb.append("  ").append(label).append(" ").append(title)
                .append(": total=").append(group.totalCount)
                .append(", single_word=").append(group.singleWordCount)
                .append(", multiword=").append(group.multiwordCount)
                .append(", hyphenated=").append(group.hyphenatedCount)
                .append("\n");
    }

    private static void writeCsv(Path path, List<ModelResult> modelResults) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,gold_n,matched_n,missing_n,stored_total,stored_tp,stored_fp,stored_fn,stored_tn,stored_accuracy,stored_irregular_precision,stored_irregular_recall,stored_irregular_f1,derived_total,derived_tp,derived_fp,derived_fn,derived_tn,derived_accuracy,derived_irregular_precision,derived_irregular_recall,derived_irregular_f1\n");
        for (ModelResult result : modelResults) {
            sb.append(result.model).append(",");
            sb.append(result.goldTotal).append(",");
            sb.append(result.matchedRecords).append(",");
            sb.append(result.missingRecords).append(",");
            appendCsvStats(sb, result.storedStats);
            sb.append(",");
            appendCsvStats(sb, result.derivedStats);
            sb.append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendCsvStats(StringBuilder sb, ConfusionStats stats) {
        sb.append(stats.total()).append(",");
        sb.append(stats.tp).append(",");
        sb.append(stats.fp).append(",");
        sb.append(stats.fn).append(",");
        sb.append(stats.tn).append(",");
        sb.append(formatRate(stats.accuracy())).append(",");
        sb.append(formatRate(stats.precision())).append(",");
        sb.append(formatRate(stats.recall())).append(",");
        sb.append(formatRate(stats.f1()));
    }

    private static void writeMismatchFile(Path path, ModelResult result) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL ").append(result.model).append("\n\n");
        appendMismatchGroup(sb, "stored: gold irregular -> predicted regular", result.storedGoldIrregularPredRegular);
        appendMismatchGroup(sb, "stored: gold regular -> predicted irregular", result.storedGoldRegularPredIrregular);
        appendMismatchGroup(sb, "derived_live: gold irregular -> predicted regular", result.derivedGoldIrregularPredRegular);
        appendMismatchGroup(sb, "derived_live: gold regular -> predicted irregular", result.derivedGoldRegularPredIrregular);
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendMismatchGroup(StringBuilder sb, String title, MismatchGroup group) {
        sb.append(title).append("\n");
        sb.append("  total=").append(group.totalCount)
                .append(", single_word=").append(group.singleWordCount)
                .append(", multiword=").append(group.multiwordCount)
                .append(", hyphenated=").append(group.hyphenatedCount)
                .append("\n");
        if (group.sampleLines.isEmpty()) {
            sb.append("  (none)\n\n");
            return;
        }
        for (String value : group.sampleLines) {
            sb.append("  ").append(value).append("\n");
        }
        sb.append("\n");
    }

    private static void writeAuditCsv(Path path, String modelName, List<AuditRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,synsetId,lemma,gold_label,stored_label,derived_label_live,matched_record,normalized_simple_past,normalized_past_participle,multiword,hyphenated\n");
        for (AuditRow row : rows) {
            sb.append(csv(modelName)).append(",");
            sb.append(csv(row.synsetId)).append(",");
            sb.append(csv(row.lemma)).append(",");
            sb.append(csv(row.goldLabel)).append(",");
            sb.append(csv(row.storedLabel)).append(",");
            sb.append(csv(row.liveDerivedLabel)).append(",");
            sb.append(row.matchedRecord).append(",");
            sb.append(csv(row.normalizedSimplePast)).append(",");
            sb.append(csv(row.normalizedPastParticiple)).append(",");
            sb.append(row.multiword).append(",");
            sb.append(row.hyphenated).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String normalizeBinaryRegularity(String raw) {
        if (raw == null) {
            return "";
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        if ("regular".equals(lowered)) {
            return "Regular";
        }
        if ("irregular".equals(lowered)) {
            return "Irregular";
        }
        return "";
    }

    private static String formatRate(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
