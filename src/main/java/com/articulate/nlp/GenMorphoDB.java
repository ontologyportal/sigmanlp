package com.articulate.nlp;

import com.articulate.sigma.*;
import java.io.File;
import java.util.Map;
import java.util.Set;

import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;

public class GenMorphoDB {

    public static Map<String,Set<String>> nounSynsetHash;   // Words in root form are String keys,
    public static Map<String,Set<String>> verbSynsetHash;   // String values are 8-digit synset lists.
    public static Map<String,Set<String>> adjectiveSynsetHash;
    public static Map<String,Set<String>> adverbSynsetHash;

    public static void genIndefiniteArticles() {

        for (Map.Entry<String, Set<String>> entry : nounSynsetHash.entrySet()) {
            String key = entry.getKey();
            Set<String> synset = entry.getValue();
            System.out.println("Key: " + key);
        }

    }

    public static void main(String [] args) {

        System.out.println("Starting Generate Morphological Database");
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();

        nounSynsetHash = WordNet.wn.nounSynsetHash;
        verbSynsetHash = WordNet.wn.verbSynsetHash;
        adjectiveSynsetHash = WordNet.wn.adjectiveSynsetHash;
        adverbSynsetHash = WordNet.wn.adverbSynsetHash;

        genIndefiniteArticles();
    }
}