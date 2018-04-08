package com.articulate.nlp.corpora;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semRewrite.RHS;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.SimpleDOMParser;
import com.articulate.sigma.SimpleElement;
import com.articulate.sigma.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 process https://cs.nyu.edu/faculty/davise/papers/WinogradSchemas/WSCollection.xml
 */
public class WGschema {

    public static boolean debug = true;

    public HashMap<Integer,Sent> sentIndex = new HashMap<>();

    public Interpreter interp = null;

    /***************************************************************
     */
    public class Sent  {

        public String toString() {
            return (text1 + "\t" + pronoun + "\t" + text2 + "\t" + answers + "\n");
        }

        public String text1;
        public String pronoun;
        public String text2;
        public String quote1;
        public String pronAgain;
        public String quote2;
        public String sentence;
        public ArrayList<String> answers = new ArrayList<String>();
        public String correctAnswer;
        public String source;
    }

    /***************************************************************
     */
    public static ArrayList<Sent> sentences = new ArrayList<>();

    /***************************************************************
     * see https://en.wikipedia.org/wiki/F1_score for explanation
     */
    public class F1Matrix {

        public int falseNegatives = 0;
        public int truePositives = 0;
        public int trueNegatives = 0;
        public int falsePositives = 0;

        public float precision() { return (float)truePositives/((float)truePositives + (float)falsePositives); }
        public float recall() { return (float)truePositives/((float)falseNegatives + (float)truePositives); }
        public float fOne() { return 2 * (precision() * recall()) / (precision() + recall()); }

        public F1Matrix add(F1Matrix two) {
            F1Matrix result = new F1Matrix();
            result.falseNegatives = falseNegatives + two.falseNegatives;
            result.truePositives  = truePositives +  two.truePositives;
            result.trueNegatives  = trueNegatives +  two.trueNegatives;
            result.falsePositives = falsePositives + two.falsePositives;
            return result;
        }

        public String toString() {
            return ("fn: " + falseNegatives + " tp: " + truePositives + " tn: " + trueNegatives + " fp: " + falsePositives);
        }
    }

    /***************************************************************
     */
    private void parseText(SimpleElement text, Sent s) {

        ArrayList<SimpleElement> children = text.getChildElements();
        if (children.size() != 3) {
            System.out.println("Error in WGschema.parseText(): wrong number of elements in " + children);
            return;
        }
        SimpleElement txt1 = children.get(0);
        if (!txt1.getTagName().startsWith("txt1")) {
            System.out.println("Error in WGschema.parseText(): bad element " + txt1);
            System.out.println("Error in WGschema.parseText(): expected <txt1> ");
            return;
        }
        s.text1 = txt1.getText();
        SimpleElement pron = children.get(1);
        if (!pron.getTagName().startsWith("pron")) {
            System.out.println("Error in WGschema.parseText(): bad element " + pron);
            System.out.println("Error in WGschema.parseText(): expected <pron> ");
            return;
        }
        s.pronoun = pron.getText();
        SimpleElement txt2 = children.get(2);
        if (!txt2.getTagName().startsWith("txt2")) {
            System.out.println("Error in WGschema.parseText(): bad element " + txt2);
            System.out.println("Error in WGschema.parseText(): expected <txt2> ");
            return;
        }
        s.text2 = txt2.getText();
        s.sentence = txt1.getText() + pron.getText() + txt2.getText();
    }

    /***************************************************************
     */
    private void parseQuote(SimpleElement quote, Sent s) {

        for (SimpleElement se : quote.getChildElements()) {
            if (se.getTagName().startsWith("quote1"))
                s.quote1 = se.getText();
            if (se.getTagName().startsWith("pron"))
                s.pronAgain = se.getText();
            if (se.getTagName().startsWith("quote2"))
                s.quote2 = se.getText();
        }
    }

    /***************************************************************
     */
    private void parseAnswers(SimpleElement quote, Sent s) {

        for (SimpleElement se : quote.getChildElements()) {
            if (se.getTagName().startsWith("answer"))
                s.answers.add(se.getText());
            else {
                System.out.println("Error in WGschema.parseAnswers(): bad element " + se);
                System.out.println("Error in WGschema.parseAnswers(): expected <answer> ");
                return;
            }
        }
    }

