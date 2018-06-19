package com.articulate.nlp.semRewrite;

import com.articulate.nlp.NERAnnotator;
import com.articulate.nlp.RelExtract;
import com.articulate.nlp.WNMultiWordAnnotator;
import com.articulate.nlp.WSDAnnotator;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import com.google.common.base.Strings;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.simple.SentenceAlgorithms;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import java.io.IOException;
import java.util.*;

/**
 * Created by apease on 6/14/18.
 */
public class NPtype {

    // not sure yet if more than one could be found
    public static HashSet<CoreLabel> heads = new HashSet<>();

    /** ***************************************************************
     * from https://stackoverflow.com/questions/19431754/using-stanford-parsercorenlp-to-find-phrase-heads/22841952
     */
    public static void dfs(Tree node, Tree parent, HeadFinder headFinder) {

        if (node == null || node.isLeaf()) {
            return;
        }
        //if node is a NP - Get the terminal nodes to get the words in the NP
        if (node.value().equals("NP") ) {
            //System.out.println(" Noun Phrase is ");
            List<Tree> leaves = node.getLeaves();
            //for (Tree leaf : leaves)
            //    System.out.print(leaf.toString() + " ");
            Tree headTerminal = node.headTerminal(headFinder, parent);
            System.out.println();
            //System.out.println(" Head string is: " + headTerminal);
            Tree[] children = headTerminal.children();
            //System.out.println("children: " + children);
            //System.out.println("list length: " + children.length);
            //System.out.println("first child: " + headTerminal.firstChild());
            List<Tree> treeleaves = headTerminal.getLeaves();
            //System.out.println("leaves: " + headTerminal.getLeaves());
            //System.out.println("is leaf: " + headTerminal.isLeaf());
            //System.out.println("label: " + headTerminal.label());
           // System.out.println("label class: " + headTerminal.label().getClass().getName());
            if (treeleaves.size() == 1) {
                Label one = headTerminal.labels().iterator().next();
                //System.out.println("one child: " + one);
                CoreLabel lab = (CoreLabel) one;
                //System.out.println(lab.getString(CoreAnnotations.PartOfSpeechAnnotation.class));
                String POS = lab.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
                if (POS.startsWith("NN")) {
                    System.out.println("head: " + lab);
                    heads.add(lab);
                    System.out.println(RelExtract.toCoreLabelString(lab));
                }
            }
        }
        for (Tree child : node.children()) {
            dfs(child, node, headFinder);
        }
    }

    /** ***************************************************************
     * Take a phrase an turn it to lower case before adding "I want to
     * buy the" and sending it to findType()
     */
    public static String findProductType(String s) {

        s = s.toLowerCase();
        return findType("I want to buy the " + s);
    }

    /** ***************************************************************
     * Guess the SUMO type of a noun phrase in context by looking at
     * the root node of the noun phrase
     */
    public static String findType(String s) {

        System.out.println("findType: \"" + s + "\"");
        s = StringUtil.removeEnclosingQuotes(s);
        Annotation document = null;
        try {
            document = Pipeline.toAnnotation(s);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        CoreMap sentence = SentenceUtil.getLastSentence(document);
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        CollinsHeadFinder headFinder = new CollinsHeadFinder();
        Tree head = headFinder.determineHead(tree);
        head.pennPrint(System.out);
        System.out.println();
        dfs(head,tree,headFinder);

        if (heads.size() != 1) {
            String firstTerm = null;
            if (heads.size() == 0)
                System.out.println("No heads found");
            else {
                System.out.println("Multiple heads");
                boolean first = true;
                for (CoreLabel cl : heads) {
                    String sumo = cl.get(WSDAnnotator.SUMOAnnotation.class);
                    String WNMWsumo = cl.get(WNMultiWordAnnotator.WNMWSUMOAnnotation.class);
                    String NERsumo = cl.get(NERAnnotator.NERSUMOAnnotation.class);
                    System.out.println("findType(): " + sumo + " : " + WNMWsumo + " : " + NERsumo);
                    if (WNMWsumo != null)
                        sumo = WNMWsumo;
                    if (NERsumo != null)
                        sumo = NERsumo;
                    if (first) {
                        firstTerm = sumo;
                        first = false;
                    }
                    System.out.println("findType (label:sumo): " + cl + " : " + sumo);
                }
            }
            System.out.println("returning: " + firstTerm);
            return firstTerm;
        }
        else {
            CoreLabel cl = heads.iterator().next();
            String SUMO = cl.get(WSDAnnotator.SUMOAnnotation.class);
            System.out.println("SUMO: " + SUMO);
            return SUMO;
        }

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
            if (!Strings.isNullOrEmpty(input) && !input.equals("exit") && !input.equals("quit")) {
                findType(input);
            }
        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Semantic Rewriting with SUMO, Sigma and E");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -t \"...\" - find the type of a noun phrase");
        System.out.println("  -i - interactive headword finder");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        System.out.println("INFO in Interpreter.main()");
        Interpreter interp = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        interp.initOnce();
        if (args != null && args.length > 1 && args[0].equals("-t")) {
            findType(args[1]);
        }
        else if (args != null && args.length > 0 && args[0].equals("-i")) {
            interpInter();
        }
        else
            showHelp();
    }
}
