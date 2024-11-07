package com.articulate.nlp;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.IntegrationTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.GenSimpTestDataTest.class,
    com.articulate.nlp.PartOfSpeechInfoTest.class,
    com.articulate.nlp.DBPediaTest.class,
    com.articulate.nlp.LemmatizerTest.class,
    com.articulate.nlp.DocumentTest.class,
    com.articulate.nlp.IntegrationTestBase.class,
    com.articulate.nlp.WNMWAnnotatorTest.class
})
public class IntegrationNlpTestSuite {

} // end class file IntegrationTestSuite.java