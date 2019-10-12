package com.articulate.nlp.corpora.VerbNet;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.VerbNet.*;
import com.articulate.sigma.wordNet.WordNet;

import java.util.List;

public class VerbNetCorpus {

    /** *************************************************************
     */
    public static void tryExamples() {

        try {
            Interpreter interp = new Interpreter();
            KBmanager.getMgr().initializeOnce();
            interp.initialize();
            interp.coref = false;
            for (Verb v : VerbNet.verbs.values()) {
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
        VerbNet.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("VerbNet.main()");
        //readVerbFiles();
        VerbNet.initOnce();
        VerbNet.processVerbs();
        System.out.println("# of verbs: " + VerbNet.verbcount);
        System.out.println("# of mapped synsets: " + VerbNet.syncount);
        System.out.println("VerbNet.main(): get vb for wn 200686447: " + VerbNet.wnMapping.get("200686447"));
        tryExamples();
    }
}
