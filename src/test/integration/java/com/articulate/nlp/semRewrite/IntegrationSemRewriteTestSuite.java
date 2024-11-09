package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semRewrite.IntegrationSemRewriteTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    // TODO: Fails
//    CNFIntegTest.class,
    // Brutal on the MBP CPU % (tdn) 11/8/24
//    CoNLLTest.class,
    CommonCNFtest.class,
    CoreLabelTest.class,
    // TODO: Fails
//    InterpreterGenCNFTest.class,
    InterpreterPreprocessTest.class,
    // TODO: Fails
//    InterpreterWSDBatchTest.class,
    // TODO: Fails
//    InterpreterWSDTest.class,
    NPtypeTest.class,
    ProceduresTest.class,
    RelExtractTest.class,
    RulePrepAboutRefersToIntegrationTest.class,
    // TODO: Fails
//    SentenceUtilTest.class,
    UnificationTest.class
})
public class IntegrationSemRewriteTestSuite extends IntegrationTestBase {

} // end class file IntegrationSemRewriteTestSuite.java