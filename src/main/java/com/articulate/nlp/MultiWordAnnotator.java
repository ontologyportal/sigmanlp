package com.articulate.nlp;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.MultiWords;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpanAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

import java.util.*;

/**
 * This class marks multi-word strings. It is an abstract class since the only
 * difference in many multi-word annotators is in the annotation type
 * and the particular instance of MultiWord used.
 *
 * @author apease
 */

public abstract class MultiWordAnnotator implements Annotator {

    // Each CoreLabel in a multi-word string gets one that provides the entire
    // multi-word in WordNet format.  Individual words will still have their own
    // synsets, so later processing should check tokens for multi-word membership.
    // Some annotators may not have synsets
    public Class multiWordAnno;

    public Class sumoAnno;

    //spans are set to token numbers not array position index
    public Class spanAnno;

    public Class tokenAnno;

    public static boolean debug = false;

    public MultiWords mw;

    public String name = "MultiWordAnnotator";

    public MultiWordAnnotator() {}

    // annotation for the actual matched form of a multi-word, which could be
    // a lemma or a lowercase version
    public static class MultiWordFormAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    /****************************************************************
     */
    public MultiWordAnnotator(String name, Properties props, Class mwAnn,
                              Class sAnn, Class spanAnn,
                              Class tokAnn, MultiWords mwSet) {

        multiWordAnno = mwAnn;
        sumoAnno = sAnn;
        spanAnno = spanAnn;
        tokenAnno = tokAnn;
        mw = mwSet;
    }

    /****************************************************************
     * Convert a list of CoreLabel to a String, separated by delimit
     * of its MultiWordFormAnnotation(s)
     */
    public static String multiWordToKey(List<CoreLabel> tokens, char delimit) {

        if (tokens == null || tokens.size() == 0)
            return "";
        StringBuilder result = new StringBuilder();
        for (CoreLabel cl : tokens) {
            if (result.length() > 0)
                result.append(delimit);
            result.append(cl.getString(MultiWordFormAnnotation.class));
        }
        return result.toString();
    }

    /****************************************************************
     * abstract method to find the SUMO term for the given key
     */
    public String findSUMO(String key) {

        return "";
    }

    /****************************************************************
     * abstract method to find the synset for the given key
     */
    public String findSynset(String key) {

        return "";
    }

    /****************************************************************
     */
    public static List<CoreLabel> labelListCopy(List<CoreLabel> l) {

        List<CoreLabel> result = new ArrayList<>();
        for (CoreLabel cl : l)
            result.add(new CoreLabel(cl));
        return result;
    }

