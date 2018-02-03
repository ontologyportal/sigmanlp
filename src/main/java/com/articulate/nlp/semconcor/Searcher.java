package com.articulate.nlp.semconcor;

import com.articulate.nlp.semRewrite.*;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.AVPair;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import com.google.common.base.Strings;

import java.sql.*;
import java.util.*;

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
public class Searcher {

    public static int countSize = 100; // parameter to optimize searching
    public static boolean debug = false;

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
        HashMap<String,String> bindings = patcnf.unify(depcnf);

        if (debug) System.out.println("Searcher.highlightDep(): bindings: " + bindings);
        if (debug) System.out.println("Searcher.highlightDep(): cnf: " + depcnf);
        StringBuffer result = new StringBuffer();
        for (Clause c : depcnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (result.length() > 0)
                    result.append(", ");
                if (l.bound) {
                    result.append("<b>" + l.toString().substring(1) + "</b>"); // don't print the 'X' bound flag
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
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet res = stmt.executeQuery("show columns from " + tableName + ";");
            while (res.next())
                System.out.println(res.getString("FIELD"));
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * @param key a  key of the form filename#sentencenum#linenum
     */
    public static String fetchSentenceFromKey(Connection conn, String key) {

        Statement stmt = null;
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
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /***************************************************************
     * @param key a  key of the form filename#sentencenum#linenum
     */
    public static String fetchDepFromKey(Connection conn, String key) {

        Statement stmt = null;
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
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /***************************************************************
     * @param keys a set of keys of the form filename#sentencenum#linenum
     */
    public static void fetchResultStrings(Connection conn, HashSet<String> keys,
                                                     ArrayList<String> sentences,
                                                     ArrayList<String> dependencies) {

        Statement stmt = null;
        try {
            for (String s : keys) {
                String[] sar = s.split("#");
                String query = "select cont,dependency from content where file='" + sar[0] +
                        "' and sentnum=" + sar[1] + " and linenum=" + sar[2] + ";";
                if (debug) System.out.println("Searcher.fetchResultStrings(): query: " + query);
                stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    sentences.add(rs.getString("CONT"));
                    dependencies.add(rs.getString("DEPENDENCY"));
                }
            }
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /***************************************************************
     * Compile a frequency list for all the given tokens.
     * Note that since AVPair takes strings, the numerical counts
     * must be padded with 0's to maintain their order.  Use 10 digits
     * so we can handle billions of terms.
     */
    public static TreeSet<AVPair> makeCountIndex(Connection conn,
                                                 ArrayList<String> tokens) {

        TreeSet<AVPair> countIndex = new TreeSet<AVPair>();
        Statement stmt = null;
        try {
            if (debug) System.out.println("Searcher.makeCountIndex(): " + conn.getCatalog());
            for (String s : tokens) {
                String query = "select * from counts where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromIndex(): query: " + query);
                stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    int count = rs.getInt("COUNT");
                    String countString = Integer.toString(count);
                    countString = StringUtil.fillString(countString,'0',10,true);
                    AVPair avp = new AVPair(countString,s);
                    countIndex.add(avp);
                }
            }
        }
        catch (SQLException e) {
            System.out.println(e.getMessage());
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
    public static HashSet<String> fetchFromSortedIndex(Connection conn, String indexName,
                                                 ArrayList<String> tokens) {

        TreeSet<AVPair> sortedCounts = makeCountIndex(conn,tokens);

        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): " + indexName + "\n" + tokens);
        if (debug) System.out.println("Searcher.fetchFromSortedIndex(): sorted counts: " + sortedCounts);
        HashSet<String> result = new HashSet<>();
        if (!indexName.equalsIgnoreCase("INDEX") && !indexName.equalsIgnoreCase("DEPINDEX")) {
            System.out.println("Error in Searcher.fetchFromSortedIndex(): bad table name " + indexName);
            return result;
        }
        Statement stmt = null;
        try {
            for (AVPair avp : sortedCounts) {
                String s = avp.value;
                if (debug) System.out.println("Searcher.fetchFromSortedIndex(): term count: " + avp.attribute);
                int termCount = Integer.parseInt(avp.attribute);
                if (result.size() > 0 && termCount > countSize * result.size())
                    return result; // don't try to union really big indexes
                String query = "select * from " + indexName + " where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromSortedIndex(): query: " + query);
                stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query);
                HashSet<String> newresult = new HashSet<>();
                while (rs.next()) {
                    String filename = rs.getString("FILE");
                    int sentNum = rs.getInt("SENTNUM");
                    int lineNum = rs.getInt("LINENUM");
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
            System.out.println(e.getMessage());
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
    public static HashSet<String> fetchFromIndex(Connection conn, String indexName,
                                                 ArrayList<String> tokens) {

        if (debug) System.out.println("Searcher.fetchFromIndex(): " + indexName + "\n" + tokens);
        HashSet<String> result = new HashSet<>();
        if (!indexName.equalsIgnoreCase("INDEX") && !indexName.equalsIgnoreCase("DEPINDEX")) {
            System.out.println("Error in Searcher.fetchFromIndex(): bad table name " + indexName);
            return result;
        }
        Statement stmt = null;
        try {
            for (String s : tokens) {
                String query = "select * from " + indexName + " where token='" + s + "';";
                if (debug) System.out.println("Searcher.fetchFromIndex(): query: " + query);
                stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query);
                HashSet<String> newresult = new HashSet<>();
                while (rs.next()) {
                    String filename = rs.getString("FILE");
                    int sentNum = rs.getInt("SENTNUM");
                    int lineNum = rs.getInt("LINENUM");
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
            System.out.println(e.getMessage());
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
    public static HashMap<String,String> matchDepBind(CNF smallcnf, String onedep) {

        onedep = StringUtil.removeEnclosingCharPair(onedep,2,'[',']'); // two layers of brackets
        if (StringUtil.emptyString(onedep))
            return null;
        Lexer lex = new Lexer(onedep);
        CNF depcnf = CNF.parseSimple(lex);
        HashMap<String,String> bindings = smallcnf.unify(depcnf);
        return bindings;
    }

    /***************************************************************
     * Note that dependency patterns must be simple CNF - no disjuncts
     * and only positive literals
     * @param smallcnf the dependency pattern to search for.
     * @param onedep the dependency match candidate
     */
    public static boolean matchDep(CNF smallcnf, String onedep) {

        HashMap<String,String> bindings = matchDepBind(smallcnf,onedep);
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
    public static ArrayList<Integer> matchDependencies(String dep,
                                     ArrayList<String> dependencies) {

        if (debug) System.out.println("Searcher.matchDependencies(): " + dep);
        if (debug) System.out.println("dependencies size: " + dependencies.size());
        ArrayList<Integer> result = new ArrayList<Integer>();
        if (Strings.isNullOrEmpty(dep) || dep.equals("null"))
            return result;
        Lexer lex = new Lexer(dep);
        CNF smallcnf = CNF.parseSimple(lex);
        for (int i = 0; i < dependencies.size(); i++) {
            String onedep = dependencies.get(i);
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
    public static ArrayList<Integer> matchSentences(String phrase,
                                     ArrayList<String> sentences) {

        if (debug) System.out.println("Searcher.matchSentences(): " + phrase);
        if (debug) System.out.println("sentences size: " + sentences.size());
        ArrayList<Integer> result = new ArrayList<Integer>();
        if (Strings.isNullOrEmpty(phrase))
            return result;
        for (int i = 0; i < sentences.size(); i++) {
            String onesent = sentences.get(i);
            if (!onesent.contains(phrase))
                result.add(i);
        }
        return result;
    }

    /***************************************************************
     * @return a set of keys of the form filename#sentencenum#linenum
     * for sentences that contain the given tokens in the two indexes
     */
    public static HashSet<String> fetchIndexes(Connection conn,
                                               ArrayList<String> sentTokens,
                                               ArrayList<String> depTokens) {

        if (debug) System.out.println("fetchIndexes():" + sentTokens + "\n" + depTokens);
        HashSet<String> result = new HashSet<String>();
        if (sentTokens != null && sentTokens.size() > 0)
            result = fetchFromSortedIndex(conn,"index",sentTokens);
        if (result.size() == 0)
            result = fetchFromSortedIndex(conn,"depindex",depTokens);
        else if (depTokens != null && depTokens.size() > 0)
            result.retainAll(fetchFromSortedIndex(conn,"depindex",depTokens));
        return result;
    }

    /***************************************************************
     * Convert the textual representation of a dependency parse
     * to a list of tokens found in the dependency
     * @return a list of string tokens
     */
    public static ArrayList<String> depToTokens(String dep) {

        if (debug) System.out.println("Searcher.depToTokens(): " + dep);
        ArrayList<String> result = new ArrayList<String>();
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
                              ArrayList<String> sentences,
                              ArrayList<String> dependencies) throws Exception {

        if (debug) System.out.println("Searcher.search(): " + phrase + "\n" + dep);
        String searchString = phrase;
        String[] ar = searchString.split(" ");
        ArrayList<String> sentTokens = new ArrayList<>();
        sentTokens.addAll(Arrays.asList(ar));

        ArrayList<String> depTokens = new ArrayList<>();
        depTokens = depToTokens(dep);

        Class.forName("org.h2.Driver");
        String corporaDir = System.getenv("CORPORA");
        Connection conn = DriverManager.getConnection("jdbc:h2:~/corpora/" + dbFilepath + ";AUTO_SERVER=TRUE", Indexer.UserName, "");
        if (debug) System.out.println("main(): Opened DB " + dbFilepath);

        Statement stmt = null;
        HashSet<String> result = new HashSet<>();
        try {
            if (debug) showTable(conn, "index");
            if (debug) showTable(conn, "counts");
            result = fetchIndexes(conn,sentTokens, depTokens);
            if (debug) System.out.println("search(): indexes size: " + result.size());
            ArrayList<String> tempSentences = new ArrayList<>();
            ArrayList<String> tempDependencies = new ArrayList<>();
            fetchResultStrings(conn,result,tempSentences,tempDependencies); // results returned in tempSentences and tempDependencies

            ArrayList<Integer> removeList = matchDependencies(dep,tempDependencies);
            removeList.addAll(matchSentences(phrase,tempSentences));

            ArrayList<String> newsent = new ArrayList<>();
            ArrayList<String> newdep = new ArrayList<>();
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
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        finally {
            if (stmt != null)
                stmt.close();
        }
        conn.close();
    }

    /***************************************************************
     */
    public static void printSearchResults(String dbFilepath, String phrase, String dep) throws Exception {

        System.out.println("Searcher.printSearchResults(): " + phrase + "\n" + dep);

        try {
            ArrayList<String> sentences = new ArrayList<>();
            ArrayList<String> dependencies = new ArrayList<>();
            search(dbFilepath, phrase, dep,sentences, dependencies);
            for (int i = 0; i < sentences.size(); i++) {
                String s = sentences.get(i);
                String d = dependencies.get(i);
                System.out.println("Sentence: " + s);
                System.out.println("Dependency: " + d);
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /** ***************************************************************
     */
    public static void interactive() {
        interactive(Indexer.JDBCString);
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
        catch (Exception e) {
            System.out.println("Error in Searcher.interactive(): " + e.getMessage());
            e.printStackTrace();
            return;
        }
        String input = "";
        String deps = "";
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
                        System.out.println(e.getMessage());
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

        Class.forName("org.h2.Driver");
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
            String searchString = "in";
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
            if (args != null && args.length > 1)
                searchString = StringUtil.removeEnclosingQuotes(args[1]);
            else
                searchString = "";
            String depString = "";
            if (searchString == "")
                 depString = "sumo(FinancialTransaction,?X)";
            if (args != null && args.length > 1)
                depString = args[1];
            printSearchResults("FCE",searchString,depString);
        }
        else
            help();
    }
}
