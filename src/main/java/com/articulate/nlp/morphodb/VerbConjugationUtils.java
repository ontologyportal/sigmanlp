package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.nlp.morphodb.evaluation.GenerativeEvalUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Shared normalization and validation helpers for verb conjugation
 * records. A valid persisted conjugation row must contain the full
 * prompt-defined 16-tense table with all six subject forms filled.
 ***************************************************************/
public final class VerbConjugationUtils {

    public enum ConjugationCompleteness {
        COMPLETE,
        PARTIAL,
        ERROR
    }

    public static final class CanonicalizationResult {
        public final ConjugationCompleteness completeness;
        public final ObjectNode record;
        public final int filledSlotCount;
        public final int totalSlotCount;
        public final int filledTenseCount;
        public final int totalTenseCount;

        CanonicalizationResult(ConjugationCompleteness completeness,
                               ObjectNode record,
                               int filledSlotCount,
                               int totalSlotCount,
                               int filledTenseCount,
                               int totalTenseCount) {
            this.completeness = completeness;
            this.record = record;
            this.filledSlotCount = filledSlotCount;
            this.totalSlotCount = totalSlotCount;
            this.filledTenseCount = filledTenseCount;
            this.totalTenseCount = totalTenseCount;
        }

        public boolean isComplete() {
            return completeness == ConjugationCompleteness.COMPLETE;
        }

        public boolean isPartial() {
            return completeness == ConjugationCompleteness.PARTIAL;
        }
    }

    public static final List<String> PERSON_PRONOUN_KEYS =
            Collections.unmodifiableList(Arrays.asList(
                    "i", "you_singular", "he_she_it", "we", "you_plural", "they"));

    public static final List<String> CANONICAL_TENSES =
            Collections.unmodifiableList(Arrays.asList(
                    "Infinitive",
                    "Simple present",
                    "Simple past",
                    "Simple future",
                    "Present progressive",
                    "Past progressive",
                    "Future progressive",
                    "Present perfect",
                    "Past perfect",
                    "Future perfect",
                    "Present perfect progressive",
                    "Past perfect progressive",
                    "Future perfect progressive",
                    "Imperative",
                    "Gerund / present participle",
                    "Past participle"
            ));

    private static final Set<String> CANONICAL_TENSE_SET =
            Collections.unmodifiableSet(new LinkedHashSet<>(CANONICAL_TENSES));
    public static final int TOTAL_CANONICAL_TENSE_COUNT = CANONICAL_TENSES.size();
    public static final int TOTAL_CANONICAL_SLOT_COUNT = TOTAL_CANONICAL_TENSE_COUNT * PERSON_PRONOUN_KEYS.size();

    private VerbConjugationUtils() {
    }

