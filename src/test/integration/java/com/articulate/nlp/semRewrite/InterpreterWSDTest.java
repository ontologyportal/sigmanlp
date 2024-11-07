/*
Copyright 2014-2015 IPsoft
          2019 - Infosys

Author: Andrei Holub andrei.holub@ipsoft.com
        Adam Pease adam.pease@infosys.com
                   apease@articulatesoftware.com

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
package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import com.articulate.nlp.pipeline.SentenceUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class InterpreterWSDTest extends IntegrationTestBase {

    public static Interpreter interp = new Interpreter();

    /** ***************************************************************
     */
    @BeforeClass
    public static void initClauses() {

        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /****************************************************************
     */
    private void doTest(String sent, Literal[] expected) {

        System.out.println("\n\nInterpreterWSDTest: sentence: " + sent);

        Annotation wholeDocument = interp.userInputs.annotateDocument(sent);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<Literal> wsds = interp.findWSD(lastSentence);
        List<CoreLabel> lastSentenceTokens = lastSentence.get(CoreAnnotations.TokensAnnotation.class);
        List<Literal> results = interp.consolidateSpans(lastSentenceTokens,wsds);

        System.out.println("InterpreterWSDTest: actual: " + results);
        System.out.println("InterpreterWSDTest: expected: " + Arrays.toString(expected));
        Set<Literal> actualSet = new HashSet<>();
        Set<Literal> expectedSet = new HashSet<>();
        expectedSet.addAll(Arrays.asList(expected));
        actualSet.addAll(results);
        if (actualSet.containsAll(expectedSet))
            System.out.println("InterpreterWSDTest: pass");
        else
            System.out.println("InterpreterWSDTest: fail");
        assertTrue(actualSet.containsAll(expectedSet));
    }

    /** ***************************************************************
     */
    @Test
    public void findWSD_NoGroups() {

        String input = "Amelia Mary Earhart was an American aviator.";
        Literal[] expected = {
                //"names(Amelia-1,\"Amelia\")", // missed without real EntityParser information
                //"names(Mary-2,\"Mary\")",
                new Literal("sumo(DiseaseOrSyndrome,Amelia-1)"), // from WordNet: Amelia
                new Literal("sumo(Woman,Mary-2)"),
                new Literal("sumo(Woman,Earhart-3)"),
                new Literal("sumo(UnitedStates,American-6)"),
                new Literal("sumo(Pilot,aviator-7)")
        };
        doTest(input,expected);
    }

    /** ***************************************************************
     */
    @Test
    public void findWSD_WithGroups() {

        String input = "Amelia Mary Earhart (July 24, 1897 - July 2, 1937) was an American aviator.";
        Literal[] expected = {
                //"names(AmeliaMaryEarhart-1,\"Amelia Mary Earhart\")", // missed without real EntityParser information
                new Literal("sumo(UnitedStates,American-17)"),
                new Literal("sumo(Pilot,aviator-18)")
        };
        doTest(input,expected);
    }

    /** ***************************************************************
     */
    @Test
    public void findWSDDupNames() {

        Interpreter.debug = true;
        String input = "I also feel obliged to point out that Jack Nicklaus thinks so highly of " +
                "Muirfield that he named a course he built in Jack Nicklaus ' native Ohio after it .";
        Literal[] expected = {
                new Literal("sumo(Golfer,Jack_Nicklaus-9)"),
                new Literal("sumo(Golfer,Jack_Nicklaus-24)")
        };
        doTest(input,expected);
    }
}