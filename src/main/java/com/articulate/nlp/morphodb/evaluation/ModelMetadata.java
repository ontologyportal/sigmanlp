package com.articulate.nlp.morphodb.evaluation;

import java.util.*;

/**
 * Metadata registry for language models.
 * Contains information about model families, parameter counts, and tiers.
 */
public class ModelMetadata {

    private final String name;
    private final double parameterBillions;
    private final String family;
    private final String tier;

    /**
     * Valid tier values.
     */
    private static final Set<String> VALID_TIERS = new HashSet<>(Arrays.asList(
        "Consumer", "HPC", "Frontier"
    ));

    /**
     * Registry of all known models, keyed by directory name.
     */
    public static final Map<String, ModelMetadata> REGISTRY = new LinkedHashMap<>();

    static {
        // Consumer tier
        REGISTRY.put("gemma3_270m", new ModelMetadata("Gemma 3 270M", 0.27, "Gemma", "Consumer"));
        REGISTRY.put("qwen3_0_6b", new ModelMetadata("Qwen 3 0.6B", 0.6, "Qwen", "Consumer"));
        REGISTRY.put("gemma3_1b", new ModelMetadata("Gemma 3 1B", 1.0, "Gemma", "Consumer"));
        REGISTRY.put("llama3_2", new ModelMetadata("LLaMA 3.2", 3.0, "LLaMA", "Consumer"));
        REGISTRY.put("gemma3_4b", new ModelMetadata("Gemma 3 4B", 4.0, "Gemma", "Consumer"));
        REGISTRY.put("mistral", new ModelMetadata("Mistral", 7.0, "Mistral", "Consumer"));
        REGISTRY.put("qwen3_8b", new ModelMetadata("Qwen 3 8B", 8.0, "Qwen", "Consumer"));
        REGISTRY.put("llama3_1_8b", new ModelMetadata("LLaMA 3.1 8B", 8.0, "LLaMA", "Consumer"));
        REGISTRY.put("phi3_14b", new ModelMetadata("Phi 3 14B", 14.0, "Phi", "Consumer"));

        // HPC tier
        REGISTRY.put("gpt_oss_20b", new ModelMetadata("GPT-OSS 20B", 20.0, "GPT-OSS", "HPC"));
        REGISTRY.put("gemma3_27b", new ModelMetadata("Gemma 3 27B", 27.0, "Gemma", "HPC"));
        REGISTRY.put("qwen3_32b", new ModelMetadata("Qwen 3 32B", 32.0, "Qwen", "HPC"));
        REGISTRY.put("llama3_1_70b", new ModelMetadata("LLaMA 3.1 70B", 70.0, "LLaMA", "HPC"));
        REGISTRY.put("gpt_oss_120b", new ModelMetadata("GPT-OSS 120B", 120.0, "GPT-OSS", "HPC"));

        // Frontier tier
        REGISTRY.put("chatgpt_5_2", new ModelMetadata("ChatGPT 5.2", -1.0, "OpenAI", "Frontier"));
        REGISTRY.put("claude_opus_4_6", new ModelMetadata("Claude Opus 4.6", -1.0, "Anthropic", "Frontier"));
    }

    /**
     * Constructor for ModelMetadata.
     *
     * @param name the display name of the model
     * @param parameterBillions number of parameters in billions (-1 for closed-source models)
     * @param family the model family (e.g., "Gemma", "LLaMA", "Anthropic")
     * @param tier the tier classification ("Consumer", "HPC", or "Frontier")
     * @throws IllegalArgumentException if tier is invalid
     */
    public ModelMetadata(String name, double parameterBillions, String family, String tier) {
        if (!VALID_TIERS.contains(tier)) {
            throw new IllegalArgumentException("Invalid tier: " + tier + ". Must be one of: " + VALID_TIERS);
        }
        this.name = name;
        this.parameterBillions = parameterBillions;
        this.family = family;
        this.tier = tier;
    }

    /**
     * Looks up a model by directory name.
     * Normalizes the directory name (lowercase, replace '-' and '.' with '_') before lookup.
     *
     * @param dirName the directory name (e.g., "gemma3-270m" or "gemma3.270m")
     * @return the ModelMetadata if found, null otherwise
     */
    public static ModelMetadata fromDirName(String dirName) {
        if (dirName == null || dirName.isEmpty()) {
            return null;
        }
        String normalized = dirName.toLowerCase().replace('-', '_').replace('.', '_');
        return REGISTRY.get(normalized);
    }

    /**
     * Gets the model name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the parameter count in billions.
     *
     * @return parameterBillions (-1 for closed-source models)
     */
    public double getParameterBillions() {
        return parameterBillions;
    }

    /**
     * Gets the model family.
     *
     * @return the family
     */
    public String getFamily() {
        return family;
    }

    /**
     * Gets the tier classification.
     *
     * @return the tier ("Consumer", "HPC", or "Frontier")
     */
    public String getTier() {
        return tier;
    }

    /**
     * Returns a string representation of the model.
     *
     * @return string in format "Name (Family, XB, Tier)" or "Name (Family, Closed-source, Tier)" for frontier
     */
    @Override
    public String toString() {
        String paramStr = parameterBillions < 0 ? "Closed-source" : String.format("%.1fB", parameterBillions);
        return String.format("%s (%s, %s, %s)", name, family, paramStr, tier);
    }

}
