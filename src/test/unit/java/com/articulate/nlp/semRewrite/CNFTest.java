package com.articulate.nlp.semRewrite;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

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
        String rule = "nmod:to(be_born*,?H2), sumo(Human,?H), sumo(Human,?H2), nsubjpass(be_born*,?H) ==> (parent(?H,?H2)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("nsubjpass(be_born-2,John-1), attribute(John-1,Male),   " +
                "sumo(Human,John-1), sumo(Human,Mary-5), nmod:to(be_born-2,Mary-5), case(Mary-5,to-4), root(ROOT-0,be_born-2), sumo(Birth,be_born-2).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNFTest.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify(): cnf1 " + cnf1);
        HashMap<String,String> bindings = cnf1.unify(cnf);
        System.out.println("INFO in CNFTest.testUnify(): bindings: " + bindings);
        System.out.println("INFO in CNFTest.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNFTest.testUnify(): expecting: parent(John-1,Mary-5).");
        assertEquals("(parent(John-1,Mary-5))",r.rhs.applyBindings(bindings).toString());
    }

    /****************************************************************
     */
    @Test
    public void testUnify2() {

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
        assertEquals("sense(212345678,Foo)",cnf.toString());
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
        assertEquals(null,cnf1.unify(cnf));
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
        assertEquals(null,cnf1.unify(cnf));
    }

    /****************************************************************
     */
    @Test
    public void testUnify5() {

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
        assertEquals("nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1)",cnf.toString());
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
        assertEquals(null,cnf1.unify(cnf));
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
        assertEquals("{?V=moves-2}",cnf12.unify(cnf2).toString());
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
        assertEquals("{?T=time-1, ?D=5, ?V=was-3}",cnf4.unify(cnf5).toString());
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
        assertEquals("{?T=time-1, ?D=5, ?V=was-3, ?M=July}",cnf4.unify(cnf5).toString());
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
        assertEquals("{?T=time-1, ?D=5, ?V=was-3, ?Y=1980, ?M=July}",cnf4.unify(cnf5).toString());
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
        System.out.println("result: " + cnf4.unify(cnf5).toString());
        assertEquals("{?X=nnn, ?Y=dontcare}",cnf4.unify(cnf5).toString());
    }
}
