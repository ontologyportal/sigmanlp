package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;

import edu.stanford.nlp.util.CoreMap;

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
    public static Map<String,Map<String,Integer>> senses = new HashMap<>();

    /***************************************************************
     * Merge two maps of counts of words that co-occur in a sentence with
     * a given word sense.
     * @param wordList1 keys are words and values are counts of that word
     * @param wordList2 keys are words and values are counts of that word
     */
    private static Map<String,Integer> mergeWordCounts(Map<String,Integer> wordList1,
                                                       Map<String,Integer> wordList2) {

        Map<String,Integer> wordCounts = new HashMap<>();
        Set<String> words = new HashSet<>();
        words.addAll(wordList1.keySet());
        words.addAll(wordList2.keySet());
        Integer count1, count2;
        int c1, c2;
        for (String w : words) {
            count1 = wordList1.get(w);
            c1 = 0;
            if (count1 != null)
                c1 = count1;
            count2 = wordList2.get(w);
            c2 = 0;
            if (count2 != null)
                c2 = count2;
            wordCounts.put(w,c1 + c2);
        }
        return wordCounts;
    }

    /***************************************************************
     */
    private static Map<String,Integer> createWordCounts(List<String> wordList) {

        Map<String,Integer> wordCounts = new HashMap<>();
        Integer count;
        for (String s : wordList) {
            count = 0;
            if (wordCounts.containsKey(s))
                count = wordCounts.get(s);
            count++;
            wordCounts.put(s,count);
        }
        return wordCounts;
    }

    /***************************************************************
     */
    private static void addToSenses(Set<String> senseList, List<String> wordList) {

        Map<String,Integer> wordCounts = createWordCounts(wordList);
        Map<String,Integer> newWordCount;
        for (String sense : senseList) {
            if (!senses.containsKey(sense))
                senses.put(sense,wordCounts);
            else {
                newWordCount = mergeWordCounts(wordCounts,senses.get(sense));
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
        List<Formula> docs = kb.ask("arg",0,"documentation");
        //ArrayList<Formula> docs = kb.askWithRestriction(0,"documentation",1,"CognitiveAgent"); // test sample
        Set<String> snss;
        List<String> words;
        String term, lang, doc, st, t, key;
        List<CoreMap> sentences;
        List<CoreLabel> tokens;
        Iterator<CoreLabel> it;
        CoreLabel token;
        int len;
        List<String> wordList;
        List<String> senseList;
        for (Formula f : docs) {
            snss = new HashSet<>(); // must be sense keys
            words = new ArrayList<>();
            term = f.getStringArgument(1);
            lang = f.getStringArgument(2);
            doc = f.getStringArgument(3);
            if (lang.equals("EnglishLanguage")) {
                Annotation wholeDocument = p.annotate(doc);
                sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap sentence : sentences) {
                    tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                    it = tokens.iterator();
                    while (it.hasNext()) {
                        token = it.next();
                        if (Character.isAlphabetic(token.lemma().charAt(0))) {
                            if (!WordNet.wn.isStopWord(token.lemma()))
                                words.add(token.lemma());
                        }
                        else if (token.lemma().equals("&") && it.hasNext()) {
                            token = it.next();
                            if (token.lemma().equals("%") && it.hasNext()) {
                                token = it.next();
                                if (Formula.isTerm(token.originalText())) {
                                    st = token.originalText();
                                    len = token.originalText().length();
                                    t = st.substring(0,len);
                                    while (len > 1) {
                                        t = st.substring(0,len);
                                        if (kb.containsTerm(t))
                                            break;
                                        len--;
                                    }
                                    senseList = WordNet.wn.SUMOHash.get(t);
                                    if (senseList != null) {
                                        wordList = new ArrayList<>();
                                        for (String s : senseList) // add all the words corresponding to a SUMO term
                                            wordList.addAll(WordNet.wn.synsetsToWords.get(s));
                                        for (String synset : senseList) {
                                            key = WordNetUtilities.getKeyFromSense(synset);
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
