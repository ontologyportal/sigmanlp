package com.articulate.nlp.semRewrite;

// extracts SUMO terms after processing via SigmaNLP

import com.articulate.nlp.WNMultiWordAnnotator;
import com.articulate.nlp.WSDAnnotator;
import com.articulate.nlp.corpora.CorpusReader;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.WordNet;

import com.google.common.base.Strings;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class TermExtractor {

    public static boolean debug = false;

    private static Pipeline p = new Pipeline(true);

    public static KB kb = null;

    /** ***************************************************************
     * collect terms from a string
     */
    public static Set<String> findTerms(String s) {

        Set<String> result = new HashSet<>();
        Annotation wholeDocument = null;
        try {
            wholeDocument = new Annotation(s);
            p.pipeline.annotate(wholeDocument);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        String orig, lemma, pos, sense, sumo, multi;
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel token : tokens) {
                orig = token.originalText();
                lemma = token.lemma();
                pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                sense = token.get(WSDAnnotator.WSDAnnotation.class);
                sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                multi = token.get(WNMultiWordAnnotator.WNMultiWordAnnotation.class);
                System.out.print(orig);
                if (!StringUtil.emptyString(lemma))
                    System.out.print("|" + lemma);
                if (!StringUtil.emptyString(pos))
                    System.out.print("|" + pos);
                if (!StringUtil.emptyString(sense))
                    System.out.print("|" + sense);
                if (!StringUtil.emptyString(sumo)) {
                    System.out.print("|" + sumo);
                    result.add(sumo);
                    if (!sumo.equals("Entity"))
                        System.out.print("|" + kb.immediateParents(sumo));
                }
                if (!StringUtil.emptyString(multi))
                    System.out.print("|" + multi);
                if (!orig.equals("."))
                    System.out.print(" ");
            }
            System.out.println();
        }
        return result;
    }

    /** ***************************************************************
     * collect noun phrases from a file
     */
    public static Set<String> collectTerms(String fname) {

        Set<String> result = new HashSet<>();
        List<String> lines = CorpusReader.readFile(fname);
        Set<String> nps;
        for (String l : lines) {
            nps = findTerms(l);
            if (!nps.isEmpty())
                result.addAll(nps);
        }
        return result;
    }

    /** ***************************************************************
     * allows interactive testing of entering noun phrase
     */
    public static void interpInter() {

        String input = "";
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter sentence: ");
            input = scanner.nextLine().trim();
            if (!Strings.isNullOrEmpty(input) && !input.equals("debug"))
                debug = true;
            else if (!Strings.isNullOrEmpty(input) && !input.equals("nodebug"))
                debug = false;
            else if (!Strings.isNullOrEmpty(input) && !input.equals("exit") && !input.equals("quit")) {
                findTerms(input);
            }
        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Noun phrase type finder");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -t \"...\" - find terms in a string");
        System.out.println("  -f fname - find terms in a file");
        System.out.println("  -i - interactive term finder");
        System.out.println("     debug - turn debugging on");
        System.out.println("     nodebug - turn debugging off");
    }

    /** ***************************************************************
     */
    public static void init() {

        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB("SUMO");
        interp.initOnce();
        System.out.println("TermExtractor.init(): AppleIPhone words: " + WordNet.wn.getWordsFromTerm("AppleIPhone"));
        System.out.println("TermExtractor.init(): iPhone sense keys: " + WordNet.wn.wordsToSenseKeys.get("iPhone"));
    }

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        KBmanager.debug = true;
        System.out.println("INFO in TermExtractor.main()");
        for (String s : args)
            System.out.println("TermExtractor.main(): arg: " + s);
        if (args == null || args.length == 0 ||
                (args != null && args.length > 1 && args[0].equals("-h"))) {
            showHelp();
        }
        if (args != null && args.length > 1 && args[0].equals("-t")) {
            System.out.println("find terms in string");
            init();
            debug = true;
            System.out.println(findTerms(args[1]));
        }
        else if (args != null && args.length > 1 && args[0].equals("-f")) {
            System.out.println("INFO in TermExtractor.main() find terms in a file");
            init();
            debug = true;
            System.out.println(collectTerms(args[1]));
        }
        else if (args != null && args.length > 0 && args[0].equals("-i")) {
            System.out.println("INFO in TermExtractor.main() Interactive mode");
            init();
            interpInter();
        }
        else
            showHelp();
    }
}
