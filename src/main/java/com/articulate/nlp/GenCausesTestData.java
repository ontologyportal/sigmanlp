package com.articulate.nlp;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;

import java.io.FileWriter;
import java.util.Collection;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
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

public class GenCausesTestData {

    public static boolean debug = false;
    public static KB kb;
    public static String outputFileEnglish = "causes-eng.txt";
    public static String outputFileLogic = "causes-log.txt";
    public static boolean EQUIVALENCE_MAPPINGS = false;

    public static String[] phrasesCauses = {
            " causes ",
            " leads to ",
            " results in ",
            " brings about ",
            " triggers ",
            " provokes ",
            " induces ",
            " produces ",
            " prompts ",
            " gives rise to ",
            " is responsible for "
    };

    public static String[] phrasesCausedBy = {
            " is caused by ",
            " is due to ",
            " is a result of ",
            " is because of ",
            " is brought about by ",
            " is triggered by ",
            " is provoked by ",
            " is induced by ",
            " is produced by ",
            " is prompted by ",
            " stems from ",
            " arises from ",
            " originates from ",
            " is driven by ",
            " is attributable to ",
            " can be traced back to "
    };

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

    /** ***************************************************************
     * Given a term, looks up all the equivalent mappings of that therm
     * in WordNet, and returns a random mapping.
     * @return a random equivalent SUMO Mapping
     */
    private static String getEquivalentSUMOMapping(String term) {
        Set<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(term.toLowerCase());
        ArrayList<String> equivalentTerms = new ArrayList();
        int counter = 0;
        for (String synset:synsetOfTerm) {
            if (debug) System.out.println("Synset of " + term + ": " + synset);
            String sumoMapping = WordNet.wn.getSUMOMapping(synset);
            if (sumoMapping != null) {
                sumoMapping = sumoMapping.substring(2);
                if (sumoMapping.charAt(sumoMapping.length() - 1) == '=' || EQUIVALENCE_MAPPINGS == false) {
                    String sumoTerm = sumoMapping.substring(0, sumoMapping.length() - 1);
                    if (debug) System.out.println("Equivalent mapping to: " + sumoTerm);
                    if(kb.kbCache.subclassOf(sumoTerm, "Process")) {
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

    /** *********************************************************************
     * Main method. Builds a test set of the form "<term> causes <term>"
     * and its logical equivalent.
     * First, selects a random process from SUMO.
     * Then asks ollama what is caused by that process.
     */
    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 2 || args.length > 3 || args[0].equals("-h"))
            System.out.println("Usage: GenCausesTestData <file prefix> <num to generate> <optional: -e (for equivalence mappings only)");
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        int numToGenerate = Integer.parseInt(args[1]);
        if (args.length == 3 && args[2].equals("-e")) {
            EQUIVALENCE_MAPPINGS = true;
            System.out.println("Using ONLY equivalence mappings");
        }
        else {
            System.out.println("Drawing from equivalence and subsuming mappings.");
        }

        OllamaAPI ollamaAPI = new OllamaAPI();
        ollamaAPI.setVerbose(false);
        boolean RAW_PROMPT = false;
        Options options = new OptionsBuilder().setTemperature(1.0f).build();

        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        System.out.println("Finished loading KBs");
        Set<String> allSUMOTermsSet = kb.kbCache.getChildClasses("Process");
        RandSet allSUMOTermsRandSet = RandSet.listToEqualPairs(allSUMOTermsSet);
        
        createFileIfDoesNotExists(outputFileEnglish);
        createFileIfDoesNotExists(outputFileLogic);
        Random random = new Random();
        String englishSentence;

        int sentenceGeneratedCounter = 0;
        while (sentenceGeneratedCounter < numToGenerate) {
            if (debug) System.out.println("\n");
            String randomSumoProcess = allSUMOTermsRandSet.getNext();
            String randomSumoProcessEnglish = kb.getTermFormat("EnglishLanguage", randomSumoProcess);
            if (randomSumoProcessEnglish == null) {
                randomSumoProcessEnglish = addSpaceBeforeCapitals((randomSumoProcess));
            }
            if (debug) System.out.println("Random SUMO Process: " + randomSumoProcess);
            String prompt = "Just the response. In a single word, what does '" + randomSumoProcessEnglish + "' cause?";

            OllamaResult result =
                    ollamaAPI.generate("llama3.2", prompt, RAW_PROMPT, options);

            if (debug) System.out.println("Ollama returns: " + result.getResponse());
            String responseOllamaEnglish = cleanOllamaResponse(result.getResponse());
            String responseInSumo = getEquivalentSUMOMapping(responseOllamaEnglish);
            
            if (responseInSumo != null) {
                if (random.nextBoolean()) {
                    int randomIndex = random.nextInt(phrasesCauses.length);
                    englishSentence = randomSumoProcessEnglish + phrasesCauses[randomIndex] + responseOllamaEnglish.toLowerCase() + ".\n";
                }
                else {
                    int randomIndex = random.nextInt(phrasesCausedBy.length);
                    englishSentence = responseOllamaEnglish.toLowerCase() + phrasesCausedBy[randomIndex] + randomSumoProcessEnglish + ".\n";
                }
                char firstChar = Character.toUpperCase(englishSentence.charAt(0));
                String remainingChars = englishSentence.substring(1).toLowerCase();
                englishSentence = firstChar + remainingChars;
                String logicPhrase = "( exists ( ?V1 ?V2)  (and (instance ?V1 " + randomSumoProcess + " ) "
                                        + " (instance ?V2 " + responseInSumo + " ) "
                                        + " (causesSubclass ?V1 ?V2) ) )\n";
                if (debug) System.out.println("Resulting English sentence: '" + englishSentence + "'");
                if (debug) System.out.println("Resulting logic: '" + logicPhrase + "'");
                writeEnglishLogicPairToFile(englishSentence, logicPhrase);
                sentenceGeneratedCounter++;
                if (sentenceGeneratedCounter % 100 == 0) {
                    System.out.print("...." + sentenceGeneratedCounter);
                }
            }
            else {
                if (debug) System.out.println("No related process for: " + result.getResponse());
            }
        }
    }
}