package com.articulate.nlp.semRewrite;

import com.articulate.sigma.Formula;
import com.articulate.sigma.FormulaUtil;
import com.articulate.sigma.KBmanager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
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
public class CNFTest {

    /****************************************************************
     */
    @Test
    public void testMerge() {

        CNF cnf1 = new CNF("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        CNF cnf2 = new CNF("foo(BodyMotion,Bob-2), bar(Human,John-1).");
        cnf1.merge(cnf2);
        System.out.println("testMerge() result: " + cnf1);
        assertEquals(cnf1.toString(),"sumo(BodyMotion,Bob-2), sumo(Human,John-1), foo(BodyMotion,Bob-2), bar(Human,John-1)");
    }

    /****************************************************************
     */
    @Test
    public void testParseSimple() {

        Lexer lex = new Lexer("num(?O,?N), +sumo(?C,?O).");
        CNF cnf1 = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testParseSimple(): " + cnf1);
        lex = new Lexer("sumo(Vehicle,?car-6), nmod:in(?put-2,?car-6)");
        cnf1 = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testParseSimple(): " + cnf1);
    }

    /****************************************************************
     */
    @Test
    public void testEquality() {

        CNF cnf1 = new CNF("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        CNF cnf2 = new CNF("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        assertTrue(cnf1.equals(cnf2));
    }

    /****************************************************************
     */
    @Test
    public void testContains() {

        CNF cnf1 = new CNF("sumo(BodyMotion,Bob-2).");
        CNF cnf2 = new CNF("sumo(BodyMotion,Bob-2).");
        ArrayList<CNF> al = new ArrayList<CNF>();
        al.add(cnf1);
        if (!al.contains(cnf2))
            al.add(cnf2);
        assertTrue(al.size() == 1);
    }

    /****************************************************************
     */
    @Test
    public void testUnify() {

        System.out.println("INFO in CNFTest.testUnify(): -------------------------------------");
        String rule = "nmod:to(be_born*,?H2), sumo(Human,?H1), sumo(Human,?H2), nsubjpass(be_born*,?H1) ==> (parent(?H1,?H2)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("nsubjpass(be_born-2,John-1), attribute(John-1,Male),   " +
                "sumo(Human,John-1), sumo(Human,Mary-5), nmod:to(be_born-2,Mary-5), " +
                "case(Mary-5,to-4), root(ROOT-0,be_born-2), sumo(Birth,be_born-2).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify(): cnf1 " + cnf1);
        Subst bindings = cnf1.unify(cnf);
        System.out.println("INFO in CNFTest.testUnify(): bindings: " + bindings);
        System.out.println("INFO in CNFTest.testUnify(): cnf " + cnf);

        String result = r.rhs.applyBindings(bindings).toString();
        String expected = "(parent(John-1,Mary-5))";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify(): success");

        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify2() {

        //Clause.bindSource = false;
        System.out.println("INFO in CNFTest.testUnify2(): -------------------------------------");
        String rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("sense(212345678,Foo).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify2(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify2(): cnf1 " + cnf1);
        System.out.println("INFO in CNFTest.testUnify2(): bindings: " + cnf1.unify(cnf));
        System.out.println("INFO in CNFTest.testUnify2(): cnf " + cnf);
        String result = cnf.toString();
        String expected = "Xsense(212345678,Foo)";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify2(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify2(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify3() {

        System.out.println("INFO in CNFTest.testUnify3(): -------------------------------------");
        String rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("sense(2123,Foo).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify3(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify3(): cnf1 " + cnf1);
        Subst result = cnf1.unify(cnf);
        String expected = null;
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (result != null)
            System.out.println("INFO in CNFTest.testUnify3(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify3(): success");
        assertEquals(null,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify4() {

        System.out.println("INFO in CNFTest.testUnify4(): -------------------------------------");
        String rule2 = "det(?X,What*), sumo(?O,?X).";
        Lexer lex = new Lexer(rule2);
        CNF cnf1 = CNF.parseSimple(lex);
        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify4(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify4(): cnf1 " + cnf1);
        Subst result = cnf1.unify(cnf);
        String expected = null;
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (result != null)
            System.out.println("INFO in CNFTest.testUnify4(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify4(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify5() {

        //Clause.bindSource = false;
        System.out.println("INFO in CNFTest.testUnify5(): -------------------------------------");
        String rule2 = "nsubj(?X,?Y), sumo(?O,?X).";
        Lexer lex = new Lexer(rule2);
        CNF cnf1 = CNF.parseSimple(lex);
        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify5(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify5(): cnf1 " + cnf1);
        System.out.println("INFO in CNFTest.testUnify5(): bindings: " + cnf1.unify(cnf));
        String result = cnf.toString();
        String expected = "Xnsubj(drives-2,John-1), root(ROOT-0,drives-2), Xsumo(Transportation,drives-2), sumo(Human,John-1)";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify5(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify5(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify6() {

        System.out.println("INFO in CNFTest.testUnify6(): -------------------------------------");
        String rule = "nsubj(?V,?Who*).";
        Lexer lex = new Lexer(rule);
        CNF cnf1 = CNF.parseSimple(lex);
        String cnfstr = "nsubj(kicks-2,John-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex = new Lexer(cnfstr);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify6(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify6(): cnf1 " + cnf1);
        Subst result = cnf1.unify(cnf);
        String expected = null;
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (result != null)
            System.out.println("INFO in CNFTest.testUnify6(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify6(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify7() {

        System.out.println("INFO in CNFTest.testUnify7(): -------------------------------------");
        String rule3 = "nsubj(?V,Who*).";
        Lexer lex2 = new Lexer(rule3);
        CNF cnf12 = CNF.parseSimple(lex2);
        String cnfstr2 = "nsubj(moves-2,Who-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex2 = new Lexer(cnfstr2);
        CNF cnf2 = CNF.parseSimple(lex2);
        System.out.println("INFO in CNFTest.testUnify7(): cnf " + cnf2);
        System.out.println("INFO in CNFTest.testUnify7(): cnf1 " + cnf12);
        String result = cnf12.unify(cnf2).toString();
        String expected = "{?V=moves-2}";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify7(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify7(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify8smallest() {

        System.out.println("INFO in CNFTest.testUnify8smallest(): -------------------------------------");
        String rule4 = "StartTime(?V,?T), day(?T,?D).";
        Lexer lex4 = new Lexer(rule4);
        CNF cnf4 = CNF.parseSimple(lex4);
        //String cnfstr5 = "root(ROOT-0,be-3), det(celebration-2,the-1), nsubj(be-3,celebration-2), prep_from(be-3,July-5), num(July-5,5-6), num(July-5,1980-8), prep_to(be-3,August-10), num(August-10,4-11), num(August-10,1980-13), sumo(SocialParty,celebration-2), number(SINGULAR,celebration-2), tense(PAST,be-3), number(SINGULAR,July-5), number(SINGULAR,August-10), day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        String cnfstr5 = "day(time-1,5), StartTime(was-3,time-1), day(time-2,4)";
        //String cnfstr5 = "StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), day(time-1,5)";
        Lexer lex5 = new Lexer(cnfstr5);
        CNF cnf5 = CNF.parseSimple(lex5);
        System.out.println("INFO in CNFTest.testUnify8smallest(): cnf " + cnf4);
        System.out.println("INFO in CNFTest.testUnify8smallest(): cnf1 " + cnf5);
        String result = cnf4.unify(cnf5).toString();
        String expected = "{?T=time-1, ?D=5, ?V=was-3}";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify8smallest(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify8smallest(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify8small() {

        System.out.println("INFO in CNFTest.testUnify8small(): -------------------------------------");
        String rule4 = "StartTime(?V,?T), day(?T,?D), month(?T,?M).";
        Lexer lex4 = new Lexer(rule4);
        CNF cnf4 = CNF.parseSimple(lex4);
        //String cnfstr5 = "root(ROOT-0,be-3), det(celebration-2,the-1), nsubj(be-3,celebration-2), prep_from(be-3,July-5), num(July-5,5-6), num(July-5,1980-8), prep_to(be-3,August-10), num(August-10,4-11), num(August-10,1980-13), sumo(SocialParty,celebration-2), number(SINGULAR,celebration-2), tense(PAST,be-3), number(SINGULAR,July-5), number(SINGULAR,August-10), day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        String cnfstr5 = "day(time-2,4), StartTime(was-3,time-1), month(time-1,July), month(time-2,August), day(time-1,5)";
        //String cnfstr5 = "StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), day(time-1,5)";
        Lexer lex5 = new Lexer(cnfstr5);
        CNF cnf5 = CNF.parseSimple(lex5);
        System.out.println("INFO in CNFTest.testUnify8small(): cnf " + cnf4);
        System.out.println("INFO in CNFTest.testUnify8small(): cnf1 " + cnf5);
        String result = cnf4.unify(cnf5).toString();
        String expected = "{?T=time-1, ?D=5, ?M=July, ?V=was-3}";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify8small(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify8small(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify8() {

        System.out.println("INFO in CNFTest.testUnify8(): -------------------------------------");
        String rule4 = "StartTime(?V,?T), day(?T,?D), month(?T,?M), year(?T,?Y).";
        Lexer lex4 = new Lexer(rule4);
        CNF cnf4 = CNF.parseSimple(lex4);
        //String cnfstr5 = "root(ROOT-0,be-3), det(celebration-2,the-1), nsubj(be-3,celebration-2), prep_from(be-3,July-5), num(July-5,5-6), num(July-5,1980-8), prep_to(be-3,August-10), num(August-10,4-11), num(August-10,1980-13), sumo(SocialParty,celebration-2), number(SINGULAR,celebration-2), tense(PAST,be-3), number(SINGULAR,July-5), number(SINGULAR,August-10), day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        String cnfstr5 = "day(time-2,4), StartTime(was-3,time-1), month(time-1,July), " +
                "year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        //String cnfstr5 = "StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), day(time-1,5)";
        Lexer lex5 = new Lexer(cnfstr5);
        CNF cnf5 = CNF.parseSimple(lex5);
        System.out.println("INFO in CNFTest.testUnify8(): cnf " + cnf4);
        System.out.println("INFO in CNFTest.testUnify8(): cnf1 " + cnf5);
        String result = cnf4.unify(cnf5).toString();
        String expected = "{?Y=1980, ?T=time-1, ?D=5, ?M=July, ?V=was-3}";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify8(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify8(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify9() {

        System.out.println("INFO in CNFTest.testUnify9(): -------------------------------------");
        String rule4 = "a(?X,?Y), b(?X,foo).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(bar,baz), b(nnn,foo), a(nnn,dontcare)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNFTest.testUnify9(): cnf " + cnf4);
        System.out.println("INFO in CNFTest.testUnify9(): cnf1 " + cnf5);
        String result = cnf4.unify(cnf5).toString();
        String expected = "{?X=nnn, ?Y=dontcare}";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testUnify9(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify9(): success");
        assertEquals(expected,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify10() {

        System.out.println("INFO in CNFTest.testUnify10(): -------------------------------------");
        String rule4 = "sumo(?T,?X).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "nsubj(kicks-2,John-1), det(cart-4,the-3), sumo(Human,John-1), " +
            "sumo(Kicking,kicks-2), sumo(Wagon,cart-4), number(SINGULAR,John-1), lemma(John,John-1), " +
            "tense(PRESENT,kicks-2), lemma(kick,kicks-2), number(SINGULAR,cart-4), lemma(cart,cart-4), " +
            "patient(kicks-2,cart-4), attribute(John-1,Male), names(John-1,\"John\")";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNFTest.testUnify10(): cnf " + cnf4);
        System.out.println("INFO in CNFTest.testUnify10(): cnf1 " + cnf5);
        HashSet<Unification> res = cnf4.unifyNew(cnf5);
        String result = res.toString();
        String expected = "{?T=Human, ?X=John-1}";
        System.out.println("result: " + res);
        System.out.println("expected: " + expected);
        System.out.println("bound content: " + cnf5);
        if (result.indexOf(expected) == -1)
            System.out.println("INFO in CNFTest.testUnify10(): fail");
        else
            System.out.println("INFO in CNFTest.testUnify10(): success");
        assertTrue(result.indexOf(expected) != -1);
    }

    /** ***************************************************************
     */
    public static void testSort() {

        String input = "dobj(ate-3, chicken-4), sumo(Eating,ate-3), sumo(Man,George-1), nmod:in(ate-3, December-6), " +
                "compound(Washington-2, George-1), root(ROOT-0, ate-3), sumo(ChickenMeat,chicken-4).";
        CNF cnf4 = new CNF(input);
        String result = cnf4.toSortedString();
        String expected = "compound(Washington-2, George-1), dobj(ate-3, chicken-4), " +
                "nmod:in(ate-3, December-6), root(ROOT-0, ate-3), sumo(Eating,ate-3), sumo(Man,George-1), " +
                " sumo(ChickenMeat,chicken-4).";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testSort(): fail");
        else
            System.out.println("INFO in CNFTest.testSort(): success");
        assertEquals(expected,result);
    }

    /** ***************************************************************
     */
    @Test
    public void testToProlog() {

        String stmt = "(birthplace ?animal ?LOC)";
        Formula f = new Formula(stmt);
        String p = FormulaUtil.toProlog(f);
        CNF cnf = new CNF(p);
        String result = cnf.toString();
        String expected = "birthplace(?animal,?LOC)";
        System.out.println("result: " + result);
        System.out.println("expected: " + expected);
        if (!result.equals(expected))
            System.out.println("INFO in CNFTest.testToProlog(): fail");
        else
            System.out.println("INFO in CNFTest.testToProlog(): success");
        assertEquals(expected, result);
    }
}
