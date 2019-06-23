package com.articulate.nlp.corpora;

import com.articulate.nlp.TFIDF;
import com.articulate.nlp.TFIDFUtil;
import com.articulate.sigma.*;
import com.articulate.sigma.trans.DB2KIF;
import com.google.common.io.Resources;

import java.io.*;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/*
copyright 2018- Infosys

contact Adam Pease adam.pease@infosys.com

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
public class DataMapper {

    public TFIDF cb = null;

    public static ArrayList<ArrayList<String>> cells = new ArrayList<>();

    // map from column name to column number in matches
    public static HashMap<String,Integer> headerIndexMap = new HashMap<>();

    // a map from column names to a list of the top N matches. List values are SUMO terms
    public static HashMap<String,ArrayList<String>> relMatches = new HashMap<>();

    public static String corpusDir = System.getenv("CORPORA");

    public static String inputFilename = corpusDir + File.separator + "UICincome" + File.separator + "adult.data-AP-small.txt.csv";

    public static String matchFilename = inputFilename + ".mch";

    /** ***************************************************************
     */
    public static String clean() {

        DB2KIF dbkif = new DB2KIF();
        DB2KIF.initSampleValues(dbkif);
        return dbkif.clean(cells);
    }

    /** ***************************************************************
     * Save the mappings from the data file. The format is
     * headerName \t sumoTerm
     * ...
     * ==Symbols==
     * headerName \t symbol \t sumoTerm
     * ...
     */
    public static void saveMappings() {

        System.out.println("DataMapper.saveMappings(): writing " + matchFilename);
        FileWriter fr = null;
        PrintWriter pr = null;
        try {
            fr = new FileWriter(matchFilename);
            pr = new PrintWriter(fr);
            for (String header : relMatches.keySet()) {
                //int colnum = headerIndexMap.get(header);
                String match = relMatches.get(header).get(0);
                pr.println(header + "\t" + match);
            }
            pr.println();
            pr.println("==Symbols==");
            for (String colName : DB2KIF.symbolMatches.keySet()) {
                HashMap<String,String> symbol2sumo = DB2KIF.symbolMatches.get(colName);
                for (String symbol : symbol2sumo.keySet())
                    pr.println(colName + "\t" + symbol + "\t" + symbol2sumo.get(symbol));
            }

        }
        catch (IOException e) {
            System.out.println("Error in DataMapper.save(): Error writing file " + matchFilename);
            e.printStackTrace();
        }
        finally {
            if (pr != null) { pr.close(); }
        }
    }

    /** ***************************************************************
     * Find a match for the given SUMO term in the list of matches,
     * where each match is of the form "sumo : doc..."
     */
    public static int findTermIndex(String choice, ArrayList<String> choices) {

        int index = -1;
        if (choices == null)
            return index;
        for (int i = 0; i < choices.size(); i++) {
            String s = choices.get(i);
            int space = s.indexOf(" : ");
            String sumo = s.substring(0,space);
            if (sumo.equals(choice))
                return i;
        }
        return index;
    }

    /** ***************************************************************
     * @param choice is a SUMO term
     * matches is a list of Strings of the form "sumo : doc..."
     */
    public static void swapMatches(int colnum, String choice) {

        ArrayList<String> choices = relMatches.get(colnum);
        System.out.println("DataMapper.swapMatches(): swapping " + choice + " in " + choices);
        int found = findTermIndex(choice,choices);
        if (found != -1) {
            String temp = choices.get(0);
            System.out.println("DataMapper.swapMatches(): swapping " + choice + " and " + temp);
            choices.set(0,choices.get(found));
            choices.set(found,temp);
        }
    }

    /** ***************************************************************
     */
    public static void loadCells() {

        //String fname = System.getenv("CORPORA") + File.separator + "UICincome" + File.separator + "adult.data-AP.txt.csv";
        cells = DB.readSpreadsheet(inputFilename,null,false,',');
        ArrayList<String> header = cells.get(0);
        for (int i = 0; i < header.size(); i++)
            headerIndexMap.put(header.get(i),i);
    }

    /** ***************************************************************
     * Load the file of mappings.  The format is
     * headerName \t sumoTerm
     * ...
     * ==Symbols==
     * headerName \t symbol \t sumoTerm
     * ...
     */
    public static void loadMappings() {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        String filename = inputFilename + ".mch";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            String section = "columns";
            while ((line = lr.readLine()) != null) {
                //System.out.println("; loadMappings(): line: " + line);
                if (line.equals("")) {
                    line = lr.readLine();
                    if (line != null && line.equals("==Symbols=="))
                        section = "symbols";
                    else if (line != null && line.equals("==Units=="))
                        section = "units";
                    else
                        line = null;
                }
                else if (section.equals("units")) {
                    if (!StringUtil.emptyString(line)) {
                        String[] tuple = line.split("\t");

                        if (tuple != null && tuple.length == 2) {
                            //System.out.println("; loadMappings(): tuple: " + tuple[0] + " " + tuple[1]);
                            DB2KIF.units.put(tuple[0], tuple[1]);
                        }
                        else
                            System.out.println("; loadMappings(): bad line: " + line);
                    }
                }
                else if (section.equals("columns")) {
                    String[] pair = line.split("\t");
                    String sumo = pair[1];
                    String colname = pair[0];
                    String doc = KButilities.getDocumentation(kb, sumo);
                    if (doc == null)
                        doc = " : ";
                    else
                        doc = " : " + doc;
                    DB2KIF.column2Rel.put(colname, sumo + doc);
                    ArrayList<String> match = new ArrayList<>();
                    match.add(sumo);
                    relMatches.put(colname,match);
                }
                else if (section.equals("symbols")) {
                    if (!StringUtil.emptyString(line)) {
                        String[] triple = line.split("\t");
                        if (triple != null && triple.length == 3)
                            updateSymbolMap(triple[0], triple[1], triple[2]);  // col, val, ont
                    }
                }
                else
                    System.out.println("; loadMappings(): bad section: " + section);
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        //System.out.println("; loadMappings(): units: " + DB2KIF.units);
    }

    /** ***************************************************************
     * Strip off the documentation strings from each match string, leaving
     * just the SUMO term, and add to relMatches.
     */
    private void addMatches(String colname, ArrayList<String> match) {

        ArrayList<String> newMatch = new ArrayList<>();
        for (int i = 0; i < match.size(); i++) {
            String m = match.get(i);
            int colon = m.indexOf(":");
            if (colon != -1) {
                newMatch.add(m.substring(0,colon-1));
            }
            else
                newMatch.add(m);
        }
        relMatches.put(colname,newMatch);
    }

    /** ***************************************************************
     * Add the matches found by TFIDF to relMatches.  Strip off the
     * documentation strings.  Note that relMatches is erased first.
     */
    public void match() {

        if (cells == null || cells.size() < 2)
            return;
        if (cells.get(1) == null)
            return;
        int size = cells.get(1).size();
        System.out.println("Info in DataMapper.match(): matching " + size + " columns ");
        ArrayList<String> row = cells.get(1);
        relMatches = new HashMap<>();  // clear relMatches to prepare for adding new matches
        for (int i = 0; i < size; i++) {
            String s = row.get(i);
            //System.out.println("Info in DataMapper.match(): column: " + i);
            System.out.println("Info in DataMapper.match(): comment: " + s);
            if (!StringUtil.emptyString(s)) {
                ArrayList<String> match = (ArrayList) cb.matchInput(s, 10);
                String colname = cells.get(0).get(i);
                KB kb = KBmanager.getMgr().getKB("SUMO");
                if (kb.terms.contains(colname)) { // If there's an exact match from a column label to SUMO relation name, make it the top guess
                    match.add(0, colname);
                }
                System.out.println("Info in DataMapper.match(): match size " + match.size());
                if (match != null && match.size() > 0) {
                    addMatches(colname,match);
                    System.out.println("Info in DataMapper.match(): result: " + match.get(0));
                }
                else {
                    ArrayList<String> blank = new ArrayList<>();
                    blank.add("");
                    relMatches.put(colname,blank);
                }
            }
        }
    }

    /** ***************************************************************
     * Index the documentation with TFIDF and run the TFIDF match. Print
     * out the results in HTML
     */
    public static void runMatch() {

        DataMapper dm = new DataMapper();
        dm.cb = TFIDFUtil.indexDocumentation(true); // index only relations
        System.out.println("DataMap.jsp: doc size: " + dm.cb.lines.size());

        dm.match();
        ArrayList<String> header = cells.get(0);
        ArrayList<String> docs = cells.get(1);
        String kbHref = "";
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i);
            if (StringUtil.emptyString(name))
                continue;
            String doc = docs.get(i);
            System.out.println(name + "<P>\n");
            System.out.println(doc + "<P>\n");
            if (dm.relMatches.size() > i) {
                String sumo = dm.relMatches.get(name).get(0);
                String SUMOlink = "<a href=\"" + kbHref + "&term=" + sumo + "\">" + sumo + "</a>";
                System.out.println(SUMOlink + "<P>\n");
            }
            System.out.println("<P>");
        }
    }

    /** ***************************************************************
     */
    public static void updateRelMap(String col, String ont) {

        int colnum = headerIndexMap.get(col);
        ArrayList<String> matchCandidates = relMatches.get(colnum);
        if (matchCandidates.contains(ont)) {
            int ontIndex = matchCandidates.indexOf(ont);
            String oldCandidate = matchCandidates.get(0);
            matchCandidates.set(0,ont);
            matchCandidates.set(ontIndex,oldCandidate);
        }
        else
            matchCandidates.add(0,ont);
    }

    /** ***************************************************************
     */
    public static void updateSymbolMap(String col, String val, String ont) {

        // map from column name key to symbol name key to SUMO term
        // public static HashMap<String,HashMap<String,String>> symbolMatches = new HashMap<>();
        HashMap<String,String> columnVals = DB2KIF.symbolMatches.get(col);
        if (columnVals == null) {
            columnVals = new HashMap<>();
            DB2KIF.symbolMatches.put(col, columnVals);
        }
        columnVals.put(val,ont);
    }

    /** ***************************************************************
     */
    public static void infMatch() {

        if (cells.size() == 0)
            loadCells();
        if (cells.size() < 3) {
            System.out.println("infMatch(): file too small for header, def'n and content");
            return;
        }
        loadMappings();
        DB2KIF dbkif = new DB2KIF();
        dbkif.toKIF(cells);
    }

    /** ***************************************************************
     * allows interactive testing of entering a rule and seeing if it
     * unifies with a set of literals
     */
    public static void interactive() {

        String input = "";
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter command: ");
            input = scanner.nextLine().trim();
            String[] args = null;
            if (input.indexOf(" ") != -1)
                args = input.split(" ");
            if (input.equals("read"))
                loadCells();
            if (input.equals("load"))
                loadMappings();
            if (input.equals("save"))
                saveMappings();
            if (input.equals("clean"))
                System.out.println(clean());
            if (input.equals("match")) {
                if (cells.size() == 0)
                    loadCells();
                runMatch();
            }
            if (args != null && args.length == 3 && args[0].equals("map")) {
                String col = args[1];
                String ont = args[2];
                updateRelMap(col,ont);
            }
            if (args != null && args.length == 4 && args[0].equals("mval")) {
                String col = args[1];
                String val = args[2];
                String ont = args[3];
                updateSymbolMap(col,val,ont);
            }
            if (args != null && args.length == 2 && args[0].equals("cd"))
                corpusDir = System.getenv("CORPORA") + File.separator + args[1];
            if (args != null && args.length == 2 && args[0].equals("in"))
                inputFilename = corpusDir + File.separator + args[1];
            if (args != null && args.length == 2 && args[0].equals("mn"))
                matchFilename = corpusDir + File.separator + inputFilename + ".mch";

        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /** ***************************************************************
     */
    public static void printHelp() {

        System.out.println("Usage: ");
        System.out.println("TFIDF -h         % show this help info");
        System.out.println("      -f name    % read data file and match to SUMO");
        System.out.println("      -inf       % find questionable values via theorem proving");
        System.out.println("      -i         % interactive commands");
        System.out.println("        cd name               % change corpus directory below system CORPORA dir");
        System.out.println("        in name               % change input file name");
        System.out.println("        mn name               % change match file name");
        System.out.println("        read                  % read data file");
        System.out.println("        load                  % load match file");
        System.out.println("        save                  % save match file");
        System.out.println("        test                  % find questionable values");

        System.out.println("        clean                 % find and fix bad values");
        System.out.println("        match                 % run and print match");
        System.out.println("        map col ont           % change the mapping of colum name 'col' to relation 'ont' in SUMO");
        System.out.println("        mval col val ont    % change the mapping of value 'val' in column 'col' to term 'ont' in SUMO");
        System.out.println("        quit     % stop this application");
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        DataMapper dm = new DataMapper();
        dm.cb = TFIDFUtil.indexDocumentation();
        if (args != null && args.length > 0 && args[0].equals("-h")) {
            printHelp();
        }
        else if (args != null && args.length > 0 && args[0].equals("-i")) {
            interactive();
        }
        else if (args != null && args.length > 0 && args[0].equals("-inf")) {
            infMatch();
        }
        else if (args != null && args.length > 1 && args[0].equals("-f")) {
            cells = DB.readSpreadsheet(args[1],null,false,',');
            dm.match();
        }
        else {
            cells = DB.readSpreadsheet(inputFilename,null,false,',');
            runMatch();
            saveMappings();
        }
    }
}
