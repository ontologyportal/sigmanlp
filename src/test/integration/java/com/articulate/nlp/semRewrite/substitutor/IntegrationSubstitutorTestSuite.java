package com.articulate.nlp.semRewrite.substitutor;

import com.articulate.nlp.IntegrationTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semRewrite.substitutor.IntegrationSubstitutorTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.semRewrite.substitutor.NounSubstitutorTest.class,
    com.articulate.nlp.semRewrite.substitutor.IdiomSubstitutorTest.class
})
public class IntegrationSubstitutorTestSuite extends IntegrationTestBase {

} // end class file IntegrationSubstitutorTestSuite.java