package com.articulate.nlp.corpora;

import com.articulate.nlp.TFIDF;
import com.articulate.nlp.TFIDFUtil;
import com.articulate.sigma.*;
import com.articulate.sigma.trans.DB2KIF;
import com.google.common.io.Resources;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    // list elements for each column are a list of the top N matches
    public static ArrayList<ArrayList<String>> matches = new ArrayList<>();

    public static String matchFilename = System.getenv("CORPORA") + File.separator + "matches.txt";

    public static String inputFilename = "";

    /** ***************************************************************
     */
    public static String clean() {

        DB2KIF dbkif = new DB2KIF();
        DB2KIF.initSampleValues(dbkif);
        return dbkif.clean(cells);
    }

    /** ***************************************************************
     */
    public static void save() {

        System.out.println("DataMapper.save(): writing " + matchFilename);
        FileWriter fr = null;
        PrintWriter pr = null;
        try {
            fr = new FileWriter(matchFilename);
            pr = new PrintWriter(fr);
            for (String header : DB2KIF.column2Rel.keySet()) {
                String match = DB2KIF.column2Rel.get(header);
                pr.println(header + "\t" + match);
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

        ArrayList<String> choices = matches.get(colnum);
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
    }

    /** ***************************************************************
     */
    public static void loadMatches() {

        String filename = "matches.txt";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            int column = 0;
            while ((line = lr.readLine()) != null) {
                String[] pair = line.split("\t");
                DB2KIF.column2Rel.put(pair[0],pair[1]);
                if (pair[0].equals(cells.get(column))) {
                    ArrayList<String> match = new ArrayList<>();
                    match.add(pair[1]);
                    matches.add(match);
                }
                column++;
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /** ***************************************************************
     */
    public void match() {

        if (cells == null)
            return;
        if (cells.get(1) == null)
            return;
        int size = cells.get(1).size();
        System.out.println("Info in DataMapper.match(): matching " + size + " columns ");
        ArrayList<String> row = cells.get(1);
        for (int i = 0; i < size; i++) {
            String s = row.get(i);
            //System.out.println("Info in DataMapper.match(): column: " + i);
            //System.out.println("Info in DataMapper.match(): comment: " + s);
            if (!StringUtil.emptyString(s)) {
                ArrayList<String> match = (ArrayList) cb.matchInput(s, 10);
                String colname = cells.get(0).get(i);
                KB kb = KBmanager.getMgr().getKB("SUMO");
                if (kb.terms.contains(colname)) {
                    match.add(0, colname + " : " + KButilities.getDocumentation(kb,colname));
                }
                //System.out.println("Info in DataMapper.match(): match size " + match.size());
                if (match != null && match.size() > 0) {
                    matches.add(match);
                    //System.out.println("Info in DataMapper.match(): result: " + match.get(0));
                }
                else {
                    ArrayList<String> blank = new ArrayList<>();
                    blank.add("");
                    matches.add(blank);
                }
            }
        }
    }

    /** ***************************************************************
     */
    public static void test() {

        DataMapper dm = new DataMapper();
        dm.cb = TFIDFUtil.indexDocumentation(true); // index only relations
        System.out.println("DataMap.jsp: doc size: " + dm.cb.lines.size());
        dm.loadCells();
        dm.match();
        ArrayList<String> header = cells.get(0);
        ArrayList<String> docs = cells.get(1);
        String kbHref = "";
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i);
            String doc = docs.get(i);
            System.out.println(name + "<P>\n");
            System.out.println(doc + "<P>\n");
            if (dm.matches.size() > i) {
                String sumo = dm.matches.get(i).get(0);
                int colon = sumo.indexOf(":");
                String sumoterm = sumo.substring(0,colon-1);
                String SUMOlink = "<a href=\"" + kbHref + "&term=" + sumoterm + "\">" + sumoterm + "</a>";
                System.out.println(SUMOlink + "<P>\n");
            }
            System.out.println("<P>");
        }
    }

    /** ***************************************************************
     */
    public static void printHelp() {

        System.out.println("Usage: ");
        System.out.println("TFIDF -h         % show this help info");
        System.out.println("      -f name    % read file");
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        DataMapper dm = new DataMapper();
        dm.cb = TFIDFUtil.indexDocumentation();
        if (args != null && args.length > 0 && args[0].equals("-h")) {
            printHelp();
        }
        else if (args != null && args.length > 1 && args[0].equals("-f")) {
            ArrayList<ArrayList<String>> cells = DB.readSpreadsheet(args[1],null,false,',');
            dm.match();
        }
        else
            test();
    }
}
