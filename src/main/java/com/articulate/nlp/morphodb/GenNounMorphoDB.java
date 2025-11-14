package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
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
 *    •	Agentive vs non-agentive nouns (can the referent perform actions?)
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
            case "-a":
                genAgentivity();
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

        String indefFileName = GenMorphoUtils.resolveOutputFile("noun", "IndefiniteArticles.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(indefFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genIndefiniteArticles() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
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
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("article", "noun", "explanation", "usage"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    responseNode.put("usage", responseNode.path("usage").asText(""));
                    String articleValue = responseNode.path("article").asText("");
                    String nounValue = responseNode.path("noun").asText("");
                    String category;
                    if ("none".equals(articleValue)) {
                        category = "NA";
                    }
                    else if (isIrregularIndefiniteArticle(articleValue, nounValue)) {
                        category = "Irregular";
                    }
                    else {
                        category = "Regular";
                    }
                    responseNode.put("article_pattern", category);
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(indefFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse indefinite article response.");
                    GenUtils.writeToFile(indefFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
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

        String collectiveFileName = GenMorphoUtils.resolveOutputFile("noun", "CollectiveNouns.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(collectiveFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genCollectiveNouns() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
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
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("noun", "collective", "explanation", "usage"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    responseNode.put("collective",
                            normalizeCollectiveCategory(responseNode.path("collective").asText("")));
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    responseNode.put("usage", responseNode.path("usage").asText(""));
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(collectiveFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse collective noun response.");
                    GenUtils.writeToFile(collectiveFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
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

        String countabilityFileName = GenMorphoUtils.resolveOutputFile("noun", "Countability.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(countabilityFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genCountability() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
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
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("noun", "countability", "explanation", "usage"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    responseNode.put("countability",
                            normalizeCountabilityCategory(responseNode.path("countability").asText("")));
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    responseNode.put("usage", responseNode.path("usage").asText(""));
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(countabilityFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse countability response.");
                    GenUtils.writeToFile(countabilityFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
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

        String humannessFileName = GenMorphoUtils.resolveOutputFile("noun", "Humanness.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(humannessFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genHumanness() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
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
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("noun", "classification", "explanation", "usage"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    responseNode.put("classification",
                            normalizeHumannessCategory(responseNode.path("classification").asText("")));
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    responseNode.put("usage", responseNode.path("usage").asText(""));
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(humannessFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse humanness response.");
                    GenUtils.writeToFile(humannessFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genHumanness().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify whether nouns can act as agents.
     ***************************************************************/
    private void genAgentivity() {

        String agentivityFileName = GenMorphoUtils.resolveOutputFile("noun", "NounAgentivity.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(agentivityFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genAgentivity() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer analyzing agentivity in English nouns. " +
                        "Determine whether the noun typically denotes an entity capable of intentional action (agentive). " +
                        "If it is agentive, indicate whether the agent is animate (living beings such as people or animals) or inanimate (institutions, organizations, artifacts, etc.).\n\n" +
                        "The noun to classify: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Consider the noun's most common literal sense.\n" +
                        " - Treat organizations, governments, and software that can act as inanimate agents.\n" +
                        " - Mark the noun as non-agentive when it does not independently perform actions.\n" +
                        " - Use 'unknown' if the evidence is insufficient.\n" +
                        " - Provide a concise usage example that matches the classification.\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON and nothing else.\n" +
                        " * Allowed values for agency: \"agentive\", \"non-agentive\", \"unknown\".\n" +
                        " * For agentive nouns, agent_type must be \"animate agent\", \"inanimate agent\", or \"mixed agent type\".\n" +
                        " * For non-agentive or unknown nouns, agent_type must be \"not applicable\" or \"unknown agent type\".\n" +
                        " * Escape quotation marks inside strings.\n\n" +
                        "Output strictly in this JSON format:\n" +
                        "\n```json\n{\n  \"noun\": \"<noun>\",\n  \"agency\": \"<agentive | non-agentive | unknown>\",\n  \"agent_type\": \"<animate agent | inanimate agent | mixed agent type | not applicable | unknown agent type>\",\n  \"explanation\": \"<short rationale>\",\n  \"usage\": \"<example sentence>\"\n}\n```";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenNounMorphoDB.genAgentivity() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("noun", "agency", "agent_type", "explanation", "usage"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    String normalizedAgency = normalizeAgencyCategory(responseNode.path("agency").asText(""));
                    responseNode.put("agency", normalizedAgency);
                    responseNode.put("agent_type",
                            normalizeAgentType(responseNode.path("agent_type").asText(""), normalizedAgency));
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    responseNode.put("usage", responseNode.path("usage").asText(""));
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(agentivityFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse agentivity response.");
                    GenUtils.writeToFile(agentivityFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenNounMorphoDB.genAgentivity().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to determine the singular and plural form of every noun
     * in WordNet.
     ***************************************************************/
    private void genPlurals() {

        String pluralsFileName = GenMorphoUtils.resolveOutputFile("noun", "Plurals.txt");
        Map<String, List<String>> classifiedEntries = GenMorphoUtils.loadExistingClassifications(pluralsFileName);
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                if (GenMorphoUtils.alreadyClassified(classifiedEntries, synsetId)) {
                    if (GenMorphoUtils.debug) {
                        System.out.println("Skipping GenNounMorphoDB.genPlurals() for \"" + term +
                                "\" (" + synsetId + ") - already classified.");
                    }
                    continue;
                }
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
                boolean errorInResponse = true;
                ObjectNode responseNode = GenMorphoUtils.extractRequiredJsonObject(llmResponse,
                        Arrays.asList("singular", "plural", "type", "explanation"));
                if (responseNode != null) {
                    errorInResponse = false;
                    responseNode = GenMorphoUtils.prependSynsetId(responseNode, synsetId);
                    responseNode.put("explanation", responseNode.path("explanation").asText(""));
                    String singularValue = responseNode.path("singular").asText("");
                    String pluralValue = responseNode.path("plural").asText("");
                    String category;
                    if (!pluralValue.equals(term) && isIrregularPlural(singularValue, pluralValue)) {
                        category = "Irregular";
                    }
                    else {
                        category = "Regular";
                    }
                    responseNode.put("plural_pattern", category);
                    responseNode.put("definition", definition == null ? "" : definition);
                    String serializedLine = GenMorphoUtils.serializeJsonLine(responseNode);
                    GenUtils.writeToFile(pluralsFileName, serializedLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, serializedLine);
                }
                if (errorInResponse) {
                    String errorLine = GenMorphoUtils.buildErrorRecord("noun", term, synsetId,
                            definition, llmResponse, "Unable to parse pluralization response.");
                    GenUtils.writeToFile(pluralsFileName, errorLine + "\n");
                    GenMorphoUtils.cacheClassification(classifiedEntries, synsetId, errorLine);
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

    private static String normalizeAgencyCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("non agent") || spacesNormalized.contains("not agent") ||
                spacesNormalized.contains("nonagent") || spacesNormalized.contains("cannot act") ||
                spacesNormalized.contains("inert") || spacesNormalized.contains("non acting") ||
                spacesNormalized.contains("non-active")) {
            return "Non-agentive";
        }
        if (spacesNormalized.contains("agentive") || spacesNormalized.contains("agent") ||
                spacesNormalized.contains("actor") || spacesNormalized.contains("doer") ||
                spacesNormalized.contains("can act") || spacesNormalized.contains("acting entity")) {
            return "Agentive";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain") ||
                spacesNormalized.contains("unclear") || spacesNormalized.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    private static String normalizeAgentType(String rawType, String agencyCategory) {

        if (agencyCategory == null || !"Agentive".equalsIgnoreCase(agencyCategory)) {
            if (rawType == null || rawType.trim().isEmpty()) {
                return "Not applicable";
            }
            String lower = rawType.trim().toLowerCase();
            if (lower.contains("unknown") || lower.contains("uncertain") || lower.contains("unspecified")) {
                return "Unknown agent type";
            }
            return "Not applicable";
        }
        if (rawType == null) {
            return "Unknown agent type";
        }
        String normalized = rawType.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown agent type";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("mixed") || spacesNormalized.contains("both") ||
                (spacesNormalized.contains("animate") && spacesNormalized.contains("inanimate"))) {
            return "Mixed agent type";
        }
        if (spacesNormalized.contains("animate") || spacesNormalized.contains("living") ||
                spacesNormalized.contains("life form") || spacesNormalized.contains("human") ||
                spacesNormalized.contains("animal") || spacesNormalized.contains("person") ||
                spacesNormalized.contains("creature") || spacesNormalized.contains("organism")) {
            return "Animate agent";
        }
        if (spacesNormalized.contains("inanimate") || spacesNormalized.contains("institution") ||
                spacesNormalized.contains("organization") || spacesNormalized.contains("collective") ||
                spacesNormalized.contains("corporate") || spacesNormalized.contains("company") ||
                spacesNormalized.contains("government") || spacesNormalized.contains("software") ||
                spacesNormalized.contains("machine") || spacesNormalized.contains("device") ||
                spacesNormalized.contains("non living") || spacesNormalized.contains("nonliving") ||
                spacesNormalized.contains("artificial")) {
            return "Inanimate agent";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain") ||
                spacesNormalized.contains("unspecified")) {
            return "Unknown agent type";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }
}
