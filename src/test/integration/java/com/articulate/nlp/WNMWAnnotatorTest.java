package com.articulate.nlp;

import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by apease on 6/21/18.
 */
public class WNMWAnnotatorTest extends IntegrationTestBase {

    public static Interpreter interp;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        KBmanager.getMgr().initializeOnce();
        interp = new Interpreter();
        interp.initOnce();
        WNMultiWordAnnotator.debug = true;
        MultiWordAnnotator.debug = true;
    }

    /** *************************************************************
     */
    @Test
    public void answeringMachine() {

        String text = "Mary likes her answering machine.";
        Annotation wholeDocument = interp.userInputs.annotateDocument(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(3);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("AudioRecorder",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void cash() {

        String text = "Mary feels put out.";
        Annotation wholeDocument = interp.userInputs.annotateDocument(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(3);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("TherapeuticProcess",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void floor() {

        String text = "John is on the floor.";
        Annotation wholeDocument = interp.userInputs.annotateDocument(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(1);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("IntentionalProcess",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void testHyundai() {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("has Hyundai term " + kb.containsTerm("HyundaiAutomobile"));
        System.out.println("has Hyundai word " + WordNet.wn.multiWords.multiWord.containsKey("Hyundai"));
        String text = "I like my Hyundai Equus.";
        Annotation wholeDocument = interp.userInputs.annotateDocument(text);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        System.out.println("result: " + lastSentenceTokens);
        CoreLabel clam = lastSentenceTokens.get(4);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("HyundaiEquus",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }
}
