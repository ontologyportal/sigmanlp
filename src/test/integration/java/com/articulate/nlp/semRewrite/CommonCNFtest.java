package com.articulate.nlp.semRewrite;
/*
Copyright 2014-2015 IPsoft

Author: Peigen You Peigen.You@ipsoft.com

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

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.KB;
import com.articulate.nlp.IntegrationTestBase;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.Before;

import java.io.IOException;
import java.util.*;
import org.junit.After;
import org.junit.Ignore;

public class CommonCNFtest extends IntegrationTestBase {

    private Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUp() throws IOException {

        interpreter = new Interpreter();
        Interpreter.inference = false;
        interpreter.initialize();
    }

    /****************************************************************
     */
    @After
    public void tearDown() {

        interpreter = null;
    }

    /***********************************************************
     * Find the most specific unifier for two strings formatted as
     * dependency parses
     */
    public void testCommonDepForms(String s1, String s2, String expectStr) {

        Collection<CNF> cnfs = new ArrayList<>();
        CNF c1 = CNF.valueOf(s1);
        CommonCNFUtil.tokensToVars(c1);
        CNF c2 = CNF.valueOf(s2);
        CommonCNFUtil.tokensToVars(c2);
        cnfs.add(c1);
        cnfs.add(c2);

        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.testCommonDepForms(): s1: " + s1);
        System.out.println("CommonCNFUtil.testCommonDepForms(): s2: " + s2);
        CNF actual = ccu.findOneCommonCNF(cnfs);
        CNF expected = new CNF(expectStr);
        String expResStr = expected.toSortedLiterals().toString();
        String actResStr = actual.toSortedLiterals().toString();
        System.out.println("CommonCNFUtil.testCommonDepForms(): result: " +
                actResStr);
        System.out.println("CommonCNFUtil.testCommonDepForms(): expected: " +
                expResStr);
        if (actResStr.equals(expResStr))
            System.out.println("pass");
        else
            System.err.println("fail");
        assertEquals(expResStr,actResStr);
        System.out.println("---------------------------------\n");
    }

    /***********************************************************
     * Find the most specific unifier for two strings formatted as
     * dependency parses
     */
    public void testMostSpecificForms(String s1, String s2, String expectStr) {

        Collection<CNF> cnfs = new ArrayList<>();
        CNF c1 = CNF.valueOf(s1);
        CommonCNFUtil.tokensToVars(c1);
        CNF c2 = CNF.valueOf(s2);
        CommonCNFUtil.tokensToVars(c2);
        cnfs.add(c1);
        cnfs.add(c2);

        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.testMostSpecificForms(): s1: " + s1);
        System.out.println("CommonCNFUtil.testMostSpecificForms(): s2: " + s2);
        CNF actual = ccu.mostSpecificForm(cnfs);
        CNF expected = new CNF(expectStr);
        String expResStr = expected.toSortedLiterals().toString();
        String actResStr = actual.toSortedLiterals().toString();
        System.out.println("CommonCNFUtil.testMostSpecificForms(): result: " +
                actResStr);
        System.out.println("CommonCNFUtil.testMostSpecificForms(): expected: " +
                expResStr);
        if (actResStr.equals(expResStr))
            System.out.println("pass");
        else
            System.err.println("fail");
        assertEquals(expResStr,actResStr);
        System.out.println("---------------------------------\n");
    }

    /***********************************************************
     * Find the most specific unifier for two strings formatted as
     * dependency parses
     */
    public void testCommonSentForms(String s1, String s2, String expected) {

        Map<Integer, String> map = new HashMap<>();
        map.put(0, s1);
        map.put(1, s2);
        Map<Integer, CNF> res = CommonCNFUtil.generateCNFForStringSet(map);
        if (res == null || res.keySet().isEmpty()) {
            System.err.println("fail");
            return;
        }
        testCommonDepForms(res.get(0).toString(), res.get(1).toString(), expected);
    }

    /***********************************************************
     */
    @Test
    public void testMostSpecificForm1c() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testMostSpecificForm1c()");
        String s2 = "sumo(Physical,pushes-2), det(wagon-4,the-3), sumo(Physical,Susan-1), " +
                "nsubj(pushes-2,Susan-1), sumo(Physical,wagon-4), root(ROOT-0,pushes-2), " +
                "names(Susan-1,\"John\"), attribute(Susan-1,Male), dobj(pushes-2,wagon-4)";
        String s3 = "root(ROOT-0,pushes-2), det(wagon-4,the-3), dobj(pushes-2,wagon-4), " +
                "sumo(Physical,Susan-1), sumo(Motion,pushes-2), nsubj(pushes-2,Susan-1), " +
                "sumo(Physical,wagon-4), attribute(Susan-1,Male), names(Susan-1,\"John\")";
        testMostSpecificForms(s2,s3,"root(?ROOT-0,?pushes-2), det(?wagon-4,?the-3), " +
                "dobj(?pushes-2,?wagon-4), sumo(Physical,?Susan-1), " +
                "sumo(Motion,?pushes-2), nsubj(?pushes-2,?Susan-1), " +
                "sumo(Physical,?wagon-4), attribute(?Susan-1,Male), " +
                "names(?Susan-1,\"John\")");
    }

    /***********************************************************
     */
    @Test
    public void testMostSpecificForm1a() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testMostSpecificForm1a()");
        String s1 = "names(Susan-1,\"John\"), nsubj(pushes-2,Susan-1), sumo(Physical,Susan-1), " +
                "sumo(UnpoweredVehicle,wagon-4), root(ROOT-0,pushes-2), attribute(Susan-1,Male), " +
                "dobj(pushes-2,wagon-4), det(wagon-4,the-3), sumo(Physical,pushes-2)";
        String s2 = "sumo(Physical,pushes-2), det(wagon-4,the-3), sumo(Physical,Susan-1), " +
                "nsubj(pushes-2,Susan-1), sumo(Physical,wagon-4), root(ROOT-0,pushes-2), " +
                "names(Susan-1,\"John\"), attribute(Susan-1,Male), dobj(pushes-2,wagon-4)";
        testMostSpecificForms(s1, s2, "names(?Susan-1,\"John\"), " +
                "nsubj(?pushes-2,?Susan-1), sumo(Physical,?Susan-1), " +
                "sumo(Physical,?wagon-4), root(?ROOT-0,?pushes-2), " +
                "attribute(?Susan-1,Male), dobj(?pushes-2,?wagon-4), " +
                "det(?wagon-4,?the-3), sumo(Physical,?pushes-2)");
    }

    /***********************************************************
     */
    @Test
    public void testMostSpecificForm1b() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testMostSpecificForm1b()");
        String s1 = "names(Susan-1,\"John\"), nsubj(pushes-2,Susan-1), sumo(Physical,Susan-1), " +
                "sumo(UnpoweredVehicle,wagon-4), root(ROOT-0,pushes-2), attribute(Susan-1,Male), " +
                "dobj(pushes-2,wagon-4), det(wagon-4,the-3), sumo(Physical,pushes-2)";
        String s3 = "root(ROOT-0,pushes-2), det(wagon-4,the-3), dobj(pushes-2,wagon-4), " +
                "sumo(Physical,Susan-1), sumo(Motion,pushes-2), nsubj(pushes-2,Susan-1), " +
                "sumo(Physical,wagon-4), attribute(Susan-1,Male), names(Susan-1,\"John\")";
        testMostSpecificForms(s1, s3, "names(?Susan-1,\"John\"), " +
                "nsubj(?pushes-2,?Susan-1), sumo(Physical,?Susan-1), " +
                "sumo(Physical,?wagon-4), root(?ROOT-0,?pushes-2), " +
                "attribute(?Susan-1,Male), dobj(?pushes-2,?wagon-4), " +
                "det(?wagon-4,?the-3), sumo(Motion,?pushes-2)");
    }

    /***********************************************************
     */
    @Test
    public void testMostSpecificForm2() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testMostSpecificForm2()");
        String s1 = "dobj(pushes-2,wagon-4), sumo(UnpoweredVehicle,wagon-4), names(Susan-1,\"John\"), " +
                "attribute(Susan-1,Male), root(ROOT-0,pushes-2), sumo(Physical,pushes-2), " +
                "sumo(Physical,Susan-1), nsubj(pushes-2,Susan-1), det(wagon-4,the-3)";
        String s2 = "root(ROOT-0,pushes-2), sumo(Object,wagon-4), det(wagon-4,the-3), " +
                "names(Susan-1,\"John\"), attribute(Susan-1,Male), sumo(Motion,pushes-2), " +
                "nsubj(pushes-2,Susan-1), dobj(pushes-2,wagon-4), sumo(Object,Susan-1)";
        System.out.println("CommonCNFUtil.testMostSpecificForm2(): ");
        testMostSpecificForms(s1,s2,"dobj(?pushes-2,?wagon-4), " +
                "sumo(Object,?wagon-4), names(?Susan-1,\"John\"), " +
                "attribute(?Susan-1,Male), root(?ROOT-0,?pushes-2), " +
                "sumo(Motion,?pushes-2), sumo(Object,?Susan-1), " +
                "nsubj(?pushes-2,?Susan-1), det(?wagon-4,?the-3");
    }

    /****************************************************************
     */
    @Test
    @Ignore // TODO: Fails
    public void testCase1() throws IOException {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testCase1()");
        KB kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFtest KB term size:" + kb.terms.size());
        testCommonSentForms("Mary drives the car to the store",
                "John flies a plane to the airport", "sumo(Object,?VAR17), " +
                        "det(?VAR18,?VAR19), det(?VAR17,?VAR21), " +
                        "nmod:to(?VAR15,?VAR18), dobj(?VAR15,?VAR17), " +
                        "root(?VAR14,?VAR15), sumo(Object,?VAR16), " +
                        "sumo(StationaryArtifact,?VAR18), " +
                        "case(?VAR18,?VAR20), nsubj(?VAR15,?VAR16), " +
                        "sumo(Process,?VAR15)");
    }

    /***********************************************************
     */
    @Test
    public void testJohnSusan() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testJohnSusan()");
        String s1 = "names(John-1,\"John\")";
        String s2 = "names(Susan-1,\"Susan\")";
        System.out.println("CommonCNFUtil.test(): ");
        testCommonDepForms(s1, s2, "");
    }

    /***********************************************************
     */
    @Test
    @Ignore // TODO: Fails
    public void testWagonCartSimple() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testWagonCartSimple()");
        String s1 = "sumo(Wagon,cart-4), sumo(Kicking,kicks-2), nsubj(kicks-2,John-1), " +
                "dobj(kicks-2,cart-4)";
        String s2 = "sumo(Pushing,pushes-2), " +
                "sumo(Trailer,wagon-4), dobj(pushes-2,wagon-4), nsubj(pushes-2,Susan-1)";
        System.out.println("CommonCNFUtil.test(): ");
        testCommonDepForms(s1, s2, "nsubj(?VAR3,?VAR5), sumo(Motion,?VAR3), " +
                "dobj(?VAR3,?VAR4), sumo(Device,?VAR4");
    }

    /***********************************************************
     */
    @Test
    @Ignore // TODO: Fails
    public void testWagonCartComplex() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testWagonCartComplex()");
        String s1 = "root(ROOT-0,kicks-2), det(cart-4,the-3), names(John-1,\"John\"), " +
                "sumo(Wagon,cart-4), sumo(Kicking,kicks-2), nsubj(kicks-2,John-1), " +
                "dobj(kicks-2,cart-4), attribute(John-1,Male), sumo(Human,John-1), " +
                "number(SINGULAR,John-1), lemma(John,John-1), tense(PRESENT,kicks-2), " +
                "lemma(kick,kicks-2), number(SINGULAR,cart-4), lemma(cart,cart-4)";
        String s2 = "root(ROOT-0,pushes-2), det(wagon-4,the-3), names(Susan-1,\"Susan\"), " +
                "attribute(Susan-1,Female), sumo(Pushing,pushes-2), sumo(Human,Susan-1), " +
                "sumo(Trailer,wagon-4), dobj(pushes-2,wagon-4), nsubj(pushes-2,Susan-1), " +
                "number(SINGULAR,Susan-1), lemma(Susan,Susan-1), tense(PRESENT,pushes-2), " +
                "lemma(push,pushes-2), number(SINGULAR,wagon-4), lemma(wagon,wagon-4)";
        System.out.println("CommonCNFUtil.test(): ");
        testCommonDepForms(s1, s2, "det(?VAR41,?VAR42), dobj(?VAR40,?VAR41), " +
                "root(?VAR39,?VAR40), nsubj(?VAR40,?VAR43), sumo(Motion,?VAR40), " +
                "sumo(Object,?VAR43), sumo(Object,?VAR41");
    }

    /***********************************************************
     */
    @Test
    @Ignore // TODO: Fails
    public void testWagonCartSent() {

        System.out.println("---------------------------------\n");
        System.out.println("\nCommonCNFtest.testWagonCartSent()");
        String s1 = "John kicks the cart";
        String s2 = "Susan pushes the wagon";
        testCommonSentForms(s1,s2,"root(ROOT-0,?B)\n" +
                "det(?D,?C)\n" +
                "names(?A,?E)\n" +
                "attribute(?A,SexAttribute)\n" +
                "sumo(Motion,?B)\n" +
                "sumo(Human,?A)\n" +
                "dobj(?B,?D)\n" +
                "nsubj(?B,?A)\n" +
                "sumo(Wagon,?D)");
    }
}
