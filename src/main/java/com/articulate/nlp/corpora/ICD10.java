package com.articulate.nlp.corpora;

import com.articulate.sigma.KBmanager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ICD10 {

    public HashMap<String,ICD10Node> nodemap = new HashMap<>();

    public class ICD10Node {
        ArrayList<String> parents = new ArrayList<>();
        ArrayList<String> children = new ArrayList<>();
    }

    /** ***************************************************************
     */
    public static void readCodesText() {

        String fname = CorpusReader.corpora + File.separator + "ICD10/icd10cm_codes-2021.txt";
        ArrayList<String> al = CorpusReader.readFile(fname);
        for (String s : al) {
            String code = s.substring(0,7);
            String name = s.substring(8);
        }
    }

    /** ***************************************************************
     */
    public static void readDrugXML() {


    }

}
