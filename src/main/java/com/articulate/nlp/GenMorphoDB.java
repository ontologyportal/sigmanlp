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
    public static void removeNonEnglishWords(Map<String,Set<String>> wordMap) {

        Iterator<Map.Entry<String, Set<String>>> iterator = wordMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            String key = entry.getKey();
            if (!key.matches("^[a-zA-Z_.\\'-]+$")) {
                System.out.println("Removing: " + key);
                iterator.remove();  // Safe removal via iterator
            }
        }
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
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
            String noun = null;
            String article = null;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase();
                if (key.equals("noun")) {
                    noun = field.getValue().asText();
                } else if (key.equals("article")) {
                    article = field.getValue().asText();
                }
            }
            if (noun == null || article == null) {
                return null; // keys missing
            }
            return new String[]{article, noun};
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

        String indefFileName = "IndefArticles_" + GenUtils.OLLAMA_MODEL + ".txt";
        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) continue;
            for (String sysnsetId : entry.getValue()) {
                String definition = "Its definition is: \"" + nounDocumentationHash.get(sysnsetId) + "\". ";
                String prompt = "You are an expert linguist of the English language. You are " +
                        "marking up nouns and noun phrases with their indefinite articles for later research. " +
                        "The noun to markup is \"" + term + "\". " + definition +
                        "Determine the indefinite article, either 'a', 'an', " +
                        "or 'none' as appropriate. Only give the definite article 'the' if there is no case where " +
                        "an indefinite article fits. The results need to be processed by a machine, so return the " +
                        "results in the following JSON format." +
                        "\n\n{\n  \"article\": \"<article>\",\n  \"noun\": \"<noun>\"\n}";
                if (debug) System.out.println("GenMorphoDB.genIndefiniteArticles() Prompt: " + prompt);
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean ERROR_IN_RESPONSE = true;
                if (jsonResponse != null) {
                    String[] indefArticleArray = extractIndefArticleJson(jsonResponse);
                    if (indefArticleArray != null) {
                        ERROR_IN_RESPONSE = false;
                        if (isIrregularIndefiniteArticle(indefArticleArray[0], indefArticleArray[1]))
                            GenUtils.writeToFile(indefFileName, "Irregular:" + Arrays.toString(indefArticleArray) + "\n");
                        else
                            GenUtils.writeToFile(indefFileName, "Regular:  " + Arrays.toString(indefArticleArray) + "\n");

                    }
                }
                if (ERROR_IN_RESPONSE)
                    GenUtils.writeToFile(indefFileName, "Error! term: " + term + " - response: " + llmResponse.replace("\n", "") + "\n");

                if (debug) System.out.println("\n\nGenMorphoDB.genIndefiniteArticles().LLMResponse: " + llmResponse + "\n\n**************\n");
            }
        }
    }


    // Different types of verbs
    

    // Plurals


    // Past tenses, other verb tenses

    // Progressives

    // Prepositions. Contingent on the verb, direct object and sometimes the indirect object
    // Do comparison against verb frames in word net, . Wordnet probably admits cases that dont work very general

    // Coens-Kappa measure of intersubject agreement.

    // Pairing adjectives with nouns and adverbs with verbs. (List all the adverbs that go with this verb).
    // Out of all the adjectives, which one fits with this noun.

    // Stative vs. Performative processes

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
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();

        nounSynsetHash = new TreeMap<>(WordNet.wn.nounSynsetHash);
        verbSynsetHash = new TreeMap<>(WordNet.wn.verbSynsetHash);
        adjectiveSynsetHash = new TreeMap<>(WordNet.wn.adjectiveSynsetHash);
        adverbSynsetHash = new TreeMap<>(WordNet.wn.adverbSynsetHash);

        removeNonEnglishWords(nounSynsetHash);
        removeNonEnglishWords(verbSynsetHash);
        removeNonEnglishWords(adjectiveSynsetHash);
        removeNonEnglishWords(adverbSynsetHash);

        nounDocumentationHash = WordNet.wn.nounDocumentationHash;
        verbDocumentationHash = WordNet.wn.verbDocumentationHash;
        adverbDocumentationHash = WordNet.wn.adverbDocumentationHash;
        adjectiveDocumentationHash = WordNet.wn.adjectiveDocumentationHash;

        // printSynsetHash(nounSynsetHash, nounDocumentationHash);
        System.out.println("DELETEME GenMorphoDB: " + nounSynsetHash.size());

        genIndefiniteArticles();
    }
}