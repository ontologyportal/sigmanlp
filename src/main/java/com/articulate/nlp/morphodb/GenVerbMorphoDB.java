package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Verb-related morphological generation.
 * 
 * •	Transitivity
*    o	Transitive/Instransitive/Ditransitive – takes a direct object
*    o	Impersonal (no subject, like “to rain”)
*    o	Reflexive (subject and object can be the same, i.e. “He hurt himself”)
*    o	Reciprocal (Two subjects act on each other i.e. (They hugged each other)
*  •	Verb conjugations
*    o	Regular/irregular verbs
* 
 ***************************************************************/
public class GenVerbMorphoDB {

    private final Map<String, Set<String>> verbSynsetHash;
    private final Map<String, String> verbDocumentationHash;

    public GenVerbMorphoDB(Map<String, Set<String>> verbSynsetHash,
                           Map<String, String> verbDocumentationHash) {
        this.verbSynsetHash = verbSynsetHash;
        this.verbDocumentationHash = verbDocumentationHash;
    }

    /***************************************************************
     * Verb generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        switch (genFunction) {
            case "-v":
                genVerbValence();
                break;
            case "-c":
                genVerbCausativity();
                break;
            default:
                System.out.println("Unsupported verb generation function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs across valency categories.
     ***************************************************************/
    private void genVerbValence() {

        String valenceFileName = "VerbValence_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English verb valency and argument structure. " +
                        "Classify the verb into the most appropriate valency category from the hierarchy below. \n\n" +
                        "VERB\n" +
                        "├── 0-Valent (Impersonal)\n" +
                        "│     ├── Pure Impersonal → \"It rains\", \"It snowed\"\n" +
                        "│     └── Quasi-Impersonal → \"It takes time\", \"It seems that...\"\n" +
                        "│\n" +
                        "├── 1-Valent (Intransitive)\n" +
                        "│     ├── Simple Intransitive → \"He sleeps\", \"The baby cried\"\n" +
                        "│     └── Prepositional Intransitive → \"She talked about politics\"\n" +
                        "│\n" +
                        "├── 2-Valent\n" +
                        "│     ├── Transitive → \"She built a house\", \"He reads books\"\n" +
                        "│     ├── Complex Transitive → \"They elected her president\", \"I found him annoying\"\n" +
                        "│     └── Copular (Linking) → \"She is happy\", \"He became a teacher\"\n" +
                        "│\n" +
                        "└── 3-Valent (Ditransitive)\n" +
                        "      ├── Double-Object → \"She gave him a book\"\n" +
                        "      └── Prepositional Ditransitive → \"She gave a book to him\"\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, valence, subtype, semantic_roles, explanation, usage.\n" +
                        " - valence must be one of: \"0-valent\", \"1-valent\", \"2-valent\", \"3-valent\".\n" +
                        " - subtype must be selected from the hierarchy (pure impersonal, quasi-impersonal, simple intransitive, prepositional intransitive, transitive, complex transitive, copular, double-object, prepositional ditransitive).\n" +
                        " - semantic_roles should summarize the key semantic roles (Agent, Experiencer, Patient/Theme, Recipient/Beneficiary, Instrument, Location, Source/Goal, Cause, Time, Stimulus, etc.). Use comma-separated values.\n" +
                        " - Provide a short explanation referencing typical complements.\n" +
                        " - Give a concise usage example sentence illustrating the classification.\n\n" +
                        "Reference examples:\n" +
                        "\"It rains.\" → 0-valent, pure impersonal, semantic_roles: natural process\n" +
                        "\"Mary opened the door.\" → 2-valent, transitive, semantic_roles: agent, patient\n" +
                        "\"Mary gave John a book.\" → 3-valent, double-object, semantic_roles: agent, recipient, theme\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"valence\": \"<0-valent|1-valent|2-valent|3-valent>\",\n" +
                        "  \"subtype\": \"<specific subtype>\",\n" +
                        "  \"semantic_roles\": \"<comma-separated roles>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbValence() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] valenceArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "valence", "subtype", "semantic_roles", "explanation", "usage"));
                    if (valenceArray != null) {
                        errorInResponse = false;
                        valenceArray[1] = normalizeValenceCategory(valenceArray[1]);
                        valenceArray[2] = normalizeSubtype(valenceArray[2]);
                        valenceArray[3] = "\"" + normalizeSemanticRoles(valenceArray[3]) + "\"";
                        valenceArray[4] = "\"" + valenceArray[4] + "\"";
                        valenceArray[5] = "\"" + valenceArray[5] + "\"";
                        valenceArray = GenUtils.appendToStringArray(valenceArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(valenceFileName, Arrays.toString(valenceArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(valenceFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbValence().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs as causative or non-causative.
     ***************************************************************/
    private void genVerbCausativity() {

        String causativityFileName = "VerbCausativity_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert in lexical semantics focusing on causativity. " +
                        "Determine whether the verb denotes a causative action (the subject causes a change in a patient), " +
                        "is typically non-causative, or has both causative and non-causative senses. \n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, causativity, explanation, usage.\n" +
                        " - causativity must be one of: \"causative\", \"non-causative\", or \"mixed\" (for verbs with both readings).\n" +
                        " - Provide a concise explanation citing typical syntactic or semantic patterns.\n" +
                        " - Supply an illustrative usage sentence that matches the classification.\n\n" +
                        "Example classifications:\n" +
                        "\"She broke the vase.\" → causative (agent causes change to patient)\n" +
                        "\"The vase broke.\" → non-causative (intransitive change of state)\n" +
                        "\"The door opened.\" / \"She opened the door.\" → mixed (verb alternates between causative and non-causative)\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"causativity\": \"<causative|non-causative|mixed>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbCausativity() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] causativityArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "causativity", "explanation", "usage"));
                    if (causativityArray != null) {
                        errorInResponse = false;
                        causativityArray[1] = normalizeCausativityCategory(causativityArray[1]);
                        causativityArray[2] = "\"" + causativityArray[2] + "\"";
                        causativityArray[3] = "\"" + causativityArray[3] + "\"";
                        causativityArray = GenUtils.appendToStringArray(causativityArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(causativityFileName, Arrays.toString(causativityArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(causativityFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbCausativity().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static String normalizeValenceCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.startsWith("0") || lower.contains("0-valent") || lower.contains("impersonal")) {
            return "0-valent";
        }
        if (lower.startsWith("1") || lower.contains("1-valent") || (lower.contains("intrans") && !lower.contains("ditrans"))) {
            return "1-valent";
        }
        if (lower.startsWith("3") || lower.contains("3-valent") || lower.contains("ditrans")) {
            return "3-valent";
        }
        if (lower.startsWith("2") || lower.contains("2-valent") ||
                lower.contains("trans") || lower.contains("copul") || lower.contains("complex")) {
            return "2-valent";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizeSubtype(String rawSubtype) {

        if (rawSubtype == null || rawSubtype.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawSubtype.trim().toLowerCase();
        if (lower.contains("pure")) {
            return "Pure impersonal";
        }
        if (lower.contains("quasi")) {
            return "Quasi-impersonal";
        }
        if (lower.contains("prepositional") && lower.contains("intrans")) {
            return "Prepositional intransitive";
        }
        if (lower.contains("simple") && lower.contains("intrans")) {
            return "Simple intransitive";
        }
        if (lower.contains("complex") && lower.contains("trans")) {
            return "Complex transitive";
        }
        if (lower.contains("copul")) {
            return "Copular";
        }
        if (lower.contains("double")) {
            return "Double-object";
        }
        if (lower.contains("prepositional") && lower.contains("di")) {
            return "Prepositional ditransitive";
        }
        if (lower.contains("trans")) {
            return "Transitive";
        }
        if (lower.contains("intrans")) {
            return "Simple intransitive";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizeSemanticRoles(String rawRoles) {

        if (rawRoles == null) {
            return "";
        }
        String cleaned = rawRoles.replaceAll("[\\[\\]]", "")
                .replaceAll("\\s*;\\s*", ",")
                .replaceAll("\\s*\\+\\s*", ",")
                .replaceAll("\\s+/\\s*", "/")
                .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] parts = cleaned.split("\\s*,\\s*");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            String role = part.trim();
            if (role.isEmpty()) {
                continue;
            }
            role = role.replaceAll("\\s+", " ");
            String formatted = GenUtils.capitalizeFirstLetter(role);
            normalized.add(formatted);
        }
        return String.join(", ", normalized);
    }

    private static String normalizeCausativityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.contains("mixed") || lower.contains("both") || lower.contains("alternat")) {
            return "Mixed";
        }
        if (lower.contains("non") || lower.contains("intrans") || lower.contains("stative")) {
            return "Non-causative";
        }
        if (lower.contains("caus")) {
            return "Causative";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }
}
