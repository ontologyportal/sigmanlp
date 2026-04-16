package com.articulate.nlp.morphodb.evaluation;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evaluates verb conjugation exact-match quality on the accepted human-audited
 * conjugation benchmark, stratified by regularity.
 */
public class VerbRegularityEvaluationRunner {

    private static final String OUTPUT_DIR_NAME = "VerbRegularityEvaluation";
    private static final String TABLES_DIR_NAME = "tables";
    private static final String LATEX_DIR_NAME = "latex";
    private static final String SUMMARY_FILE_NAME = "summary_verb_regularity_evaluation.txt";
    private static final String AGREEMENT_CSV_FILE_NAME = "verb_regularity_agreement.csv";
    private static final String COVERAGE_CSV_FILE_NAME = "verb_regularity_coverage.csv";
    private static final String LATEX_FILE_NAME = "verb_regularity_conjugation_table.tex";

    private static final String STRATUM_REGULAR = "Regular";
    private static final String STRATUM_IRREGULAR = "Irregular";
    private static final String STRATUM_OVERALL = "OVERALL";

    private static final class MetricSpec {
        final String comparisonKey;
        final String outputName;

        MetricSpec(String comparisonKey, String outputName) {
            this.comparisonKey = comparisonKey;
            this.outputName = outputName;
        }
    }

    private static final List<MetricSpec> METRICS = Arrays.asList(
            new MetricSpec(GenerativeEvalUtils.SLOT_INFINITIVE, "infinitive_exact_match"),
            new MetricSpec(GenerativeEvalUtils.SLOT_PRESENT_3SG, "present_3sg_exact_match"),
            new MetricSpec(GenerativeEvalUtils.SLOT_SIMPLE_PAST, "simple_past_exact_match"),
            new MetricSpec(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE, "past_participle_exact_match"),
            new MetricSpec(GenerativeEvalUtils.SLOT_GERUND, "gerund_exact_match"),
            new MetricSpec("all_parts_exact", "all_parts_exact_match")
    );

    static final class AgreementRow {
        final String model;
        final String stratum;
        final String metric;
        final int denominatorN;
        final int matchCount;
        final Double rate;
        final EvaluationSupport.ConfidenceInterval confidenceInterval;

        AgreementRow(String model,
                     String stratum,
                     String metric,
                     int denominatorN,
                     int matchCount,
                     Double rate,
                     EvaluationSupport.ConfidenceInterval confidenceInterval) {
            this.model = model;
            this.stratum = stratum;
            this.metric = metric;
            this.denominatorN = denominatorN;
            this.matchCount = matchCount;
            this.rate = rate;
            this.confidenceInterval = confidenceInterval;
        }
    }

    static final class CoverageRow {
        final String model;
        final int denominatorN;
        final int matchedRecords;
        final Double coverage;

        CoverageRow(String model, int denominatorN, int matchedRecords, Double coverage) {
            this.model = model;
            this.denominatorN = denominatorN;
            this.matchedRecords = matchedRecords;
            this.coverage = coverage;
        }
    }

    static final class EvaluationResult {
        final Path outputRoot;
        final Path summaryFile;
        final Path agreementCsvFile;
        final Path coverageCsvFile;
        final Path latexFile;
        final int acceptedAuditCount;
        final int regularDenominatorCount;
        final int irregularDenominatorCount;
        final List<AgreementRow> agreementRows;
        final List<CoverageRow> coverageRows;

        EvaluationResult(Path outputRoot,
                         Path summaryFile,
                         Path agreementCsvFile,
                         Path coverageCsvFile,
                         Path latexFile,
                         int acceptedAuditCount,
                         int regularDenominatorCount,
                         int irregularDenominatorCount,
                         List<AgreementRow> agreementRows,
                         List<CoverageRow> coverageRows) {
            this.outputRoot = outputRoot;
            this.summaryFile = summaryFile;
            this.agreementCsvFile = agreementCsvFile;
            this.coverageCsvFile = coverageCsvFile;
            this.latexFile = latexFile;
            this.acceptedAuditCount = acceptedAuditCount;
            this.regularDenominatorCount = regularDenominatorCount;
            this.irregularDenominatorCount = irregularDenominatorCount;
            this.agreementRows = agreementRows;
            this.coverageRows = coverageRows;
        }
    }

