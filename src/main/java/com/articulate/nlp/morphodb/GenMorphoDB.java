package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

    /***************************************************************
     * Entry point that delegates morphological database generation
     * to word-type specific generators.
     ***************************************************************/
public class GenMorphoDB {

    private static final String NO_WORD_TYPE_MESSAGE = "Please specify a word type (noun, verb, adjective, adverb).";

    /***************************************************************
     * Emits CLI usage details so future generators inherit a single
     * source of truth.
     ***************************************************************/
    private static void printHelp() {

        System.out.println("Usage: com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> <model>");
        System.out.println("word-types supported: noun, verb, adjective, adverb");
        System.out.println("Noun gen-functions:");
        System.out.println("  -i to generate indefinite articles");
        System.out.println("  -c to generate countability classifications");
        System.out.println("  -p to generate plurals");
        System.out.println("Verb gen-functions:");
        System.out.println("  -v to classify verbs by valence");
        System.out.println("  -c to classify verbs by causativity");
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
            default:
                System.out.println("Unsupported word type: " + wordType);
                System.out.println(NO_WORD_TYPE_MESSAGE);
                printHelp();
                break;
        }
    }
}
