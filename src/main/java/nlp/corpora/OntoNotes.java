package nlp.corpora;

import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.BrownCorpus;
import com.google.common.io.Resources;
import edu.stanford.nlp.ling.Word;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.articulate.sigma.WSD.findWordSenseInContext;

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

    // a map index by WordNet sense keys where values are counts of co-occurring words
    public HashMap<String, HashMap<String,Integer>> senses = new HashMap<>();

    // a list of sentences containing a list of tokens in the sentence
    public ArrayList<ArrayList<Token>> sentences = new ArrayList<>();

    /***************************************************************
     * A particular word and its annotations
     */
    public class Token {
        String token = "";
        String file = "";
        int num = 0;
        int sent = 0;
        String lemma = "";
        String sense = ""; // a nine digit wordnet sense id
        String mfs = ""; // a nine digit wordnet sense id just by most frequent sense
        int posNum = 1;
        HashMap<String,String> map = new HashMap<>();
    }

    /***************************************************************
     * Read a text file into lines
     */
    public ArrayList<String> readFile(String filename) {

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
                    System.out.println("line: " + l);
                    if (StringUtil.emptyString(l)) { // empty means the end of the sentence
                        inLeaves = false;
                        processOnfFile(tokens);
                        tokens = new ArrayList<>();
                        sent++;
                    }
                    else {
                        //     4   tourists
                        Pattern p = Pattern.compile("\\s*(\\d+)\\s*(\\w+).*"); // start of a new sentence
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
                            tok.token = m.group(2);
                            tok.num = tokNum;
                            tokens.add(tok);
                        }
                        else {
                            //            sense: bring-v.2
                            Pattern p2 = Pattern.compile("\\s*sense:\\s*(.*)");
                            Matcher m2 = p2.matcher(l);
                            if (m2.matches()) {
                                String senseKey = m2.group(1);
                                tok.sense = WordNetUtilities.synsetFromOntoNotes(senseKey);
                                tok.posNum = Integer.parseInt(Character.toString(tok.sense.charAt(0)));
                                System.out.println("adding sense " + senseKey);
                            }
                            else {
                                //            sense: bring-v.2
                                Pattern p3 = Pattern.compile("\\s*prop:\\s*(\\w+)\\..*");
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
            result.add(t.token);
        }
        return result;
    }

    /***************************************************************
     */
    private void processOnfFile(ArrayList<Token> tokens) {

        ArrayList<String> sentence = recreateSentence(tokens);
        sentence = WordNet.wn.removeStopWords(sentence);
        HashMap<String,Integer> words = new HashMap<>();
        HashSet<String> tempSenses = new HashSet<>();
        for (Token tok : tokens) {
            if (!StringUtil.emptyString(tok.sense)) {
                tempSenses.add(tok.sense);
                String candidateSense = WSD.findWordSenseInContextWithPos(tok.token,sentence,tok.posNum);
                //String candidateSense = WSD.getBestDefaultSense(tok.token);
                String sumo = WordNet.wn.getSUMOMapping(tok.sense);
                if (sumo != null)
                    sumo = WordNetUtilities.getBareSUMOTerm(sumo);
                String candSumo = WordNet.wn.getSUMOMapping(candidateSense);
                if (candSumo != null)
                    candSumo = WordNetUtilities.getBareSUMOTerm(candSumo);
                if (tok.sense.equals(candidateSense))
                    System.out.println("INFO in OntoNotes.processOnfFile(): correct sense " +
                            tok.sense + " for token " + tok.token);
                else
                    System.out.println("Error in OntoNotes.processOnfFile(): non matching sense " +
                             candidateSense + " for token " + tok.token + " with marked sense " + tok.sense);
                if (sumo != null && sumo.equals(candSumo))
                    System.out.println("INFO in OntoNotes.processOnfFile(): correct sumo " +
                            candSumo + " for token " + tok.token);
                else
                    System.out.println("Error in OntoNotes.processOnfFile(): non matching sumo " +
                            candSumo + " for token " + tok.token + " with marked sumo " + sumo +
                            " and sense " + candidateSense + " with marked sense " + tok.sense +
                    " on sentence:token " + tok.sent + ":" + tok.num + " in " + tok.file);
            }
            String tokString = tok.token;
            if (!StringUtil.emptyString(tok.lemma))
                tokString = tok.lemma;
            Integer value = new Integer(0);
            if (words.containsKey(tokString))
                value = words.get(tokString);
            value = value + 1;
            words.put(tokString, value);
        }
        for (String sense : tempSenses) {
            HashMap<String,Integer> wordList = new HashMap<>();
            if (senses.containsKey(sense))
                wordList = senses.get(sense);
            else {
                senses.put(sense,wordList);
            }
            for (String word : words.keySet()) {
                Integer count = new Integer(1);
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

        String ontonotesHome = System.getenv("ONTONOTES");
        // such as /home/user/corpora/ontonotes-release-5.0/
        // then get all files under data/files/data/english/annotations
        // such as bc/cctv/00/cctv0000.sense

        String dataDir = ontonotesHome + File.separator + "data/files/data/english/annotations";
        //String dataDir = "/home/apease/corpora/ontoNotesTest";
        //readNameFiles(dataDir);
        //readSenseFiles(dataDir);
        readOnfFiles(dataDir);
        System.out.println(senses);
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
        KBmanager.getMgr().initializeOnce();
        OntoNotes on = new OntoNotes();
        on.readCorpus();
    }
}
