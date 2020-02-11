package com.articulate.nlp;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * This code is copyright CloudMinds 2017.
 * This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
 * Users of this code also consent, by use of this code, to credit Articulate Software
 * and Teknowledge in any writings, briefings, publications, presentations, or
 * other representations of any software which incorporates, builds on, or uses this
 * code.  Please cite the following article in any publication with references:
 * Pease, A., (2003). The Sigma Ontology Development Environment,
 * in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
 * August 9, Acapulco, Mexico.
 *
 * Created by areed on 3/4/15.
 * Utility class for TFIDF.java
 */
public class TFIDFUtil {

    /** ***************************************************************
     * @param filename file to be read
     * @param separateSentences should sentences be separated if they occur on one line
     * @return list of strings from each line of the document
     * This method reads in a text file, breaking it into single line documents
     * Currently, sentences are not separated if they occur on the same line.
     */
    public static List<String> readFile(String filename, boolean separateSentences) throws IOException {
        
        List<String> documents = Lists.newArrayList();
        URL fileURL = Resources.getResource(filename);
        File f = new File(filename);
        BufferedReader bf = new BufferedReader(new FileReader(fileURL.getPath()));
        String line;
        while ((line = bf.readLine()) != null) {
            if (line == null || line.equals("")) 
                continue;
            documents.add(line);
        }
        return documents;
    }

    /** ***************************************************************
     */
    public static TFIDF indexDocumentation() {

        return indexDocumentation(false);
    }

    /** ***************************************************************
     * Perform TFIDF indexing of SUMO relations and their documentation.
     * Each relation with its documentation string appended is considered
     * a "document"
     */
    public static TFIDF indexDocumentation(boolean relOnly) {

        List<String> documents = new ArrayList<>();
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB("SUMO");
        ArrayList<Formula> forms = kb.ask("arg",0,"documentation");
        for (Formula f : forms) {
            String term = f.getStringArgument(1);
            String doc = f.getStringArgument(3);
            if (!relOnly || kb.isRelation(term))
                documents.add(term + " : " + doc);
        }

        TFIDF cb = null;
        try {
            String dirname =  KBmanager.getMgr().getPref("kbDir") + File.separator + "WordNetMappings";
            String fname = dirname + File.separator + "stopwords.txt";
            cb = new TFIDF(documents, fname);
        }

        catch (IOException e) {
            System.out.println("Error in TFIDFUtil.indexDocumentation(): " + e.getMessage());
            e.printStackTrace();
            return cb;
        }
        return cb;
    }

    /** ***************************************************************
     */
    public static void printHelp() {

        System.out.println("Usage: ");
        System.out.println("TFIDF -h         % show this help info");
        System.out.println("      -f \"string\"   % find best match in SUMO documentation");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        TFIDF cb = indexDocumentation();
        if (args != null && args.length > 0 && args[0].equals("-h")) {
            printHelp();
        }
        else if (args != null && args.length > 1 && args[0].equals("-f")) {
            System.out.println(cb.matchInput(args[1],10));
        }
        else
            printHelp();
    }
}
