package com.articulate.nlp.corpora;

import com.articulate.sigma.StringUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

/** This code is copyright Teknowledge (c) 2003, Articulate Software (c) 2003-2017,
 Infosys (c) 2017-present.

 This software is released under the GNU Public License
 <http://www.gnu.org/copyleft/gpl.html>.

 Please cite the following article in any publication with references:

 Pease A., and Benzmüller C. (2013). Sigma: An Integrated Development Environment
 for Logical Theories. AI Communications 26, pp79-97.  See also
 http://github.com/ontologyportal
 */
public class CorpusReader {

    // Key is filename, value is a list of lines of text.  Sentences
    // must not cross a line.
    public static HashMap<String,ArrayList<String>> docs = new HashMap<>();
    public static String fileExt = ".xml";
    public static String regExGoodLine = "<p>.+</p>";
    public static String regExRemove = "<c>[^<]+</c>";
    public static String regExReplacement = "";
    public static String corpora = System.getenv("CORPORA");
    public static String dataDir = corpora + File.separator + "fce-released-dataset/dataset";
    public static boolean oneFile = false;
    public static boolean removeHTML = true;
    public static int startline = 0;

    /***************************************************************
     * @return a list of lines of text with markup removed
     */
    private static void processFile(ArrayList<String> doc, String filename) {

        System.out.println("Info in CorpusReader.processFile(): " + filename);
        ArrayList<String> result = new ArrayList<>();
        for (String s : doc) {
            if (StringUtil.emptyString(regExGoodLine) || s.matches(regExGoodLine)) {
                if (!StringUtil.emptyString(regExRemove))
                    s = s.replaceAll(regExRemove,regExReplacement); // remove corrections to the English
                if (removeHTML)
                    s = StringUtil.removeHTML(s);
                System.out.println("Info in CorpusReader.processFile(): line: " + s);
                result.add(s);
            }
        }
        docs.put(filename,result);
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
            System.out.println("Error in CorpusReader.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
        return result;
    }

    /***************************************************************
     */
    public static void readFiles(String dir) {

        try {
            if (oneFile) {
                ArrayList<String> doc = readFile(dir);
                processFile(doc, dir);
            }
            else {
                Files.walk(Paths.get(dir)).forEach(filePath -> {
                    if (Files.isRegularFile(filePath) &&
                            filePath.toString().endsWith(fileExt)) {
                        ArrayList<String> doc = readFile(filePath.toString());
                        processFile(doc, filePath.getFileName().toString());
                    }
                });
            }
        }
        catch (IOException ioe) {
            System.out.println("Error in CorpusReader.readFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void readCorpus() {

        System.out.println("Info in CorpusReader.readCorpus(): starting read");
        readFiles(dataDir);
    }
}
