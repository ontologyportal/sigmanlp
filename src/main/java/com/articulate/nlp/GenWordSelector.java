package com.articulate.nlp;


/** ***************************************************************
 *   This code chooses words for generated sentences.
 *   There are four different methods to choose from:
 *   1. Random
 *   2. WordPair frequency (calculated using the COCA corpus)
 *   3. Frame-lite (requires frame-lite file)
 *   4. Ollama
 */

public class GenWordSelector {

    private static final SelectionStrategy strategy = SelectionStrategy.RANDOM;

    public enum SelectionStrategy {
        RANDOM, WORD_PAIR, FRAME_LITE, OLLAMA
    }

    public static String getNounFromVerb(LFeatureSets lfeatset, LFeatures lfeat) {
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounFromVerb(lfeatset, lfeat);
            case FRAME_LITE:
                return "frameLiteNoun";
            case OLLAMA:
                return "ollamaNoun";
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    public static String getNounInClassFromVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kb, String className) {
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounInClassFromVerb(lfeatset, lfeat, kb, className);
            case FRAME_LITE:
                return "frameLiteNoun";
            case OLLAMA:
                return "ollamaNoun";
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    public static String getNounFromNounAndVerb(LFeatureSets lfeatset, LFeatures lfeat) {
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounFromNounAndVerb(lfeatset, lfeat);
            case FRAME_LITE:
                return "frameLiteNoun";
            case OLLAMA:
                return "ollamaNoun";
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

}