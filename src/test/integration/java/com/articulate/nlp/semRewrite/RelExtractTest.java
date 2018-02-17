package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.StringUtil;
import com.articulate.sigma.IntegrationTestBase;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Ignore;
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
public class RelExtractTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        interpreter.inference = false;
        Interpreter.debug = true;
        Interpreter.replaceInstances = true;
        CNF.debug = true;
        //Procedures.debug = true;
        //KBcache.debug = true;
        //RHS.debug = true;
        //Literal.debug = true;
        KBmanager.getMgr().initializeOnce();
        interpreter.initialize();
        RelExtract.initOnce();
    }

    /****************************************************************
     */
    private void doRuleTest(String input, String rule, String expectedOutput) {

        Lexer lex = new Lexer(input);
        CNF cnfInput = CNF.parseSimple(lex);
        Rule r = new Rule();
        r = Rule.parseString(rule);
        CNF cnf = Clausifier.clausify(r.lhs);
        System.out.println();
        System.out.println("INFO in RelExtractTest.doRuleTest(): Input: " + cnfInput);
        System.out.println("INFO in RelExtractTest.doRuleTest(): CNF rule antecedent: " + cnf);
        HashMap<String, String> bindings = cnf.unify(cnfInput);
        System.out.println("bindings: " + bindings);
        RHS res = r.rhs.applyBindings(bindings);
        System.out.println("result: " + res);

        ArrayList<String> kifClauses = new ArrayList();
        String resultString = StringUtil.removeEnclosingCharPair(res.toString(),1,'{','}');

        assertEquals(expectedOutput, resultString);
    }

    /****************************************************************
     */
    private void doOneResultTest(String input, String expectedOutput) {

        ArrayList<CNF> inputs = Lists.newArrayList(interpreter.interpretGenCNF(input));
        System.out.println();
        System.out.println("-------------------");
        System.out.println("INFO in RelExtractTest.doTest(): Input: " + input);
        System.out.println("INFO in RelExtractTest.doTest(): CNF input: " + inputs);
        ArrayList<String> kifClauses = RelExtract.sentenceExtract(input);
        System.out.println("INFO in RelExtractTest.doTest(): result: " + kifClauses);
        System.out.println("INFO in RelExtractTest.doTest(): expected: " + expectedOutput);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0);
        assertEquals(expectedOutput,result);
    }

    /****************************************************************
     */
    private void doAllRuleTest(String input, String expectedOutput) {

        Lexer lex = new Lexer(input);
        CNF cnfInput = CNF.parseSimple(lex);
        ArrayList<CNF> inputs = new ArrayList<>();
        inputs.add(cnfInput);
        System.out.println();
        System.out.println("-------------------");
        System.out.println("INFO in RelExtractTest.doTest(): Input: " + input);
        System.out.println("INFO in RelExtractTest.doTest(): CNF input: " + inputs);
        ArrayList<String> kifClauses = RelExtract.cnfExtract(inputs);
        System.out.println("INFO in RelExtractTest.doTest(): result: " + kifClauses);
        System.out.println("INFO in RelExtractTest.doTest(): expected: " + expectedOutput);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0);
        assertEquals(expectedOutput,result);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testRobertWearsAShirt() {

        System.out.println("INFO in RelExtractTest.testRobertWearsAShirt()");
        String input = "sumo(Putting,wears-2), \n" +
                "root(ROOT-0,wears-2), \n" +
                "nsubj(wears-2,Robert-1), \n" +
                "sumo(Human,Robert-1), \n" +
                "dobj(wears-2,shirt-4), \n" +
                "attribute(Robert-1,Male), \n" +
                "names(Robert-1,\"Robert\"), \n" +
                "sumo(Shirt,shirt-4), \n" +
                "det(shirt-4,a-3), \n" +
                "number(SINGULAR, Robert-1), \n" +
                "lemma(Robert, Robert-1), \n" +
                "tense(PRESENT, wears-2), \n" +
                "lemma(wear, wears-2), \n" +
                "number(SINGULAR, shirt-4), \n" +
                "lemma(shirt, shirt-4)";
        String rule = "dobj(?wear,?item-6), lemma(wear, ?wear), nsubj(?wear,?animal-2), sumo(?TYPEVAR1,?animal-2), isSubclass(?TYPEVAR1,Animal), sumo(?TYPEVAR0,?item-6), isSubclass(?TYPEVAR0,WearableItem) ==> {(wears ?animal-2 ?item-6)}.";
        String expected = "(wears Robert-1 shirt-4)";

        doRuleTest(input, rule, expected);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimple() {

        System.out.println("INFO in RelExtractTest.testSimple()");
        String input = "dobj(wears-2,shirt-4), lemma(wear, wears-2)";
        String rule = "dobj(?wear,?item-6), lemma(wear, ?wear) ==> {(wears ?animal-2 ?item-6)}.";
        String expected = "(wears ?animal-2 shirt-4)";

        doRuleTest(input, rule, expected);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimpleSent1() {

        System.out.println("INFO in RelExtractTest.testSimpleSent1()");
        String input = "Robert wears a shirt";
        String expected = "(wears Robert-1 shirt-4)";

        doOneResultTest(input, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Test
    public void testSimpleHakawatiTheatre() {

        System.out.println("INFO in RelExtractTest.testSimpleHakawatiTheatre()");
        String input = "nmod:in(Hakawati_Theatre-6,JerusalemIsrael), sumo(Auditorium,Hakawati_Theatre-6), sumo(JerusalemIsrael,JerusalemIsrael)";
        String rule = "nmod:in(?object-7,?physical-2),  sumo(?TYPEVAR1,?object-7), " +
                "sumo(?TYPEVAR0,?physical-2), isChildOf(?TYPEVAR0,Physical), isChildOf(?TYPEVAR1,Object) ==> " +
                "{(located ?object-7 ?physical-2)}.";
        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doRuleTest(input, rule, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Test
    public void testDepHakawatiTheatre() {

        System.out.println("INFO in RelExtractTest.testDepHakawatiTheatre()");
        String input = "acl(series-14,killed-19),\n" +
                "amod(JerusalemIsrael,East),\n" +
                "case(Hakawati_Theatre-6,at-4),\n" +
                "case(JerusalemIsrael,in-8),\n" +
                "case(Palestinians-18,of-17),\n" +
                "case(portraits-16,of-15),\n" +
                "case(rebellion-22,in-20),\n" +
                "compound(exhibit-3,art-2),\n" +
                "compound(JerusalemIsrael,Arab-9),\n" +
                "cop(series-14,was-12),\n" +
                "det(exhibit-3,An-1),\n" +
                "det(Hakawati_Theatre-6,the-5),\n" +
                "det(rebellion-22,the-21),\n" +
                "det(series-14,a-13),\n" +
                "lemma(Arab, Arab-9),\n" +
                "lemma(art, art-2),\n" +
                "lemma(be, was-12),\n" +
                "lemma(exhibit, exhibit-3),\n" +
                "lemma(Hakawati, Hakawati-6),\n" +
                "lemma(Jerusalem, Jerusalem-11),\n" +
                "lemma(kill, killed-19),\n" +
                "lemma(Palestinians, Palestinians-18),\n" +
                "lemma(portrait, portraits-16),\n" +
                "lemma(series, series-14),\n" +
                "lemma(rebellion, rebellion-22),\n" +
                "lemma(Theatre, Theatre-7),\n" +
                "nmod:at(exhibit-3,Hakawati_Theatre-6),\n" +
                "nmod:in(Hakawati_Theatre-6,JerusalemIsrael),\n" +
                "nmod:in(killed-19,rebellion-22),\n" +
                "nmod:of(portraits-16,Palestinians-18),\n" +
                "nmod:of(series-14,portraits-16),\n" +
                "nsubj(series-14,exhibit-3),\n" +
                "number(PLURAL, Palestinians-18),\n" +
                "number(PLURAL, portraits-16),\n" +
                "number(SINGULAR, Arab-9),\n" +
                "number(SINGULAR, art-2),\n" +
                "number(SINGULAR, exhibit-3),\n" +
                "number(SINGULAR, Hakawati-6),\n" +
                "number(SINGULAR, Jerusalem-11),\n" +
                "number(SINGULAR, rebellion-22),\n" +
                "number(SINGULAR, series-14),\n" +
                "number(SINGULAR, Theatre-7),\n" +
                "root(ROOT-0,series-14),\n" +
                "sumo(ArtWork,art-2),\n" +
                "sumo(Attribute,was-12),\n" +
                "sumo(Auditorium,Hakawati_Theatre-6),\n" +
                "sumo(City,JerusalemIsrael),\n" +
                "sumo(Collection,series-14),\n" +
                "sumo(ContentBearingObject,exhibit-3),\n" +
                "sumo(Contest,rebellion-22),\n" +
               // "sumo(DirectionalAttribute,East),\n" + // added
                "sumo(Horse,Arab-9),\n" +
                "sumo(Killing,killed-19),\n" +
                "sumo(Text,portraits-16),\n" +
                "tense(PAST, killed-19),\n" +
                "tense(PAST, was-12)";

        String rule = "nmod:in(?object-7,?physical-2),  sumo(?TYPEVAR1,?object-7), " +
                "sumo(?TYPEVAR0,?physical-2), isChildOf(?TYPEVAR0,Physical), isChildOf(?TYPEVAR1,Object) ==> " +
                "{(located ?object-7 ?physical-2)}.";
        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doRuleTest(input, rule, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Test
    public void testDepAllRulesHakawatiTheatre() {

        System.out.println("INFO in RelExtractTest.testDepAllRulesHakawatiTheatre()");
        String input = "acl(series-14,killed-19),\n" +
                "amod(JerusalemIsrael,East),\n" +
                "case(Hakawati_Theatre-6,at-4),\n" +
                "case(JerusalemIsrael,in-8),\n" +
                "case(Palestinians-18,of-17),\n" +
                "case(portraits-16,of-15),\n" +
                "case(rebellion-22,in-20),\n" +
                "compound(exhibit-3,art-2),\n" +
                "compound(JerusalemIsrael,Arab-9),\n" +
                "cop(series-14,was-12),\n" +
                "det(exhibit-3,An-1),\n" +
                "det(Hakawati_Theatre-6,the-5),\n" +
                "det(rebellion-22,the-21),\n" +
                "det(series-14,a-13),\n" +
                "lemma(Arab, Arab-9),\n" +
                "lemma(art, art-2),\n" +
                "lemma(be, was-12),\n" +
                "lemma(exhibit, exhibit-3),\n" +
                "lemma(Hakawati, Hakawati-6),\n" +
                "lemma(Jerusalem, Jerusalem-11),\n" +
                "lemma(kill, killed-19),\n" +
                "lemma(Palestinians, Palestinians-18),\n" +
                "lemma(portrait, portraits-16),\n" +
                "lemma(series, series-14),\n" +
                "lemma(rebellion, rebellion-22),\n" +
                "lemma(Theatre, Theatre-7),\n" +
                "nmod:at(exhibit-3,Hakawati_Theatre-6),\n" +
                "nmod:in(Hakawati_Theatre-6,JerusalemIsrael),\n" +
                "nmod:in(killed-19,rebellion-22),\n" +
                "nmod:of(portraits-16,Palestinians-18),\n" +
                "nmod:of(series-14,portraits-16),\n" +
                "nsubj(series-14,exhibit-3),\n" +
                "number(PLURAL, Palestinians-18),\n" +
                "number(PLURAL, portraits-16),\n" +
                "number(SINGULAR, Arab-9),\n" +
                "number(SINGULAR, art-2),\n" +
                "number(SINGULAR, exhibit-3),\n" +
                "number(SINGULAR, Hakawati-6),\n" +
                "number(SINGULAR, Jerusalem-11),\n" +
                "number(SINGULAR, rebellion-22),\n" +
                "number(SINGULAR, series-14),\n" +
                "number(SINGULAR, Theatre-7),\n" +
                "root(ROOT-0,series-14),\n" +
                "sumo(ArtWork,art-2),\n" +
                "sumo(Attribute,was-12),\n" +
                "sumo(Auditorium,Hakawati_Theatre-6),\n" +
                "sumo(City,JerusalemIsrael),\n" +
                "sumo(Collection,series-14),\n" +
                "sumo(ContentBearingObject,exhibit-3),\n" +
                "sumo(Contest,rebellion-22),\n" +
                "sumo(Horse,Arab-9),\n" +
                "sumo(Killing,killed-19),\n" +
                "sumo(Text,portraits-16),\n" +
                "tense(PAST, killed-19),\n" +
                "tense(PAST, was-12)";

        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doAllRuleTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testSentHakawatiTheatre() {

        System.out.println("INFO in RelExtractTest.testSentHakawatiTheatre()");
        String input = " An art exhibit at the Hakawati Theatre in Arab east Jerusalem was a series of portraits of Palestinians killed in the rebellion .";
        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doOneResultTest(input, expected);
    }
}
