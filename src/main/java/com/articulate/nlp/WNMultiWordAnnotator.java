package com.articulate.nlp;

import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpanAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

/**
 * This class marks WordNet multi-word strings.
 *
 * @author apease
 */

public class WNMultiWordAnnotator implements Annotator {
	
	public static boolean firstTime = false;

    // Each CoreLabel in a multi-word string gets one that provides the entire
    // multi-word in WordNet format.  Individual words will still have their own
    // synsets, so later processing should check tokens for multi-word membership
    public static class WNMultiWordAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static class WNMWSUMOAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    //spans are set to token numbers not array position index
    public static class WNMWSpanAnnotation  extends CoreAnnotations.SpanAnnotation {
        public Class<IntPair> getType() {
            return IntPair.class;
        }
    }

    public static class WNMWTokenAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    //static final Annotator.Requirement WNMW_REQUIREMENT = new Annotator.Requirement("wnmw");
    public static boolean debug = false;

    /****************************************************************
     */
    public WNMultiWordAnnotator(String name, Properties props) {

        //KBmanager.getMgr().initializeOnce();
    }

    /** ***************************************************************
     * Find the synset for a multi-word string, if it exists.
     *
     * @param tokens is a List of CoreLabel words.
     * @param startIndex is the first word in the list to look at,
     *                   note that token numbers start at 1 but list inices
     *                   start at 0
     * @param synset is an array of only one element, if a synset is found
     * and empty otherwise
     * @return the index into the next word to be checked, in text,
     * which could be the same as startIndex, if no multi-word was found
     */
    public static int findMultiWord(List<CoreLabel> tokens, int startIndex, List<String> synset,
                             StringBuffer multiWordToken) {

        if (debug) System.out.println("INFO in WNMultiWordAnnotator.findMultiWord(): tokens: '" + tokens + "'");
        if (debug) System.out.println("INFO in WNMultiWordAnnotator.findMultiWord(): start: '" + startIndex + "'");
        if (debug) System.out.println("INFO in WNMultiWordAnnotator.findMultiWord(): synset: '" + synset + "'");
        if (debug) System.out.println("INFO in WNMultiWordAnnotator.findMultiWord(): multiWordToken: '" + multiWordToken + "'");

        if (startIndex + 1 < tokens.size())
            return startIndex + findMultiWord(tokens.get(startIndex),
                tokens.subList(startIndex + 1, tokens.size()), synset, multiWordToken);
        else
            return startIndex;
    }

    /** ***************************************************************
     *  We need to try both the root form and the original form,
     *  which includes capitalized and lower case versions.
     *  @return the count of words in the multi-word expression
     *  put the variable token in foundMultiWord
     */
    public static int findMultiWord(CoreLabel token, List<CoreLabel> multiWordTail, List<String> synset,
                                    StringBuffer foundMultiWord) {

    	if (firstTime) {
	    	firstTime = false;
	    	for (String key : WordNet.wn.getMultiWords().multiWord.keySet()) {
	    		if (debug) System.out.println("findMultiWord(2): " + key + ": " + WordNet.wn.getMultiWords().multiWord.get(key));
	    	}
    	}
        if (debug) System.out.println("findMultiWord(2): " + token);
        int endIndex = findMultiWord(token.originalText(), multiWordTail, synset, foundMultiWord);
        if (endIndex == 0) // TODO what if there a multiword with both original and root form?
            endIndex = findMultiWord(token.lemma(), multiWordTail, synset, foundMultiWord);
        return endIndex;
    }

    /** ***************************************************************
     *  We need to try both the root form and the original form,
     *  which includes capitalized and lower case versions.
     *  @return the count of words in the multi-word expression
     *  put the variable token in foundMultiWord
     */
    private static int findMultiWord(String multiWordKey, List<CoreLabel> multiWordTail, List<String> synset,
                                    StringBuffer foundMultiWord) {

        if (debug) System.out.println("findMultiWord(3): " + multiWordKey);
        StringBuffer currentMultiWord = new StringBuffer();
        if (debug) System.out.println("findMultiWord(3): value " + WordNet.wn.getMultiWords().multiWord.get(multiWordKey));
        int wordIndex = 0;
        int endIndex = 0;
        String sense = "";
        if (WordNet.wn.getMultiWords().multiWord.containsKey(multiWordKey) && !multiWordTail.isEmpty()) {
            int mwlen = foundMultiWord.length();
            currentMultiWord.delete(0,mwlen);
            currentMultiWord = currentMultiWord.append(multiWordKey + "_" + multiWordTail.get(wordIndex).originalText());
            Collection<String> candidates = WordNet.wn.getMultiWords().multiWord.get(multiWordKey);
            while (candidates.size() > 0) {
                ArrayList<String> newCandidates = new ArrayList<String>();
                for (String candidate : candidates) {
                    if (candidate.equals(currentMultiWord.toString())) {
                        sense = WSD.getBestDefaultSense(currentMultiWord.toString());
                        foundMultiWord.replace(0,currentMultiWord.length(),currentMultiWord.toString());
                        if (!StringUtil.emptyString(sense)) {
                            endIndex = wordIndex + 2;
                        }
                    }
                    else if (candidate.startsWith(currentMultiWord.toString())) {
                        newCandidates.add(candidate);
                    }
                }
                if (newCandidates.size() > 0) {
                    if (wordIndex > multiWordTail.size() - 1) {
                        candidates = new ArrayList<String>();  // ran out of words, trigger an exit
                    }
                    else {
                        candidates = newCandidates;
                        wordIndex++;
                        if (wordIndex < multiWordTail.size())
                            currentMultiWord.append("_" + multiWordTail.get(wordIndex).originalText());
                    }
                }
                else {
                    candidates = new ArrayList<>();
                }
            }
        }
        if (!StringUtil.emptyString(sense))
            synset.add(sense);
        else
            if (debug) System.out.println("findMultiWord(): empty sense for " + currentMultiWord.toString());
        return endIndex;
    }

