package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.pipeline.Annotation;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
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

Read and process the contents of the OntoNotes corpus
http://www.isi.edu/natural-language/people/hovy/papers/06HLT-NAACL-OntoNotes-short.pdf
https://catalog.ldc.upenn.edu/docs/LDC2013T19/OntoNotes-Release-5.0.pdf

*/
public class OntoNotes {

    public int correctSumo = 0;
    public int correctSense = 0;
    public int incorrectSumo = 0;
    public int incorrectSense = 0;

    public static int errorCount = 0;

    // time to process
    public static double sentsProcessed = 0;
    public static double millis = 0;

    // a map index by WordNet sense keys where values are counts of co-occurring words
    public HashMap<String, HashMap<String,Integer>> senses = new HashMap<>();

    public static Pipeline p = new Pipeline(true,"tokenize, ssplit, pos, lemma");

    // a list of sentences containing a list of tokens in the sentence
    //public ArrayList<ArrayList<Token>> sentences = new ArrayList<>();

    /***************************************************************
     * A particular word and its annotations
     */
    public class Token {
        String origToken = "";
        String lowerCase = "";
        String file = "";
        int num = 0;
        int sent = 0;
        String lemma = "";
        String senseKey = ""; // a nine digit wordnet sense id
        int posNum = 1;
        HashMap<String,String> map = new HashMap<>();
    }

