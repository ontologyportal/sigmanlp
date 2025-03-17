package com.articulate.nlp.semconcor;

import com.articulate.nlp.semRewrite.*;
import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.KButilities;
import com.articulate.sigma.PasswordService;
import com.articulate.sigma.utils.StringUtil;

import com.google.common.base.Strings;

import java.io.IOException;
import java.sql.*;
import java.util.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/*
Copyright 2017 Articulate software
          2017-     Infosys

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
*/
@WebListener
public class Searcher implements ServletContextListener {

    private static final String H2_DRIVER = "org.h2.Driver";
    private static Connection conn = null;

    public static int countSize = 100; // parameter to optimize searching
    public static boolean debug = false;

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        KButilities.getInstance().contextInitialized(servletContextEvent);
        System.out.println("Starting " + Searcher.class.getName() + " Service...");
    }

    // H2 shutdown guidance from: https://github.com/spring-projects/spring-boot/issues/21221
    //                       and: https://stackoverflow.com/questions/9972372/what-is-the-proper-way-to-close-h2
    // Fix for issue #135
    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        PasswordService pws = PasswordService.getInstance();
        pws.contextDestroyed(servletContextEvent);
        KButilities.getInstance().contextDestroyed(servletContextEvent);
        System.out.println("Shutting down " + Searcher.class.getName() + " Service...");
        org.h2.Driver.unload();
        System.out.println("Deregistering and shutting down: " + H2_DRIVER);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
        catch (SQLException e) {
            System.err.println(H2_DRIVER + " shutdown issues: " + e.getLocalizedMessage());
        }
    }

    /***************************************************************
     * Add HTML markup highlighting the matching phrase
     */
    public static String highlightSent(String s, String phrase) {

        int i = s.indexOf(phrase);
        if (i == -1)
            return s;
        int start = i;
        int end = i + phrase.length();
        return s.substring(0,start) + "<b>" + s.substring(start,end) +
                "</b>" + s.substring(end,s.length());
    }

    /***************************************************************
     * Add HTML markup highlighting the matching dependency.  Iterate
     * through the clauses, highlighting each bound clause.
     * @return a String with HTML boldface of the bound clauses
     */
    public static String highlightDep(String pattern, String depParse) {

        if (debug) System.out.println("Searcher.highlightDep(): pattern: " + pattern + "\ndepParse: " + depParse);
        if (StringUtil.emptyString(pattern))
            return depParse;
        Lexer lex = new Lexer(StringUtil.removeEnclosingCharPair(pattern,1,'[',']'));
        CNF patcnf = CNF.parseSimple(lex);

        lex = new Lexer(StringUtil.removeEnclosingCharPair(depParse,1,'[',']'));
        CNF depcnf = CNF.parseSimple(lex);
        Subst bindings = patcnf.unify(depcnf);

        if (debug) System.out.println("Searcher.highlightDep(): bindings: " + bindings);
        if (debug) System.out.println("Searcher.highlightDep(): cnf: " + depcnf);
        StringBuilder result = new StringBuilder();
        for (Clause c : depcnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (result.length() > 0)
                    result.append(", ");
                if (l.bound) {
                    result.append("<b>").append(l.toString().substring(1)).append("</b>"); // don't print the 'X' bound flag
                }
                else {
                    result.append(l);
                }
            }
        }
        return result.toString();
    }

    /***************************************************************
     * a testing method to help validate there's a connection to the
     * intended DB
     */
    public static void showTable(Connection conn, String tableName) {

        System.out.println("showTable(): for " + tableName);
        Statement stmt;
        try {
            stmt = conn.createStatement();
            ResultSet res = stmt.executeQuery("show columns from " + tableName + ";");
            while (res.next())
                System.out.println(res.getString("FIELD"));
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * @param key a  key of the form filename#sentencenum#linenum
     */
    public static String fetchSentenceFromKey(Connection conn, String key) {

        Statement stmt;
        String result = null;
        try {
            String[] sar = key.split("#");
            String query = "select cont from content where file='" + sar[0] +
                    "' and sentnum=" + sar[1] + " and linenum=" + sar[2] + ";";
            if (debug) System.out.println("Searcher.fetchSentenceFromKey(): query: " + query);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                result = rs.getString("CONT");
            }
            return result;
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /***************************************************************
     * @param key a  key of the form filename#sentencenum#linenum
     */
    public static String fetchDepFromKey(Connection conn, String key) {

        Statement stmt;
        String result = null;
        try {
            String[] sar = key.split("#");
            String query = "select dependency from content where file='" + sar[0] +
                    "' and sentnum=" + sar[1] + " and linenum=" + sar[2] + ";";
            if (debug) System.out.println("Searcher.fetchDepFromKey(): query: " + query);
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                result = rs.getString("DEPENDENCY");
            }
            return result;
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /***************************************************************
     * @param keys a set of keys of the form filename#sentencenum#linenum
     */
    public static void fetchResultStrings(Connection conn, Set<String> keys,
                                                     List<String> sentences,
                                                     List<String> dependencies) {

        Statement stmt;
        String[] sar;
        String query;
        ResultSet rs;
        try {
            for (String s : keys) {
                sar = s.split("#");
                query = "select cont,dependency from content where file='" + sar[0] +
                        "' and sentnum=" + sar[1] + " and linenum=" + sar[2] + ";";
                if (debug) System.out.println("Searcher.fetchResultStrings(): query: " + query);
                stmt = conn.createStatement();
                rs = stmt.executeQuery(query);
                while (rs.next()) {
                    sentences.add(rs.getString("CONT"));
                    dependencies.add(rs.getString("DEPENDENCY"));
                }
            }
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * Compile a frequency list for all the given tokens.
     * Note that since AVPair takes strings, the numerical counts
     * must be padded with 0's to maintain their order.  Use 10 digits
     * so we can handle billions of terms.
     */
    public static Set<AVPair> makeCountIndex(Connection conn,
                                                 List<String> tokens) {

        Set<AVPair> countIndex = new TreeSet<>();
        try {
            Statement stmt = conn.createStatement();
            if (debug) System.out.println("Searcher.makeCountIndex(): " + conn.getCatalog());
            String query;
            ResultSet rs;
            int count;
            String countString;
            AVPair avp;
            for (String s : tokens) {
                query = "select * from counts where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromIndex(): query: " + query);
                rs = stmt.executeQuery(query);
                while (rs.next()) {
                    count = rs.getInt("COUNT");
                    countString = Integer.toString(count);
                    countString = StringUtil.fillString(countString,'0',10,true);
                    avp = new AVPair(countString,s);
                    countIndex.add(avp);
                }
            }
            if (stmt != null)
                stmt.close(); // closes the rs
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return countIndex;
    }

    /***************************************************************
     * @return a set of keys of the form filename#sentencenum#linenum
     * for sentences that contain the given tokens in the given index.
     * If a given token has 100 times the number of instances of the
     * current token set ignore it, since doing the union of the indexes
     * will be more expensive than just searching for the token in the
     * result set.
     */
    public static Set<String> fetchFromSortedIndex(Connection conn, String indexName,
                                                        List<String> tokens) {

        Set<AVPair> sortedCounts = makeCountIndex(conn,tokens);

        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): " + indexName + "\n" + tokens);
        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): sorted counts: " + sortedCounts);
        Set<String> result = new HashSet<>();
        if (!indexName.equalsIgnoreCase("INDEX") && !indexName.equalsIgnoreCase("DEPINDEX")) {
            System.out.println("Error in Searcher.fetchFromSortedIndex(): bad table name " + indexName);
            return result;
        }
        Statement stmt;
        String s, query, filename;
        int termCount, sentNum, lineNum;
        ResultSet rs;
        Set<String> newresult = new HashSet<>();
        try {
            for (AVPair avp : sortedCounts) {
                s = avp.value;
                if (debug) System.out.println("Searcher.fetchFromSortedIndex(): term count: " + avp.attribute);
                termCount = Integer.parseInt(avp.attribute);
                if (!result.isEmpty() && termCount > countSize * result.size())
                    return result; // don't try to union really big indexes
                query = "select * from " + indexName + " where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromSortedIndex(): query: " + query);
                stmt = conn.createStatement();
                rs = stmt.executeQuery(query);
                newresult.clear();
                while (rs.next()) {
                    filename = rs.getString("FILE");
                    sentNum = rs.getInt("SENTNUM");
                    lineNum = rs.getInt("LINENUM");
                    newresult.add(filename + "#" + Integer.toString(sentNum) + "#" + Integer.toString(lineNum));
                }
                if (result.isEmpty())
                    result.addAll(newresult);
                else
                    result.retainAll(newresult);
                if (debug) System.out.println("Searcher.fetchFromSortedIndex(): result size: " + result.size());
            }
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): result size: " + result.size());
        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): result: " + result);
        return result;
    }

    /***************************************************************
     * @return a set of keys of the form filename#sentencenum#linenum
     * for sentences that contain the given tokens in the given index
     */
    public static Set<String> fetchFromIndex(Connection conn, String indexName,
                                                 List<String> tokens) {

        if (debug) System.out.println("Searcher.fetchFromIndex(): " + indexName + "\n" + tokens);
        Set<String> result = new HashSet<>();
        if (!indexName.equalsIgnoreCase("INDEX") && !indexName.equalsIgnoreCase("DEPINDEX")) {
            System.out.println("Error in Searcher.fetchFromIndex(): bad table name " + indexName);
            return result;
        }
        Statement stmt;
        ResultSet rs;
        Set<String> newresult = new HashSet<>();
        String filename;
        int sentNum, lineNum;
        try {
            String query;
            for (String s : tokens) {
                query = "select * from " + indexName + " where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromIndex(): query: " + query);
                stmt = conn.createStatement();
                rs = stmt.executeQuery(query);
                newresult.clear();
                while (rs.next()) {
                    filename = rs.getString("FILE");
                    sentNum = rs.getInt("SENTNUM");
                    lineNum = rs.getInt("LINENUM");
                    newresult.add(filename + "#" + Integer.toString(sentNum) + "#" + Integer.toString(lineNum));
                }
                if (result.isEmpty())
                    result.addAll(newresult);
                else
                    result.retainAll(newresult);
                if (debug) System.out.println("Searcher.fetchFromIndex(): result size: " + result.size());
            }
        }
        catch (SQLException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        if (debug) System.out.println("Searcher.fetchFromIndex(): result size: " + result.size());
        return result;
    }

    /***************************************************************
     * Note that dependency patterns must be simple CNF - no disjuncts
     * and only positive literals
     * @param smallcnf the dependency pattern to search for.
     * @param onedep the dependency match candidate
     */
    public static Subst matchDepBind(CNF smallcnf, String onedep) {

        onedep = StringUtil.removeEnclosingCharPair(onedep,2,'[',']'); // two layers of brackets
        if (StringUtil.emptyString(onedep))
            return null;
        Lexer lex = new Lexer(onedep);
        CNF depcnf = CNF.parseSimple(lex);
        Subst bindings = smallcnf.unify(depcnf);
        return bindings;
    }

    /***************************************************************
     * Note that dependency patterns must be simple CNF - no disjuncts
     * and only positive literals
     * @param smallcnf the dependency pattern to search for.
     * @param onedep the dependency match candidate
     */
    public static boolean matchDep(CNF smallcnf, String onedep) {

        Subst bindings = matchDepBind(smallcnf,onedep);
        if (bindings == null)  // remove all that don't unify
            return false;
        else {
            if (debug) System.out.println("Searcher.matchDep(): " + bindings);
            return true;
        }
    }

    /***************************************************************
     * Note that dependency patterns must be simple CNF - no disjuncts
     * and only positive literals
     * returns the indices of all the dependency forms that don't unify
     * with the search dependency form
     * @param dep the dependency pattern to search for.
     * @param dependencies the dependency parses of sentences to check for a match
     * @return a list of integer indexes to items that don't match the pattern
     */
    public static List<Integer> matchDependencies(String dep,
                                     List<String> dependencies) {

        if (debug) System.out.println("Searcher.matchDependencies(): " + dep);
        if (debug) System.out.println("dependencies size: " + dependencies.size());
        List<Integer> result = new ArrayList<>();
        if (Strings.isNullOrEmpty(dep) || dep.equals("null"))
            return result;
        Lexer lex = new Lexer(dep);
        CNF smallcnf = CNF.parseSimple(lex);
        String onedep;
        for (int i = 0; i < dependencies.size(); i++) {
            onedep = dependencies.get(i);
            if (!matchDep(smallcnf,onedep))
                result.add(i);
        }
        return result;
    }

    /***************************************************************
     * returns the indices of all the dependency forms that don't unify
     * with the search dependency form
     * @param phrase the word or phrase to search for.
     * @param sentences the sentences to check for a match
     * @return a list of integer indexes to items that don't match
     */
    public static List<Integer> matchSentences(String phrase,
                                     List<String> sentences) {

        if (debug) System.out.println("Searcher.matchSentences(): " + phrase);
        if (debug) System.out.println("sentences size: " + sentences.size());
        List<Integer> result = new ArrayList<>();
        if (Strings.isNullOrEmpty(phrase))
            return result;
        String onesent;
        for (int i = 0; i < sentences.size(); i++) {
            onesent = sentences.get(i);
            if (!onesent.contains(phrase))
                result.add(i);
        }
        return result;
    }

    /***************************************************************
     * @return a set of keys of the form filename#sentencenum#linenum
     * for sentences that contain the given tokens in the two indexes
     */
    public static Set<String> fetchIndexes(Connection conn,
                                               List<String> sentTokens,
                                               List<String> depTokens) {

        if (debug) System.out.println("fetchIndexes():" + sentTokens + "\n" + depTokens);
        Set<String> result = new HashSet<>();
        if (sentTokens != null && !sentTokens.isEmpty())
            result = fetchFromSortedIndex(conn,"index",sentTokens);
        if (result.isEmpty())
            result = fetchFromSortedIndex(conn,"depindex",depTokens);
        else if (depTokens != null && !depTokens.isEmpty())
            result.retainAll(fetchFromSortedIndex(conn,"depindex",depTokens));
        return result;
    }

    /***************************************************************
     * Convert the textual representation of a dependency parse
     * to a list of tokens found in the dependency
     * @return a list of string tokens
     */
    public static List<String> depToTokens(String dep) {

        if (debug) System.out.println("Searcher.depToTokens(): " + dep);
        List<String> result = new ArrayList<>();
        if (dep == null)
            return result;
        Lexer lex = new Lexer(dep);
        CNF cnf = CNF.parseSimple(lex);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (!Procedures.isProcPred(l.pred)) {
                    if (!Literal.isVariable(l.arg1))
                        result.add(l.arg1);
                    if (!Literal.isVariable(l.arg2))
                        result.add(l.arg2);
                    if (!Literal.isVariable(l.pred))
                        result.add(l.pred);
                }
            }
        }
        return result;
    }

    /***************************************************************
     * dbFilepath is assumed to be under CORPORA directory
     */
    public static void search(String dbFilepath, String phrase, String dep,
                              List<String> sentences,
                              List<String> dependencies) throws Exception {

        if (debug) System.out.println("Searcher.search(): " + phrase + "\n" + dep);
        String searchString = phrase;
        String[] ar = searchString.split(" ");
        List<String> sentTokens = new ArrayList<>();
        sentTokens.addAll(Arrays.asList(ar));

        List<String> depTokens = new ArrayList<>();
        depTokens = depToTokens(dep);

        String corporaDir = System.getenv("CORPORA");
        conn = DriverManager.getConnection("jdbc:h2:" + corporaDir + "/" + dbFilepath + ";AUTO_SERVER=TRUE", Indexer.UserName, "");
        if (debug) System.out.println("main(): Opened DB " + dbFilepath);
        Set<String> result;
        try {
            if (debug) showTable(conn, "index");
            if (debug) showTable(conn, "counts");
            result = fetchIndexes(conn,sentTokens, depTokens);
            if (debug) System.out.println("search(): indexes size: " + result.size());
            List<String> tempSentences = new ArrayList<>();
            List<String> tempDependencies = new ArrayList<>();
            fetchResultStrings(conn,result,tempSentences,tempDependencies); // results returned in tempSentences and tempDependencies

            List<Integer> removeList = matchDependencies(dep,tempDependencies);
            removeList.addAll(matchSentences(phrase,tempSentences));

            List<String> newsent = new ArrayList<>();
            List<String> newdep = new ArrayList<>();
            for (int i = 0; i < tempDependencies.size(); i++) {
                if (!removeList.contains(i)) {
                    newsent.add(tempSentences.get(i));
                    newdep.add(tempDependencies.get(i));
                }
            }
            sentences.addAll(newsent);
            dependencies.addAll(newdep);
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     */
    public static void printSearchResults(String dbFilepath, String phrase, String dep) throws Exception {

        System.out.println("Searcher.printSearchResults(): " + phrase + "\n" + dep);

        try {
            List<String> sentences = new ArrayList<>();
            List<String> dependencies = new ArrayList<>();
            search(dbFilepath, phrase, dep,sentences, dependencies);
            String s, d;
            for (int i = 0; i < sentences.size(); i++) {
                s = sentences.get(i);
                d = dependencies.get(i);
                System.out.println("Sentence: " + s);
                System.out.println("Dependency: " + d);
            }
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /** ***************************************************************
     */
    public static void interactive() {
        interactive(Indexer.JDBC_STRING);
    }

    /** ***************************************************************
     * Allows interactive testing of entering a word, phrase, or dependency
     * pattern and returning the matching sentences, or just the first 10
     * and a count if there are more than 10 results
     */
    public static void interactive(String dbFilepath) {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (IOException e) {
            System.err.println("Error in Searcher.interactive(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        String input, deps;
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter word or phrase: ");
            input = scanner.nextLine().trim();
            if (!Strings.isNullOrEmpty(input) && (input.equals("exit") || input.equals("quit")))
                return;
            System.out.print("Enter dependency pattern: ");
            deps = scanner.nextLine().trim();
            if (!Strings.isNullOrEmpty(input) || !Strings.isNullOrEmpty(deps)) {
                if (Strings.isNullOrEmpty(input) || (!input.equals("exit") && !input.equals("quit"))) {
                    try {
                        printSearchResults(dbFilepath, input, deps);
                    }
                    catch (Exception e) {
                        System.err.println(e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /***************************************************************
     */
    public static void help() {

        System.out.println("Semantic Concordancer Searching - commands:");
        System.out.println("    -i <path>                     Interactive corpus search");
        System.out.println("    -t \"<optional search string>\" test searching");
        System.out.println("    -h                            show this Help message");
    }

    /***************************************************************
     * search for a matching sentence with the first quoted argument
     * being a word or phrase and the second quoted argument being
     * a dependency pattern.
     */
    public static void main(String[] args) throws Exception {

        if (args != null && args.length > 0 && args[0].equals("-i")) {
            if (args.length > 1 && !StringUtil.emptyString(args[1])) {
                interactive(args[1]);
            }
            else
                interactive();
        }
        else if (args != null && args.length > 0 && args[0].equals("-t")) {
            //System.out.println("main(): Opening DB " + Indexer.JDBCString);
            //Connection conn = DriverManager.getConnection(Indexer.JDBCString, Indexer.UserName, "");
            //System.out.println("main(): Opened DB " + Indexer.JDBCString);
            //showTable(conn,"index");
            //showTable(conn,"counts");
            String searchString;
            KBmanager.getMgr().initializeOnce();
            Interpreter interp = new Interpreter();
            try {
                interp.initialize();
            }
            catch (IOException e) {
                System.err.println("Error in Indexer.storeWikiText(): " + e.getMessage());
                e.printStackTrace();
                return;
            }
            if (args.length > 1)
                searchString = StringUtil.removeEnclosingQuotes(args[1]);
            else
                searchString = "";
            String depString = "";
            if ("".equals(searchString))
                 depString = "sumo(FinancialTransaction,?X)";
            if (args.length > 1)
                depString = args[1];
            printSearchResults("FCE",searchString,depString);
        }
        else
            help();
    }
}
