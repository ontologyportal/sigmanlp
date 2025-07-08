package com.articulate.nlp;


import java.util.Random;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/** ***************************************************************
 *   This code chooses words for generated sentences.
 *   There are four different methods to choose from:
 *   1. Random
 *   2. WordPair frequency (calculated using the COCA corpus)
 *   3. Frame-lite (requires frame-lite file)
 *   4. Ollama
 */

public class GenWordSelector {

    private static final SelectionStrategy strategy = SelectionStrategy.OLLAMA;
    private static final int OBJ_SUBSET_SIZE = 20;
    public static final Random rand = new Random();


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
                String tInfoJSON = getJSONSetOfObjectsOfSize(OBJ_SUBSET_SIZE, lfeatset);
                String prompt = "You are an expert linguist that only knows JSON format. " +
                        "I need help choosing a subject that best goes with the verb <" + lfeat.verb + ">." +
                        "Choose the top five terms that go with the verb from the following JSON list," +
                        "and respond in JSON format: " + tInfoJSON;
                System.out.println("DELETEME: " + tInfoJSON);
                String response = GenUtils.askOllama(prompt);
                System.out.println("\n\n" + response + "\n\n\n");
                return lfeatset.objects.getNext();
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
                return lfeatset.objects.getNext();
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
                return lfeatset.objects.getNext();
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }


    private static String getJSONSetOfObjectsOfSize(int n, LFeatureSets lfeatset) {
        Collections.shuffle(lfeatset.termInfos);
        List<LFeatureSets.TermInfo> subsetTermInfos = lfeatset.termInfos;
        if (n <= subsetTermInfos.size()) {
            subsetTermInfos = lfeatset.termInfos.subList(0, n);
        }
        String tInfoJSON = "{\n\"terms\":[";
        for (LFeatureSets.TermInfo tInfo:subsetTermInfos) {
            if (tInfo.termFormats != null) {
                tInfoJSON += "\n\t{\n\t\t\"TermName\":\"" + tInfo.termInSumo + "\",";
                tInfoJSON += "\n\t\t\"English\":\"" + tInfo.termFormats.get(rand.nextInt(tInfo.termFormats.size())) + "\"";
                if (tInfo.documentation != null) {
                    tInfoJSON += ",\n\t\t\"Definition\":\"" + tInfo.documentation + "\"";
                }
                tInfoJSON += "\n\t},";
            }
        }
        if (tInfoJSON.endsWith(",")) {
            tInfoJSON = tInfoJSON.substring(0, tInfoJSON.length() - 1);
        }
        tInfoJSON += "]\n}";
        return tInfoJSON;
    }

}