package com.articulate.nlp.morphodb.evaluation;

import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EvaluationRunner: One-command entry point for generating all evaluation tables, LaTeX, and figures.
 *
 * CLI: java ... EvaluationRunner --input /path/to/MorphoDB [--gold /path/to/gold/] --output /path/to/results
 */
public class EvaluationRunner {

    /**
     * Inner class defining a property file specification.
     */
    static class PropertySpec {
        String fileName;      // Relative path within model dir (e.g., "noun/Countability.txt")
        String wordClass;     // "noun", "verb", "adjective", "adverb"
        String propertyName;  // e.g., "Countability", "VerbValence"
        String goldKey;       // Key matching HumanAnnotationTool property names (e.g., "countability")

        PropertySpec(String fileName, String wordClass, String propertyName, String goldKey) {
            this.fileName = fileName;
            this.wordClass = wordClass;
            this.propertyName = propertyName;
            this.goldKey = goldKey;
        }
    }

    /**
     * Holds aggregated statistics for a single property across all models.
     */
    static class PropertyStats {
        String propertyName;
        String wordClass;
        double krippendorffAlpha;
        double fleissKappa;
        double percentAgreement;
        double avgErrorRate;

        PropertyStats(String propertyName, String wordClass) {
            this.propertyName = propertyName;
            this.wordClass = wordClass;
        }
    }

    /**
     * Holds per-model evaluation results.
     */
    static class ModelResults {
        ModelMetadata metadata;
        Map<String, Double> cohensKappaByClass;  // word class → kappa vs gold
        Map<String, Double> errorRateByProperty; // property name → error rate %
        double avgKappa;
        double avgErrorRate;

        ModelResults(ModelMetadata metadata) {
            this.metadata = metadata;
            this.cohensKappaByClass = new HashMap<>();
            this.errorRateByProperty = new HashMap<>();
        }
    }

    static final List<PropertySpec> PROPERTY_SPECS = Arrays.asList(
        new PropertySpec("noun/Countability.txt", "noun", "Countability", "countability"),
        new PropertySpec("noun/Humanness.txt", "noun", "Humanness", "humanness"),
        new PropertySpec("noun/NounAgentivity.txt", "noun", "NounAgentivity", "agentivity"),
        new PropertySpec("noun/CollectiveNouns.txt", "noun", "CollectiveNouns", "collective"),
        new PropertySpec("noun/IndefiniteArticles.txt", "noun", "IndefiniteArticles", "indefinite_article"),
        new PropertySpec("noun/Plurals.txt", "noun", "Plurals", null),
        new PropertySpec("verb/VerbValence.txt", "verb", "VerbValence", "valence"),
        new PropertySpec("verb/VerbReflexive.txt", "verb", "VerbReflexive", "reflexivity"),
        new PropertySpec("verb/VerbCausativity.txt", "verb", "VerbCausativity", "causativity"),
        new PropertySpec("verb/VerbAchievementProcess.txt", "verb", "VerbAchievementProcess", "aktionsart"),
        new PropertySpec("verb/VerbReciprocal.txt", "verb", "VerbReciprocal", "reciprocity"),
        new PropertySpec("verb/VerbConjugations.txt", "verb", "VerbConjugations", "conjugation_regularity"),
        new PropertySpec("adjective/AdjectiveSemanticClasses.txt", "adjective", "AdjectiveSemanticClasses", "adjective_category"),
        new PropertySpec("adverb/AdverbSemanticClasses.txt", "adverb", "AdverbSemanticClasses", "adverb_category")
    );