    public static CanonicalizationResult canonicalizeRecord(JsonNode rawNode,
                                                            String fallbackSynsetId,
                                                            String fallbackLemma,
                                                            String fallbackVerb) {

        if (rawNode == null || !rawNode.isObject()) {
            return new CanonicalizationResult(ConjugationCompleteness.ERROR, null, 0,
                    TOTAL_CANONICAL_SLOT_COUNT, 0, TOTAL_CANONICAL_TENSE_COUNT);
        }
        String synsetId = firstNonEmpty(rawNode.path("synsetId").asText(""), fallbackSynsetId);
        if (synsetId.isEmpty()) {
            return new CanonicalizationResult(ConjugationCompleteness.ERROR, null, 0,
                    TOTAL_CANONICAL_SLOT_COUNT, 0, TOTAL_CANONICAL_TENSE_COUNT);
        }
        String verb = firstNonEmpty(rawNode.path("verb").asText(""), fallbackVerb);
        if (verb.isEmpty()) {
            return new CanonicalizationResult(ConjugationCompleteness.ERROR, null, 0,
                    TOTAL_CANONICAL_SLOT_COUNT, 0, TOTAL_CANONICAL_TENSE_COUNT);
        }
        String lemma = GenMorphoUtils.normalizeLemma(
                firstNonEmpty(rawNode.path("lemma").asText(""), fallbackLemma, verb));
        if (lemma.isEmpty()) {
            return new CanonicalizationResult(ConjugationCompleteness.ERROR, null, 0,
                    TOTAL_CANONICAL_SLOT_COUNT, 0, TOTAL_CANONICAL_TENSE_COUNT);
        }

        LinkedHashMap<String, ObjectNode> canonicalTenseMap = canonicalizeSparseTenseMap(rawNode.get("tenses"));
        int filledSlotCount = countFilledSlots(canonicalTenseMap);
        int filledTenseCount = canonicalTenseMap.size();
        if (filledSlotCount <= 0) {
            return new CanonicalizationResult(ConjugationCompleteness.ERROR, null, 0,
                    TOTAL_CANONICAL_SLOT_COUNT, 0, TOTAL_CANONICAL_TENSE_COUNT);
        }

        ConjugationCompleteness completeness =
                filledSlotCount == TOTAL_CANONICAL_SLOT_COUNT
                        ? ConjugationCompleteness.COMPLETE
                        : ConjugationCompleteness.PARTIAL;

        ArrayNode orderedTenses = buildOrderedTenses(canonicalTenseMap, completeness == ConjugationCompleteness.COMPLETE);
        ObjectNode record = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        record.put("synsetId", synsetId);
        record.put("lemma", lemma);
        record.put("verb", verb);
        record.set("tenses", orderedTenses);

        if (completeness == ConjugationCompleteness.PARTIAL) {
            record.put("status", "partial");
            record.put("filledSlotCount", filledSlotCount);
            record.put("totalSlotCount", TOTAL_CANONICAL_SLOT_COUNT);
            record.put("filledTenseCount", filledTenseCount);
            record.put("totalTenseCount", TOTAL_CANONICAL_TENSE_COUNT);
        }

        String derivedRegularity = inferVerbRegularity(lemma, verb, orderedTenses);
        if ("Regular".equals(derivedRegularity) || "Irregular".equals(derivedRegularity)) {
            record.put("regularity_derived", derivedRegularity);
            String regularity = normalizeRegularity(rawNode.path("regularity").asText(""));
            if (!"Regular".equals(regularity) && !"Irregular".equals(regularity)) {
                regularity = derivedRegularity;
            }
            record.put("regularity", regularity);
        } else if (completeness == ConjugationCompleteness.COMPLETE) {
            record.put("regularity_derived", derivedRegularity);
            String regularity = normalizeRegularity(rawNode.path("regularity").asText(""));
            if (!"Regular".equals(regularity) && !"Irregular".equals(regularity)) {
                regularity = derivedRegularity;
            }
            record.put("regularity", regularity);
        }

        String notes = rawNode.path("notes").asText("").trim();
        if (!notes.isEmpty()) {
            record.put("notes", notes);
        }
        return new CanonicalizationResult(completeness, record, filledSlotCount,
                TOTAL_CANONICAL_SLOT_COUNT, filledTenseCount, TOTAL_CANONICAL_TENSE_COUNT);
    }

    public static ObjectNode canonicalizeCompleteRecord(JsonNode rawNode,
                                                        String fallbackSynsetId,
                                                        String fallbackLemma,
                                                        String fallbackVerb) {

        CanonicalizationResult result = canonicalizeRecord(rawNode, fallbackSynsetId, fallbackLemma, fallbackVerb);
        return result.isComplete() ? result.record : null;
    }

    public static ArrayNode canonicalizeCompleteTenses(JsonNode rawTenses) {

        LinkedHashMap<String, ObjectNode> tenseMap = canonicalizeSparseTenseMap(rawTenses);
        if (tenseMap.size() != CANONICAL_TENSES.size() || countFilledSlots(tenseMap) != TOTAL_CANONICAL_SLOT_COUNT) {
            return null;
        }
        return buildOrderedTenses(tenseMap, true);
    }

