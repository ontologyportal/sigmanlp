package com.articulate.nlp.semRewrite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semRewrite.UnitSemRewriteTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.semRewrite.ProceduresUnitTest.class,
    com.articulate.nlp.semRewrite.LexerTest.class,
    com.articulate.nlp.semRewrite.AddQueryObjectQuantifierTest.class,
    com.articulate.nlp.semRewrite.LiteralTest.class,
    com.articulate.nlp.semRewrite.CNFTest.class,
    com.articulate.nlp.semRewrite.RunAllUnitSemRewrite.class,
    com.articulate.nlp.semRewrite.RHSTest.class,
    com.articulate.nlp.semRewrite.SemRewriteToFormulaTest.class,
    com.articulate.nlp.semRewrite.SemRewriteTest2.class,
    com.articulate.nlp.semRewrite.SemRewriteTest.class,
    com.articulate.nlp.semRewrite.InterpUnitTest.class,
    com.articulate.nlp.semRewrite.SemRewriteTestTimeDate.class,
    com.articulate.nlp.semRewrite.ClauseTest.class,
    com.articulate.nlp.semRewrite.SemRewriteTestByUDepRel.class,
    com.articulate.nlp.semRewrite.RulePrepAboutRefersToUnitTest.class,
    com.articulate.nlp.semRewrite.CNFNewTest.class,
    com.articulate.nlp.semRewrite.substitutor.LocationSubstitutorTest.class
})
public class UnitSemRewriteTestSuite {

} // end class file UnitSemRewriteTestSuite.java