package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/***************************************************************
 *  Adjective-related morphological generation.
 ***************************************************************/
public class GenAdjectiveMorphoDB {

    private final Map<String, Set<String>> adjectiveSynsetHash;
    private final Map<String, String> adjectiveDocumentationHash;

    public GenAdjectiveMorphoDB(Map<String, Set<String>> adjectiveSynsetHash,
                                Map<String, String> adjectiveDocumentationHash) {
        this.adjectiveSynsetHash = adjectiveSynsetHash;
        this.adjectiveDocumentationHash = adjectiveDocumentationHash;
    }

    /***************************************************************
     * Adjective generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        switch (genFunction) {
            case "-c":
                genAdjectiveClasses();
                break;
            default:
                System.out.println("Adjective morphological generation not yet implemented for function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify adjectives by function.
     ***************************************************************/
    private void genAdjectiveClasses() {

        String adjectiveFileName = "AdjectiveSemanticClasses_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : adjectiveSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = adjectiveDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" :
                        "Definition: \"" + adjectiveDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English adjective classes. " +
                        "Assign the adjective to exactly one category from the list below.\n\n" +
                        "Categories:\n" +
                        "1. Descriptive / Qualitative - inherent qualities or states (e.g., red, tall, metallic, tired)\n" +
                        "2. Evaluative - judgment, value, or attitude, including affective participial forms (e.g., good, boring, frightening)\n" +
                        "3. Quantitative / Indefinite - vague amount or extent (e.g., some, many, enough)\n" +
                        "4. Numeral - exact number or order (e.g., one, third, tenth)\n" +
                        "5. Demonstrative (Deictic) - point to specific entities (this, that, these, those)\n" +
                        "6. Possessive - ownership or association (my, your, her, John's)\n" +
                        "7. Interrogative - used in questions (which, what, whose)\n" +
                        "8. Distributive - refer to members individually (each, every, either, neither)\n" +
                        "9. Proper / Nominal - derived from proper nouns (French, Shakespearean, Victorian)\n" +
                        "10. Other - residual adjectives not captured above (rare classifiers, syntactic particles, etc.)\n\n" +
                        "Adjective: \"" + term + "\".\n" +
                        definitionStatement + "\n" +
                        "Instructions:\n" +
                        " - Consider the adjective's default dictionary sense.\n" +
                        " - If it has multiple functions, choose the most canonical or cite the primary role in standard usage.\n" +
                        " - Output valid JSON only, with fields adjective, category, explanation, usage.\n" +
                        " - \"category\" must match one of the ten category names above exactly.\n" +
                        " - Provide a short explanation mentioning the key semantic cue.\n" +
                        " - Provide a single usage sentence illustrating the classification.\n\n" +
                        "JSON schema:\n" +
                        "{\n" +
                        "  \"adjective\": \"<adjective>\",\n" +
                        "  \"category\": \"<one of the ten categories>\",\n" +
                        "  \"explanation\": \"<short rationale>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenAdjectiveMorphoDB.genAdjectiveSemanticClasses() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] adjectiveArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("adjective", "category", "explanation", "usage"));
                    if (adjectiveArray != null) {
                        errorInResponse = false;
                        adjectiveArray[1] = normalizeAdjectiveCategory(adjectiveArray[1]);
                        adjectiveArray[2] = "\"" + adjectiveArray[2] + "\"";
                        adjectiveArray[3] = "\"" + adjectiveArray[3] + "\"";
                        adjectiveArray = GenUtils.appendToStringArray(adjectiveArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(adjectiveFileName, Arrays.toString(adjectiveArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(adjectiveFileName,
                            "ERROR! adjective: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenAdjectiveMorphoDB.genAdjectiveSemanticClasses().LLMResponse: " +
                            llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static String normalizeAdjectiveCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("descriptive") || spacesNormalized.contains("qualitative") ||
                spacesNormalized.contains("quality") || spacesNormalized.contains("stative")) {
            return "Descriptive / Qualitative";
        }
        if (spacesNormalized.contains("evaluative") || spacesNormalized.contains("value") ||
                spacesNormalized.contains("judg") || spacesNormalized.contains("affective")) {
            return "Evaluative";
        }
        if (spacesNormalized.contains("quant") || spacesNormalized.contains("indefinite") ||
                spacesNormalized.contains("amount") || spacesNormalized.contains("degree")) {
            return "Quantitative / Indefinite";
        }
        if (spacesNormalized.contains("numeral") || spacesNormalized.contains("number") ||
                spacesNormalized.contains("ordinal") || spacesNormalized.contains("cardinal")) {
            return "Numeral";
        }
        if (spacesNormalized.contains("demonstrative") || spacesNormalized.contains("deictic") ||
                spacesNormalized.contains("pointing") || spacesNormalized.contains("this") || spacesNormalized.contains("that")) {
            return "Demonstrative (Deictic)";
        }
        if (spacesNormalized.contains("possessive") || spacesNormalized.contains("ownership") ||
                spacesNormalized.contains("genitive")) {
            return "Possessive";
        }
        if (spacesNormalized.contains("interrogative") || spacesNormalized.contains("question")) {
            return "Interrogative";
        }
        if (spacesNormalized.contains("distributive") || spacesNormalized.contains("each") ||
                spacesNormalized.contains("every") || spacesNormalized.contains("either") || spacesNormalized.contains("neither")) {
            return "Distributive";
        }
        if (spacesNormalized.contains("proper") || spacesNormalized.contains("nominal") ||
                spacesNormalized.contains("origin") || spacesNormalized.contains("demonym") || spacesNormalized.contains("derived from")) {
            return "Proper / Nominal";
        }
        if (spacesNormalized.contains("other") || spacesNormalized.contains("misc") ||
                spacesNormalized.contains("catch all") || spacesNormalized.contains("catch-all")) {
            return "Other";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(rawCategory.trim());
    }
}
