package com.articulate.nlp.corpora.VerbNet;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.VerbNet.*;
import com.articulate.sigma.wordNet.WordNet;
import java.io.IOException;

import java.util.List;

public class VerbNetCorpus {

    /** *************************************************************
     */
    public static void tryExamples() {

        try {
            Interpreter interp = new Interpreter();
            KBmanager.getMgr().initializeOnce();
            interp.initialize();
            Interpreter.coref = false;
            List<String> results;
            for (Verb v : VerbNet.verbs.values()) {
                for (Frame f : v.frames) {
                    System.out.println();
                    System.out.println("VerbNet.tryExamples(): " + f.example);
                    results = null;
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
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** *************************************************************
     */
    public static void main(String[] args) {

        KBmanager.getMgr().initializeOnce();
        WordNet.initOnce();
        VerbNet.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("VerbNetCorpus.main()");
        //readVerbFiles();
        VerbNet.initOnce();
        VerbNet.processVerbs();
        System.out.println("# of verbs: " + VerbNet.verbcount);
        System.out.println("# of mapped synsets: " + VerbNet.syncount);
        System.out.println("VerbNet.main(): get vb for wn 200686447: " + VerbNet.wnMapping.get("200686447"));
        tryExamples();
    }
}
