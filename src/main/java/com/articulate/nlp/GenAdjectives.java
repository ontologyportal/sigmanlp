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
    public static String makeLogTense(LFeatures lfeat) {

        if (lfeat.isPast())
            return "(before ?T Now) ";
        else if (lfeat.isFuture())
            return "(before Now ?T) ";
        else // (lfeat.isPresent())
            return "(equal ?T Now) ";
    }

    /** ***************************************************************
     */
    public static String conjugateToBe(LFeatures lfeat) {

        String verb = "";
        String neg = " ";
        if (lfeat.negatedBody) {
            if (gstd.rand.nextBoolean())
                neg = " not ";
            else
                neg = "n't ";
        }
        if (lfeat.isPast())
            verb = "was" + neg;
        if (lfeat.isPresent())
            verb = "is" + neg;
        if (lfeat.isFuture()) {
            if (lfeat.negatedBody) {
                if (neg.equals("n't "))
                    verb = "won't be ";
                else
                    verb = "will not be ";
            }
            else {
                verb = "will be ";
            }
        }
        return verb;
    }

    /** ***************************************************************
     */
    public static  String conjugateToHave(LFeatures lfeat) {

        String verb = "";
        String neg = " ";
        if (lfeat.negatedBody) {
            if (gstd.rand.nextBoolean())
                neg = " not ";
            else
                neg = "n't ";
        }
        if (lfeat.isPast()) {
            if (lfeat.negatedBody)
                verb = "did" + neg + "have ";
            else
                verb = "has ";
        }
        if (lfeat.isPresent()) {
            if (lfeat.negatedBody)
                verb = "does" + neg + "have ";
            else
                verb = "has ";
        }
        if (lfeat.isFuture()) {
            if (lfeat.negatedBody) {
                if (neg.equals("n't "))
                    verb = "won't have ";
                else
                    verb = "will not have ";
            }
            else {
                verb = "will have ";
            }
        }
        return verb;
    }

    /** ***************************************************************
     */
    public static String conjugateToFeel(LFeatures lfeat) {

        String verb = "";
        String neg = " ";
        if (lfeat.negatedBody) {
            if (gstd.rand.nextBoolean())
                neg = " not ";
            else
                neg = "n't ";
        }
        if (lfeat.isPast()) {
            if (lfeat.negatedBody)
                verb = "did" + neg + "feel ";
            else
                verb = "felt ";
        }
        if (lfeat.isPresent()) {
            if (lfeat.negatedBody)
                verb = "does" + neg + "have ";
            else
                verb = "has ";
        }
        if (lfeat.isFuture()) {
            if (lfeat.negatedBody) {
                if (neg.equals("n't "))
                    verb = "won't have ";
                else
                    verb = "will not have ";
            }
            else {
                verb = "will have ";
            }
        }
        return verb;
    }

    /** ***************************************************************
     */
    public static void createIs(){

        boolean useTense = true;
        Lists val = Lists.EMO;
        for (int i = 0; i < gstd.sentMax; i++) {
            LFeatures lfeat = new LFeatures();
            lfeat.tense = gstd.rand.nextInt(3) * 2; // PAST = 0; PRESENT = 2; FUTURE = 4
            lfeat.negatedBody = gstd.rand.nextBoolean();

            StringBuilder english = new StringBuilder();
            StringBuilder prop = new StringBuilder();
            if (lfeat.negatedBody)
                prop.append("(not ");
            prop.append("(exists (?H) (and ");

            lfeat.frame = WordNet.wn.VerbFrames.get(7);
            lfeat.framePart = WordNet.wn.VerbFrames.get(7);
            gstd.generateHumanSubject(english, prop, lfeat);
            String term = "";
            if (gstd.biasedBoolean(1, 3)) {
                term = emoState.getNext();
                val = Lists.EMO;
            } else if (gstd.biasedBoolean(1, 3)) {
                term = consc.getNext();
                val = Lists.CONSC;
            } else {
                term = disease.getNext();
                val = Lists.ILL;
            }
            String word = gstd.getTermFormat(term);
            String verb = "";
            if (val == Lists.CONSC)
                verb = conjugateToBe(lfeat);
            else if (val == Lists.ILL)
                verb = conjugateToHave(lfeat);
            else if (val == Lists.EMO)
                verb = conjugateToFeel(lfeat);

            english.append(verb + word);
            if (lfeat.question)
                english.append("?");
            else
                english.append(".");
            gstd.capitalize(english);
            gstd.englishFile.println(english);
            if (useTense) {
                prop.append(makeLogTense(lfeat));
                prop.append("(holdsDuring ?T ");
            }
            prop.append("(attribute ?H " + term + ")))");
            if (useTense)
                prop.append(")");
            if (lfeat.negatedBody)
                prop.append(")");
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
