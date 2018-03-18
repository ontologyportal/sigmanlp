package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.sigma.IntegrationTestBase;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertThat;

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
public class SentenceUtilTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        interpreter.inference = false;
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
    public void testToDependencies() {

        String input = "Robert sits on the mat.";
        System.out.println("SentenceUtilTest.testToDependencies():");

        Annotation anno = interpreter.p.annotate(input);
        CoreMap sentence = SentenceUtil.getLastSentence(anno);
        ArrayList<Literal> lits = SentenceUtil.toDependenciesList(sentence);
        assertThat(lits, hasItems(new Literal("nmod:on(sits-2,mat-5)")));
    }

    /** *************************************************************
     * test printing of complete information in CoreLabels using
     * printCoreLabel()
     */
    @Test
    public void testToEdges() {

        String input = "Robert sits on the mat.";
        System.out.println("SentenceUtilTest.testToEdges():");

        Annotation anno = interpreter.p.annotate(input);
        CoreMap sentence = SentenceUtil.getLastSentence(anno);
        ArrayList<SemanticGraphEdge> edges = SentenceUtil.toEdgesList(sentence);
        System.out.println("Edges: " + edges);
        ArrayList<Literal> lits = SentenceUtil.toDepList(sentence);
        System.out.println("Deps: " + lits);
        assertThat(lits, hasItems(new Literal("nmod:on(sits-2,mat-5)")));
        //assertThat(edges, hasItems(new Literal("nmod:on(sits-2,mat-5)")));
    }
}
