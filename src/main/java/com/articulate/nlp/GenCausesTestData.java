package com.articulate.nlp;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;

import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.Random;

import com.articulate.sigma.*;
import java.util.*;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WSD;

public class GenCausesTestData {

    public static boolean debug = false;
    public static KBLite kbLite;
    public static String outputFileEnglish = "causes-eng.txt";
    public static String outputFileLogic = "causes-log.txt";
    public static boolean EQUIVALENCE_MAPPINGS = false;
    public static boolean RAW_PROMPT = false;
    public static Options options;
    public static String englishSentence;
    public static String englishSentenceQuestion;
    public static String englishSentenceWithArticles;
    public static String logicPhrase;
    public static String logicPhraseWithArticles;
    public static OllamaAPI ollamaAPI;
    public static RandSet allSUMOTermsRandSet;
    public static Random random;
    public static int numToGenerate;
    public static int sentenceGeneratedCounter;
    public static boolean articlesAreValid;

    public static String[] phrasesCauses = {
            " causes ",             " leads to ",         " results in ",         " brings about ",
            " triggers ",           " provokes ",         " induces ",            " produces ",
            " prompts ",            " gives rise to ",    " is responsible for "
    };

    public static String[] phrasesNotCauses = {
            " does not cause ",            " does not lead to ",           " does not result in ",
            " does not bring about ",      " does not trigger ",           " does not provoke ",
            " does not induce ",           " does not produce ",           " does not prompt ",
            " does not give rise to ",     " is not responsible for "
    };

    public static String[] phrasesCausedBy = {
            " is caused by ",        " is due to ",          " is a result of ",      " is because of ",
            " is brought about by ", " is triggered by ",    " is provoked by ",      " is induced by ",
            " is produced by ",      " is prompted by ",     " stems from ",          " arises from ",
            " originates from ",     " is driven by ",       " is attributable to ",  " can be traced back to "
    };

    public static String[] phrasesNotCausedBy = {
            " is not caused by ",            " is not due to ",           " is not a result of ",
            " is not because of ",           " is not brought about by ", " is not triggered by ",
            " is not provoked by ",          " is not induced by ",       " is not produced by ",
            " is not prompted by ",          " does not stem from ",      " does not arise from ",
            " does not originate from ",     " is not driven by ",        " is not attributable to ",
            " cannot be traced back to "
    };

    public static String[] phrasesQuestionTermCauses = {
            "What does <TERM> <NOT> cause?",            "What does <TERM> <NOT> lead to?",
            "What does <TERM> <NOT> result in?",        "What does <TERM> <NOT> bring about?",
            "What does <TERM> <NOT> trigger?",          "What does <TERM> <NOT> provoke?",
            "What does <TERM> <NOT> induce?",           "What does <TERM> <NOT> produce?",
            "What does <TERM> <NOT> prompt? ",          "What does <TERM> <NOT> give rise to?",
            "What is <TERM> <NOT> responsible for?",    "What is <NOT> caused by <TERM>?",
            "What is <NOT> due to <TERM>?",             "What is <NOT> a result of <TERM>?",
            "What is <NOT> because of <TERM>?",         "What is <NOT> brought about by <TERM>?",
            "What is <NOT> triggered by <TERM>?",       "What is <NOT> provoked by <TERM>?",
            "What is <NOT> induced by <TERM>?",         "What is <NOT> produced by <TERM>?",
            "What is <NOT> prompted by <TERM>?",        "What <DOES NOT> stem<S> from <TERM>?",
            "What <DOES NOT> arise<S> from <TERM>?",    "What <DOES NOT> originate<S> from <TERM>?",
            "What is <NOT> driven by <TERM>?",          "What is <NOT> attributable to <TERM>?",
            "What can<NOT>  be traced back to <TERM>?"
    };

