package com.articulate.nlp.semRewrite;

import com.articulate.nlp.UnitTestBase;

import java.text.ParseException;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
public class ClauseTest extends UnitTestBase {

    /** *************************************************************
     */
    public void doTestUnifyProc(String input) {

        Literal l = new Literal(input);
        Clause d = new Clause();
        d.disjuncts.add(l);
        Clause c = new Clause();
        System.out.println("Clause.testUnifyProc2(): " + c.unify(d));
        assertEquals("{}",c.unify(d).toString());
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc1() {

        Literal l = null;
        try {
            String input = "isCELTclass(Sub,Super).";
            Lexer lex = new Lexer(input);
            lex.look();
            l = Literal.parse(lex, 0);
        }
        catch (ParseException ex) {
            String message = ex.getMessage();
            System.err.println("Error in Clause.testUnifyProc1() " + message);
            ex.printStackTrace();
        }
        Clause d = new Clause();
        d.disjuncts.add(l);
        Clause c = new Clause();
        assertEquals("{}",c.unify(d).toString());
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc2() {

        doTestUnifyProc("isCELTclass(Man,Person).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc3() {

        //"isCELTclass","isSubclass", "isInstanceOf", "isSubAttribute", "isChildOf"
        doTestUnifyProc("isCELTclass(Year,Time).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc4() {

        doTestUnifyProc("isCELTclass(Wednesday,Time).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc5() {

        Clause.debug = true;
        Procedures.debug = true;
        String input = "isCELTclass(Number,Time).";
        Literal l = new Literal(input);
        Clause d = new Clause();
        d.disjuncts.add(l);
        Clause c = new Clause();
        Subst result = c.unify(d);
        System.out.println("Clause.testUnifyProc5(): " + result);
        String resStr = null;
        if (result != null)
            resStr = result.toString();
        assertNotEquals("{}",resStr);
    }
}
