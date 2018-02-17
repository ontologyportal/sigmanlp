package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import org.junit.Before;
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
public class ProceduresTest extends IntegrationTestBase {

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() {

        KBmanager.getMgr().initializeOnce();
    }

    /****************************************************************
     * Robert wears a shirt
     */
    @Test
    public void testIsChildOfClass() {

        System.out.println("INFO in ProceduresTest.testIsChildOf()");
        String lit = "isChildOf(Clothing,Artifact)";
        Literal l = new Literal(lit);
        assert(Procedures.isChildOf(l).equals("true"));
    }
}