    public static void main(String[] args) {
        try {
            Map<String, String> cli = EvaluationSupport.parseArgs(args);
            String inputDir = cli.get("input");
            String outputDir = cli.getOrDefault("output", ".");
            String goldFile = cli.get("gold-file");
            int bootstrapResamples = EvaluationSupport.parseInt(cli.get("bootstrap-resamples"), 1000);
            long bootstrapSeed = EvaluationSupport.parseLong(cli.get("bootstrap-seed"), 42L);
            if (inputDir == null || inputDir.trim().isEmpty()) {
                System.err.println("Usage: java ... VerbRegularityEvaluationRunner --input <path> [--gold-file <path>] [--output <path>] [--bootstrap-resamples <n>] [--bootstrap-seed <seed>]");
                System.exit(1);
            }

            Path resolvedGoldPath = goldFile == null || goldFile.trim().isEmpty()
                    ? defaultGoldPath()
                    : Paths.get(goldFile);
            EvaluationResult result = run(
                    Paths.get(inputDir),
                    resolvedGoldPath,
                    Paths.get(outputDir),
                    bootstrapResamples,
                    bootstrapSeed
            );
            System.out.println("Verb regularity evaluation complete.");
            System.out.println("  accepted audit items: " + result.acceptedAuditCount);
            System.out.println("  output: " + result.outputRoot.toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("Error during verb regularity evaluation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static EvaluationResult run(Path inputDir,
                                Path goldAuditPath,
                                Path outputDir,
                                int bootstrapResamples,
                                long bootstrapSeed) throws IOException {
        if (!Files.exists(goldAuditPath)) {
            throw new IOException("Verb conjugation audit file not found: " + goldAuditPath);
        }

        List<ModelMetadata> models = EvaluationSupport.scanModelDirectories(inputDir.toString());
        if (models.isEmpty()) {
            throw new IOException("No model directories found under " + inputDir);
        }

        Map<String, EvaluationRunner.AuditEntry> loadedEntries = EvaluationRunner.loadAuditEntries(goldAuditPath);
        List<EvaluationRunner.AuditEntry> acceptedEntries = filterAcceptedConjugationAuditEntries(loadedEntries);
        int regularDenominatorCount = countEntriesForStratum(acceptedEntries, STRATUM_REGULAR);
        int irregularDenominatorCount = countEntriesForStratum(acceptedEntries, STRATUM_IRREGULAR);
        int acceptedAuditCount = acceptedEntries.size();

        Path outputRoot = outputDir.resolve(OUTPUT_DIR_NAME);
        Path tablesDir = outputRoot.resolve(TABLES_DIR_NAME);
        Path latexDir = outputRoot.resolve(LATEX_DIR_NAME);
        Path summaryFile = outputRoot.resolve(SUMMARY_FILE_NAME);
        Path agreementCsvFile = tablesDir.resolve(AGREEMENT_CSV_FILE_NAME);
        Path coverageCsvFile = tablesDir.resolve(COVERAGE_CSV_FILE_NAME);
        Path latexFile = latexDir.resolve(LATEX_FILE_NAME);
        Files.createDirectories(tablesDir);
        Files.createDirectories(latexDir);

        List<AgreementRow> agreementRows = new ArrayList<>();
        List<CoverageRow> coverageRows = new ArrayList<>();
        for (ModelMetadata model : models) {
            Map<String, GenerativeEvalUtils.ConjugationRecord> modelRecords = loadModelRecordsByAuditId(
                    inputDir.resolve(model.getDirectoryName()).resolve("verb").resolve("VerbConjugations.txt")
            );
            agreementRows.addAll(evaluateModel(
                    model.getName(),
                    acceptedEntries,
                    modelRecords,
                    bootstrapResamples,
                    bootstrapSeed
            ));
            coverageRows.add(buildCoverageRow(model.getName(), acceptedEntries, modelRecords));
        }

        writeAgreementCsv(agreementCsvFile, agreementRows);
        writeCoverageCsv(coverageCsvFile, coverageRows);
        writeLatexTable(latexFile, models, agreementRows, coverageRows, regularDenominatorCount, irregularDenominatorCount);
        writeSummary(summaryFile, outputRoot, goldAuditPath, acceptedAuditCount, regularDenominatorCount,
                irregularDenominatorCount, agreementCsvFile, coverageCsvFile, latexFile, agreementRows, coverageRows);

        return new EvaluationResult(
                outputRoot,
                summaryFile,
                agreementCsvFile,
                coverageCsvFile,
                latexFile,
                acceptedAuditCount,
                regularDenominatorCount,
                irregularDenominatorCount,
                agreementRows,
                coverageRows
        );
    }

    private static Path defaultGoldPath() {
        return Paths.get(System.getProperty("user.home"), ".sigmanlp", "Gold", "gold", "conjugation_audit.jsonl");
    }

    private static List<EvaluationRunner.AuditEntry> filterAcceptedConjugationAuditEntries(Map<String, EvaluationRunner.AuditEntry> entries) {
        List<EvaluationRunner.AuditEntry> result = new ArrayList<>();
        for (EvaluationRunner.AuditEntry entry : entries.values()) {
            if (entry == null) {
                continue;
            }
            if (!GenerativeEvalUtils.CONJUGATION_AUDIT_MODE.equals(entry.property)) {
                continue;
            }
            if (!"Correct".equals(entry.humanLabel)) {
                continue;
            }
            String stratum = normalizeRegularityStratum(entry.sampleStratum);
            if (stratum.isEmpty() || entry.referenceOutput == null) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private static String normalizeRegularityStratum(String raw) {
        if (raw == null) {
            return "";
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        if ("regular".equals(lowered)) {
            return STRATUM_REGULAR;
        }
        if ("irregular".equals(lowered)) {
            return STRATUM_IRREGULAR;
        }
        return "";
    }

    private static int countEntriesForStratum(List<EvaluationRunner.AuditEntry> entries, String stratum) {
        int count = 0;
        for (EvaluationRunner.AuditEntry entry : entries) {
            if (stratum.equals(normalizeRegularityStratum(entry.sampleStratum))) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, GenerativeEvalUtils.ConjugationRecord> loadModelRecordsByAuditId(Path file) throws IOException {
        Map<String, GenerativeEvalUtils.ConjugationRecord> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(file.toString());
        for (JSONObject json : loadResult.records) {
            GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
            if (record != null && !result.containsKey(record.auditId)) {
                result.put(record.auditId, record);
            }
        }
        return result;
    }

    private static List<AgreementRow> evaluateModel(String modelName,
                                                    List<EvaluationRunner.AuditEntry> acceptedEntries,
                                                    Map<String, GenerativeEvalUtils.ConjugationRecord> modelRecords,
                                                    int bootstrapResamples,
                                                    long bootstrapSeed) {
        Map<String, Map<String, List<Boolean>>> outcomes = new LinkedHashMap<>();
        for (MetricSpec metric : METRICS) {
            Map<String, List<Boolean>> byStratum = new LinkedHashMap<>();
            byStratum.put(STRATUM_REGULAR, new ArrayList<Boolean>());
            byStratum.put(STRATUM_IRREGULAR, new ArrayList<Boolean>());
            outcomes.put(metric.outputName, byStratum);
        }

        for (EvaluationRunner.AuditEntry entry : acceptedEntries) {
            String stratum = normalizeRegularityStratum(entry.sampleStratum);
            GenerativeEvalUtils.ConjugationRecord record = modelRecords.get(entry.auditId);
            Map<String, Boolean> comparison = record == null
                    ? failedConjugationComparison()
                    : GenerativeEvalUtils.compareConjugationToReference(record, entry.referenceOutput);
            for (MetricSpec metric : METRICS) {
                outcomes.get(metric.outputName).get(stratum).add(Boolean.TRUE.equals(comparison.get(metric.comparisonKey)));
            }
        }

        List<AgreementRow> rows = new ArrayList<>();
        for (MetricSpec metric : METRICS) {
            Map<String, List<Boolean>> byStratum = outcomes.get(metric.outputName);
            rows.add(buildAgreementRow(
                    modelName,
                    STRATUM_REGULAR,
                    metric.outputName,
                    byStratum.get(STRATUM_REGULAR),
                    bootstrapResamples,
                    bootstrapSeed + 13L * modelName.hashCode() + metric.outputName.hashCode()
            ));
            rows.add(buildAgreementRow(
                    modelName,
                    STRATUM_IRREGULAR,
                    metric.outputName,
                    byStratum.get(STRATUM_IRREGULAR),
                    bootstrapResamples,
                    bootstrapSeed + 17L * modelName.hashCode() + metric.outputName.hashCode()
            ));
            rows.add(buildAgreementRow(
                    modelName,
                    STRATUM_OVERALL,
                    metric.outputName,
                    merge(byStratum.get(STRATUM_REGULAR), byStratum.get(STRATUM_IRREGULAR)),
                    bootstrapResamples,
                    bootstrapSeed + 19L * modelName.hashCode() + metric.outputName.hashCode()
            ));
        }
        return rows;
    }

    private static AgreementRow buildAgreementRow(String modelName,
                                                  String stratum,
                                                  String metric,
                                                  List<Boolean> outcomes,
                                                  int bootstrapResamples,
                                                  long bootstrapSeed) {
        List<Boolean> safeOutcomes = outcomes == null ? Collections.<Boolean>emptyList() : outcomes;
        int denominator = safeOutcomes.size();
        int matches = EvaluationSupport.countTrue(safeOutcomes);
        Double rate = EvaluationSupport.rate(matches, denominator);
        EvaluationSupport.ConfidenceInterval ci = EvaluationSupport.bootstrapBinaryCI(
                safeOutcomes,
                bootstrapResamples,
                bootstrapSeed
        );
        return new AgreementRow(modelName, stratum, metric, denominator, matches, rate, ci);
    }

    private static CoverageRow buildCoverageRow(String modelName,
                                                List<EvaluationRunner.AuditEntry> acceptedEntries,
                                                Map<String, GenerativeEvalUtils.ConjugationRecord> modelRecords) {
        int denominator = acceptedEntries.size();
        int matched = 0;
        for (EvaluationRunner.AuditEntry entry : acceptedEntries) {
            if (modelRecords.containsKey(entry.auditId)) {
                matched++;
            }
        }
        return new CoverageRow(modelName, denominator, matched, EvaluationSupport.rate(matched, denominator));
    }

    private static List<Boolean> merge(List<Boolean> left, List<Boolean> right) {
        List<Boolean> result = new ArrayList<>();
        if (left != null) {
            result.addAll(left);
        }
        if (right != null) {
            result.addAll(right);
        }
        return result;
    }

    private static Map<String, Boolean> failedConjugationComparison() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put(GenerativeEvalUtils.SLOT_INFINITIVE, false);
        result.put(GenerativeEvalUtils.SLOT_PRESENT_3SG, false);
        result.put(GenerativeEvalUtils.SLOT_SIMPLE_PAST, false);
        result.put(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE, false);
        result.put(GenerativeEvalUtils.SLOT_GERUND, false);
        result.put("all_parts_exact", false);
        return result;
    }

    private static void writeAgreementCsv(Path path, List<AgreementRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,stratum,metric,denominator_n,match_n,rate,ci_lower,ci_upper\n");
        for (AgreementRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.stratum).append(",");
            sb.append(row.metric).append(",");
            sb.append(row.denominatorN).append(",");
            sb.append(row.matchCount).append(",");
            sb.append(formatRate(row.rate)).append(",");
            sb.append(formatRate(row.confidenceInterval.lower)).append(",");
            sb.append(formatRate(row.confidenceInterval.upper)).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCoverageCsv(Path path, List<CoverageRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,denominator_n,matched_records,coverage\n");
        for (CoverageRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.denominatorN).append(",");
            sb.append(row.matchedRecords).append(",");
            sb.append(formatRate(row.coverage)).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeLatexTable(Path path,
                                        List<ModelMetadata> models,
                                        List<AgreementRow> agreementRows,
                                        List<CoverageRow> coverageRows,
                                        int regularDenominatorCount,
                                        int irregularDenominatorCount) throws IOException {
        Map<String, Double> regularAllParts = indexAgreementRate(agreementRows, STRATUM_REGULAR, "all_parts_exact_match");
        Map<String, Double> irregularAllParts = indexAgreementRate(agreementRows, STRATUM_IRREGULAR, "all_parts_exact_match");
        Map<String, Double> overallAllParts = indexAgreementRate(agreementRows, STRATUM_OVERALL, "all_parts_exact_match");
        Map<String, Double> coverageByModel = new LinkedHashMap<>();
        for (CoverageRow row : coverageRows) {
            coverageByModel.put(row.model, row.coverage);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{table*}[t]\n");
        sb.append("\\centering\n");
        sb.append("\\small\n");
        sb.append("\\caption{Exact match percentages on the accepted human-audited verb conjugation benchmark stratified by regularity. Regular denominator ")
                .append(regularDenominatorCount)
                .append(". Irregular denominator ")
                .append(irregularDenominatorCount)
                .append(".}\n");
        sb.append("\\label{tab:verb-regularity-evaluation}\n");
        sb.append("\\resizebox{\\textwidth}{!}{%\n");
        sb.append("\\begin{tabular}{lrrrr}\n");
        sb.append("\\toprule\n");
        sb.append("Model & Regular all parts & Irregular all parts & Overall all parts & Coverage \\\\\n");
        sb.append("\\midrule\n");
        List<ModelMetadata> latexModels = new ArrayList<>(models);
        latexModels.sort(ModelMetadata::compareForLatex);
        ModelMetadata previous = null;
        for (ModelMetadata model : latexModels) {
            if (ModelMetadata.shouldInsertLatexSeparator(previous, model)) {
                sb.append("\\midrule\n");
            }
            String modelName = model.getName();
            sb.append("\\texttt{").append(escapeLatex(model.getLatexDisplayName())).append("}");
            sb.append(" & ").append(formatPercentCell(regularAllParts.get(modelName)));
            sb.append(" & ").append(formatPercentCell(irregularAllParts.get(modelName)));
            sb.append(" & ").append(formatPercentCell(overallAllParts.get(modelName)));
            sb.append(" & ").append(formatPercentCell(coverageByModel.get(modelName)));
            sb.append(" \\\\\n");
            previous = model;
        }
        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}%\n");
        sb.append("}\n");
        sb.append("\\end{table*}\n");
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Double> indexAgreementRate(List<AgreementRow> rows, String stratum, String metric) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (AgreementRow row : rows) {
            if (stratum.equals(row.stratum) && metric.equals(row.metric)) {
                result.put(row.model, row.rate);
            }
        }
        return result;
    }

    private static void writeSummary(Path path,
                                     Path outputRoot,
                                     Path goldAuditPath,
                                     int acceptedAuditCount,
                                     int regularDenominatorCount,
                                     int irregularDenominatorCount,
                                     Path agreementCsvFile,
                                     Path coverageCsvFile,
                                     Path latexFile,
                                     List<AgreementRow> agreementRows,
                                     List<CoverageRow> coverageRows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("VERB REGULARITY EVALUATION\n");
        sb.append("==========================\n\n");
        sb.append("Output root: ").append(outputRoot.toAbsolutePath().normalize()).append("\n");
        sb.append("Audit gold file: ").append(goldAuditPath.toAbsolutePath().normalize()).append("\n");
        sb.append("Accepted audit items: ").append(acceptedAuditCount).append("\n");
        sb.append("Regular denominator: ").append(regularDenominatorCount).append("\n");
        sb.append("Irregular denominator: ").append(irregularDenominatorCount).append("\n");
        sb.append("Agreement CSV: ").append(agreementCsvFile.toAbsolutePath().normalize()).append("\n");
        sb.append("Coverage CSV: ").append(coverageCsvFile.toAbsolutePath().normalize()).append("\n");
        sb.append("LaTeX table: ").append(latexFile.toAbsolutePath().normalize()).append("\n");
        sb.append("Agreement rows: ").append(agreementRows.size()).append("\n");
        sb.append("Coverage rows: ").append(coverageRows.size()).append("\n\n");
        sb.append("All-parts summary\n");
        sb.append("model | regular_all_parts | irregular_all_parts | overall_all_parts | coverage\n");
        Map<String, Double> regularAllParts = indexAgreementRate(agreementRows, STRATUM_REGULAR, "all_parts_exact_match");
        Map<String, Double> irregularAllParts = indexAgreementRate(agreementRows, STRATUM_IRREGULAR, "all_parts_exact_match");
        Map<String, Double> overallAllParts = indexAgreementRate(agreementRows, STRATUM_OVERALL, "all_parts_exact_match");
        for (CoverageRow row : coverageRows) {
            sb.append(row.model).append(" | ")
                    .append(formatRate(regularAllParts.get(row.model))).append(" | ")
                    .append(formatRate(irregularAllParts.get(row.model))).append(" | ")
                    .append(formatRate(overallAllParts.get(row.model))).append(" | ")
                    .append(formatRate(row.coverage)).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String formatRate(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatPercentCell(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%.2f", value * 100.0d);
    }

    private static String escapeLatex(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\textbackslash{}")
                .replace("_", "\\_")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("#", "\\#");
    }
}
