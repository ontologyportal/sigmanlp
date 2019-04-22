package com.articulate.nlp.semRewrite;

import com.articulate.sigma.UnitTestBase;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;

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

public class LiteralTest {

    /****************************************************************
     */
    @Test
    public void testLiteral() {

        String example4 = "root(ROOT-0, reached-14), prepc_after(reached-14, making-2), " +
            "amod(stops-4, several-3), dobj(making-2, stops-4), nn(islands-7, Caribbean-6), " +
            "prep_at(making-2, islands-7), appos(islands-7, de-9), nsubj(reached-14, Leon-10), " +
            "poss(men-13, his-12), conj_and(Leon-10, men-13), nsubj(reached-14, men-13), " +
            "det(coast-17, the-15), amod(coast-17, east-16), dobj(reached-14, coast-17), " +
            "prep_of(coast-17, Florida-19), nn(Augustine-22, St.-21), appos(Florida-19, Augustine-22), " +
            "prep_on(reached-14, April-25), num(April-25, 2-26), num(April-25, 1513-28)";
        Lexer lex = new Lexer(example4);
        Literal.parse(lex,0);

        String example = "root(ROOT-0, than-15), mark(1/1000th-5, Although-1)";
        lex = new Lexer(example);
        Literal.parse(lex,0);

        example = "root(ROOT-0, than-15), mark(3,200, Although-1)";
        lex = new Lexer(example);
        Literal.parse(lex,0);
    }

    /****************************************************************
     */
    public void testUnify(String lstr1, String lstr2, String resStr) {

        Literal l = new Literal(lstr1);
        Literal l2 = new Literal(lstr2);
        Subst bindings = l.mguTermList(l2);
        System.out.println("LiteralTest.testUnify(): " + lstr1);
        System.out.println("LiteralTest.testUnify(): " + lstr2);
        System.out.println("LiteralTest.testUnify(): result: " + bindings);
        String result = null;
        if (bindings != null)
            result = bindings.toString();
        assertEquals(resStr,result);
    }

    /****************************************************************
     */
    @Test
    public void testUnify1() {

        String example1 = "root(ROOT-0, reached-14)";
        String example2 = "root(?V, reached*)";
        testUnify(example1,example2,"{?V=ROOT-0}");
    }

    /****************************************************************
     */
    @Test
    public void testUnify2() {

        String example1 = "root(ROOT-0, reached-14)";
        String example2 = "root(?V, lost*)";
        testUnify(example1,example2,null);
    }

    /****************************************************************
     */
    @Test
    public void testUnify3() {

        String example1 = "foo(ROOT-0, reached-14)";
        String example2 = "root(?V, reached*)";
        testUnify(example1,example2,null);
    }
}
