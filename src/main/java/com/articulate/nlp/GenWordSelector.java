package com.articulate.nlp;


import java.util.Random;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;


/** ***************************************************************
 *   This code chooses words for generated sentences.
 *   There are four different methods to choose from:
 *   1. Random
 *   2. WordPair frequency (calculated using the COCA corpus)
 *   3. Frame-lite (requires frame-lite file)
 *   4. Ollama
 */

public class GenWordSelector {

    public static boolean debug = false;
    private static final SelectionStrategy strategy = SelectionStrategy.OLLAMA;
    private static final int OBJ_SUBSET_SIZE = 20;
    public static final Random rand = new Random();


    public enum SelectionStrategy {
        RANDOM, WORD_PAIR, FRAME_LITE, OLLAMA
    }

    public static String getNounFromVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite) {
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounFromVerb(lfeatset, lfeat);
            case FRAME_LITE:
                return "frameLiteNoun";
            case OLLAMA:
                return getObjectFromOllama(String verb, String Subject, kbLite, lfeat, lfeatset);
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

    public static String getNounFromNounAndVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite) {
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

    public static String getObjectFromOllama(String verb, String subject, String dirObject, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {
        String tInfoJSON = getJSONSetOfObjectsOfSize(OBJ_SUBSET_SIZE, lfeatset);
        String verbDefinition = LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.verbType));
        String objectToGet = "";
        if (subject == null) {
            objectToGet = "a subject";
        }
        else if (dirObject == null) {
            objectToGet = "a direct object";
        }
        else {
            objectToGet = "an indirect object";
        }
        String prompt = "You are an expert linguist that only knows JSON format. " +
                "I need help choosing " + objectToGet + " that best goes with the verb <" + lfeat.verb + ">." +
                "The definition of " + lfeat.verb + " is " + verbDefinition + ". " +
                "Choose the top five terms that go with the verb from the following JSON list," +
                "and respond in JSON format: " + tInfoJSON;
        System.out.println(prompt);
        String response = GenUtils.askOllama(prompt);
        List<String> returnedObjects = extractTermNames(response);
        if (returnedObjects == null) return lfeatset.objects.getNext();
        if (debug) System.out.println("\n\n" + response + "\n\n\n");
        if (debug) System.out.println("Final Results: " + returnedObjects);
        for (String obj:returnedObjects) {
            if (lfeatset.objects.terms.contains(obj)) {
                System.out.println("Returning: " + obj + " for verb " + lfeat.verb);
                return obj;
            }
        }
        return lfeatset.objects.getNext();
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

    public static List<String> extractTermNames(String llmOutput) {
        // Step 1: Remove all <think>...</think> sections
        String cleaned = llmOutput.replaceAll("(?s)<think>.*?</think>", "");

        // Step 2: Find the first JSON object with a "terms" array
        Pattern pattern = Pattern.compile("\\{\\s*\"terms\"\\s*:\\s*\\[.*?\\]\\s*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(cleaned);

        if (!matcher.find()) {
            return null;
        }

        String jsonString = matcher.group();

        // Step 3: Parse JSON and extract TermNames using Jackson
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            return null;
        }
        JsonNode terms = root.get("terms");
        List<String> termNames = new ArrayList<>();

        if (terms != null && terms.isArray()) {
            for (JsonNode term : terms) {
                JsonNode termNameNode = term.get("TermName");
                if (termNameNode != null) {
                    termNames.add(termNameNode.asText());
                }
            }
        }
        return termNames;
    }


}