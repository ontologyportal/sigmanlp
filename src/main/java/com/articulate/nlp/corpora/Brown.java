package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.sigma.StringUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.time.TimeAnnotator;
import edu.stanford.nlp.util.CoreMap;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.articulate.nlp.pipeline.SentenceUtil.toDependenciesList;

public class Brown {

    ArrayList<CoreMap> sentences = new ArrayList<>();
    public static Pipeline p = null;
    private static Annotation wholeDocument = null;
    public static boolean initialized = false;
    private static boolean debug = false;

    public static final List<String> epis = Arrays.asList("know", "think",
            "learn", "understand", "perceive", "feel", "guess", "recognize",
            "notice", "want", "wish", "hope", "decide", "expect", "prefer",
            "remember", "forget", "imagine", "believe");

    public static final List<String> modal = Arrays.asList("can", "could",
            "may", "might", "must", "shall", "should", "will", "would");

    public static final List<String> otherModal = Arrays.asList("ought", "dare", "need");

    public static final List<String> quant = Arrays.asList("some", "many", "few", "all");

    public int negCount = 0;
    public int epiCount = 0;
    public int modalCount = 0;
    public int otherModalCount = 0;
    public int quantCount = 0;
    public int simpleCount = 0;

    /** ***************************************************************
     */
    public static void init() {

        if (initialized)
            return;
        System.out.println("in Brown.init(): ");
        Properties props = new Properties();
        String propString = "tokenize, ssplit, pos, lemma, parse, depparse, natlog";
        p = new Pipeline(true,propString);
        initialized = true;
        System.out.println("in Brown.init(): completed initialization");
    }

    /** ***************************************************************
     */
    public void process(CoreMap sent) {

        boolean simple = true;
        ArrayList<Literal> deps = SentenceUtil.toDependenciesList(sent);
        System.out.println("process(): deps: " + deps);
        for (Literal lit : deps) {
            if (lit.pred.equals("neg")) {
                negCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel cl : tokens) {
            if (epis.contains(cl.lemma())) {
                System.out.println("epistemic: " + sent);
                epiCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        for (CoreLabel cl : tokens) {
            if (modal.contains(cl.lemma())) {
                System.out.println("modal: " + sent);
                modalCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        for (CoreLabel cl : tokens) {
            if (otherModal.contains(cl.lemma())) {
                System.out.println("other modal: " + sent);
                otherModalCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        if (simple)
            simpleCount++;
    }

    /** ***************************************************************
     */
    public void run() {

        HashSet<String> companies = new HashSet<>();
        System.out.println("INFO in Brown.main()");
        init();
        String filename = System.getenv("CORPORA") + File.separator + "brown.txt";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            StringBuffer para = new StringBuffer();
            while ((line = lr.readLine()) != null) {
                if (StringUtil.emptyString(line)) {
                    wholeDocument = new Annotation(para.toString());
                    p.pipeline.annotate(wholeDocument);
                    List<CoreMap> sents = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                    for (CoreMap cm : sents)
                        sentences.add(cm);
                    System.out.println("run(): sents: " + sents);
                    para = new StringBuffer();
                }
                else {
                    para.append(line);
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        for (CoreMap s : sentences)
            process(s);
        System.out.println("negations: " + negCount);
        System.out.println("epistemics: " + epiCount);
        System.out.println("modalCount: " + modalCount);
        System.out.println("otherModalCount: " + otherModalCount);
        System.out.println("quantified: " + quantCount);
        System.out.println("simple: " + simpleCount);
        System.out.println("total: " + sentences.size());
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        Brown brown = new Brown();
        brown.run();
    }
}
