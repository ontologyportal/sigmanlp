package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.corpora.CoNLL04;
import com.articulate.sigma.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

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
    private static CoNLL04 conll04 = new CoNLL04();

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
        conll04.parse();
        System.out.println("INFO in CoNLLTest.setUpInterpreter(): completed initialization");
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
        ArrayList<RHS> kifClauses = RelExtract.sentenceExtract(input);
        System.out.println("INFO in CoNLLTest.doTest(): result: " + kifClauses);
        System.out.println("INFO in CoNLLTest.doTest(): expected: " + expectedOutput);

        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0).toString();
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("time to process: " + seconds + " seconds (not counting init)");
        assertEquals(expectedOutput,result);
    }

    /****************************************************************
     */
    private void doOneScoreResultTest(String input, String expectedOutput, CoNLL04.Sent s) {

        long startTime = System.currentTimeMillis();
        ArrayList<CNF> inputs = Lists.newArrayList(interpreter.interpretGenCNF(input));
        System.out.println();
        System.out.println("-------------------");
        System.out.println("INFO in CoNLLTest.doTest(): Input: " + input);
        System.out.println("INFO in CoNLLTest.doTest(): CNF input: " + inputs);
        ArrayList<RHS> kifClauses = RelExtract.sentenceExtract(input);
        System.out.println("INFO in CoNLLTest.doTest(): result: " + kifClauses);
        System.out.println("INFO in CoNLLTest.doTest(): expected: " + expectedOutput);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        Set<CoNLL04.Relation> rels = conll04.toCoNLLRels(kifClauses,s);
        CoNLL04.F1Matrix mat = conll04.score(rels,s.relations);
        System.out.println("CoNLL: score: " + mat);
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0).toString();
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("time to process: " + seconds + " seconds (not counting init)");
        assertEquals(expectedOutput,result);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimpleSent1() {

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
        String expected = "(located Hakawati_Theatre-6 Jerusalem-11)";

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
        String expected = "(employs Associated_Press-10 W._Dale_Nelson-1)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testScoreWork() {

        System.out.println("INFO in CoNLLTest.testSentWork()");
        String input = "W. Dale Nelson covers the White House for The Associated Press .";
        String expected = "(employs Associated_Press-10 W._Dale_Nelson-1)";
        CoNLL04.Sent s = conll04.sentIndex.get(46); // sentence number in CoNLL04 corpus
        doOneScoreResultTest(input, expected, s);
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
    public void testSentBatavia() {

        System.out.println("INFO in CoNLLTest.testSentBatavia()");
        String input = " One message came from Anderson 's sister , Peggy Say of Batavia , N.Y.";
        String expected = "(located Batavia-12 N.Y.-14)";
        // should also say that Peggy lives in Batavia but that's not marked in the corpus

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentGainesville() {

        System.out.println("INFO in CoNLLTest.testSentGainesville()");
        String input = "The third Weerts child , 12-year-old Tanya , died Saturday " +
                "evening at Shands Hospital in nearby Gainesville .";
        String expected = "(located ShandsHospital Gainesville)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentZambia() {

        System.out.println("INFO in CoNLLTest.testSentZambia()");
        String input = "Crocodiles in Zambia 's Kafue River have devoured five people " +
                "since the beginning of the year";
        String expected = "(located KafueRiver Zambia)";  // CoreNLP NER divides Kafue and River but also makes it a compound()

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentNsukka() {

        System.out.println("INFO in CoNLLTest.testSentZambia()");
        String input = "The registrar of the University of Nigeria 's Nsukka campus , U. Umeh , " +
                "said he had closed the university on the advice of the government .";
        String expected = "(located NsukkaCampus Nigeria)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentBedford() {

        System.out.println("INFO in CoNLLTest.testSentBedford()");
        String input = "He said he is not sure what caused the rift between the brothers , who grew " +
                "up in Bedford in southern Indiana playing basketball and fishing together.";
        String expected = "(located Bedford Indiana)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentMadagascar() {

        System.out.println("INFO in CoNLLTest.testSentMadagascar()");
        String input = "The program , financed with a grant from the United States Agency for International " +
                "Development , is aimed at reducing infant mortality in Madagascar , an island " +
                "nation in the Indian Ocean off the eastern coast of Africa.";
        String expected = "(located Madagascar IndianOcean)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentMadonna() {

        System.out.println("INFO in CoNLLTest.testSentMadonna()");
        String input = "As a real native Detroiter , I want to remind everyone that Madonna is from Bay City , Mich. , " +
                "a nice place in the thumb of the state 's lower peninsula .";
        String expected = "(birthplace Madonna-13 Bay_City-16)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentWilson() {

        System.out.println("INFO in CoNLLTest.testSentWilson()");
        String input = "True , Woodrow Wilson was born in Virginia , but he won his political fame as governor of New Jersey , " +
                "so he hardly counted as a Southerner in political terms .";
        String expected = "(birthplace Woodrow_Wilson-3 Virginia-8)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentFargo() {

        System.out.println("INFO in CoNLLTest.testSentFargo()");
        String input = "Mr. Vee , a Fargo native , sent one of his recording contracts and an old sweater.";
        String expected = "(birthplace Vee-2 Fargo-3)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentOhio() {

        // amod(Ohio-28,native-27), nmod:poss(Ohio-28,Jack_Nicklaus-24), sumo(Ohio,Ohio-28),
        // sumo(Golfer,Jack_Nicklaus-9)

        // amod(?LOC,native*), nmod:poss(?LOC,?animal), isChildOf(?TYPEVAR0,Animal), sumo(?TYPEVAR0,?animal),
        // isChildOf(?TYPEVAR1,Object), sumo(?TYPEVAR1,?LOC) ==> {(birthplace ?animal ?LOC)}.

        // needs coref - replaced "his" with "Jack Nicklaus"
        System.out.println("INFO in CoNLLTest.testSentOhio()");
        String input = "I also feel obliged to point out that Jack Nicklaus thinks so highly of " +
                "Muirfield that he named a course he built in Jack Nicklaus ' native Ohio after it .";
             // "Muirfield that he named a course he built in his native Ohio after it .";
        String expected = "(birthplace Jack_Nicklaus-4 Ohio-28)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentLazio() {

        System.out.println("INFO in CoNLLTest.testSentLazio()");
        String input = "The ANSA news agency said the operation involved police in Umbria and Lazio provinces " +
                "in central Italy and Campania province in the south , without specifying exact arrest locations .";
        String expected = "(birthplace Jack_Nicklaus-4 Ohio-28)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentCharlotte() {

        System.out.println("INFO in CoNLLTest.testSentCharlotte()");
        String input = "Nor did he argue , as he did in a speech at the University of Virginia in Charlottesville " +
                "Dec. 16 , that Congress had perpetuated a dangerous situation in Central America by its ` ` on-again , " +
                "off-again indecisiveness ' ' on his program of aid to the anti-communist Contra rebels .";
        String expected = "(located University_of_Virginia-14,Charlottesville-16)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentYale() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentYale()");
        String input = "Winter , 53 , a former Yale University law professor who took the bench in 1982 , and Starr , a " +
                "fellow appointee of President Reagan , are both known as judicial conservatives .";
        String expected = "(employs Yale_University-7 Winter-1)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentQuayle() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentQuayle()");
        String input = "Dan Quayle 's first trip out of the country as vice is likely to be to Caracas , Venezuela , " +
                "for Carlos Andres Perez 's presidential inauguration on Feb. 2 COMMA an official in President-elect " +
                "Bush 's transition said Thursday .";
        String expected = "(located Caracas-15 Venezuela-17)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentRubble() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentRubble()");
        String input = "On Dec. 14 COMMA rescuers pulled a mother , Susanna Petrosyan , and her 4-year-old daughter , " +
                "Gayaney , out of the rubble in Leninakan .";
        String expected = "(located Susanna_Petrosyan-7 Leninakan-21), (located Gayaney-14 Leninakan-21)";
        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentRockyFlats() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentRockyFlats()");
        String input = "It recommended stopping nuclear fuels processing at four sites where problems have been highly " +
                "publicized : the Rocky Flats Plant near Denver , the Hanford Site near Richland , Wash. , the Feed " +
                "Materials Production Center at Fernald , Ohio , and the Mound Plant in Ohio .";
        String expected = "(located Richmod-24 Wash.-26), (located Mound_Plant-37 Ohio-39)";
        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentRay() {

        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentRay()");
        String input = "In 1977 , James Earl Ray , the convicted assassin of civil rights leader Dr. Martin Luther King " +
                "Jr. , was recaptured following his escape from a Tennessee prison June 10 .";
        String expected = "(kills James_Earl_Ray-4 Martin_Luther_King_Jr.-16)";
        doOneResultTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentScoreAriz() {

        CoNLL04 conll = new CoNLL04();
        String l1 = "341	Loc	0	O	NNP	TUCSON	O	O	O";
        String l2 = "341	O	1	O	,	COMMA	O	O	O";
        String l3 = "341	Loc	2	O	NNP/.	Ariz/.	O	O	O";

        CoNLL04.Token t1 = conll.tokparse(l1.split("\t"));
        CoNLL04.Token t2 = conll.tokparse(l2.split("\t"));
        CoNLL04.Token t3 = conll.tokparse(l3.split("\t"));
        String r1 = "0	2	Located_In";
        CoNLL04.Relation rel = conll.new Relation();
        rel.first = 0;
        rel.second = 2;
        rel.relName = "Located_In";
        CoNLL04.Sent sent = conll.new Sent();
        sent.tokens.add(t1);
        sent.tokens.add(t2);
        sent.tokens.add(t3);
        sent.relations.add(rel);
        conll.makeTokenMap(sent);
        Interpreter.debug = true;
        System.out.println("INFO in CoNLLTest.testSentAriz()");
        String input = "TUCSON , Ariz .";

        ArrayList<RHS> kifClauses = RelExtract.sentenceExtract(input);
        Set<CoNLL04.Relation> rels = conll.toCoNLLRels(kifClauses,sent);
        System.out.println("testSentScoreAriz(): rels: " + rels);
        System.out.println("testSentScoreAriz(): tokmap: " + sent.tokMap.toString());
        CoNLL04.F1Matrix mat = conll.score(rels,sent.relations);
        System.out.println("testSentScoreAriz(): mat: " + mat);
        assertEquals(mat.truePositives,1);
    }

    /****************************************************************
     * "Sam Smith liked steak" will be
     * Sam_Smith-1 liked-2 steak-3 under CoNLL but
     * Sam-1 Smith-2 liked-3 steak-4 under CoreNLP.
     */
    @Test
    public void testTokMap() {

        CoNLL04.debug = true;
        System.out.println("INFO in CoNLLTest.testTokMap()");
        CoNLL04 conll = new CoNLL04();
        CoNLL04.Sent sent = conll.new Sent();
        String t1 = "5002\tPeop\t0\tO\tNNP/NNP\tSam/Smith\tO\tO\tO";
        String t2 = "5002\tO\t1\tO\tVBD\tliked\tO\tO\tO";
        String t3 = "5002\tO\t2\tO\tNN\tsteak\tO\tO\tO";

        String[] tabbed = t1.split("\\t");
        CoNLL04.Token tok1 = conll.tokparse(tabbed);
        sent.tokens.add(tok1);

        tabbed = t2.split("\\t");
        CoNLL04.Token tok2 = conll.tokparse(tabbed);
        sent.tokens.add(tok2);

        tabbed = t3.split("\\t");
        CoNLL04.Token tok3 = conll.tokparse(tabbed);
        sent.tokens.add(tok3);

        conll.makeTokenMap(sent);
        //System.out.println(sent);
        //System.out.println(sent.tokMap);
        assertEquals(sent.tokMap.toString(),"{0=0, 1=0, 2=0, 3=1, 4=2}");

    }
}