    public static String[] phrasesQuestionCausesTerm = {
            "Why does <TERM> <NOT> occur?",            "What causes <TERM> to <NOT> occur?",
            "What is <NOT> the cause of <TERM>?",      "Where does <TERM> <NOT> come from?",
            "What is <NOT> the source of <TERM>?",     "How does <TERM> <NOT> occur?",
            "How does <TERM> <NOT> happen?",           "How is <TERM> <NOT> caused?",
            "How does <TERM> <NOT> come about?",       "What <DOES NOT> cause<S> <TERM>?",
            "What <DOES NOT> lead<S> to <TERM>?",      "What <DOES NOT> result<S> in <TERM>?",
            "What <DOES NOT> bring<S> about <TERM>?",  "What <DOES NOT> trigger<S> <TERM>?",
            "What <DOES NOT> provoke<S> <TERM>?",      "What <DOES NOT> induce<S> <TERM>?",
            "What <DOES NOT> produce<S> <TERM>?",      "What <DOES NOT> prompt<S> <TERM>?",
            "What <DOES NOT> give<S> rise to <TERM>?", "What is <NOT> responsible for <TERM>?",
            "What is <TERM> <NOT> caused by?",         "What is <TERM> <NOT> due to?",
            "What is <TERM> <NOT> a result of?",       "What is <TERM> <NOT> because of?",
            "What is <TERM> <NOT> brought about by?",  "What is <TERM> <NOT> triggered by?",
            "What is <TERM> <NOT> provoked by?",       "What is <TERM> <NOT> induced by?",
            "What is <TERM> <NOT> produced by?",       "What is <TERM> <NOT> prompted by?",
            "What does <TERM> <NOT> stem<S> from?",    "What does <TERM> <NOT> arise<S> from?",
            "What does <TERM> <NOT> originate<S> from?","What is <TERM> <NOT> driven by?",
            "What is <TERM> <NOT> attributable to?",   "What can <TERM> <NOT> be traced back to?"
    };

    public static String[] phrasesQuestionTermCausesTerm = {
            "Does <TERM> <NOT> cause <RESULT>?",      "Is it true that <TERM> <DOES NOT> cause<S> <RESULT>?",
            "Does <TERM> <NOT> lead to <RESULT>?",    "Is it true that <TERM> <DOES NOT> lead<S> to <RESULT>?",
            "Does <TERM> <NOT> result in <RESULT>?",  "Is it true that <TERM> <DOES NOT> result<S> in <RESULT>?",
            "Does <TERM> <NOT> bring about <RESULT>?","Is it true that <TERM> <DOES NOT> bring<S> about <RESULT>?",
            "Does <TERM> <NOT> trigger <RESULT>?",    "Is it true that <TERM> <DOES NOT> trigger<S> <RESULT>?",
            "Does <TERM> <NOT> provoke <RESULT>?",    "Is it true that <TERM> <DOES NOT> provoke<S> <RESULT>?",
            "Does <TERM> <NOT> induce <RESULT>?",     "Is it true that <TERM> <DOES NOT> induce<S> <RESULT>?",
            "Does <TERM> <NOT> produce <RESULT>?",    "Is it true that <TERM> <DOES NOT> produce<S> <RESULT>?",
            "Does <TERM> <NOT> prompt <RESULT>?",     "Is it true that <TERM> <DOES NOT> prompt<S> <RESULT>?",
            "Does <TERM> <NOT> give rise to <RESULT>?","Is it true that <TERM> <DOES NOT> give<S> rise to <RESULT>?",
            "Is <TERM> <NOT> responsible for <RESULT>?", "Is it true that <TERM> is <NOT> responsible for <RESULT>?"
    };

    public static String[] phrasesQuestionTermCausedByTerm = {
            "Is <RESULT> <NOT> caused by <TERM>?",            "Is <RESULT> <NOT> due to <TERM>?",
            "Is <RESULT> <NOT> a result of <TERM>?",          "Is <RESULT> <NOT> because of <TERM>?",
            "Is <RESULT> <NOT> brought about by <TERM>?",     "Is <RESULT> <NOT> triggered by <TERM>?",
            "Is <RESULT> <NOT> provoked by <TERM>?",          "Is <RESULT> <NOT> induced by <TERM>?",
            "Is <RESULT> <NOT> produced by <TERM>?",          "Is <RESULT> <NOT> prompted by <TERM>?",
            "Does <RESULT> <NOT> stem from <TERM>?",          "Does <RESULT> <NOT> arise from <TERM>?",
            "Does <RESULT> <NOT> originate from <TERM>?",     "Is <RESULT> <NOT> driven by <TERM>?",
            "Is <RESULT> <NOT> attributable to <TERM>?",      "Can <RESULT> <NOT> be traced back to <TERM>?"
    };

