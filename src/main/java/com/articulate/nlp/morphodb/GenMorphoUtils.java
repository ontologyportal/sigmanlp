package com.articulate.nlp.morphodb;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Shared utilities for morphological database generation.
 ***************************************************************/
public final class GenMorphoUtils {

    public static boolean debug = true;

    /***************************************************************
     * Filters out non-English words and numbers from the provided map.
     ***************************************************************/
    public static void removeNonEnglishWords(Map<String, Set<String>> wordMap, String wordMapName) {

        Iterator<Map.Entry<String, Set<String>>> iterator = wordMap.entrySet().iterator();
        int numDeleted = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            String key = entry.getKey();
            if (!key.matches("^[a-zA-Z_.\\'-]+$")) {
                iterator.remove();
                numDeleted++;
            }
        }
        System.out.println("Non-English words removed from " + wordMapName + ": " + numDeleted);
    }

    /***************************************************************
     * Utility to determine whether the supplied character is a consonant.
     ***************************************************************/
    public static boolean isConsonant(char c) {

        return "bcdfghjklmnpqrstvwxyz".indexOf(Character.toLowerCase(c)) != -1;
    }

    /***************************************************************
     * Convenience method for debugging synset hashes.
     ***************************************************************/
    public static void printSynsetHash(Map<String, Set<String>> wordHash, Map<String, String> documentationHash) {

        for (Map.Entry<String, Set<String>> entry : wordHash.entrySet()) {
            String key = entry.getKey();
            Set<String> values = entry.getValue();
            System.out.println(key + " : " + values);
            for (String value : values) {
                System.out.println("    " + value + ": " + documentationHash.get(value));
            }
        }
    }
}
