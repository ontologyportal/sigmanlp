package com.articulate.nlp.corpora;

import com.articulate.nlp.constants.LangLib;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;
import com.articulate.sigma.utils.MapUtils;

public class COCA {

    HashMap<String, Integer> verbPhrase = new HashMap<>();

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
                if (!line.startsWith("#") && line.indexOf("\t") != -1) {
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
            System.out.println("INFO in COCA.main(): seconds to run: " + (System.currentTimeMillis() - millis) / 1000);
        }
    }
}
