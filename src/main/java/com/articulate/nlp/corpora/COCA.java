package com.articulate.nlp.corpora;

// Code to read and manipulate the billion-word contents of the Corpus of Contemporary
// American English - https://corpus.byu.edu/coca/?c=coca&q=67259739
// COCA part of speech tags are lowercase versions of https://ucrel.lancs.ac.uk/claws7tags.html
// Although this code is open source, COCA is not, and running this code requires
// access to COCA itself.

/*
Author: Adam Pease

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA
*/

import com.articulate.nlp.constants.LangLib;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.MapUtils;
import com.articulate.sigma.utils.PairMap;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.*;

public class COCA {

    public HashMap<String, HashMap<String,Integer>> verbs = new HashMap<>();
    public HashMap<String, HashMap<String,Integer>> nouns = new HashMap<>();

    public HashMap<String, TreeMap<Integer,HashSet<String>>> freqVerbs = new HashMap<>();
    public HashMap<String, TreeMap<Integer,HashSet<String>>> freqNouns = new HashMap<>();

    public HashMap<String, Integer> verbPhrase = new HashMap<>();

    /** ***************************************************************
     * Parse lines that have words from a sentence in three columns.
     * The first column is the word as it appears, the second is in
     * all lowercase root form and the third is the part of speech.
     * Example:
     * may	may	vm
     * not	not	xx
     * be	be	vbi
     * @return a triple of word,wor,pos as the inner ArrayList. The
     * outer ArrayList is a list of those triples, where a '#' token
     * delineates sentences.
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
     */
    public static boolean excluded(String p) {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        if (p.equals("True") || p.equals("False") ||
                kb.isInstanceOf(p,"DeonticAttribute") || kb.isSubclass(p,"DeonticAttribute"))
            return true;
        //System.out.println("excluded(): p: " + p + " kb.isInstanceOf(p,\"Attribute\"): " + kb.isInstanceOf(p,"Attribute"));
        //System.out.println("excluded(): p: " + p + " kb.isSubclass(p,\"Attribute\"): " + kb.isSubclass(p,"Attribute"));
        //System.out.println("excluded(): p: " + p + " (kb.isInstanceOf(p,\"Attribute\") || kb.isSubclass(p,\"Attribute\"): " +
        //        (kb.isInstanceOf(p,"Attribute") || kb.isSubclass(p,"Attribute")));
        if (kb.isInstanceOf(p,"Attribute") || kb.isSubclass(p,"Attribute") ) {
            //System.out.println("excluded(): " + p + " is not excluded");
            return false;
        }
        else {
            return true;
        }
    }

    /** ***************************************************************
     * allow only modifiers that have a mapping to SUMO
     */
    public static void filterModifiers(HashMap<String, TreeMap<Integer,HashSet<String>>> fVerbs,
        HashMap<String, TreeMap<Integer,HashSet<String>>> fNouns ) {

        HashSet<String> nounRemove = new HashSet<>();
        for (String key : fNouns.keySet()) {
            TreeMap<Integer,HashSet<String>> map = fNouns.get(key);
            HashSet<Integer> keyRemove = new HashSet<>();
            for (Integer i : map.keySet()) {
                HashSet<String> theSet = map.get(i);
                HashSet<String> toRemove = new HashSet<>();
                for (String s : theSet) {
                    String SUMO = WSD.getBestDefaultSUMOsense(s,3);
                    SUMO = WordNetUtilities.getBareSUMOTerm(SUMO);
                    if (StringUtil.emptyString(SUMO)) {
                        toRemove.add(s);
                        //System.out.println("no SUMO mapping for " + s);
                    }
                    else {
                        if (excluded(SUMO)) {
                            toRemove.add(s);
                            //System.out.println("excluded mapping of " + s + " to " + SUMO);
                        }
                        //else
                            //System.out.println("found SUMO " + SUMO + " for " + s);
                    }
                }
                theSet.removeAll(toRemove);
                if (theSet.isEmpty())
                    keyRemove.add(i);
            }
            for (Integer rem : keyRemove)
                map.remove(rem);
            if (map.isEmpty())
                nounRemove.add(key);
        }
        for (String k : nounRemove)
            fNouns.remove(k);

        HashSet<String> verbRemove = new HashSet<>();
        for (String key : fVerbs.keySet()) {
            TreeMap<Integer,HashSet<String>> map = fVerbs.get(key);
            HashSet<Integer> keyRemove = new HashSet<>();
            for (Integer i : map.keySet()) {
                HashSet<String> theSet = map.get(i);
                HashSet<String> toRemove = new HashSet<>();
                for (String s : theSet) {
                    String SUMO = WSD.getBestDefaultSUMOsense(s,4);
                    SUMO = WordNetUtilities.getBareSUMOTerm(SUMO);
                    if (StringUtil.emptyString(SUMO)) {
                        toRemove.add(s);
                        //System.out.println("no SUMO mapping for " + s + " with verb " + key);
                    }
                    else {
                        if (excluded(SUMO)) {
                            toRemove.add(s);
                            //System.out.println("excluded mapping of " + s + " to " + SUMO + " with verb " + key);
                        }
                        //else
                            //System.out.println("found SUMO " + SUMO + " for " + s + " with verb " + key);
                    }
                }
                theSet.removeAll(toRemove);
                if (theSet.isEmpty())
                    keyRemove.add(i);
            }
            for (Integer rem : keyRemove)
                map.remove(rem);
            if (map.isEmpty())
                verbRemove.add(key);
        }
        for (String k : verbRemove)
            fVerbs.remove(k);
    }

