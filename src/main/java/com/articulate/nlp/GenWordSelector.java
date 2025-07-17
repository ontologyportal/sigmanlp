package com.articulate.nlp;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map.Entry;
import com.articulate.sigma.wordNet.WordNet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;


/** **************************************************************************
 *   This code chooses words for generated sentences.
 *   There are four different methods to choose from:
 *   1. Random
 *   2. WordPair frequency (calculated using the COCA corpus)
 *   3. Frame-lite (requires frame-lite ProcessTypes file)
 *   4. Ollama just ask - just asks for words and hopes they are in SUMO.
 *   5. Ollama subset - pulls best fit from a subset of random SUMO objects
 */

public class GenWordSelector {

    public static boolean debug = true;
    public static final SelectionStrategy strategy = SelectionStrategy.FRAME_LITE;
    private static final int OBJ_SUBSET_SIZE = 20;
    public static final Random rand = new Random();


    public enum SelectionStrategy {
        RANDOM, WORD_PAIR, FRAME_LITE, OLLAMA_JUST_ASK, OLLAMA_SUBSET
    }

    public static boolean isFrameLiteStrategy() {
        return strategy == SelectionStrategy.FRAME_LITE;
    }

    public static String getNounFromVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite) {

        System.out.println("****************** getNounFromVerb " + strategy + " *************************");
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounFromVerb(lfeatset, lfeat);
            case FRAME_LITE:
                return getObjectFromProcessTypes(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_JUST_ASK:
                return getObjectJustAskOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_SUBSET:
                return getObjectFromSubsetWithOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    public static String getNounInClassFromVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite, String className) {
        System.out.println("++++++++++++++++++ getNounInClassFromVerb  " + strategy + " ++++++++++++++++++++++");
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounInClassFromVerb(lfeatset, lfeat, kbLite, className);
            case FRAME_LITE:
                return getObjectFromProcessTypes(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_JUST_ASK:
                return getObjectJustAskOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_SUBSET:
                return getObjectFromSubsetWithOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    public static String getNounFromNounAndVerb(LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite) {
        System.out.println("========== getNounFromNounAndVerb  " + strategy + " ==================");
        switch (strategy) {
            case RANDOM:
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                return WordPairFrequency.getNounFromNounAndVerb(lfeatset, lfeat);
            case FRAME_LITE:
                return getObjectFromProcessTypes(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_JUST_ASK:
                return getObjectJustAskOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            case OLLAMA_SUBSET:
                return getObjectFromSubsetWithOllama(lfeat.subj, lfeat.directName, kbLite, lfeat, lfeatset);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    /*************************************************
     * Frame-lite strategies
     *             verb
     *             SubjectClass
     *             CaseRoleSubject
     *             ObjectClass
     *             CaseRoleObject
     *             PrepositionObject
     *             IndirectObjClass
     *             CaseRoleIndObj
     *             prepositionIndObj
     *             HelperVerb;
     */
    public static String getObjectFromProcessTypes(String subject, String dirObject, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {
        List<LFeatureSets.ProcessTypeEntry> processes = lfeatset.processTypes.get(lfeat.verbType);
        if (processes == null) return lfeatset.objects.getNext();
        LFeatureSets.ProcessTypeEntry process = processes.get(new Random().nextInt(processes.size()));
        System.out.println("GenWordSelector.getObjectFromProcessTypes(): " + lfeat.verbType);
        LFeatureSets.printProcessTypeEntry(process);
        if (subject == null) {
            return lfeatset.getRandomSubclassFrom(process.SubjectClass);
        }
        else if (dirObject == null) {
            lfeat.directPrep = process.PrepositionObject;
            return lfeatset.getRandomSubclassFrom(process.ObjectClass);
        }
        else { // indirect object
            lfeat.indirectPrep = process.prepositionIndObj;
            return lfeatset.getRandomSubclassFrom(process.IndirectObjClass);
        }
    }

    /*************************************************
     * Ollama strategies
     */
    public static String getObjectJustAskOllama(String subject, String dirObject, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {
        String prompt = getOllamaPromptPrefix(subject, dirObject, kbLite, lfeat, lfeatset) +
                " and respond in the following JSON format, putting results in the \"TermName\" field: " +
                "\n{\n\t\"terms\":[\n\t\t{\"TermName\"=\"term1\"},{\"TermName\"=\"term2\"},{\"TermName\"=\"term3\"},{\"TermName\"=\"term4\"},{\"TermName\"=\"term5\"}]";
        System.out.println(prompt);
        String response = GenUtils.askOllama(prompt);
        List<String> returnedObjects = extractTermNames(response);
        if (debug) System.out.println("\n\n" + response + "\n\n\n");
        if (debug) System.out.println("Final Results: " + returnedObjects);
        if (returnedObjects == null) return lfeatset.objects.getNext();
        for (String obj:returnedObjects) {
            Set<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(obj.toLowerCase());
            String noun = GenUtils.getBestSUMOMapping(synsetOfTerm);
            if (lfeatset.objects.terms.contains(noun)) {
                System.out.println("Returning: " + noun + " for verb " + lfeat.verb);
                return noun;
            }
        }
        return lfeatset.objects.getNext();
    }


    public static String getObjectFromSubsetWithOllama(String subject, String dirObject, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {
        String tInfoJSON = getJSONSetOfObjectsOfSize(OBJ_SUBSET_SIZE, lfeatset);
        String prompt = getOllamaPromptPrefix(subject, dirObject, kbLite, lfeat, lfeatset) +
                " from the following JSON list, and respond in JSON format: " + tInfoJSON;
        System.out.println(prompt);
        String response = GenUtils.askOllama(prompt);
        List<String> returnedObjects = extractTermNames(response);
        if (debug) System.out.println("\n\n" + response + "\n\n\n");
        if (debug) System.out.println("Final Results: " + returnedObjects);
        if (returnedObjects == null) return lfeatset.objects.getNext();
        for (String obj:returnedObjects) {
            if (lfeatset.objects.terms.contains(obj)) {
                System.out.println("Returning: " + obj + " for verb " + lfeat.verb);
                return obj;
            }
        }
        return lfeatset.objects.getNext();
    }

    private static String getOllamaPromptPrefix(String subject, String dirObject, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {
        String verbDefinition = LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.verbType));
        String objectToGet = "a subject";
        String verbDef = "The definition of the verb \"" + lfeat.verb + "\" is \"" + verbDefinition + "\". ";
        String subjDef = "";
        String dirObjDef = "";
        String promptFirstPartEnding = ". ";
        if (subject != null) {
            objectToGet = "a direct object";
            promptFirstPartEnding = " and the subject <" + subject + ">. ";
            subjDef = "The definition of the subject \"" + subject + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.subjType)) + "\". ";
        }
        if (dirObject != null) {
            objectToGet = "an indirect object";
            promptFirstPartEnding = ", the subject <" + subject + ">, and the direct object <" + dirObject + ">. ";
            dirObjDef = "The definition of the direct object \"" + dirObject + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.directType)) + "\". ";
        }
        return "You are an expert linguist that only knows JSON format. " +
                "I need help choosing " + objectToGet + " that best goes with the verb <" + lfeat.verb + ">" +
                promptFirstPartEnding + verbDef + subjDef + dirObjDef +
                "Give me the top five terms that go well as " + objectToGet;
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

        // Get anything in the form "TermName":"Thing to Get"
        List<String> termNames = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"TermName\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(llmOutput);
        while (matcher.find()) {
            termNames.add(matcher.group(1));
        }
        return termNames;
    }



}