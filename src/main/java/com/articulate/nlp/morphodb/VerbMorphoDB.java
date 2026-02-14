package com.articulate.nlp.morphodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/***************************************************************
 * Read-only verb morphology database loaded from persisted files.
 ***************************************************************/
public class VerbMorphoDB {

    public final Map<String, List<ObjectNode>> valence;
    public final Map<String, List<ObjectNode>> causativity;
    public final Map<String, List<ObjectNode>> reflexive;
    public final Map<String, List<ObjectNode>> reciprocal;
    public final Map<String, List<ObjectNode>> achievementProcess;
    public final Map<String, List<ObjectNode>> conjugations;

    private final Map<String, Map<String, Map<String, String>>> conjugationIndex;

    public VerbMorphoDB(Map<String, List<ObjectNode>> valence,
                        Map<String, List<ObjectNode>> causativity,
                        Map<String, List<ObjectNode>> reflexive,
                        Map<String, List<ObjectNode>> reciprocal,
                        Map<String, List<ObjectNode>> achievementProcess,
                        Map<String, List<ObjectNode>> conjugations) {
        this.valence = valence;
        this.causativity = causativity;
        this.reflexive = reflexive;
        this.reciprocal = reciprocal;
        this.achievementProcess = achievementProcess;
        this.conjugations = conjugations;
        this.conjugationIndex = buildConjugationIndex(conjugations);
    }

    public static VerbMorphoDB load(String morphoDbPath) {

        return new VerbMorphoDB(
                loadVerbValence(morphoDbPath),
                loadVerbCausativity(morphoDbPath),
                loadVerbReflexive(morphoDbPath),
                loadVerbReciprocal(morphoDbPath),
                loadVerbAchievementProcess(morphoDbPath),
                loadVerbConjugations(morphoDbPath));
    }

    private static Map<String, List<ObjectNode>> loadVerbValence(String morphoDbPath) {

        return loadVerbClassifications("VerbValence.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbCausativity(String morphoDbPath) {

        return loadVerbClassifications("VerbCausativity.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbReflexive(String morphoDbPath) {

        return loadVerbClassifications("VerbReflexive.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbReciprocal(String morphoDbPath) {

        return loadVerbClassifications("VerbReciprocal.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbAchievementProcess(String morphoDbPath) {

        return loadVerbClassifications("VerbAchievementProcess.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbConjugations(String morphoDbPath) {

        return loadVerbClassifications("VerbConjugations.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadVerbClassifications(String fileName, String morphoDbPath) {

        Path root = GenMorphoUtils.expandHomePath(morphoDbPath);
        return GenMorphoUtils.loadClassificationObjects(root.resolve("verb").resolve(fileName).toString());
    }

    /***************************************************************
     * Lookup by lemma + tense + grammatical person.
     ***************************************************************/
    public String getVerbConjugation(String lemma, String tense, String grammaticalPerson) {

        String lemmaKey = normalizeLemma(lemma);
        String tenseKey = normalizeKey(tense);
        String personKey = normalizePersonKey(grammaticalPerson);
        if (lemmaKey.isEmpty() || tenseKey.isEmpty() || personKey.isEmpty()) {
            return null;
        }
        Map<String, Map<String, String>> byTense = conjugationIndex.get(lemmaKey);
        if (byTense == null) {
            return null;
        }
        Map<String, String> byPerson = byTense.get(tenseKey);
        if (byPerson == null) {
            return null;
        }
        String value = byPerson.get(personKey);
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    private static Map<String, Map<String, Map<String, String>>> buildConjugationIndex(
            Map<String, List<ObjectNode>> verbConjugations) {

        Map<String, Map<String, Map<String, String>>> index = new HashMap<>();
        if (verbConjugations == null || verbConjugations.isEmpty()) {
            return index;
        }
        for (List<ObjectNode> records : verbConjugations.values()) {
            if (records == null) {
                continue;
            }
            for (ObjectNode record : records) {
                if (record == null) {
                    continue;
                }
                String lemma = normalizeLemma(record.path("verb").asText(""));
                if (lemma.isEmpty()) {
                    continue;
                }
                JsonNode tensesNode = record.get("tenses");
                if (tensesNode == null || !tensesNode.isArray()) {
                    continue;
                }
                for (JsonNode tenseNode : tensesNode) {
                    if (tenseNode == null || !tenseNode.isObject()) {
                        continue;
                    }
                    String tenseKey = normalizeKey(tenseNode.path("tense").asText(""));
                    if (tenseKey.isEmpty()) {
                        continue;
                    }
                    JsonNode forms = tenseNode.get("forms");
                    if (forms == null || !forms.isObject()) {
                        continue;
                    }
                    Map<String, String> personMap = index
                            .computeIfAbsent(lemma, k -> new HashMap<>())
                            .computeIfAbsent(tenseKey, k -> new HashMap<>());
                    for (String person : Arrays.asList("i", "you_singular", "he_she_it", "we", "you_plural", "they")) {
                        String normalizedPerson = normalizePersonKey(person);
                        String rawForm = forms.path(person).asText("");
                        if ((rawForm == null || rawForm.trim().isEmpty()) && forms.has("summary")) {
                            rawForm = forms.path("summary").asText("");
                        }
                        String value = extractSurfaceForm(rawForm, normalizedPerson);
                        if (value == null || value.isEmpty()) {
                            continue;
                        }
                        personMap.putIfAbsent(normalizedPerson, value);
                    }
                }
            }
        }
        return index;
    }

    private static String normalizeLemma(String lemma) {

        if (lemma == null) {
            return "";
        }
        String normalized = lemma.trim().toLowerCase();
        if (normalized.startsWith("to ")) {
            normalized = normalized.substring(3).trim();
        }
        return normalized;
    }

    private static String normalizeKey(String value) {

        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private static String normalizePersonKey(String grammaticalPerson) {

        if (grammaticalPerson == null) {
            return "";
        }
        String key = grammaticalPerson.trim().toLowerCase();
        if ("you".equals(key)) {
            return "you_singular";
        }
        if ("3sg".equals(key) || "third_person_singular".equals(key) || "third person singular".equals(key)) {
            return "he_she_it";
        }
        return key;
    }

    private static String extractSurfaceForm(String rawForm, String personKey) {

        if (rawForm == null) {
            return null;
        }
        String value = rawForm.trim();
        if (value.isEmpty()) {
            return null;
        }
        String lowered = value.toLowerCase();
        if ("i".equals(personKey) && lowered.startsWith("i ")) {
            value = value.substring(2).trim();
        }
        else if ("you_singular".equals(personKey) || "you_plural".equals(personKey)) {
            if (lowered.startsWith("you ")) {
                value = value.substring(4).trim();
            }
        }
        else if ("we".equals(personKey) && lowered.startsWith("we ")) {
            value = value.substring(3).trim();
        }
        else if ("they".equals(personKey) && lowered.startsWith("they ")) {
            value = value.substring(5).trim();
        }
        else if ("he_she_it".equals(personKey)) {
            String candidate = value;
            String candidateLower = lowered;
            for (String prefix : Arrays.asList("he/she/it ", "he/she ", "he or she ", "he ", "she ", "it ")) {
                if (candidateLower.startsWith(prefix)) {
                    candidate = candidate.substring(prefix.length()).trim();
                    candidateLower = candidate.toLowerCase();
                    break;
                }
            }
            value = candidate;
        }
        value = value.replaceAll("\\s+", " ").trim();
        return value.isEmpty() ? null : value;
    }
}
