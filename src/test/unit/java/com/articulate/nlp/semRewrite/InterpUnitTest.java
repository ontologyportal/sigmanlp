package com.articulate.nlp.semRewrite;

import com.articulate.nlp.UnitTestBase;
import java.util.ArrayList;

import org.junit.Test;
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
public class InterpUnitTest extends UnitTestBase {

    /****************************************************************
     */
    @Test
    public void testInterpCNF() {

        String input = "attribute(Mary-1,Female), aux(walk-3,might-2), " +
                "names(Mary-1,\"Mary\"), nsubj(walk-3,Mary-1), " +
                "number(SINGULAR,Mary-1), root(ROOT-0,walk-3), sumo(Human,Mary-1), " +
                "sumo(Walking,walk-3).";
        ArrayList<CNF> clauses = new ArrayList<>();
        clauses.add(new CNF(input));
        Rule r = Rule.parseString("aux(?V,might*) ==> (possible(?V,DUMMY)).");
        RuleSet rs = new RuleSet();
        rs.rules.add(r);
        r = Rule.parseString("possible(?X,DUMMY) ==> {(possible ?X DUMMY)}.");
        rs.rules.add(r);
        Interpreter interp = new Interpreter(rs);
        ArrayList<String> result = interp.interpretCNF(clauses);
        String expected = "(possible walk-3 DUMMY)";
        System.out.println("testInterpCNF(): result" + result);
        System.out.println("testInterpCNF(): expected" + expected);
        if (result.contains(expected))
            System.out.println("testInterpCNF(): pass");
        else
            System.out.println("testInterpCNF(): false");
        assertTrue(result.contains(expected));
    }


}
