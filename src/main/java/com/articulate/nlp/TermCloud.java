package com.articulate.nlp;

import com.articulate.nlp.corpora.CorpusReader;
import com.articulate.nlp.semRewrite.NPtype;
import com.articulate.sigma.utils.StringUtil;

import java.util.*;

/** This code is copyright Infosys (c) 2019-2020, Articulate Software 2020-.

 This software is released under the GNU Public License
 <http://www.gnu.org/copyleft/gpl.html>.

 Please cite the following article in any publication with references:

 Pease A., and Benzmüller C. (2013). Sigma: An Integrated Development Environment
 for Logical Theories. AI Communications 26, pp79-97.  See also
 http://github.com/ontologyportal

 This class extracts phrases from text, with their cooccurring phrases, sorted
 by frequency.  It can be used to propose likely high value ontology terms
 referred to in free text.
 */

public class TermCloud {


    //terms sorted by frequency
    public TreeMap<Integer,HashSet<String>> termFreq = new TreeMap<>();

    // frequency of a given term in a corpus
    public HashMap<String,Integer> freqTerm = new HashMap<>();

    // the cooc string is two terms joined by a tab
    public TreeMap<Integer,HashSet<String>> coocFreq = new TreeMap<>();

    // the cooc string is two terms joined by a tab
    public HashMap<String, Integer> freqCooc = new HashMap<>();

    // term keys and the terms they cooccur with
    public HashMap<String,HashSet<String>> terms = new HashMap<>();

    /** ***************************************************************
     */
    public void addToCoocFreq(Integer freq, String combined) {

        HashSet<String> coocs = new HashSet<>();
        if (coocFreq.containsKey(freq)) {
            coocs = coocFreq.get(freq);
        }
        else
            coocFreq.put(freq,coocs);
        coocs.add(combined);
    }

    /** ***************************************************************
     */
    public String makeCooc(String t1, String t2) {

        return t1 + "\t" + t2;
    }

    /** ***************************************************************
     */
    public void addCooc(String t1, String t2) {

        String combined = makeCooc(t1,t2);
        if (freqCooc.containsKey(combined)) {
            Integer freq = freqCooc.get(combined);
            freq++;
            freqCooc.put(combined,freq);
            addToCoocFreq(freq,combined);
        }
        else {
            Integer freq = new Integer(1);
            freqCooc.put(combined,freq);
            addToCoocFreq(freq,combined);
        }
    }

    /** ***************************************************************
     * collect noun phrase co-occurrences from a line
     */
    public void processLine(HashSet<String> nps) {

        for (String s : nps) {
            Integer freq = freqTerm.get(s);
            if (freq == null)
                freq = new Integer(0);
            freq++;
            freqTerm.put(s,freq);

            HashSet<String> tset = termFreq.get(freq);
            if (tset == null)
                tset = new HashSet<>();
            tset.add(s);
            termFreq.put(freq,tset);

            HashSet<String> coocs = terms.get(s);
            if (coocs == null)
                coocs = new HashSet<>();
            for (String c : nps) {
                if (!c.equals(s)) {
                    addCooc(s,c);
                    coocs.add(c);
                }
            }
            terms.put(s,coocs);
        }
    }

    /** ***************************************************************
     * collect noun phrase co-occurrences from a file
     */
    public void collectNPcoocs(String fname) {

        ArrayList<String> lines = CorpusReader.readFile(fname);
        for (String l : lines) {
            if (StringUtil.emptyString(l))
                continue;
            System.out.println("\ncollectNPcoocs(): line: " + l);
            HashSet<String> nps = NPtype.findNPs(l);
            System.out.println("collectNPcoocs(): NPs: " + nps);
            if (nps.size() > 0)
                processLine(nps);
        }
    }

    /** ***************************************************************
     */
    public void printAll() {

        System.out.println("TermCloud.printAll(): terms: " + terms);
        System.out.println("TermCloud.printAll(): freqCooc: " + freqCooc);
        System.out.println("TermCloud.printAll(): coocFreq: " + coocFreq);
        System.out.println("TermCloud.printAll(): freqTerm: " + freqTerm);
        System.out.println("TermCloud.printAll(): termFreq: " + termFreq);
    }

    /** ***************************************************************
     */
    public static void printTopNFreqMap(TreeMap<Integer,HashSet<String>> freqMap,
                                        int n) {
        int count = 0;
        Iterator<Integer> it = freqMap.descendingKeySet().iterator();
        while (it.hasNext() && count < n) {
            HashSet<String> termSet = freqMap.get(it.next());
            count = count + termSet.size();
            for (String s : termSet) {
                System.out.println("printTopNFreqMap(): " + s);
            }
        }
    }

    /** ***************************************************************
     * get the top co-occurring terms for a given term.  Collect co-occurrences
     * that appear in both orders
     */
    public static void addToFreqMap(TreeMap<Integer,HashSet<String>> freqMap,
                             Integer freq, String s) {

        HashSet<String> termSet = new HashSet<>();
        if (freqMap.containsKey(freq))
            termSet = freqMap.get(freq);
        else
            freqMap.put(freq,termSet);
        termSet.add(s);
    }

    /** ***************************************************************
     * get the top co-occurring terms for a given term.  Collect co-occurrences
     * that appear in both orders
     */
    public void showTopCooc(String s) {

        TreeMap<Integer,HashSet<String>> localCoocFreq = new TreeMap<>();
        HashSet<String> coocTerms = terms.get(s);
        for (String c : coocTerms) {
            String cooc = makeCooc(s,c);
            String coocRev = makeCooc(c,s); // reverse order of above variable
            Integer freq = freqCooc.get(cooc);
            addToFreqMap(localCoocFreq,freq,cooc);
            addToFreqMap(localCoocFreq,freq,coocRev);
        }
        printTopNFreqMap(localCoocFreq,10);
    }

    /** ***************************************************************
     */
    public void printTopResults() {

        int countMax = 10; // how many terms to display
        System.out.println("printTopResults(): Show top " + countMax +
                " most frequent terms and the top 10 most frequently co-occurring terms for that term.");
        Iterator<Integer> it = termFreq.descendingKeySet().iterator();
        int count = 0;
        while (it.hasNext() && count < countMax) {
            HashSet<String> termSet = termFreq.get(it.next());
            count = count + termSet.size();
            for (String s : termSet) {
                System.out.println("\n========= " + s + " =========");
                showTopCooc(s);
            }
        }
    }

    /*************************************************************
     */
    public static void main (String[] args) {

        TermCloud tc = new TermCloud();
        tc.collectNPcoocs(args[0]); // filename of text corpus
        // tc.printAll();
        tc.printTopResults();
    }
}