    /****************************************************************
     */
    public static List<CoreLabel> findMultiWord(List<CoreLabel> head, List<CoreLabel> rest,
                                                Collection<String> candidates) {

        //if (debug) System.out.println("findMultiWord(1): " + head + " : " + rest);
        if (rest.size() == 0)
            return null;
        List<CoreLabel> result = new ArrayList<>();
        String headString = multiWordToKey(head,'_');
        CoreLabel token = new CoreLabel(rest.get(0));
        List<CoreLabel> newRest = labelListCopy(rest.subList(1,rest.size()));
        List<CoreLabel> newHead = labelListCopy(head);
        newHead.add(token);
        String orig = token.originalText();
        String lower = token.originalText().toLowerCase();
        String lemma = token.lemma();
        String lemmalower = null;
        if (!StringUtil.emptyString(token.lemma()))
            lemmalower = token.lemma().toLowerCase();
        String bestMatch = "";
        for (String s : candidates) {
            String newString = headString + "_" + orig;
            //if (debug) System.out.println("findMultiWord(1): s, newString " + s + ", " + newString);
            if (s.equals(newString)) {
                if (newString.length() > bestMatch.length()) {
                    result = labelListCopy(head);
                    result.add(token);
                    bestMatch = newString;
                    token.set(MultiWordFormAnnotation.class,orig);
                }
            }
            else if (s.startsWith(newString)) {
                token.set(MultiWordFormAnnotation.class,orig);
                List<CoreLabel> newResult = findMultiWord(newHead,newRest,candidates);
                newString = multiWordToKey(newResult,'_');
                if (newString.length() > bestMatch.length()) {
                    result = newResult;
                    bestMatch = newString;
                }
            }

            newString = headString + "_" + lemma;
            if (s.equals(newString)) {
                if (newString.length() > bestMatch.length()) {
                    result = labelListCopy(head);
                    result.add(token);
                    bestMatch = newString;
                    token.set(MultiWordFormAnnotation.class,lemma);
                }
            }
            else if (s.startsWith(newString)) {
                token.set(MultiWordFormAnnotation.class,lemma);
                List<CoreLabel> newResult = findMultiWord(newHead,newRest,candidates);
                newString = multiWordToKey(newResult,'_');
                if (newString.length() > bestMatch.length()) {
                    result = newResult;
                    bestMatch = newString;
                }
            }

            newString = headString + "_" + lower;
            if (s.equals(newString)) {
                if (newString.length() > bestMatch.length()) {
                    result = labelListCopy(head);
                    result.add(token);
                    bestMatch = newString;
                    token.set(MultiWordFormAnnotation.class,lower);
                }
            }
            else if (s.startsWith(newString)) {
                token.set(MultiWordFormAnnotation.class,lower);
                List<CoreLabel> newResult = findMultiWord(newHead,newRest,candidates);
                newString = multiWordToKey(newResult,'_');
                if (newString.length() > bestMatch.length()) {
                    result = newResult;
                    bestMatch = newString;
                }
            }

            newString = headString + "_" + lemmalower;
            if (s.equals(newString)) {
                if (newString.length() > bestMatch.length()) {
                    result = labelListCopy(head);
                    result.add(token);
                    bestMatch = newString;
                    token.set(MultiWordFormAnnotation.class,lemmalower);
                }
            }
            else if (s.startsWith(newString)) {
                token.set(MultiWordFormAnnotation.class,lemmalower);
                List<CoreLabel> newResult = findMultiWord(newHead,newRest,candidates);
                newString = multiWordToKey(newResult,'_');
                if (newString.length() > bestMatch.length()) {
                    result = newResult;
                    bestMatch = newString;
                }
            }
        }
        return result;
    }

    /****************************************************************
     * First part of the algorithm just to match the head of any multiword
     */
    public List<CoreLabel> findMultiWord(CoreLabel token, List<CoreLabel> rest) {


        List<CoreLabel> result = new ArrayList<>();
        String orig = token.originalText();
        String lower = token.originalText().toLowerCase();
        String lemma = token.lemma();
        String lemmalower = null;
        if (!StringUtil.emptyString(token.lemma()))
            lemmalower = token.lemma().toLowerCase();
        //if (debug) System.out.println("findMultiWord(2): " + orig + " : " + lemma);
        //if (debug) System.out.println("findMultiWord(2): matches: " + mw.multiWord.get(orig).size());
        if (mw.multiWordSerialized.containsKey(orig)) {
            Collection<String> candidates = mw.multiWordSerialized.get(orig);
            List<CoreLabel> head = new ArrayList<>();
            head.add(new CoreLabel(token));
            head.get(0).set(MultiWordFormAnnotation.class,orig);
            List<CoreLabel> newres = findMultiWord(head,rest,candidates);
            if (newres != null && newres.size() > result.size()) {
                result = newres;
            }
        }
        if (mw.multiWordSerialized.containsKey(lower)) {
            Collection<String> candidates = mw.multiWordSerialized.get(lower);
            List<CoreLabel> head = new ArrayList<>();
            head.add(new CoreLabel(token));
            head.get(0).set(MultiWordFormAnnotation.class,lower);
            List<CoreLabel> newres = findMultiWord(head,rest,candidates);
            if (newres != null && newres.size() > result.size()) {
                result = newres;
            }
        }
        if (mw.multiWordSerialized.containsKey(lemma)) {
            Collection<String> candidates = mw.multiWordSerialized.get(lemma);
            List<CoreLabel> head = new ArrayList<>();
            head.add(new CoreLabel(token));
            head.get(0).set(MultiWordFormAnnotation.class,lemma);
            List<CoreLabel> newres = findMultiWord(head,rest,candidates);
            if (newres != null && newres.size() > result.size()) {
                result = newres;
            }
        }
        if (mw.multiWordSerialized.containsKey(lemmalower)) {
            Collection<String> candidates = mw.multiWordSerialized.get(lemmalower);
            List<CoreLabel> head = new ArrayList<>();
            head.add(new CoreLabel(token));
            head.get(0).set(MultiWordFormAnnotation.class,lemmalower);
            List<CoreLabel> newres = findMultiWord(head,rest,candidates);
            if (newres != null && newres.size() > result.size()) {
                result = newres;
            }
        }
        return result;
    }

