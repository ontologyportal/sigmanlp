package com.articulate.nlp.semconcor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.articulate.nlp.TFIDF;
import com.articulate.nlp.corpora.CorpusReader;
import com.articulate.nlp.corpora.OntoNotes;
import com.articulate.nlp.corpora.CLCFCE;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.*;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.corpora.wikipedia.SimpleSentenceExtractor;
/*
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

Read wikipedia text in wikimedia format using SimpleSentenceExtractor
and store in an H2 SQL database

java -jar h2*.jar

to start the server

DB schema created in createDB() is

 CONTENT
   FILE
   SENTNUM
   CONT
   DEPENDENCY
   LINENUM
 COUNTS
   TOKEN
   COUNT
 DEPINDEX
   TOKEN
   FILE
   SENTNUM
   LINENUM
 INDEX
   TOKEN
   FILE
   SENTNUM
   LINENUM
*/

public class Indexer {

    public static final int tokensMax = 25;
    //public static final String JDBCString = "jdbc:h2:~/corpora/transJudge";
    public static final String JDBCString = "jdbc:h2:~/corpora/FCE";
    public static String UserName = "sa";
    public static int startline = 0;

    /****************************************************************
     */
    private static boolean initialCapital(List<CoreLabel> tokens) {

        return Character.isUpperCase(tokens.get(0).originalText().charAt(0));
    }

    /****************************************************************
     * exclude questions
     */
    private static boolean endPunctuation(List<CoreLabel> tokens) {

        String last = tokens.get(tokens.size()-1).originalText();
        return (last.equals(".") || last.equals("!"));
    }