    public static void main(String[] args) {
        try {
            // Parse CLI arguments
            Map<String, String> cliArgs = parseArgs(args);

            String inputDir = cliArgs.get("input");
            String goldDir = cliArgs.get("gold");
            String outputDir = cliArgs.get("output");

            if (inputDir == null || outputDir == null) {
                System.err.println("Usage: java EvaluationRunner --input <path> --output <path> [--gold <path>]");
                System.exit(1);
            }

            // Auto-detect gold directory inside MorphoDB if not explicitly provided
            if (goldDir == null) {
                Path autoGold = Paths.get(inputDir, "gold");
                if (Files.isDirectory(autoGold)) {
                    goldDir = autoGold.toString();
                }
            }

            System.out.println("Starting evaluation from: " + inputDir);
            System.out.println("Output directory: " + outputDir);
            if (goldDir != null) {
                System.out.println("Gold standard directory: " + goldDir);
            }

            // Create output directories
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath.resolve("tables"));
            Files.createDirectories(outputPath.resolve("latex"));
            Files.createDirectories(outputPath.resolve("figures"));

            // Step 1: Scan for model directories
            List<ModelMetadata> models = scanModelDirectories(inputDir);
            System.out.println("Found " + models.size() + " models");

            if (models.isEmpty()) {
                System.err.println("No valid models found in " + inputDir);
                System.exit(1);
            }

            // Step 2: Load property data for all models and properties
            Map<String, Map<String, Map<String, String>>> propertyData = new HashMap<>(); // prop → model → lemma → classification
            Map<String, Map<String, Map<String, Long>>> errorStats = new HashMap<>();      // prop → model → error stats
            Map<String, Map<String, String>> goldData = new HashMap<>();                   // prop → lemma → classification

            for (PropertySpec spec : PROPERTY_SPECS) {
                Map<String, Map<String, String>> modelMaps = new HashMap<>();
                Map<String, Map<String, Long>> modelErrors = new HashMap<>();

                for (ModelMetadata model : models) {
                    Path propFile = Paths.get(inputDir, model.getName(), spec.fileName);
                    if (Files.exists(propFile)) {
                        MorphoDBLoader.LoadResult result = MorphoDBLoader.loadDatabaseWithStats(propFile.toString());
                        modelMaps.put(model.getName(), result.classifications);
                        Map<String, Long> statsMap = new HashMap<>();
                        statsMap.put("errors", (long) result.errorRecords);
                        statsMap.put("total",  (long) result.totalRecords);
                        modelErrors.put(model.getName(), statsMap);
                    } else {
                        System.out.println("Warning: Property file not found for " + model.getName() + ": " + spec.fileName);
                    }
                }

                // Load gold standard if provided
                if (goldDir != null && spec.goldKey != null) {
                    Path goldFile = Paths.get(goldDir, spec.goldKey + ".jsonl");
                    if (Files.exists(goldFile)) {
                        MorphoDBLoader.LoadResult goldResult = MorphoDBLoader.loadDatabaseWithStats(goldFile.toString());
                        goldData.put(spec.propertyName, goldResult.classifications);
                    }
                }

                propertyData.put(spec.propertyName, modelMaps);
                errorStats.put(spec.propertyName, modelErrors);
            }

            // Step 3: Compute agreement metrics per property
            List<PropertyStats> propertyStatsList = new ArrayList<>();
            Map<String, Map<String, Double>> modelCohensKappas = new HashMap<>(); // model → class → kappa

            for (PropertySpec spec : PROPERTY_SPECS) {
                PropertyStats pStats = new PropertyStats(spec.propertyName, spec.wordClass);

                Map<String, Map<String, String>> modelMaps = propertyData.get(spec.propertyName);
                Map<String, String> gold = goldData.get(spec.propertyName);

                // Find common lemmas across all loaded models
                Set<String> commonLemmas = findCommonLemmas(modelMaps.values());

                if (!commonLemmas.isEmpty()) {
                    // Build per-item annotation sets: one map per lemma, model → classification
                    List<Map<String, String>> annotationSets = new ArrayList<>();
                    for (String lemma : commonLemmas) {
                        Map<String, String> item = new LinkedHashMap<>();
                        for (String model : modelMaps.keySet()) {
                            String val = modelMaps.get(model).get(lemma);
                            if (val != null) item.put(model, val);
                        }
                        if (item.size() > 1) annotationSets.add(item);
                    }

                    if (!annotationSets.isEmpty()) {
                        pStats.krippendorffAlpha = InterRaterStats.computeKrippendorffAlpha(annotationSets).alpha;
                        pStats.fleissKappa       = InterRaterStats.computeFleissKappa(annotationSets).kappa;
                    }

                    // Compute pairwise percent agreement
                    double totalAgreement = 0;
                    int comparisons = 0;
                    List<String> modelList = new ArrayList<>(modelMaps.keySet());
                    for (int i = 0; i < modelList.size(); i++) {
                        for (int j = i + 1; j < modelList.size(); j++) {
                            String model1 = modelList.get(i);
                            String model2 = modelList.get(j);
                            int matches = 0;
                            int total = 0;
                            for (String lemma : commonLemmas) {
                                String val1 = modelMaps.get(model1).get(lemma);
                                String val2 = modelMaps.get(model2).get(lemma);
                                if (val1 != null && val2 != null) {
                                    total++;
                                    if (val1.equals(val2)) {
                                        matches++;
                                    }
                                }
                            }
                            if (total > 0) {
                                totalAgreement += (double) matches / total;
                                comparisons++;
                            }
                        }
                    }
                    pStats.percentAgreement = comparisons > 0 ? totalAgreement / comparisons : 0;

                    // Compute Cohen's kappa vs gold if available
                    if (gold != null && !gold.isEmpty()) {
                        for (String model : modelMaps.keySet()) {
                            Map<String, String> modelFiltered = new HashMap<>();
                            Map<String, String> goldFiltered = new HashMap<>();

                            for (String lemma : commonLemmas) {
                                if (modelMaps.get(model).containsKey(lemma) && gold.containsKey(lemma)) {
                                    modelFiltered.put(lemma, modelMaps.get(model).get(lemma));
                                    goldFiltered.put(lemma, gold.get(lemma));
                                }
                            }

                            if (!modelFiltered.isEmpty()) {
                                InterRaterStats.CohensKappaResult result = InterRaterStats.computeCohensKappa(modelFiltered, goldFiltered);
                                modelCohensKappas.computeIfAbsent(model, k -> new HashMap<>())
                                    .put(spec.wordClass, result.kappa);
                            }
                        }
                    }
                }

                // Compute average error rate
                double totalErrorRate = 0;
                int modelCount = 0;
                for (String model : errorStats.get(spec.propertyName).keySet()) {
                    Map<String, Long> stats = errorStats.get(spec.propertyName).get(model);
                    long errors = stats.getOrDefault("errors", 0L);
                    long total = stats.getOrDefault("total", 1L);
                    totalErrorRate += (double) errors / total * 100;
                    modelCount++;
                }
                pStats.avgErrorRate = modelCount > 0 ? totalErrorRate / modelCount : 0;

                propertyStatsList.add(pStats);
            }

            // Step 4: Compute model results
            List<ModelResults> modelResultsList = new ArrayList<>();
            for (ModelMetadata model : models) {
                ModelResults mResults = new ModelResults(model);

                // Compute per-class kappa by averaging properties
                Map<String, List<Double>> kappasByClass = new HashMap<>();
                for (PropertySpec spec : PROPERTY_SPECS) {
                    Double kappa = modelCohensKappas.getOrDefault(model.getName(), new HashMap<>()).get(spec.wordClass);
                    if (kappa != null) {
                        kappasByClass.computeIfAbsent(spec.wordClass, k -> new ArrayList<>()).add(kappa);
                    }
                }
                for (String wordClass : kappasByClass.keySet()) {
                    double avg = kappasByClass.get(wordClass).stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0);
                    mResults.cohensKappaByClass.put(wordClass, avg);
                }
                mResults.avgKappa = mResults.cohensKappaByClass.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);

