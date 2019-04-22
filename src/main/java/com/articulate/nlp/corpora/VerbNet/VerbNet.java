package com.articulate.nlp.corpora.VerbNet;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.WordNet;

import java.io.*;
import java.util.*;

/**
 * Created by apease on 7/23/18.
 */
public class VerbNet {

    public static KB kb;

    private static boolean debug = true;
    private static HashMap<String,SimpleElement> verbFiles = new HashMap<>();
    private static HashMap<String,String> roles = new HashMap<>(); // VN to SUMO role mappings
    private static boolean initialized = false;
    private static int verbcount = 0;
    private static int syncount = 0;

    // verb ID keys and Verb values
    private static HashMap<String,Verb> verbs = new HashMap<>();

    /** *************************************************************
     */
    public static void initOnce() {

        ArrayList<String> keys = new ArrayList<String>(Arrays.asList("Actor","involvedInEvent",
            "Agent","agent", "Asset","objectTransferred", "Attribute","attribute",
            "Beneficiary","beneficiary", "Cause","involvedInEvent",
            "Co-Agent","agent", "Co-Patient","patient", "Co-Theme","patient",
            "Destination","destination", "Duration","time",
            "Experiencer","experiencer", "Extent","", "Final_Time","EndFn",
            "Frequency","frequency", "Goal","", "Initial_Location","origin",
            "Initial_Time","BeginFn", "Instrument","instrument",
            "Location","located", "Material","resource",
            "Participant","involvedInEvent", "Patient","patient",
            "Pivot","patient", "Place","located", "Product","result",
            "Recipient","recipient", "Result","result",
            "Source","origin", "Stimulus","causes", "Time","WhenFn",
            "Theme","patient", "Trajectory","path",
            "Topic","containsInformation", "Undergoer","patient",
            "Value", "measure"));
        if (!initialized) {
            for (int i = 1; i < keys.size()/2; i++) {
                roles.put(keys.get(i*2 - 1), keys.get(i*2));
            }
            readVerbFiles();
            initialized = true;
        }
    }

    /** *************************************************************
     */
    public static void readVerbFiles() {

        SimpleElement configuration = null;
        try {
            String dirStr = "/home/apease/ontology/VerbNet3-2";
            File dir = new File(dirStr);
            if (!dir.exists()) {
                return;
            }
            try {
                File folder = new File(dirStr);
                for (File fileEntry : folder.listFiles()) {
                    if (!fileEntry.toString().endsWith(".xml"))
                        continue;
                    BufferedReader br = new BufferedReader(new FileReader(fileEntry.toString()));
                    SimpleDOMParser sdp = new SimpleDOMParser();
                    verbFiles.put(fileEntry.toString(), sdp.parse(br));
                }
            }
            catch (FileNotFoundException e) {
                System.out.println("Error in VerbNet.readVerbFiles(): " + e.getMessage());
                e.printStackTrace();
            }
        }
        catch (Exception e) {
            System.out.println("Error in VerbNet.readVerbFiles(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** *************************************************************
     */
    public static void processVerbs() {

        for (String fname : verbFiles.keySet()) {
            System.out.println("\n==================");
            System.out.println("VerbNet.processVerbs(): " + fname);
            SimpleElement verb = verbFiles.get(fname);
            String name = (String) verb.getAttribute("ID");
            verbcount++;
            String xmlns = (String) verb.getAttribute("xmlns:xsi");
            String xsi = (String) verb.getAttribute("xsi:noNamespaceSchemaLocation");
            Verb v = new Verb();
            v.readVerb(verb);
            v.ID = name;
            verbs.put(name,v);
        }
    }

    /** *************************************************************
     */
    public static void tryExamples() {

        try {
            Interpreter interp = new Interpreter();
            KBmanager.getMgr().initializeOnce();
            interp.initialize();
            interp.coref = false;
            for (Verb v : verbs.values()) {
                for (Frame f : v.frames) {
                    System.out.println();
                    System.out.println("VerbNet.tryExamples(): " + f.example);
                    List<String> results = null;
                    try {
                        results = interp.interpret(f.example);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("VerbNet.tryExamples(): " + f.example);
                    System.out.println("VerbNet.tryExamples(): logical form:");
                    System.out.println(results);
                    System.out.println();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** *************************************************************
    */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        WordNet.initOnce();
        kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("VerbNet.main()");
        //readVerbFiles();
        initOnce();
        processVerbs();
        System.out.println("# of verbs: " + verbcount);
        System.out.println("# of mapped synsets: " + syncount);
        tryExamples();
    }
}