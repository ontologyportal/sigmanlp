package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.MultiWords;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

Class to process portions of the DBPedia dataset.  Some files I've
looked at are, with an example line from the file:

article_categories_en.ttl
<http://dbpedia.org/resource/Albedo> <http://purl.org/dc/terms/subject> <http://dbpedia.org/resource/Category:Climatology>

category_labels_en.ttl
<http://dbpedia.org/resource/Category:Climatology> <http://www.w3.org/2000/01/rdf-schema#label> "Climatology"@en

instance_types_dbtax_dbo_en.ttl
<http://dbpedia.org/resource/Paris> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Place>

instance_types_en.ttl
<http://dbpedia.org/resource/Paris> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/PopulatedPlace>

wordnet_links.nt
<http://dbpedia.org/resource/Paris> <http://dbpedia.org/property/wordnet_type> <http://www.w3.org/2006/03/wn/wn20/instances/synset-monument-noun-2>

yago_links.nt
<http://dbpedia.org/resource/Paris> <http://www.w3.org/2002/07/owl#sameAs> <http://yago-knowledge.org/resource/Paris>

surfaceForms-fromOccs.count.txt - pick the highest count mapping for each string
  43908 Paris	Paris

 */
public class DBPedia {

    // strings to URLs from thresh.out
    public static HashMap<String, String> stringToPage = new HashMap<>();

    public static HashMap<String, String> pageToString = new HashMap<>();

    // String to DBPedia ontology types
    public static HashMap<String, String> stringToDBPOnto = new HashMap<>();

    // String to SUMO types
    public static HashMap<String, String> stringToSUMO = new HashMap<>();

    // a mapping from a DBPedia/WN 2.0 pseudo-sense-key to SUMO
    public static HashMap<String, String> wnToOnto = new HashMap<>();

    // the sense index from WordNet 2.0
    public static HashMap<String, String> senseIndex20 = new HashMap<>();

    public static boolean suppressErrors = false;

    public static MultiWords multiWords = new MultiWords();

    // DBPedia ontology term is the key and SUMO term is the value.
    // SUMO terms have appended '=' or '+' depending on whether the
    // DBPedia term is equivalent or more specific than SUMO.
    public static HashMap<String,String> DBP2SUMO = new HashMap<>();

    /***************************************************************
     * read a tab-delimited file of strings and their Wikipedia page IDs and
     * counts of frequencies e.g.
     *   13 !Kung	!Kung_language
     *    7 !Kung	ǃKung_people
     * Only keep the highest count mapping for each string.
     */
    public static void readStringToPageCount(String path) {

        System.out.println("DBPedia.readStringToPageCount()");
        ArrayList<String> lines = CorpusReader.readFile(path + "surfaceForms-fromOccs.count.txt");
        int bestCount = 0;
        String bestPage = "";
        for (String s : lines) {
            Pattern p = Pattern.compile("[ ]+(\\d+)[^\\d]([^\\t]+)\\t(.*)");
            Matcher m = p.matcher(s);
            if (m.matches()) {
                String countStr = m.group(1);
                String str = m.group(2);
                String page = m.group(3);
                int count = 0;
                if (!StringUtil.emptyString(countStr))
                    count = Integer.parseInt(countStr);
                if (str.equals(bestPage) || bestPage == "") {
                    if (count > bestCount) {
                        bestCount = count;
                        bestPage = page;
                    }
                }
                else {
                    stringToPage.put(str,bestPage);
                    pageToString.put(bestPage,str);
                    if (str.indexOf('_') > 0)
                        multiWords.addMultiWord(str, ' ');
                    bestCount = count;
                    bestPage = page;
                }
            }
        }
    }