    /** ***************************************************************
     *   Initiates important variables and objects needed
     *   for cause generation.
     */
    public static void init(String[] args) {
        // parse input variables
        if (args == null || args.length < 3 || args.length > 4 || args[0].equals("-h")) {
            System.out.println("Usage: GenCausesTestData <file prefix> <num to generate> <ollama port number> <optional: -e (for equivalence mappings only)");
            System.exit(0);
        }
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        numToGenerate = Integer.parseInt(args[1]);
        if (args.length == 4 && args[3].equals("-e")) {
            EQUIVALENCE_MAPPINGS = true;
            System.out.println("Using ONLY equivalence mappings");
        }
        else {
            System.out.println("Drawing from equivalence and subsuming mappings.");
        }

        // connect to Ollama
        System.out.println("Connecting to Ollama...");
        String host = "http://localhost:" + args[2] + "/";
        System.out.println("Connecting to " + host);
        ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setVerbose(false);
        options = new OptionsBuilder().setTemperature(1.0f).build();
        System.out.println("Connected.");

        // load the knowledge base
        System.out.println("Loading KBs");
        kbLite = new KBLite("SUMO");
        System.out.println("Finished loading KBs");
        System.out.println("Loading Wordnet");
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
        System.out.println("Wordnet loaded");
        Set<String> allSUMOTermsSet = kbLite.getChildClasses("Process");
        allSUMOTermsRandSet = RandSet.listToEqualPairs(allSUMOTermsSet);
        random = new Random();

        // create output files
        createFileIfDoesNotExists(outputFileEnglish);
        createFileIfDoesNotExists(outputFileLogic);
    }

