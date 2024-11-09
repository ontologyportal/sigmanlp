package com.articulate.nlp.semRewrite;

import com.articulate.nlp.IntegrationTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.nlp.semRewrite.datesandnumber.*;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        InterpreterPreprocessTest.class,
        InterpretNumericsTest.class,
        ProceduresTest.class,
        RelExtractTest.class,
        RulePrepAboutRefersToIntegrationTest.class,
        SentenceUtilTest.class,
        UnificationTest.class
})

public class RunAllSemRewriteIntegTest extends IntegrationTestBase {

}