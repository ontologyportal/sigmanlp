package com.articulate.nlp;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.IntegrationTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    DBPediaTest.class,
    DocumentTest.class,
    // TODO: Need corpora/COCA files (tdn) 11/8/24
//    GenSimpTestDataTest.class,
    LemmatizerTest.class,
    // TODO: Fails
    PartOfSpeechInfoTest.class,
    WNMWAnnotatorTest.class
})
public class IntegrationNlpTestSuite extends IntegrationTestBase {

} // end class file IntegrationTestSuite.java