    /** ***************************************************************
     *   Creates a file if one doesn't exist already.
     */
    public static void createFileIfDoesNotExists(String fileName) {
        Path filePath = Paths.get(fileName);
        if (Files.exists(filePath)) {
            return;
        } else {
            try {
                Files.createFile(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** ***********************************************************************
     *   Takes an enlish phrase and its logic equivalent.
     *   Files are locked, so that if multiple processes are building
     *   the dataset, the correspondence of english/logic pairs is preserved.
     */
    public static void writeEnglishLogicPairToFile(String english, String logic) {
        FileChannel fileChannel1 = null;
        FileChannel fileChannel2 = null;
        FileLock lock1 = null;
        FileLock lock2 = null;
        english = english + "\n";
        logic = logic + "\n";

        try {
            fileChannel1 = FileChannel.open(Paths.get(outputFileEnglish), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            fileChannel2 = FileChannel.open(Paths.get(outputFileLogic), StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            lock1 = fileChannel1.lock();
            lock2 = fileChannel2.lock();

            ByteBuffer buffer1 = ByteBuffer.wrap(english.getBytes());
            ByteBuffer buffer2 = ByteBuffer.wrap(logic.getBytes());

            fileChannel1.write(buffer1);
            fileChannel2.write(buffer2);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (lock1 != null) lock1.release();
                if (fileChannel1 != null) fileChannel1.close();
                if (lock2 != null) lock2.release();
                if (fileChannel2 != null) fileChannel2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        sentenceGeneratedCounter++;
    }

    /** ***********************************************************************
     *   Ask Ollama if articles are valid in a particular sentence.
     */
    public static boolean areArticlesValidOllama(String englishSentenceWithArticles) throws Exception {
        String prompt = "Just the response. In a single word, are the grammer articles in this sentence used correctly: '" + englishSentenceWithArticles + "'";
        if (debug) System.out.println("Articles Valid Prompt: " + prompt);
        OllamaResult result =
                ollamaAPI.generate("llama3.2", prompt, RAW_PROMPT, options);

        if (debug) System.out.println("Ollama, are the grammer articles in this sentence used correctly returns: " + result.getResponse());
        String response = result.getResponse();
        if (response != null) {
            if (response.length() >= 5) {
                response = response.substring(0, 5);
            }
            response = response.toLowerCase();
            return response.contains("yes");
        }
        return false;
    }

    /** ***********************************************************************
     *   Ask Ollama for a random process. Prompt changes based on input paramters.
     */
    public static String askOllamaForProcess(String process, boolean negation, boolean SUMOProcessFirst) throws Exception {
        String prompt = "";
        if (negation) {
            if (!SUMOProcessFirst) {
                prompt = "Just the response. In a single word, what is '" + process + "' not caused by?";
            }
            else {
                prompt = "Just the response. In a single word, what does '" + process + "' not cause?";
            }
        }
        else {
            if (!SUMOProcessFirst) {
                prompt = "Just the response. In a single word, what causes '" + process + "'?";
            }
            else {
                prompt = "Just the response. In a single word, what does '" + process + "' cause?";
            }
        }
        if (debug) System.out.println("Prompt: " + prompt);
        OllamaResult result =
                ollamaAPI.generate("llama3.2", prompt, RAW_PROMPT, options);

        if (debug) System.out.println("Ollama returns: " + result.getResponse());
        return cleanOllamaResponse(result.getResponse());
    }

    /** ***************************************************************
     *   Takes a string which is a response to an Ollama query.
     *   Removes all punctuation and splits camel case answers.
     *   @return a clean ollama answer.
     */
    private static String cleanOllamaResponse(String str) {
        StringBuilder result = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isUpperCase(c) && result.length() > 0) {
                result.append(" ");
            }
            result.append(c);
        }
        String sentence = result.toString();
        sentence = Arrays.stream(sentence.split("\\s+"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        List<Character> punctuation = Arrays.asList('.', ',', '!', '?', ';', ':', '-', '(', ')', '[', ']', '{', '}', '"', '\'', ' ');
        sentence = sentence.chars()
                    .mapToObj(c -> (char) c)
                    .filter(c -> !punctuation.contains(c))
                    .map(String::valueOf)
                    .collect(Collectors.joining());
        return sentence;
    }

    /** ***************************************************************
     *   Takes a string, such as "ThisIsAStringWithCapitalLetters"
     *   and breaks it up into "This Is A String With Capital Letters"
     *   @return a string broken up into words with spaces.
     */
    public static String addSpaceBeforeCapitals(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder result = new StringBuilder();
        result.append(input.charAt(0)); // Append the first character as is
        for (int i = 1; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (Character.isUpperCase(currentChar)) {
                result.append(' ');
            }
            result.append(currentChar);
        }
        return result.toString();
    }

    /** **********************************************************************************
     * Given a term, looks up all the mappings of that term
     * in WordNet, and returns a random mapping that is a process.
     * if EQUIVALENCE_MAPPINGS is set to true, then it only returns equivalence mappings.
     * @return a random SUMO Mapping
     */
    private static String getRandomSUMOMapping(String term) {
        Set<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(term.toLowerCase());
        System.out.println("Term: " + term.toLowerCase() + " - Synset: " + synsetOfTerm);
        ArrayList<String> equivalentTerms = new ArrayList();
        int counter = 0;
        for (String synset:synsetOfTerm) {
            if (debug) System.out.println("Synset of " + term + ": " + synset);
            String sumoMapping = WordNet.wn.getSUMOMapping(synset);
            if (sumoMapping != null) {
                sumoMapping = sumoMapping.substring(2);
                if (sumoMapping.charAt(sumoMapping.length() - 1) == '=' || EQUIVALENCE_MAPPINGS == false) {
                    String sumoTerm = sumoMapping.substring(0, sumoMapping.length() - 1);
                    if (debug) System.out.println("Mapping to: " + sumoTerm);
                    if(kbLite.subclassOf(sumoTerm, "Process")) {
                        if (debug) System.out.println(sumoTerm + " is a process. Added.");
                        equivalentTerms.add(sumoMapping.substring(0, sumoMapping.length() - 1));
                    }
                }
            }
        }
        if (!equivalentTerms.isEmpty()) {
            Random rand = new Random();
            return equivalentTerms.get(rand.nextInt(equivalentTerms.size()));
        }
        return null;
    }

    /** ***********************************************************************
     *   Adds articles to the front of processes, to essentially "instantiate"
     *   them.
     */
    public static String addArticlesToSentence(String sentence, String [] processes) {
        sentence = sentence.substring(0, 1).toLowerCase() + sentence.substring(1);
        for (String process : processes) {
            if (debug) System.out.println("Process in add article: " + process);
            char firstChar = Character.toLowerCase(process.charAt(0));
            if (GenSimpTestData.biasedBoolean(1, 2)) {
                sentence = sentence.replace(process, "the " + process);
            }
            else {
                if (firstChar == 'a' || firstChar == 'e' || firstChar == 'i' || firstChar == 'o' || firstChar == 'u' || firstChar == 'y') {
                    sentence = sentence.replace(process, "an " + process);
                }
                else {
                    sentence = sentence.replace(process, "a " + process);
                }
            }
        }
        return sentence.substring(0, 1).toUpperCase() + sentence.substring(1);
    }

    /** *********************************************************************
     *   Takes an ollama response, and sees if there is a good mapping
     *   for the term in SUMO.
     */
    public static String mapResponseToSUMOTerm(String responseOllamaEnglish, String randomSumoProcessEnglish) {
        // Find the word sense disambibuation, aka, the closest mapping to the Ollama response in SUMO.
        /*
        String[] arr = randomSumoProcessEnglish.split("\\s+"); // Splitting by one or more whitespace characters
        ArrayList<String> processSplitIntoWords = new ArrayList<>(Arrays.asList(arr));
        processSplitIntoWords.add("causes");
        String responseOllamaWNSynset = WSD.findWordSenseInContext(responseOllamaEnglish, processSplitIntoWords);

        String responseInSumo = null;
        if (responseOllamaWNSynset != null) {
            responseInSumo = WordNet.wn.getSUMOMapping(responseOllamaWNSynset);
            if (responseInSumo != null) {
                responseInSumo = responseInSumo.substring(2, responseInSumo.length() - 1);
                // The resulting term must be a process. If its not, then just choose a random process that maps to the term.
                if (!kbLite.subclassOf(responseInSumo, "Process")) {
                    responseInSumo = getRandomSUMOMapping(responseOllamaEnglish);
                }
            }
        }
        return responseInSumo;
        */
        Set<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(responseOllamaEnglish.toLowerCase());
        return GenUtils.getBestSUMOMapping(synsetOfTerm);
    }


    /** *********************************************************************
     *   Generate a question with a SUMOProcess of the form:
     *      "What does <TERM> <NOT> cause?"
     *      "What <DOES NOT> cause<S> <TERM>?"
     *         also
     *      "What does <ARTICLE> <TERM> <NOT> cause?
     */
    public static void generateQuestionTermCauses(boolean negation, boolean SUMOProcessFirst, String randomSumoProcess, String randomSumoProcessEnglish) throws Exception {
        String notPhrase = negation ? "not " : "";
        String doesNotPhrase = negation ? "does not " : "";
        String sPhrase = negation ? "" : "s";
        if (SUMOProcessFirst) { // Questions of the form "What does <TERM> <NOT> cause?"
            int randomIndex = random.nextInt(phrasesQuestionTermCauses.length);
            englishSentence = phrasesQuestionTermCauses[randomIndex];
            logicPhrase = "(causesSubclass " + randomSumoProcess + " ?X)";
        }
        else { // Questions of the form: "What <DOES NOT> cause<S> <TERM>?"
            int randomIndex = random.nextInt(phrasesQuestionCausesTerm.length);
            englishSentence = phrasesQuestionCausesTerm[randomIndex];
            logicPhrase = "(causesSubclass ?X " + randomSumoProcess + ")";
        }
        englishSentence = englishSentence
                .replace("<TERM>", randomSumoProcessEnglish)
                .replace("<DOES NOT> ", doesNotPhrase)
                .replace("<NOT> ", notPhrase)
                .replace("<S>", sPhrase);
        if (negation) {
            logicPhrase = "(not " + logicPhrase + ")";
        }
        writeEnglishLogicPairToFile(englishSentence, logicPhrase);

        // Add question with articles
        String[] processes = {randomSumoProcessEnglish};
        englishSentence = addArticlesToSentence(englishSentence, processes);
        if (SUMOProcessFirst) {
            logicPhrase = "(exists (?X1 ?X2) (and (instance ?X1 " + randomSumoProcess + ") (causes ?X1 ?X2)))";
        }
        else {
            logicPhrase = "(exists (?X1 ?X2) (and (instance ?X1 " + randomSumoProcess + ") (causes ?X2 ?X1)))";
        }
        if (negation) {
            logicPhrase = "(not " + logicPhrase + ")";
        }
        writeEnglishLogicPairToFile(englishSentence, logicPhrase);
    }


    /** *********************************************************************
     * Main method. Builds a test set of the form
     *   "<term> causes <term>"
     *   "<term> does not cause <term>"
     *   "<term> is caused by <term>"
     *        or
     *   "<term> is not caused by <term>
     * and its logical equivalent.
     * First, selects a random process from SUMO.
     * Then asks ollama what is caused by that process.
     */
    public static void main(String[] args) throws Exception {
        init(args);
        System.out.println("Finished initialization, beginning sentence generation.");
        sentenceGeneratedCounter = 0;
        while (sentenceGeneratedCounter < numToGenerate) {
            // get a random SUMO Process
            if (debug) System.out.println("\n");
            String randomSumoProcess = allSUMOTermsRandSet.getNext();
            String randomSumoProcessEnglish = kbLite.getTermFormat("EnglishLanguage", randomSumoProcess);
            if (randomSumoProcessEnglish == null) {
                randomSumoProcessEnglish = addSpaceBeforeCapitals((randomSumoProcess));
            }
            if (debug) System.out.println("Random SUMO Process: " + randomSumoProcess);
            boolean negation = GenSimpTestData.biasedBoolean(1, 2);
            boolean SUMOProcessFirst = GenSimpTestData.biasedBoolean(1, 2);
            boolean generateQuestionTermCauses = GenSimpTestData.biasedBoolean(1, 15);
            boolean generateQuestionTermCausesTerm = GenSimpTestData.biasedBoolean(1, 15);

            if (generateQuestionTermCauses && sentenceGeneratedCounter < numToGenerate+2) {
                generateQuestionTermCauses(negation, SUMOProcessFirst, randomSumoProcess, randomSumoProcessEnglish);
            }

            // Get a related process from Ollama
            String responseOllamaEnglish = askOllamaForProcess(randomSumoProcessEnglish, negation, SUMOProcessFirst);
            String responseInSumo = mapResponseToSUMOTerm(responseOllamaEnglish, randomSumoProcessEnglish);


            if (responseInSumo != null) {
                String causingProcess = (SUMOProcessFirst) ? randomSumoProcessEnglish : responseOllamaEnglish.toLowerCase();
                String causingProcessLog = (SUMOProcessFirst) ? randomSumoProcess : responseInSumo;

                String resultProcess = (SUMOProcessFirst) ? responseOllamaEnglish.toLowerCase() : randomSumoProcessEnglish;
                String resultProcessLog = (SUMOProcessFirst) ? responseInSumo : randomSumoProcess;
                if (debug) System.out.println("Causing Process: " + causingProcess);
                if (debug) System.out.println("Result  Process: " + resultProcess);
                if (GenSimpTestData.biasedBoolean(1, 2)) {
                    int randomIndex = random.nextInt(phrasesCauses.length);
                    String causePhrase = negation ? phrasesNotCauses[randomIndex] : phrasesCauses[randomIndex] ;
                    englishSentence = causingProcess + causePhrase + resultProcess + ".";
                }
                else {
                    int randomIndex = random.nextInt(phrasesCausedBy.length);
                    String causedByPhrase = negation ? phrasesNotCausedBy[randomIndex] : phrasesCausedBy[randomIndex];
                    englishSentence = resultProcess + causedByPhrase + causingProcess + ".";
                }
                char firstChar = Character.toUpperCase(englishSentence.charAt(0));
                String remainingChars = englishSentence.substring(1).toLowerCase();
                englishSentence = firstChar + remainingChars;
                logicPhrase = "(causesSubclass " + causingProcessLog + " " + resultProcessLog + ")";
                if (negation) {
                    logicPhrase = "(not " + logicPhrase + ")";
                }
                if (debug) System.out.println("Resulting English sentence: '" + englishSentence + "'");
                if (debug) System.out.println("Resulting logic: '" + logicPhrase + "'");
                writeEnglishLogicPairToFile(englishSentence, logicPhrase);

                // Form question of form "Is it true that <TERM> <DOES NOT> cause<S> <RESULT>?" or "Does <TERM> <NOT> cause <RESULT>?"
                if (generateQuestionTermCausesTerm && sentenceGeneratedCounter < numToGenerate) {
                    if (GenSimpTestData.biasedBoolean(1, 2)) {
                        int randomIndex = random.nextInt(phrasesQuestionTermCausesTerm.length);
                        englishSentenceQuestion = phrasesQuestionTermCausesTerm[randomIndex];
                    }
                    else {
                        int randomIndex = random.nextInt(phrasesQuestionTermCausedByTerm.length);
                        englishSentenceQuestion = phrasesQuestionTermCausedByTerm[randomIndex];
                    }
                    englishSentenceQuestion = englishSentenceQuestion.replace("<TERM>", causingProcess)
                            .replace("<DOES NOT> ", (negation) ? "does not " : "")
                            .replace("<NOT> ", (negation) ? "not " : "")
                            .replace("<S>", (negation) ? "" : "s")
                            .replace("<RESULT>", resultProcess);
                    writeEnglishLogicPairToFile(englishSentenceQuestion, logicPhrase);
                }

                // Add articles to the sentence
                if (sentenceGeneratedCounter < numToGenerate) {
                    String[] processes = {causingProcess, resultProcess};
                    englishSentenceWithArticles = addArticlesToSentence(englishSentence, processes);

                    articlesAreValid = areArticlesValidOllama(englishSentenceWithArticles);
                    if (articlesAreValid) {
                        // Convert to uppercase
                        String causingVariableName = causingProcessLog.toUpperCase();
                        String resultingVariableName = resultProcessLog.toUpperCase();
                        // Remove all white space
                        causingVariableName = causingVariableName.replaceAll("\\s+", "");
                        resultingVariableName = resultingVariableName.replaceAll("\\s+", "");

                        logicPhraseWithArticles = "(exists (?"+causingVariableName+" ?"+resultingVariableName+") (and (instance ?"+causingVariableName+" "+ causingProcessLog+") (instance ?"+resultingVariableName+" " + resultProcessLog + ") (causes ?"+causingVariableName+" ?"+resultingVariableName+")))";
                        if (negation) {
                            logicPhraseWithArticles = "(not " + logicPhraseWithArticles + ")";
                        }
                        writeEnglishLogicPairToFile(englishSentenceWithArticles, logicPhraseWithArticles);

                        // generate Question with articles.
                        if (generateQuestionTermCausesTerm && sentenceGeneratedCounter < numToGenerate) {
                            englishSentenceWithArticles = addArticlesToSentence(englishSentenceQuestion, processes);
                            writeEnglishLogicPairToFile(englishSentenceWithArticles, logicPhraseWithArticles);
                        }
                    }
                }

            }
            else {
                if (debug) System.out.println("No related process for: " + responseOllamaEnglish);
            }
        }
    }
}