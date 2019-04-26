package com.articulate.nlp.semRewrite;

import com.articulate.sigma.*;
import org.junit.Test;

import java.util.HashMap;

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
public class ProceduresUnitTest extends UnitTestBase {

    /** *************************************************************
     */
    public void doTestUnifyProc(boolean expected, String input) {

        Literal l = null;
        l = new Literal(input);
        String str = "";
        if (input.startsWith("isCELTclass"))
            str = Procedures.isCELTclass(l);
        else if (input.startsWith("isSubclass"))
            str = Procedures.isSubclass(l);
        else if (input.startsWith("isInstanceOf"))
            str = Procedures.isInstanceOf(l);
        else if (input.startsWith("isSubAttribute"))
            str = Procedures.isSubAttribute(l);
        else if (input.startsWith("isChildOf"))
            str = Procedures.isChildOf(l);
        boolean actual = false;
        if (str.equals("true"))
            actual = true;
        System.out.println("Clause.doTestUnifyProc(): " + actual);
        assertEquals(expected,actual);
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc1() {

        doTestUnifyProc(true,"isCELTclass(Sub,Super).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc2() {

        doTestUnifyProc(true,"isCELTclass(Man,Person).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc3() {

        //"isCELTclass","isSubclass", "isInstanceOf", "isSubAttribute", "isChildOf"
        KB kb = KBmanager.getMgr().getKB("SUMO");
        //System.out.println("testUnifyProc3(): " + kb.isSubclass("Year", "TimeMeasure"));
        doTestUnifyProc(true,"isCELTclass(Year,Time).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc4() {

        //KB kb = KBmanager.getMgr().getKB("SUMO");
        //System.out.println("testUnifyProc4(): " + kb.isSubclass("Wednesday", "TimeMeasure"));
        //System.out.println("testUnifyProc4(): " + kb.isSubclass("Wednesday", "TimePosition"));
        //System.out.println("testUnifyProc4(): " + kb.isSubclass("Wednesday", "TimeInterval"));
        //System.out.println("testUnifyProc4(): " + kb.isSubclass("Wednesday", "Day"));
        //System.out.println("testUnifyProc4(): " + kb.kbCache.getParentClasses("Wednesday"));
        //System.out.println("testUnifyProc4(): " + kb.terms.contains("Wednesday"));
        //System.out.println("testUnifyProc4(): " + kb.kbCache.childOfP("subclass","Wednesday", "TimeMeasure"));
        //System.out.println("testUnifyProc4(): " + kb.kbCache.subclassOf("Wednesday", "TimeMeasure"));
        doTestUnifyProc(true,"isCELTclass(Wednesday,Time).");
    }

    /** *************************************************************
     */
    @Test
    public void testUnifyProc5() {

        doTestUnifyProc(false,"isCELTclass(Number,Time).");
    }

    /** *************************************************************

    @Test
    public void testUnifyProc6() {  // requires Media.kif

        doTestUnifyProc(true,"isCELTclass(ChristmasDay,Time).");
    }
*/

    /** *************************************************************
     */
    @Test
    public void testUnifyProc7() {

        doTestUnifyProc(true,"isSubclass(Deciding,Process).");
    }

}
