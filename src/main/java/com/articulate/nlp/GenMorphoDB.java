package com.articulate.nlp;

import com.articulate.sigma.*;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.Iterator;
import java.util.Arrays;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;


/** ***************************************************************
 *  Uses OLLAMA to build a morphological database.
 */
public class GenMorphoDB {

    public static boolean debug = true;
    public static TreeMap<String,Set<String>> nounSynsetHash;   // Words in root form are String keys,
    public static TreeMap<String,Set<String>> verbSynsetHash;   // String values are 8-digit synset lists.
    public static TreeMap<String,Set<String>> adjectiveSynsetHash; // Using TreeMaps to leverage
    public static TreeMap<String,Set<String>> adverbSynsetHash;    // alphabetical order

    public static Map<String,String> verbDocumentationHash;       // Keys are synset Strings, values
    public static Map<String,String> adjectiveDocumentationHash;  // are documentation strings.
    public static Map<String,String> adverbDocumentationHash;
    public static Map<String,String> nounDocumentationHash;


    /** ***************************************************************
     *  Used to filter out non-English words and numbers
     */
    public static void removeNonEnglishWords(Map<String,Set<String>> wordMap, String wordMapName) {

        Iterator<Map.Entry<String, Set<String>>> iterator = wordMap.entrySet().iterator();
        int numDeleted = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            String key = entry.getKey();
            if (!key.matches("^[a-zA-Z_.\\'-]+$")) {
                iterator.remove();  // Safe removal via iterator
                numDeleted++;
            }
        }
        System.out.println("Non-English words removed from " + wordMapName + ": " + numDeleted);
    }


    /** ****************************************************************
     * Returns the indefinite article ("a" or "an")
     * based only on the first letter of the given noun.
     */
    public static boolean isIrregularIndefiniteArticle(String article, String noun) {

        char firstChar = Character.toLowerCase(noun.trim().charAt(0));
        if ("aeiou".indexOf(firstChar) >= 0) {
            return "an".equals(article);
        } else {
            return "a".equals(article);
        }
    }



    /** *********************************************************************
     *    Extracts the indefinite article from a JSON object returned by
     */
    public static String[] extractIndefArticleJson(String jsonString) {

        try {
            jsonString = GenUtils.extractFirstJsonObject(jsonString);
            //jsonString = jsonString.replaceAll("(?<=:\\s*\".*?)(?<!\\\\)\"(?!,|\\s*})", "\\\\\"");
            System.out.println("DELETEME: JSON String extracted: " + jsonString);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
            String noun = null;
            String article = null;
            String explanation = null;
            String usage = null;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                System.out.println("DELETEME Field Name: " + field.getKey());
                System.out.println("DELETEME Field Value: " + field.getValue().toString());
                String key = field.getKey().toLowerCase();
                if (key.equals("noun")) {
                    noun = field.getValue().asText();
                } else if (key.equals("article")) {
                    article = field.getValue().asText();
                } else if (key.equals("explanation")) {
                    explanation = field.getValue().asText();
                } else if (key.equals("usage")) {
                    usage = field.getValue().asText();
                }
            }
            System.out.println(noun + article + explanation + usage);
            if (noun == null || article == null) {
                return null; // keys missing
            }
            return new String[]{article, noun, explanation, usage};
        } catch (Exception e) {
            // Parsing error or any unexpected error
            return null;
        }
    }


    /** ***************************************************************
     *  Uses OLLAMA to determine the indefinite article of every noun
     *  in WordNet.
     */
    public static void genIndefiniteArticles() {

        String indefFileName = "IndefiniteArticles_" + GenUtils.OLLAMA_MODEL + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) continue;
            for (String sysnsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(sysnsetId);
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(sysnsetId) + "\". ";
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
                        " * Valid JSON requires escape sequences for quotation markings within strings in JSON fields.\n" +
                        " * Do not include any commentary outside the JSON.\n\n" +
                        "Output strictly in this JSON format (all lowercase for the article):" +
                        "\n\n```json\n{\n  \"article\": \"<a|an|none>\",\n  \"noun\": \"<noun>\",\n  \"explanation\":\"<rationale for classification>\",\n  \"usage\":\"<example sentence with article>\" \n}";
                if (debug) System.out.println("GenMorphoDB.genIndefiniteArticles() Prompt: " + prompt);
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean ERROR_IN_RESPONSE = true;
                if (jsonResponse != null) {
                    String[] indefArticleArray = extractIndefArticleJson(jsonResponse);
                    if (indefArticleArray != null) {
                        ERROR_IN_RESPONSE = false;
                        String category;
                        indefArticleArray[2] = "\"" + indefArticleArray[2] + "\""; //explanation
                        indefArticleArray[3] = "\"" + indefArticleArray[3] + "\""; //usage
                        if (indefArticleArray[0].equals("none"))
                            category = "NA";
                        else if (isIrregularIndefiniteArticle(indefArticleArray[0], indefArticleArray[1]))
                            category = "Irregular";
                        else
                            category = "Regular";

                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, category);
                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(indefFileName, Arrays.toString(indefArticleArray) + "\n");
                    }
                }
                if (ERROR_IN_RESPONSE)
                    GenUtils.writeToFile(indefFileName, "ERROR! term: " + term + ". " + definitionStatement + " - LLM response: " + llmResponse.replace("\n", "") + "\n");

                if (debug) System.out.println("\n\nGenMorphoDB.genIndefiniteArticles().LLMResponse: " + llmResponse + "\n\n**************\n");
            }
        }
    }


    

    // Plurals
    /*
    public static void genPlurals() {
        String indefFileName = "Plurals" + GenUtils.OLLAMA_MODEL + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) continue;
            for (String sysnsetId : entry.getValue()) {
                String definition = nounDocumentationHash.get(sysnsetId);
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + nounDocumentationHash.get(sysnsetId) + "\". ";
                String prompt = "You are an expert linguist specializing in English noun phrase syntax and plural forms. " +
                        "Your task is to determine the singular and plural form of the given noun or noun phrase. \n\n" +
                        "The noun to classify: \"" + term + "\". \n" +
                        definitionStatement + "\n\n" +
                        "Instructions: \n" +
                        "- Decide whether the correct indefinite article is \"a\", \"an\", or \"none\".\n" +
                        "- Base your decision on pronunciation and grammatical convention.\n" +
                        "- If the noun is a scientific or proper name that normally does not take an article, return \"none\".\n" +
                        "- If an article applies only in specific usage contexts, still provide the indefinite article.\n" +
                        "- Always provide:\n" +
                        "  1. The singular form of the noun.\n" +
                        "  2. The plural form of the noun.\n\n" +
                        "Important formatting rules:\n" +
                        " * Output only valid JSON.\n" +
                        " * Do not include any commentary outside the JSON.\n" +
                        "Output strictly in this JSON format (all lowercase for the article):" +
                        "\n\n```json\n{\n  \"singular\": \"<noun in singular form>\",\n  \"plural\": \"<noun in plural form>\",\n  \"explanation\":\"<rationale for classification>\" \n}";
                if (debug) System.out.println("GenMorphoDB.genPlurals() Prompt: " + prompt);
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean ERROR_IN_RESPONSE = true;
                if (jsonResponse != null) {
                    String[] indefArticleArray = extractIndefArticleJson(jsonResponse);
                    if (indefArticleArray != null) {
                        ERROR_IN_RESPONSE = false;
                        String category;
                        indefArticleArray[2] = "\"" + indefArticleArray[2] + "\""; //explanation
                        indefArticleArray[3] = "\"" + indefArticleArray[3] + "\""; //usage
                        if (indefArticleArray[0].equals("none"))
                            category = "NA";
                        else if (isIrregularIndefiniteArticle(indefArticleArray[0], indefArticleArray[1]))
                            category = "Irregular";
                        else
                            category = "Regular";

                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, category);
                        indefArticleArray = GenUtils.appendToStringArray(indefArticleArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(indefFileName, Arrays.toString(indefArticleArray) + "\n");
                    }
                }
                if (ERROR_IN_RESPONSE)
                    GenUtils.writeToFile(indefFileName, "ERROR! term: " + term + ". " + definitionStatement + " - LLM response: " + llmResponse.replace("\n", "") + "\n");

                if (debug) System.out.println("\n\nGenMorphoDB.genIndefiniteArticles().LLMResponse: " + llmResponse + "\n\n**************\n");
            }
        }
    }
*/
    // Past tenses, other verb tenses

    // Progressives

    // Prepositions. Contingent on the verb, direct object and sometimes the indirect object
    // Do comparison against verb frames in word net, . Wordnet probably admits cases that dont work very general

    // Coens-Kappa measure of intersubject agreement.

    // Pairing adjectives with nouns and adverbs with verbs. (List all the adverbs that go with this verb).
    // Out of all the adjectives, which one fits with this noun.

    // Stative vs. Performative processes
    // Different types of verbs

    /**********************************************************************************
     *
     * @param HashMap
     */
    public static void printSynsetHash(Map<String, Set<String>> wordHash, Map<String,String> documentationHash) {

        for (Map.Entry<String, Set<String>> entry : wordHash.entrySet()) {
            String key = entry.getKey();
            Set<String> values = entry.getValue();
            System.out.println(key + " : " + values);
            for (String value : values) {
                System.out.println("    " + value + ": " + documentationHash.get(value));
            }
        }
    }


    public static void main(String [] args) {

        System.out.println("Starting Generate Morphological Database");
        if (args.length > 0) {
            GenUtils.setOllamaModel(args[0]);
        }

        System.out.println("Using model: " + GenUtils.OLLAMA_MODEL);
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();

        nounSynsetHash = new TreeMap<>(WordNet.wn.nounSynsetHash);
        verbSynsetHash = new TreeMap<>(WordNet.wn.verbSynsetHash);
        adjectiveSynsetHash = new TreeMap<>(WordNet.wn.adjectiveSynsetHash);
        adverbSynsetHash = new TreeMap<>(WordNet.wn.adverbSynsetHash);

        removeNonEnglishWords(nounSynsetHash, "noun set");
        removeNonEnglishWords(verbSynsetHash, "verb set");
        removeNonEnglishWords(adjectiveSynsetHash, "adjective set");
        removeNonEnglishWords(adverbSynsetHash, "adverb set");

        nounDocumentationHash = WordNet.wn.nounDocumentationHash;
        verbDocumentationHash = WordNet.wn.verbDocumentationHash;
        adverbDocumentationHash = WordNet.wn.adverbDocumentationHash;
        adjectiveDocumentationHash = WordNet.wn.adjectiveDocumentationHash;

        // printSynsetHash(nounSynsetHash, nounDocumentationHash);
        System.out.println("Noun set size      : " + nounSynsetHash.size());
        System.out.println("Verb set size      : " + verbSynsetHash.size());
        System.out.println("Adjective set size : " + adjectiveSynsetHash.size());
        System.out.println("Adverb set size    : " + adverbSynsetHash.size());

        genIndefiniteArticles();
    }
}