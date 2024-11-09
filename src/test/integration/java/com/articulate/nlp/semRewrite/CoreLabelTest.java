package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import static org.junit.Assert.assertTrue;

/*
Copyright 2017-     Infosys

Author: Adam Pease apease@articulatesoftware.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA
*/
public class CoreLabelTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        Interpreter.inference = false;
        Interpreter.debug = true;
        Interpreter.replaceInstances = false;
        //CNF.debug = true;
        //Procedures.debug = true;
        //KBcache.debug = true;
        //RHS.debug = true;
        //Literal.debug = true;
        KBmanager.getMgr().initializeOnce();
        interpreter.initialize();
        RelExtract.initOnce();
    }

    /** *************************************************************
     * test printing of complete information in CoreLabels using
     * printCoreLabel()
     */
    @Test
    public void testCoreLabel() {

        String input = "Robert sits on the mat.";
        System.out.println("RelExtract.testCoreLabel():");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        Annotation anno = interp.p.annotate(input);
        List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
        System.out.println("RelExtract.testCoreLabel(): input: " + input);
        List<CoreLabel> tokens = null;
        for (CoreMap sentence : sentences) {
            tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel cl : tokens) {
                RelExtract.printCoreLabel(cl);
            }
        }
        assertTrue(tokens != null && !tokens.isEmpty());
    }
}
