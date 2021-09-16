package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semRewrite.NPtype;
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
     * collect concepts from a file
     * @param categories reports on concepts that are subclasses or instances
     *                   of any of the provided terms, if present
     */
    public static void processFile(String fname, HashSet<String> categories) {

        Interpreter interp = new Interpreter();
        HashMap<String,String> nps = new HashMap<>();
        HashSet<String> newNps = new HashSet<>();
        Map<String,Integer> sumo = new HashMap<>();
        HashSet<String> sumoCat = new HashSet<>();
        ArrayList<String> lines = CorpusReader.readFile(fname);
        for (String l : lines) {
            if (StringUtil.emptyString(l))
                continue;
            System.out.println("\n------------------------------\nprocessFile(): line: " + l + "\n");
            nps.putAll(NPtype.findNPs(l,false));
            //System.out.println("processFile(1): nps: " + nps);
            //System.out.println("processFile(1): new nps: " + newNps);
            //System.out.println("processFile(1): sumo: " + sumo);
            sumo = MapUtils.mergeToFreqMap(sumo,WSD.collectSUMOFromString(l));
            for (CoreMap cm : NPtype.sentences) {
                System.out.println(interp.interpretGenCNF(cm));
                List<Literal> lits = Interpreter.findWSD(cm);
                for (Literal lit : lits) {
                    String arg1 = lit.arg1;
                    MapUtils.addToFreqMap(sumo,arg1,1);
                }
            }
        }
        System.out.println("processFile(2): nps: " + nps.keySet());
        newNps.addAll(elimKnownNPs(nps));
        System.out.println("processFile(2): new nps: " + newNps);
        System.out.println("processFile(2): sumo: " + MapUtils.sortedFreqMapToString(MapUtils.toSortedFreqMap(sumo)));
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
            HashSet<String> cats = new HashSet<>();
            if (args.length > 2) {
                for (int i = 2; i <= args.length; i++) {

                }
            }
            processFile(args[1],cats);
        }
    }
}
