package com.articulate.nlp;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.Formula;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests the SemRewrite of a given parse.
 * In: Dependency parse as string.
 * Out: The right-hand-side of a SemRewrite rule that is triggered by the input.
 */
public class SemRewriteCorpusTest extends IntegrationTestBase {

    private static Interpreter interpreter;

    @Before
    public void setUpInterpreter() throws IOException {

        interpreter = new Interpreter();
        Interpreter.inference = false;
        interpreter.initialize();
    }

    /****************************************************************
     */
    private void doTest(String input, String expected) {

        if (input == null || "".equals(input) || expected == null || "".equals(expected)) {
            System.out.println("Error in SemRewriteCorpusTest.doTest(): null input");
            return;
        }
        Formula fExpected = new Formula(expected);
        String actual = interpreter.interpretSingle(input);
        Formula fActual = new Formula(actual);
        System.out.println("SemRewriteCorpusTest: input: " + input);
        System.out.println("SemRewriteCorpusTest: expected: " + fExpected);
        System.out.println("SemRewriteCorpusTest: actual: " + fActual);
        //Formula.debug = true;
        if (fExpected.deepEquals(fActual))
            System.out.println("SemRewriteCorpusTest: pass");
        else
            System.out.println("SemRewriteCorpusTest: fail");
        assertTrue(fExpected.deepEquals(fActual));
    }

    /****************************************************************
     */
    @Test
    public void testLeighBlankets()   {

        String input = "Leigh swaddled the baby in blankets.";
        String expected = "(exists (?Leigh-1 ?baby-4 ?blankets-6 ?swaddled-2)\n" +
                "  (and\n" +
                "    (orientation ?swaddled-2 ?blankets-6 Inside)\n" +
                "    (destination ?swaddled-2 ?blankets-6)\n" +
                "    (names ?Leigh-1 \"Leigh\")\n" +
                "    (instance ?baby-4 HumanBaby)\n" +
                "    (agent ?swaddled-2 ?Leigh-1)\n" +
                "    (patient ?swaddled-2 ?baby-4)\n" +
                "    (earlier\n" +
                "      (WhenFn ?swaddled-2) Now)\n" +
                "    (instance ?blankets-6 Blanket)\n" +
                "    (instance ?Leigh-1 Human)\n" +
                "    (instance ?swaddled-2 Covering)))";
        doTest(input,expected);
    }

}