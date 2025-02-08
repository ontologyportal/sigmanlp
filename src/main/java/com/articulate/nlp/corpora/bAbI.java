package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.FileUtil;
import com.articulate.sigma.utils.MapUtils;

import java.io.File;
import java.util.*;

public class bAbI {

    public class Problem {
        public HashMap<Integer,String> inputs = new HashMap<>();
        public String query;
        public int queryLineNum;
        public String answer;
        public ArrayList<Integer> deduction = new ArrayList<>();
    }

    public static ArrayList<bAbI.Problem> problems = new ArrayList<>();
    public static HashMap<String,String> nps = new HashMap<>();
    public static Map<String,Integer> sumo = new HashMap<>();
    public static KB kb;
    public static Interpreter interp;
    public static HashSet<String> newNps = new HashSet<>();
    public static Map<Integer,Set<String>> freqMapSUMO = new HashMap<>();

    /** ***************************************************************
     */
    public static void init() {

        interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB("SUMO");
        interp.initOnce();
    }

    /** ***************************************************************
     */
    public void extract(String input) {

        Collection<String> files = null;
        File f = new File(input);
        if (f.isDirectory()) {
            files = FileUtil.walk(input);
            System.out.println("bAbI.extract(): # files: " + files.size());
        }
        else {
            files = new ArrayList<>();
            files.add(input);
        }
        for (String s : files) {
            List<String> lines = FileUtil.readLines(s,false);
            Problem p = new Problem();
            for (String l : lines) {
                if (l.contains("?")) {
                    String num = l.substring(0,l.indexOf(" "));
                    int tab = l.indexOf("\t");
                    String sent = l.substring(l.indexOf(" "),tab);
                    int tab2 = l.indexOf("\t",tab+1);
                    p.answer = l.substring(tab+1,tab2);
                    String supports = l.substring(tab2,l.length());
                    String[] supar = supports.split(" ");
                    for (String i : supar) {
                        p.deduction.add(Integer.parseInt(i.trim()));
                    }
                    problems.add(p);
                    p = new Problem();
                }
                else {
                    String num = l.substring(0,l.indexOf(" "));
                    String sent = l.substring(l.indexOf(" "),l.length());
                    p.inputs.put(Integer.parseInt(num.trim()),sent);
                }
            }
        }
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
    public static void main(String[] args) {

        System.out.println("INFO in bAbI.main()");
        for (String s : args)
            System.out.println("bAbI.main(): arg: " + s);
        if (args.length == 0)
            System.out.println("bAbI.main(): no arguments");
        if (args != null && args.length > 1 && args[0].equals("-f")) {
            init();
            Interpreter interp = new Interpreter();
            String input = System.getenv("CORPORA") + File.separator + args[1];
            System.out.println("INFO in bAbI.main(); dir: " + input);
            bAbI babi = new bAbI();
            babi.extract(input);
            for (Problem p : problems) {
                for (String l : p.inputs.values())
                    Analyze.genFreqMaps(interp, sumo, nps, l);
            }
            System.out.println("extract(2): nps: " + nps.keySet());
            newNps.addAll(Analyze.elimKnownNPs(nps));
            System.out.println("extract(2): new nps: " + newNps);
            freqMapSUMO = MapUtils.toSortedFreqMap(sumo);
            System.out.println("extract(2): sumo: " + MapUtils.sortedFreqMapToString(freqMapSUMO));
        }
        else
            showHelp();
    }
}