    /****************************************************************
     */
    public static void annotateSentence(List<CoreLabel> tokens) {

        if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): start: " + tokens);
        int wordIndex = 0;
        for (CoreLabel token : tokens) {
            if (token.index() < wordIndex) // skip the found multi-word
                continue;
            int i = token.index() - 1 ;
            ArrayList<String> multiWordResult = new ArrayList<String>();
            StringBuffer multiWordToken = new StringBuffer();
            wordIndex = findMultiWord(tokens, i, multiWordResult, multiWordToken);
            if (multiWordToken.length() > 0 && debug)
                System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): found multi-word: " + multiWordToken);
            //multiWordToken.insert(0,"?");
            if (multiWordToken.length() > 0)
                multiWordToken.append("-" +  Integer.toString(token.index())); // set to token number not list index
            if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): start: " + i + " end: " + wordIndex);
            if (wordIndex != i && multiWordToken.length() > 0) {
                String synset = multiWordResult.get(0);
                for (int index = i; index < wordIndex; index++) {
                    CoreLabel tok = tokens.get(index);  // note that token index is token number -1
                    tok.set(WNMultiWordAnnotation.class,synset);
                    IntPair ip = new IntPair(i+1,wordIndex); // spans are set to token numbers
                    if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotate(): set span to: " +
                            i + ":" + (wordIndex-1));
                    //ip.set(i,wordIndex);
                    tok.set(WNMWSpanAnnotation.class,ip);
                    String sumo = WordNetUtilities.getBareSUMOTerm(WordNet.wn.getSUMOMapping(synset));
                    if (!StringUtil.emptyString(sumo))
                        tok.set(WNMWSUMOAnnotation.class,sumo);
                    if (!StringUtil.emptyString(multiWordToken.toString()))
                        tok.set(WNMWTokenAnnotation.class,multiWordToken.toString());
                    if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): set MW synset for token: " + RelExtract.toCoreLabelString(tok));
                    if (debug) System.out.println("sumo: " + tok.getString(WNMWSUMOAnnotation.class));
                    if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): set MW synset for index: " + index);
                    if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): set sumo: " + sumo);
                    if (debug) System.out.println("INFO in WNMultiWordAnnotator.annotateSentence(): set token: " +
                            multiWordToken.toString() + " sumo: " + sumo + " ip: " + ip);
                }
            }
        }
    }

    /****************************************************************
     * Mark all the multiwords in the text with their synset, sumo
     * term and the span of the multiword using tokens indexes (which
     * start at 0 and not token numbers, which start at 1)
     */
    public void annotate(Annotation annotation) {

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

        return Collections.singleton(WNMultiWordAnnotator.WNMultiWordAnnotation.class);
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
    public static void test1() {

        debug = true;
        KBmanager.getMgr().initializeOnce();
        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = setCoreLabel("John",1);
        al.add(cl);

        cl = setCoreLabel("is",2);
        cl.setLemma("be");
        al.add(cl);

        cl = setCoreLabel("on",3);
        al.add(cl);

        cl = setCoreLabel("the",4);
        al.add(cl);

        cl = setCoreLabel("floor",5);
        al.add(cl);
        annotateSentence(al);

        System.out.println("result: " + al);
    }

    /****************************************************************
     */
    public static void test2() {

        debug = true;
        KBmanager.getMgr().initializeOnce();
        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = setCoreLabel("Mary",1);
        al.add(cl);

        cl = setCoreLabel("is",2);
        cl.setLemma("be");
        al.add(cl);

        cl = setCoreLabel("out",3);
        al.add(cl);

        cl = setCoreLabel("of",4);
        al.add(cl);

        cl = setCoreLabel("cash",5);
        al.add(cl);
        annotateSentence(al);

        System.out.println("result: " + al);
    }

    /****************************************************************
     */
    public static void test3() {

        debug = true;
        KBmanager.getMgr().initializeOnce();
        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = setCoreLabel("Mary",1);
        al.add(cl);

        cl = setCoreLabel("likes",2);
        cl.setLemma("like");
        al.add(cl);

        cl = setCoreLabel("her",3);
        al.add(cl);

        cl = setCoreLabel("answering",4);
        al.add(cl);

        cl = setCoreLabel("machine",5);
        al.add(cl);
        annotateSentence(al);

        System.out.println("result: " + al);
    }

    /****************************************************************
     */
    public static void main(String[] args) {
        test3();
    }
}

