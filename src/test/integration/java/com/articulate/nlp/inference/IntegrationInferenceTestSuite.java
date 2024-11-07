package com.articulate.nlp.inference;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.inference.IntegrationInferenceTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.inference.DemoScript20150421_3Test.class,
    com.articulate.nlp.inference.DemoScript20150401Test.class,
    com.articulate.nlp.inference.DemoScript20150421_1Test.class,
    com.articulate.nlp.inference.DemoScript20150421_4Test.class,
    com.articulate.nlp.inference.QAInferenceTest.class,
    com.articulate.nlp.inference.DemoScript20150421_2Test.class,
    com.articulate.nlp.inference.InferenceInitTest.class
})
public class IntegrationInferenceTestSuite {

} // end class file IntegrationInferenceTestSuite.java