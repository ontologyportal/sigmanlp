package com.articulate.nlp.corpora;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by apease on 6/27/18.
 */
public class Sick {

    /** ***************************************************************
     *  Read the SICK data set
     *  http://clic.cimec.unitn.it/composes/sick.html
     */
    public static ArrayList<ArrayList<String>> readSick() {

        System.out.println("In WSD.readSick(): Reading SICK file ");
        ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
        LineNumberReader lr = null;
        try {
            // pair_ID sentence_A sentence_B entailment_label relatedness_score entailment_AB entailment_BA sentence_A_original
            // sentence_B_original sentence_A_dataset sentence_B_dataset SemEval_set
            String line;
            String f = System.getenv("CORPORA") + "/SICK/SICK.txt";
            File sickFile = new File(f);
            if (sickFile == null) {
                System.out.println("Error in WSD.readSick(): The file does not exist in " + f );
                return null;
            }
            long t1 = System.currentTimeMillis();
            FileReader r = new FileReader(sickFile);
            lr = new LineNumberReader(r);
            while ((line = lr.readLine()) != null) {
                System.out.println(line);
                //if (lr.getLineNumber() % 1000 == 0)
                //    System.out.print('.');
                String[] ls = line.split("\t");
                ArrayList<String> al = new ArrayList<String>(Arrays.asList(ls));
                result.add(al);
            }
        }
        catch (IOException ex) {
            System.out.println("Error in WSD.readSick()");
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        return result;
    }

    /** ***************************************************************
     *  Extract SUMO terms from the SICK data set
     *  http://clic.cimec.unitn.it/composes/sick.html
     */
    public static void collectSUMOFromSICK() {

        FileWriter fw = null;
        PrintWriter pw = null;
        String fname = System.getenv("CORPORA") + "/SICK/SickOut.txt";

        try {
            fw = new FileWriter(fname);
            pw = new PrintWriter(fw);

            KBmanager.getMgr().initializeOnce();

            //WordNet.initOnce();
            ArrayList<ArrayList<String>> sickAr = readSick();
            System.out.println("collectSUMOFromSICK: size of array" + sickAr.size());
            for (int i = 0; i < sickAr.size(); i++) {
                ArrayList<String> al = sickAr.get(i);
                System.out.println("WSD.collectSUMOFromSICK():  line " + i);
                if (al.size() > 2) {
                    System.out.println("WSD.collectSUMOFromSICK(): at line " + al.get(0));
                    String sentA = al.get(1);
                    String sumoA = WSD.collectSUMOFromWords(sentA).toString();
                    String sentB = al.get(2);
                    String sumoB = WSD.collectSUMOFromWords(sentB).toString();
                    pw.println(sentA);
                    pw.println(sumoA);
                    pw.println(sentB);
                    pw.println(sumoB);
                }
            }
        }
        catch (Exception ex) {
            System.out.println("Error in WSD.collectSUMOFromSICK()");
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        finally {
            try {
                if (pw != null) {
                    pw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            }
            catch (Exception ex) {
                System.out.println("Error in WSD.collectSUMOFromSICK()");
                System.out.println(ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        System.out.println("INFO in Sick.main()");
        readSick();
    }
}
