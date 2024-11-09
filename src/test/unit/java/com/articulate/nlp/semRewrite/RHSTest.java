package com.articulate.nlp.semRewrite;

import com.articulate.nlp.UnitTestBase;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

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
public class RHSTest extends UnitTestBase {

    /****************************************************************
     */
    @Test
    public void testRHSCNF() {

        //RHS.debug = true;
        String input = "nsubj(grasps*,?animal), dobj(grasps*,?object), sumo(?TYPEVAR1,?object), " +
                "isCELTclass(?animal,Person), isChildOf(?TYPEVAR1,Object) ==> {(grasps ?animal ?object)}.";
        Lexer lex = new Lexer(input);
        Rule r = Rule.parse(lex);
        System.out.println("RHSTest.testRHSCNF(): " + r.rhs.cnf);
        assertTrue(r.rhs.cnf != null);
    }


}
