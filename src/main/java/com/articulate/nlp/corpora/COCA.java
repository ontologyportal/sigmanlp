package com.articulate.nlp.corpora;

import com.articulate.nlp.constants.LangLib;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.utils.MapUtils;
import com.articulate.sigma.utils.Pair;
import com.articulate.sigma.utils.PairMap;
import com.articulate.sigma.utils.StringUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import static com.articulate.nlp.pipeline.Pipeline.showResults;

public class COCA {

    public HashMap<String, HashMap<String,Integer>> verbs = new HashMap<>();
    public HashMap<String, HashMap<String,Integer>> nouns = new HashMap<>();

    public HashMap<String, Integer> verbPhrase = new HashMap<>();

    /** ***************************************************************
     * Parse lines that have words from a sentence in three columns.
     * The first column is the word as it appears, the second is in
     * all lowercase root form and the third is the part of speech.
     * Example:
     * may	may	vm
     * not	not	xx
     * be	be	vbi
     */
    public ArrayList<ArrayList<String>> readWordFile(String filename) {

        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lnr = new LineNumberReader(r);
            ArrayList<ArrayList<String>> result = new ArrayList<>();
            String line;
            StringBuffer l = new StringBuffer();
            int linecount = 0;
            while ((line = lnr.readLine()) != null) {
                //System.out.println(line);
                linecount++;
                if (linecount == 1000)
                    System.out.print(".");
                if (line.indexOf("\t") != -1) {
                    ArrayList<String> temp = new ArrayList<>();
                    temp.addAll(Arrays.asList(line.split("\t")));
                    result.add(temp);
                }
            }
            return result;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** ***************************************************************
     */
    public static boolean preposition(String p) {
        return LangLib.prepositions.contains(p);
    }

    /** ***************************************************************
     * read adj/noun, adv/verb stats from nouns.txt and verbs.txt"
     */
    public void readPairs() {

        System.out.println("read pairs");
        nouns = PairMap.readMap("nouns.txt");
        verbs = PairMap.readMap("verbs.txt");
        System.out.println("Nouns: " + nouns);
        System.out.println("Verbs: " + verbs);
    }

    /** ***************************************************************
     * Get frequency sorted adjective-noun pairs from a file of COCA
     */
    public void adjNounFreqFile(String fname) {

        ArrayList<String> excluded = new ArrayList<String>(
                Arrays.asList("not","never"));
        // String propString = "tokenize, ssplit";
        String propString = "tokenize, ssplit, pos, lemma, " +
                "ner, nersumo, gender, parse";
        Pipeline p = new Pipeline(true,propString);
        ArrayList<ArrayList<String>> result = readWordFile(fname);
        boolean verb = true;
        String root = "";
        StringBuffer sent = new StringBuffer();
        HashSet<String> allpairs = new HashSet<>();

        for (ArrayList<String> ar : result) {
            //System.out.println(ar);
            String word = ar.get(0);
            if (word.equals("#")) {
                Annotation wholeDocument = p.annotate(sent.toString());
                //System.out.println("subst: " + Interpreter.corefSubst(wholeDocument));
                List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap sentence : sentences) {
                    String modifier = "";
                    HashSet<String> pairs = new HashSet<>();
                    for (CoreLabel cl : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                        //System.out.print (cl.lemma() + ":");
                        //System.out.print(cl.tag() + " ");
                        if ((cl.tag().startsWith("NN") || cl.tag().startsWith("VB")) && !StringUtil.emptyString(modifier)) {
                            pairs.add(modifier + "-" + cl.lemma());
                            if (cl.tag().startsWith("NN"))
                                PairMap.addToMap(nouns,cl.lemma(),modifier);
                            if (cl.tag().startsWith("VB"))
                                PairMap.addToMap(verbs,cl.lemma(),modifier);
                        }
                        if ((cl.tag().equals("JJ") || cl.tag().equals("RB") ) && !excluded.contains(cl.lemma())) {
                            modifier = cl.lemma();
                        }
                        else
                            modifier = "";
                    }
                    //System.out.println("\n" + pairs);
                    allpairs.addAll(pairs);
                }
                sent = new StringBuffer();
            }
            else if (word.equals("@"))
                System.out.print('.');
            else
                sent.append(word + " ");
        }
        System.out.println("All: " + allpairs);
        System.out.println("Nouns: " + nouns);
        System.out.println("Verbs: " + verbs);
        PairMap.saveMap(nouns,"nouns.txt");
        PairMap.saveMap(verbs,"verbs.txt");
    }

