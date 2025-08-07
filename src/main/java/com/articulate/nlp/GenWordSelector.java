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
 *   3. Frame-lite (requires frame-lite ProcessTypes.csv file)
 *   4. Ollama just ask - just asks for words and hopes they are in SUMO.
 *   5. Ollama subset - pulls best fit from a subset of random SUMO objects
 *   6. Ollama with Frame-lite - Ollama selects from a set defined by ProcessTypes.csv
 */

public class GenWordSelector {

    public static boolean debug = false;
    public static SelectionStrategy strategy = SelectionStrategy.FRAME_LITE_WITH_OLLAMA;
    private static final int OBJ_SUBSET_SIZE = 20;
    public static final Random rand = new Random();

    public static int foundWordReturned = 0; // Used to track when Ollama finds a word in SUMO successfully.
    public static int randomWordReturned = 0; // Used to track when Ollama can't find a word so a random word is returned.

    public enum SelectionStrategy {
        RANDOM, WORD_PAIR, FRAME_LITE, FRAME_LITE_WITH_OLLAMA, OLLAMA_JUST_ASK, OLLAMA_SUBSET
    }

    public enum PoS {
        SUBJECT, DIRECT, INDIRECT
    }

    public static boolean isFrameLiteStrategy() {
        return strategy == SelectionStrategy.FRAME_LITE_WITH_OLLAMA || strategy == SelectionStrategy.FRAME_LITE;
    }
    public static boolean isWordPairStrategy() { return strategy == SelectionStrategy.WORD_PAIR; }


