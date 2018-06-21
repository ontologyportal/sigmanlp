package com.articulate.nlp;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreLabel;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * Created by apease on 6/21/18.
 */
public class WNMWAnnotatorTest extends IntegrationTestBase {

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        KBmanager.getMgr().initializeOnce();
        WNMultiWordAnnotator.debug = true;
    }

    /** *************************************************************
     */
    @Test
    public void answeringMachine() {

        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = WNMultiWordAnnotator.setCoreLabel("Mary",1);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("likes",2);
        cl.setLemma("like");
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("her",3);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("answering",4);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("machine",5);
        al.add(cl);
        WNMultiWordAnnotator.annotateSentence(al);

        System.out.println("result: " + al);
        CoreLabel clam = al.get(3);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("AudioRecorder",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void cash() {

        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = WNMultiWordAnnotator.setCoreLabel("Mary",1);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("feels",2);
        cl.setLemma("be");
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("put",3);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("out",4);
        al.add(cl);

        WNMultiWordAnnotator.annotateSentence(al);
        System.out.println("result: " + al);
        CoreLabel clam = al.get(3);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("IntentionalPsychologicalProcess",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }

    /** *************************************************************
     */
    @Test
    public void floor() {

        ArrayList<CoreLabel> al =  new ArrayList<>();
        CoreLabel cl = null;
        cl = WNMultiWordAnnotator.setCoreLabel("John",1);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("is",2);
        cl.setLemma("be");
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("on",3);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("the",4);
        al.add(cl);

        cl = WNMultiWordAnnotator.setCoreLabel("floor",5);
        al.add(cl);
        WNMultiWordAnnotator.annotateSentence(al);

        System.out.println("result: " + al);
        CoreLabel clam = al.get(1);
        System.out.println(clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
        assertEquals("IntentionalProcess",clam.getString(WNMultiWordAnnotator.WNMWSUMOAnnotation.class));
    }
}
