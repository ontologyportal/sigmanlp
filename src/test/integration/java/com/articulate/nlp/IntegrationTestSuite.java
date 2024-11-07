package com.articulate.nlp;

import com.articulate.nlp.inference.IntegrationInferenceTestSuite;
import com.articulate.nlp.semRewrite.IntegrationSemRewriteTestSuite;
import com.articulate.nlp.semRewrite.datesandnumber.IntegrationDateAndNumberTestSuite;
import com.articulate.nlp.semRewrite.substitutor.IntegrationSubstitutorTestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.IntegrationTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.IntegrationNlpTestSuite.class,
    IntegrationInferenceTestSuite.class,
    IntegrationSemRewriteTestSuite.class,
    IntegrationDateAndNumberTestSuite.class,
    IntegrationSubstitutorTestSuite.class
})
public class IntegrationTestSuite {

} // end class file IntegrationTestSuite.java