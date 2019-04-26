package com.articulate.nlp;

import com.articulate.nlp.pipeline.SentenceUtil;
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

public class WNMultiWordAnnotator extends MultiWordAnnotator {
	
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
    public WNMultiWordAnnotator(String n, Properties props) {

        System.out.println("WNMultiWordAnnotator()");
        multiWordAnno = WNMultiWordAnnotation.class;
        sumoAnno = WNMWSUMOAnnotation.class;
        spanAnno = WNMWSpanAnnotation.class;
        tokenAnno = WNMWTokenAnnotation.class;
        mw = WordNet.wn.multiWords;
        name = "WNMultiWordAnnotator";
    }

    /****************************************************************
     * Method to find the SUMO term for the given key
     */
    @Override
    public String findSUMO(String key) {

        System.out.println("WNMultiWordAnnotator.findSUMO(): " + key);
        //Collection<String> synsets = WordNetUtilities.wordsToSynsets(key);
        Collection<String> synsets = WordNet.wn.getSynsetsFromWord(key);
        System.out.println("WNMultiWordAnnotator.findSUMO():sense keys: " + WordNet.wn.wordsToSenseKeys.get(key));
        System.out.println("WNMultiWordAnnotator.findSUMO():synsets: " + synsets);
        if (synsets != null)
            System.out.println("WNMultiWordAnnotator.findSUMO():synset: " + synsets);
        if (synsets == null || synsets.size() == 0)
            return null;
        String sumo = WordNetUtilities.getBareSUMOTerm(WordNet.wn.getSUMOMapping(synsets.iterator().next()));
        System.out.println("WNMultiWordAnnotator.findSUMO():sumo: " + sumo);
        return sumo;
    }

    /****************************************************************
     * Method to find the synset for the given key
     */
    @Override
    public String findSynset(String key) {

        System.out.println("WNMultiWordAnnotator.findSynset(): " + key);
        Collection<String> synsets = WordNetUtilities.wordsToSynsets(key);
        if (synsets == null || synsets.size() == 0)
            return null;
        String synset = synsets.iterator().next();
        return synset;
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
    public static void main(String[] args) {

    }
}

