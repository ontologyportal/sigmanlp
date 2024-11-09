package com.articulate.nlp.semRewrite.datesandnumber;

import com.articulate.nlp.semRewrite.*;
import com.articulate.nlp.IntegrationTestBase;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Tests Interpreter.interpretGenCNF( )
 * In: Natural language sentences.
 * Out: A CNF, aka the dependency parse.
 * TODO: possibly just make the numbers and units the required part of the output
 * to avoid a test dependency on things other than the numeric processing
 */
public class InterpretNumericsTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        Interpreter.inference = false;
        interpreter.initialize();

        IntegrationTestBase.resetAllForInference();
    }

    /****************************************************************
     */
    private void doTest(String input, Set<String> expectedOutput) {

        System.out.println("\n\nInterpretNumericsTest: sentence: " + input);
        CNF cnf = interpreter.interpretGenCNF(input);

        System.out.println("\n\nInterpretNumericsTest: actual: " + cnf.toSortedString());
        Set<String> actual = Sets.newHashSet(cnf.toListString());
        System.out.println("InterpretNumericsTest: expected: " + (new TreeSet(expectedOutput)));
        if (actual.equals(expectedOutput))
            System.out.println("InterpretNumericsTest: pass");
        else
            System.out.println("InterpretNumericsTest: fail");
        assertEquals(expectedOutput, actual);
    }

    /****************************************************************
     */
    @Test
    public void testNumerics1() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics1()-------------------------");
        String input = "John was killed on 8/15/2014 at 3:45 PM.";
        Set<String> expected = Sets.newHashSet("attribute(John-1,Male)", "auxpass(killed-3,was-2)",
            "day(time-1,15)", "hour(time-2,15)", "lemma(John,John-1)", "lemma(be,was-2)",
            "lemma(kill,killed-3)", "lemma(pm,PM-8)", "minute(time-2,45)", "month(time-1,august)",
            "names(John-1,\"John\")", "nsubjpass(killed-3,John-1)", "number(SINGULAR,John-1)",
            "number(SINGULAR,PM-8)", "punct(killed-3,.-9)", "root(ROOT-0,killed-3)",
            "sumo(Human,John-1)", "sumo(Killing,killed-3)", "tense(PAST,was-2)",
            "time(killed-3,time-1)", "time(killed-3,time-2)", "year(time-1,2014)");
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore   // must handle the "percent" qualifier
    @Test
    public void testNumerics2() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics2()-------------------------");
        String input = "As of 2012, sweet oranges accounted for approximately 70 percent of citrus production.";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore // to complicated
    @Test
    public void testNumerics3() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics3()-------------------------");
        String input = "The standard goal of sigma is to achieve precision to 4.5 standard deviations above or below the mean.";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testNumerics4() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics4()-------------------------");
        String input = "Taj Mahal attracts some 3000000 people a year for visit.";
        Set<String> expected = Sets.newHashSet("case(visit-10,for-9)", "lemma(Mahal,Mahal-2)",
                "lemma(Taj,Taj-1)", "lemma(attract,attracts-3)", "lemma(visit,visit-10)",
                "lemma(year,year-8)", "measure(Mahal-2,measure1)", "nmod:for(attracts-3,visit-10)",
                "nsubj(attracts-3,Taj_Mahal)", "number(SINGULAR,Mahal-2)", "number(SINGULAR,Taj-1)",
                "number(SINGULAR,visit-10)", "number(SINGULAR,year-8)", "punct(attracts-3,.-11)",
                "root(ROOT-0,attracts-3)", "sumo(Process,attracts-3)",
                "sumo(StationaryArtifact,Taj_Mahal)", "sumo(StationaryArtifact,Taj_Mahal)",
                "sumo(Translocation,visit-10)", "tense(PRESENT,attracts-3)",
                "unit(measure1,GroupOfPeople)", "value(measure1,3000000)", "valueToken(3000000,3000000-5)");
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testNumerics5() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics5()-------------------------");
        String input = "In 2014, Fiat owned 90% of Ferrari.";
        Set<String> expected = Sets.newHashSet("case(Ferrari-9,of-8)", "lemma(%,%-7)",
            "lemma(Ferrari,Ferrari-9)", "lemma(Fiat,Fiat-4)", "measure(Fiat-4,measure1)",
            "number(SINGULAR,%-7)", "number(SINGULAR,Ferrari-9)", "number(SINGULAR,Fiat-4)",
            "sumo(FiatAutomobile,Fiat-4)", "sumo(FerrariAutomobile,Ferrari-9)", "time(owned-5,time-1)", "unit(measure1,%)",
            "value(measure1,90)", "valueToken(90,90-6)", "year(time-1,2014)");
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testNumerics6() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics6()-------------------------");
        String input = "John killed Mary on 31 March and also in July 1995 by travelling back in time.";
        Set<String> expected = Sets.newHashSet("advmod(travelling-13,back-14)",
                "attribute(John-1,Male)", "attribute(Mary-3,Female)", "cc(killed-2,and-7)",
                "day(time-1,31)", "dobj(killed-2,Mary-3)", "lemma(John,John-1)",
                "lemma(March,March-6)", "lemma(Mary,Mary-3)", "lemma(kill,killed-2)",
                "lemma(time,time-16)", "lemma(travel,travelling-13)", "mark(travelling-13,by-12)",
                "month(time-1,March)", "month(time-2,July)", "names(John-1,\"John\")",
                "names(Mary-3,\"Mary\")", "nmod:in(travelling-13,in_time)",
                "nsubj(killed-2,John-1)", "number(SINGULAR,John-1)", "number(SINGULAR,March-6)",
                "number(SINGULAR,Mary-3)", "number(SINGULAR,time-16)", "punct(killed-2,.-17)",
                "root(ROOT-0,killed-2)", "sumo(BackFn,back-14)", "sumo(Human,John-1)",
                "sumo(Human,Mary-3)", "sumo(Killing,killed-2)", "sumo(Transportation,travelling-13)",
                "sumo(and,also-8)", "tense(PAST,killed-2)", "time(killed-2,time-1)",
                "time(killed-2,time-2)", "year(time-2,1995)");
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore   // need to implement handling of currency
    @Test
    public void testNumerics7() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics7()-------------------------");
        String input = "The $200,000 and $60,000 in the first and fourth charges were not lost";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore  // too complicated
    @Test
    public void testNumerics8() {

        System.out.println("-------------------------InterpretNumericsTest.testNumerics8()-------------------------");
        String input = "Of the 11 counts, the applicant was convicted of 9 counts (namely, the 3rd charge to the 11th charge).";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore   // needs special handling of currency
    @Test
    public void testNumerics9() {

        interpreter.debug = true;
        System.out.println("-------------------------InterpretNumericsTest.testNumerics9()-------------------------");
        String input = "The total amount of monies involved in Charges 1 and 2 was approximately HK$2.7 million.";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Test
    public void testNumerics10() {

        interpreter.debug = true;
        System.out.println("-------------------------InterpretNumericsTest.testNumerics10()-------------------------");
        String input = "Mary weighs 120lbs.";
        Set<String> expected = Sets.newHashSet("number(PLURAL,lbs-4)", "names(Mary-1,\"Mary\")",
                "attribute(Mary-1,Female)", "measure(Mary-1,measure1)", "sumo(Human,Mary-1)",
                "lemma(Mary,Mary-1)", "sumo(PoundMass,lbs-4)", "lemma(lb,lbs-4)",
                "unit(measure1,PoundMass)", "valueToken(120,120-3)", "value(measure1,120)",
                "number(SINGULAR,Mary-1)", "nummod(lbs-4,120-3)");
        doTest(input, expected);
    }

    /****************************************************************
     */
    @Ignore // not handling symbols for units
    @Test
    public void testNumerics11() {

        interpreter.debug = true;
        System.out.println("-------------------------InterpretNumericsTest.testNumerics11()-------------------------");
        String input = "John is 6' tall.";
        Set<String> expected = Sets.newHashSet();
        doTest(input, expected);
    }
}