    /***************************************************************
     * Read a text file into lines
     */
    public static ArrayList<String> readFile(String filename) {

        ArrayList<String> result = new ArrayList<>();
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {
                result.add(line);
            }
        }
        catch (IOException i) {
            System.out.println("Error in OntoNotes.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
        return result;
    }

    /***************************************************************
     * .name has full sentences with XML-based NER tags
     */
    public void readNameFiles(String dir) {

        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".name")) {
                    ArrayList<String> doc = readFile(filePath.toString());
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in OntoNotes.readNameFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /***************************************************************
     * .sense has sentence and token number then lemma, pos and sense num
     */
    public void readSenseFiles(String dir) {

        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".sense")) {
                    ArrayList<String> doc = readFile(filePath.toString());
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in OntoNotes.readSenseFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /***************************************************************
     * .onf has many things but includes a token-by-token breakdown
     * with one line (or more) per token and relations for the token
     * that are the semantic roles.  The relevant section for each
     * sentence starts with the keyword "Leaves:" on its own line.
     */
    private void scanOnfFile(ArrayList<String> doc, String filename) {

        ArrayList<Token> tokens = new ArrayList<>();
        Token tok = null;
        int sent = -1;

        boolean inLeaves = false;
        for (int i = 0; i < doc.size(); i++) {
            String l = doc.get(i);
            if (l.startsWith("Leaves:") || l.equals("-------")) {
                inLeaves = true;
            }
            else {
                if (inLeaves) {
                    //System.out.println("line: " + l);
                    if (StringUtil.emptyString(l)) { // empty means the end of the sentence
                        inLeaves = false;
                        processOnfFile(tokens);
                        tokens = new ArrayList<>();
                        sent++;
                    }
                    else {
                        //     4   tourists
                        Pattern p = Pattern.compile("\\s+(\\d+)\\s+(\\w+).*"); // start of a new sentence
                        Matcher m = p.matcher(l);
                        if (m.matches()) {
                            tok = new Token();
                            tok.file = filename;
                            tok.sent = sent;
                            String num = m.group(1);
                            int tokNum = 0;
                            try {
                                tokNum = Integer.parseInt(num);
                            }
                            catch (NumberFormatException e) {
                                System.out.println("Error in OntoNotes.processOnfFile(): " + num);
                                System.out.println(e.getMessage());
                                e.printStackTrace();
                            }
                            tok.origToken = m.group(2);
                            tok.lowerCase = tok.origToken.toLowerCase();
                            //System.out.println("OntoNotes.scanOnfFile: added token : " + tok.token +
                            //        " for line " + l);
                            tok.num = tokNum;
                            tokens.add(tok);
                        }
                        else {
                            //            sense: bring-v.2
                            Pattern p2 = Pattern.compile("\\s+sense:\\s+(.+)");
                            Matcher m2 = p2.matcher(l);
                            if (m2.matches()) {
                                String senseKey = m2.group(1);
                                String synset = WordNetUtilities.synsetFromOntoNotes(senseKey);
                                if (synset != null) {
                                    tok.senseKey = WordNetUtilities.getKeyFromSense(synset);
                                    tok.posNum = Integer.parseInt(Character.toString(synset.charAt(0)));
                                    String sumo = WordNet.wn.getSUMOMapping(synset);
                                    if (sumo == null) {
                                        if (errorCount < 20) {
                                            System.out.println("Error in OntoNotes.scanOnfFile: No SUMO sense for token:key " +
                                                    tok.origToken + ":" + tok.senseKey + " and key: " + senseKey);
                                            errorCount++;
                                            if (errorCount >= 20)
                                                System.out.println("surpressing further errors...");
                                        }
                                    }
                                }
                                else {
                                    if (errorCount < 20) {
                                        System.out.println("Error in OntoNotes.scanOnfFile: No synset for token:key " +
                                                tok.origToken + ":" + tok.senseKey + " and key: " + senseKey);
                                        errorCount++;
                                        if (errorCount >= 20)
                                            System.out.println("surpressing further errors...");
                                    }
                                }
                            }
                            else {
                                //            sense: bring-v.2
                                Pattern p3 = Pattern.compile("\\s+prop:\\s+(\\w+)\\..*");
                                Matcher m3 = p2.matcher(l); // lemma form of the word
                                if (m3.matches()) {
                                    String prop = m3.group(1);
                                    tok.lemma = prop;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /***************************************************************
     */
    private ArrayList<String> recreateSentence(ArrayList<Token> tokens) {

        ArrayList<String> result = new ArrayList<String>();
        for (Token t : tokens) {
            result.add(t.lowerCase);
        }
        return result;
    }

    /***************************************************************
     * Process a list of word tokens from a sentence, using the
     * given part of speech in the token, guessing its word sense
     * and scoring whether that sense matches the gold-standard
     * sense from the corpus
     */
    private void processOnfFile(ArrayList<Token> tokens) {

        ArrayList<String> sentence = recreateSentence(tokens);
        sentence = WordNet.wn.removeStopWords(sentence);
        HashMap<String,Integer> words = new HashMap<>();
        HashSet<String> tempSenses = new HashSet<>();
        double time = System.currentTimeMillis();
        if (p != null) { // just a dummy test for timing
            Annotation wholeDocument = p.annotate(String.join(" ", sentence));
            millis = millis + (System.currentTimeMillis() - time);
            System.out.println("processOnfFile(): sentence: " + wholeDocument);
        }
        for (Token tok : tokens) {
            if (!WordNet.wn.isStopWord(tok.origToken)) {
                if (!StringUtil.emptyString(tok.senseKey)) {
                    tempSenses.add(tok.senseKey);
                    time = System.currentTimeMillis();
                    String candidateSynset = WSD.findWordSenseInContextWithPos(tok.origToken, sentence, tok.posNum, false);
                    millis = millis + (System.currentTimeMillis() - time);
                    sentsProcessed++;
                    System.out.println("processOnfFile(): avg time: " + millis/sentsProcessed);
                    System.out.println("processOnfFile(): total time: " + millis);
                    System.out.println("processOnfFile(): sentences: " + sentsProcessed);
                    System.out.println();
                    if (StringUtil.emptyString(candidateSynset)) {
                        incorrectSense++;
                        incorrectSumo++;
                        continue;
                    }
                    String candidateSenseKey = WordNetUtilities.getKeyFromSense(candidateSynset);
                    String answerSynset = WordNetUtilities.getSenseFromKey(tok.senseKey);

                    if (tok.senseKey.equals(candidateSenseKey)) {
                        //System.out.println("in OntoNotes.processOnfFile(): correct sense " +
                        //        tok.sense + " for token " + tok.token);
                        correctSense++;
                    }
                    else {
                        //System.out.println("in OntoNotes.processOnfFile(): non matching sense " +
                        //        candidateSense + " for token " + tok.token + " with marked sense " + tok.sense);
                        incorrectSense++;
                    }

                    String sumo = WordNet.wn.getSUMOMapping(answerSynset);
                    if (sumo != null)
                        sumo = WordNetUtilities.getBareSUMOTerm(sumo);
                    else {
                        System.out.println("OntoNotes.processOnfFile: No SUMO sense for key " +
                                tok.origToken + ":" + candidateSynset);
                        continue;
                    }
                    String candSumo = WordNet.wn.getSUMOMapping(candidateSynset);
                    if (candSumo != null)
                        candSumo = WordNetUtilities.getBareSUMOTerm(candSumo);
                    else {
                        incorrectSumo++;
                        continue;
                    }
                    int random = (int) (Math.random() * 2000 + 1);
                    if (sumo != null && sumo.equals(candSumo)) {
                        if (random == 1000)
                            System.out.println("in OntoNotes.processOnfFile(): correct sumo " +
                                    candSumo + " for token " + tok.origToken + " and sentence \n" + sentence);
                        correctSumo++;
                    }
                    else {
                        if (random == 1000)
                            System.out.println("in OntoNotes.processOnfFile(): non matching sumo " +
                                    candSumo + " for token " + tok.origToken + " with marked sumo " + sumo +
                                    " and sense " + candidateSynset + " with marked sense " + tok.senseKey +
                                    " on sentence:token " + tok.sent + ":" + tok.num + " in " + tok.file +
                                    " and sentence \n" + sentence);
                        incorrectSumo++;
                    }
                }
                String tokString = tok.lowerCase;
                if (!StringUtil.emptyString(tok.lemma))
                    tokString = tok.lemma;
                Integer value = 0;
                if (words.containsKey(tokString))
                    value = words.get(tokString);
                value = value + 1;
                words.put(tokString, value);
            }
        }

        for (String sense : tempSenses) {
            HashMap<String,Integer> wordList = new HashMap<>();
            if (senses.containsKey(sense))
                wordList = senses.get(sense);
            else {
                senses.put(sense,wordList);
            }
            for (String word : words.keySet()) {
                Integer count = 1;
                if (wordList.containsKey(word))
                    count = count + wordList.get(word);
                wordList.put(word,count);
            }
        }
    }

    /***************************************************************
     * see scanOnfFile(ArrayList<String>)
     * see processOnfFile(ArrayList<Token>)
     */
    public void readOnfFiles(String dir) {

        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".onf")) {
                    ArrayList<String> doc = readFile(filePath.toString());
                    scanOnfFile(doc,filePath.toString());
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in OntoNotes.readOnfFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /***************************************************************
     * File types are .coref .name .onf .parallel .parse .prop .sense .speaker
     * .name has full sentences with XML-based NER tags
     * .sense has word senses
     * .onf has semantic roles
     */
    public void readCorpus() {

        System.out.println("Info in OntoNotes.readCorpus(): starting read");
        String ontonotesHome = System.getenv("ONTONOTES");
        // such as /home/user/corpora/ontonotes-release-5.0/
        // then get all files under data/files/data/english/annotations
        // such as bc/cctv/00/cctv0000.sense

        String dataDir = ontonotesHome + File.separator + "data/files/data/english/annotations";
        //String dataDir = "/home/apease/corpora/ontoNotesTest";
        //readNameFiles(dataDir);
        //readSenseFiles(dataDir);
        readOnfFiles(dataDir);
        //System.out.println(senses);
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        /*
        Pattern p = Pattern.compile("\\s*\\d+\\s*(\\w+).*");
        Matcher m = p.matcher("    10  have");
        if (m.matches())
            System.out.println("match!");
        else
            System.out.println("no match!");
        Pattern p2 = Pattern.compile("\\s*sense:\\s*(\\w+).*");
        Matcher m2 = p2.matcher("           sense: look-v.5");
        if (m2.matches())
            System.out.println("match!");
        else
            System.out.println("no match!");
        */
        for (int i = 1; i <= 5; i++) {
            WSD.threshold = i * i;
            WSD.gap = i;
            System.out.println("OntoNotes.main(): ----------------------------------------------------");
            System.out.println("WSD threshold: " + WSD.threshold);
            System.out.println("WSD gap: " + WSD.gap);
            KBmanager.getMgr().initializeOnce();
            WordNet.wn.readWordCoFrequencies();
            OntoNotes on = new OntoNotes();
            on.readCorpus();
            WordNet.writeWordCoFrequencies("wordFreqOntoNotes.txt",on.senses);
            System.out.println("OntoNotes.main(): ");
            System.out.println("Test OntoNotes with SemCor as training set and " +
                    WordNet.wn.wordCoFrequencies.keySet().size() + " senses in inventory");
            System.out.println("correct sense: " + on.correctSense);
            System.out.println("incorrect sense: " + on.incorrectSense);
            System.out.println("correct SUMO: " + on.correctSumo);
            System.out.println("incorrect SUMO: " + on.incorrectSumo);

            SUMOtoCoSense.load();
            on = new OntoNotes();
            on.readCorpus();
            System.out.println("Test OntoNotes with SemCor and SUMO as training set and " +
                    WordNet.wn.wordCoFrequencies.keySet().size() + " senses in inventory");
            System.out.println("correct sense: " + on.correctSense);
            System.out.println("incorrect sense: " + on.incorrectSense);
            System.out.println("correct SUMO: " + on.correctSumo);
            System.out.println("incorrect SUMO: " + on.incorrectSumo);

            XtendedWN.load();
            on = new OntoNotes();
            on.readCorpus();
            System.out.println("Test OntoNotes with SemCor, SUMO and XWN as training set and " +
                    WordNet.wn.wordCoFrequencies.keySet().size() + " senses in inventory");
            System.out.println("correct sense: " + on.correctSense);
            System.out.println("incorrect sense: " + on.incorrectSense);
            System.out.println("correct SUMO: " + on.correctSumo);
            System.out.println("incorrect SUMO: " + on.incorrectSumo);
        }
    }
}