    /***************************************************************
     * Parse one test of the WGSchema corpus into Sent.  Should have a
     * <text>, <quote>, <answers> <correctAnswer> and <source> as child
     * level elements
     */
    public void parseTest(SimpleElement sec) {

        Sent s = new Sent();
        ArrayList<SimpleElement> children = sec.getChildElements();
        if (children.size() != 5) {
            System.out.println("Error in WGschema.parseTest(): wrong number of elements in " + children);
            return;
        }
        SimpleElement text = children.get(0);
        if (!text.getTagName().startsWith("text")) {
            System.out.println("Error in WGschema.parseTest(): bad element " + text);
            System.out.println("Error in WGschema.parseTest(): expected <textuote> ");
            return;
        }
        parseText(text,s);
        SimpleElement quote = children.get(1);
        if (!quote.getTagName().startsWith("quote")) {
            System.out.println("Error in WGschema.parseTest(): bad element " + quote);
            System.out.println("Error in WGschema.parseTest(): expected <quote> ");
            return;
        }
        parseQuote(quote,s);
        SimpleElement answers = children.get(2);
        if (!answers.getTagName().startsWith("answers")) {
            System.out.println("Error in WGschema.parseTest(): bad element " + answers);
            System.out.println("Error in WGschema.parseTest(): expected <answers> ");
            return;
        }
        parseAnswers(answers,s);
        SimpleElement correctAnswer = children.get(3);
        if (!correctAnswer.getTagName().startsWith("correctAnswer")) {
            System.out.println("Error in WGschema.parseTest(): bad element " + correctAnswer);
            System.out.println("Error in WGschema.parseTest(): expected <correctAnswer> ");
            return;
        }
        s.correctAnswer = correctAnswer.getText().trim();
        SimpleElement source = children.get(4);
        if (!source.getTagName().startsWith("source")) {
            System.out.println("Error in WGschema.parseTest(): bad element " + source);
            System.out.println("Error in WGschema.parseTest(): expected <source> ");
            return;
        }
        s.source = source.getText().trim();
        sentences.add(s);
    }

    /***************************************************************
     * Parse WGSchema corpus into Sent
     */
    public void parse() {

        String filename = System.getenv("CORPORA") + File.separator + "WSCollection-small.xml";
        System.out.println("WGschema.parse(): reading corpus from " + filename);
        SimpleDOMParser sdp = new SimpleDOMParser();
        SimpleElement se = null;
        try {
            System.out.println(filename);
            File f = new File(filename);
            if (!f.exists())
                return;
            BufferedReader br = new BufferedReader(new FileReader(filename));
            se = sdp.parse(br);
            //System.out.println(se.toString());
        }
        catch (java.io.IOException e) {
            System.out.println("Error in main(): IO exception parsing file " + filename);
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        if (!se.getTagName().equals("collection")) {
            System.out.println("Error in WGschema.parse(): bad tag " + se.getTagName());
            return;
        }
        for (SimpleElement sec : se.getChildElements()) {
            if (!sec.getTagName().equals("schema")) {
                System.out.println("Error in WGschema.parse(): bad tag " + sec.getTagName());
                return;
            }
            parseTest(sec);
        }
        System.out.println("CoNLWGschemaL04.parse(): complete reading corpus of " + sentences.size() + " sentences");
    }

    /***************************************************************
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        for (Sent s : sentences) {
            sb.append(s.toString());
        }
        return sb.toString();
    }

    /***************************************************************
     */
    public F1Matrix score() {

       // if (debug) System.out.println("score(): found rels   : " + rels);
        F1Matrix f1mat = new F1Matrix();
        return f1mat;
    }

    /***************************************************************
     */
    public void process() {

        F1Matrix total = new F1Matrix();
        long startTime = System.currentTimeMillis();
        int totalGroundTruth = 0;
        int totalExtracted = 0;
        for (Sent s : sentences) {
                try {
                    System.out.println(interp.interpretGenCNF(s.sentence));
                }
                catch (Exception e) { e.printStackTrace(); }
        }
        System.out.println("CoNLL04.extractAll(): expected: " + totalGroundTruth + " found: " + totalExtracted);
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("time to process: " + seconds + " seconds (not counting init)");
        System.out.println("time to process: " + (seconds / sentences.size()) + " seconds per sentence");
        System.out.println("total score: " + total);
        System.out.println("precision:   " + total.precision());
        System.out.println("recall:      " + total.recall());
        System.out.println("F1:          " + total.fOne());
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        debug = false;
        Interpreter.replaceInstances = false;
        Interpreter.debug = false;
        Interpreter.coref = true;
        KBmanager.getMgr().initializeOnce();
        WGschema wg = new WGschema();
        wg.interp = new Interpreter();
        wg.interp.initOnce();
        wg.parse();
        wg.process();
    }
}
