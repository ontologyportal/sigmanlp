package com.articulate.nlp;

import com.articulate.sigma.*;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.Iterator;

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

    /** ***************************************************************
     *  Uses OLLAMA to determine the indefinite article of every noun
     *  in WordNet.
     */
    public static void genIndefiniteArticles() {

        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) break;
            String prompt = "You are an expert linguist of the English language. You are " +
                    "marking up nouns and noun phrases with their indefinite articles for later research. " +
                    "The noun to markup is \""+term+"\". Determine the indefinite article, either 'a', 'an', " +
                    "or 'none' as appropriate. Never give the definite article 'the'. " +
                    "The results need to be processed by a machine, so return the results " +
                    "in the following JSON format.\n\n{\n  \"noun\": \"<noun>\",\n  \"article\": \"<article>\"\n" +
                    "}";
            if (debug) System.out.println("GenMorphoDB.genIndefiniteArticles() Prompt: " + prompt);
            String llmResponse = GenUtils.askOllama(prompt);
            if (debug) System.out.println("\n\nGenMorphoDB.genIndefiniteArticles().LLMResponse: " + llmResponse + "\n\n**************\n");
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