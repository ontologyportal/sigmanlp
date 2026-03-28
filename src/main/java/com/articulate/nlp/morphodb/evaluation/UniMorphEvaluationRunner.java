package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.MorphoWordNetUtils;
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
import java.util.Set;
import java.util.TreeSet;

/**
 * Evaluates MorphoDB model outputs against normalized UniMorph gold files.
 */
public class UniMorphEvaluationRunner {

    private static final String CANONICAL_METRIC_SUFFIX = "_canonical_denominator";
    private static final String EVALUATION_OUTPUT_DIR_NAME = "UniMorphEvaluation";
    private static final String NON_OVERLAPS_DIR_NAME = "non_overlaps";
    private static final String LATEX_DIR_NAME = "latex";
    private static final String LATEX_TABLE_FILE_NAME = "unimorph_canonical_agreement_table.tex";

    private static final class CanonicalFeatureSpec {
        final String baseMetric;
        final String latexLabel;
        final boolean nounFeature;

        CanonicalFeatureSpec(String baseMetric, String latexLabel, boolean nounFeature) {
            this.baseMetric = baseMetric;
            this.latexLabel = latexLabel;
            this.nounFeature = nounFeature;
        }
    }

    private static final List<CanonicalFeatureSpec> CANONICAL_REPORT_FEATURES = Arrays.asList(
            new CanonicalFeatureSpec("singular_plural_pair_exact", "Singular plural pair", true),
            new CanonicalFeatureSpec("infinitive_exact_match", "Infinitive", false),
            new CanonicalFeatureSpec("present_3sg_exact_match", "Present 3sg", false),
            new CanonicalFeatureSpec("simple_past_exact_match", "Simple past", false),
            new CanonicalFeatureSpec("past_participle_exact_match", "Past participle", false),
            new CanonicalFeatureSpec("gerund_exact_match", "Gerund", false),
            new CanonicalFeatureSpec("all_parts_exact_match", "All parts", false)
    );

    static class AgreementRow {
        final String model;
        final String property;
        final String metric;
        final int denominatorN;
        final int matchCount;
        final Double rate;
        final EvaluationSupport.ConfidenceInterval confidenceInterval;

        AgreementRow(String model,
                     String property,
                     String metric,
                     int denominatorN,
                     int matchCount,
                     Double rate,
                     EvaluationSupport.ConfidenceInterval confidenceInterval) {
            this.model = model;
            this.property = property;
            this.metric = metric;
            this.denominatorN = denominatorN;
            this.matchCount = matchCount;
            this.rate = rate;
            this.confidenceInterval = confidenceInterval;
        }
    }

    static class CoverageRow {
        final String model;
        final String property;
        final int goldTotal;
        final int modelTotal;
        final int overlapN;
        final Double coverageOfGold;
        final Double coverageOfModel;

        CoverageRow(String model,
                    String property,
                    int goldTotal,
                    int modelTotal,
                    int overlapN,
                    Double coverageOfGold,
                    Double coverageOfModel) {
            this.model = model;
            this.property = property;
            this.goldTotal = goldTotal;
            this.modelTotal = modelTotal;
            this.overlapN = overlapN;
            this.coverageOfGold = coverageOfGold;
            this.coverageOfModel = coverageOfModel;
        }
    }

    static class EvaluationResult {
        final List<AgreementRow> nounAgreementRows;
        final List<AgreementRow> verbAgreementRows;
        final List<CoverageRow> coverageRows;
        final int nounGoldCount;
        final int verbGoldCount;
        final int canonicalNounDenominatorCount;
        final int canonicalVerbDenominatorCount;

