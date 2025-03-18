package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    public Map<String,List<String>> docs = new HashMap<>();
    public String fileExt = ".xml";
    public String regExGoodLine = "<p>.+</p>";
    public String regExRemove = "<c>[^<]+</c>";
    public String regExReplacement = "";
    public static String corpora = System.getenv("CORPORA");
    public String dataDir = "";
    public boolean oneFile = false;
    public boolean removeHTML = true;
    public int startline = 0;

    /***************************************************************
     * Find all corpus databases under the corpora directory
     */
    public static List<String> findCorporaDBs() {

        List<String> result = new ArrayList<>();
        try {
            Path p = Paths.get(corpora);
            final int maxDepth = 10;
            Stream<Path> matches = Files.find(p, maxDepth, (path, basicFileAttributes) ->
                    String.valueOf(path).endsWith(".mv.db"));
            matches.map(path -> path.toString().substring(corpora.length()+1,path.toString().length()-6)).forEach(result::add);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /***************************************************************
     * Read and process a single corpus file one line at a time.
     */
    public void processFileByLine(Connection conn, String file) {

        Interpreter interp = new Interpreter();
        try (InputStream in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader)) {
            KBmanager.getMgr().initializeOnce();
            interp.initialize();
            System.out.println("Info in CorpusReader.processFileByLine(): " + file);
            String line;
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
        catch (IOException e) {
            System.err.println("Error in CorpusReader.processFileByLine(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * store in docs a list of lines of text with markup removed
     */
    private void processFile(List<String> doc, String filename) {

        System.out.println("Info in CorpusReader.processFile(): " + filename);
        List<String> result = new ArrayList<>();
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
     */
    public static int countLines(File file) throws IOException {

        int lines = 0;
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8 * 1024]; // BUFFER_SIZE = 8 * 1024
            int read;
            while ((read = fis.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == '\n') lines++;
                }
            }
        } // BUFFER_SIZE = 8 * 1024
        return lines;
    }

    /***************************************************************
     * Read a text file into lines
     */
    public static List<String> readFile(String filename) {

        List<String> result = new ArrayList<>();
        try {
            File f = new File(filename);
            long len = f.length();
            int lines = 0;
            if (len > 1000000) {
                lines = countLines(f);
            }
            try (Reader r = new FileReader(filename);
                LineNumberReader lr = new LineNumberReader(r)) {
                String line;
                long counter = 0;
                while ((line = lr.readLine()) != null) {
                    result.add(line);
                    counter++;
                    if (counter % 10000 == 0) {
                        if (lines == 0)
                            System.out.print(".");
                        else
                            System.out.print("\b\b\b\b" + (counter * 100) / lines + "%");
                    }
                }
            }
            System.out.println();
        }
        catch (IOException i) {
            System.err.println("Error in CorpusReader.readFile() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
        return result;
    }

    /***************************************************************
     */
    public void readFiles(String dir) {

        try {
            if (oneFile) {
                List<String> doc = readFile(dir);
                processFile(doc, dir);
            }
            else {
                Files.walk(Paths.get(dir)).forEach(filePath -> {
                    if (Files.isRegularFile(filePath) &&
                            filePath.toString().endsWith(fileExt)) {
                        List<String> doc = readFile(filePath.toString());
                        processFile(doc, filePath.getFileName().toString());
                    }
                });
            }
        }
        catch (IOException ioe) {
            System.err.println("Error in CorpusReader.readFiles(): " + ioe.getMessage());
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

        System.out.println(findCorporaDBs());
    }
}
