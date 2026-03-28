package com.articulate.nlp.morphodb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/***************************************************************
 * Shared categorical schema definitions used by maintenance
 * normalization, evaluation, and human annotation.
 ***************************************************************/
public final class MorphoCategoricalSchema {

    public static final String CATEGORIZATION_STATUS_FIELD = "categorizationStatus";
    public static final String INVALID_CATEGORIZATION_STATUS = "invalid-categorization";
    public static final String INVALID_CATEGORIZATION_FIELD_FIELD = "invalidCategorizationField";
    public static final String INVALID_CATEGORIZATION_RAW_FIELD = "invalidCategorizationRaw";

    public static final class CategorySpec {
        private final String modeKey;
        private final String relativePath;
        private final String wordClass;
        private final String fieldName;
        private final List<String> canonicalOptions;
        private final Function<String, String> generationNormalizer;

        CategorySpec(String modeKey,
                     String relativePath,
                     String wordClass,
                     String fieldName,
                     Function<String, String> generationNormalizer,
                     String... canonicalOptions) {
            this.modeKey = modeKey;
            this.relativePath = relativePath;
            this.wordClass = wordClass;
            this.fieldName = fieldName;
            this.generationNormalizer = generationNormalizer;
            this.canonicalOptions = Collections.unmodifiableList(Arrays.asList(canonicalOptions));
        }

        public String getModeKey() {
            return modeKey;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getWordClass() {
            return wordClass;
        }

        public String getFieldName() {
            return fieldName;
        }

        public List<String> getCanonicalOptions() {
            return canonicalOptions;
        }

        public String[] getCanonicalOptionsArray() {
            return canonicalOptions.toArray(new String[0]);
        }

        public String normalizeGeneratedValue(String rawValue) {
            if (generationNormalizer == null) {
                return rawValue == null ? "" : rawValue;
            }
            return generationNormalizer.apply(rawValue);
        }

        public String canonicalizeStoredValue(String rawValue) {
            String collapsed = collapseWhitespace(rawValue);
            if (collapsed.isEmpty()) {
                return "";
            }
            for (String option : canonicalOptions) {
                if (option.equalsIgnoreCase(collapsed)) {
                    return option;
                }
            }
            return "";
        }

        public String quotedCanonicalOptions() {
            List<String> quoted = new ArrayList<>();
            for (String option : canonicalOptions) {
                quoted.add("\"" + option + "\"");
            }
            return String.join(", ", quoted);
        }

        public String placeholderCanonicalOptions() {
            return "<" + String.join(" | ", canonicalOptions) + ">";
        }
    }

    private static final List<CategorySpec> ALL_CATEGORY_SPECS;
    private static final Map<String, CategorySpec> CATEGORY_SPECS_BY_MODE;
    private static final Map<String, CategorySpec> CATEGORY_SPECS_BY_PATH;