    /****************************************************************
     * Look for multiwords in the sentence starting from the first
     * CoreLabel, calling findMultiWord()
     */
    public void annotateSentence(List<CoreLabel> tokens) {

        if (debug) System.out.println("annotateSentence(): " + tokens);
        if (tokens.size() < 2)
            return;
        for (int i = 0; i < tokens.size() - 1; i++) { // don't try just the last token since one token can't be multiword
            List<CoreLabel> rest = tokens.subList(i+1,tokens.size());
            CoreLabel token = tokens.get(i);
            if (debug) System.out.println("annotateSentence(): token: " + token);
            List<CoreLabel> multiWord = findMultiWord(token, rest);
            if (debug) System.out.println("annotateSentence(): multiword: " + multiWord);
            if (multiWord.size() > 0) {
                String key = multiWordToKey(multiWord,'_');
                int end = i + multiWord.size();
                for (int index = i; index < end; index++) {
                    CoreLabel tok = tokens.get(index);  // note that token index is token number -1
                    tok.set(multiWordAnno,findSynset(key));
                    IntPair ip = new IntPair(i+1,end); // spans are set to token numbers
                    tok.set(spanAnno,ip);
                    String sumo = findSUMO(key);
                    if (!StringUtil.emptyString(sumo)) {
                        tok.set(sumoAnno, sumo);
                        if (debug) System.out.println("annotateSentence(): sumo: " + sumo);
                        if (debug) System.out.println("annotateSentence(): anno: " + sumoAnno);
                    }
                    if (!StringUtil.emptyString(key)) {
                        tok.set(tokenAnno, key);
                        if (debug) System.out.println("annotateSentence(): multiword token: " + key);
                    }
                }
                i = end; // skip to end of current multiWord
            }
        }
    }

    /****************************************************************
     * Mark all the multiwords in the text with their synset, sumo
     * term and the span of the multiword using tokens indexes (which
     * start at 0 and not token numbers, which start at 1)
     */
    public void annotate(Annotation annotation) {

        if (mw == null) {
            System.out.println("Error in " + name + ".annotate(): null multiword");
            Thread.dumpStack();
        }
        if (!annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Unable to find sentences in " + annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            annotateSentence(tokens);
        }
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>> requires() {

        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.SentencesAnnotation.class,
                CoreAnnotations.LemmaAnnotation.class)));
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {

        return Collections.singleton(multiWordAnno);
    }

    /****************************************************************
     */
    public static CoreLabel setCoreLabel(String s, int i) {

        CoreLabel cl = new CoreLabel();
        cl.setLemma(s);
        cl.setOriginalText(s);
        cl.setValue(s);
        cl.setWord(s);
        cl.setIndex(i);
        return cl;
    }

    /****************************************************************
     */
    public static void main(String[] args) {

    }
}

