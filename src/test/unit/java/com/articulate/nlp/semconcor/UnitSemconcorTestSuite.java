package com.articulate.nlp.semconcor;

import com.articulate.nlp.UnitTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.corpora.UnitSemconcorTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    IndexerTest.class,
    SearcherTest.class
})
public class UnitSemconcorTestSuite extends UnitTestBase {

} // end class file UnitCorporaTestSuite.java