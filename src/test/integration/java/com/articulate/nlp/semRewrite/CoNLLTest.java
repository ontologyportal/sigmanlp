package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.sigma.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

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
public class CoNLLTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        interpreter.inference = false;
        Interpreter.debug = true;
        Interpreter.replaceInstances = true;
        //CNF.debug = true;
        //Procedures.debug = true;
        //KBcache.debug = true;
        //RHS.debug = true;
        //Literal.debug = true;
        KBmanager.getMgr().initializeOnce();
        interpreter.initialize();
        RelExtract.initOnce("Relations.txt");
    }

    /****************************************************************
     */
    private void doOneResultTest(String input, String expectedOutput) {

        long startTime = System.currentTimeMillis();
        ArrayList<CNF> inputs = Lists.newArrayList(interpreter.interpretGenCNF(input));
        System.out.println();
        System.out.println("-------------------");
        System.out.println("INFO in CoNLLTest.doTest(): Input: " + input);
        System.out.println("INFO in CoNLLTest.doTest(): CNF input: " + inputs);
        ArrayList<String> kifClauses = RelExtract.sentenceExtract(input);
        System.out.println("INFO in CoNLLTest.doTest(): result: " + kifClauses);
        System.out.println("INFO in CoNLLTest.doTest(): expected: " + expectedOutput);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0);
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("time to process: " + seconds + " seconds (not counting init)");
        assertEquals(expectedOutput,result);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimpleSent1() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSimpleSent1()");
        String input = "Robert wears a shirt";
        String expected = "(wears Robert-1 shirt-4)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentHakawatiTheatre() {

        System.out.println("INFO in CoNLLTest.testSentHakawatiTheatre()");
        String input = " An art exhibit at the Hakawati Theatre in Arab east Jerusalem " +
                "was a series of portraits of Palestinians killed in the rebellion .";
        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentTuvia() {

        System.out.println("INFO in CoNLLTest.testSentTuvia()");
        String input = "Israel television rejected a skit by comedian Tuvia Tzafir " +
                "that attacked public apathy by depicting an Israeli family watching TV while a fire raged outside .";
        String expected = "";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentUmbria() {

        System.out.println("INFO in CoNLLTest.testSentUmbria()");
        String input = "Officials in Perugia in Umbria province are happy .";
        String expected = "(located Perugia Umbria)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentCampania() {

        System.out.println("INFO in CoNLLTest.testSentCampania()");
        String input = "Rome is in Lazio province and Naples in Campania .";
        String expected = "(located Rome Lazio)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentWork() {

        System.out.println("INFO in CoNLLTest.testSentWork()");
        String input = "W. Dale Nelson covers the White House for The Associated Press .";
        String expected = "(employs WDaleNelson TheAssociatedPress)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentWright() {

        System.out.println("INFO in CoNLLTest.testSentWright()");
        String input = "Wright , a University of Texas law professor , is happy.";
        String expected = "(employs UniversityOfTexas Wright)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentBattavia() {

        System.out.println("INFO in CoNLLTest.testSentBattavia()");
        String input = " One message came from Anderson 's sister , Peggy Say of Batavia , N.Y.";
        String expected = "(located Battavia NewYork)";
        // should also say that Peggy lives in Batavia but that's not marked in the corpus

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentGainesville() {

        System.out.println("INFO in CoNLLTest.testSentGainesville()");
        String input = "The third Weerts child , 12-year-old Tanya , died Saturday evening at Shands Hospital in nearby Gainesville .";
        String expected = "(located ShandsHospital Gainesville)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentZambia() {

        System.out.println("INFO in CoNLLTest.testSentZambia()");
        String input = "Crocodiles in Zambia 's Kafue River have devoured five people since the beginning of the year";
        String expected = "(located KafueRiver Zambia)";  // CoreNLP NER divides Kafue and River but also makes it a compound()

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentNsukka() {

        System.out.println("INFO in CoNLLTest.testSentZambia()");
        String input = "The registrar of the University of Nigeria 's Nsukka campus , U. Umeh , said he had closed the university on the advice of the government .";
        String expected = "(located NsukkaCampus Nigeria)";  // CoreNLP NER divides Kafue and River but also makes it a compound()

        doOneResultTest(input, expected);
    }
}
