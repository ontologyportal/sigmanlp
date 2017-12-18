package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
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
    public HashMap<String,ArrayList<String>> docs = new HashMap<>();
    public String fileExt = ".xml";
    public String regExGoodLine = "<p>.+</p>";
    public String regExRemove = "<c>[^<]+</c>";
    public String regExReplacement = "";
    public String corpora = System.getenv("CORPORA");
    public String dataDir = "";
    public boolean oneFile = false;
    public boolean removeHTML = true;
    public int startline = 0;

    /***************************************************************
     * Read and process a single corpus file one line at a time.
     */
    public void processFileByLine(Connection conn, String file) {

        try {
            Interpreter interp = new Interpreter();
            interp.initialize();
            InputStream in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            KBmanager.getMgr().initializeOnce();
            System.out.println("Info in CorpusReader.processFileByLine(): " + file);
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (StringUtil.emptyString(regExGoodLine) || line.matches(regExGoodLine)) {
                    if (!StringUtil.emptyString(regExRemove))
                        line = line.replaceAll(regExRemove,regExReplacement); // remove corrections to the English
                    if (removeHTML)
                        line = StringUtil.removeHTML(line);
                    Indexer.extractOneAugmentLine(interp,conn,line,Indexer.tokensMax,file,lnr.getLineNumber());
                    System.out.println("Info in CorpusReader.processFileByLine(): line: " + line);
                }
            }
        }
        catch (Exception e) {
            System.out.println("Error in CorpusReader.processFileByLine(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * @return a list of lines of text with markup removed
     */
    private void processFile(ArrayList<String> doc, String filename) {

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
    public void readFiles(String dir) {

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
    public void readCorpus() {

        System.out.println("Info in CorpusReader.readCorpus(): starting read");
        readFiles(dataDir);
    }

    /***************************************************************
     */
    public static void main(String[] args) {

    }
}
