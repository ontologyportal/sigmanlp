package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utilities for generative audit building and evaluation.
 */
public final class GenerativeEvalUtils {

    public static final String PLURAL_AUDIT_MODE = "plural_audit";
    public static final String CONJUGATION_AUDIT_MODE = "conjugation_audit";

    public static final String STRATUM_SURFACE_PLURAL = "surface_plural";
    public static final String STRATUM_NO_PLURAL = "no_plural";

    public static final String SLOT_INFINITIVE = "infinitive";
    public static final String SLOT_PRESENT_3SG = "present_3sg";
    public static final String SLOT_SIMPLE_PAST = "simple_past";
    public static final String SLOT_PAST_PARTICIPLE = "past_participle";
    public static final String SLOT_GERUND = "gerund";
    public static final List<String> CONJUGATION_SLOTS = Collections.unmodifiableList(
            Arrays.asList(SLOT_INFINITIVE, SLOT_PRESENT_3SG, SLOT_SIMPLE_PAST, SLOT_PAST_PARTICIPLE, SLOT_GERUND));

    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s\\p{Punct}&&[^'/-]]+|[\\s\\p{Punct}&&[^'/-]]+$");
    private static final Pattern TOKEN_EDGE_PUNCTUATION = Pattern.compile("^[^a-z'/-]+|[^a-z'/-]+$");
    private static final Set<String> SUBJECT_OR_DETERMINER_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "i", "you", "we", "they", "he", "she", "it", "he/she/it", "he-she-it",
            "the", "a", "an", "his", "her", "its", "our", "my", "your", "their",
            "this", "that", "these", "those", "let", "lets", "let's"
    ));
    private static final Set<String> POSSESSIVE_PRONOUN_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "my", "your", "his", "her", "its", "our", "their", "one's"
    ));
    private static final Set<String> REFLEXIVE_PRONOUN_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "myself", "yourself", "himself", "herself", "itself", "ourselves", "yourselves", "themselves", "oneself"
    ));
    private static final Set<String> AUXILIARY_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "am", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "will", "shall", "do", "does", "did",
            "can", "could", "would", "should", "may", "might", "must"
    ));

    private GenerativeEvalUtils() {
    }

    public static class PluralRecord {
        public final String auditId;
        public final String synsetId;
        public final String lemma;
        public final String singularRaw;
        public final String pluralRaw;
        public final String singularNormalized;
        public final String pluralNormalized;
        public final String sampleStratum;

        PluralRecord(String auditId,
                     String synsetId,
                     String lemma,
                     String singularRaw,
                     String pluralRaw,
                     String singularNormalized,
                     String pluralNormalized,
                     String sampleStratum) {
            this.auditId = auditId;
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.singularRaw = singularRaw;
            this.pluralRaw = pluralRaw;
            this.singularNormalized = singularNormalized;
            this.pluralNormalized = pluralNormalized;
            this.sampleStratum = sampleStratum;
        }
    }

    public static class ConjugationRecord {
        public final String auditId;
        public final String synsetId;
        public final String lemma;
        public final String regularity;
        public final Map<String, String> rawForms;
        public final Map<String, String> normalizedForms;
        public final String sampleStratum;

        ConjugationRecord(String auditId,
                          String synsetId,
                          String lemma,
                          String regularity,
                          Map<String, String> rawForms,
                          Map<String, String> normalizedForms,
                          String sampleStratum) {
            this.auditId = auditId;
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.regularity = regularity;
            this.rawForms = rawForms;
            this.normalizedForms = normalizedForms;
            this.sampleStratum = sampleStratum;
        }
    }

    private static final class PhraseMatch {
        final String phrase;
        final int exactMatches;
        final int phraseLength;
        final int startIndex;

        PhraseMatch(String phrase, int exactMatches, int phraseLength, int startIndex) {
            this.phrase = phrase;
            this.exactMatches = exactMatches;
            this.phraseLength = phraseLength;
            this.startIndex = startIndex;
        }
    }

    public static String buildAuditId(String synsetId, String lemma) {
        return synsetId + "|" + GenMorphoUtils.normalizeLemma(lemma);
    }

    public static String normalizePluralForm(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("none".equals(normalized)) {
            return "none";
        }
        normalized = stripEdgePunctuation(normalized);
        return normalized.trim();
    }

    public static PluralRecord extractPluralRecord(JSONObject json) {
        String synsetId = json.optString("synsetId", "").trim();
        String lemma = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
        String singularRaw = json.optString("singular", json.optString("lemma", "")).trim();
        String pluralRaw = json.optString("plural", "").trim();

        if (synsetId.isEmpty() || lemma.isEmpty() || pluralRaw.isEmpty()) {
            return null;
        }

        String singularNormalized = normalizePluralForm(singularRaw.isEmpty() ? lemma : singularRaw);
        String pluralNormalized = normalizePluralForm(pluralRaw);
        if (pluralNormalized.isEmpty()) {
            return null;
        }

        return new PluralRecord(
                buildAuditId(synsetId, lemma),
                synsetId,
                lemma,
                singularRaw,
                pluralRaw,
                singularNormalized,
                pluralNormalized,
                determinePluralStratum(pluralNormalized)
        );
    }

    public static String determinePluralStratum(String pluralNormalized) {
        if ("none".equals(pluralNormalized)) {
            return STRATUM_NO_PLURAL;
        }
        return STRATUM_SURFACE_PLURAL;
    }

    public static JSONObject toReferenceOutput(PluralRecord record) {
        JSONObject root = new JSONObject();
        JSONObject raw = new JSONObject();
        raw.put("singular", record.singularRaw);
        raw.put("plural", record.pluralRaw);
        JSONObject normalized = new JSONObject();
        normalized.put("singular", record.singularNormalized);
        normalized.put("plural", record.pluralNormalized);
        root.put("raw", raw);
        root.put("normalized", normalized);
        return root;
    }

    public static boolean pluralMatchesReference(PluralRecord record, JSONObject referenceOutput) {
        JSONObject normalized = referenceOutput.optJSONObject("normalized");
        if (normalized == null) {
            return false;
        }
        String expected = normalizePluralForm(normalized.optString("plural", ""));
        return !expected.isEmpty() && expected.equals(record.pluralNormalized);
    }

    public static ConjugationRecord extractConjugationRecord(JSONObject json) {
        String synsetId = json.optString("synsetId", "").trim();
        String lemma = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
        String regularity = json.optString("regularity", "").trim();
        Map<String, String> normalizedForms = extractNormalizedConjugationForms(json);
        if (synsetId.isEmpty() || lemma.isEmpty() || regularity.isEmpty() || normalizedForms == null) {
            return null;
        }
        for (String slot : CONJUGATION_SLOTS) {
            if (!normalizedForms.containsKey(slot)) {
                return null;
            }
        }
        if (normalizedForms.size() < CONJUGATION_SLOTS.size()) {
            return null;
        }

        JSONArray tenses = json.optJSONArray("tenses");
        Map<String, JSONObject> tenseMap = indexTensesByName(tenses);
        Map<String, String> rawForms = new LinkedHashMap<>();
        rawForms.put(SLOT_INFINITIVE, extractInfinitiveRaw(json, tenseMap));
        rawForms.put(SLOT_PRESENT_3SG, extractTenseField(tenseMap, "Simple present", "he_she_it", null));
        rawForms.put(SLOT_SIMPLE_PAST, extractTenseField(tenseMap, "Simple past", "i", null));
        rawForms.put(SLOT_PAST_PARTICIPLE, extractTenseField(tenseMap, "Past participle", "summary", "i"));
        rawForms.put(SLOT_GERUND, extractTenseField(tenseMap, "Gerund / present participle", "summary", "i"));

        return new ConjugationRecord(
                buildAuditId(synsetId, lemma),
                synsetId,
                lemma,
                regularity,
                rawForms,
                normalizedForms,
                regularity
        );
    }

    public static Map<String, String> extractNormalizedConjugationForms(JSONObject json) {
        if (json == null) {
            return null;
        }
        String lemma = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
        if (lemma.isEmpty()) {
            return null;
        }

        JSONArray tenses = json.optJSONArray("tenses");
        Map<String, JSONObject> tenseMap = indexTensesByName(tenses);
        Map<String, String> rawForms = new LinkedHashMap<>();
        rawForms.put(SLOT_INFINITIVE, extractInfinitiveRaw(json, tenseMap));
        rawForms.put(SLOT_PRESENT_3SG, extractTenseField(tenseMap, "Simple present", "he_she_it", null));
        rawForms.put(SLOT_SIMPLE_PAST, extractTenseField(tenseMap, "Simple past", "i", null));
        rawForms.put(SLOT_PAST_PARTICIPLE, extractTenseField(tenseMap, "Past participle", "summary", "i"));
        rawForms.put(SLOT_GERUND, extractTenseField(tenseMap, "Gerund / present participle", "summary", "i"));

        Map<String, String> normalizedForms = new LinkedHashMap<>();
        for (String slot : CONJUGATION_SLOTS) {
            String normalized = normalizeVerbSurface(rawForms.get(slot), slot, lemma);
            if (!normalized.isEmpty()) {
                normalizedForms.put(slot, normalized);
            }
        }
        return normalizedForms;
    }

    public static JSONObject toReferenceOutput(ConjugationRecord record) {
        JSONObject root = new JSONObject();
        JSONObject raw = new JSONObject();
        for (Map.Entry<String, String> entry : record.rawForms.entrySet()) {
            raw.put(entry.getKey(), entry.getValue());
        }
        raw.put("regularity", record.regularity);

        JSONObject normalized = new JSONObject();
        for (Map.Entry<String, String> entry : record.normalizedForms.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue());
        }
        normalized.put("regularity", record.regularity.toLowerCase(Locale.ROOT));

        root.put("raw", raw);
        root.put("normalized", normalized);
        return root;
    }

    public static Map<String, Boolean> compareConjugationToReference(ConjugationRecord record, JSONObject referenceOutput) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        JSONObject normalized = referenceOutput.optJSONObject("normalized");
        if (normalized == null) {
            for (String slot : CONJUGATION_SLOTS) {
                result.put(slot, false);
            }
            result.put("regularity", false);
            result.put("all_parts_exact", false);
            return result;
        }

        boolean allPartsMatch = true;
        for (String slot : CONJUGATION_SLOTS) {
            String expected = normalized.optString(slot, "");
            boolean match = !expected.isEmpty() && expected.equals(record.normalizedForms.get(slot));
            result.put(slot, match);
            allPartsMatch &= match;
        }
        String expectedRegularity = normalized.optString("regularity", "");
        boolean regularityMatch = !expectedRegularity.isEmpty()
                && expectedRegularity.equals(record.regularity.toLowerCase(Locale.ROOT));
        result.put("regularity", regularityMatch);
        result.put("all_parts_exact", allPartsMatch);
        return result;
    }

    public static String normalizeVerbSurface(String raw, String slot) {
        return normalizeVerbSurface(raw, slot, "");
    }

    public static String normalizeVerbSurface(String raw, String slot, String lemma) {
        if (raw == null) {
            return "";
        }
        List<String> tokens = tokenize(raw.toLowerCase(Locale.ROOT));
        if (tokens.isEmpty()) {
            return "";
        }

        List<String> lemmaTokens = tokenize(GenMorphoUtils.normalizeLemma(lemma));
        if (lemmaTokens.size() > 1) {
            String phrase = extractLemmaPhrase(tokens, lemmaTokens);
            if (!phrase.isEmpty()) {
                return phrase;
            }
        }

        return normalizeHeadVerbSurface(tokens, slot);
    }

    private static String normalizeHeadVerbSurface(List<String> tokens, String slot) {
        if (SLOT_INFINITIVE.equals(slot)) {
            String infinitive = findTokenAfter(tokens, "to");
            return infinitive.isEmpty() ? firstLexicalToken(tokens) : infinitive;
        }
        if (SLOT_GERUND.equals(slot)) {
            String gerund = firstTokenMatching(tokens, token -> token.endsWith("ing"));
            if (!gerund.isEmpty()) {
                return gerund;
            }
        }
        if (SLOT_PAST_PARTICIPLE.equals(slot)) {
            String participle = tokenAfterAuxiliarySequence(tokens);
            if (!participle.isEmpty()) {
                return participle;
            }
        }

        List<String> withoutLead = stripLeadingSubjects(tokens);
        String afterAux = stripLeadingAuxiliaries(withoutLead);
        if (!afterAux.isEmpty()) {
            return afterAux;
        }

        String anyAux = tokenAfterAuxiliarySequence(tokens);
        if (!anyAux.isEmpty()) {
            return anyAux;
        }

        return firstLexicalToken(tokens);
    }

    private static String extractLemmaPhrase(List<String> tokens, List<String> lemmaTokens) {
        if (lemmaTokens.size() < 2 || tokens.isEmpty()) {
            return "";
        }
        PhraseMatch bestMatch = extractLemmaPhraseVariant(tokens, lemmaTokens);
        List<String> expandedLemmaTokens = expandHyphenatedLemmaTokens(lemmaTokens);
        if (expandedLemmaTokens.size() != lemmaTokens.size()) {
            bestMatch = betterPhraseMatch(bestMatch, extractLemmaPhraseVariant(tokens, expandedLemmaTokens));
        }
        return bestMatch == null ? "" : bestMatch.phrase;
    }

    private static PhraseMatch extractLemmaPhraseVariant(List<String> tokens, List<String> lemmaTokens) {
        int phraseLength = lemmaTokens.size();
        PhraseMatch bestMatch = null;
        for (int i = 0; i + phraseLength <= tokens.size(); i++) {
            if (isSkipToken(tokens.get(i))) {
                continue;
            }
            int exactMatches = 0;
            for (int j = 0; j < phraseLength; j++) {
                if (lemmaPhraseTokenMatches(lemmaTokens.get(j), tokens.get(i + j))) {
                    exactMatches++;
                }
            }
            if (exactMatches >= phraseLength - 1) {
                PhraseMatch candidate = new PhraseMatch(
                        String.join(" ", tokens.subList(i, i + phraseLength)),
                        exactMatches,
                        phraseLength,
                        i
                );
                bestMatch = betterPhraseMatch(bestMatch, candidate);
            }
        }
        return bestMatch;
    }

    private static List<String> expandHyphenatedLemmaTokens(List<String> lemmaTokens) {
        List<String> expanded = new ArrayList<>();
        for (String token : lemmaTokens) {
            if (token.contains("-")) {
                for (String part : token.split("-")) {
                    String normalized = part.trim();
                    if (!normalized.isEmpty()) {
                        expanded.add(normalized);
                    }
                }
            } else {
                expanded.add(token);
            }
        }
        return expanded;
    }

    private static PhraseMatch betterPhraseMatch(PhraseMatch left, PhraseMatch right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (right.exactMatches != left.exactMatches) {
            return right.exactMatches > left.exactMatches ? right : left;
        }
        if (right.phraseLength != left.phraseLength) {
            return right.phraseLength > left.phraseLength ? right : left;
        }
        return right.startIndex < left.startIndex ? right : left;
    }

    private static boolean lemmaPhraseTokenMatches(String lemmaToken, String surfaceToken) {
        if (lemmaToken.equals(surfaceToken)) {
            return true;
        }
        if ("one's".equals(lemmaToken)) {
            return isPlaceholderVariant(surfaceToken, POSSESSIVE_PRONOUN_TOKENS);
        }
        if ("oneself".equals(lemmaToken)) {
            return isPlaceholderVariant(surfaceToken, REFLEXIVE_PRONOUN_TOKENS);
        }
        return false;
    }

    private static boolean isPlaceholderVariant(String surfaceToken, Set<String> allowedTokens) {
        if (allowedTokens.contains(surfaceToken)) {
            return true;
        }
        if (!surfaceToken.contains("/")) {
            return false;
        }
        String[] parts = surfaceToken.split("/");
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            String normalized = part.trim();
            if (normalized.isEmpty() || !allowedTokens.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, JSONObject> indexTensesByName(JSONArray tenses) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        if (tenses == null) {
            return result;
        }
        for (int i = 0; i < tenses.length(); i++) {
            JSONObject tense = tenses.optJSONObject(i);
            if (tense == null) {
                continue;
            }
            String tenseName = tense.optString("tense", "").trim();
            if (!tenseName.isEmpty()) {
                result.put(tenseName, tense);
            }
        }
        return result;
    }

    private static String extractInfinitiveRaw(JSONObject json, Map<String, JSONObject> tenseMap) {
        String verb = json.optString("verb", "").trim();
        if (!verb.isEmpty()) {
            return verb;
        }
        return extractTenseField(tenseMap, "Infinitive", "i", null);
    }

    private static String extractTenseField(Map<String, JSONObject> tenseMap,
                                            String tenseName,
                                            String preferredField,
                                            String fallbackField) {
        JSONObject tense = tenseMap.get(tenseName);
        if (tense == null) {
            return "";
        }
        JSONObject forms = tense.optJSONObject("forms");
        if (forms == null) {
            return "";
        }

        String preferred = forms.optString(preferredField, "").trim();
        if (!preferred.isEmpty()) {
            return preferred;
        }
        if (fallbackField != null) {
            return forms.optString(fallbackField, "").trim();
        }
        return "";
    }

    private static List<String> tokenize(String value) {
        String cleaned = value.replace('(', ' ')
                .replace(')', ' ')
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('{', ' ')
                .replace('}', ' ')
                .replace('"', ' ')
                .replace('`', ' ');
        String[] pieces = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String piece : pieces) {
            String normalized = normalizeToken(piece);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private static String normalizeToken(String token) {
        String stripped = TOKEN_EDGE_PUNCTUATION.matcher(token).replaceAll("");
        return stripped.trim();
    }

    private static String stripEdgePunctuation(String value) {
        return EDGE_PUNCTUATION.matcher(value).replaceAll("");
    }

    private static String findTokenAfter(List<String> tokens, String marker) {
        for (int i = 0; i < tokens.size(); i++) {
            if (marker.equals(tokens.get(i))) {
                for (int j = i + 1; j < tokens.size(); j++) {
                    String candidate = tokens.get(j);
                    if (isSkipToken(candidate)) {
                        continue;
                    }
                    return candidate;
                }
            }
        }
        return "";
    }

    private static String firstTokenMatching(List<String> tokens, java.util.function.Predicate<String> predicate) {
        for (String token : tokens) {
            if (predicate.test(token)) {
                return token;
            }
        }
        return "";
    }

    private static List<String> stripLeadingSubjects(List<String> tokens) {
        List<String> result = new ArrayList<>(tokens);
        while (!result.isEmpty()) {
            String first = result.get(0);
            if ("to".equals(first) || SUBJECT_OR_DETERMINER_TOKENS.contains(first)) {
                result.remove(0);
            } else {
                break;
            }
        }
        return result;
    }

    private static String stripLeadingAuxiliaries(List<String> tokens) {
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            if ("to".equals(token) || AUXILIARY_TOKENS.contains(token)) {
                index++;
                continue;
            }
            if (!isSkipToken(token)) {
                return token;
            }
            index++;
        }
        return "";
    }

    private static String tokenAfterAuxiliarySequence(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!AUXILIARY_TOKENS.contains(tokens.get(i))) {
                continue;
            }
            int j = i;
            while (j < tokens.size() && AUXILIARY_TOKENS.contains(tokens.get(j))) {
                j++;
            }
            while (j < tokens.size()) {
                String candidate = tokens.get(j);
                if (!isSkipToken(candidate)) {
                    return candidate;
                }
                j++;
            }
        }
        return "";
    }

    private static String firstLexicalToken(List<String> tokens) {
        for (String token : tokens) {
            if (!isSkipToken(token)) {
                return token;
            }
        }
        return "";
    }

    private static boolean isSkipToken(String token) {
        return token.isEmpty()
                || "to".equals(token)
                || SUBJECT_OR_DETERMINER_TOKENS.contains(token)
                || AUXILIARY_TOKENS.contains(token);
    }
}
