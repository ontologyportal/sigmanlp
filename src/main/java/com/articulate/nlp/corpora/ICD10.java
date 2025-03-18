package com.articulate.nlp.corpora;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ICD10 {

    public Map<String,ICD10Node> nodemap = new HashMap<>();

    public class ICD10Node {
        List<String> parents = new ArrayList<>();
        List<String> children = new ArrayList<>();
    }

    /** ***************************************************************
     */
    public static void readCodesText() {

        String fname = CorpusReader.corpora + File.separator + "ICD10/icd10cm_codes-2021.txt";
        List<String> al = CorpusReader.readFile(fname);
        String code, name;
        for (String s : al) {
            code = s.substring(0,7);
            name = s.substring(8);
        }
    }

    /** ***************************************************************
     */
    public static void readDrugXML() {


    }

}
