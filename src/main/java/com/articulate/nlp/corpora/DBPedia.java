package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/*
copyright 2018- Infosys

contact Adam Pease adam.pease@infosys.com

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
public class DBPedia {

    // strings to URLs from thresh.out
    public static HashMap<String, String> pageToString = new HashMap<>();

    public static HashMap<String, String> stringToOnto = new HashMap<>();

    // a mapping from a DBPedia/WN 2.0 pseudo-sense-key to SUMO
    public static HashMap<String, String> wnToOnto = new HashMap<>();

    // the sense index from WordNet 2.0
    public static HashMap<String, String> senseIndex20 = new HashMap<>();

    public static boolean suppressErrors = true;

    /***************************************************************
     * read a tab-delimited file of strings and their Wikipedia page IDs.  e.g.
     * Book of Pooh: Stories from the Heart	The_Book_of_Pooh:_Stories_from_the_Heart
     */
    public static void readPageToString(String path) {

        //ArrayList<String> thresh = CorpusReader.readFile(path + "thresh-reduced.out");
        ArrayList<String> thresh = CorpusReader.readFile(path + "surfaceForms-fromOccs-thresh3.tsv");
        for (String s : thresh) {
            String[] pair = s.split("\t");
            pageToString.put(pair[1], pair[0]);
        }
    }

    /***************************************************************
     * Create NER strings in DBPedia to ontology terms.
     * Input file is triples in XML format.  First element is URL of
     * the page, second is a URI of a relation, third is an URI of
     * a DBPedia ontology term
     */
    public static void readStringToDBPOnto(String path) {

        File file = new File(path + "instance_types_dbtax_dbo_en.ttl");
        try {
            InputStream in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            //System.out.println("Info in DBPedia.readStringToDBPOnto(): " + file);
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("<http://dbpedia.org/resource/")) {
                    //System.out.println("Info in CorpusReader.processFileByLine(): line: " + line);
                    String page = line.substring(29, line.indexOf(">"));
                    if (pageToString.containsKey(page)) {
                        String onto = line.substring(line.lastIndexOf("/") + 1, line.lastIndexOf(">"));
                        stringToOnto.put(pageToString.get(page), onto);
                    }
                }
            }
        }
        catch (Exception e) {
            if (!suppressErrors) {
                System.out.println("; Error in DBPedia.readStringToDBPOnto(): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /***************************************************************
     * Convert DBPedia WN links in the form word_POSword_sensenum to
     * the standard word_LL_sensenum where POS IDs are two letter
     * strings and not the spelled out parts of speech.  Since the
     * keys are not WN standard form, we can use WordNetUtilities
     * routines for decomposing the keys
     */
    private static String wordPOSKeytoWNKey(String in) {

        int lastUS = in.lastIndexOf("-");
        int secondToLastUS = in.lastIndexOf("-",lastUS - 1);
        if (lastUS < 0 || secondToLastUS < 0) {
            if (!suppressErrors) System.out.println("; Error DBPedia.wordPOSKeytoWNKey(): bad key " + in);
            return "";
        }
        String word = in.substring(0,secondToLastUS);
        String num = in.substring(lastUS+1,in.length());
        String posWord = in.substring(secondToLastUS + 1,lastUS);
        String POSlets = WordNetUtilities.posWordToAlphaKey(posWord);
        return word + "_" + POSlets + "_" + num;
    }

    /***************************************************************
     * Read an XML file of links between pages and WordNet.
     * First element is the URL of a DBPedia page, second element is
     * always "<http://dbpedia.org/property/wordnet_type>", and third
     * element is a pseudo-sense-key link in WordNet 2.0
     */
    public static void readWordNetMapping(String path) {

        File file = new File(path + "wordnet_links.nt");
        try {
            InputStream in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            //System.out.println("Info in DBPedia.readWordNetMapping(): " + file);
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("<http://dbpedia.org/resource/")) {
                    //System.out.println("Info in DBPedia.readWordNetMapping(): line: " + line);
                    String page = line.substring(29,line.indexOf(">"));
                    //System.out.println("Info in DBPedia.readWordNetMapping(): page: " + page);
                    if (pageToString.containsKey(page)) {
                        String wn = line.substring(line.lastIndexOf("/")+8,line.lastIndexOf(">")); // remove leading "synset-"
                        String standardWNkey = wordPOSKeytoWNKey(wn);
                        if (wnToOnto.keySet().contains(standardWNkey)) {
                            String str = pageToString.get(page);
                            String onto = wnToOnto.get(standardWNkey);
                            stringToOnto.put(str, onto);
                            //System.out.println("Info in DBPedia.readWordNetMapping(): str, onto " + str + ", "  + onto);
                        }
                        else
                        if (!suppressErrors) System.out.println("; Error in DBPedia.readWordNetMapping(): no ontology mapping for " + standardWNkey);
                    }
                    else
                    if (!suppressErrors) System.out.println("; Error in DBPedia.readWordNetMapping(): no page to string for " + page);
                }
            }
        }
        catch (Exception e) {
            if (!suppressErrors) System.out.println("; Error in DBPedia.readWordNetMapping(): " + e.getMessage());
            if (!suppressErrors) e.printStackTrace();
        }
    }

    /***************************************************************
     * Make a mapping from a DBPedia/WN 2.0 pseudo-sense-key to SUMO.
     * Go through all sense keys in WN 2.0, mapping them first to
     * 2.0 synset offsets and then to 3.0 synset offsets
     */
    public static void makeWnToOnto() {

        for (String s : DBPedia.senseIndex20.keySet()) {
            // alpha POS - NN,VB etc
            //String key = word + "_" + posString + "_" + sensenum;
            //System.out.println("makeWnToOnto(): key " + key);
            //System.out.println("makeWnToOnto(): s " + s);
            String POS = WordNetUtilities.getPOSfromKey(s);
            String oldSynset = DBPedia.senseIndex20.get(s);
            String posNum = WordNetUtilities.posLettersToNumber(POS);
            String newSynset = WordNetUtilities.mappings.get(posNum + oldSynset);
            //System.out.println("makeWnToOnto(): oldSynset " + oldSynset);
            //System.out.println("makeWnToOnto(): newSynset " + newSynset);
            if (newSynset == null) {
                if (!suppressErrors) System.out.println("; Error DBPedia.makeWnToOnto(): null synset for "  + s + " and old synset " + oldSynset);
                continue;
            }
            String sumo = WordNet.wn.getSUMOMapping(newSynset);
            if (!StringUtil.emptyString(sumo)) {
                sumo = WordNetUtilities.getBareSUMOTerm(sumo);
                wnToOnto.put(s, sumo);
                //System.out.println("DBPedia.makeWnToOnto(): s, sumo: " + s + ", " + sumo);
            }
            else {
                if (!suppressErrors) System.out.println("; Error in DBPedia.makeWnToOnto(): null sumo for "  + s + " and old synset " + oldSynset);
            }
            //System.out.println();
        }
    }

    /***************************************************************
     */
    public static void printPageToSUMO() {

        System.out.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
                "@prefix dbp:  <http://dbpedia.org/resource/> .\n" +
                "@prefix sumo: <https://github.com/ontologyportal/sumo#> .");
        for (String p : pageToString.keySet()) {
            String s = pageToString.get(p);
            String sumo = stringToOnto.get(s);
            if (sumo != null)
                System.out.println("dbp:" + p + "\t" + "rdf:type" + "\tsumo:" + sumo + " .");
        }
    }

    /***************************************************************
     */
    public static void printPageToString() {

        System.out.println("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
                "@prefix dbp:  <http://dbpedia.org/resource/> .\n" +
                "@prefix sumo: <https://github.com/ontologyportal/sumo#> .");
        for (String p : pageToString.keySet()) {
            String s = pageToString.get(p);
            String sumo = stringToOnto.get(s);
            if (sumo != null)
                System.out.println("dbp:" + p + "\trdfs:label\t\"" + s + "\" .");
        }
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        String sep = File.separator;
        String path = System.getenv("CORPORA") + File.separator + "DBPedia" + File.separator;
        WordNet.wn.compileRegexPatterns();
        WordNet.wn.readSenseIndex(System.getenv("CORPORA") + sep + "WordNet-2.0" + sep + "dict" + sep + "index.sense");
        DBPedia.senseIndex20 = WordNet.wn.senseIndex;
        WordNet.wn.senseIndex = new HashMap<>();
        WordNet.wn.readSenseIndex(System.getenv("CORPORA") + sep + "WordNet-3.0" + sep + "dict" + sep + "index.sense");
        readPageToString(path);
        //readStringToDBPOnto(path);
        //for (String s : stringToOnto.keySet()) {
        //    System.out.println(s + "\t" + stringToOnto.get(s));
        //}
        String wnPath = System.getenv("CORPORA") + sep + "mappings-upc-2007" + sep + "mapping-20-30" + sep;
        try {
            WordNetUtilities.updateWNversionReading(wnPath, "20-30");
        }
        catch (Exception e) {
            if (!suppressErrors) System.out.println("; Error in DBPedia.main(): " + e.getMessage());
            if (!suppressErrors) e.printStackTrace();
        }
        makeWnToOnto();
        readWordNetMapping(path);

        //printPageToSUMO();
        printPageToString();

    }
}
