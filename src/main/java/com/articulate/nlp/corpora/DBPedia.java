package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semconcor.Indexer;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

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
public class DBPedia {

    public static HashMap<String,String> pageToString = new HashMap<>();
    public static HashMap<String,String> stringToOnto = new HashMap<>();

    /***************************************************************
     */
    public static void main(String[] args) {

        //WordNetUtilities.readWNversionMap()
        // WordNetUtilities.mappings
        // KBmanager.getMgr().initializeOnce();
        String path = System.getenv("CORPORA") + File.separator + "DBPedia" + File.separator;
        ArrayList<String> thresh = CorpusReader.readFile(path + "thresh-reduced.out");
        for (String s : thresh) {
            String[] pair = s.split("\t");
            pageToString.put(pair[1],pair[0]);
        }
        File file = new File(path + "instance_types_dbtax_dbo_en.ttl");
        try {
            InputStream in = new FileInputStream(file);
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            System.out.println("Info in CorpusReader.processFileByLine(): " + file);
            String line = "";
            while ((line = lnr.readLine()) != null) {
                if (line.startsWith("<http://dbpedia.org/resource/")) {
                    //System.out.println("Info in CorpusReader.processFileByLine(): line: " + line);
                    String page = line.substring(29,line.indexOf(">"));
                    if (pageToString.containsKey(page)) {
                        String onto = line.substring(line.lastIndexOf("/")+1,line.lastIndexOf(">"));
                        stringToOnto.put(pageToString.get(page),onto);
                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println("Error in CorpusReader.processFileByLine(): " + e.getMessage());
            e.printStackTrace();
        }
        for (String s : stringToOnto.keySet()) {
            System.out.println(s + "\t" + stringToOnto.get(s));
        }

    }

}