    public static String normalizeRegularity(String rawRegularity) {

        if (rawRegularity == null || rawRegularity.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawRegularity.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("irregular")) {
            return "Irregular";
        }
        if (lower.contains("regular")) {
            return "Regular";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeTenseName(String rawName) {

        if (rawName == null) {
            return "";
        }
        String lower = rawName.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return "";
        }
        if (lower.contains("infinitive") || lower.contains("base form")) {
            return "Infinitive";
        }
        if (lower.contains("simple present") || lower.contains("present simple")) {
            return "Simple present";
        }
        if (lower.contains("simple past") || lower.contains("past simple")) {
            return "Simple past";
        }
        if (lower.contains("simple future") || lower.contains("future simple")) {
            return "Simple future";
        }
        if ((lower.contains("present progressive") || lower.contains("present continuous"))
                && !lower.contains("perfect")) {
            return "Present progressive";
        }
        if ((lower.contains("past progressive") || lower.contains("past continuous"))
                && !lower.contains("perfect")) {
            return "Past progressive";
        }
        if ((lower.contains("future progressive") || lower.contains("future continuous"))
                && !lower.contains("perfect")) {
            return "Future progressive";
        }
        if (lower.contains("present perfect progressive") || lower.contains("present perfect continuous")) {
            return "Present perfect progressive";
        }
        if (lower.contains("past perfect progressive") || lower.contains("past perfect continuous")) {
            return "Past perfect progressive";
        }
        if (lower.contains("future perfect progressive") || lower.contains("future perfect continuous")) {
            return "Future perfect progressive";
        }
        if (lower.contains("present perfect")) {
            return "Present perfect";
        }
        if (lower.contains("past perfect")) {
            return "Past perfect";
        }
        if (lower.contains("future perfect")) {
            return "Future perfect";
        }
        if (lower.contains("imperative")) {
            return "Imperative";
        }
        if (lower.contains("gerund") || lower.contains("present participle")) {
            return "Gerund / present participle";
        }
        if (lower.contains("past participle")) {
            return "Past participle";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizePronounKey(String rawKey) {

        if (rawKey == null) {
            return null;
        }
        String normalized = rawKey.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        switch (normalized) {
            case "i":
            case "first_person":
            case "first_person_singular":
            case "1st_person_singular":
            case "first_singular":
            case "1sg":
                return "i";
            case "you":
            case "you_singular":
            case "second_person":
            case "second_person_singular":
            case "2nd_person_singular":
            case "singular_you":
            case "2sg":
                return "you_singular";
            case "he":
            case "she":
            case "it":
            case "he_she":
            case "she_he":
            case "he_she_it":
            case "third_person_singular":
            case "3rd_person_singular":
            case "third_singular":
            case "3sg":
                return "he_she_it";
            case "we":
            case "first_person_plural":
            case "1st_person_plural":
            case "first_plural":
            case "1pl":
                return "we";
            case "you_plural":
            case "youplural":
            case "second_person_plural":
            case "2nd_person_plural":
            case "plural_you":
            case "2pl":
            case "yall":
            case "ya_ll":
            case "ye":
                return "you_plural";
            case "they":
            case "third_person_plural":
            case "3rd_person_plural":
            case "third_plural":
            case "3pl":
                return "they";
            default:
                return null;
        }
    }

    public static ObjectNode normalizeConjugationForms(JsonNode rawForms) {

        ObjectNode forms = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        for (String pronoun : PERSON_PRONOUN_KEYS) {
            forms.put(pronoun, "");
        }
        forms.put("summary", "");

        if (rawForms == null || rawForms.isNull()) {
            return forms;
        }
        if (rawForms.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = rawForms.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String canonicalPronoun = normalizePronounKey(field.getKey());
                String value = field.getValue() == null ? "" : field.getValue().asText("").trim();
                if (canonicalPronoun != null) {
                    forms.put(canonicalPronoun, value);
                    continue;
                }
                String lowerFieldName = field.getKey().trim().toLowerCase(Locale.ROOT);
                if ("summary".equals(lowerFieldName) || "note".equals(lowerFieldName) || "notes".equals(lowerFieldName)) {
                    forms.put("summary", value);
                }
            }
            return forms;
        }
        if (rawForms.isArray()) {
            List<String> collected = new ArrayList<>();
            for (JsonNode node : rawForms) {
                String value = node == null ? "" : node.asText("").trim();
                if (!value.isEmpty()) {
                    collected.add(value);
                }
            }
            forms.put("summary", String.join("; ", collected).trim());
            return forms;
        }
        forms.put("summary", rawForms.asText("").trim());
        return forms;
    }

    private static List<JsonNode> flattenRawTenseEntries(JsonNode rawTenses) {

        List<JsonNode> entries = new ArrayList<>();
        if (rawTenses == null || rawTenses.isNull()) {
            return entries;
        }
        if (rawTenses.isArray()) {
            for (JsonNode entry : rawTenses) {
                entries.add(entry);
            }
            return entries;
        }
        if (rawTenses.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = rawTenses.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                ObjectNode entry = GenMorphoUtils.JSON_MAPPER.createObjectNode();
                entry.put("tense", field.getKey());
                JsonNode value = field.getValue();
                if (value != null && value.isObject()) {
                    JsonNode formsNode = value.get("forms");
                    if (formsNode != null && !formsNode.isNull()) {
                        entry.set("forms", formsNode);
                    }
                    else {
                        entry.set("forms", value);
                    }
                    JsonNode exampleNode = value.get("example");
                    if (exampleNode != null && !exampleNode.isNull()) {
                        entry.set("example", exampleNode);
                    }
                    JsonNode notesNode = value.get("notes");
                    if (notesNode != null && !notesNode.isNull()) {
                        entry.set("notes", notesNode);
                    }
                }
                else if (value != null) {
                    entry.set("forms", value);
                }
                entries.add(entry);
            }
            return entries;
        }
        entries.add(rawTenses);
        return entries;
    }

    private static LinkedHashMap<String, ObjectNode> canonicalizeSparseTenseMap(JsonNode rawTenses) {

        LinkedHashMap<String, ObjectNode> tenseMap = new LinkedHashMap<>();
        for (JsonNode rawEntry : flattenRawTenseEntries(rawTenses)) {
            ObjectNode normalizedEntry = canonicalizeSparseTenseEntry(rawEntry);
            if (normalizedEntry == null) {
                continue;
            }
            String tenseName = normalizedEntry.path("tense").asText("");
            ObjectNode existing = tenseMap.get(tenseName);
            if (existing == null) {
                tenseMap.put(tenseName, normalizedEntry);
            } else {
                mergeTenseEntries(existing, normalizedEntry);
            }
        }
        return tenseMap;
    }

    private static ArrayNode buildOrderedTenses(Map<String, ObjectNode> tenseMap, boolean requireCompleteInventory) {

        ArrayNode ordered = GenMorphoUtils.JSON_MAPPER.createArrayNode();
        for (String tenseName : CANONICAL_TENSES) {
            ObjectNode entry = tenseMap.get(tenseName);
            if (entry == null) {
                if (requireCompleteInventory) {
                    return null;
                }
                continue;
            }
            ordered.add(entry);
        }
        return ordered;
    }

    private static ObjectNode canonicalizeSparseTenseEntry(JsonNode rawEntry) {

        if (rawEntry == null || rawEntry.isNull()) {
            return null;
        }
        String rawTenseName = rawEntry.path("tense").asText(rawEntry.isTextual() ? rawEntry.asText("") : "");
        String tenseName = normalizeTenseName(rawTenseName);
        if (!CANONICAL_TENSE_SET.contains(tenseName)) {
            return null;
        }

        JsonNode formsNode = rawEntry.get("forms");
        if ((formsNode == null || formsNode.isNull()) && rawEntry.isObject()) {
            ObjectNode derivedForms = GenMorphoUtils.JSON_MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = rawEntry.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                if ("tense".equalsIgnoreCase(fieldName) ||
                        "forms".equalsIgnoreCase(fieldName) ||
                        "example".equalsIgnoreCase(fieldName) ||
                        "notes".equalsIgnoreCase(fieldName)) {
                    continue;
                }
                String canonicalPronoun = normalizePronounKey(fieldName);
                if (canonicalPronoun != null) {
                    derivedForms.set(canonicalPronoun, field.getValue());
                }
            }
            if (derivedForms.size() > 0) {
                formsNode = derivedForms;
            }
        }

        ObjectNode normalizedForms = normalizeConjugationForms(formsNode);
        ObjectNode sparseForms = sparseNonEmptyForms(normalizedForms);
        if (countFilledPronounSlots(sparseForms) <= 0) {
            return null;
        }

        ObjectNode normalizedEntry = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        normalizedEntry.put("tense", tenseName);
        normalizedEntry.set("forms", sparseForms);
        if (rawEntry.isObject()) {
            String example = rawEntry.path("example").asText("").trim();
            if (!example.isEmpty()) {
                normalizedEntry.put("example", example);
            }
            String notes = rawEntry.path("notes").asText("").trim();
            if (!notes.isEmpty()) {
                normalizedEntry.put("notes", notes);
            }
        }
        return normalizedEntry;
    }

