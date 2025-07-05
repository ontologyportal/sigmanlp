package com.articulate.nlp;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;


public class GenAdjectivesOllama {

    public static boolean debug = false;
    public static KBLite kbLite;
    public static String outputFileEnglish = "adjectives-eng.txt";
    public static String outputFileLogic = "adjectives-log.txt";
    public static String englishSentence;
    public static String englishSentenceQuestion;
    public static String englishSentenceWithArticles;
    public static String logicPhrase;
    public static String logicPhraseWithArticles;
    public static int numToGenerate;

    public static GenSimpTestData gstd = new GenSimpTestData();
    public static RandSet emoState = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("EmotionalState"));
    public static RandSet consc = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("ConsciousnessAttribute"));
    public static RandSet disease = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("DiseaseOrSyndrome"));
    public static RandSet socialRoles = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("SocialRole"));
    public static RandSet humanNames;

    /** ***************************************************************
     *   Initiates important variables and objects needed
     *   for adjective generation.
     */
    public static void init(String[] args) {
        // parse input variables
        if (args == null || args.length != 3 || args[0].equals("-h")) {
            System.out.println("Usage: GenAdjectivesOllama <file prefix> <num to generate> <ollama port number>");
            System.exit(0);
        }
        numToGenerate = Integer.parseInt(args[1]);
        GenUtils.startOllamaServer(Integer.parseInt(args[2]));
        String ollamaResponse = GenUtils.askOllama("You are a computer that only knows JSON format talking to another computer that only understands JSON format. Give me 100 human names in JSON format, split into unique first name and unique last name. Use this format: {\"names\": [{\"first\": \"<name>\", \"last\": \"<name>\"},{\"first\": \"<name>\", \"last\": \"<name>\"}, etc.");
    }


    /** ***************************************************************
     * init and call main routine.
     */
    public static void main(String args[]) {
        System.out.println("Generating synthetic sentences with adjectives.");
        init(args);
        int generated = 0;
        while (generated < numToGenerate) {
            LFeatures lfeat = new LFeatures();
            lfeat.tense = gstd.rand.nextInt(3) * 2; // PAST = 0; PRESENT = 2; FUTURE = 4
            lfeat.negatedBody = gstd.rand.nextBoolean();
            // Subject is either a role (e.g. nurse) or a named human (e.g. "Bill").
            /*if (gstd.rand.nextInt(2)) { // Choose role

            } else { // Choose name

            }*/
            StringBuilder english = new StringBuilder();
            StringBuilder prop = new StringBuilder();

            gstd.generateHumanSubject(lfeat);
            generated++;
        }
        System.out.println(GenUtils.askOllama("You are a helpful assistant. Always answer directly and do not use <think></think> tags. Take the following parts of speech and turn it into a coherent sentence. Use \"to be\" or \"to have\", and make the negative version of the sentence. {\"subject\": \"Carol\",\"attribute\":\"nurse\"}"));
    }
}