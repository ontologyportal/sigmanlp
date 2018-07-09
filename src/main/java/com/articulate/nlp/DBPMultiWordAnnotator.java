package com.articulate.nlp;

import com.articulate.nlp.corpora.DBPedia;
import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.MultiWords;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;

import java.io.File;
import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntPair;

/**
 * This class marks WordNet multi-word strings.
 *
 * @author apease
 */

public class DBPMultiWordAnnotator extends MultiWordAnnotator {
	
    // Each CoreLabel in a multi-word string gets one that provides the entire
    // multi-word in DBP format.  Individual words will still have their own
    // synsets, so later processing should check tokens for multi-word membership
    public static class DBPMultiWordAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static class DBPMWSUMOAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    //spans are set to token numbers not array position index
    public static class DBPMWSpanAnnotation extends CoreAnnotations.SpanAnnotation {
        public Class<IntPair> getType() {
            return IntPair.class;
        }
    }

    public static class DBPMWTokenAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    /****************************************************************
     */
    public DBPMultiWordAnnotator() {

        System.out.println("DBPMultiWordAnnotator(): ");
        multiWordAnno = DBPMultiWordAnnotation.class;
        sumoAnno = DBPMWSUMOAnnotation.class;
        spanAnno = DBPMWSpanAnnotation.class;
        tokenAnno = DBPMWTokenAnnotation.class;
        DBPedia.initOnce();
        mw = DBPedia.multiWords;
        //String path = System.getenv("CORPORA") + File.separator + "DBPedia" + File.separator;
    }

    /****************************************************************
     * abstract method to find the SUMO term for the given key
     */
    @Override
    public String findSUMO(String key) {

        if (debug) System.out.println("DBPMultiWordAnnotator.findSUMO(): " + key);
        key = key.replace('_',' ');
        String sumo = DBPedia.stringToSUMO.get(key);
        if (sumo != null)
            sumo = WordNetUtilities.getBareSUMOTerm(sumo);
        return sumo;
    }

    /****************************************************************
     * no synsets for DBPedia
     */
    @Override
    public String findSynset(String key) {

        if (debug) System.out.println("DBPMultiWordAnnotator.findSynset(): " + key);
        return "";
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

        return Collections.singleton(DBPMultiWordAnnotator.DBPMultiWordAnnotation.class);
    }
}