    /****************************************************************
     * remove literals with tokens that don't have a token number
     */
    private static String revertAnnotations (String dep) {

        StringBuffer result = new StringBuffer();
        System.out.println("Searcher.depToTokens(): " + dep);
        if (dep == null)
            return null;
        Lexer lex = new Lexer(dep);
        CNF cnf = CNF.parseSimple(lex);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (!Procedures.isProcPred(l.pred)) {
                    if (Literal.tokenNum(l.arg1) != -1 && Literal.tokenNum(l.arg2) != -1) {
                        if (result.toString() == "")
                            result.append(",");
                        result.append(l.toString());
                    }
                }
            }
        }
        return result.toString();
    }

    /****************************************************************
     */
    private static void storeCount(Connection conn, String tok) {

        try {
            String str = "select count from counts where token='" + tok + "';";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(str);

            if (!rs.next()) {
                str = "insert into counts (token,count) values ";
                stmt = conn.createStatement();
                stmt.execute(str + "('" + tok + "', '1');");
            }
            else {
                int count = rs.getInt("count");
                count++;
                str = "update counts set count='" + count + "' where token='" + tok + "';";
                stmt = conn.createStatement();
                stmt.execute(str);
            }
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     */
    private static void storeSentenceToken(Connection conn, String file, int sentnum, int linenum, CoreLabel tok) {

        try {
            String str = "insert into index (token,file,sentnum,linenum) values ";
            Statement stmt = conn.createStatement();
            String token = tok.originalText();
            token = token.replaceAll("'","''");
            stmt.execute(str + "('" + token + "', '" +
                    file + "', '" + sentnum + "', '" + linenum + "');");
            storeCount(conn, token);
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     * exclude questions
     */
    private static void storeDependency(Connection conn, String file, int sentnum, int linenum, String token) {

        try {
            String str = "insert into depindex (token,file,sentnum,linenum) values ";
            Statement stmt = conn.createStatement();
            token = token.replaceAll("'","''");
            stmt.execute(str + "('" + token + "', '" +
                    file + "', '" + sentnum + "', '" + linenum + "');");
            storeCount(conn, token);
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     */
    public static boolean trapNumberHack(String s) {

        Pattern p = Pattern.compile("(\\d,\\d\\d\\d[^\\d])");
        Matcher m = p.matcher(s);
        if (m.find())
            return true;
        else
            return false;
    }

    /****************************************************************
     */
    public static String removeBrackets(String s) {

         return s.replaceAll("(\\[|\\]|\\(|\\)|\\{|\\})","");
    }

    /****************************************************************
     * Take one line of plan text, parse it into dependencies augmented
     * by the semantic rewriting system and add
     * indexes for the raw tokens and the tokens in the dependency parse
     * into the database. The DB tables are
     * content - the sentence and its dependency parse
     * index - tokens and the sentences in which they are found
     * depindex - tokens and the dependencies in which they are found
     * counts - the number of occurrences of a token, used to order joins
     */
    public static void extractOneAugmentLine(Interpreter interp, Connection conn, String line,
                                             int limit, String file, int linenum) {


        if (trapNumberHack(line))
            return;
        line = removeBrackets(line);
        System.out.println("extractOneAugmentLine(): " + line);
        List<CoreMap> sentences = null;
        try {
            Annotation wholeDocument = new Annotation(line);
            wholeDocument.set(CoreAnnotations.DocDateAnnotation.class, "2017-09-17");
            interp.p.pipeline.annotate(wholeDocument);
            sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        }
        catch (Exception e) {
            System.out.println("Error in extractOneAugmentLine(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        int sentnum = 0;
        for (CoreMap sentence : sentences) {
            System.out.println("extractOneAugmentLine(): sentence: " + sentence);
            sentnum++;
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            if (tokens.size() > 2 && tokens.size() < limit &&
                    initialCapital(tokens) && endPunctuation(tokens)) {
                CNF cnf = interp.interpretGenCNF(sentence);
                List<String> dependenciesList = cnf.toListString();
                try {
                    String str = "insert into content (cont,dependency,file,sentnum,linenum) values ";
                    Statement stmt = conn.createStatement();
                    String sent = sentence.toString();
                    sent = sent.replaceAll("'","''");
                    String dep = dependenciesList.toString();
                    dep = dep.replaceAll("'","''");
                    str = str + "('" + sent + "', '" +
                            dep + "', '" + file + "', '" +
                            sentnum + "', '" + linenum + "');";
                    stmt.execute(str);
                    System.out.println("extractOneAugmentLine(): " + str);

                    for (CoreLabel tok : tokens)
                        storeSentenceToken(conn,file,sentnum,linenum,tok);

                    for (String s : dependenciesList) {
                        Literal l = new Literal(s);
                        storeDependency(conn,file,sentnum,linenum,l.arg1);
                        storeDependency(conn,file,sentnum,linenum,l.arg2);
                        storeDependency(conn,file,sentnum,linenum,l.pred);
                    }
                }
                catch(Exception e) {
                    System.out.println(e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /****************************************************************
     */
    public static void storeCorpusText(Connection conn) {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println("Error in Indexer.storeCorpusText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        try {
            String corporaDir = System.getenv("CORPORA");
            String dir = corporaDir + File.separator + "transJudge";
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".txt")) {
                    ArrayList<String> doc = OntoNotes.readFile(filePath.toString());
                    System.out.println("storeCorpusText(): " + filePath.toString());
                    for (int i = 0; i < doc.size(); i++) {
                        String line = doc.get(i);
                        if (line.matches("\\d*[\\.\\)] .*"))
                            extractOneAugmentLine(interp,conn,line,tokensMax,filePath.getFileName().toString(),i);
                    }
                }
            });
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     * @param docs Key is filename, value is a list of lines of text.
     *            Sentences must not cross a line.
     */
    public static void storeDocCorpusText(Connection conn, HashMap<String,ArrayList<String>> docs) {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println("Error in Indexer.storeDocCorpusText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        try {
            for (String fname : docs.keySet()) {
                ArrayList<String> doc = docs.get(fname);
                System.out.println("Info in storeDocCorpusText(): processing file: " + fname);
                for (int i = 0; i < doc.size(); i++) {
                    String line = doc.get(i);
                    extractOneAugmentLine(interp,conn,line,tokensMax,fname,i);
                }
            }
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     * http://www.evanjones.ca/software/wikipedia2text.html
     * http://www.evanjones.ca/software/wikipedia2text-extracted.txt.bz2 plain text of 10M words
     */
    public static void storeWikiText(Connection conn) {

        int maxSent = 1000;
        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println("Error in Indexer.storeWikiText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        int count = 0;
        try {
            String corporaDir = System.getenv("CORPORA");
            String file = "wikipedia2text-extracted.txt";
            InputStream in = new FileInputStream(corporaDir + "/wikipedia/" + file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            String line = null;
            if (startline > 0) {
                while ((line = lnr.readLine()) != null && lnr.getLineNumber() < startline);
            }
            long t1 = System.currentTimeMillis();
            while ((line = lnr.readLine()) != null) { // && count++ < maxSent) {
                extractOneAugmentLine(interp,conn,line,tokensMax,file,lnr.getLineNumber());
                if (lnr.getLineNumber() % 1000 == 0)
                    System.out.println("storeWikiText(): line: " + lnr.getLineNumber());
            }
            double seconds = ((System.currentTimeMillis() - t1) / 1000.0);
            System.out.println("time to process: " + seconds + " seconds");
            System.out.println("time to process: " + (seconds / maxSent) + " seconds per sentence");
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * clear all the database content
     */
    public static void clearDB(Connection conn) throws Exception {

        Statement stmt = null;
        ArrayList<String> commands = new ArrayList<>();
        String query = "delete from index";
        commands.add(query);
        query = "delete from depindex";
        commands.add(query);
        query = "delete from counts";
        commands.add(query);
        query = "delete from content";
        commands.add(query);
        for (String s : commands) {
            stmt = conn.createStatement();
            boolean res = stmt.execute(s);
        }
    }

    /***************************************************************
     * clear all the database content
     */
    public static void createDB(Connection conn) throws Exception {

        System.out.println("createDB()");
        Statement stmt = null;
        ArrayList<String> commands = new ArrayList<>();
        String command = "CREATE TABLE COUNTS(TOKEN VARCHAR(50), COUNT INT);";
        commands.add(command);
        command = "CREATE TABLE DEPINDEX(TOKEN VARCHAR(50),FILE VARCHAR(100),SENTNUM INT,LINENUM INTEGER);";
        commands.add(command);
        command = "CREATE TABLE INDEX(TOKEN VARCHAR(50),FILE VARCHAR(100),SENTNUM INT,LINENUM INTEGER);";
        commands.add(command);
        command = "CREATE TABLE CONTENT(FILE VARCHAR(100),SENTNUM INT,CONT VARCHAR(1000),DEPENDENCY VARCHAR(2500),LINENUM INTEGER);";
        commands.add(command);
        try {
            for (String s : commands) {
                stmt = conn.createStatement();
                boolean res = stmt.execute(s);
            }
        }
        catch (SQLException e ) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void help() {

        System.out.println("Semantic Concordancer Indexing - commands:");
        System.out.println("    -c <db>         Clear db or create if not present");
        System.out.println("    -i <path>       Index corpus in <path> under corpus directory");
        System.out.println("    -w <line> <db>  index Wikipedia starting at line with db file name");
        System.out.println("    -h              show this Help message");
    }

    /***************************************************************
     */
    public static void main(String[] args) throws Exception {

        System.out.println();
        KBmanager.getMgr().initializeOnce();
        Class.forName("org.h2.Driver");
        Connection conn = null;
        String corporaDir = System.getenv("CORPORA");
        if (args != null && args.length > 1 && args[0].equals("-i")) {
            conn = DriverManager.getConnection("jdbc:h2:~/corpora/" + args[1], UserName, "");
            storeCorpusText(conn);
        }
        else if (args != null && args.length > 2 && args[0].equals("-w")) {
            String dbfilename = args[2];
            conn = DriverManager.getConnection("jdbc:h2:" + corporaDir + "/wikipedia/" + dbfilename,UserName, "");
            startline = Integer.parseInt(args[1]);
            CorpusReader cr = new CorpusReader();
            cr.regExGoodLine = "";
            cr.regExRemove = "";
            String file = corporaDir + "/wikipedia/wikipedia2text-extracted.txt";
            cr.processFileByLine(conn,file);
            //storeWikiText(conn);
        }
        if (args != null && args.length > 0 && args[0].equals("-c")) {
            String dbFilename = "wiki";
            if (args.length > 1 && !StringUtil.emptyString(args[1]))
                dbFilename = args[1];
            try {
                conn = DriverManager.getConnection("jdbc:h2:" + corporaDir + File.separator + dbFilename, UserName, "");
                clearDB(conn);
            }
            catch (SQLException e ) {
                System.out.println(e.getMessage());
                e.printStackTrace();
                createDB(conn);
            }
            System.out.println("cleared/created db");
        }
        else {
            help();
        }
        conn.close();
    }
}
