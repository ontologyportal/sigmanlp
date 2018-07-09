package com.articulate.nlp;

import com.articulate.nlp.corpora.DBPedia;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by apease on 7/2/18.
 */
public class DBPediaTest {

    private String defaultProp = "tokenize, ssplit, pos, lemma, " +
            "ner, nersumo, gender, parse, coref, depparse, wnmw, wsd, tsumo, dbpmw";

    public static Interpreter interp;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        System.out.println("DBPediaTest.setUpInterpreter()");
        KBmanager.getMgr().initializeOnce();
        DBPedia.initOnce();
        interp = new Interpreter(defaultProp);
        //interp.initOnce();
        DBPMultiWordAnnotator.debug = true;
        MultiWordAnnotator.debug = true;
    }

    /** *************************************************************
     */
    @Test
    public void test1() {

        System.out.println("DBPediaTest.test1()");
        String text = "I'm driving to Palacios de Riopisuerga.";
        //System.out.println("Palacios: " + DBPedia.multiWords.multiWord.get("Palacios"));
        Annotation wholeDocument = interp.p.annotate(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(5);
        System.out.println(clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
        assertEquals("City",clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void test2() {

        String text = "I attended Sacred Heart Academy.";
        //System.out.println("Sacred: " + DBPedia.multiWords.multiWord.get("Sacred"));
        Annotation wholeDocument = interp.p.annotate(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(3);
        System.out.println(clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
        assertEquals("School",clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
    }

    /** *************************************************************
     * Make sure the WordNet annotator works at the same time
     */
    @Test
    public void test3() {

        String text = "I flew Air Zambezi to the Zambezi River.";
        //System.out.println("Sacred: " + DBPedia.multiWords.multiWord.get("Sacred"));
        Annotation wholeDocument = interp.p.annotate(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(3);
        System.out.println(clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
        assertEquals("Airline",clam.getString(DBPMultiWordAnnotator.DBPMWSUMOAnnotation.class));
        clam = lastSentenceTokens.get(7);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("River",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }
}
