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
public class CNFNewTest {

    /****************************************************************
     */
    @Test
    public void testUnify1() {

        CNF.debug = true;
        System.out.println("INFO in CNF.testUnify1(): -------------------------------------");
        String rule4 = "a(?X,?Y), b(?X,foo).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(bar,baz), b(nnn,foo), a(nnn,dontcare)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf4);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf5);
        Subst bindings = cnf4.unify(cnf5);
        String bindStr = "null";
        if (bindings != null)
            bindStr = bindings.toString();
        assertEquals("{?X=nnn, ?Y=dontcare}",bindStr);
    }

    /****************************************************************
     */
    @Test
    public void testUnify2() {

        CNF.debug = true;
        System.out.println("INFO in CNF.testUnify2(): -------------------------------------");
        String rule4 = "b(?X,foo), a(?X,?Y).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(bar,baz), b(nnn,foo), a(nnn,dontcare)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf4);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf5);
        Subst bindings = cnf4.unify(cnf5);
        String bindStr = "null";
        if (bindings != null)
            bindStr = bindings.toString();
        assertEquals("{?X=nnn, ?Y=dontcare}",bindStr);
    }

    /****************************************************************
     */
    @Test
    public void testUnify3() {

        CNF.debug = true;
        System.out.println("INFO in CNF.testUnify3(): -------------------------------------");
        String rule4 = "a(?X,?Y), b(?X,foo).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(nnn,dontcare), b(nnn,foo), a(bar,baz)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf4);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf5);
        Subst bindings = cnf4.unify(cnf5);
        String bindStr = "null";
        if (bindings != null)
            bindStr = bindings.toString();
        assertEquals("{?X=nnn, ?Y=dontcare}",bindStr);
    }

    /****************************************************************
     */
    @Test
    public void testUnify4() {

        CNF.debug = true;
        System.out.println("INFO in CNF.testUnify4(): -------------------------------------");
        String rule4 = "b(?X,foo), a(?X,?Y).";
        CNF cnf4 = new CNF(rule4);
        String cnfstr5 = "a(nnn,dontcare), b(nnn,foo), a(bar,baz)";
        CNF cnf5 = new CNF(cnfstr5);
        System.out.println("INFO in CNF.testUnify4(): cnf " + cnf4);
        System.out.println("INFO in CNF.testUnify4(): cnf1 " + cnf5);
        Subst bindings = cnf4.unify(cnf5);
        String bindStr = "null";
        if (bindings != null)
            bindStr = bindings.toString();
        assertEquals("{?X=nnn, ?Y=dontcare}",bindStr);
    }
}
