package com.articulate.nlp.morphodb.evaluation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata for a model directory under MorphoDB_Research.
 *
 * The directory name is the canonical identifier used throughout evaluation
 * outputs. Family, size, and tier are best-effort metadata used for sorting
 * and summary tables.
 */
public class ModelMetadata {

    private static final Pattern BILLION_PATTERN = Pattern.compile("(\\d+)(?:[_\\.-](\\d+))?b");
    private static final Pattern MILLION_PATTERN = Pattern.compile("(\\d+)(?:[_\\.-](\\d+))?m");

    private final String directoryName;
    private final double parameterBillions;
    private final String family;
    private final String tier;
    private final String latexDisplayName;

    /**
     * Registry for known models whose directory names do not cleanly encode all metadata.
     */
    private static final Map<String, ModelMetadata> REGISTRY = new LinkedHashMap<>();
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = new HashSet<>(Arrays.asList(
            "figures",
            "gold",
            "latex",
            "tables",
            "unimorphevaluation"
    ));

    static {
        register("gemma3_270m", 0.27, "Gemma", "Consumer", "gemma3:270m");
        register("qwen3_0_6b", 0.6, "Qwen", "Consumer", "qwen3:0.6b");
        register("gemma3_1b", 1.0, "Gemma", "Consumer", "gemma3:1b");
        register("llama3_2", 3.0, "LLaMA", "Consumer", "llama3.2");
        register("gemma3_4b", 4.0, "Gemma", "Consumer", "gemma3:4b");
        register("mistral", 7.0, "Mistral", "Consumer", "mistral:7b");
        register("qwen3-8b", 8.0, "Qwen", "Consumer", "qwen3:8b");
        register("llama3_1_8b", 8.0, "LLaMA", "Consumer", "llama3.1:8b");
        register("phi3_14b", 14.0, "Phi", "Consumer", "phi3:14b");
        register("gpt-oss-20b", 20.0, "GPT-OSS", "HPC", "gpt-oss:20b");
        register("gemma-3-27b-it", 27.0, "Gemma", "HPC", "gemma3:27b");
        register("qwen3-32b", 32.0, "Qwen", "HPC", "qwen3:32b");
        register("llama-3_3-70b-instruct", 70.0, "LLaMA", "HPC", "llama3.3:70b");
        register("gpt-oss-120b", 120.0, "GPT-OSS", "HPC", "gpt-oss:120b");
        register("ensemble_consumer", -1.0, "Ensemble", "Consumer", "ensemble consumer");
        register("ensemble_HPC", -1.0, "Ensemble", "HPC", "ensemble HPC");
        register("openai__gpt-5-nano", -1.0, "OpenAI", "Frontier", "gpt-5-nano");
        register("openai__gpt-5_2", -1.0, "OpenAI", "Frontier", "gpt-5.2");
    }

    private static void register(String directoryName, double parameterBillions, String family, String tier) {
        REGISTRY.put(directoryName, new ModelMetadata(directoryName, parameterBillions, family, tier));
    }

    private static void register(String directoryName, double parameterBillions, String family, String tier, String latexDisplayName) {
        REGISTRY.put(directoryName, new ModelMetadata(directoryName, parameterBillions, family, tier, latexDisplayName));
    }

    public ModelMetadata(String directoryName, double parameterBillions, String family, String tier) {
        this(directoryName, parameterBillions, family, tier, directoryName.replace("_", " "));
    }

    public ModelMetadata(String directoryName, double parameterBillions, String family, String tier, String latexDisplayName) {
        this.directoryName = directoryName;
        this.parameterBillions = parameterBillions;
        this.family = family;
        this.tier = tier;
        this.latexDisplayName = latexDisplayName;
    }

    /**
     * Returns known metadata when available, otherwise infers a best-effort record
     * directly from the directory name. Never returns null for a non-empty name.
     */
    public static ModelMetadata fromDirName(String dirName) {
        if (dirName == null || dirName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model directory name cannot be blank");
        }
        String trimmed = dirName.trim();
        if (REGISTRY.containsKey(trimmed)) {
            return REGISTRY.get(trimmed);
        }
        double size = inferParameterBillions(trimmed);
        String family = inferFamily(trimmed);
        String tier = inferTier(trimmed, size);
        return new ModelMetadata(trimmed, size, family, tier);
    }

    public String getDirectoryName() {
        return directoryName;
    }

    /**
     * The directory name is the canonical model identifier used in outputs.
     */
    public String getName() {
        return directoryName;
    }

    public double getParameterBillions() {
        return parameterBillions;
    }

    public String getFamily() {
        return family;
    }

    public String getTier() {
        return tier;
    }

    public String getLatexDisplayName() {
        return latexDisplayName;
    }