    /** ***************************************************************
     * Get frequency sorted adjective-noun pairs from a directory of COCA
     */
    public void adjNounFreq(String dir) {

        File f = new File(dir);
        Collection<String> sents = new ArrayList<>();
        String[] pathnames = f.list();
        for (String s : pathnames) {
            System.out.println("running on file: " + dir + File.separator + s);
            adjNounFreqFile(dir + File.separator + s);
        }
    }

    /** ***************************************************************
     * Regenerate all sentences from a directory of COCA
     */
    public void regen(String fname) {

        // String propString = "tokenize, ssplit";
        String propString = "tokenize, ssplit, pos, lemma, " +
            "ner, nersumo, gender, parse, coref";
        Pipeline p = new Pipeline(true,propString);
        ArrayList<ArrayList<String>> result = readWordFile(fname);
        boolean verb = true;
        String root = "";
        StringBuffer sent = new StringBuffer();
        for (ArrayList<String> ar : result) {
            //System.out.println(ar);
            String word = ar.get(0);
            if (word.equals("#")) {
                Annotation wholeDocument = p.annotate(sent.toString());
                System.out.println("subst: " + Interpreter.corefSubst(wholeDocument));
                List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap sentence : sentences)
                    System.out.println(sentence);
                sent = new StringBuffer();
            }
            else if (word.equals("@"))
                System.out.print('.');
            else
                sent.append(word + " ");
        }
    }

    /** ***************************************************************
     * Regenerate all sentences from a directory of COCA
     */
    public void regenDir(String dir) {

        File f = new File(dir);
        Collection<String> sents = new ArrayList<>();
        String[] pathnames = f.list();
        for (String s : pathnames) {
            System.out.println("running on file: " + dir + File.separator + s);
            regen(dir + File.separator + s);
        }
    }

    /** ***************************************************************
     */
    public void runDir(String dir) {

        File f = new File(dir);
        String[] pathnames = f.list();
        for (String s : pathnames) {
            System.out.println("running on file: " + s);
            ArrayList<ArrayList<String>> result = readWordFile(dir + File.separator + s);
            boolean verb = true;
            String root = "";
            for (ArrayList<String> ar : result) {
                if (ar.size() > 2) {
                    if (ar.get(2).startsWith("v") && !verb) {
                        root = ar.get(1);
                        verb = true;
                    }
                    else {
                        verb = false;
                        if (preposition(ar.get(1))) {
                            MapUtils.addToFreqMap(verbPhrase,root + "-" + ar.get(1),1);
                        }
                    }
                }
            }
        }
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("COCA corpus analysis");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -d fname - run on directory");
        System.out.println("  -s fname - regenerate sentences from a directory");
        System.out.println("  -a fname - collect frequency sorted adjective noun pairs from file");
        System.out.println("  -r - read adj/noun, adv/verb stats from a file");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        if (args == null || args.length == 0 ||
                (args != null && args.length > 1 && args[0].equals("-h"))) {
            showHelp();
        }
        else {
            COCA coca = new COCA();
            long millis = System.currentTimeMillis();
            if (args != null && args.length > 1 && args[0].startsWith("-d")) {
                coca.runDir(args[1]);
                Map<Integer, HashSet<String>> sorted = MapUtils.toSortedFreqMap(coca.verbPhrase);
                System.out.println(sorted);
            }
            else if (args != null && args.length > 1 && args[0].startsWith("-s")) {
                coca.regen(args[1]);
            }
            else if (args != null && args.length > 1 && args[0].startsWith("-a")) {
                coca.adjNounFreqFile(args[1]);
            }
            else if (args != null && args.length > 0 && args[0].startsWith("-r")) {
                coca.readPairs();
            }
            System.out.println("INFO in COCA.main(): seconds to run: " + (System.currentTimeMillis() - millis) / 1000);
        }
    }
}