    /** ***************************************************************
     * read adj/noun, adv/verb stats from nouns.txt and verbs.txt"
     */
    public void readPairs(String dir) {

        System.out.println("read pairs");
        freqNouns = PairMap.readMap(dir + File.separator + "nouns.txt");
        freqVerbs = PairMap.readMap(dir + File.separator + "verbs.txt");
        //System.out.println("Nouns: " + freqNouns);
        //System.out.println("Verbs: " + freqVerbs);
        System.out.println("COCA.readPairs(): " + freqNouns.keySet().size() +
                " nouns and " + freqVerbs.keySet().size() + " verbs read");
        filterModifiers(freqVerbs,freqNouns);
        //System.out.println("=================================");
        //System.out.println("Nouns: " + freqNouns);
        //System.out.println("Verbs: " + freqVerbs);
        System.out.println("COCA.readPairs(): " + freqNouns.keySet().size() +
                " nouns and " + freqVerbs.keySet().size() + " verbs after filtering");
    }

    /** ***************************************************************
     * Get frequency sorted adjective-noun pairs from a file of COCA. Use
     * Stanford CoreNLP to find sentence boundaries and POS, ignoring
     * the POS data from COCA.

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
*/
    /** ***************************************************************
     * Test whether the CLAWS POS tag is a noun
     */
    public static boolean isNounPOS(String s) {
        if (s.startsWith("nn") || s.startsWith("np"))
            return true;
        else
            return false;
    }

    /** ***************************************************************
     * Test whether the CLAWS POS tag is a verb
     */
    public static boolean isVerbPOS(String s) {
        if (s.startsWith("vb") || s.startsWith("vd") || s.startsWith("vh") || s.startsWith("vv"))
            return true;
        else
            return false;
    }

    /** ***************************************************************
     * Get frequency sorted adjective-noun and adverb-verb pairs from
     * COCA.  Use the COCA POS info. Note that sentence boundaries
     * don't matter for this method.
     */
    public void modifierFreqFile(String fname) {

        ArrayList<String> excluded = new ArrayList<String>(
                Arrays.asList("not","never"));
        ArrayList<ArrayList<String>> result = readWordFile(fname);
        String modifier = "";
        for (ArrayList<String> ar : result) {
            //System.out.println(ar);
            if (ar.size() != 3)
                continue;
            String word = ar.get(1).replace(",","").replace(":","");
              // remove delimiter characters
            String pos = ar.get(2);
            //System.out.print (word + ":");
            //System.out.print(pos + " ");
            if ((isNounPOS(pos)|| isVerbPOS(pos)) && !StringUtil.emptyString(modifier)) {
                if (isNounPOS(pos))
                    PairMap.addToMap(nouns,word,modifier);
                if (isVerbPOS(pos))
                    PairMap.addToMap(verbs,word,modifier);
            }
            if ((pos.startsWith("jj") || pos.startsWith("r") ) && !excluded.contains(word)) {
                modifier = word;
            }
            else
                modifier = "";
            if (word.equals("@"))
                System.out.print('.');
        }
        //System.out.println("Nouns: " + nouns);
        //System.out.println("Verbs: " + verbs);
        String COCA = System.getenv("CORPORA") + File.separator + "COCA" + File.separator;
        PairMap.saveMap(nouns,COCA + "nouns.txt");
        PairMap.saveMap(verbs,COCA + "verbs.txt");
    }

    /** ***************************************************************
     * Get frequency sorted adjective-noun and adverb-verb pairs from a directory of COCA
     */
    public void pairFreq(String dir) {

        //System.out.println("COCA.pairFreq(): dir: " + dir);
        File f = new File(dir);
        File[] pathnames = f.listFiles();
        for (File next : pathnames) {
            //System.out.println("COCA.pairFreq(): checking file: " + next.getAbsolutePath());
            //System.out.println("COCA.pairFreq(): checking file: " + next.getPath());
            //System.out.println("COCA.pairFreq(): exists: " + next.exists());
            //System.out.println("COCA.pairFreq(): directory: " + next.isDirectory());
            if (next.isDirectory()) {
                //System.out.println("COCA.pairFreq(): is dir: " + next.getAbsolutePath());
                pairFreq(next.getAbsolutePath());
            }
            else {
                //System.out.println("COCA.pairFreq(): not dir: " + next.getAbsolutePath());
                if (next.getAbsolutePath().endsWith(".txt")) {
                    //System.out.println("COCA.pairFreq(): running on file: " + next.getAbsolutePath());
                    modifierFreqFile(next.getAbsolutePath());
                }
            }
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
        System.out.println("  -d fname - collect verb phrases from files in directory");
        System.out.println("  -s fname - regenerate sentences from a directory");
        System.out.println("  -a fname - collect frequency sorted adjective noun pairs");
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
            else if (args != null && args.length > 0 && args[0].startsWith("-a")) {
                String prefix = System.getenv("CORPORA") + File.separator + "COCA" + File.separator;
                coca.pairFreq(prefix);
            }
            else if (args != null && args.length > 0 && args[0].startsWith("-r")) {
                KBmanager.getMgr().initializeOnce();
                coca.readPairs(".");
            }
            System.out.println("INFO in COCA.main(): seconds to run: " + (System.currentTimeMillis() - millis) / 1000);
        }
    }
}