    /***************************************************************
     * read a tab-delimited file of strings and their Wikipedia page IDs.  e.g.
     * Book of Pooh: Stories from the Heart	The_Book_of_Pooh:_Stories_from_the_Heart
     * Only add strings that weren't found in readStringToPageCount()
     */
    public static void readPageToString(String path) {

        //ArrayList<String> thresh = CorpusReader.readFile(path + "thresh-reduced.out");
        //ArrayList<String> thresh = CorpusReader.readFile(path + "surfaceForms-fromOccs-thresh3.tsv");
        System.out.println("DBPedia.readPageToString()");
        String filename = path + "surfaceForms-fromOccs.tsv";
        try {
            File f = new File(filename);
            long len = f.length();
            int lines = 0;
            if (len > 1000000)
                lines = CorpusReader.countLines(f);
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            long counter = 0;
            while ((line = lr.readLine()) != null) {
                String[] pair = line.split("\t");
                String st = pair[0];
                st = st.trim();
                String page = pair[1].trim();
                if (!pageToString.containsKey(page))
                    pageToString.put(page,st);
                if (!stringToPage.containsKey(st)) {
                    stringToPage.put(st, pair[1]);
                    if (st.indexOf(" ") > 0)
                        multiWords.addMultiWord(st, ' ');
                }
                counter++;
                if (counter % 10000 == 0) {
                    if (lines == 0)
                        System.out.print(".");
                    else
                        System.out.print("\b\b\b\b" + (counter * 100) / lines + "%");
                }
            }
            System.out.println();
        }
        catch (IOException i) {
            System.out.println("Error in CorpusReader.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
        System.out.println();
    }

    /***************************************************************
     * read a tab-delimited file of DBPedia and SUMO term mappings. e.g..
     * dbo:Election	owl:equivalentClass	sumo:Election
     */
    public static void readDBP2SUMO(String path) {

        System.out.println("DBPedia.readDBP2SUMO()");
        //ArrayList<String> thresh = CorpusReader.readFile(path + "thresh-reduced.out");
        ArrayList<String> thresh = CorpusReader.readFile(path + File.separator + "DBpediaMapping.ttl");
        for (String s : thresh) {
            if (s.indexOf('\t') < 0)
                continue;
            String[] pair = s.split("\t");
            String dbp = pair[0].split(":")[1];
            String map = pair[1].split(":")[1];
            if (map.equals("equivalentClass"))
                map = "=";
            if (map.equals("subClassOf"))
                map = "+";
            String sumo = pair[2].split(":")[1];
            sumo = sumo.substring(0,sumo.length()-2);
            DBP2SUMO.put(dbp, sumo + map);
        }
    }

    /***************************************************************
     * ensure that multiwords are added at the same time
     */
    public static void addString2SUMO(String str, String sumo) {

        if (str.indexOf(" ") > 0)
            multiWords.addMultiWord(str,' ');
        stringToSUMO.put(str,sumo);
    }

    /***************************************************************
     * read a tab-delimited file of String and SUMO term mappings. e.g..
     * Josh Brookes	Actor
     */
    public static void readString2SUMO(String path) {

        System.out.println("DBPedia.readString2SUMO()");
        ///ArrayList<String> map = CorpusReader.readFile(path + File.separator + "SUMOtypes.txt");
        ArrayList<String> map = CorpusReader.readFile(path + File.separator + "DBPout.txt");
        for (String s : map) {
            if (s.indexOf('\t') < 0)
                continue;
            String[] pair = s.split("\t");
            String str = pair[0].trim();
            String sumo = pair[1].trim();
            addString2SUMO(str,sumo);
        }
    }

    /***************************************************************
     * Create NER strings in DBPedia to DBP ontology terms.
     * Input file is triples in XML format.  First element is URL of
     * the page, second is a URI of a relation, third is an URI of
     * a DBPedia ontology term
     * <http://dbpedia.org/resource/Paris> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Place>
     */
    public static void readPageToDBPOnto(String path) {

        System.out.println("DBPedia.readStringToDBPOnto()");
        File f = new File(path + "instance_types_dbtax_dbo_en.ttl");
        try {
            long len = f.length();
            int lines = 0;
            if (len > 1000000)
                lines = CorpusReader.countLines(f);
            InputStream in = new FileInputStream(f);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            //System.out.println("Info in DBPedia.readStringToDBPOnto(): " + file);
            long counter = 0;
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("<http://dbpedia.org/resource/")) {
                    //System.out.println("Info in CorpusReader.processFileByLine(): line: " + line);
                    String page = line.substring(29, line.indexOf(">"));
                    if (pageToString.containsKey(page)) {
                        String onto = line.substring(line.lastIndexOf("/") + 1, line.lastIndexOf(">"));
                        stringToDBPOnto.put(pageToString.get(page), onto);
                    }
                }
                counter++;
                if (counter % 10000 == 0) {
                    if (lines == 0)
                        System.out.print(".");
                    else
                        System.out.print("\b\b\b\b" + (counter * 100) / lines + "%");
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
     * Create NER strings in DBPedia to DBP ontology terms.
     * Not sure why instance_type_en.ttl has a different set of
     * pages than instance_types_dbtax_dbo_en.ttl .
     * Input file is triples in XML format.  First element is URL of
     * the page, second is a URI of a relation, third is an URI of
     * a DBPedia ontology term
     * <http://dbpedia.org/resource/Paris> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://dbpedia.org/ontology/Place> .
     */
    public static void readPageToDBPOnto2(String path) {

        System.out.println("DBPedia.readPageToDBPOnto2()");
        File f = new File(path + "instance_types_en.ttl");
        try {
            long len = f.length();
            int lines = 0;
            if (len > 1000000)
                lines = CorpusReader.countLines(f);
            InputStream in = new FileInputStream(f);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            //System.out.println("Info in DBPedia.readStringToDBPOnto(): " + file);
            long counter = 0;
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("<http://dbpedia.org/resource/")) {
                    //System.out.println("Info in CorpusReader.processFileByLine(): line: " + line);
                    String page = line.substring(29, line.indexOf(">"));
                    if (pageToString.containsKey(page)) {
                        String onto = line.substring(line.lastIndexOf("/") + 1, line.lastIndexOf(">"));
                        stringToDBPOnto.put(pageToString.get(page), onto);
                    }
                }
                counter++;
                if (counter % 10000 == 0) {
                    if (lines == 0)
                        System.out.print(".");
                    else
                        System.out.print("\b\b\b\b" + (counter * 100) / lines + "%");
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
     * keys are not WN standard form, we can't use WordNetUtilities
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
     *
     * <http://dbpedia.org/resource/Paris> <http://dbpedia.org/property/wordnet_type> <http://www.w3.org/2006/03/wn/wn20/instances/synset-monument-noun-2>
     */
    public static void readWordNetMapping(String path) {

        System.out.println("DBPedia.readWordNetMapping()");
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
                            addString2SUMO(str, onto);
                            System.out.println("Info in DBPedia.readWordNetMapping(): str, onto " + str + ", "  + onto);
                        }
                        else
                            if (!suppressErrors) System.out.println("; Error in DBPedia.readWordNetMapping(): no ontology mapping for " + standardWNkey);
                    }
                    else {
                        if (!suppressErrors)
                            System.out.println("; Error in DBPedia.readWordNetMapping(): no page to string for " + page);
                        String wn = line.substring(line.lastIndexOf("/")+8,line.lastIndexOf(">")); // remove leading "synset-"
                        String standardWNkey = wordPOSKeytoWNKey(wn);
                        if (wnToOnto.keySet().contains(standardWNkey)) {
                            String str = page.replace('_',' ');
                            String onto = wnToOnto.get(standardWNkey);
                            addString2SUMO(str, onto);
                            if (!suppressErrors) System.out.println("Info in DBPedia.readWordNetMapping(): made new str, onto " + str + ", "  + onto);
                        }
                        else
                            if (!suppressErrors) System.out.println("; Error in DBPedia.readWordNetMapping(2): no ontology mapping for " + standardWNkey);
                    }
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

        System.out.println("DBPedia.makeWnToOnto()");
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
                if (WordNetUtilities.nonWNsynset(posNum + oldSynset))
                    newSynset = posNum + oldSynset;
                else {
                    if (!suppressErrors)
                        System.out.println("; Error DBPedia.makeWnToOnto(): null synset for " + s + " and old synset " + oldSynset);
                    continue;
                }
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
            String sumo = stringToSUMO.get(s);
            if (sumo != null)
                System.out.println("dbp:" + p + "\t" + "rdf:type" + "\tsumo:" + sumo + " .");
        }
    }

    /***************************************************************
     */
    public static void printStringToSUMO() {

        for (String s : stringToSUMO.keySet()) {
            String sumo = stringToSUMO.get(s);
            if (!sumo.endsWith("=") && !sumo.endsWith("+"))
                sumo = sumo + "=";
            if (sumo != null && !sumo.startsWith("Article") &&
                    !sumo.startsWith("Class") && !sumo.startsWith("List"))
                System.out.println(s + "\t" + sumo);
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
            String sumo = stringToSUMO.get(s);
            if (sumo != null)
                System.out.println("dbp:" + p + "\trdfs:label\t\"" + s + "\" .");
        }
    }

    /***************************************************************
     * Fill in gaps where there's no mapping from a string to wordnet
     * (and therefore to SUMO) in DBPedia, so we use the DBPedia string
     * to DBPedia Ontology mapping, and then use DBPedia to SUMO
     * mapping
     */
    public static void combine() {

        System.out.println("DBPedia.combine()");
        for (String s : stringToDBPOnto.keySet()) {
            if (!stringToSUMO.containsKey(s)) {
                String DBPonto = stringToDBPOnto.get(s);
                if (DBP2SUMO.containsKey(DBPonto)) {
                    String SUMO = DBP2SUMO.get(DBPonto);
                    addString2SUMO(s,SUMO);
                }
            }
        }
    }

    /***************************************************************
     */
    public static void processFiles() {

        KBmanager.getMgr().initializeOnce();
        String sep = File.separator;
        String path = System.getenv("CORPORA") + sep + "DBPedia" + sep;
        WordNet.wn.compileRegexPatterns();
        WordNet.wn.readSenseIndex(System.getenv("CORPORA") + sep + "WordNet-2.0" + sep + "dict" + sep + "index.sense");
        DBPedia.senseIndex20 = WordNet.wn.senseIndex;
        WordNet.wn.senseIndex = new HashMap<>();
        WordNet.wn.readSenseIndex(System.getenv("CORPORA") + sep + "WordNet-3.0" + sep + "dict" + sep + "index.sense");
        String mapPath = System.getenv("ONTOLOGYPORTAL_GIT") + sep + "sumo" + sep + "mappings";
        readDBP2SUMO(mapPath);
        readStringToPageCount(path);
        readPageToString(path);
        readPageToDBPOnto(path);
        readPageToDBPOnto2(path);
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
        combine();
    }

    /***************************************************************
     */
    public static void initOnce() {

        KBmanager.getMgr().initializeOnce();
        String sep = File.separator;
        String path = System.getenv("CORPORA") + sep + "DBPedia" + sep;
        readString2SUMO(path);
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        initOnce();
        //printPageToSUMO();
        //printPageToString();
        processFiles();
        printStringToSUMO();
        System.out.println(DBPedia.stringToDBPOnto.get("Municipality of Dobrovnik"));
        System.out.println(DBPedia.stringToSUMO.get("Sacred Heart Academy"));
    }
}