        EvaluationResult(List<AgreementRow> nounAgreementRows,
                         List<AgreementRow> verbAgreementRows,
                         List<CoverageRow> coverageRows,
                         int nounGoldCount,
                         int verbGoldCount,
                         int canonicalNounDenominatorCount,
                         int canonicalVerbDenominatorCount) {
            this.nounAgreementRows = nounAgreementRows;
            this.verbAgreementRows = verbAgreementRows;
            this.coverageRows = coverageRows;
            this.nounGoldCount = nounGoldCount;
            this.verbGoldCount = verbGoldCount;
            this.canonicalNounDenominatorCount = canonicalNounDenominatorCount;
            this.canonicalVerbDenominatorCount = canonicalVerbDenominatorCount;
        }
    }

    public static void main(String[] args) {
        try {
            Map<String, String> cli = EvaluationSupport.parseArgs(args);
            String inputDir = cli.get("input");
            String goldDir = cli.getOrDefault("gold-dir", ".");
            String outputDir = cli.getOrDefault("output", ".");
            int bootstrapResamples = EvaluationSupport.parseInt(cli.get("bootstrap-resamples"), 1000);
            long bootstrapSeed = EvaluationSupport.parseLong(cli.get("bootstrap-seed"), 42L);

            if (inputDir == null || inputDir.trim().isEmpty()) {
                System.err.println("Usage: java ... UniMorphEvaluationRunner --input <path> [--gold-dir <path>] [--output <path>] [--bootstrap-resamples <n>] [--bootstrap-seed <seed>]");
                System.err.println("  Canonical-denominator UniMorph metrics require SIGMA_HOME so filtered WordNet lemma sets can be loaded.");
                System.exit(1);
            }

            EvaluationResult result = run(
                    Paths.get(inputDir),
                    Paths.get(goldDir),
                    Paths.get(outputDir),
                    bootstrapResamples,
                    bootstrapSeed
            );
            System.out.println("UniMorph evaluation complete.");
            System.out.println("  noun gold records: " + result.nounGoldCount);
            System.out.println("  verb gold records: " + result.verbGoldCount);
            System.out.println("  output: " + resolveEvaluationOutputRoot(Paths.get(outputDir)).toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("Error during UniMorph evaluation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static EvaluationResult run(Path inputDir,
                                Path goldDir,
                                Path outputDir,
                                int bootstrapResamples,
                                long bootstrapSeed) throws IOException {
        MorphoWordNetUtils.LemmaSets filteredLemmaSets = MorphoWordNetUtils.loadFilteredNormalizedNounVerbLemmaSets();
        return run(inputDir, goldDir, outputDir, bootstrapResamples, bootstrapSeed,
                filteredLemmaSets.nounLemmas,
                filteredLemmaSets.verbLemmas);
    }

    static EvaluationResult run(Path inputDir,
                                Path goldDir,
                                Path outputDir,
                                int bootstrapResamples,
                                long bootstrapSeed,
                                Set<String> filteredWordNetNounLemmas,
                                Set<String> filteredWordNetVerbLemmas) throws IOException {
        Path nounGoldPath = goldDir.resolve(UniMorphGoldUtils.NOUN_OUTPUT_FILE);
        Path verbGoldPath = goldDir.resolve(UniMorphGoldUtils.VERB_OUTPUT_FILE);
        if (!Files.exists(nounGoldPath)) {
            throw new IOException("UniMorph noun gold file not found: " + nounGoldPath);
        }
        if (!Files.exists(verbGoldPath)) {
            throw new IOException("UniMorph verb gold file not found: " + verbGoldPath);
        }

        List<ModelMetadata> models = EvaluationSupport.scanModelDirectories(inputDir.toString());
        if (models.isEmpty()) {
            throw new IOException("No model directories found under " + inputDir);
        }

        Map<String, UniMorphGoldUtils.NounGoldRecord> nounGold = UniMorphGoldUtils.loadNounGold(nounGoldPath);
        Map<String, UniMorphGoldUtils.VerbGoldRecord> verbGold = UniMorphGoldUtils.loadVerbGold(verbGoldPath);
        Set<String> canonicalNounLemmas = sortedIntersection(nounGold.keySet(), filteredWordNetNounLemmas);
        Set<String> canonicalVerbLemmas = sortedIntersection(verbGold.keySet(), filteredWordNetVerbLemmas);
        Path evaluationOutputRoot = resolveEvaluationOutputRoot(outputDir);
        Path tablesDir = evaluationOutputRoot.resolve("tables");
        Path nonOverlapsDir = evaluationOutputRoot.resolve(NON_OVERLAPS_DIR_NAME);
        Path latexDir = evaluationOutputRoot.resolve(LATEX_DIR_NAME);
        Path latexTablePath = latexDir.resolve(LATEX_TABLE_FILE_NAME);
        Files.createDirectories(tablesDir);
        Files.createDirectories(nonOverlapsDir);
        Files.createDirectories(latexDir);

        List<AgreementRow> nounAgreementRows = new ArrayList<>();
        List<AgreementRow> verbAgreementRows = new ArrayList<>();
        List<CoverageRow> coverageRows = new ArrayList<>();

        for (ModelMetadata model : models) {
            Map<String, GenerativeEvalUtils.PluralRecord> pluralRecords = loadModelPluralRecords(
                    inputDir.resolve(model.getDirectoryName()).resolve("noun/Plurals.txt")
            );
            Map<String, GenerativeEvalUtils.ConjugationRecord> conjugationRecords = loadModelVerbRecords(
                    inputDir.resolve(model.getDirectoryName()).resolve("verb/VerbConjugations.txt")
            );
            writeNonOverlapFilesForModel(
                    nonOverlapsDir.resolve(model.getName()),
                    model.getName(),
                    canonicalNounLemmas,
                    pluralRecords.keySet(),
                    canonicalVerbLemmas,
                    conjugationRecords.keySet()
            );

            coverageRows.add(buildCoverageRow(model.getName(), UniMorphGoldUtils.NOUN_PROPERTY, nounGold.keySet(), pluralRecords.keySet()));
            coverageRows.add(buildCoverageRow(model.getName(), UniMorphGoldUtils.VERB_PROPERTY, verbGold.keySet(), conjugationRecords.keySet()));

            nounAgreementRows.addAll(buildNounAgreementRows(
                    model.getName(),
                    nounGold,
                    pluralRecords,
                    bootstrapResamples,
                    bootstrapSeed
            ));
            nounAgreementRows.addAll(buildCanonicalNounAgreementRows(
                    model.getName(),
                    nounGold,
                    pluralRecords,
                    canonicalNounLemmas,
                    bootstrapResamples,
                    bootstrapSeed
            ));
            verbAgreementRows.addAll(buildVerbAgreementRows(
                    model.getName(),
                    verbGold,
                    conjugationRecords,
                    bootstrapResamples,
                    bootstrapSeed
            ));
            verbAgreementRows.addAll(buildCanonicalVerbAgreementRows(
                    model.getName(),
                    verbGold,
                    conjugationRecords,
                    canonicalVerbLemmas,
                    bootstrapResamples,
                    bootstrapSeed
            ));
        }

        exportAgreementRows(tablesDir.resolve("unimorph_noun_agreement.csv"), nounAgreementRows);
        exportAgreementRows(tablesDir.resolve("unimorph_verb_agreement.csv"), verbAgreementRows);
        exportCoverageRows(tablesDir.resolve("unimorph_coverage.csv"), coverageRows);
        writeCanonicalAgreementLatexTable(
                latexTablePath,
                models,
                nounAgreementRows,
                verbAgreementRows,
                canonicalNounLemmas.size(),
                canonicalVerbLemmas.size()
        );
        writeSummary(evaluationOutputRoot.resolve("summary_unimorph.txt"),
                evaluationOutputRoot,
                tablesDir,
                nonOverlapsDir,
                latexTablePath,
                models,
                nounGold.size(),
                verbGold.size(),
                canonicalNounLemmas.size(),
                canonicalVerbLemmas.size(),
                nounAgreementRows,
                verbAgreementRows,
                coverageRows);

        return new EvaluationResult(
                nounAgreementRows,
                verbAgreementRows,
                coverageRows,
                nounGold.size(),
                verbGold.size(),
                canonicalNounLemmas.size(),
                canonicalVerbLemmas.size()
        );
    }

    private static List<AgreementRow> buildNounAgreementRows(String modelName,
                                                             Map<String, UniMorphGoldUtils.NounGoldRecord> goldRecords,
                                                             Map<String, GenerativeEvalUtils.PluralRecord> modelRecords,
                                                             int bootstrapResamples,
                                                             long bootstrapSeed) {
        List<String> overlap = sortedOverlap(goldRecords.keySet(), modelRecords.keySet());
        List<Boolean> pluralOutcomes = new ArrayList<>();
        List<Boolean> pairOutcomes = new ArrayList<>();

        for (String lemma : overlap) {
            UniMorphGoldUtils.NounGoldRecord gold = goldRecords.get(lemma);
            GenerativeEvalUtils.PluralRecord model = modelRecords.get(lemma);
            boolean pluralExact = gold.pluralVariants.contains(model.pluralNormalized);
            boolean pairExact = pluralExact && gold.singularVariants.contains(model.singularNormalized);
            pluralOutcomes.add(pluralExact);
            pairOutcomes.add(pairExact);
        }

        List<AgreementRow> rows = new ArrayList<>();
        rows.add(buildAgreementRow(
                modelName,
                UniMorphGoldUtils.NOUN_PROPERTY,
                "plural_exact_match",
                pluralOutcomes,
                bootstrapResamples,
                bootstrapSeed + 31L * modelName.hashCode() + 7L
        ));
        rows.add(buildAgreementRow(
                modelName,
                UniMorphGoldUtils.NOUN_PROPERTY,
                "singular_plural_pair_exact",
                pairOutcomes,
                bootstrapResamples,
                bootstrapSeed + 31L * modelName.hashCode() + 13L
        ));
        return rows;
    }

    private static List<AgreementRow> buildCanonicalNounAgreementRows(String modelName,
                                                                      Map<String, UniMorphGoldUtils.NounGoldRecord> goldRecords,
                                                                      Map<String, GenerativeEvalUtils.PluralRecord> modelRecords,
                                                                      Set<String> canonicalLemmas,
                                                                      int bootstrapResamples,
                                                                      long bootstrapSeed) {
        List<String> lemmas = sortedLemmas(canonicalLemmas);
        List<Boolean> pluralOutcomes = new ArrayList<>();
        List<Boolean> pairOutcomes = new ArrayList<>();

        for (String lemma : lemmas) {
            UniMorphGoldUtils.NounGoldRecord gold = goldRecords.get(lemma);
            GenerativeEvalUtils.PluralRecord model = modelRecords.get(lemma);
            boolean pluralExact = model != null && gold != null && gold.pluralVariants.contains(model.pluralNormalized);
            boolean pairExact = model != null
                    && gold != null
                    && pluralExact
                    && gold.singularVariants.contains(model.singularNormalized);
            pluralOutcomes.add(pluralExact);
            pairOutcomes.add(pairExact);
        }

        List<AgreementRow> rows = new ArrayList<>();
        rows.add(buildAgreementRow(
                modelName,
                UniMorphGoldUtils.NOUN_PROPERTY,
                canonicalMetricName("plural_exact_match"),
                pluralOutcomes,
                bootstrapResamples,
                bootstrapSeed + 31L * modelName.hashCode() + 17L
        ));
        rows.add(buildAgreementRow(
                modelName,
                UniMorphGoldUtils.NOUN_PROPERTY,
                canonicalMetricName("singular_plural_pair_exact"),
                pairOutcomes,
                bootstrapResamples,
                bootstrapSeed + 31L * modelName.hashCode() + 23L
        ));
        return rows;
    }

    private static List<AgreementRow> buildVerbAgreementRows(String modelName,
                                                             Map<String, UniMorphGoldUtils.VerbGoldRecord> goldRecords,
                                                             Map<String, GenerativeEvalUtils.ConjugationRecord> modelRecords,
                                                             int bootstrapResamples,
                                                             long bootstrapSeed) {
        List<String> overlap = sortedOverlap(goldRecords.keySet(), modelRecords.keySet());
        Map<String, List<Boolean>> outcomesByMetric = new LinkedHashMap<>();
        for (String slot : UniMorphGoldUtils.VERB_SLOTS) {
            outcomesByMetric.put(slot, new ArrayList<>());
        }
        outcomesByMetric.put("all_parts_exact_match", new ArrayList<>());

        for (String lemma : overlap) {
            UniMorphGoldUtils.VerbGoldRecord gold = goldRecords.get(lemma);
            GenerativeEvalUtils.ConjugationRecord model = modelRecords.get(lemma);
            boolean allMatch = true;
            for (String slot : UniMorphGoldUtils.VERB_SLOTS) {
                String predicted = model.normalizedForms.get(slot);
                boolean slotMatch = gold.slotVariants.get(slot).contains(predicted);
                outcomesByMetric.get(slot).add(slotMatch);
                allMatch &= slotMatch;
            }
            outcomesByMetric.get("all_parts_exact_match").add(allMatch);
        }

        List<AgreementRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Boolean>> entry : outcomesByMetric.entrySet()) {
            rows.add(buildAgreementRow(
                    modelName,
                    UniMorphGoldUtils.VERB_PROPERTY,
                    metricNameForSlot(entry.getKey()),
                    entry.getValue(),
                    bootstrapResamples,
                    bootstrapSeed + 101L * modelName.hashCode() + entry.getKey().hashCode()
            ));
        }
        return rows;
    }

    private static List<AgreementRow> buildCanonicalVerbAgreementRows(String modelName,
                                                                      Map<String, UniMorphGoldUtils.VerbGoldRecord> goldRecords,
                                                                      Map<String, GenerativeEvalUtils.ConjugationRecord> modelRecords,
                                                                      Set<String> canonicalLemmas,
                                                                      int bootstrapResamples,
                                                                      long bootstrapSeed) {
        List<String> lemmas = sortedLemmas(canonicalLemmas);
        Map<String, List<Boolean>> outcomesByMetric = new LinkedHashMap<>();
        for (String slot : UniMorphGoldUtils.VERB_SLOTS) {
            outcomesByMetric.put(slot, new ArrayList<>());
        }
        outcomesByMetric.put("all_parts_exact_match", new ArrayList<>());

        for (String lemma : lemmas) {
            UniMorphGoldUtils.VerbGoldRecord gold = goldRecords.get(lemma);
            GenerativeEvalUtils.ConjugationRecord model = modelRecords.get(lemma);
            boolean allMatch = model != null && gold != null;
            for (String slot : UniMorphGoldUtils.VERB_SLOTS) {
                String predicted = model == null ? null : model.normalizedForms.get(slot);
                boolean slotMatch = model != null
                        && gold != null
                        && gold.slotVariants.get(slot).contains(predicted);
                outcomesByMetric.get(slot).add(slotMatch);
                allMatch &= slotMatch;
            }
            outcomesByMetric.get("all_parts_exact_match").add(allMatch);
        }

        List<AgreementRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<Boolean>> entry : outcomesByMetric.entrySet()) {
            rows.add(buildAgreementRow(
                    modelName,
                    UniMorphGoldUtils.VERB_PROPERTY,
                    canonicalMetricName(metricNameForSlot(entry.getKey())),
                    entry.getValue(),
                    bootstrapResamples,
                    bootstrapSeed + 131L * modelName.hashCode() + entry.getKey().hashCode()
            ));
        }
        return rows;
    }

    private static AgreementRow buildAgreementRow(String modelName,
                                                  String property,
                                                  String metric,
                                                  List<Boolean> outcomes,
                                                  int bootstrapResamples,
                                                  long seed) {
        int matches = EvaluationSupport.countTrue(outcomes);
        int denominator = outcomes.size();
        return new AgreementRow(
                modelName,
                property,
                metric,
                denominator,
                matches,
                EvaluationSupport.rate(matches, denominator),
                EvaluationSupport.bootstrapBinaryCI(outcomes, bootstrapResamples, seed)
        );
    }

    private static CoverageRow buildCoverageRow(String modelName,
                                                String property,
                                                Set<String> goldKeys,
                                                Set<String> modelKeys) {
        Set<String> overlap = new TreeSet<>(goldKeys);
        overlap.retainAll(modelKeys);
        int overlapN = overlap.size();
        return new CoverageRow(
                modelName,
                property,
                goldKeys.size(),
                modelKeys.size(),
                overlapN,
                EvaluationSupport.rate(overlapN, goldKeys.size()),
                EvaluationSupport.rate(overlapN, modelKeys.size())
        );
    }

    private static List<String> sortedOverlap(Set<String> goldKeys, Set<String> modelKeys) {
        List<String> overlap = new ArrayList<>(goldKeys);
        overlap.retainAll(modelKeys);
        Collections.sort(overlap);
        return overlap;
    }

    private static Set<String> sortedIntersection(Set<String> left, Set<String> right) {
        Set<String> intersection = new TreeSet<>(left);
        if (right == null) {
            intersection.clear();
            return intersection;
        }
        intersection.retainAll(right);
        return intersection;
    }

    private static List<String> sortedLemmas(Set<String> lemmas) {
        List<String> result = new ArrayList<>(lemmas);
        Collections.sort(result);
        return result;
    }

    private static List<String> sortedDifference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected == null ? Collections.<String>emptySet() : expected);
        missing.removeAll(actual == null ? Collections.<String>emptySet() : actual);
        return new ArrayList<>(missing);
    }

    private static Path resolveEvaluationOutputRoot(Path outputDir) {
        return outputDir.resolve(EVALUATION_OUTPUT_DIR_NAME);
    }

    private static void writeNonOverlapFilesForModel(Path modelOutputDir,
                                                     String modelName,
                                                     Set<String> canonicalNounLemmas,
                                                     Set<String> modelNounLemmas,
                                                     Set<String> canonicalVerbLemmas,
                                                     Set<String> modelVerbLemmas) throws IOException {
        Files.createDirectories(modelOutputDir);
        for (CanonicalFeatureSpec feature : CANONICAL_REPORT_FEATURES) {
            Set<String> expected = feature.nounFeature ? canonicalNounLemmas : canonicalVerbLemmas;
            Set<String> present = feature.nounFeature ? modelNounLemmas : modelVerbLemmas;
            List<String> missing = sortedDifference(expected, present);

            StringBuilder sb = new StringBuilder();
            sb.append("model: ").append(modelName).append("\n");
            sb.append("metric: ").append(feature.baseMetric).append("\n");
            sb.append("canonical denominator: ").append(expected.size()).append("\n");
            sb.append("missing lemma count: ").append(missing.size()).append("\n\n");
            for (String lemma : missing) {
                sb.append(lemma).append("\n");
            }

            Files.write(
                    modelOutputDir.resolve(feature.baseMetric + "_non_overlaps.txt"),
                    sb.toString().getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private static Map<String, GenerativeEvalUtils.PluralRecord> loadModelPluralRecords(Path file) throws IOException {
        Map<String, GenerativeEvalUtils.PluralRecord> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(file.toString());
        for (JSONObject json : loadResult.records) {
            GenerativeEvalUtils.PluralRecord record = GenerativeEvalUtils.extractPluralRecord(json);
            if (record != null) {
                result.put(record.lemma, record);
            }
        }
        return result;
    }

    private static Map<String, GenerativeEvalUtils.ConjugationRecord> loadModelVerbRecords(Path file) throws IOException {
        Map<String, GenerativeEvalUtils.ConjugationRecord> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(file.toString());
        for (JSONObject json : loadResult.records) {
            GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
            if (record != null) {
                result.put(record.lemma, record);
            }
        }
        return result;
    }

    private static String metricNameForSlot(String slot) {
        if ("all_parts_exact_match".equals(slot)) {
            return slot;
        }
        if (GenerativeEvalUtils.SLOT_PRESENT_3SG.equals(slot)) {
            return "present_3sg_exact_match";
        }
        if (GenerativeEvalUtils.SLOT_SIMPLE_PAST.equals(slot)) {
            return "simple_past_exact_match";
        }
        if (GenerativeEvalUtils.SLOT_PAST_PARTICIPLE.equals(slot)) {
            return "past_participle_exact_match";
        }
        if (GenerativeEvalUtils.SLOT_GERUND.equals(slot)) {
            return "gerund_exact_match";
        }
        if (GenerativeEvalUtils.SLOT_INFINITIVE.equals(slot)) {
            return "infinitive_exact_match";
        }
        return slot + "_exact_match";
    }

    private static void exportAgreementRows(Path path, List<AgreementRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,property,metric,denominator_n,match_n,agreement_rate,ci_low,ci_high\n");
        for (AgreementRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.property).append(",");
            sb.append(row.metric).append(",");
            sb.append(row.denominatorN).append(",");
            sb.append(row.matchCount).append(",");
            sb.append(EvaluationSupport.formatMaybeDouble(row.rate)).append(",");
            sb.append(EvaluationSupport.formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.lower)).append(",");
            sb.append(EvaluationSupport.formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.upper)).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void exportCoverageRows(Path path, List<CoverageRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,property,gold_total,model_total,overlap_n,coverage_of_gold,coverage_of_model\n");
        for (CoverageRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.property).append(",");
            sb.append(row.goldTotal).append(",");
            sb.append(row.modelTotal).append(",");
            sb.append(row.overlapN).append(",");
            sb.append(EvaluationSupport.formatMaybeDouble(row.coverageOfGold)).append(",");
            sb.append(EvaluationSupport.formatMaybeDouble(row.coverageOfModel)).append("\n");
        }
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSummary(Path path,
                                     Path evaluationOutputRoot,
                                     Path tablesDir,
                                     Path nonOverlapsDir,
                                     Path latexTablePath,
                                     List<ModelMetadata> models,
                                     int nounGoldCount,
                                     int verbGoldCount,
                                     int canonicalNounDenominatorCount,
                                     int canonicalVerbDenominatorCount,
                                     List<AgreementRow> nounAgreementRows,
                                     List<AgreementRow> verbAgreementRows,
                                     List<CoverageRow> coverageRows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("UNIMORPH EVALUATION SUMMARY\n");
        sb.append("==========================\n\n");
        sb.append("Output root: ").append(evaluationOutputRoot.toAbsolutePath().normalize()).append("\n");
        sb.append("Tables directory: ").append(tablesDir.toAbsolutePath().normalize()).append("\n");
        sb.append("Non-overlaps directory: ").append(nonOverlapsDir.toAbsolutePath().normalize()).append("\n");
        sb.append("LaTeX table: ").append(latexTablePath.toAbsolutePath().normalize()).append("\n\n");
        sb.append("Models evaluated: ").append(models.size()).append("\n");
        sb.append("Noun gold records: ").append(nounGoldCount).append("\n");
        sb.append("Verb gold records: ").append(verbGoldCount).append("\n");
        sb.append("Canonical noun denominator: ").append(canonicalNounDenominatorCount).append("\n");
        sb.append("Canonical verb denominator: ").append(canonicalVerbDenominatorCount).append("\n");
        sb.append("Noun agreement rows: ").append(nounAgreementRows.size()).append("\n");
        sb.append("Verb agreement rows: ").append(verbAgreementRows.size()).append("\n");
        sb.append("Noun agreement rows (overlap-conditioned): ").append(countAgreementRows(nounAgreementRows, false)).append("\n");
        sb.append("Noun agreement rows (canonical-denominator): ").append(countAgreementRows(nounAgreementRows, true)).append("\n");
        sb.append("Verb agreement rows (overlap-conditioned): ").append(countAgreementRows(verbAgreementRows, false)).append("\n");
        sb.append("Verb agreement rows (canonical-denominator): ").append(countAgreementRows(verbAgreementRows, true)).append("\n");
        sb.append("Coverage rows: ").append(coverageRows.size()).append("\n");
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeCanonicalAgreementLatexTable(Path path,
                                                          List<ModelMetadata> models,
                                                          List<AgreementRow> nounAgreementRows,
                                                          List<AgreementRow> verbAgreementRows,
                                                          int canonicalNounDenominatorCount,
                                                          int canonicalVerbDenominatorCount) throws IOException {
        Map<String, Map<String, Double>> ratesByModel = new LinkedHashMap<>();
        indexCanonicalAgreementRates(ratesByModel, nounAgreementRows);
        indexCanonicalAgreementRates(ratesByModel, verbAgreementRows);

        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{table*}[t]\n");
        sb.append("\\centering\n");
        sb.append("\\scriptsize\n");
        sb.append("\\caption{Canonical-denominator UniMorph exact match percentages by model. Noun denominator ")
                .append(canonicalNounDenominatorCount)
                .append(". Verb denominator ")
                .append(canonicalVerbDenominatorCount)
                .append(".}\n");
        sb.append("\\label{tab:unimorph-canonical-agreement}\n");
        sb.append("\\resizebox{\\textwidth}{!}{%\n");
        sb.append("\\begin{tabular}{lrrrrrrr}\n");
        sb.append("\\toprule\n");
        sb.append("Model");
        for (CanonicalFeatureSpec feature : CANONICAL_REPORT_FEATURES) {
            sb.append(" & ").append(feature.latexLabel);
        }
        sb.append(" \\\\\n");
        sb.append("\\midrule\n");
        for (ModelMetadata model : models) {
            String modelName = model.getName();
            Map<String, Double> modelRates = ratesByModel.getOrDefault(modelName, Collections.<String, Double>emptyMap());
            sb.append("\\texttt{").append(escapeLatex(modelName)).append("}");
            for (CanonicalFeatureSpec feature : CANONICAL_REPORT_FEATURES) {
                sb.append(" & ").append(formatLatexPercent(modelRates.get(canonicalMetricName(feature.baseMetric))));
            }
            sb.append(" \\\\\n");
        }
        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}%\n");
        sb.append("}\n");
        sb.append("\\end{table*}\n");
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void indexCanonicalAgreementRates(Map<String, Map<String, Double>> ratesByModel,
                                                     List<AgreementRow> rows) {
        for (AgreementRow row : rows) {
            if (!row.metric.endsWith(CANONICAL_METRIC_SUFFIX)) {
                continue;
            }
            ratesByModel.computeIfAbsent(row.model, ignored -> new LinkedHashMap<>()).put(row.metric, row.rate);
        }
    }

    private static String canonicalMetricName(String metric) {
        return metric + CANONICAL_METRIC_SUFFIX;
    }

    private static int countAgreementRows(List<AgreementRow> rows, boolean canonical) {
        int count = 0;
        for (AgreementRow row : rows) {
            boolean rowIsCanonical = row.metric.endsWith(CANONICAL_METRIC_SUFFIX);
            if (rowIsCanonical == canonical) {
                count++;
            }
        }
        return count;
    }

    private static String formatLatexPercent(Double rate) {
        if (rate == null || Double.isNaN(rate) || Double.isInfinite(rate)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%.2f", rate * 100.0);
    }

    private static String escapeLatex(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\textbackslash{}")
                .replace("_", "\\_")
                .replace("%", "\\%")
                .replace("&", "\\&")
                .replace("#", "\\#")
                .replace("$", "\\$")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }
}
