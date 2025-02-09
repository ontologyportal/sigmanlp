package com.articulate.nlp.corpora;

import com.articulate.sigma.DB;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.utils.MapUtils;
import com.articulate.sigma.utils.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/*
  Code to read the RXNorm drug database https://www.nlm.nih.gov/research/umls/rxnorm
  "Concepts" appear to be drugs.  "Atoms" appear to be drugs in a particular dose and
  delivery type (tablet, syringe etc).
 */
public class RxNorm {

    public static Map<String, Set<String>> drugConceptID = new HashMap<>();
    public static HashMap<String,String> drugAtomID = new HashMap<>();
    public static HashMap<String,HashSet<AVPair>> rels = new HashMap<>();
    public static HashMap<String,HashSet<AVPair>> attrib = new HashMap<>();
    public static HashMap<String,HashSet<String>> types = new HashMap<>();
    public static HashMap<String,HashSet<String>> doses = new HashMap<>();
    public static Map<String,Set<String>> packaging = new HashMap<>();
    public static KB kb = null;

    /** ***************************************************************
     */
    public static ArrayList<String> decomposeNameParts(String name) {

        ArrayList<String> parts = new ArrayList<>();
        if (name.contains("@"))
            parts.addAll(Arrays.asList(name.split("@")));
        return parts;
    }

    /** ***************************************************************
     */
    public static void processRxnconso(List<List<String>> rxnconso ) {

        System.out.println("processRxnconso()");
        // 141962|ENG||||||944489|944489|141962||RXNORM|SCD|141962|Azithromycin 250 MG Oral Capsule||N|4096|
        String conceptid, id, drug;
        List<String> parts;
        for (List<String> line : rxnconso) {
            conceptid = line.get(0);
            id = line.get(7);
            drug = line.get(14);
            parts = decomposeNameParts(drug);
            if (!parts.isEmpty()) {
                MapUtils.addToMap(drugConceptID, conceptid, parts.get(0));
                drugAtomID.put(id,parts.get(1));
                MapUtils.addToMap(packaging, id, parts.get(2));
            }
            else {
                MapUtils.addToMap(drugConceptID, conceptid, drug);
                drugAtomID.put(id,drug);
            }
        }
    }

