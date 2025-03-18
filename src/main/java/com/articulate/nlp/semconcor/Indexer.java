package com.articulate.nlp.semconcor;

import com.articulate.nlp.corpora.CorpusReader;
import com.articulate.nlp.corpora.OntoNotes;
import com.articulate.nlp.semRewrite.*;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    //public static final String JDBC_STRING = "jdbc:h2: + System.getenv("CORPORA") + "/transJudge";
    public static final String JDBC_STRING = "jdbc:h2:" + System.getenv("CORPORA") + "/FCE";
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

        StringBuilder result = new StringBuilder();
        System.out.println("Searcher.depToTokens(): " + dep);
        if (dep == null)
            return null;
        Lexer lex = new Lexer(dep);
        CNF cnf = CNF.parseSimple(lex);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (!Procedures.isProcPred(l.pred)) {
                    if (Literal.tokenNum(l.arg1) != -1 && Literal.tokenNum(l.arg2) != -1) {
                        if ("".equals(result.toString()))
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
        catch(SQLException e) {
            System.err.println(e.getMessage());
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
        catch(SQLException e) {
            System.err.println(e.getMessage());
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
        catch(SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     */
    public static boolean trapNumberHack(String s) {

        Pattern p = Pattern.compile("(\\d,\\d\\d\\d[^\\d])");
        Matcher m = p.matcher(s);
        return m.find();
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
            System.err.println("Error in extractOneAugmentLine(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        int sentnum = 0;
        List<CoreLabel> tokens;
        CNF cnf;
        List<String> dependenciesList;
        String str, sent, dep;
        for (CoreMap sentence : sentences) {
            System.out.println("extractOneAugmentLine(): sentence: " + sentence);
            sentnum++;
            tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            if (tokens.size() > 2 && tokens.size() < limit &&
                    initialCapital(tokens) && endPunctuation(tokens)) {
                cnf = interp.interpretGenCNF(sentence);
                dependenciesList = cnf.toListString();
                try (Statement stmt = conn.createStatement()) {
                    str = "insert into content (cont,dependency,file,sentnum,linenum) values ";
                    sent = sentence.toString();
                    sent = sent.replaceAll("'","''");
                    dep = dependenciesList.toString();
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
                catch(SQLException e) {
                    System.err.println(e.getMessage());
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
        catch (IOException e) {
            System.err.println("Error in Indexer.storeCorpusText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        try {
            String corporaDir = System.getenv("CORPORA");
            String dir = corporaDir + File.separator + "transJudge";
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".txt")) {
                    List<String> doc = OntoNotes.readFile(filePath.toString());
                    System.out.println("storeCorpusText(): " + filePath.toString());
                    String line;
                    for (int i = 0; i < doc.size(); i++) {
                        line = doc.get(i);
                        if (line.matches("\\d*[\\.\\)] .*"))
                            extractOneAugmentLine(interp,conn,line,tokensMax,filePath.getFileName().toString(),i);
                    }
                }
            });
        }
        catch(IOException e) {
            System.err.println(e.getMessage());
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
        catch (IOException e) {
            System.err.println("Error in Indexer.storeDocCorpusText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        try {
            List<String> doc;
            String line;
            for (String fname : docs.keySet()) {
                doc = docs.get(fname);
                System.out.println("Info in storeDocCorpusText(): processing file: " + fname);
                for (int i = 0; i < doc.size(); i++) {
                    line = doc.get(i);
                    extractOneAugmentLine(interp,conn,line,tokensMax,fname,i);
                }
            }
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
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
        catch (IOException e) {
            System.err.println("Error in Indexer.storeWikiText(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        try {
            String corporaDir = System.getenv("CORPORA");
            String file = "wikipedia2text-extracted.txt";
            InputStream in = new FileInputStream(corporaDir + "/wikipedia/" + file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            String line;
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
        catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * clear all the database content
     */
    public static void clearDB(Connection conn) throws Exception {

        Statement stmt = conn.createStatement();
        List<String> commands = new ArrayList<>();
        String query = "delete from index";
        commands.add(query);
        query = "delete from depindex";
        commands.add(query);
        query = "delete from counts";
        commands.add(query);
        query = "delete from content";
        commands.add(query);
        try {
            for (String s : commands) {
                stmt.execute(s);
            }
        } catch (SQLException e ) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
            stmt.close();
        }
    }

    /***************************************************************
     * create the database content
     */
    public static void createDB(Connection conn) throws Exception {

        System.out.println("createDB()");
        Statement stmt = conn.createStatement();
        List<String> commands = new ArrayList<>();
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
                stmt.execute(s);
            }
        }
        catch (SQLException e ) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
            stmt.close();
        }
    }

    /***************************************************************
     * Checks the database content for a particular table before
     * writing to it
     * @param conn the DB connection for checking
     * @param table the table to check for
     * @return true if table is found
     */
    public static boolean checkForTable(Connection conn, String table) {

        boolean retVal = false;
        try {
            DatabaseMetaData dbm = conn.getMetaData();
            try (ResultSet tables = dbm.getTables(null, null, table, null)) {
                retVal = tables.next();
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return retVal;
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
        Connection conn = null;
        String corporaDir = System.getenv("CORPORA");
        if (args != null && args.length > 1 && args[0].equals("-i")) {
            conn = DriverManager.getConnection("jdbc:h2:" + corporaDir + args[1], UserName, "");
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
                if (checkForTable(conn, "INDEX"))
                    clearDB(conn);
                else
                    createDB(conn);
            }
            catch (SQLException e ) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
            System.out.println("cleared/created db");
        }
        else
            help();
        conn.close();
    }
}
