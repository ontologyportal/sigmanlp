package com.articulate.nlp.corpora;

import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import com.articulate.nlp.pipeline.Pipeline;

import java.util.*;

/**
 Copyright 2017 Articulate Software

 Author: Adam Pease apease@articulatesoftware.com

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

 Generate word sense co-occurrence data from SUMO documentation

 */
public class SUMOtoCoSense {

    // a map index by WordNet sense keys where values are counts of co-occurring words
    public static HashMap<String,HashMap<String,Integer>> senses = new HashMap<>();

    /***************************************************************
     * Merge two maps of counts of words that co-occur in a sentence with
     * a given word sense.
     * @param wordList1 keys are words and values are counts of that word
     * @param wordList2 keys are words and values are counts of that word
     */
    private static HashMap<String,Integer> mergeWordCounts(HashMap<String,Integer> wordList1,
                                                           HashMap<String,Integer> wordList2) {

        HashMap<String,Integer> wordCounts = new HashMap<>();
        HashSet<String> words = new HashSet<>();
        words.addAll(wordList1.keySet());
        words.addAll(wordList2.keySet());
        for (String w : words) {
            Integer count1 = wordList1.get(w);
            int c1 = 0;
            if (count1 != null)
                c1 = count1.intValue();
            Integer count2 = wordList2.get(w);
            int c2 = 0;
            if (count2 != null)
                c2 = count2.intValue();
            wordCounts.put(w,c1 + c2);
        }
        return wordCounts;
    }

    /***************************************************************
     */
    private static HashMap<String,Integer> createWordCounts(ArrayList<String> wordList) {

        HashMap<String,Integer> wordCounts = new HashMap<>();
        for (String s : wordList) {
            Integer count = Integer.valueOf(0);
            if (wordCounts.containsKey(s))
                count = wordCounts.get(s);
            count++;
            wordCounts.put(s,count);
        }
        return wordCounts;
    }

    /***************************************************************
     */
    private static void addToSenses(HashSet<String> senseList, ArrayList<String> wordList) {

        HashMap<String,Integer> wordCounts = createWordCounts(wordList);
        for (String sense : senseList) {
            if (!senses.containsKey(sense))
                senses.put(sense,wordCounts);
            else {
                HashMap<String,Integer> newWordCount = mergeWordCounts(wordCounts,senses.get(sense));
                senses.put(sense,newWordCount);
            }
        }
    }

    /***************************************************************
     * Treat every SUMO term as the word senses it maps to and take
     * words in its documentation string as co-occurring words.
     * TODO: use multi-word and ner
     * TODO: handle plural SUMO ids
     * TODO: record all synsets and words for a SUMO term?
     */
    public static void docToSenses() {

        String propString = "tokenize, ssplit, pos, lemma";
        Pipeline p = new Pipeline(true,propString);
        KB kb = KBmanager.getMgr().getKB("SUMO");
        ArrayList<Formula> docs = kb.ask("arg",0,"documentation");
        //ArrayList<Formula> docs = kb.askWithRestriction(0,"documentation",1,"CognitiveAgent"); // test sample
        for (Formula f : docs) {
            HashSet<String> snss = new HashSet<>(); // must be sense keys
            ArrayList<String> words = new ArrayList<>();
            String term = f.getArgument(1);
            String lang = f.getArgument(2);
            String doc = f.getArgument(3);
            if (lang.equals("EnglishLanguage")) {
                Annotation wholeDocument = p.annotate(doc);
                List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap sentence : sentences) {
                    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                    Iterator<CoreLabel> it = tokens.iterator();
                    while (it.hasNext()) {
                        CoreLabel token = it.next();
                        if (Character.isAlphabetic(token.lemma().charAt(0))) {
                            if (!WordNet.wn.isStopWord(token.lemma()))
                                words.add(token.lemma());
                        }
                        else if (token.lemma().equals("&") && it.hasNext()) {
                            token = it.next();
                            if (token.lemma().equals("%") && it.hasNext()) {
                                token = it.next();
                                if (Formula.isTerm(token.originalText())) {
                                    String st = token.originalText();
                                    int len = token.originalText().length();
                                    String t = st.substring(0,len);
                                    while (len > 1) {
                                        t = st.substring(0,len);
                                        if (kb.containsTerm(t))
                                            break;
                                        len--;
                                    }
                                    ArrayList<String> senseList = WordNet.wn.SUMOHash.get(t);
                                    if (senseList != null) {
                                        ArrayList<String> wordList = new ArrayList<String>();
                                        for (String s : senseList) // add all the words corresponding to a SUMO term
                                            wordList.addAll(WordNet.wn.synsetsToWords.get(s));
                                        for (String synset : senseList) {
                                            String key = WordNetUtilities.getKeyFromSense(synset);
                                            if (key != null)
                                                snss.add(key);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                addToSenses(snss,words);
            }
        }
    }

    /***************************************************************
     * TODO: implement this - treat all terms in an axiom as words in
     * a sentence
     */
    public static void axiomsToSenses() {

    }

    /***************************************************************
     */
    public static void load() {

        System.out.println("Info in SUMOtoCoSense.load(): starting process");
        docToSenses();
        axiomsToSenses();
        WordNet.writeWordCoFrequencies("wordFreqSUMO.txt",senses);
        System.out.println("Info in SUMOtoCoSense.load(): before merge sense inventory: " +
                WordNet.wn.wordCoFrequencies.keySet().size());
        WordNet.wn.mergeWordCoFrequencies(senses);
        System.out.println("Info in SUMOtoCoSense.load(): after merge sense inventory: " +
                WordNet.wn.wordCoFrequencies.keySet().size());
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        load();
        System.out.println(senses);
    }
}