    /** ***************************************************************
     */
    public static void processRxnrel(String fname) {

        System.out.println("processRxnrel()");
        // 141962||CUI|RN|105259||CUI|tradename_of|4170244||RXNORM||||N||
        File f = new File(fname);
        String line = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(f));
            while ((line = bf.readLine()) != null) {
                if ((line == null || line.equals("")))
                    continue;
                String[] fields = line.split("\\|");
                //System.out.println("line: " + Arrays.toString(fields));
                String fromConceptID = fields[0];
                String fromAtomID = fields[1];
                String fromType = fields[2];
                String abbrevrel = fields[3];
                String toConceptID = fields[4];
                String toAtomID = fields[5];
                String toType = fields[6];
                String rel = fields[7];
                String source = null;
                String target = null;
                if (drugAtomID.containsKey(fromAtomID))
                    source = StringUtil.stringToKIFid(drugAtomID.get(fromAtomID));
                else if (drugConceptID.containsKey(fromConceptID))
                    source = StringUtil.stringToKIFid(drugConceptID.get(fromConceptID).iterator().next());
                if (drugAtomID.containsKey(toAtomID))
                    target = StringUtil.stringToKIFid(drugAtomID.get(toAtomID));
                else if (drugConceptID.containsKey(toConceptID))
                    target = StringUtil.stringToKIFid(drugConceptID.get(toConceptID).iterator().next());


                if (!StringUtil.emptyString(rel))
                    rel = StringUtil.stringToKIFid(rel);
                else
                    rel = StringUtil.stringToKIFid(abbrevrel);
                if (rel.equals("SY"))  {
                    rel = "synonym";
                    target = "\"" + target + "\"";
                }
                if (rel.equals("SIB"))
                    rel = "sibling";

                if (rel.equals("RN")) rel = "subclass";
                if (rel.equals("RO")) rel = "relatedInternalConcept";
                if (rel.equals("CHD")) rel = "subclass";
                if (rel.equals("has_ingredient")) rel = "ingredient";
                if (rel.equals("PAR") || rel.equals("RB") ) {
                    rel = "subclass";
                    String tmp = source;
                    source = target;
                    target = tmp;
                }
                if (!StringUtil.emptyString(rel) && Character.isUpperCase(rel.charAt(0)))
                    System.out.println("; processRxnrel(): unknown relation : " + rel + " in line " + line);
                if (!StringUtil.emptyString(source) && !StringUtil.emptyString(target) && !StringUtil.emptyString(rel)) {
                    source = source.substring(0, 1).toUpperCase() + source.substring(1);
                    if (Character.isLetter(target.charAt(0)))
                        target = target.substring(0, 1).toUpperCase() + target.substring(1);
                    System.out.println("(" + rel + " " + source + " " + target + ")");
                }
                else {
                    if (source == null)
                        System.out.println("; processRxnrel(): source id not found : " + source + " in line " + line);
                    if (target == null)
                        System.out.println("; processRxnrel(): target id not found : " + target + " in line " + line);
                    if (StringUtil.emptyString(rel))
                        System.out.println("; processRxnrel(): rel not found : " + rel + " in line " + line);
                }

            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("; processRxnrel(): " +
                    "Unable to read line in file. Last line successfully read was: " + line);
        }
    }

    /** ***************************************************************
     */
    public static void processRxnsat(String fname) {

        System.out.println("processRxnsat()");
        // 141962||CUI|RN|105259||CUI|tradename_of|4170244||RXNORM||||N||
        File f = new File(fname);
        String line = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(f));
            while ((line = bf.readLine()) != null) {
                if ((line == null || line.equals("")))
                    continue;
                String[] fields = line.split("\\|");
                String id = fields[0];
                String attr = fields[8];
                String val = fields[10];
                String source = null;
                if (drugAtomID.containsKey(id))
                    source = StringUtil.stringToKIFid(drugAtomID.get(id));
                else if (drugConceptID.containsKey(id))
                    source = StringUtil.stringToKIFid(drugConceptID.get(id).iterator().next());
                String rel = StringUtil.stringToKIFid(attr);
                String value = null;
                if (!StringUtil.emptyString(rel) && Character.isUpperCase(rel.charAt(0)))
                    System.out.println("; processRxnsat(): unknown attribute : " + rel + " in line " + line);
                if (rel != null && rel.equals("MESH_DEFINITION")) {
                    value = val;
                    rel = "documentation";
                    System.out.println("(" + rel + " " + source + " EnglishLanguage \"" + value + "\")");
                }
                else {
                    value = StringUtil.stringToKIFid(val);
                    System.out.println("(" + rel + " " + source + " " + value + ")");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("; processRxnsat(): " +
                    "Unable to read line in file. Last line successfully read was: " + line);
        }
    }

    /** ***************************************************************
     */
    public static void processRxnsty(String fname) {

        System.out.println("processRxnsty()");
        // 141962|T200|A1.3.3|Clinical Drug||4096|
        // 141962||CUI|RN|105259||CUI|tradename_of|4170244||RXNORM||||N||
        File f = new File(fname);
        String line = null;
        try {
            BufferedReader bf = new BufferedReader(new FileReader(f));
            while ((line = bf.readLine()) != null) {
                if ((line == null || line.equals("")))
                    continue;
                String[] fields = line.split("\\|");
                String id = fields[0];
                String parent = fields[3];
                String source = null;
                source = StringUtil.stringToKIFid(drugConceptID.get(id).iterator().next());
                String superclass = StringUtil.stringToKIFid(parent);
                if (parent.equals("Clinical Drug"))
                    superclass = "Medicine";
                System.out.println("(subclass " + source + " " + superclass + ")");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            System.out.println("; processRxnsty(): " +
                    "Unable to read line in file. Last line successfully read was: " + line);
        }
    }

    /** ***************************************************************
     */
    public static void readFiles() {

        System.out.println("readFiles()");
        String input = System.getenv("CORPORA") + File.separator + "RxNorm" + File.separator +
                "rrf" + File.separator;

        //String filename = input + "RXNCONSO-small.RRF";
        String filename = input + "RXNCONSO.RRF";
        List<List<String>> sheet = DB.readSpreadsheet(filename,null,false,'|');
        processRxnconso(sheet);

        //filename = input + "RXNREL-small.RRF";
        filename = input + "RXNREL.RRF";
        processRxnrel(filename);

        //filename = input + "RXNSAT.RRF";
        filename = input + "RXNSAT.RRF";
        processRxnsat(filename);

        //filename = input + "RXNSTY.RRF";
        filename = input + "RXNSTY.RRF";
        processRxnsty(filename);


    }

    /** ***************************************************************
     */
    public static void generateSUMO() {

        System.out.println("generateSUMO()");
        for (String id : drugAtomID.keySet()) {
            String drugName = drugAtomID.get(id);
            if (kb.capterms.containsKey(drugName)) {
                drugName = kb.capterms.get(drugName);
            }
            String classPrefix = "(subclass " + drugName + " ";
            if (types.containsKey(id))
                for (String s : types.get(id))
                    System.out.println(classPrefix + StringUtil.stringToKIFid(s) + ")");
        }
/*
        for (String id : attrib.keySet()) {
            HashSet<AVPair> theset = attrib.get(id);
            for (AVPair avp : theset) {
                String drugName1 = StringUtil.stringToKIFid(drugs.get(id).iterator().next());
                String rel = StringUtil.stringToKIFid(avp.attribute);
                System.out.println("(attribute " + drugName1 + " " + rel + ")");
            }
        }
        for (String id : doses.keySet()) {
            HashSet<String> theset = doses.get(id);
        }
        for (String id : packaging.keySet()) {
            HashSet<String> theset = packaging.get(id);
        }
        */

    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        RxNorm.kb = KBmanager.getMgr().getKB("SUMO");
        readFiles();
        //generateSUMO();
    }
}
