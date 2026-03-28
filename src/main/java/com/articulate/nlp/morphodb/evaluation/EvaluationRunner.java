package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.MorphoCategoricalSchema;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Entry point for categorical and generative MorphoDB evaluation.
 */
public class EvaluationRunner {

    // Keep multi-rater agreement support available in InterRaterStats, but do not emit
    // the Fleiss'/Krippendorff runner output until we decide to restore it.
    static final boolean INCLUDE_MULTI_RATER_AGREEMENT_OUTPUTS = false;

    static class CategoricalSpec {
        final String fileName;
        final String wordClass;
        final String propertyName;
        final String goldKey;
        final String modelField;

        CategoricalSpec(String fileName, String wordClass, String propertyName, String goldKey, String modelField) {
            this.fileName = fileName;
            this.wordClass = wordClass;
            this.propertyName = propertyName;
            this.goldKey = goldKey;
            this.modelField = modelField;
        }
    }

    static class GroupedKappaColumn {
        final String propertyName;
        final String displayName;

        GroupedKappaColumn(String propertyName, String displayName) {
            this.propertyName = propertyName;
            this.displayName = displayName;
        }
    }

    static class GenerativeSpec {
        final String fileName;
        final String wordClass;
        final String propertyName;
        final String auditFileName;
        final String auditMode;

        GenerativeSpec(String fileName, String wordClass, String propertyName, String auditFileName, String auditMode) {
            this.fileName = fileName;
            this.wordClass = wordClass;
            this.propertyName = propertyName;
            this.auditFileName = auditFileName;
            this.auditMode = auditMode;
        }
    }

    static class SourceSpec {
        final String sourceName;
        final String fileName;
        final boolean categoricalSummary;

        SourceSpec(String sourceName, String fileName, boolean categoricalSummary) {
            this.sourceName = sourceName;
            this.fileName = fileName;
            this.categoricalSummary = categoricalSummary;
        }
    }

    static class FileStats {
        final int totalRecords;
        final int errorRecords;
        final int parseFailures;

        FileStats(int totalRecords, int errorRecords, int parseFailures) {
            this.totalRecords = totalRecords;
            this.errorRecords = errorRecords;
            this.parseFailures = parseFailures;
        }

        double errorRatePercent() {
            if (totalRecords <= 0) {
                return Double.NaN;
            }
            return (double) errorRecords / totalRecords * 100.0;
        }
    }

    static class PropertyAgreementStats {
        String propertyName;
        String wordClass;
        int itemCount;
        double krippendorffAlpha;
        double fleissKappa;
        double percentAgreement;
        double avgErrorRate;
    }

    static class ConfidenceInterval {
        final Double lower;
        final Double upper;

        ConfidenceInterval(Double lower, Double upper) {
            this.lower = lower;
            this.upper = upper;
        }
    }

    static class CategoricalRow {
        final String model;
        final String property;
        final String wordClass;
        Double kappaGold;
        int nGold;
        boolean lowNGold;
        Double kappaReference;
        int nReference;
        boolean lowNReference;
        Double deltaKappaGold;
        int nDelta;
        boolean lowNDelta;
        ConfidenceInterval deltaCI;

        CategoricalRow(String model, String property, String wordClass) {
            this.model = model;
            this.property = property;
            this.wordClass = wordClass;
        }
    }

    static class ModelSummaryRow {
        final ModelMetadata metadata;
        final Map<String, Double> kappaByWordClass = new LinkedHashMap<>();
        Double avgKappa;
        final Map<String, Double> errorRateBySource = new LinkedHashMap<>();
        Double avgErrorRate;

        ModelSummaryRow(ModelMetadata metadata) {
            this.metadata = metadata;
        }
    }

    static class AuditEntry {
        final String auditId;
        final String property;
        final String referenceModel;
        final String sampleStratum;
        final String synsetId;
        final String lemma;
        final String humanLabel;
        final JSONObject referenceOutput;

        AuditEntry(String auditId,
                   String property,
                   String referenceModel,
                   String sampleStratum,
                   String synsetId,
                   String lemma,
                   String humanLabel,
                   JSONObject referenceOutput) {
            this.auditId = auditId;
            this.property = property;
            this.referenceModel = referenceModel;
            this.sampleStratum = sampleStratum;
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.humanLabel = humanLabel;
            this.referenceOutput = referenceOutput;
        }
    }

    static class GenerativeCorpusContext {
        final FileStats fileStats;
        final Map<String, Double> stratumWeights;
        final int extractableCount;

        GenerativeCorpusContext(FileStats fileStats,
                                Map<String, Double> stratumWeights,
                                int extractableCount) {
            this.fileStats = fileStats;
            this.stratumWeights = stratumWeights;
            this.extractableCount = extractableCount;
        }
    }

    static class GenerativeAuditRow {
        final String property;
        final String rowType;
        final String stratum;
        final int auditedN;
        final int acceptedN;
        final Double rate;
        final ConfidenceInterval confidenceInterval;
        final Double corpusWeight;
        final FileStats sourceStats;
        final Integer extractableCount;
        final Double parseSuccessRate;

        GenerativeAuditRow(String property,
                           String rowType,
                           String stratum,
                           int auditedN,
                           int acceptedN,
                           Double rate,
                           ConfidenceInterval confidenceInterval,
                           Double corpusWeight,
                           FileStats sourceStats,
                           Integer extractableCount,
                           Double parseSuccessRate) {
            this.property = property;
            this.rowType = rowType;
            this.stratum = stratum;
            this.auditedN = auditedN;
            this.acceptedN = acceptedN;
            this.rate = rate;
            this.confidenceInterval = confidenceInterval;
            this.corpusWeight = corpusWeight;
            this.sourceStats = sourceStats;
            this.extractableCount = extractableCount;
            this.parseSuccessRate = parseSuccessRate;
        }
    }

    static class GenerativeAgreementRow {
        final String model;
        final String property;
        final String scope;
        final String metric;
        final String rowType;
        final String stratum;
        final int denominatorN;
        final int matchCount;
        final Double rate;
        final ConfidenceInterval confidenceInterval;
        final Double corpusWeight;

        GenerativeAgreementRow(String model,
                               String property,
                               String scope,
                               String metric,
                               String rowType,
                               String stratum,
                               int denominatorN,
                               int matchCount,
                               Double rate,
                               ConfidenceInterval confidenceInterval,
                               Double corpusWeight) {
            this.model = model;
            this.property = property;
            this.scope = scope;
            this.metric = metric;
            this.rowType = rowType;
            this.stratum = stratum;
            this.denominatorN = denominatorN;
            this.matchCount = matchCount;
            this.rate = rate;
            this.confidenceInterval = confidenceInterval;
            this.corpusWeight = corpusWeight;
        }
    }

    static final List<CategoricalSpec> CATEGORICAL_SPECS = Arrays.asList(
            new CategoricalSpec("noun/Countability.txt", "noun", "Countability", "countability", "countability"),
            new CategoricalSpec("noun/Humanness.txt", "noun", "Humanness", "humanness", "classification"),
            new CategoricalSpec("noun/NounAgentivity.txt", "noun", "NounAgentivity", "agentivity", "agency"),
            new CategoricalSpec("noun/CollectiveNouns.txt", "noun", "CollectiveNouns", "collective", "collective"),
            new CategoricalSpec("noun/IndefiniteArticles.txt", "noun", "IndefiniteArticles", "indefinite_article", "article"),
            new CategoricalSpec("verb/VerbValence.txt", "verb", "VerbValence", "valence", "valence"),
            new CategoricalSpec("verb/VerbReflexive.txt", "verb", "VerbReflexive", "reflexivity", "reflexivity"),
            new CategoricalSpec("verb/VerbCausativity.txt", "verb", "VerbCausativity", "causativity", "causativity"),
            new CategoricalSpec("verb/VerbAchievementProcess.txt", "verb", "VerbAchievementProcess", "aktionsart", "aktionsart"),
            new CategoricalSpec("verb/VerbReciprocal.txt", "verb", "VerbReciprocal", "reciprocity", "reciprocity"),
            new CategoricalSpec("verb/VerbConjugations.txt", "verb", "ConjugationRegularity", "conjugation_regularity", "regularity"),
            new CategoricalSpec("adjective/AdjectiveSemanticClasses.txt", "adjective", "AdjectiveSemanticClasses", "adjective_category", "category"),
            new CategoricalSpec("adverb/AdverbSemanticClasses.txt", "adverb", "AdverbSemanticClasses", "adverb_category", "category")
    );