    /***************************************************************
     *    getNoun is the entry point of GenWordSelector.
     *    Based on the strategy defined, this method
     *    will return a noun for the requested part of speech
     */
    public static String getNoun(PoS pos, LFeatureSets lfeatset, LFeatures lfeat, KBLite kbLite, String className) {

        if (debug) System.out.println("\n\n");
        if (debug) Thread.dumpStack();
        if (debug) System.out.println("Ollama has been used to find " + GenWordSelector.foundWordReturned + " words. Ollama failed to find a word " + GenWordSelector.randomWordReturned + " times.");

        switch (strategy) {
            case RANDOM:
                if (className != null && !className.equals(""))
                    return lfeatset.getRandomSubclassFrom(className);
                return lfeatset.objects.getNext();
            case WORD_PAIR:
                if (className != null && className != "")
                    return WordPairFrequency.getNounInClassFromVerb(lfeatset, lfeat, kbLite, className);
                else if (pos == PoS.SUBJECT)
                    return WordPairFrequency.getNounFromVerb(lfeatset, lfeat);
                else if (pos == PoS.DIRECT || pos == PoS.INDIRECT)
                    return WordPairFrequency.getNounFromNounAndVerb(lfeatset, lfeat);
                System.out.println("UNKNOWN PART OF SPEECH BEING REQUESTED.");
                System.exit(0);
                return WordPairFrequency.getNounFromVerb(lfeatset, lfeat);
            case FRAME_LITE:
            case FRAME_LITE_WITH_OLLAMA:
                return getObjectFromProcessTypes(pos, kbLite, lfeat, lfeatset);
            case OLLAMA_JUST_ASK:
                return getObjectJustAskOllama(pos, kbLite, lfeat, lfeatset);
            case OLLAMA_SUBSET:
                return getTermFromSubsetWithOllama(pos, kbLite, lfeat, lfeatset, null);
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
    public static String getObjectFromProcessTypes(PoS pos, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {

        List<LFeatureSets.ProcessTypeEntry> processes = lfeatset.processTypes.get(lfeat.verbType);
        if (processes == null) return lfeatset.objects.getNext();
        LFeatureSets.ProcessTypeEntry process = processes.get(new Random().nextInt(processes.size()));
        //LFeatureSets.printProcessTypeEntry(process);
        if (pos == PoS.INDIRECT) { // indirect object
            if (process.IndirectObjClass == null || process.IndirectObjClass.equals("")) return "";
            if (process.IndirectObjClass.equals("ComputerUser"))
                return "Human";
            lfeat.indirectPrep = process.prepositionIndObj;
            if (strategy == SelectionStrategy.FRAME_LITE_WITH_OLLAMA)
                return getTermFromSubsetWithOllama(PoS.INDIRECT, kbLite, lfeat, lfeatset, process.IndirectObjClass);
            return lfeatset.getRandomSubclassFrom(process.IndirectObjClass);
        }
        if (pos == PoS.DIRECT) {
            if (process.ObjectClass == null || process.ObjectClass.equals("")) return "";
            if (process.ObjectClass.equals("ComputerUser"))
                return "Human";
            lfeat.directPrep = process.PrepositionObject;
            if (strategy == SelectionStrategy.FRAME_LITE_WITH_OLLAMA)
                return getTermFromSubsetWithOllama(PoS.DIRECT, kbLite, lfeat, lfeatset, process.ObjectClass);
            return lfeatset.getRandomSubclassFrom(process.ObjectClass);
        }
        if (pos == PoS.SUBJECT) {
            if (process.SubjectClass == null || process.SubjectClass.equals("")) return "";
            if (process.SubjectClass.equals("ComputerUser"))
                return "Human";
            if (strategy == SelectionStrategy.FRAME_LITE_WITH_OLLAMA)
                return getTermFromSubsetWithOllama(PoS.SUBJECT, kbLite, lfeat, lfeatset, process.SubjectClass);
            return lfeatset.getRandomSubclassFrom(process.SubjectClass);
        }
        System.exit(0);
        return "ERROR in getObjectFromProcessTypes(). Unknown pos!";
    }


    /** ******************************************************************
     *   Ollama strategy. With this method, Ollama is asked what the best
     *   part of speech should be, and then a determination must be made
     *   what the best SUMO matching of the returned term is.
     */
    public static String getObjectJustAskOllama(PoS pos, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {

        String prompt = getOllamaPromptPrefix(pos, kbLite, lfeat, lfeatset) +
                " and respond in the following JSON format, putting results in the \"TermName\" field: " +
                "\n{\n\t\"terms\":[\n\t\t{\"TermName\"=\"term1\"},{\"TermName\"=\"term2\"},{\"TermName\"=\"term3\"},{\"TermName\"=\"term4\"},{\"TermName\"=\"term5\"}]";
        if (debug) System.out.println(prompt);
        String response = GenUtils.askOllama(prompt);
        List<String> returnedObjects = extractTermNames(response);
        if (debug) System.out.println("\n\n" + response + "\n\n\n");
        if (debug) System.out.println("Final Results: " + returnedObjects);
        if (returnedObjects == null) {
            randomWordReturned++;
            return lfeatset.objects.getNext();
        }
        for (String obj:returnedObjects) {
            Set<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(obj.toLowerCase());
            String noun = GenUtils.getBestSUMOMapping(synsetOfTerm);
            if (lfeatset.objects.terms.contains(noun)) {
                if (debug) System.out.println("Returning: " + noun + " for verb " + lfeat.verb);
                foundWordReturned++;
                if (rand.nextBoolean() && kbLite.isSubclass(noun, "Human"))
                    return "Human";
                return noun;
            }
        }
        randomWordReturned++;
        return lfeatset.objects.getNext();
    }

    /** *************************************************************
     * Ollama strategy where a subset of SUMO terms are passed,
     * and Ollama is asked to select the best SUMO term.
     */
    public static String getTermFromSubsetWithOllama(PoS pos, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset, String className) {

        if (debug) System.out.println("Getting from class: " + className);
        String subject = lfeat.subj;
        String dirObject = lfeat.directName;
        String indirObj = lfeat.indirectName;
        String tInfoJSON = "";
        if (className != null && !className.equals("")) {
            tInfoJSON = getJSONSetOfSize(OBJ_SUBSET_SIZE, lfeatset.getSubclassAsTermInfos(className));
        }
        else {
            tInfoJSON = getJSONSetOfSize(OBJ_SUBSET_SIZE, lfeatset.termInfos);
        }
        String prompt = getOllamaPromptPrefix(pos, kbLite, lfeat, lfeatset) +
                " from the following JSON list, and respond in JSON format: " + tInfoJSON;
        if (debug) System.out.println(prompt);
        String response = GenUtils.askOllama(prompt);
        List<String> returnedTerms = extractTermNames(response);
        if (debug) System.out.println("\n\n" + response + "\n\n\n");
        if (debug) System.out.println("Final Results: " + returnedTerms);
        if (returnedTerms == null) {
            if (debug) System.out.println("returnedObjects is null. Returning a random item from the subclass.");
            randomWordReturned++;
            if (className != null && !className.equals(""))
                return lfeatset.getRandomSubclassFrom(className);
            return lfeatset.objects.getNext();
        }
        for (String term:returnedTerms) {
            if (isFrameLiteStrategy()) {
                if (className != null && !className.equals("") && lfeatset.termInSubclass(term, className)) {
                    foundWordReturned++;
                    if (rand.nextBoolean() && kbLite.isSubclass(term, "Human")) {
                        if (debug) System.out.println("GenWordSelector.getTermFromSubsetWithOllama() returning a generic Human randomly");
                        return "Human";
                    }
                    if (debug) System.out.println("Returning: " + term + " in subclass " + className + " for verb " + lfeat.verb);
                    return term;
                }
            }
            else if (lfeatset.objects.terms.contains(term)) {
                if (debug) System.out.println("Returning object: " + term + " for verb " + lfeat.verb);
                foundWordReturned++;
                if (rand.nextBoolean() && kbLite.isSubclass(term, "Human"))
                    return "Human";
                return term;
            }
        }
        if (debug) System.out.println("No objects matched an appropriate SUMO term. Returning a random object.");
        randomWordReturned++;
        return lfeatset.objects.getNext();
    }

    /******************************************************************
     *   Used with ollama strategies to generate a prompt prefix.
     */
    private static String getOllamaPromptPrefix(PoS pos, KBLite kbLite, LFeatures lfeat, LFeatureSets lfeatset) {

        String verbDefinition = LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.verbType));
        String objectToGet = "";
        String verbDef = "The definition of the verb \"" + lfeat.verb + "\" is \"" + verbDefinition + "\". ";
        String subjDef = "";
        String dirObjDef = "";
        String indObjDef = "";
        String promptFirstPartEnding = "";
        if (pos == PoS.DIRECT)
            objectToGet = "a direct object";
        else if (pos == PoS.INDIRECT)
            objectToGet = "an indirect object";
        else if (pos == PoS.SUBJECT)
            objectToGet = "a subject";

        // This is a cludge because of the inconsistent use of lfeat.subj vs lfeat.subjType
        if (isFrameLiteStrategy() && lfeat.subjType != null) {
            promptFirstPartEnding += "The subject is <" + lfeat.subjName + ">. ";
            subjDef = "The definition of the subject \"" + lfeat.subjName + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.subjType)) + "\". ";
        }
        else if (lfeat.subj != null) {
            promptFirstPartEnding += "The subject is <" + lfeat.subj + ">. ";
            subjDef = "The definition of the subject \"" + lfeat.subj + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.subjType)) + "\". ";
        }
        if (lfeat.directName != null) {
            promptFirstPartEnding += "The direct object <" + lfeat.directName + ">. ";
            dirObjDef = "The definition of the direct object \"" + lfeat.directName + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.directType)) + "\". ";
        }
        if (lfeat.indirectName != null) {
            promptFirstPartEnding += "The indirect object is <" + lfeat.indirectName + ">. ";
            indObjDef = "The definition of the indirect object \"" + lfeat.indirectName + "\" is \"" + LFeatureSets.processDocumentation(kbLite.getDocumentation(lfeat.indirectType)) + "\". ";
        }
        return "You are an expert linguist that only knows JSON format. " +
                "I need help choosing " + objectToGet + " that best goes with the verb <" + lfeat.verb + ">. " +
                promptFirstPartEnding + verbDef + subjDef + dirObjDef + indObjDef +
                "Give me the top five terms that go well as " + objectToGet;
    }


    /** *******************************************************************
     *  Used with Ollama strategy to turn SUMO classes into JSON objects
     */
    private static String getJSONSetOfSize(int n, ArrayList<LFeatureSets.TermInfo> termInfos) {

        Collections.shuffle(termInfos);
        List<LFeatureSets.TermInfo> subsetTermInfos = termInfos;
        if (n <= subsetTermInfos.size()) {
            subsetTermInfos = termInfos.subList(0, n);
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


    /**************************************************************************
     *  Used with Ollama strategies to extract SUMO classes from JSON objects
     *  returned by Ollama.
     */
    public static List<String> extractTermNames(String llmOutput) {

        // Step 1: Remove all <think>...</think> sections
        String cleaned = llmOutput.replaceAll("(?s)<think>.*?</think>", "");

        // Get anything in the form "TermName":"Thing to Get"
        List<String> termNames = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"(?:TermName|term)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(llmOutput);
        while (matcher.find()) {
            termNames.add(matcher.group(1));
        }
        return termNames;
    }

}