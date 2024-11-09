package com.articulate.nlp.inference;

import com.articulate.nlp.IntegrationTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.inference.IntegrationInferenceTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    DemoScript20150421_3Test.class,
    DemoScript20150401Test.class,
    DemoScript20150421_1Test.class,
    DemoScript20150421_4Test.class,
    DemoScript20150421_2Test.class,
    InferenceInitTest.class,
    QAInferenceTest.class
})
public class IntegrationInferenceTestSuite extends IntegrationTestBase {

} // end class file IntegrationInferenceTestSuite.java