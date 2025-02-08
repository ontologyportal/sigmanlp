package com.articulate.nlp.semRewrite;

import com.articulate.nlp.UnitTestBase;
import com.articulate.nlp.semRewrite.substitutor.LocationSubstitutorTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semRewrite.UnitSemRewriteTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    AddQueryObjectQuantifierTest.class,
    CNFNewTest.class,
    CNFTest.class,
    // TODO: Fails
    ClauseTest.class,
    InterpUnitTest.class,
    LexerTest.class,
    LiteralTest.class,
    ProceduresUnitTest.class,
    RHSTest.class,
    RulePrepAboutRefersToUnitTest.class,
    SemRewriteTest.class,
    SemRewriteTest2.class,
    SemRewriteTestByUDepRel.class,
    SemRewriteTestTimeDate.class,
    // TODO: Fails
    SemRewriteToFormulaTest.class,
    // TODO: Fails
    LocationSubstitutorTest.class
})
public class UnitSemRewriteTestSuite extends UnitTestBase {

} // end class file UnitSemRewriteTestSuite.java