                // Compute per-property error rates
                double totalErrorRate = 0;
                int propCount = 0;
                for (PropertySpec spec : PROPERTY_SPECS) {
                    Map<String, Long> stats = errorStats.getOrDefault(spec.propertyName, new HashMap<>())
                        .getOrDefault(model.getName(), new HashMap<>());
                    long errors = stats.getOrDefault("errors", 0L);
                    long total = stats.getOrDefault("total", 1L);
                    double errorRate = (double) errors / total * 100;
                    mResults.errorRateByProperty.put(spec.propertyName, errorRate);
                    totalErrorRate += errorRate;
                    propCount++;
                }
                mResults.avgErrorRate = propCount > 0 ? totalErrorRate / propCount : 0;

                modelResultsList.add(mResults);
            }

            // Sort models by parameter size
            modelResultsList.sort((a, b) -> Double.compare(a.metadata.getParameterBillions(), b.metadata.getParameterBillions()));

            // Step 5: Compute pairwise agreement matrix
            Map<String, Map<String, Double>> pairwiseAgreement = computePairwiseAgreement(
                models, propertyData, PROPERTY_SPECS
            );

            // Step 6: Export CSVs
            exportModelKappaCSV(outputPath.resolve("tables/model_kappa.csv"), modelResultsList, goldDir != null);
            exportPropertyAlphaCSV(outputPath.resolve("tables/property_alpha.csv"), propertyStatsList);
            exportErrorRatesCSV(outputPath.resolve("tables/error_rates.csv"), modelResultsList, PROPERTY_SPECS);
            exportPairwiseAgreementCSV(outputPath.resolve("tables/pairwise_agreement.csv"), pairwiseAgreement, models);
            exportArchitecturePairsCSV(outputPath.resolve("tables/architecture_pairs.csv"),
                modelResultsList, propertyStatsList);

            // Step 7: Export LaTeX
            exportLatexTables(outputPath.resolve("latex/all_tables.tex"),
                modelResultsList, propertyStatsList, pairwiseAgreement, models);

            // Step 8: Invoke Python for figures
            invokePythonPlotter(outputPath.resolve("tables").toString(),
                outputPath.resolve("figures").toString());

            // Step 9: Write summary
            writeSummary(outputPath.resolve("summary.txt"), modelResultsList, propertyStatsList);

            System.out.println("Evaluation complete. Results in: " + outputDir);

        } catch (Exception e) {
            System.err.println("Error during evaluation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parse command-line arguments into a map.
     */
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) {
                String key = args[i].substring(2);
                String value = args[i + 1];
                result.put(key, value);
                i++;
            }
        }
        return result;
    }

    /**
     * Scan the input directory for valid model subdirectories.
     */
    static List<ModelMetadata> scanModelDirectories(String inputDir) throws IOException {
        List<ModelMetadata> models = new ArrayList<>();
        File dir = new File(inputDir);

        if (!dir.isDirectory()) {
            System.err.println("Input directory not found: " + inputDir);
            return models;
        }

        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs == null) return models;

        for (File subdir : subdirs) {
            try {
                ModelMetadata metadata = ModelMetadata.fromDirName(subdir.getName());
                models.add(metadata);
            } catch (IllegalArgumentException e) {
                System.out.println("Warning: Skipping unknown directory: " + subdir.getName());
            }
        }

        return models;
    }

    /**
     * Find common lemmas across all model maps for a property.
     */
    static Set<String> findCommonLemmas(Collection<Map<String, String>> maps) {
        if (maps.isEmpty()) return new HashSet<>();

        Set<String> common = new HashSet<>(maps.iterator().next().keySet());
        for (Map<String, String> map : maps) {
            common.retainAll(map.keySet());
        }
        return common;
    }

    /**
     * Compute pairwise percent agreement across all properties combined.
     */
    static Map<String, Map<String, Double>> computePairwiseAgreement(
            List<ModelMetadata> models,
            Map<String, Map<String, Map<String, String>>> propertyData,
            List<PropertySpec> specs) {

        Map<String, Map<String, Double>> result = new HashMap<>();
        List<String> modelNames = models.stream().map(m -> m.getName()).collect(Collectors.toList());

        for (int i = 0; i < modelNames.size(); i++) {
            result.put(modelNames.get(i), new HashMap<>());
            for (int j = 0; j < modelNames.size(); j++) {
                if (i == j) {
                    result.get(modelNames.get(i)).put(modelNames.get(j), 1.0);
                } else {
                    String model1 = modelNames.get(i);
                    String model2 = modelNames.get(j);

                    int totalMatches = 0;
                    int totalComparisons = 0;

                    for (PropertySpec spec : specs) {
                        Map<String, Map<String, String>> modelMaps = propertyData.get(spec.propertyName);
                        Map<String, String> map1 = modelMaps.get(model1);
                        Map<String, String> map2 = modelMaps.get(model2);

                        if (map1 != null && map2 != null) {
                            Set<String> commonLemmas = new HashSet<>(map1.keySet());
                            commonLemmas.retainAll(map2.keySet());

                            for (String lemma : commonLemmas) {
                                totalComparisons++;
                                if (map1.get(lemma).equals(map2.get(lemma))) {
                                    totalMatches++;
                                }
                            }
                        }
                    }

                    double agreement = totalComparisons > 0 ? (double) totalMatches / totalComparisons : 0;
                    result.get(model1).put(model2, agreement);
                }
            }
        }

        return result;
    }

    /**
     * Export model_kappa.csv
     */
    static void exportModelKappaCSV(Path path, List<ModelResults> results, boolean hasGold) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,tier,size_b,noun_kappa,verb_kappa,adj_kappa,adv_kappa,avg_kappa\n");

        for (ModelResults r : results) {
            sb.append(r.metadata.getName()).append(",");
            sb.append(r.metadata.getTier()).append(",");
            sb.append(r.metadata.getParameterBillions()).append(",");

            if (hasGold) {
                sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("noun", 0.0))).append(",");
                sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("verb", 0.0))).append(",");
                sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("adjective", 0.0))).append(",");
                sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("adverb", 0.0))).append(",");
                sb.append(formatDouble(r.avgKappa)).append("\n");
            } else {
                // If no gold, use Krippendorff's alpha as proxy
                sb.append(",,,,,").append(formatDouble(0.0)).append("\n");
            }
        }

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Export property_alpha.csv
     */
    static void exportPropertyAlphaCSV(Path path, List<PropertyStats> stats) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("property,word_class,kripp_alpha,fleiss_kappa,pct_agree,avg_error_rate\n");

        for (PropertyStats s : stats) {
            sb.append(s.propertyName).append(",");
            sb.append(s.wordClass).append(",");
            sb.append(formatDouble(s.krippendorffAlpha)).append(",");
            sb.append(formatDouble(s.fleissKappa)).append(",");
            sb.append(formatDouble(s.percentAgreement)).append(",");
            sb.append(formatDouble(s.avgErrorRate)).append("\n");
        }

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Export error_rates.csv
     */
    static void exportErrorRatesCSV(Path path, List<ModelResults> results, List<PropertySpec> specs) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("model,tier,size_b");
        for (PropertySpec spec : specs) {
            sb.append(",").append(spec.propertyName);
        }
        sb.append(",avg_error_rate\n");

        for (ModelResults r : results) {
            sb.append(r.metadata.getName()).append(",");
            sb.append(r.metadata.getTier()).append(",");
            sb.append(r.metadata.getParameterBillions()).append(",");

            for (PropertySpec spec : specs) {
                Double rate = r.errorRateByProperty.getOrDefault(spec.propertyName, 0.0);
                sb.append(formatDouble(rate)).append(",");
            }
            sb.append(formatDouble(r.avgErrorRate)).append("\n");
        }

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Export pairwise_agreement.csv
     */
    static void exportPairwiseAgreementCSV(Path path,
            Map<String, Map<String, Double>> agreement,
            List<ModelMetadata> models) throws IOException {
        StringBuilder sb = new StringBuilder();

        List<String> modelNames = models.stream().map(m -> m.getName()).collect(Collectors.toList());
        sb.append("model");
        for (String name : modelNames) {
            sb.append(",").append(name);
        }
        sb.append("\n");

        for (String model1 : modelNames) {
            sb.append(model1);
            for (String model2 : modelNames) {
                Double val = agreement.getOrDefault(model1, new HashMap<>()).getOrDefault(model2, 0.0);
                sb.append(",").append(formatDouble(val));
            }
            sb.append("\n");
        }

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Export architecture_pairs.csv for same-size comparison pairs.
     */
    static void exportArchitecturePairsCSV(Path path,
            List<ModelResults> results,
            List<PropertyStats> propertyStats) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("pair_name,model_a,model_b,property,kappa_a,kappa_b\n");

        // Define known same-size pairs
        List<String[]> pairs = Arrays.asList(
            new String[] {"qwen3:8b", "llama3.1:8b"},
            new String[] {"gemma3:27b", "qwen3:32b"}
        );

        Map<String, ModelResults> resultsByName = results.stream()
            .collect(Collectors.toMap(r -> r.metadata.getName(), r -> r));

        for (String[] pair : pairs) {
            ModelResults modelA = resultsByName.get(pair[0]);
            ModelResults modelB = resultsByName.get(pair[1]);

            if (modelA != null && modelB != null) {
                for (PropertyStats pstat : propertyStats) {
                    sb.append(pair[0]).append("_vs_").append(pair[1]).append(",");
                    sb.append(pair[0]).append(",");
                    sb.append(pair[1]).append(",");
                    sb.append(pstat.propertyName).append(",");

                    Double kappaA = modelA.cohensKappaByClass.get(pstat.wordClass);
                    Double kappaB = modelB.cohensKappaByClass.get(pstat.wordClass);

                    sb.append(formatDouble(kappaA != null ? kappaA : 0.0)).append(",");
                    sb.append(formatDouble(kappaB != null ? kappaB : 0.0)).append("\n");
                }
            }
        }

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Export LaTeX tables for the paper.
     */
    static void exportLatexTables(Path path,
            List<ModelResults> results,
            List<PropertyStats> propertyStats,
            Map<String, Map<String, Double>> pairwiseAgreement,
            List<ModelMetadata> models) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("% Auto-generated LaTeX tables\n\n");

        // Table 1: Model Kappa
        sb.append("\\begin{table}[h]\n");
        sb.append("\\centering\n");
        sb.append("\\caption{Model Agreement with Gold Standard (Cohen's $\\kappa$)}\n");
        sb.append("\\begin{tabular}{@{}lrrrrrr@{}}\n");
        sb.append("\\toprule\n");
        sb.append("Model & Size & Noun & Verb & Adj. & Adv. & Avg. \\\\\n");
        sb.append("\\midrule\n");

        for (ModelResults r : results) {
            sb.append(r.metadata.getName()).append(" & ");
            sb.append(formatDouble(r.metadata.getParameterBillions())).append(" & ");
            sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("noun", 0.0))).append(" & ");
            sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("verb", 0.0))).append(" & ");
            sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("adjective", 0.0))).append(" & ");
            sb.append(formatDouble(r.cohensKappaByClass.getOrDefault("adverb", 0.0))).append(" & ");
            sb.append(formatDouble(r.avgKappa)).append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{table}\n\n");

        // Table 2: Property Statistics
        sb.append("\\begin{table}[h]\n");
        sb.append("\\centering\n");
        sb.append("\\caption{Inter-Annotator Agreement by Property}\n");
        sb.append("\\begin{tabular}{@{}lrrrr@{}}\n");
        sb.append("\\toprule\n");
        sb.append("Property & Kripp. $\\alpha$ & Fleiss' $\\kappa$ & Pct. Agree & Err. Rate \\\\\n");
        sb.append("\\midrule\n");

        for (PropertyStats s : propertyStats) {
            sb.append(s.propertyName).append(" & ");
            sb.append(formatDouble(s.krippendorffAlpha)).append(" & ");
            sb.append(formatDouble(s.fleissKappa)).append(" & ");
            sb.append(formatDouble(s.percentAgreement)).append(" & ");
            sb.append(String.format("%.2f\\%%", s.avgErrorRate)).append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{table}\n\n");

        // Table 3: Pairwise Agreement
        sb.append("\\begin{table}[h]\n");
        sb.append("\\centering\n");
        sb.append("\\caption{Pairwise Percent Agreement Across All Properties}\n");
        sb.append("\\begin{tabular}{@{}");
        for (int i = 0; i <= models.size(); i++) {
            sb.append("r");
        }
        sb.append("@{}}\n");
        sb.append("\\toprule\n");
        sb.append("Model");
        for (ModelMetadata m : models) {
            sb.append(" & ").append(m.getName());
        }
        sb.append(" \\\\\n");
        sb.append("\\midrule\n");

        List<String> modelNames = models.stream().map(m -> m.getName()).collect(Collectors.toList());
        for (String model1 : modelNames) {
            sb.append(model1);
            for (String model2 : modelNames) {
                Double val = pairwiseAgreement.getOrDefault(model1, new HashMap<>()).getOrDefault(model2, 0.0);
                sb.append(" & ").append(formatDouble(val));
            }
            sb.append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{table}\n\n");

        // Table 4: Error Rates (sample)
        sb.append("\\begin{table}[h]\n");
        sb.append("\\centering\n");
        sb.append("\\caption{Average Error Rates by Model}\n");
        sb.append("\\begin{tabular}{@{}lrr@{}}\n");
        sb.append("\\toprule\n");
        sb.append("Model & Size & Avg. Error Rate \\\\\n");
        sb.append("\\midrule\n");

        for (ModelResults r : results) {
            sb.append(r.metadata.getName()).append(" & ");
            sb.append(formatDouble(r.metadata.getParameterBillions())).append(" & ");
            sb.append(String.format("%.2f\\%%", r.avgErrorRate)).append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{table}\n\n");

        // Table 5: Architecture Comparison
        sb.append("\\begin{table}[h]\n");
        sb.append("\\centering\n");
        sb.append("\\caption{Architecture Comparison (Same-Size Pairs)}\n");
        sb.append("\\begin{tabular}{@{}lrr@{}}\n");
        sb.append("\\toprule\n");
        sb.append("Pair & Model A Kappa & Model B Kappa \\\\\n");
        sb.append("\\midrule\n");

        // Sample comparison pairs
        String[] pairs = {"qwen3:8b_vs_llama3.1:8b", "gemma3:27b_vs_qwen3:32b"};
        for (String pair : pairs) {
            sb.append(pair.replace("_vs_", " vs. ")).append(" & ");
            sb.append("(see architecture\\_pairs.csv)").append(" \\\\\n");
        }

        sb.append("\\bottomrule\n");
        sb.append("\\end{tabular}\n");
        sb.append("\\end{table}\n");

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Invoke Python script for figure generation.
     */
    static void invokePythonPlotter(String tablesDir, String figuresDir) {
        try {
            String plotScript = System.getenv("MORPHODB_PLOT_SCRIPT");
            if (plotScript == null) {
                plotScript = "plot_figures.py";
            }

            ProcessBuilder pb = new ProcessBuilder("python3", plotScript, "--input", tablesDir, "--output", figuresDir);
            pb.inheritIO();

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.out.println("Warning: Python figure generation exited with code " + exitCode);
            } else {
                System.out.println("Figures generated successfully");
            }
        } catch (Exception e) {
            System.out.println("Warning: Could not invoke Python plotter: " + e.getMessage());
        }
    }

    /**
     * Write human-readable summary.
     */
    static void writeSummary(Path path, List<ModelResults> results, List<PropertyStats> stats) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("EVALUATION SUMMARY\n");
        sb.append("==================\n\n");

        sb.append("MODELS EVALUATED: ").append(results.size()).append("\n");
        for (ModelResults r : results) {
            sb.append("  - ").append(r.metadata.getName()).append(" (").append(r.metadata.getParameterBillions()).append("B)\n");
        }
        sb.append("\n");

        sb.append("PROPERTIES: ").append(stats.size()).append("\n");
        for (PropertyStats s : stats) {
            sb.append("  - ").append(s.propertyName).append(" [").append(s.wordClass).append("]\n");
            sb.append("    Krippendorff's α: ").append(formatDouble(s.krippendorffAlpha)).append("\n");
            sb.append("    Fleiss' κ: ").append(formatDouble(s.fleissKappa)).append("\n");
            sb.append("    Pct. Agreement: ").append(formatDouble(s.percentAgreement)).append("\n");
            sb.append("    Avg. Error Rate: ").append(formatDouble(s.avgErrorRate)).append("%\n\n");
        }

        sb.append("MODEL PERFORMANCE:\n");
        double avgKappaAll = 0;
        for (ModelResults r : results) {
            sb.append("  ").append(r.metadata.getName()).append(":\n");
            sb.append("    Avg Cohen's κ: ").append(formatDouble(r.avgKappa)).append("\n");
            sb.append("    Avg Error Rate: ").append(formatDouble(r.avgErrorRate)).append("%\n");
            avgKappaAll += r.avgKappa;
        }
        sb.append("\n  Overall Avg κ: ").append(formatDouble(avgKappaAll / results.size())).append("\n");

        Files.write(path, sb.toString().getBytes());
    }

    /**
     * Format double for CSV output (3 decimal places).
     */
    static String formatDouble(double value) {
        return String.format("%.3f", value);
    }

}
