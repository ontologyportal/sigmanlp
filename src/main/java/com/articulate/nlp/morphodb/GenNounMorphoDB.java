package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Generates noun-related morphological data.
 * 
 *    •	Indefinite article (a/an/none)
 *    •	Plural/non-plural form
 *      o	Plural only (scissors, trousers)/Singular only (furniture, information)
 *      o	Proper/common
 *    •	Countable/Mass
 *    •	Human/non-human nouns (persons name, or things like teacher/doctor/student)
 *    •	Animate/non-animate nouns (living things/non-living things)
 *    •	Collective nouns (i.e. team, family, flock, etc.)
****************************************************************/
public class GenNounMorphoDB {

    private final Map<String, Set<String>> nounSynsetHash;
    private final Map<String, String> nounDocumentationHash;

    public GenNounMorphoDB(Map<String, Set<String>> nounSynsetHash,
                           Map<String, String> nounDocumentationHash) {
        this.nounSynsetHash = nounSynsetHash;
        this.nounDocumentationHash = nounDocumentationHash;
    }

    /***************************************************************
     * Dispatch generation requests for nouns.
     ***************************************************************/
    public void generate(String genFunction) {

        switch (genFunction) {
            case "-i":
                genIndefiniteArticles();
                break;
            case "-c":
                genCountability();
                break;
            case "-p":
                genPlurals();
                break;
            case "-h":
                genHumanness();
                break;
            case "-l":
                genCollectiveNouns();
                break;
            default:
                System.out.println("Unsupported noun generation function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to determine the indefinite article of every noun
     * in WordNet.
     ***************************************************************/
    private void genIndefiniteArticles() {

        String indefFileName = "IndefiniteArticles_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert linguist specializing in English noun phrase syntax and article usage. " +
                        "Your task is to determine the correct indefinite article usage for the given noun or noun phrase. \n\n" +
                        "The noun to classify: \"" + term + "\". \n" +
                        definitionStatement + "\n\n" +
                        "Instructions: \n" +
                        "- Decide whether the correct indefinite article is \"a\", \"an\", or \"none\".\n" +
                        "- Base your decision on pronunciation and grammatical convention.\n" +
                        "- If the noun is a scientific or proper name that normally does not take an article, return \"none\".\n" +
                        "- If an article applies only in specific usage contexts, still provide the indefinite article.\n" +
                        "- Always provide:\n" +
                        "  1. The chosen article.\n" +
                        "  2. The noun exactly as given.\n" +
                        "  3. A clear explanation of the rationale.\n" +
                        "  4. An example sentence showing the article in use (or stating why no article is used).\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON.\n" +
                        " * All JSON strings must escape sequences for quotation marks if necessary (\" → \\\") and (' → \\')\n" +
                        " * Do not place the usage sentence itself inside quotation marks.\n" +
                        " * Do not include any commentary outside the JSON.\n\n" +
                        "Output strictly in this JSON format (all lowercase for the article):" +
                        "\n\n```json\n{\n  \"article\": \"<a|an|none>\",\n  \"noun\": \"<noun>\",\n  \"explanation\":\"<rationale for classification (escaped quotes if neccessary)>\",\n  \"usage\":\"<example sentence with article (escaped quotes if neccessary)>\" \n}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genIndefiniteArticles() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] indefArticleArray = GenUtils.extractJsonFields(jsonResponse, Arrays.asList("article", "noun", "explanation", "usage"));
                    if (indefArticleArray != null) {
                        errorInResponse = false;
                        String category;
                        indefArticleArray[2] = "\"" + indefArticleArray[2] + "\""; // explanation
                        indefArticleArray[3] = "\"" + indefArticleArray[3] + "\""; // usage
                        if (indefArticleArray[0].equals("none")) {
                            category = "NA";
                        }
                        else if (isIrregularIndefiniteArticle(indefArticleArray[0], indefArticleArray[1])) {
                            category = "Irregular";
                        }
                        else {
                            category = "Regular";
                        }

                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, category);
                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(indefFileName, Arrays.toString(indefArticleArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(indefFileName,
                            "ERROR! term: " + term + ". " + definitionStatement +
                                    " - LLM response: " + llmResponse.replace("\n", "") + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genIndefiniteArticles().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to determine whether nouns are collective.
     ***************************************************************/
    private void genCollectiveNouns() {

        String collectiveFileName = "CollectiveNouns_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English collective nouns. " +
                        "Determine whether the given noun typically refers to a collection of individuals or things acting as a single unit. " +
                        "If the noun can be collective in some contexts but not others, classify it as context-dependent. " +
                        "If you cannot determine the usage, mark it as unknown.\n\n" +
                        "The noun to classify: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Consider standard contemporary English usage.\n" +
                        " - Treat words for groups (team, family, crew, flock, etc.) as collective.\n" +
                        " - Treat ordinary concrete or abstract nouns that do not inherently refer to a group as not collective.\n" +
                        " - Provide one concise usage example that matches the classification.\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON and nothing else.\n" +
                        " * Allowed values for the collective field: \"collective\", \"not collective\", \"context-dependent\", \"unknown\".\n" +
                        " * Escape quotation marks within strings.\n\n" +
                        "Output strictly in this JSON format:\n" +
                        "\n```json\n{\n  \"noun\": \"<noun>\",\n  \"collective\": \"<collective | not collective | context-dependent | unknown>\",\n  \"explanation\": \"<short rationale>\",\n  \"usage\": \"<example sentence>\"\n}\n```";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genCollectiveNouns() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] collectiveArray = GenUtils.extractJsonFields(jsonResponse, Arrays.asList("noun", "collective", "explanation", "usage"));
                    if (collectiveArray != null) {
                        errorInResponse = false;
                        collectiveArray[1] = normalizeCollectiveCategory(collectiveArray[1]);
                        collectiveArray[2] = "\"" + collectiveArray[2] + "\"";
                        collectiveArray[3] = "\"" + collectiveArray[3] + "\"";
                        collectiveArray = GenUtils.appendToStringArray(collectiveArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(collectiveFileName, Arrays.toString(collectiveArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(collectiveFileName,
                            "ERROR! term: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genCollectiveNouns().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify nouns by countability.
     ***************************************************************/
    private void genCountability() {

        String countabilityFileName = "Countability_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English noun countability. " +
                        "Classify whether the noun is a count noun, mass noun, or can be used as both. " +
                        "If it is a proper noun that typically does not pluralize, identify it as a proper noun. " +
                        "If the countability genuinely cannot be determined, mark it as unknown.\n\n" +
                        "The noun to classify: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Consider standard modern English usage.\n" +
                        " - If the noun has both count and mass uses, classify it as \"count and mass noun\" and explain when each use applies.\n" +
                        " - Provide one concise usage example illustrating the classification.\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON and nothing else.\n" +
                        " * Use the following allowed values for the countability field: \"count noun\", \"mass noun\", \"count and mass noun\", \"proper noun\", \"unknown\".\n" +
                        " * Escape quotation marks within strings.\n\n" +
                        "Output strictly in this JSON format:\n" +
                        "\n```json\n{\n  \"noun\": \"<noun>\",\n  \"countability\": \"<count noun | mass noun | count and mass noun | proper noun | unknown>\",\n  \"explanation\": \"<short rationale>\",\n  \"usage\": \"<example sentence>\"\n}\n```";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genCountability() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] countabilityArray = GenUtils.extractJsonFields(jsonResponse, Arrays.asList("noun", "countability", "explanation", "usage"));
                    if (countabilityArray != null) {
                        errorInResponse = false;
                        countabilityArray[1] = normalizeCountabilityCategory(countabilityArray[1]);
                        countabilityArray[2] = "\"" + countabilityArray[2] + "\"";
                        countabilityArray[3] = "\"" + countabilityArray[3] + "\"";
                        countabilityArray = GenUtils.appendToStringArray(countabilityArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(countabilityFileName, Arrays.toString(countabilityArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(countabilityFileName,
                            "ERROR! term: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genCountability().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify whether nouns typically refer to humans.
     ***************************************************************/
    private void genHumanness() {

        String humannessFileName = "Humanness_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in the semantics of English nouns. " +
                        "Classify whether the noun typically denotes a human being (including professions, roles, demonyms, and personal names), a non-human entity, or can refer to both. " +
                        "If the reference cannot be determined, mark it as unknown.\n\n" +
                        "The noun to classify: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Consider the most common contemporary usage.\n" +
                        " - Treat words for animals, objects, abstractions, or organizations as non-human.\n" +
                        " - Use \"human and non-human\" when the noun regularly refers to both (e.g., words like \"host\" or \"leader\").\n" +
                        " - Provide one concise usage example that matches the classification.\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON and nothing else.\n" +
                        " * Allowed values for the classification field: \"human\", \"non-human\", \"human and non-human\", \"unknown\".\n" +
                        " * Escape quotation marks within strings.\n\n" +
                        "Output strictly in this JSON format:\n" +
                        "\n```json\n{\n  \"noun\": \"<noun>\",\n  \"classification\": \"<human | non-human | human and non-human | unknown>\",\n  \"explanation\": \"<short rationale>\",\n  \"usage\": \"<example sentence>\"\n}\n```";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genHumanness() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] humannessArray = GenUtils.extractJsonFields(jsonResponse, Arrays.asList("noun", "classification", "explanation", "usage"));
                    if (humannessArray != null) {
                        errorInResponse = false;
                        humannessArray[1] = normalizeHumannessCategory(humannessArray[1]);
                        humannessArray[2] = "\"" + humannessArray[2] + "\"";
                        humannessArray[3] = "\"" + humannessArray[3] + "\"";
                        humannessArray = GenUtils.appendToStringArray(humannessArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(humannessFileName, Arrays.toString(humannessArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(humannessFileName,
                            "ERROR! term: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genHumanness().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to determine the singular and plural form of every noun
     * in WordNet.
     ***************************************************************/
    private void genPlurals() {

        String pluralsFileName = "Plurals_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert linguist specializing in English noun phrase syntax and plural forms. " +
                        "Your task is to determine the singular and plural form of the given noun or noun phrase. \n\n" +
                        "The noun to classify: \"" + term + "\". \n" +
                        definitionStatement + "\n\n" +
                        "Instructions:" +
                        " - Always provide:" +
                        "   1. The singular form.\n" +
                        "   2. The plural form.\n" +
                        "   3. The noun type, such as \"count noun\", \"mass noun\", \"proper noun\", \"collective noun\", etc.\n" +
                        "   4. An explanation justifying the classification.\n\n" +
                        " - If the noun is a proper noun or unique entity (e.g., personal names, places, institutions) that does not naturally pluralize, return the singular unchanged and \"none\" for the plural." +
                        " - If the noun can pluralize in specific contexts (e.g., metaphorical or generic use), provide the most standard plural form." +
                        " - The explanation must justify why the chosen plural is valid, \"none\", or context-dependent." +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON and nothing else.\n" +
                        " * Do not include any commentary outside the JSON.\n" +
                        "Output strictly in this JSON format:" +
                        "\n\n```json\n{\n  \"singular\": \"<noun in singular form>\",\n  \"plural\": \"<noun in plural form>\",\n  \"type\": \"<count noun | mass noun | proper noun | collective noun | other>\",\n  \"explanation\":\"<rationale for classification>\" \n}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genPlurals() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] pluralsArray = GenUtils.extractJsonFields(jsonResponse, Arrays.asList("singular", "plural", "type", "explanation"));
                    if (pluralsArray != null) {
                        errorInResponse = false;
                        String category;
                        if (!pluralsArray[1].equals(term) && isIrregularPlural(pluralsArray[0], pluralsArray[1])) {
                            category = "Irregular";
                        }
                        else {
                            category = "Regular";
                        }

                        pluralsArray = GenUtils.appendToStringArray(pluralsArray, category);
                        pluralsArray = GenUtils.appendToStringArray(pluralsArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(pluralsFileName, Arrays.toString(pluralsArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(pluralsFileName,
                            "ERROR! term: " + term + ". " + definitionStatement +
                                    " - LLM response: " + llmResponse.replace("\n", "") + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genPlurals().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static boolean isIrregularIndefiniteArticle(String article, String noun) {

        char firstChar = Character.toLowerCase(noun.trim().charAt(0));
        if ("aeiou".indexOf(firstChar) >= 0) {
            return "an".equals(article);
        }
        else {
            return "a".equals(article);
        }
    }

    private static boolean isIrregularPlural(String singular, String givenPlural) {

        if (singular == null || singular.isEmpty() || givenPlural == null || givenPlural.isEmpty()) {
            return false;
        }

        String expectedPlural = pluralize(singular);
        return !expectedPlural.equalsIgnoreCase(givenPlural);
    }

    private static String pluralize(String word) {

        word = word.toLowerCase();
        int len = word.length();

        if (len > 1 && word.endsWith("y") && GenMorphoUtils.isConsonant(word.charAt(len - 2))) {
            return word.substring(0, len - 1) + "ies";
        }
        if (word.endsWith("s") || word.endsWith("sh") || word.endsWith("ch") ||
                word.endsWith("x") || word.endsWith("z")) {
            return word + "es";
        }
        if (word.endsWith("fe")) {
            return word.substring(0, len - 2) + "ves";
        }
        if (word.endsWith("f")) {
            return word.substring(0, len - 1) + "ves";
        }
        if (word.endsWith("o")) {
            return word + "es";
        }
        return word + "s";
    }

    private static String normalizeCountabilityCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        if (normalized.contains("count") && normalized.contains("mass")) {
            return "Count and mass noun";
        }
        if (normalized.contains("count")) {
            return "Count noun";
        }
        if (normalized.contains("uncount") || normalized.contains("mass") ||
                normalized.contains("noncount") || normalized.contains("non-count")) {
            return "Mass noun";
        }
        if (normalized.contains("proper")) {
            return "Proper noun";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    private static String normalizeHumannessCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("both") || spacesNormalized.contains("human and non") ||
                (spacesNormalized.contains("human") && spacesNormalized.contains("non human"))) {
            return "Human and non-human";
        }
        if (spacesNormalized.contains("non human") || spacesNormalized.contains("nonhuman") ||
                spacesNormalized.contains("not human")) {
            return "Non-human";
        }
        if (spacesNormalized.contains("human") || spacesNormalized.contains("person") || spacesNormalized.contains("people")) {
            return "Human";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear") ||
                spacesNormalized.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    private static String normalizeCollectiveCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("context") || spacesNormalized.contains("sometimes") ||
                spacesNormalized.contains("can be") || spacesNormalized.contains("depends")) {
            return "Context-dependent";
        }
        boolean mentionsCollective = spacesNormalized.contains("collective") || spacesNormalized.contains("group noun");
        boolean negatedCollective = spacesNormalized.contains("not collective") ||
                spacesNormalized.contains("non collective") ||
                spacesNormalized.contains("no collective") ||
                spacesNormalized.contains("not a collective") ||
                spacesNormalized.contains("never collective");
        if (negatedCollective || spacesNormalized.contains("ordinary") || spacesNormalized.contains("regular noun")) {
            return "Not collective";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain") ||
                spacesNormalized.contains("unclear") || spacesNormalized.contains("undetermined") ||
                spacesNormalized.contains("indeterminate")) {
            return "Unknown";
        }
        if (mentionsCollective) {
            return "Collective";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }
}