    public static boolean shouldIncludeAsModelDirectory(String directoryName) {
        if (directoryName == null || directoryName.trim().isEmpty()) {
            return false;
        }
        return !EXCLUDED_DIRECTORY_NAMES.contains(directoryName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isEnsemble() {
        return directoryName != null && directoryName.toLowerCase(Locale.ROOT).startsWith("ensemble");
    }

    public int getLatexGroupRank() {
        if (isEnsemble()) {
            return 3;
        }
        if ("Consumer".equals(tier)) {
            return 0;
        }
        if ("HPC".equals(tier)) {
            return 1;
        }
        if ("Frontier".equals(tier)) {
            return 2;
        }
        return 4;
    }

    public static int compareForLatex(ModelMetadata left, ModelMetadata right) {
        int groupCompare = Integer.compare(left.getLatexGroupRank(), right.getLatexGroupRank());
        if (groupCompare != 0) {
            return groupCompare;
        }

        if (left.isEnsemble() && right.isEnsemble()) {
            int ensembleCompare = Integer.compare(ensembleRankForLatex(left.directoryName), ensembleRankForLatex(right.directoryName));
            if (ensembleCompare != 0) {
                return ensembleCompare;
            }
        }

        if (left.getLatexGroupRank() == 2) {
            int frontierCompare = Integer.compare(frontierRankForLatex(left.directoryName), frontierRankForLatex(right.directoryName));
            if (frontierCompare != 0) {
                return frontierCompare;
            }
        }

        double leftSize = left.getParameterBillions();
        double rightSize = right.getParameterBillions();
        boolean leftKnown = leftSize >= 0;
        boolean rightKnown = rightSize >= 0;

        if (leftKnown && rightKnown) {
            int sizeCompare = Double.compare(leftSize, rightSize);
            if (sizeCompare != 0) {
                return sizeCompare;
            }
        } else if (leftKnown != rightKnown) {
            return leftKnown ? -1 : 1;
        }

        return left.getDirectoryName().compareTo(right.getDirectoryName());
    }

    public static boolean shouldInsertLatexSeparator(ModelMetadata previous, ModelMetadata current) {
        return previous != null && current != null && previous.getLatexGroupRank() != current.getLatexGroupRank();
    }

    public static int compareForDisplay(ModelMetadata left, ModelMetadata right) {
        double leftSize = left.getParameterBillions();
        double rightSize = right.getParameterBillions();
        boolean leftKnown = leftSize >= 0;
        boolean rightKnown = rightSize >= 0;

        if (leftKnown && rightKnown) {
            int sizeCompare = Double.compare(leftSize, rightSize);
            if (sizeCompare != 0) {
                return sizeCompare;
            }
        } else if (leftKnown != rightKnown) {
            return leftKnown ? -1 : 1;
        }

        return left.getName().compareTo(right.getName());
    }

    private static double inferParameterBillions(String dirName) {
        String normalized = dirName.toLowerCase(Locale.ROOT);
        Matcher billionMatcher = BILLION_PATTERN.matcher(normalized);
        if (billionMatcher.find()) {
            return parseDecimalParts(billionMatcher.group(1), billionMatcher.group(2));
        }
        Matcher millionMatcher = MILLION_PATTERN.matcher(normalized);
        if (millionMatcher.find()) {
            return parseDecimalParts(millionMatcher.group(1), millionMatcher.group(2)) / 1000.0;
        }
        return -1.0;
    }

    private static double parseDecimalParts(String whole, String fraction) {
        if (fraction == null || fraction.isEmpty()) {
            return Double.parseDouble(whole);
        }
        return Double.parseDouble(whole + "." + fraction);
    }

    private static String inferFamily(String dirName) {
        String lower = dirName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("openai")) return "OpenAI";
        if (lower.startsWith("claude")) return "Anthropic";
        if (lower.startsWith("gpt-oss")) return "GPT-OSS";
        if (lower.startsWith("qwen")) return "Qwen";
        if (lower.startsWith("gemma")) return "Gemma";
        if (lower.startsWith("llama")) return "LLaMA";
        if (lower.startsWith("phi")) return "Phi";
        if (lower.startsWith("mistral")) return "Mistral";

        String[] tokens = lower.split("[-_]+");
        if (tokens.length > 0 && !tokens[0].isEmpty()) {
            return tokens[0];
        }
        return "Unknown";
    }

    private static String inferTier(String dirName, double size) {
        String lower = dirName.toLowerCase(Locale.ROOT);
        if (Arrays.asList("openai", "claude").stream().anyMatch(lower::startsWith)) {
            return "Frontier";
        }
        if (size < 0) {
            return "Unknown";
        }
        if (size < 16.0) {
            return "Consumer";
        }
        return "HPC";
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

    private static int ensembleRankForLatex(String modelName) {
        if (modelName == null) {
            return Integer.MAX_VALUE;
        }
        if ("ensemble_consumer".equals(modelName)) {
            return 0;
        }
        if ("ensemble_HPC".equals(modelName)) {
            return 1;
        }
        return 100;
    }

    @Override
    public String toString() {
        String sizeText = parameterBillions < 0
                ? "unknown"
                : String.format(Locale.ROOT, "%.3fB", parameterBillions);
        return directoryName + " (" + family + ", " + sizeText + ", " + tier + ")";
    }
}