    static final List<GroupedKappaColumn> GROUPED_KAPPA_COLUMNS = Arrays.asList(
            new GroupedKappaColumn("IndefiniteArticles", "Indefinite Articles"),
            new GroupedKappaColumn("Countability", "Countability"),
            new GroupedKappaColumn("Humanness", "Humanness"),
            new GroupedKappaColumn("NounAgentivity", "Agentivity"),
            new GroupedKappaColumn("CollectiveNouns", "Collective Nouns"),
            new GroupedKappaColumn("VerbValence", "Valence"),
            new GroupedKappaColumn("VerbReflexive", "Reflexive"),
            new GroupedKappaColumn("VerbCausativity", "Causativity"),
            new GroupedKappaColumn("VerbAchievementProcess", "Achievement Process"),
            new GroupedKappaColumn("VerbReciprocal", "Reciprocal"),
            new GroupedKappaColumn("ConjugationRegularity", "Conjugation Regularity"),
            new GroupedKappaColumn("AdjectiveSemanticClasses", "Semantic Classes"),
            new GroupedKappaColumn("AdverbSemanticClasses", "Semantic Classes")
    );

    static final List<GenerativeSpec> GENERATIVE_SPECS = Arrays.asList(
            new GenerativeSpec("noun/Plurals.txt", "noun", "Plurals", "plural_audit.jsonl", GenerativeEvalUtils.PLURAL_AUDIT_MODE),
            new GenerativeSpec("verb/VerbConjugations.txt", "verb", "VerbConjugations", "conjugation_audit.jsonl", GenerativeEvalUtils.CONJUGATION_AUDIT_MODE)
    );

    static final List<SourceSpec> SOURCE_SPECS = Arrays.asList(
            new SourceSpec("Countability", "noun/Countability.txt", true),
            new SourceSpec("Humanness", "noun/Humanness.txt", true),
            new SourceSpec("NounAgentivity", "noun/NounAgentivity.txt", true),
            new SourceSpec("CollectiveNouns", "noun/CollectiveNouns.txt", true),
            new SourceSpec("IndefiniteArticles", "noun/IndefiniteArticles.txt", true),
            new SourceSpec("Plurals", "noun/Plurals.txt", false),
            new SourceSpec("VerbValence", "verb/VerbValence.txt", true),
            new SourceSpec("VerbReflexive", "verb/VerbReflexive.txt", true),
            new SourceSpec("VerbCausativity", "verb/VerbCausativity.txt", true),
            new SourceSpec("VerbAchievementProcess", "verb/VerbAchievementProcess.txt", true),
            new SourceSpec("VerbReciprocal", "verb/VerbReciprocal.txt", true),
            new SourceSpec("VerbConjugations", "verb/VerbConjugations.txt", true),
            new SourceSpec("AdjectiveSemanticClasses", "adjective/AdjectiveSemanticClasses.txt", true),
            new SourceSpec("AdverbSemanticClasses", "adverb/AdverbSemanticClasses.txt", true)
    );

    public static void main(String[] args) {
        try {
            Map<String, String> cli = parseArgs(args);
            String inputDir = cli.get("input");
            String goldDir = cli.containsKey("gold-dir") ? cli.get("gold-dir") : cli.get("gold");
            String outputDir = cli.get("output");
            String referenceModelName = cli.getOrDefault("reference-model", "openai__gpt-5_2");
            int bootstrapResamples = parseInt(cli.get("bootstrap-resamples"), 1000);
            long bootstrapSeed = parseLong(cli.get("bootstrap-seed"), 42L);

            if (inputDir == null || outputDir == null) {
                System.err.println("Usage: java ... EvaluationRunner --input <path> --output <path> [--gold-dir <path>] [--reference-model <name>] [--bootstrap-resamples <n>] [--bootstrap-seed <seed>]");
                System.exit(1);
            }

            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath.resolve("tables"));
            Files.createDirectories(outputPath.resolve("latex"));
            Files.createDirectories(outputPath.resolve("figures"));

            List<ModelMetadata> models = scanModelDirectories(inputDir);
            models.sort(ModelMetadata::compareForDisplay);
            if (models.isEmpty()) {
                throw new IOException("No model directories found under " + inputDir);
            }

            ModelMetadata referenceModel = models.stream()
                    .filter(model -> model.getName().equals(referenceModelName))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Reference model not found: " + referenceModelName));

            Map<String, Map<String, FileStats>> sourceStats = loadSourceStats(inputDir, models);
            Map<String, Map<String, Map<String, String>>> categoricalData = loadCategoricalData(inputDir, models);
            Map<String, Map<String, String>> goldData = loadGoldData(goldDir);

            List<PropertyAgreementStats> propertyAgreementStats = INCLUDE_MULTI_RATER_AGREEMENT_OUTPUTS
                    ? computePropertyAgreementStats(categoricalData, sourceStats)
                    : Collections.emptyList();
            List<CategoricalRow> categoricalRows = computeCategoricalRows(
                    models,
                    referenceModel,
                    categoricalData,
                    goldData,
                    bootstrapResamples,
                    bootstrapSeed
            );
            List<ModelSummaryRow> modelSummaryRows = buildModelSummaries(models, categoricalRows, sourceStats);
            Map<String, Map<String, Double>> pairwiseAgreement = computePairwiseAgreement(models, categoricalData);

            List<GenerativeAuditRow> generativeAuditRows = new ArrayList<>();
            List<GenerativeAgreementRow> generativeAgreementRows = new ArrayList<>();
            List<GenerativeAgreementRow> generativeAgreementAcceptedRows = new ArrayList<>();
            if (goldDir != null && !goldDir.trim().isEmpty()) {
                generateGenerativeOutputs(
                        inputDir,
                        goldDir,
                        models,
                        referenceModel,
                        bootstrapResamples,
                        bootstrapSeed,
                        generativeAuditRows,
                        generativeAgreementRows,
                        generativeAgreementAcceptedRows
                );
            }

            exportCategoricalKappaVsGoldCSV(outputPath.resolve("tables/categorical_kappa_vs_gold.csv"), categoricalRows);
            exportCategoricalKappaVsReferenceCSV(outputPath.resolve("tables/categorical_kappa_vs_reference.csv"), categoricalRows);
            if (INCLUDE_MULTI_RATER_AGREEMENT_OUTPUTS) {
                exportPropertyAlphaCSV(outputPath.resolve("tables/property_alpha.csv"), propertyAgreementStats);
            }
            exportErrorRatesCSV(outputPath.resolve("tables/error_rates.csv"), modelSummaryRows);
            exportPairwiseAgreementCSV(outputPath.resolve("tables/pairwise_agreement.csv"), pairwiseAgreement, models);
            exportModelKappaCSV(outputPath.resolve("tables/model_kappa.csv"), modelSummaryRows);
            exportGenerativeAuditCSV(outputPath.resolve("tables/generative_reference_audit.csv"), generativeAuditRows);
            exportGenerativeAgreementCSV(outputPath.resolve("tables/generative_reference_agreement.csv"), generativeAgreementRows);
            exportGenerativeAgreementCSV(outputPath.resolve("tables/generative_reference_agreement_accepted.csv"), generativeAgreementAcceptedRows);
            exportLatexTables(outputPath.resolve("latex/all_tables.tex"), categoricalRows, generativeAuditRows, generativeAgreementRows, generativeAgreementAcceptedRows);
            exportLatexTableTextFiles(outputPath.resolve("latex"), categoricalRows, generativeAuditRows, generativeAgreementRows, generativeAgreementAcceptedRows);

            invokePythonPlotter(outputPath.resolve("tables").toString(), outputPath.resolve("figures").toString());
            writeSummary(outputPath.resolve("summary.txt"), models, referenceModel, categoricalRows, generativeAuditRows, generativeAgreementRows);

            System.out.println("Evaluation complete. Results in: " + outputDir);
        } catch (Exception e) {
            System.err.println("Error during evaluation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
            result.add(ModelMetadata.fromDirName(subdir.getName()));
        }
        return result;
    }

