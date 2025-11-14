package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/***************************************************************
 * Entry point that delegates morphological database generation
 * to word-type specific generators.
 ***************************************************************/
public class GenMorphoDB {


    /***************************************************************
     * Runs every morphology generator for all word types.
     ***************************************************************/
    private static void generateAllClassifications(Map<String, Set<String>> nounSynsetHash,
                                                   Map<String, String> nounDocumentationHash,
                                                   Map<String, Set<String>> verbSynsetHash,
                                                   Map<String, String> verbDocumentationHash,
                                                   Map<String, Set<String>> adjectiveSynsetHash,
                                                   Map<String, String> adjectiveDocumentationHash,
                                                   Map<String, Set<String>> adverbSynsetHash,
                                                   Map<String, String> adverbDocumentationHash) {

        System.out.println("Generating all morphological classifications (noun, verb, adjective, adverb).");

        GenNounMorphoDB nounGenerator = new GenNounMorphoDB(nounSynsetHash, nounDocumentationHash);
        for (String fn : Arrays.asList("-i", "-c", "-p", "-h", "-a", "-l")) {
            System.out.println("Running noun generator with " + fn);
            nounGenerator.generate(fn);
        }

        GenVerbMorphoDB verbGenerator = new GenVerbMorphoDB(verbSynsetHash, verbDocumentationHash);
        for (String fn : Arrays.asList("-v", "-c", "-r", "-p", "-a", "-t")) {
            System.out.println("Running verb generator with " + fn);
            verbGenerator.generate(fn);
        }

        GenAdjectiveMorphoDB adjectiveGenerator = new GenAdjectiveMorphoDB(adjectiveSynsetHash, adjectiveDocumentationHash);
        System.out.println("Running adjective generator with -c");
        adjectiveGenerator.generate("-c");

        GenAdverbMorphoDB adverbGenerator = new GenAdverbMorphoDB(adverbSynsetHash, adverbDocumentationHash);
        System.out.println("Running adverb generator with -c");
        adverbGenerator.generate("-c");

        System.out.println("Completed all morphology generation tasks.");
    }


    /***************************************************************
     * Emits CLI usage details so future generators inherit a single
     * source of truth.
     ***************************************************************/
    private static void printHelp() {

        System.out.println("Usage: com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> <model>");
        System.out.println("word-types supported: noun, verb, adjective, adverb, all");
        System.out.println("Noun gen-functions:");
        System.out.println("  -i to generate indefinite articles");
        System.out.println("  -c to generate countability classifications");
        System.out.println("  -p to generate plurals");
        System.out.println("  -h to classify human vs non-human nouns");
        System.out.println("  -a to classify nouns by agentivity (can the referent perform actions?)");
        System.out.println("  -l to classify collective nouns");
        System.out.println("Verb gen-functions:");
        System.out.println("  -v to classify verbs by valence");
        System.out.println("  -c to classify verbs by causativity");
        System.out.println("  -r to classify verbs by reflexive behavior");
        System.out.println("  -p to classify verbs by reciprocal behavior");
        System.out.println("  -a to classify verbs as achievements vs processes");
        System.out.println("  -t to generate full conjugation tables");
        System.out.println("Adjective gen-functions:");
        System.out.println("  -c to classify adjectives by semantic category");
        System.out.println("Adverb gen-functions:");
        System.out.println("  -c to classify adverbs by semantic category");
        System.out.println("All gen-functions:");
        System.out.println("  -a to run every morphology generator");
        System.out.println("Example: java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i llama3.2");
    }

    
    /***************************************************************
     * Entry point: validates CLI args, preps WordNet data, and
     * dispatches to word-type generators.
     ***************************************************************/
    public static void main(String[] args) {

        System.out.println("Starting Generate Morphological Database");

        if (args.length < 3) {
            printHelp();
            return;
        }

        String wordType = args[0].toLowerCase();
        String genFunction = args[1];
        String model = args[2];

        GenUtils.setOllamaModel(model);
        System.out.println("Using model: " + GenUtils.getOllamaModel());

        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();

        Map<String, Set<String>> nounSynsetHash = new TreeMap<>(WordNet.wn.nounSynsetHash);
        Map<String, Set<String>> verbSynsetHash = new TreeMap<>(WordNet.wn.verbSynsetHash);
        Map<String, Set<String>> adjectiveSynsetHash = new TreeMap<>(WordNet.wn.adjectiveSynsetHash);
        Map<String, Set<String>> adverbSynsetHash = new TreeMap<>(WordNet.wn.adverbSynsetHash);

        GenMorphoUtils.removeNonEnglishWords(nounSynsetHash, "noun set");
        GenMorphoUtils.removeNonEnglishWords(verbSynsetHash, "verb set");
        GenMorphoUtils.removeNonEnglishWords(adjectiveSynsetHash, "adjective set");
        GenMorphoUtils.removeNonEnglishWords(adverbSynsetHash, "adverb set");

        System.out.println("Noun set size      : " + nounSynsetHash.size());
        System.out.println("Verb set size      : " + verbSynsetHash.size());
        System.out.println("Adjective set size : " + adjectiveSynsetHash.size());
        System.out.println("Adverb set size    : " + adverbSynsetHash.size());

        switch (wordType) {
            case "noun":
            case "nouns": {
                GenNounMorphoDB nounGenerator = new GenNounMorphoDB(nounSynsetHash, WordNet.wn.nounDocumentationHash);
                nounGenerator.generate(genFunction);
                break;
            }
            case "verb":
            case "verbs": {
                GenVerbMorphoDB verbGenerator = new GenVerbMorphoDB(verbSynsetHash, WordNet.wn.verbDocumentationHash);
                verbGenerator.generate(genFunction);
                break;
            }
            case "adjective":
            case "adjectives": {
                GenAdjectiveMorphoDB adjectiveGenerator =
                        new GenAdjectiveMorphoDB(adjectiveSynsetHash, WordNet.wn.adjectiveDocumentationHash);
                adjectiveGenerator.generate(genFunction);
                break;
            }
            case "adverb":
            case "adverbs": {
                GenAdverbMorphoDB adverbGenerator =
                        new GenAdverbMorphoDB(adverbSynsetHash, WordNet.wn.adverbDocumentationHash);
                adverbGenerator.generate(genFunction);
                break;
            }
            case "all": {
                if (!"-a".equals(genFunction)) {
                    System.out.println("To run every morphology generator, specify word type \"all\" with gen-function \"-a\".");
                    printHelp();
                    break;
                }
                generateAllClassifications(
                        nounSynsetHash, WordNet.wn.nounDocumentationHash,
                        verbSynsetHash, WordNet.wn.verbDocumentationHash,
                        adjectiveSynsetHash, WordNet.wn.adjectiveDocumentationHash,
                        adverbSynsetHash, WordNet.wn.adverbDocumentationHash);
                break;
            }
            default:
                System.out.println("Unsupported word type: " + wordType);
                System.out.println("Please specify a word type (noun, verb, adjective, adverb).");
                printHelp();
                break;
        }
    }


}
