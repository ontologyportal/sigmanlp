package com.articulate.nlp.semRewrite;

import com.articulate.nlp.RelExtract;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.IntegrationTestBase;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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
        //Interpreter.debug = true;
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

    /****************************************************************
     * @param input is a CNF string
     */
    private void doRuleTest(String title, String input, String rule, String expectedOutput) {

        System.out.println();
        System.out.println("-------------------" + title + "-------------------");
        Lexer lex = new Lexer(input);
        CNF cnfInput = CNF.parseSimple(lex);

        Rule r = new Rule();
        r = Rule.parseString(rule);
        r.cnf = Clausifier.clausify(r.lhs);
        System.out.println();
        System.out.println("INFO in RelExtractTest.doRuleTest(): CNF Input: " + cnfInput);
        System.out.println("INFO in RelExtractTest.doRuleTest(): rule: " + rule);
        System.out.println("INFO in RelExtractTest.doRuleTest(): CNF rule antecedent: " + r.cnf);

        HashSet<String> preds = cnfInput.getPreds();
        HashSet<String> terms = cnfInput.getTerms();
        System.out.println("RelExtractTest.doRuleTest(): input preds: " + preds);
        System.out.println("RelExtractTest.doRuleTest(): input terms: " + terms);

        System.out.println("RelExtractTest.doRuleTest(): rule preds: " + r.preds);
        System.out.println("RelExtractTest.doRuleTest(): rule terms: " + r.terms);

        if (!interpreter.termCoverage(preds,terms,r))
            System.out.println("RelExtractTest.doRuleTest(): no coverage for rule ");
        Subst bindings = r.cnf.unify(cnfInput);
        System.out.println("bindings: " + bindings);
        RHS res = r.rhs.applyBindings(bindings);


        String resultString = StringUtil.removeEnclosingCharPair(res.toString(),1,'{','}');

        System.out.println("result: " + resultString);
        System.out.println("expected: " + expectedOutput);
        if (resultString.equals(expectedOutput))
            System.out.println("RelExtractTest.doRuleTest(): pass");
        else
            System.out.println("RelExtractTest.doRuleTest(): fail");
        assertEquals(expectedOutput, resultString);
        System.out.println("-------------------");
    }

    /****************************************************************
     * @param input is a sentence
     */
    private void doOneResultTest(String title, String input, String expectedOutput) {

        System.out.println();
        System.out.println("-------------------" + title + "-------------------");
        System.out.println("INFO in RelExtractTest.doOneResultTest(): Input: " + input);
        ArrayList<RHS> kifClauses = RelExtract.sentenceExtract(input);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0).toString();
        System.out.println("INFO in RelExtractTest.doOneResultTest(): result: " + result);
        System.out.println("INFO in RelExtractTest.doOneResultTest(): expected: " + expectedOutput);
        if (result.equals(expectedOutput))
            System.out.println("RelExtractTest.doOneResultTest(): pass");
        else
            System.out.println("RelExtractTest.doOneResultTest(): fail");
        assertEquals(expectedOutput,result);
        System.out.println("-------------------");
    }

    /****************************************************************
     * @param input is a CNF string
     */
    private void doAllRuleTest(String title, String input, String expectedOutput) {

        Lexer lex = new Lexer(input);
        CNF cnfInput = CNF.parseSimple(lex);
        ArrayList<CNF> inputs = new ArrayList<>();
        inputs.add(cnfInput);
        System.out.println();
        System.out.println("-------------------" + title + "-------------------");
        System.out.println("INFO in RelExtractTest.doAllRuleTest(): Input: " + input);
        System.out.println("INFO in RelExtractTest.doAllRuleTest(): CNF input: " + inputs);
        ArrayList<RHS> kifClauses = new ArrayList<>();
        HashSet<String> preds = cnfInput.getPreds();
        HashSet<String> terms = cnfInput.getTerms();
        for (Rule r : RelExtract.rs.rules) {
            if (!interpreter.termCoverage(preds, terms, r))
                continue;
            Subst bindings = r.cnf.unify(cnfInput);
            if (bindings != null) {
                System.out.println("bindings: " + bindings);
                System.out.println("rule: " + r);
                RHS res = r.rhs.applyBindings(bindings);
                kifClauses.add(res);
                System.out.println("result: " + res);
            }
        }
        System.out.println("INFO in RelExtractTest.doAllRuleTest(): result: " + kifClauses);
        System.out.println("INFO in RelExtractTest.doAllRuleTest(): expected: " + expectedOutput);
        //String result = StringUtil.removeEnclosingCharPair(kifClauses.toString(),0,'[',']');
        String result = "";
        if (kifClauses != null && kifClauses.size() > 0)
            result = kifClauses.get(0).toString();
        if (result.equals(expectedOutput))
            System.out.println("RelExtractTest.doAllRuleTest(): pass");
        else
            System.out.println("RelExtractTest.doAllRuleTest(): fail");
        assertEquals(expectedOutput,result);
        System.out.println("-------------------");
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testRobertWearsAShirt() {

        String title = "RelExtractTest.testRobertWearsAShirt()";
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
        String rule = "dobj(?wear,?item-6), lemma(wear, ?wear), nsubj(?wear,?animal-2), sumo(?TYPEVAR1,?animal-2), " +
                "isSubclass(?TYPEVAR1,Animal), sumo(?TYPEVAR0,?item-6), isSubclass(?TYPEVAR0,WearableItem) ==> {(wears ?animal-2 ?item-6)}.";
        String expected = "(wears(Robert-1,shirt-4))";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimple() {

        String title = "RelExtractTest.testSimple()";
        String input = "dobj(wears-2,shirt-4), lemma(wear, wears-2)";
        String rule = "dobj(?wear,?item-6), lemma(wear, ?wear) ==> {(wears ?animal-2 ?item-6)}.";
        String expected = "(wears(?animal-2,shirt-4))";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testSimpleSent1() {

        //Interpreter.debug = true;
        //CNF.debug = true;
        //Procedures.debug = true;
        String title = "RelExtractTest.testSimpleSent1()";
        String input = "Robert wears a shirt";
        String expected = "(wears(Robert-1,shirt-4))";

        doOneResultTest(title,input, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Ignore
    @Test
    public void testSimpleHakawatiTheatre() {

        String title = "RelExtractTest.testSimpleHakawatiTheatre()";
        String input = "nmod:in(Hakawati_Theatre-6,JerusalemIsrael), sumo(Auditorium,Hakawati_Theatre-6), sumo(JerusalemIsrael,JerusalemIsrael)";
        String rule = "nmod:in(?object-7,?physical-2),  sumo(?TYPEVAR1,?object-7), " +
                "sumo(?TYPEVAR0,?physical-2), isChildOf(?TYPEVAR0,Physical), isChildOf(?TYPEVAR1,Object) ==> " +
                "{(located ?object-7 ?physical-2)}.";
        String expected = "(located Hakawati_Theatre-6 JerusalemIsrael)";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Ignore
    @Test
    public void testDepHakawatiTheatre() {

        String title = "RelExtractTest.testDepHakawatiTheatre()";
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

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     * An art exhibit at the Hakawati Theatre in Arab east Jerusalem
     * was a series of portraits of Palestinians killed in the rebellion
     */
    @Ignore
    @Test
    public void testDepAllRulesHakawatiTheatre() {

        String title = "RelExtractTest.testDepAllRulesHakawatiTheatre()";
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

        doAllRuleTest(title,input, expected);
    }

    /****************************************************************
     */
    @Ignore
    @Test
    public void testSentHakawatiTheatre() {

        String title = "RelExtractTest.testSentHakawatiTheatre()";
        String input = " An art exhibit at the Hakawati Theatre in Arab east Jerusalem " +
                "was a series of portraits of Palestinians killed in the rebellion .";
        String expected = "(located Hakawati_Theatre-6 Jerusalem-11)";

        doOneResultTest(title,input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testUnify10() {

        System.out.println();
        System.out.println("-------------------- RelExtractTest.testUnify10(): -------------------------------------");
        String rule4 = "isChildOf(Human,Object).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(bar,baz)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in RelExtractTest.testUnify10(): cnf " + cnf4);
        System.out.println("INFO in RelExtractTest.testUnify10(): cnf1 " + cnf5);
        String result = cnf4.unify(cnf5).toString();
        String expected = "{}";
        System.out.println("result: " + result);
        if (result.equals(expected))
            System.out.println("RelExtractTest.testUnify10(): pass");
        else
            System.out.println("RelExtractTest.testUnify10(): fail");
        assertEquals(expected,result);
        System.out.println("-------------------");
    }

    /****************************************************************
     */
    @Test
    public void testUnify11() {

        //CNF.debug = true;
        System.out.println();
        System.out.println("-------------------- RelExtractTest.testUnify11(): -------------------------------------");
        String rule4 = "isChildOf(?X,Object), a(?X,?Y).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(Human,baz)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in RelExtractTest.testUnify11(): cnf: " + cnf4);
        System.out.println("INFO in RelExtractTest.testUnify11(): cnf1: " + cnf5);
        Subst sub = cnf4.unify(cnf5);
        System.out.println("INFO in RelExtractTest.testUnify11(): sub: " + sub);
        String result = sub.toString();
        String expected = "{?X=Human, ?Y=baz}";
        System.out.println("result: " + result);
        if (result.equals(expected))
            System.out.println("RelExtractTest.testUnify11(): pass");
        else
            System.out.println("RelExtractTest.testUnify11(): fail");
        assertEquals(expected,result);
        System.out.println("-------------------");
    }

    /****************************************************************
     */
    @Test
    public void testDepMadonna() {

        String title = "RelExtractTest.testDepMadonna()";
        String input = "amod(Detroiter-5,True), amod(Detroiter-5,native-4), amod(peninsula-32,lower-31), amod(place-23,nice-22), " +
                "appos(Bay_City-16,Mich.-19), appos(Bay_City-16,place-23), case(Bay_City-16,from-15), case(Detroiter-5,As-1), " +
                "case(peninsula-32,of-27), case(state-29,'s-30), case(thumb-26,in-24), ccomp(everyone-11,Bay_City-16), " +
                "cop(Bay_City-16,is-14), det(Detroiter-5,a-2), det(place-23,a-21), det(state-29,the-28), det(thumb-26,the-25), " +
                "dobj(remind-10,everyone-11), lemma(Bay,Bay-16), lemma(City,City-17), lemma(Madonna,Madonna-13), " +
                "lemma(Mich.,Mich.-19), lemma(be,is-14), lemma(detroiter,Detroiter-5), lemma(everyone,everyone-11), " +
                "lemma(peninsula,peninsula-32), lemma(place,place-23), lemma(remind,remind-10), lemma(state,state-29), " +
                "lemma(thumb,thumb-26), lemma(want,want-8), mark(Bay_City-16,that-12), mark(remind-10,to-9), " +
                "names(Madonna-13,\"Madonna\"), nmod:as(want-8,Detroiter-5), nmod:in(place-23,thumb-26), " +
                "nmod:of(thumb-26,peninsula-32), nmod:poss(peninsula-32,state-29), nsubj(Bay_City-16,Madonna-13), " +
                "nsubj(want-8,I-7), number(SINGULAR,Bay-16), number(SINGULAR,City-17), number(SINGULAR,Detroiter-5), " +
                "number(SINGULAR,Madonna-13), number(SINGULAR,Mich.-19), number(SINGULAR,everyone-11), " +
                "number(SINGULAR,peninsula-32), number(SINGULAR,place-23), number(SINGULAR,state-29), number(SINGULAR,thumb-26), " +
                "root(ROOT-0,want-8), sumo(Attribute,is-14), sumo(Attribute,state-29), sumo(Bay,Bay_City-16), " +
                "sumo(City,Bay_City-16), sumo(Human,Madonna-13), sumo(Peninsula,peninsula-32), sumo(PsychologicalAttribute,want-8), " +
                "sumo(Region,place-23), sumo(Reminding,remind-10), sumo(StateOrProvince,Mich.-19), " +
                "sumo(SubjectiveAssessmentAttribute,native-4), sumo(SubjectiveStrongPositiveAttribute,nice-22), " +
                "sumo(Thumb,thumb-26), sumo(TruthValue,True), tense(PRESENT,is-14), tense(PRESENT,want-8), xcomp(want-8,remind-10)";

        String rule = "nsubj(?LOC, ?animal-8), cop(?LOC, ?COP), case(?LOC, from*), isSubclass(?TYPEVAR0,Animal), " +
                "sumo(?TYPEVAR0,?animal-8), isSubclass(?TYPEVAR1,Object), sumo(?TYPEVAR1,?LOC) ==> {(birthplace ?animal-8 ?LOC)}..";
        String expected = "(birthplace(Madonna-13,Bay_City-16))";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     */
    @Ignore
    @Test
    public void testDepWilson() {

        String title = "RelExtractTest.testDepWilson()";
        String input = "advcl(be_born-5,counted-25), advmod(be_born-5,True), advmod(counted-25,hardly-24), advmod(counted-25,manner), " +
                "amod(fame-15,political-14), amod(terms-31,political-30), case(Governor,as-16), case(NewJersey,of-18), " +
                "case(Southerner-28,as-26), case(Virginia,in-7), case(terms-31,in-29), cc(be_born-5,but-10), conj:but(be_born-5,Won), " +
                "det(Southerner-28,a-27), dobj(Won,fame-15), lemma(Jersey,Jersey-20), lemma(New,New-19), lemma(Virginia,Virginia-8), " +
                "lemma(Wilson,Wilson-4), lemma(Woodrow,Woodrow-3), lemma(be,was-5), lemma(bear,born-6), lemma(count,counted-25), " +
                "lemma(fame,fame-15), lemma(governor,governor-17), lemma(southerner,Southerner-28), lemma(term,terms-31), " +
                "lemma(win,won-12), nmod:as(Won,Governor), nmod:as(counted-25,Southerner-28), nmod:in(Southerner-28,terms-31), " +
                "nmod:in(be_born-5,Virginia), nmod:of(Governor,NewJersey), nmod:poss(fame-15,his-13), nsubj(Won,he-11), " +
                "nsubj(counted-25,he-23), nsubjpass(be_born-5,PresidentOfTheUnitedStates), number(PLURAL,terms-31), " +
                "number(SINGULAR,Jersey-20), number(SINGULAR,New-19), number(SINGULAR,Southerner-28), number(SINGULAR,Virginia-8), " +
                "number(SINGULAR,Wilson-4), number(SINGULAR,Woodrow-3), number(SINGULAR,fame-15), number(SINGULAR,governor-17), " +
                "root(ROOT-0,be_born-5), sumo(AmericanState,NewJersey), sumo(AmericanState,Virginia), sumo(AsymmetricRelation,manner), " +
                "sumo(Birth,be_born-5), sumo(ContestAttribute,Won), sumo(Counting,counted-25), sumo(IrreflexiveRelation,manner), " +
                "sumo(NounPhrase,terms-31), sumo(PoliticalOrganization,political-14), sumo(PoliticalOrganization,political-30), " +
                "sumo(Position,Governor), sumo(Position,PresidentOfTheUnitedStates), sumo(President,PresidentOfTheUnitedStates), " +
                "sumo(SubjectiveAssessmentAttribute,Southerner-28), sumo(SubjectiveAssessmentAttribute,fame-15), " +
                "sumo(SubjectiveAssessmentAttribute,hardly-24), sumo(TotalValuedRelation,manner), sumo(TruthValue,True), " +
                "sumo(property,manner), tense(PAST,counted-25), tense(PAST,was-5), tense(PAST,won-12)";

        // nmod:in(be_born-5,Virginia), nsubjpass(be_born-5,PresidentOfTheUnitedStates), sumo(AmericanState,Virginia)
        String rule = "nmod:in(be_born*,?LOC), nsubjpass(be_born*,?animal-8), isCELTclass(?animal-8,Person), isSubclass(?TYPEVAR1,Object), " +
                "sumo(?TYPEVAR1,?LOC) ==> {(birthplace ?animal-8 ?LOC)}.";
        String expected = "(birthplace(PresidentOfTheUnitedStates,Virginia))";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     */
    @Ignore
    @Test
    public void testDepWilson2() {

        //Interpreter.debug = true;
        //CNF.debug = true;
        //Procedures.debug = true;
        String title = "RelExtractTest.testDepWilson2()";
        String input = "nmod:in(be_born-5,Virginia), nsubjpass(be_born-5,PresidentOfTheUnitedStates), " +
                "sumo(President,PresidentOfTheUnitedStates), sumo(AmericanState,Virginia).";

        // nmod:in(be_born-5,Virginia), nsubjpass(be_born-5,PresidentOfTheUnitedStates), sumo(AmericanState,Virginia)
        String rule = "nmod:in(be_born*,?LOC), nsubjpass(be_born*,?animal-8), isCELTclass(?animal-8,Person), isSubclass(?TYPEVAR1,Object), " +
                "sumo(?TYPEVAR1,?LOC) ==> {(birthplace ?animal-8 ?LOC)}.";
        String expected = "(birthplace(PresidentOfTheUnitedStates,Virginia))";

        doRuleTest(title,input, rule, expected);
    }

    /****************************************************************
     */
    @Ignore
    @Test
    public void testSentWilson() {

        //RelExtract.debug = true;
        //CNF.debug = true;
        //Procedures.debug = true;
        String title = "RelExtractTest.testSentWilson()";
        String input = "True , Woodrow Wilson was born in Virginia , but he won his political " +
                "fame as governor of New Jersey , so he hardly counted as a Southerner in political terms .";

        // nmod:in(be_born-5,Virginia), nsubjpass(be_born-5,PresidentOfTheUnitedStates), sumo(AmericanState,Virginia)
        String expected = "(birthplace Woodrow_Wilson-3 Virginia-8)";

        doOneResultTest(title,input, expected);
    }
}
