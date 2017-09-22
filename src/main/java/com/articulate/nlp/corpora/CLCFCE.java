package com.articulate.nlp.corpora;

import com.articulate.sigma.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by apease on 9/22/17.
 *
 * https://www.ilexir.co.uk/datasets/index.html
 * Yannakoudakis, Helen and Briscoe, Ted and Medlock, Ben,
 * ‘A New Dataset and Method for Automatically Grading ESOL Texts’,
 * Proceedings of the 49th Annual Meeting of the Association for
 * Computational Linguistics: Human Language Technologies.
 *
 */
public class CLCFCE {

    public static HashMap<String,ArrayList<String>> docs = new HashMap<>();

    /***************************************************************
     * @return a list of lines of text with markup removed
     */
    private static void processFile(ArrayList<String> doc, String filename) {

        ArrayList<String> result = new ArrayList<>();
        for (String s : doc) {
            if (s.contains("<p>") && s.contains("</p>")) {
                s = s.replaceAll("<c>[^<]+</c>",""); // remove corrections to the English
                result.add(StringUtil.removeHTML(s));
            }
        }
        docs.put(filename,result);
    }

    /***************************************************************
     */
    public static void readFiles(String dir) {

        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".xml")) {
                    ArrayList<String> doc = OntoNotes.readFile(filePath.toString());
                    processFile(doc,filePath.toString());
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in OntoNotes.readOnfFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void readCorpus() {

        System.out.println("Info in CLCFCE.readCorpus(): starting read");
        String corpora = System.getenv("CORPORA");
        String dataDir = corpora + File.separator + "fce-released-dataset/dataset";
        readFiles(dataDir);
    }
}
