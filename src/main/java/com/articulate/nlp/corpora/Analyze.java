package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semRewrite.NPtype;
import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.MapUtils;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.WSD;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.util.*;

public class Analyze {

    private static Pipeline p = new Pipeline(true);

    public static boolean debug = false;

    public static KB kb = null;

    public static HashMap<String,String> nps = new HashMap<>();
    public static HashSet<String> newNps = new HashSet<>();
    //public static Map<String,Integer> sumo = new HashMap<>();
    public static Map<Integer,HashSet<String>> freqMapSUMO = new HashMap<>();

    /** ***************************************************************
     */
    public static void init() {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB("SUMO");
        interp.initOnce();
    }

    /** ***************************************************************
     * collect noun phrase co-occurrences from a file
     */
    public static HashSet<String> elimKnownNPs(HashMap<String,String> nps) {

        HashSet<String> result = new HashSet<>();
        for (String s : nps.keySet()) {
            String lemma = nps.get(s);
            if (NPtype.evaluateNP(s,lemma))
                result.add(s);
        }
        return result;
    }

    /** ***************************************************************
     */
    public static void reportRelations(String rel, HashSet<String> cats) {

        ArrayList<Formula> rels = kb.ask("arg",0,rel);
        System.out.println("reportRelations(): rel : " + rel);

        Map<String,Set<String>> relMap = new HashMap<>();
        for (Formula f : rels) {
            String arg1 = f.getStringArgument(1);
            String arg2 = f.getStringArgument(2);
            if (debug) System.out.println("reportRelations(): rel, arg1, arg2 : " + rel + "," + arg1 + "," + arg2);
            if (StringUtil.emptyString(arg1) || StringUtil.emptyString(arg2) || arg1.contains("(") || arg2.contains("("))
                continue;
            MapUtils.addToMap(relMap,arg2,arg1);
        }
        for (int i : freqMapSUMO.keySet()) {
            if (debug) System.out.println("reportRelations(): freq : " + i);
            HashSet<String> sumoTerms =  freqMapSUMO.get(i);
            //System.out.println("reportRelations(): sumo terms : " + sumoTerms);
            for (String kbVal : relMap.keySet()) {
                //System.out.println("reportRelations(): kbVal : " + kbVal);
                for (String textVal : sumoTerms) {
                    //System.out.println("reportRelations(): textVal : " + textVal);
                    if (textVal.equals(kbVal) || kb.isChildOf(textVal,kbVal))
                        System.out.println(textVal + " is a " + kbVal + " in " + rel + " for " + relMap.get(kbVal));
                }
            }
        }
    }

    /** ***************************************************************
     */
    public static Map<String,Integer> genFreqMaps(Interpreter interp, Map<String,Integer> sumo,
                                   HashMap<String,String> nps, String l) {

        nps.putAll(NPtype.findNPs(l,false));
        //System.out.println("genFreqMaps(): nps: " + nps);
        //System.out.println("genFreqMaps(): new nps: " + newNps);
        sumo = MapUtils.mergeToFreqMap(sumo,WSD.collectSUMOFromString(l));
        if (debug) System.out.println("genFreqMaps(): sumo: " + sumo);
        for (CoreMap cm : NPtype.sentences) {
            System.out.println(interp.interpretGenCNF(cm));
            List<Literal> lits = Interpreter.findWSD(cm);
            if (debug) System.out.println("genFreqMaps(): lits: " + lits);
            for (Literal lit : lits) {
                String arg1 = lit.arg1;
                MapUtils.addToFreqMap(sumo,arg1,1);
            }
        }
        if (debug) System.out.println("genFreqMaps(2): sumo: " + sumo);
        return sumo;
    }

    /** ***************************************************************
     * collect concepts from a file
     * @param categories reports on concepts that are subclasses or instances
     *                   of any of the provided terms, if present
     */
    public static void processFile(String fname, HashSet<String> categories) {

        Interpreter interp = new Interpreter();

        Map<String,Integer> sumo = new HashMap<>();
        HashSet<String> sumoCat = new HashSet<>();
        ArrayList<String> lines = CorpusReader.readFile(fname);
        for (String l : lines) {
            //System.out.println("\n------------------------------\nprocessFile(): line: " + l + "\n");
            if (StringUtil.emptyString(l))
                continue;
            sumo = genFreqMaps(interp,sumo,nps,l);
        }
        newNps.addAll(elimKnownNPs(nps));
        freqMapSUMO = MapUtils.toSortedFreqMap(sumo);
        System.out.println("processFile(): nps: " + nps.keySet());
        System.out.println("processFile(): new nps: " + newNps);
        System.out.println("processFile(): sumo: " + sumo);
        System.out.println("processFile(): sumo freq: " + MapUtils.sortedFreqMapToString(freqMapSUMO));
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Text analysis");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -f \"...\" - analyze a file");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        System.out.println("INFO in NPtype.main()");
        for (String s : args)
            System.out.println("NPtype.main(): arg: " + s);
        if (args.length == 0)
            System.out.println("NPtype.main(): no arguments");
        if (args != null && args.length > 1 && args[0].equals("-f")) {
            init();
            String rel = null;
            if (args.length> 2)
                rel = args[2];
            HashSet<String> cats = new HashSet<>();
            if (args.length > 3) {
                for (int i = 3; i <= args.length; i++) {
                    cats.add(args[i]);
                }
            }
            processFile(args[1],cats);
            if (rel != null)
                reportRelations(rel,cats);
        }
    }
}
