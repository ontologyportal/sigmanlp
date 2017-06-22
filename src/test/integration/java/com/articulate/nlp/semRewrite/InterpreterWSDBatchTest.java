/*
Copyright 2014-2015 IPsoft

Author: Andrei Holub andrei.holub@ipsoft.com

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
package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import com.articulate.sigma.KBmanager;
import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.substitutor.NounSubstitutor;
import com.articulate.nlp.semRewrite.substitutor.SubstitutionUtil;
import com.articulate.sigma.test.JsonReader;
import com.google.common.collect.Maps;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/** ***************************************************************
 */
@RunWith(Parameterized.class)
public class InterpreterWSDBatchTest extends IntegrationTestBase {

    public static Interpreter interp = new Interpreter();

    @Parameterized.Parameter(value = 0)
    public String input;
    @Parameterized.Parameter(value = 1)
    public String[] expected;

    @BeforeClass
    public static void initInterpreter() {

        KBmanager.getMgr().initializeOnce();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** ***************************************************************
     */
    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> prepare() {

        return JsonReader.transform("resources/interpreter_wsd_batch.json", (JSONObject jo) -> {
            String input = (String) jo.get("input");
            JSONArray test = (JSONArray) jo.get("test");
            return new Object[]{input, test.toArray(new String[]{})};
        });
    }

    /** ***************************************************************
     */
    @Test
    public void test() {

        List<String> wsds = doFullWSD(input);
        assertThat(wsds, hasItems(expected));
        assertEquals(expected.length, wsds.size());
    }

    /** ***************************************************************
     */
    private List<String> doFullWSD(String input) {

        Annotation wholeDocument = interp.userInputs.annotateDocument(input);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<String> wsds = interp.findWSD(lastSentence);

        return wsds;
    }
}
