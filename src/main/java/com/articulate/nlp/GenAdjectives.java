package com.articulate.nlp;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

public class GenAdjectives {

    public static GenSimpTestData gstd = new GenSimpTestData();
    public static RandSet emoState = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("EmotionalState"));
    public static RandSet consc = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("ConsciousnessAttribute"));
    public static RandSet disease = RandSet.listToEqualPairs(GenSimpTestData.kb.kbCache.getInstancesForType("DiseaseOrSyndrome"));
    enum Lists {EMO, CONSC, ILL};

    /** ***************************************************************
     */
    public static void init() {
    }

    /** ***************************************************************
     */
    public static void createIs(){

        Lists val = Lists.EMO;
        for (int i = 0; i < gstd.sentMax; i++) {
            StringBuilder english = new StringBuilder();
            StringBuilder prop = new StringBuilder();
            prop.append("(exists (?H) (and ");
            LFeatures lfeat = new LFeatures(gstd);
            lfeat.frame = WordNet.wn.VerbFrames.get(7);
            lfeat.framePart = WordNet.wn.VerbFrames.get(7);
            gstd.generateHumanSubject(english, prop, lfeat);
            String term = "";
            if (gstd.biasedBoolean(1,3)) {
                term = emoState.getNext();
                val = Lists.EMO;
            }
            else if (gstd.biasedBoolean(1,3)) {
                term = consc.getNext();
                val = Lists.CONSC;
            }
            else {
                term = disease.getNext();
                val = Lists.ILL;
            }
            String word = gstd.getTermFormat(term);
            if (val == Lists.CONSC)
                english.append("is " + word);
            else
                english.append("has " + word);
            if (lfeat.question)
                english.append("?");
            else
                english.append(".");
            gstd.capitalize(english);
            gstd.englishFile.println(english);
            prop.append("(attribute ?H " + term + ")))");
            gstd.logicFile.println(prop);
            gstd.frameFile.println(lfeat);
        }
    }

    /** ***************************************************************
     */
    public static void createHas() {

    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Sentence generation");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -s - generate sentences");
    }

    /** ***************************************************************
     * init and call main routine.
     */
    public static void main(String args[]) {

        KBmanager.prefOverride.put("TPTP","no");
        try {
            if (args == null || args.length == 0 || args[0].equals("-h"))
                showHelp();
            if (args.length > 2)
                gstd.sentMax = Integer.parseInt(args[2]);
            if (args.length > 1) {
                FileWriter fweng;
                FileWriter fwlog;
                FileWriter fwframe;
                fweng = new FileWriter(args[1] + "-eng.txt");
                gstd.englishFile = new PrintWriter(fweng);
                fwlog = new FileWriter(args[1] + "-log.txt");
                gstd.logicFile = new PrintWriter(fwlog);
                fwframe = new FileWriter(args[1] + "-frame.txt");
                gstd.frameFile = new PrintWriter(fwframe);
                createIs();
                createHas();
                gstd.englishFile.close();
                gstd.logicFile.close();
                gstd.frameFile.close();
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }
}