    private static Map<String, Map<String, FileStats>> loadSourceStats(String inputDir,
                                                                       List<ModelMetadata> models) throws IOException {
        Map<String, Map<String, FileStats>> result = new LinkedHashMap<>();
        for (SourceSpec sourceSpec : SOURCE_SPECS) {
            Map<String, FileStats> byModel = new LinkedHashMap<>();
            MorphoCategoricalSchema.CategorySpec categoricalSpec = MorphoCategoricalSchema.getByRelativePath(sourceSpec.fileName);
            String invalidCategorizationFieldName = categoricalSpec == null ? null : categoricalSpec.getFieldName();
            for (ModelMetadata model : models) {
                Path file = Paths.get(inputDir, model.getDirectoryName(), sourceSpec.fileName);
                if (Files.exists(file)) {
                    MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.countJsonRecords(
                            file.toString(), invalidCategorizationFieldName);
                    byModel.put(model.getName(), new FileStats(loadResult.totalRecords, loadResult.errorRecords, loadResult.parseFailures));
                } else {
                    byModel.put(model.getName(), new FileStats(0, 0, 0));
                }
            }
            result.put(sourceSpec.sourceName, byModel);
        }
        return result;
    }

    private static Map<String, Map<String, Map<String, String>>> loadCategoricalData(String inputDir,
                                                                                     List<ModelMetadata> models) throws IOException {
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
        for (CategoricalSpec spec : CATEGORICAL_SPECS) {
            Map<String, Map<String, String>> byModel = new LinkedHashMap<>();
            for (ModelMetadata model : models) {
                Path file = Paths.get(inputDir, model.getDirectoryName(), spec.fileName);
                if (!Files.exists(file)) {
                    byModel.put(model.getName(), new LinkedHashMap<>());
                    continue;
                }
                MorphoDBLoader.LoadResult loadResult = MorphoDBLoader.loadClassificationsWithStats(
                        file.toString(), spec.modelField, spec.modelField);
                byModel.put(model.getName(), loadResult.classifications);
            }
            result.put(spec.propertyName, byModel);
        }
        return result;
    }

    private static Map<String, Map<String, String>> loadGoldData(String goldDir) throws IOException {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (goldDir == null || goldDir.trim().isEmpty()) {
            return result;
        }
        for (CategoricalSpec spec : CATEGORICAL_SPECS) {
            Path goldFile = Paths.get(goldDir, spec.goldKey + ".jsonl");
            if (Files.exists(goldFile)) {
                MorphoDBLoader.LoadResult loadResult = MorphoDBLoader.loadClassificationsWithStats(goldFile.toString(), "human_label");
                result.put(spec.propertyName, loadResult.classifications);
            }
        }
        return result;
    }

    private static List<PropertyAgreementStats> computePropertyAgreementStats(Map<String, Map<String, Map<String, String>>> categoricalData,
                                                                              Map<String, Map<String, FileStats>> sourceStats) {
        List<PropertyAgreementStats> rows = new ArrayList<>();
        for (CategoricalSpec spec : CATEGORICAL_SPECS) {
            Map<String, Map<String, String>> modelMaps = categoricalData.getOrDefault(spec.propertyName, Collections.emptyMap());
            List<Map<String, String>> annotationSets = buildAnnotationSetsUnion(modelMaps);
            PropertyAgreementStats row = new PropertyAgreementStats();
            row.propertyName = spec.propertyName;
            row.wordClass = spec.wordClass;
            row.itemCount = annotationSets.size();
            row.krippendorffAlpha = annotationSets.isEmpty() ? Double.NaN
                    : InterRaterStats.computeKrippendorffAlpha(annotationSets).alpha;
            row.fleissKappa = annotationSets.isEmpty() ? Double.NaN
                    : InterRaterStats.computeFleissKappa(annotationSets).kappa;
            row.percentAgreement = computeAveragePairwiseAgreement(modelMaps);
            row.avgErrorRate = averageErrorRate(sourceStats.getOrDefault(sourceNameForFile(spec.fileName), Collections.emptyMap()));
            rows.add(row);
        }
        return rows;
    }

    static List<Map<String, String>> buildAnnotationSetsUnion(Map<String, Map<String, String>> modelMaps) {
        Map<String, Map<String, String>> byLemma = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> modelEntry : modelMaps.entrySet()) {
            for (Map.Entry<String, String> item : modelEntry.getValue().entrySet()) {
                byLemma.computeIfAbsent(item.getKey(), ignored -> new LinkedHashMap<>())
                        .put(modelEntry.getKey(), item.getValue());
            }
        }

