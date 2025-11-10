package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Shared utilities for morphological database generation.
 ***************************************************************/
public final class GenMorphoUtils {

    public static boolean debug = true;
    private static final String OUTPUT_ROOT = "MorphologicalDatabase";

    /***************************************************************
     * Builds the standardized output path for morphology resources.
     ***************************************************************/
    public static String resolveOutputFile(String wordType, String classificationFileName) {

        if (classificationFileName == null || classificationFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("classificationFileName cannot be null or empty.");
        }
        String normalizedWordType = (wordType == null || wordType.trim().isEmpty())
                ? "misc"
                : wordType.trim().toLowerCase();
        String sanitizedModelName = GenUtils.getOllamaModel()
                .replace('.', '_')
                .replace(':', '_');
        Path outputDir = Paths.get(OUTPUT_ROOT, sanitizedModelName, normalizedWordType);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create output directory: " + outputDir, e);
        }
        return outputDir.resolve(classificationFileName).toString();
    }

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
