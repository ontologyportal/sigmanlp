package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KBmanager;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
public class CNFIntegTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    /****************************************************************
     */
    @Before
    public void setUp() throws IOException {

        KBmanager.getMgr().initializeOnce();
        interpreter = new Interpreter();
        interpreter.inference = false;
        interpreter.initialize();

        IntegrationTestBase.resetAllForInference();
    }

    /****************************************************************
     */
    @Test
    public void testUnify5() {

        CNF.debug = true;
        System.out.println("INFO in CNFIntegTest.testUnify5(): -------------------------------------");
        String rule4 = "prep_about(?X,?Y), sumo(?C,?X), isSubclass(?C,Process).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "root(ROOT-0,be-6), det(decision-2,the-1), nsubj(be-6,decision-2), " +
                "det(matter-5,the-4), prep_about(decision-2,matter-5), det(committee-10,the-9), " +
                "advmod(be-6,up-7), prep_to(up-7,committee-10), sumo(StateOfMind,up-7), " +
                "sumo(Deciding,decision-2), sumo(Commission,committee-10), " +
                "sumo(Proposition,matter-5), number(SINGULAR,decision-2), " +
                "number(SINGULAR,matter-5), tense(PRESENT,be-6), number(SINGULAR,committee-10)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNFIntegTest.testUnify5(): cnf " + cnf4);
        System.out.println("INFO in CNFIntegTest.testUnify5(): cnf1 " + cnf5);
        Subst bindings = cnf4.unify(cnf5);
        String bindStr = "null";
        if (bindings != null)
            bindStr = bindings.toString();
        assertEquals("{?X=decision-2, ?Y=matter-5, ?C=Deciding}",bindStr);
    }
}