        List<Map<String, String>> annotationSets = new ArrayList<>();
        for (Map<String, String> item : byLemma.values()) {
            if (item.size() >= 2) {
                annotationSets.add(item);
            }
        }
        return annotationSets;
    }

    private static double computeAveragePairwiseAgreement(Map<String, Map<String, String>> modelMaps) {
        List<String> modelNames = new ArrayList<>(modelMaps.keySet());
        double totalAgreement = 0.0;
        int comparisons = 0;
        for (int i = 0; i < modelNames.size(); i++) {
            for (int j = i + 1; j < modelNames.size(); j++) {
                Map<String, String> left = modelMaps.get(modelNames.get(i));
                Map<String, String> right = modelMaps.get(modelNames.get(j));
                Set<String> overlap = intersectKeys(left, right);
                if (overlap.isEmpty()) {
                    continue;
                }
                int matches = 0;
                for (String key : overlap) {
                    if (left.get(key).equals(right.get(key))) {
                        matches++;
                    }
                }
                totalAgreement += (double) matches / overlap.size();
                comparisons++;
            }
        }
        return comparisons == 0 ? Double.NaN : totalAgreement / comparisons;
    }

    private static List<CategoricalRow> computeCategoricalRows(List<ModelMetadata> models,
                                                               ModelMetadata referenceModel,
                                                               Map<String, Map<String, Map<String, String>>> categoricalData,
                                                               Map<String, Map<String, String>> goldData,
                                                               int bootstrapResamples,
                                                               long bootstrapSeed) {
        List<CategoricalRow> rows = new ArrayList<>();

        for (CategoricalSpec spec : CATEGORICAL_SPECS) {
            Map<String, Map<String, String>> modelMaps = categoricalData.getOrDefault(spec.propertyName, Collections.emptyMap());
            Map<String, String> referenceMap = modelMaps.getOrDefault(referenceModel.getName(), Collections.emptyMap());
            Map<String, String> goldMap = goldData.getOrDefault(spec.propertyName, Collections.emptyMap());

            for (ModelMetadata model : models) {
                Map<String, String> modelMap = modelMaps.getOrDefault(model.getName(), Collections.emptyMap());
                CategoricalRow row = new CategoricalRow(model.getName(), spec.propertyName, spec.wordClass);

                Set<String> goldOverlap = intersectKeys(modelMap, goldMap);
                row.nGold = goldOverlap.size();
                row.lowNGold = isLowN(row.nGold);
                if (row.nGold >= 30) {
                    row.kappaGold = computeKappaOnSubset(modelMap, goldMap, goldOverlap);
                }

                Set<String> referenceOverlap = intersectKeys(modelMap, referenceMap);
                row.nReference = referenceOverlap.size();
                row.lowNReference = isLowN(row.nReference);
                if (row.nReference >= 30) {
                    row.kappaReference = computeKappaOnSubset(modelMap, referenceMap, referenceOverlap);
                }

                Set<String> deltaOverlap = intersectKeys(modelMap, referenceMap, goldMap);
                row.nDelta = deltaOverlap.size();
                row.lowNDelta = isLowN(row.nDelta);
                if (row.nDelta >= 30) {
                    Double modelKappa = computeKappaOnSubset(modelMap, goldMap, deltaOverlap);
                    Double referenceKappa = computeKappaOnSubset(referenceMap, goldMap, deltaOverlap);
                    if (modelKappa != null && referenceKappa != null) {
                        row.deltaKappaGold = modelKappa - referenceKappa;
                        row.deltaCI = bootstrapDeltaKappa(
                                modelMap,
                                referenceMap,
                                goldMap,
                                deltaOverlap,
                                bootstrapResamples,
                                bootstrapSeed + 31L * spec.propertyName.hashCode() + model.getName().hashCode()
                        );
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    static boolean isLowN(int n) {
        return n >= 30 && n < 100;
    }

    private static Double computeKappaOnSubset(Map<String, String> left, Map<String, String> right, Set<String> subset) {
        if (subset.size() < 1) {
            return null;
        }
        Map<String, String> leftFiltered = subsetMap(left, subset);
        Map<String, String> rightFiltered = subsetMap(right, subset);
        InterRaterStats.CohensKappaResult result = InterRaterStats.computeCohensKappa(leftFiltered, rightFiltered);
        if (result == null || Double.isNaN(result.kappa)) {
            return null;
        }
        return result.kappa;
    }

    static ConfidenceInterval bootstrapDeltaKappa(Map<String, String> modelMap,
                                                  Map<String, String> referenceMap,
                                                  Map<String, String> goldMap,
                                                  Set<String> subset,
                                                  int bootstrapResamples,
                                                  long seed) {
        if (subset.isEmpty() || bootstrapResamples <= 0) {
            return new ConfidenceInterval(null, null);
        }

        List<String> keys = new ArrayList<>(subset);
        List<String> modelValues = keys.stream().map(modelMap::get).collect(Collectors.toList());
        List<String> referenceValues = keys.stream().map(referenceMap::get).collect(Collectors.toList());
        List<String> goldValues = keys.stream().map(goldMap::get).collect(Collectors.toList());

        Random random = new Random(seed);
        List<Double> deltas = new ArrayList<>();
        int sampleSize = keys.size();

        for (int sample = 0; sample < bootstrapResamples; sample++) {
            Map<String, String> sampledModel = new LinkedHashMap<>();
            Map<String, String> sampledReference = new LinkedHashMap<>();
            Map<String, String> sampledGold = new LinkedHashMap<>();

            for (int i = 0; i < sampleSize; i++) {
                int index = random.nextInt(sampleSize);
                String sampleKey = "b" + sample + "_" + i;
                sampledModel.put(sampleKey, modelValues.get(index));
                sampledReference.put(sampleKey, referenceValues.get(index));
                sampledGold.put(sampleKey, goldValues.get(index));
            }

            InterRaterStats.CohensKappaResult modelResult = InterRaterStats.computeCohensKappa(sampledModel, sampledGold);
            InterRaterStats.CohensKappaResult referenceResult = InterRaterStats.computeCohensKappa(sampledReference, sampledGold);
            if (modelResult != null && referenceResult != null
                    && !Double.isNaN(modelResult.kappa) && !Double.isNaN(referenceResult.kappa)) {
                deltas.add(modelResult.kappa - referenceResult.kappa);
            }
        }

        if (deltas.isEmpty()) {
            return new ConfidenceInterval(null, null);
        }
        Collections.sort(deltas);
        return new ConfidenceInterval(percentile(deltas, 0.025), percentile(deltas, 0.975));
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

    static Set<String> intersectKeys(Map<String, ?> left, Map<String, ?> right) {
        Set<String> result = new LinkedHashSet<>(left.keySet());
        result.retainAll(right.keySet());
        return result;
    }

    static Set<String> intersectKeys(Map<String, ?> first, Map<String, ?> second, Map<String, ?> third) {
        Set<String> result = intersectKeys(first, second);
        result.retainAll(third.keySet());
        return result;
    }

    static <T> Map<String, T> subsetMap(Map<String, T> input, Set<String> subset) {
        Map<String, T> result = new LinkedHashMap<>();
        for (String key : subset) {
            if (input.containsKey(key)) {
                result.put(key, input.get(key));
            }
        }
        return result;
    }

    private static Map<String, Map<String, Double>> computePairwiseAgreement(List<ModelMetadata> models,
                                                                             Map<String, Map<String, Map<String, String>>> categoricalData) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        List<String> modelNames = models.stream().map(ModelMetadata::getName).collect(Collectors.toList());

        for (String leftModel : modelNames) {
            Map<String, Double> row = new LinkedHashMap<>();
            result.put(leftModel, row);
            for (String rightModel : modelNames) {
                if (leftModel.equals(rightModel)) {
                    row.put(rightModel, 1.0);
                    continue;
                }

                int total = 0;
                int matches = 0;
                for (CategoricalSpec spec : CATEGORICAL_SPECS) {
                    Map<String, Map<String, String>> byModel = categoricalData.getOrDefault(spec.propertyName, Collections.emptyMap());
                    Map<String, String> left = byModel.getOrDefault(leftModel, Collections.emptyMap());
                    Map<String, String> right = byModel.getOrDefault(rightModel, Collections.emptyMap());
                    Set<String> overlap = intersectKeys(left, right);
                    for (String key : overlap) {
                        total++;
                        if (left.get(key).equals(right.get(key))) {
                            matches++;
                        }
                    }
                }
                row.put(rightModel, total == 0 ? Double.NaN : (double) matches / total);
            }
        }
        return result;
    }

    private static List<ModelSummaryRow> buildModelSummaries(List<ModelMetadata> models,
                                                             List<CategoricalRow> categoricalRows,
                                                             Map<String, Map<String, FileStats>> sourceStats) {
        Map<String, List<CategoricalRow>> rowsByModel = categoricalRows.stream()
                .collect(Collectors.groupingBy(row -> row.model, LinkedHashMap::new, Collectors.toList()));

        List<ModelSummaryRow> summaries = new ArrayList<>();
        for (ModelMetadata model : models) {
            ModelSummaryRow row = new ModelSummaryRow(model);
            List<CategoricalRow> modelRows = rowsByModel.getOrDefault(model.getName(), Collections.emptyList());

            for (String wordClass : Arrays.asList("noun", "verb", "adjective", "adverb")) {
                List<Double> kappas = modelRows.stream()
                        .filter(metric -> wordClass.equals(metric.wordClass))
                        .filter(metric -> metric.kappaGold != null && metric.nGold >= 100)
                        .map(metric -> metric.kappaGold)
                        .collect(Collectors.toList());
                row.kappaByWordClass.put(wordClass, average(kappas));
            }
            row.avgKappa = average(row.kappaByWordClass.values().stream()
                    .filter(value -> value != null && !Double.isNaN(value))
                    .collect(Collectors.toList()));

            List<Double> categoricalErrorRates = new ArrayList<>();
            for (SourceSpec sourceSpec : SOURCE_SPECS) {
                FileStats stats = sourceStats.getOrDefault(sourceSpec.sourceName, Collections.emptyMap()).get(model.getName());
                double errorRate = stats == null ? Double.NaN : stats.errorRatePercent();
                row.errorRateBySource.put(sourceSpec.sourceName, errorRate);
                if (sourceSpec.categoricalSummary && !Double.isNaN(errorRate)) {
                    categoricalErrorRates.add(errorRate);
                }
            }
            row.avgErrorRate = average(categoricalErrorRates);
            summaries.add(row);
        }
        return summaries;
    }

    private static Double average(Collection<Double> values) {
        List<Double> cleaned = values.stream()
                .filter(value -> value != null && !Double.isNaN(value))
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return Double.NaN;
        }
        return cleaned.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static double averageErrorRate(Map<String, FileStats> statsByModel) {
        List<Double> errorRates = new ArrayList<>();
        for (FileStats stats : statsByModel.values()) {
            double errorRate = stats.errorRatePercent();
            if (!Double.isNaN(errorRate)) {
                errorRates.add(errorRate);
            }
        }
        Double average = average(errorRates);
        return average == null ? Double.NaN : average;
    }

    private static void generateGenerativeOutputs(String inputDir,
                                                  String goldDir,
                                                  List<ModelMetadata> models,
                                                  ModelMetadata referenceModel,
                                                  int bootstrapResamples,
                                                  long bootstrapSeed,
                                                  List<GenerativeAuditRow> auditRows,
                                                  List<GenerativeAgreementRow> agreementRows,
                                                  List<GenerativeAgreementRow> agreementAcceptedRows) throws IOException {
        for (GenerativeSpec spec : GENERATIVE_SPECS) {
            Path auditFile = Paths.get(goldDir, spec.auditFileName);
            if (!Files.exists(auditFile)) {
                continue;
            }

            GenerativeCorpusContext referenceContext = loadGenerativeCorpusContext(
                    Paths.get(inputDir, referenceModel.getDirectoryName(), spec.fileName),
                    spec
            );
            Map<String, AuditEntry> auditEntries = loadAuditEntries(auditFile);
            if (auditEntries.isEmpty()) {
                continue;
            }

            auditRows.addAll(computeGenerativeAuditRows(spec, referenceContext, auditEntries, bootstrapResamples, bootstrapSeed));
            agreementRows.addAll(computeGenerativeAgreementRows(
                    spec,
                    inputDir,
                    models,
                    referenceModel,
                    auditEntries,
                    referenceContext,
                    bootstrapResamples,
                    bootstrapSeed,
                    false
            ));
            agreementAcceptedRows.addAll(computeGenerativeAgreementRows(
                    spec,
                    inputDir,
                    models,
                    referenceModel,
                    auditEntries,
                    referenceContext,
                    bootstrapResamples,
                    bootstrapSeed,
                    true
            ));
        }
    }

    private static GenerativeCorpusContext loadGenerativeCorpusContext(Path file, GenerativeSpec spec) throws IOException {
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(file.toString());
        Map<String, Integer> stratumCounts = new LinkedHashMap<>();

        for (JSONObject json : loadResult.records) {
            if (GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(spec.auditMode)) {
                GenerativeEvalUtils.PluralRecord record = GenerativeEvalUtils.extractPluralRecord(json);
                if (record != null) {
                    stratumCounts.merge(record.sampleStratum, 1, Integer::sum);
                }
            } else {
                GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
                if (record != null) {
                    stratumCounts.merge(record.sampleStratum, 1, Integer::sum);
                }
            }
        }

        int extractableCount = stratumCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : stratumCounts.entrySet()) {
            weights.put(entry.getKey(), extractableCount == 0 ? 0.0 : (double) entry.getValue() / extractableCount);
        }

        return new GenerativeCorpusContext(
                new FileStats(loadResult.totalRecords, loadResult.errorRecords, loadResult.parseFailures),
                weights,
                extractableCount
        );
    }

    static Map<String, AuditEntry> loadAuditEntries(Path path) throws IOException {
        Map<String, AuditEntry> finalEntries = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject json = new JSONObject(trimmed);
                    String auditId = json.optString("audit_id", "");
                    if (auditId.isEmpty()) {
                        continue;
                    }
                    String humanLabel = json.optString("human_label", "");
                    if ("__UNDONE__".equals(humanLabel)) {
                        finalEntries.remove(auditId);
                        continue;
                    }
                    finalEntries.put(auditId, new AuditEntry(
                            auditId,
                            json.optString("property", ""),
                            json.optString("reference_model", ""),
                            json.optString("sample_stratum", ""),
                            json.optString("synsetId", ""),
                            json.optString("lemma", ""),
                            humanLabel,
                            json.optJSONObject("reference_output")
                    ));
                } catch (Exception ignored) {
                }
            }
        }
        return finalEntries;
    }

    private static List<GenerativeAuditRow> computeGenerativeAuditRows(GenerativeSpec spec,
                                                                       GenerativeCorpusContext referenceContext,
                                                                       Map<String, AuditEntry> auditEntries,
                                                                       int bootstrapResamples,
                                                                       long bootstrapSeed) {
        List<GenerativeAuditRow> rows = new ArrayList<>();
        Map<String, List<Boolean>> outcomesByStratum = groupAuditOutcomesByStratum(auditEntries.values(), false);

        for (Map.Entry<String, List<Boolean>> entry : outcomesByStratum.entrySet()) {
            int accepted = countTrue(entry.getValue());
            double rate = entry.getValue().isEmpty() ? Double.NaN : (double) accepted / entry.getValue().size();
            rows.add(new GenerativeAuditRow(
                    spec.propertyName,
                    "stratum",
                    entry.getKey(),
                    entry.getValue().size(),
                    accepted,
                    rate,
                    null,
                    referenceContext.stratumWeights.get(entry.getKey()),
                    null,
                    null,
                    null
            ));
        }

        double weightedRate = weightedRate(outcomesByStratum, referenceContext.stratumWeights);
        ConfidenceInterval ci = stratifiedBootstrapBinaryCI(
                outcomesByStratum,
                referenceContext.stratumWeights,
                bootstrapResamples,
                bootstrapSeed + 131L * spec.propertyName.hashCode()
        );
        rows.add(new GenerativeAuditRow(
                spec.propertyName,
                "overall_weighted",
                "OVERALL",
                auditEntries.size(),
                auditEntries.values().stream().mapToInt(entry -> "Correct".equals(entry.humanLabel) ? 1 : 0).sum(),
                weightedRate,
                ci,
                1.0,
                referenceContext.fileStats,
                referenceContext.extractableCount,
                referenceContext.fileStats.totalRecords == 0 ? Double.NaN : (double) referenceContext.extractableCount / referenceContext.fileStats.totalRecords
        ));
        return rows;
    }

    private static List<GenerativeAgreementRow> computeGenerativeAgreementRows(GenerativeSpec spec,
                                                                               String inputDir,
                                                                               List<ModelMetadata> models,
                                                                               ModelMetadata referenceModel,
                                                                               Map<String, AuditEntry> auditEntries,
                                                                               GenerativeCorpusContext referenceContext,
                                                                               int bootstrapResamples,
                                                                               long bootstrapSeed,
                                                                               boolean acceptedOnly) throws IOException {
        List<GenerativeAgreementRow> rows = new ArrayList<>();
        String scope = acceptedOnly ? "accepted_only" : "full_audited";
        List<String> metrics = metricNamesForSpec(spec);

        for (ModelMetadata model : models) {
            if (model.getName().equals(referenceModel.getName())) {
                continue;
            }

            Map<String, ?> modelRecords = loadModelGenerativeRecords(Paths.get(inputDir, model.getDirectoryName(), spec.fileName), spec);
            Map<String, Map<String, List<Boolean>>> outcomesByMetric = buildGenerativeComparisonOutcomes(
                    spec,
                    auditEntries,
                    modelRecords,
                    acceptedOnly
            );

            for (String metric : metrics) {
                Map<String, List<Boolean>> byStratum = outcomesByMetric.getOrDefault(metric, Collections.emptyMap());
                for (Map.Entry<String, List<Boolean>> entry : byStratum.entrySet()) {
                    int matches = countTrue(entry.getValue());
                    double rate = entry.getValue().isEmpty() ? Double.NaN : (double) matches / entry.getValue().size();
                    rows.add(new GenerativeAgreementRow(
                            model.getName(),
                            spec.propertyName,
                            scope,
                            metric,
                            "stratum",
                            entry.getKey(),
                            entry.getValue().size(),
                            matches,
                            rate,
                            null,
                            referenceContext.stratumWeights.get(entry.getKey())
                    ));
                }

                List<Boolean> overallOutcomes = flatten(byStratum.values());
                Double overallRate;
                ConfidenceInterval ci;
                if (acceptedOnly) {
                    overallRate = overallOutcomes.isEmpty() ? Double.NaN : (double) countTrue(overallOutcomes) / overallOutcomes.size();
                    ci = null;
                } else {
                    overallRate = weightedRate(byStratum, referenceContext.stratumWeights);
                    ci = stratifiedBootstrapBinaryCI(
                            byStratum,
                            referenceContext.stratumWeights,
                            bootstrapResamples,
                            bootstrapSeed + 197L * spec.propertyName.hashCode() + model.getName().hashCode() + metric.hashCode()
                    );
                }

                rows.add(new GenerativeAgreementRow(
                        model.getName(),
                        spec.propertyName,
                        scope,
                        metric,
                        acceptedOnly ? "overall_raw" : "overall_weighted",
                        "OVERALL",
                        overallOutcomes.size(),
                        countTrue(overallOutcomes),
                        overallRate,
                        ci,
                        1.0
                ));
            }
        }
        return rows;
    }

    private static Map<String, ?> loadModelGenerativeRecords(Path file, GenerativeSpec spec) throws IOException {
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(file.toString());
        if (GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(spec.auditMode)) {
            Map<String, GenerativeEvalUtils.PluralRecord> result = new LinkedHashMap<>();
            for (JSONObject json : loadResult.records) {
                GenerativeEvalUtils.PluralRecord record = GenerativeEvalUtils.extractPluralRecord(json);
                if (record != null) {
                    result.put(record.auditId, record);
                }
            }
            return result;
        }

        Map<String, GenerativeEvalUtils.ConjugationRecord> result = new LinkedHashMap<>();
        for (JSONObject json : loadResult.records) {
            GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
            if (record != null) {
                result.put(record.auditId, record);
            }
        }
        return result;
    }

    private static Map<String, Map<String, List<Boolean>>> buildGenerativeComparisonOutcomes(GenerativeSpec spec,
                                                                                              Map<String, AuditEntry> auditEntries,
                                                                                              Map<String, ?> modelRecords,
                                                                                              boolean acceptedOnly) {
        Map<String, Map<String, List<Boolean>>> byMetric = new LinkedHashMap<>();
        for (String metric : metricNamesForSpec(spec)) {
            byMetric.put(metric, new LinkedHashMap<>());
        }

        for (AuditEntry auditEntry : auditEntries.values()) {
            if (acceptedOnly && !"Correct".equals(auditEntry.humanLabel)) {
                continue;
            }

            if (GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(spec.auditMode)) {
                @SuppressWarnings("unchecked")
                Map<String, GenerativeEvalUtils.PluralRecord> pluralRecords = (Map<String, GenerativeEvalUtils.PluralRecord>) modelRecords;
                GenerativeEvalUtils.PluralRecord record = pluralRecords.get(auditEntry.auditId);
                boolean match = record != null && GenerativeEvalUtils.pluralMatchesReference(record, auditEntry.referenceOutput);
                byMetric.get("plural_exact_match")
                        .computeIfAbsent(auditEntry.sampleStratum, ignored -> new ArrayList<>())
                        .add(match);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, GenerativeEvalUtils.ConjugationRecord> conjugationRecords = (Map<String, GenerativeEvalUtils.ConjugationRecord>) modelRecords;
                GenerativeEvalUtils.ConjugationRecord record = conjugationRecords.get(auditEntry.auditId);
                Map<String, Boolean> comparison = record == null
                        ? failedConjugationComparison()
                        : GenerativeEvalUtils.compareConjugationToReference(record, auditEntry.referenceOutput);
                for (Map.Entry<String, Boolean> metric : comparison.entrySet()) {
                    String metricName = metricToOutputName(metric.getKey());
                    byMetric.get(metricName)
                            .computeIfAbsent(auditEntry.sampleStratum, ignored -> new ArrayList<>())
                            .add(metric.getValue());
                }
            }
        }
        return byMetric;
    }

    private static Map<String, Boolean> failedConjugationComparison() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put(GenerativeEvalUtils.SLOT_INFINITIVE, false);
        result.put(GenerativeEvalUtils.SLOT_PRESENT_3SG, false);
        result.put(GenerativeEvalUtils.SLOT_SIMPLE_PAST, false);
        result.put(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE, false);
        result.put(GenerativeEvalUtils.SLOT_GERUND, false);
        result.put("regularity", false);
        result.put("all_parts_exact", false);
        return result;
    }

    private static String metricToOutputName(String internal) {
        switch (internal) {
            case GenerativeEvalUtils.SLOT_INFINITIVE:
                return "infinitive_agreement";
            case GenerativeEvalUtils.SLOT_PRESENT_3SG:
                return "present_3sg_agreement";
            case GenerativeEvalUtils.SLOT_SIMPLE_PAST:
                return "simple_past_agreement";
            case GenerativeEvalUtils.SLOT_PAST_PARTICIPLE:
                return "past_participle_agreement";
            case GenerativeEvalUtils.SLOT_GERUND:
                return "gerund_agreement";
            case "regularity":
                return "regularity_agreement";
            case "all_parts_exact":
                return "all_parts_exact_match";
            default:
                return internal;
        }
    }

    private static List<String> metricNamesForSpec(GenerativeSpec spec) {
        if (GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(spec.auditMode)) {
            return Collections.singletonList("plural_exact_match");
        }
        return Arrays.asList(
                "infinitive_agreement",
                "present_3sg_agreement",
                "simple_past_agreement",
                "past_participle_agreement",
                "gerund_agreement",
                "regularity_agreement",
                "all_parts_exact_match"
        );
    }

    private static Map<String, List<Boolean>> groupAuditOutcomesByStratum(Collection<AuditEntry> auditEntries, boolean acceptedOnly) {
        Map<String, List<Boolean>> result = new LinkedHashMap<>();
        for (AuditEntry entry : auditEntries) {
            if (acceptedOnly && !"Correct".equals(entry.humanLabel)) {
                continue;
            }
            result.computeIfAbsent(entry.sampleStratum, ignored -> new ArrayList<>())
                    .add("Correct".equals(entry.humanLabel));
        }
        return result;
    }

    static double weightedRate(Map<String, List<Boolean>> outcomesByStratum, Map<String, Double> weights) {
        double total = 0.0;
        for (Map.Entry<String, Double> weight : weights.entrySet()) {
            List<Boolean> outcomes = outcomesByStratum.get(weight.getKey());
            if (outcomes == null || outcomes.isEmpty()) {
                continue;
            }
            total += weight.getValue() * ((double) countTrue(outcomes) / outcomes.size());
        }
        return total;
    }

    static ConfidenceInterval stratifiedBootstrapBinaryCI(Map<String, List<Boolean>> outcomesByStratum,
                                                          Map<String, Double> weights,
                                                          int bootstrapResamples,
                                                          long seed) {
        if (bootstrapResamples <= 0) {
            return new ConfidenceInterval(null, null);
        }
        Random random = new Random(seed);
        List<Double> estimates = new ArrayList<>();
        for (int sample = 0; sample < bootstrapResamples; sample++) {
            double total = 0.0;
            boolean sawData = false;
            for (Map.Entry<String, Double> weightEntry : weights.entrySet()) {
                List<Boolean> outcomes = outcomesByStratum.get(weightEntry.getKey());
                if (outcomes == null || outcomes.isEmpty()) {
                    continue;
                }
                sawData = true;
                int successes = 0;
                for (int i = 0; i < outcomes.size(); i++) {
                    if (outcomes.get(random.nextInt(outcomes.size()))) {
                        successes++;
                    }
                }
                total += weightEntry.getValue() * ((double) successes / outcomes.size());
            }
            if (sawData) {
                estimates.add(total);
            }
        }
        if (estimates.isEmpty()) {
            return new ConfidenceInterval(null, null);
        }
        Collections.sort(estimates);
        return new ConfidenceInterval(percentile(estimates, 0.025), percentile(estimates, 0.975));
    }

    private static int countTrue(List<Boolean> values) {
        int count = 0;
        for (Boolean value : values) {
            if (Boolean.TRUE.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static List<Boolean> flatten(Collection<List<Boolean>> grouped) {
        List<Boolean> result = new ArrayList<>();
        for (List<Boolean> values : grouped) {
            result.addAll(values);
        }
        return result;
    }

    private static void exportCategoricalKappaVsGoldCSV(Path path, List<CategoricalRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,property,word_class,kappa_gold,n_gold,low_n_gold\n");
        for (CategoricalRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.property).append(",");
            sb.append(row.wordClass).append(",");
            sb.append(formatMaybeDouble(row.kappaGold)).append(",");
            sb.append(row.nGold).append(",");
            sb.append(row.lowNGold).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportCategoricalKappaVsReferenceCSV(Path path, List<CategoricalRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,property,word_class,kappa_reference,n_reference,low_n_reference,delta_kappa_gold,delta_ci_low,delta_ci_high,n_delta,low_n_delta\n");
        for (CategoricalRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.property).append(",");
            sb.append(row.wordClass).append(",");
            sb.append(formatMaybeDouble(row.kappaReference)).append(",");
            sb.append(row.nReference).append(",");
            sb.append(row.lowNReference).append(",");
            sb.append(formatMaybeDouble(row.deltaKappaGold)).append(",");
            sb.append(formatMaybeDouble(row.deltaCI == null ? null : row.deltaCI.lower)).append(",");
            sb.append(formatMaybeDouble(row.deltaCI == null ? null : row.deltaCI.upper)).append(",");
            sb.append(row.nDelta).append(",");
            sb.append(row.lowNDelta).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportPropertyAlphaCSV(Path path, List<PropertyAgreementStats> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("property,word_class,item_count,kripp_alpha,fleiss_kappa,pct_agree,avg_error_rate\n");
        for (PropertyAgreementStats row : rows) {
            sb.append(row.propertyName).append(",");
            sb.append(row.wordClass).append(",");
            sb.append(row.itemCount).append(",");
            sb.append(formatMaybeDouble(row.krippendorffAlpha)).append(",");
            sb.append(formatMaybeDouble(row.fleissKappa)).append(",");
            sb.append(formatMaybeDouble(row.percentAgreement)).append(",");
            sb.append(formatMaybeDouble(row.avgErrorRate)).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportErrorRatesCSV(Path path, List<ModelSummaryRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,tier,size_b");
        for (SourceSpec sourceSpec : SOURCE_SPECS) {
            sb.append(",").append(sourceSpec.sourceName);
        }
        sb.append(",avg_error_rate\n");

        for (ModelSummaryRow row : rows) {
            sb.append(row.metadata.getName()).append(",");
            sb.append(row.metadata.getTier()).append(",");
            sb.append(formatSize(row.metadata.getParameterBillions())).append(",");
            for (SourceSpec sourceSpec : SOURCE_SPECS) {
                sb.append(formatMaybeDouble(row.errorRateBySource.get(sourceSpec.sourceName))).append(",");
            }
            sb.append(formatMaybeDouble(row.avgErrorRate)).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportPairwiseAgreementCSV(Path path,
                                                   Map<String, Map<String, Double>> agreement,
                                                   List<ModelMetadata> models) throws IOException {
        StringBuilder sb = new StringBuilder();
        List<String> modelNames = models.stream().map(ModelMetadata::getName).collect(Collectors.toList());
        sb.append("model");
        for (String model : modelNames) {
            sb.append(",").append(model);
        }
        sb.append("\n");

        for (String leftModel : modelNames) {
            sb.append(leftModel);
            for (String rightModel : modelNames) {
                sb.append(",").append(formatMaybeDouble(agreement.getOrDefault(leftModel, Collections.emptyMap()).get(rightModel)));
            }
            sb.append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportModelKappaCSV(Path path, List<ModelSummaryRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,tier,size_b,noun_kappa,verb_kappa,adj_kappa,adv_kappa,avg_kappa\n");
        for (ModelSummaryRow row : rows) {
            sb.append(row.metadata.getName()).append(",");
            sb.append(row.metadata.getTier()).append(",");
            sb.append(formatSize(row.metadata.getParameterBillions())).append(",");
            sb.append(formatMaybeDouble(row.kappaByWordClass.get("noun"))).append(",");
            sb.append(formatMaybeDouble(row.kappaByWordClass.get("verb"))).append(",");
            sb.append(formatMaybeDouble(row.kappaByWordClass.get("adjective"))).append(",");
            sb.append(formatMaybeDouble(row.kappaByWordClass.get("adverb"))).append(",");
            sb.append(formatMaybeDouble(row.avgKappa)).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportGenerativeAuditCSV(Path path, List<GenerativeAuditRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("property,row_type,stratum,audited_n,accepted_n,acceptance_rate,ci_low,ci_high,corpus_weight,source_total,source_error_records,source_parse_failures,source_extractable,source_parse_success_rate\n");
        for (GenerativeAuditRow row : rows) {
            sb.append(row.property).append(",");
            sb.append(row.rowType).append(",");
            sb.append(row.stratum).append(",");
            sb.append(row.auditedN).append(",");
            sb.append(row.acceptedN).append(",");
            sb.append(formatMaybeDouble(row.rate)).append(",");
            sb.append(formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.lower)).append(",");
            sb.append(formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.upper)).append(",");
            sb.append(formatMaybeDouble(row.corpusWeight)).append(",");
            sb.append(row.sourceStats == null ? "" : row.sourceStats.totalRecords).append(",");
            sb.append(row.sourceStats == null ? "" : row.sourceStats.errorRecords).append(",");
            sb.append(row.sourceStats == null ? "" : row.sourceStats.parseFailures).append(",");
            sb.append(row.extractableCount == null ? "" : row.extractableCount).append(",");
            sb.append(formatMaybeDouble(row.parseSuccessRate)).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportGenerativeAgreementCSV(Path path, List<GenerativeAgreementRow> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,property,scope,metric,row_type,stratum,denominator_n,match_n,agreement_rate,ci_low,ci_high,corpus_weight\n");
        for (GenerativeAgreementRow row : rows) {
            sb.append(row.model).append(",");
            sb.append(row.property).append(",");
            sb.append(row.scope).append(",");
            sb.append(row.metric).append(",");
            sb.append(row.rowType).append(",");
            sb.append(row.stratum).append(",");
            sb.append(row.denominatorN).append(",");
            sb.append(row.matchCount).append(",");
            sb.append(formatMaybeDouble(row.rate)).append(",");
            sb.append(formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.lower)).append(",");
            sb.append(formatMaybeDouble(row.confidenceInterval == null ? null : row.confidenceInterval.upper)).append(",");
            sb.append(formatMaybeDouble(row.corpusWeight)).append("\n");
        }
        Files.write(path, sb.toString().getBytes());
    }

    private static void exportLatexTables(Path path,
                                          List<CategoricalRow> categoricalRows,
                                          List<GenerativeAuditRow> generativeAuditRows,
                                          List<GenerativeAgreementRow> generativeAgreementRows,
                                          List<GenerativeAgreementRow> generativeAgreementAcceptedRows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("% Auto-generated MorphoDB evaluation tables\n\n");
        sb.append(buildCategoricalGoldLatexTable(categoricalRows)).append("\n\n");
        sb.append(buildCategoricalReferenceLatexTable(categoricalRows)).append("\n\n");
        sb.append(buildGenerativeAuditLatexTable(generativeAuditRows)).append("\n\n");
        sb.append(buildGenerativeAgreementLatexTable(generativeAgreementRows, generativeAgreementAcceptedRows)).append("\n");

        Files.write(path, sb.toString().getBytes());
    }

    private static void exportLatexTableTextFiles(Path latexDir,
                                                  List<CategoricalRow> categoricalRows,
                                                  List<GenerativeAuditRow> generativeAuditRows,
                                                  List<GenerativeAgreementRow> generativeAgreementRows,
                                                  List<GenerativeAgreementRow> generativeAgreementAcceptedRows) throws IOException {
        Files.write(latexDir.resolve("categorical_kappa_vs_gold_table.txt"),
                buildCategoricalGoldLatexTable(categoricalRows).getBytes());
        Files.write(latexDir.resolve("categorical_kappa_vs_reference_table.txt"),
                buildCategoricalReferenceLatexTable(categoricalRows).getBytes());
        Files.write(latexDir.resolve("generative_reference_audit_table.txt"),
                buildGenerativeAuditLatexTable(generativeAuditRows).getBytes());
        Files.write(latexDir.resolve("generative_reference_agreement_table.txt"),
                buildGenerativeAgreementLatexTable(generativeAgreementRows, generativeAgreementAcceptedRows).getBytes());
    }

    private static String buildCategoricalGoldLatexTable(List<CategoricalRow> categoricalRows) {
        return buildGroupedCategoricalKappaLatexTable(
                categoricalRows,
                false,
                "Categorical $\\kappa$ vs human gold annotations",
                "tab:categorical_kappa_vs_gold"
        );
    }

    private static String buildCategoricalReferenceLatexTable(List<CategoricalRow> categoricalRows) {
        return buildGroupedCategoricalKappaLatexTable(
                categoricalRows,
                true,
                "Categorical $\\kappa$ vs reference model",
                "tab:categorical_kappa_vs_reference"
        );
    }

    private static String buildGroupedCategoricalKappaLatexTable(List<CategoricalRow> categoricalRows,
                                                                 boolean useReferenceKappa,
                                                                 String caption,
                                                                 String label) {
        StringBuilder sb = new StringBuilder();
        Map<String, Map<String, Double>> kappaByModel = new LinkedHashMap<>();
        for (CategoricalRow row : categoricalRows) {
            kappaByModel.computeIfAbsent(row.model, ignored -> new LinkedHashMap<>())
                    .put(row.property, useReferenceKappa ? row.kappaReference : row.kappaGold);
        }

        List<String> modelNames = new ArrayList<>(kappaByModel.keySet());
        modelNames.sort(EvaluationRunner::compareModelsForLatexTable);

        sb.append("\\begin{table*}[t]\n");
        sb.append("\\centering\n");
        sb.append("\\scriptsize\n");
        sb.append("\\caption{").append(caption).append("}\n");
        sb.append("\\label{").append(label).append("}\n");
        sb.append("\\resizebox{\\textwidth}{!}{%\n");
        sb.append("\\begin{tabular}{lrrrrr|rrrrrr|r|r|r}\n");
        sb.append("\\toprule\n");
        sb.append("& \\multicolumn{5}{c}{Nouns} ");
        sb.append("& \\multicolumn{6}{c}{Verbs} ");
        sb.append("& \\multicolumn{1}{c}{Adjectives} ");
        sb.append("& \\multicolumn{1}{c}{Adverbs} ");
        sb.append("& \\multicolumn{1}{c}{Overall} \\\\\n");
        sb.append("\\cmidrule(lr){2-6} \\cmidrule(lr){7-12} \\cmidrule(lr){13-13} \\cmidrule(lr){14-14} \\cmidrule(lr){15-15}\n");
        sb.append("Model");
        for (GroupedKappaColumn column : GROUPED_KAPPA_COLUMNS) {
            sb.append(" & \\rot{").append(escapeLatex(column.displayName)).append("}");
        }
        sb.append(" & \\rot{Overall $\\kappa$} \\\\\n");
        sb.append("\\midrule\n");

        for (String modelName : modelNames) {
            Map<String, Double> byProperty = kappaByModel.getOrDefault(modelName, Collections.emptyMap());
            List<Double> displayedKappas = new ArrayList<>();
            sb.append("\\texttt{").append(escapeLatex(modelName)).append("}");
            for (GroupedKappaColumn column : GROUPED_KAPPA_COLUMNS) {
                Double value = byProperty.get(column.propertyName);
                if (value != null && !Double.isNaN(value)) {
                    displayedKappas.add(value);
                }
                sb.append(" & ").append(formatMaybeDouble(value));
            }
            sb.append(" & ").append(formatMaybeDouble(average(displayedKappas))).append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}%\n");
        sb.append("}\n");
        sb.append("\\end{table*}\n");
        return sb.toString();
    }

    private static String buildGenerativeAuditLatexTable(List<GenerativeAuditRow> generativeAuditRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{longtable}{lllrrrr}\n");
        sb.append("\\caption{Reference-model generative audit.}\\\\\n");
        sb.append("Property & Row & Stratum & Audited & Accepted & Rate & CI\\\\\n");
        sb.append("\\hline\n");
        for (GenerativeAuditRow row : generativeAuditRows) {
            sb.append(escapeLatex(row.property)).append(" & ")
                    .append(escapeLatex(row.rowType)).append(" & ")
                    .append(escapeLatex(row.stratum)).append(" & ")
                    .append(row.auditedN).append(" & ")
                    .append(row.acceptedN).append(" & ")
                    .append(formatMaybeDouble(row.rate)).append(" & ")
                    .append(formatConfidenceInterval(row.confidenceInterval)).append("\\\\\n");
        }
        sb.append("\\end{longtable}\n");
        return sb.toString();
    }

    private static String buildGenerativeAgreementLatexTable(List<GenerativeAgreementRow> generativeAgreementRows,
                                                             List<GenerativeAgreementRow> generativeAgreementAcceptedRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("\\begin{longtable}{llllllrr}\n");
        sb.append("\\caption{Cross-model agreement with the audited reference model.}\\\\\n");
        sb.append("Model & Property & Scope & Metric & Row & Stratum & $n$ & Rate\\\\\n");
        sb.append("\\hline\n");
        for (GenerativeAgreementRow row : generativeAgreementRows) {
            sb.append(escapeLatex(row.model)).append(" & ")
                    .append(escapeLatex(row.property)).append(" & ")
                    .append(escapeLatex(row.scope)).append(" & ")
                    .append(escapeLatex(row.metric)).append(" & ")
                    .append(escapeLatex(row.rowType)).append(" & ")
                    .append(escapeLatex(row.stratum)).append(" & ")
                    .append(row.denominatorN).append(" & ")
                    .append(formatMaybeDouble(row.rate)).append("\\\\\n");
        }
        for (GenerativeAgreementRow row : generativeAgreementAcceptedRows) {
            sb.append(escapeLatex(row.model)).append(" & ")
                    .append(escapeLatex(row.property)).append(" & ")
                    .append(escapeLatex(row.scope)).append(" & ")
                    .append(escapeLatex(row.metric)).append(" & ")
                    .append(escapeLatex(row.rowType)).append(" & ")
                    .append(escapeLatex(row.stratum)).append(" & ")
                    .append(row.denominatorN).append(" & ")
                    .append(formatMaybeDouble(row.rate)).append("\\\\\n");
        }
        sb.append("\\end{longtable}\n");
        return sb.toString();
    }

    private static int compareModelsForLatexTable(String leftModel, String rightModel) {
        ModelMetadata left = ModelMetadata.fromDirName(leftModel);
        ModelMetadata right = ModelMetadata.fromDirName(rightModel);

        double leftSize = left.getParameterBillions();
        double rightSize = right.getParameterBillions();
        boolean leftKnown = leftSize >= 0;
        boolean rightKnown = rightSize >= 0;

        if (leftKnown && rightKnown) {
            int sizeCompare = Double.compare(leftSize, rightSize);
            if (sizeCompare != 0) {
                return sizeCompare;
            }
            return leftModel.compareTo(rightModel);
        }
        if (leftKnown != rightKnown) {
            return leftKnown ? -1 : 1;
        }

        int frontierCompare = Integer.compare(frontierRankForLatex(leftModel), frontierRankForLatex(rightModel));
        if (frontierCompare != 0) {
            return frontierCompare;
        }
        return leftModel.compareTo(rightModel);
    }

    private static int frontierRankForLatex(String modelName) {
        if (modelName == null) {
            return Integer.MAX_VALUE;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("openai__gpt-5-nano")) {
            return 0;
        }
        if (lower.startsWith("openai__gpt-5_2")) {
            return 1;
        }
        return 100;
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

    private static String formatConfidenceInterval(ConfidenceInterval interval) {
        if (interval == null || interval.lower == null || interval.upper == null) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "[%.3f, %.3f]", interval.lower, interval.upper);
    }

    static void invokePythonPlotter(String tablesDir, String figuresDir) {
        try {
            String plotScript = System.getenv("MORPHODB_PLOT_SCRIPT");
            if (plotScript == null || plotScript.trim().isEmpty()) {
                plotScript = "src/main/java/com/articulate/nlp/morphodb/evaluation/python/plot_figures.py";
            }

            ProcessBuilder pb = new ProcessBuilder("python3", plotScript, "--input", tablesDir, "--output", figuresDir);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("Warning: Python figure generation exited with code " + exitCode);
            }
        } catch (Exception e) {
            System.out.println("Warning: Could not invoke Python plotter: " + e.getMessage());
        }
    }

    private static void writeSummary(Path path,
                                     List<ModelMetadata> models,
                                     ModelMetadata referenceModel,
                                     List<CategoricalRow> categoricalRows,
                                     List<GenerativeAuditRow> generativeAuditRows,
                                     List<GenerativeAgreementRow> generativeAgreementRows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("MORPHODB EVALUATION SUMMARY\n");
        sb.append("==========================\n\n");
        sb.append("Models evaluated: ").append(models.size()).append("\n");
        sb.append("Reference model: ").append(referenceModel.getName()).append("\n\n");

        sb.append("Categorical rows: ").append(categoricalRows.size()).append("\n");
        long lowNRows = categoricalRows.stream().filter(row -> row.lowNGold || row.lowNReference || row.lowNDelta).count();
        sb.append("Low-N categorical rows: ").append(lowNRows).append("\n\n");

        if (!generativeAuditRows.isEmpty()) {
            sb.append("Generative audit rows: ").append(generativeAuditRows.size()).append("\n");
        }
        if (!generativeAgreementRows.isEmpty()) {
            sb.append("Generative agreement rows: ").append(generativeAgreementRows.size()).append("\n");
        }

        Files.write(path, sb.toString().getBytes());
    }

    private static String sourceNameForFile(String fileName) {
        for (SourceSpec sourceSpec : SOURCE_SPECS) {
            if (sourceSpec.fileName.equals(fileName)) {
                return sourceSpec.sourceName;
            }
        }
        return fileName;
    }

    static String formatMaybeDouble(Double value) {
        if (value == null || Double.isNaN(value)) {
            return "N/A";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatSize(double value) {
        if (value < 0) {
            return "-1";
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(raw.trim());
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Long.parseLong(raw.trim());
    }
}