    static {
        List<CategorySpec> specs = new ArrayList<>();
        specs.add(new CategorySpec(
                "countability",
                "noun/Countability.txt",
                "noun",
                "countability",
                MorphoFlatSchemaUtils::normalizeCountabilityCategory,
                "Count noun", "Mass noun", "Proper noun", "Count and mass noun", "Unknown"));
        specs.add(new CategorySpec(
                "humanness",
                "noun/Humanness.txt",
                "noun",
                "classification",
                MorphoFlatSchemaUtils::normalizeHumannessCategory,
                "Human", "Non-human", "Human and non-human", "Unknown"));
        specs.add(new CategorySpec(
                "agentivity",
                "noun/NounAgentivity.txt",
                "noun",
                "agency",
                MorphoFlatSchemaUtils::normalizeAgencyCategory,
                "Agentive", "Non-agentive", "Unknown"));
        specs.add(new CategorySpec(
                "collective",
                "noun/CollectiveNouns.txt",
                "noun",
                "collective",
                MorphoFlatSchemaUtils::normalizeCollectiveCategory,
                "Collective", "Not collective", "Context-dependent", "Unknown"));
        specs.add(new CategorySpec(
                "indefinite_article",
                "noun/IndefiniteArticles.txt",
                "noun",
                "article",
                MorphoCategoricalSchema::normalizeArticleValue,
                "a", "an", "none"));
        specs.add(new CategorySpec(
                "plural_regularity",
                null,
                "noun",
                "regularity",
                VerbConjugationUtils::normalizeRegularity,
                "Regular", "Irregular", "Unknown"));
        specs.add(new CategorySpec(
                "causativity",
                "verb/VerbCausativity.txt",
                "verb",
                "causativity",
                MorphoFlatSchemaUtils::normalizeCausativityCategory,
                "Causative", "Non-causative", "Mixed", "Unknown"));
        specs.add(new CategorySpec(
                "reflexivity",
                "verb/VerbReflexive.txt",
                "verb",
                "reflexivity",
                MorphoFlatSchemaUtils::normalizeReflexivityCategory,
                "Must be reflexive", "Can be reflexive", "Never reflexive"));
        specs.add(new CategorySpec(
                "reciprocity",
                "verb/VerbReciprocal.txt",
                "verb",
                "reciprocity",
                MorphoFlatSchemaUtils::normalizeReciprocityCategory,
                "Must be reciprocal", "Can be reciprocal", "Never reciprocal"));
        specs.add(new CategorySpec(
                "aktionsart",
                "verb/VerbAchievementProcess.txt",
                "verb",
                "aktionsart",
                MorphoFlatSchemaUtils::normalizeAktionsartCategory,
                "Achievement", "Process", "Mixed", "Unknown"));
        specs.add(new CategorySpec(
                "valence",
                "verb/VerbValence.txt",
                "verb",
                "valence",
                MorphoFlatSchemaUtils::normalizeValenceCategory,
                "0-valent", "1-valent", "2-valent", "3-valent", "Unknown"));
        specs.add(new CategorySpec(
                "conjugation_regularity",
                "verb/VerbConjugations.txt",
                "verb",
                "regularity",
                VerbConjugationUtils::normalizeRegularity,
                "Regular", "Irregular", "Unknown"));
        specs.add(new CategorySpec(
                "adjective_category",
                "adjective/AdjectiveSemanticClasses.txt",
                "adjective",
                "category",
                MorphoFlatSchemaUtils::normalizeAdjectiveCategory,
                "Descriptive / Qualitative",
                "Evaluative",
                "Quantitative / Indefinite",
                "Numeral",
                "Demonstrative (Deictic)",
                "Possessive",
                "Interrogative",
                "Distributive",
                "Proper / Nominal",
                "Other",
                "Unknown"));
        specs.add(new CategorySpec(
                "adverb_category",
                "adverb/AdverbSemanticClasses.txt",
                "adverb",
                "category",
                MorphoFlatSchemaUtils::normalizeAdverbCategory,
                "Manner",
                "Place / Location",
                "Direction / Path",
                "Time",
                "Duration",
                "Frequency",
                "Sequence",
                "Degree / Intensifier",
                "Approximator / Scalar",
                "Measure / Multiplier",
                "Epistemic",
                "Evidential",
                "Attitudinal / Evaluative",
                "Style / Domain",
                "Focus (additive / restrictive / emphatic)",
                "Negation / Polarity",
                "Affirmative",
                "Connective / Linking",
                "Topic-management / Discourse",
                "Interrogative",
                "Relative",
                "Unknown"));
        ALL_CATEGORY_SPECS = Collections.unmodifiableList(specs);

        Map<String, CategorySpec> byMode = new LinkedHashMap<>();
        Map<String, CategorySpec> byPath = new LinkedHashMap<>();
        for (CategorySpec spec : ALL_CATEGORY_SPECS) {
            byMode.put(spec.getModeKey(), spec);
            if (spec.getRelativePath() != null && !spec.getRelativePath().isEmpty()) {
                byPath.put(spec.getRelativePath(), spec);
            }
        }
        CATEGORY_SPECS_BY_MODE = Collections.unmodifiableMap(byMode);
        CATEGORY_SPECS_BY_PATH = Collections.unmodifiableMap(byPath);
    }

    private MorphoCategoricalSchema() {
    }

    public static List<CategorySpec> getAllCategorySpecs() {
        return ALL_CATEGORY_SPECS;
    }

    public static List<CategorySpec> getHumanAnnotationCategorySpecs() {
        return ALL_CATEGORY_SPECS;
    }

    public static List<CategorySpec> getNormalizationCategorySpecs() {
        List<CategorySpec> specs = new ArrayList<>();
        for (CategorySpec spec : ALL_CATEGORY_SPECS) {
            if (spec.getRelativePath() != null && !spec.getRelativePath().isEmpty()) {
                specs.add(spec);
            }
        }
        return specs;
    }

    public static CategorySpec getByModeKey(String modeKey) {
        return CATEGORY_SPECS_BY_MODE.get(modeKey);
    }

    public static CategorySpec getByRelativePath(String relativePath) {
        return CATEGORY_SPECS_BY_PATH.get(relativePath);
    }

    public static boolean isInvalidCategorizationStatus(String rawStatus) {
        return INVALID_CATEGORIZATION_STATUS.equalsIgnoreCase(collapseWhitespace(rawStatus));
    }

    public static boolean isInvalidCategorizationForField(String rawStatus, String rawFieldName, String expectedFieldName) {
        if (!isInvalidCategorizationStatus(rawStatus)) {
            return false;
        }
        String expected = expectedFieldName == null ? "" : expectedFieldName.trim();
        String actual = rawFieldName == null ? "" : rawFieldName.trim();
        return !expected.isEmpty() && expected.equals(actual);
    }

    private static String normalizeArticleValue(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return collapseWhitespace(rawValue).toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().replaceAll("\\s+", " ");
    }
}