    private static ObjectNode sparseNonEmptyForms(ObjectNode forms) {

        ObjectNode sparse = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        if (forms == null) {
            return sparse;
        }
        for (String pronoun : PERSON_PRONOUN_KEYS) {
            String value = forms.path(pronoun).asText("").trim();
            if (!value.isEmpty()) {
                sparse.put(pronoun, value);
            }
        }
        String summary = forms.path("summary").asText("").trim();
        if (!summary.isEmpty()) {
            sparse.put("summary", summary);
        }
        return sparse;
    }

    private static int countFilledSlots(Map<String, ObjectNode> tenseMap) {

        int count = 0;
        if (tenseMap == null) {
            return 0;
        }
        for (ObjectNode entry : tenseMap.values()) {
            count += countFilledPronounSlots(entry == null ? null : (ObjectNode) entry.get("forms"));
        }
        return count;
    }

    private static int countFilledPronounSlots(ObjectNode forms) {

        if (forms == null) {
            return 0;
        }
        int count = 0;
        for (String pronoun : PERSON_PRONOUN_KEYS) {
            if (!forms.path(pronoun).asText("").trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void mergeTenseEntries(ObjectNode target, ObjectNode update) {

        if (target == null || update == null) {
            return;
        }
        ObjectNode targetForms = target.with("forms");
        JsonNode updateFormsNode = update.get("forms");
        if (updateFormsNode != null && updateFormsNode.isObject()) {
            ObjectNode updateForms = (ObjectNode) updateFormsNode;
            for (String pronoun : PERSON_PRONOUN_KEYS) {
                if (targetForms.path(pronoun).asText("").trim().isEmpty()) {
                    String value = updateForms.path(pronoun).asText("").trim();
                    if (!value.isEmpty()) {
                        targetForms.put(pronoun, value);
                    }
                }
            }
            if (targetForms.path("summary").asText("").trim().isEmpty()) {
                String summary = updateForms.path("summary").asText("").trim();
                if (!summary.isEmpty()) {
                    targetForms.put("summary", summary);
                }
            }
        }
        if (target.path("example").asText("").trim().isEmpty()) {
            String example = update.path("example").asText("").trim();
            if (!example.isEmpty()) {
                target.put("example", example);
            }
        }
        if (target.path("notes").asText("").trim().isEmpty()) {
            String notes = update.path("notes").asText("").trim();
            if (!notes.isEmpty()) {
                target.put("notes", notes);
            }
        }
    }

    private static boolean hasCompletePronounForms(ObjectNode forms) {

        if (forms == null) {
            return false;
        }
        for (String pronoun : PERSON_PRONOUN_KEYS) {
            if (forms.path(pronoun).asText("").trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String inferVerbRegularity(String lemma, String verb, ArrayNode tenses) {

        if (lemma == null || lemma.trim().isEmpty() || tenses == null || tenses.isEmpty()) {
            return "Unknown";
        }
        JSONObject record = new JSONObject();
        record.put("lemma", lemma);
        record.put("verb", verb == null ? "" : verb);
        record.put("tenses", new JSONArray(tenses.toString()));
        Map<String, String> normalizedForms = GenerativeEvalUtils.extractNormalizedConjugationForms(record);
        if (normalizedForms == null) {
            return "Unknown";
        }
        return VerbRegularityUtils.classifyRegularity(lemma, normalizedForms);
    }

    private static String firstNonEmpty(String... values) {

        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return "";
    }
}
