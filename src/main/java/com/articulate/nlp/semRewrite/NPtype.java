package com.articulate.nlp.semRewrite;

import com.articulate.nlp.NERAnnotator;
import com.articulate.nlp.RelExtract;
import com.articulate.nlp.WNMultiWordAnnotator;
import com.articulate.nlp.WSDAnnotator;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.KButilities;
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

    private static Pipeline p = new Pipeline(true);

    public static boolean debug = false;

    public static KB kb = null;

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
                    //System.out.println("head: " + lab);
                    heads.add(lab);
                    //System.out.println("dfs(): " + RelExtract.toCoreLabelString(lab));
                }
            }
        }
        for (Tree child : node.children()) {
            dfs(child, node, headFinder);
        }
    }

    /** ***************************************************************
     * Remove special characters and add "I want to
     * buy the" and sending it to findType().  Restrict results to
     * Object(s).  Try both original and lower case
     */
    public static String findProductType(String s) {

        if (StringUtil.emptyString(s))
            return s;
        s = StringUtil.replaceNonAsciiChars(s,' ');
        s = StringUtil.removeEscapes(s);
        if (StringUtil.emptyString(s))
            return s;
        s = s.replace("w/","with ");
        s = StringUtil.removeDoubleSpaces(s);
        if (StringUtil.emptyString(s))
            return s;
        String resultUC = findType("I want to buy the " + s,"Object");
        s = s.toLowerCase();
        String resultLC = findType("I want to buy the " + s,"Object");
        if (StringUtil.emptyString(resultUC))
            return resultLC;
        if (StringUtil.emptyString(resultLC))
            return resultUC;
        if (kb.compareTermDepth(resultLC,resultUC) > 0)
            return resultLC;
        else
            return resultUC;
    }

    /** ***************************************************************
     * Use the new term if it's not a non-product type like a Region or
     * Substance - a true result means newC is a better term replacement
     */
    private static boolean filterTypesOk(String newC) {

        if (kb.isChildOf(newC,"Region") || kb.isChildOf(newC,"Substance") ||
                kb.isChildOf(newC,"SymbolicString") || kb.isChildOf(newC,"Human") ||
                kb.isChildOf(newC,"Attribute") || kb.isChildOf(newC,"Region") ||
                kb.isChildOf(newC,"Relation") || kb.isChildOf(newC,"TimePosition") ||
                kb.isChildOf(newC,"LinguisticExpression") || kb.isChildOf(newC,"Process")) {
            return false;
        }
        return true;
    }

    /** ***************************************************************
     * Check whether the new candidate type is more specific than the
     * old one, and whether it's a reasonable type for a product, by
     * calling filterTypes()
     */
    public static boolean betterTermReplacement(String newC, String oldC, String type) {

        if (debug) System.out.println("betterTermReplacement() new: " + newC + " and old: " + oldC);
        if (StringUtil.emptyString(newC))
            return false;
        if (!filterTypesOk(newC))  // new one isn't better because it's a bad type
            return false;
        if (StringUtil.emptyString(oldC)) // && implied that newC != null)
            return true;
        if (debug) System.out.println("betterTermReplacement() compare: " + kb.compareTermDepth(newC,oldC));
        if (debug && type != null)
            System.out.println("betterTermReplacement() child: " + kb.isChildOf(newC,type));

        boolean result = false;
        if (type != null)
            result = (oldC == null ||
                (newC != null && kb.compareTermDepth(newC,oldC) > 0 && kb.isChildOf(newC,type)));
        else
            result = (oldC == null ||
                    (newC != null && kb.compareTermDepth(newC,oldC) > 0));
        if (result)
            if (debug) System.out.println("betterTermReplacement() " + newC + " is better than " + oldC);
        if (!result)
            if (debug) System.out.println("betterTermReplacement() " + newC + " is not better than " + oldC);
        return result;
    }

    /** ***************************************************************
     * Guess the SUMO type of a noun phrase in context by looking at
     * the root node of the noun phrase.  If there are multiple
     * roots, pick the most specific one.
     */
    public static String findType(String s, String typeRestrict) {

        System.out.println("findType: \"" + s + "\"");
        s = StringUtil.removeEnclosingQuotes(s);
        Annotation document = null;
        try {
            document = p.annotate(s);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        CoreMap sentence = SentenceUtil.getLastSentence(document);
        List<CoreLabel> labels = sentence.get(CoreAnnotations.TokensAnnotation.class);
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        CollinsHeadFinder headFinder = new CollinsHeadFinder();
        Tree head = headFinder.determineHead(tree);
        head.pennPrint(System.out);
        System.out.println();
        dfs(head,tree,headFinder);
        String sumo = null;
        if (heads.size() != 1) {
            String firstTerm = null;
            if (heads.size() == 0)
                System.out.println("No heads found");
            else {
                if (debug) System.out.println("Multiple heads");
                for (CoreLabel cl : heads) {
                    int toknum = cl.index();
                    if (toknum-1 >= labels.size()) {
                        System.out.println("error in NPtype.findType(): " + heads);
                        System.out.println("error in NPtype.findType(): " + s);
                        Thread.currentThread().dumpStack();
                        continue;
                    }
                    CoreLabel lab = labels.get(toknum-1);
                    String newsumo = lab.get(WSDAnnotator.SUMOAnnotation.class);
                    if (betterTermReplacement(newsumo,sumo,typeRestrict))
                        sumo = newsumo;
                    if (debug && sumo != null && newsumo != null) {
                        int eqrel = kb.compareTermDepth(newsumo,sumo);
                        String eqText = KButilities.eqNum2Text(eqrel);
                        System.out.println("findType(): " + newsumo + " " + eqText + " " + sumo);
                    }
                    String WNMWsumo = lab.get(WNMultiWordAnnotator.WNMWSUMOAnnotation.class);
                    String NERsumo = lab.get(NERAnnotator.NERSUMOAnnotation.class);
                    if (debug) System.out.println("findType(): core label ---------- \n" + RelExtract.toCoreLabelString(lab));
                    if (debug) System.out.println("findType(): (newsumo:sumo:WNMW:NER): " + newsumo + " : " + sumo + " : " +
                            WNMWsumo + " : " + NERsumo);
                    if (betterTermReplacement(WNMWsumo,sumo,typeRestrict))
                        sumo = WNMWsumo;
                    if (betterTermReplacement(NERsumo,sumo,typeRestrict))
                        sumo = NERsumo;
                    if (debug) System.out.println("findType (label:sumo): " + lab + " : " + sumo);
                }
            }
            System.out.println("findType(): returning SUMO: " + sumo);
            return sumo;
        }
        else {
            CoreLabel cl = heads.iterator().next();
            int toknum = cl.index();
            CoreLabel lab = labels.get(toknum-1);
            if (debug) System.out.println("findType(): " + RelExtract.toCoreLabelString(lab));
            sumo = lab.get(WSDAnnotator.SUMOAnnotation.class);
            String WNMWsumo = lab.get(WNMultiWordAnnotator.WNMWSUMOAnnotation.class);
            String NERsumo = lab.get(NERAnnotator.NERSUMOAnnotation.class);
            if (debug) System.out.println("findType(): " + sumo + " : " + WNMWsumo + " : " + NERsumo);
            if (betterTermReplacement(WNMWsumo,sumo,typeRestrict))
                sumo = WNMWsumo;
            if (betterTermReplacement(NERsumo,sumo,typeRestrict))
                sumo = NERsumo;
            System.out.println("findType(): returning SUMO: " + sumo);
            return sumo;
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
            if (!Strings.isNullOrEmpty(input) && !input.equals("debug"))
                debug = true;
            else if (!Strings.isNullOrEmpty(input) && !input.equals("nodebug"))
                debug = false;
            else if (!Strings.isNullOrEmpty(input) && !input.equals("exit") && !input.equals("quit")) {
                findType(input,null);
            }
        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Noun phrase type finder");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -p \"...\" - find the product type of a noun phrase");
        System.out.println("  -t \"...\" <class> - find the product type of a noun phrase with restriction to a class");
        System.out.println("  -i - interactive headword finder");
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
    }

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        System.out.println("INFO in NPtype.main()");
        init();
        if (args != null && args.length > 1 && args[0].equals("-p")) {
            debug = true;
            findProductType(args[1]);
        }
        else if (args != null && args.length > 2 && args[0].equals("-t")) {
            debug = true;
            findType(args[1],args[2]);
        }
        else if (args != null && args.length > 0 && args[0].equals("-i")) {
            interpInter();
        }
        else
            showHelp();
    }
}
