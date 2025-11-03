package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.util.Arrays;
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
            case "-t":
                genVerbTransitivity();
                break;
            default:
                System.out.println("Unsupported verb generation function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs as transitive, intransitive, or ditransitive.
     ***************************************************************/
    private void genVerbTransitivity() {

        String transitivityFileName = "VerbTransitivity_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English verb argument structure. " +
                        "Classify the verb according to whether it is typically used as transitive, intransitive, or ditransitive. \n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return one of the following categories: \"transitive\", \"intransitive\", or \"ditransitive\".\n" +
                        " - Consider the core argument structure in contemporary usage.\n" +
                        " - Provide a short explanation referencing typical complements.\n" +
                        " - Give a concise usage example sentence illustrating the classification.\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"transitivity\": \"<transitive|intransitive|ditransitive>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbTransitivity() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] transitivityArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "transitivity", "explanation", "usage"));
                    if (transitivityArray != null) {
                        errorInResponse = false;
                        transitivityArray[1] = normalizeTransitivityCategory(transitivityArray[1]);
                        transitivityArray[2] = "\"" + transitivityArray[2] + "\"";
                        transitivityArray[3] = "\"" + transitivityArray[3] + "\"";
                        transitivityArray = GenUtils.appendToStringArray(transitivityArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(transitivityFileName, Arrays.toString(transitivityArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(transitivityFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbTransitivity().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static String normalizeTransitivityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.contains("di")) {
            return "Ditransitive";
        }
        if (lower.contains("intrans")) {
            return "Intransitive";
        }
        if (lower.contains("trans")) {
            return "Transitive";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }
}
