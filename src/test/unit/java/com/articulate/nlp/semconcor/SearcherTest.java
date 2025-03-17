package com.articulate.nlp.semconcor;

import com.articulate.nlp.UnitTestBase;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.utils.StringUtil;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semconcor.SearcherTest">Terry Norbraten, NPS MOVES</a>
 */
public class SearcherTest extends UnitTestBase {

    Interpreter interp;
    String searchString;
    String depString;
    boolean success;

    @Before
    public void startUp() {

        searchString = "sumo(FinancialTransaction,?X)";
        depString = searchString;
        interp = new Interpreter();
        success = true;
    }

    @After
    public void tearDown() {

        interp = null;
    }

   @Test
   public void testPrintSearchResults() {

       System.out.println("------------- SearcherTest.testPrintSearchResults() -------------");
        try {
            interp.initialize();
            searchString = StringUtil.removeEnclosingQuotes(searchString);
            Searcher.printSearchResults("FCE",searchString,depString);
        }
        catch (Exception e) {
            e.printStackTrace();
            success = false;
        }
        assertTrue(success);
   }

} // end class file SearcherTest.java