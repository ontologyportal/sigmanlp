package nlp.corpora;

import com.articulate.sigma.StringUtil;
import com.articulate.sigma.WordNet;
import com.articulate.sigma.WordNetUtilities;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;

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

Read the contents of eXtended WordNet http://www.hlt.utdallas.edu/~xwn/downloads.html

*/
public class XtendedWN {

    public static HashMap<String,HashMap<String,Integer>> senses = new HashMap<>();

    /***************************************************************
     * turn a string of space delimited key=value pairs into a map
     */
    private static HashMap<String,String> stringToMap(String str) {

        HashMap<String,String> result = new HashMap<>();
        String[] attr = str.split(" ");
        for (String s : attr) {
            String key = s.substring(0,s.indexOf("="));
            String value = StringUtil.removeEnclosingQuotes(s.substring(s.indexOf("=")+1,s.length()));
            result.put(key,value);
        }
        return result;
    }

    /***************************************************************
     */
    private static void readFile(String filename, String pos) {

        ArrayList<String> snss = new ArrayList<>();
        ArrayList<String> words = new ArrayList<>();
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {
                if (line.startsWith("<gloss")) {
                    String synset = pos + line.substring(line.length()-10,line.length()-2);
                    snss = new ArrayList<>();
                    snss.add(synset);
                    words = new ArrayList<>();
                }
                else if (line.startsWith("      <wf ")) { // parse all the word senses
                    int item = line.indexOf("<");
                    int close = line.indexOf(">");
                    String attribs = line.substring(item+4,close);
                    HashMap<String,String> m = stringToMap(attribs);
                    String word = line.substring(close+1,line.indexOf("<",close + 1));
                    if (m.containsKey("lemma"))
                        words.add(m.get("lemma"));
                    if (m.containsKey("pos") && m.containsKey("lemma") && m.containsKey("wnsn")) {
                        String POSpenn = StringUtil.removeEnclosingQuotes(m.get("pos"));
                        char POSnum = WordNetUtilities.posPennToNumber(POSpenn);
                        String POSlet = WordNetUtilities.posNumberToLetters(Character.toString(POSnum));
                        String senseKey = m.get("lemma") + "_" + POSlet + "_" + m.get("wnsn");
                        String synset = POSnum + WordNet.wn.senseIndex.get(senseKey);
                        if (WordNet.wn.senseIndex.get(senseKey) == null && POSlet.equals("JJ")) {
                            // WordNet calls some adjectives "adjective satellites"
                            POSlet = "AS";
                            senseKey = m.get("lemma") + "_" + POSlet + "_" + m.get("wnsn");
                            synset = "3" + WordNet.wn.senseIndex.get(senseKey);
                        }
                        // convert from WN 2.0 synsets in XWN to current WN 3.0
                        if (WordNetUtilities.mappings.containsKey(synset))
                            snss.add(WordNetUtilities.mappings.get(synset));
                        else
                            System.out.println("Error in XtendedWN.readFile(): no mapping to WN 3.0 from 2.0 synset: " + synset);
                    }
                }
                else if (line.startsWith("  </wsd>")) { // add all the co-occurrence data
                    for (String sense : snss) {
                        HashMap<String,Integer> wordList = new HashMap<>();
                        if (senses.containsKey(sense))
                            wordList = senses.get(sense);
                        else {
                            senses.put(sense,wordList);
                        }
                        for (String word : words) {
                            Integer count = new Integer(1);
                            if (wordList.containsKey(word))
                                count = count + wordList.get(word);
                            wordList.put(word,count);
                        }
                    }
                }
            }
        }
        catch (IOException i) {
            System.out.println("Error in XtendedWN.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        WordNet.initOnce();
        WordNet.wn.baseDirFile = new File("/home/apease/corpora/WordNet-2.0/dict");
        WordNet.wn.readSenseIndex();
        try {
            WordNetUtilities.updateWNversionReading("/home/apease/corpora/mappings-upc-2007/mapping-20-30/", "20-30");
        }
        catch (IOException ioe) {
            System.out.println("Error in XtendedWN.main()" + ioe.getMessage());
            ioe.printStackTrace();
        }
        //XtendedWN.readFile("/home/apease/corpora/XWN2.0-1.1/noun.xml","1");
        XtendedWN.readFile("/home/apease/corpora/XWN2.0-1.1/noun-small.xml","1");
        System.out.println(senses);
    }
}
