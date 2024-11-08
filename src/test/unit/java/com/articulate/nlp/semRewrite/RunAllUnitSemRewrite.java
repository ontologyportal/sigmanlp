package com.articulate.nlp.semRewrite;

import com.articulate.nlp.UnitTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.nlp.corpora.SchemaOrgTest;
import com.articulate.nlp.semRewrite.substitutor.LocationSubstitutor;

@RunWith(Suite.class)
@Suite.SuiteClasses({
//        LocationSubstitutor.class,

        AddQueryObjectQuantifierTest.class,
//        CNFNewTest.class,
        CNFTest.class,
        InterpUnitTest.class,
        LexerTest.class,
        LiteralTest.class,
        ProceduresUnitTest.class,
        RHSTest.class,
        RulePrepAboutRefersToUnitTest.class,
//        SemRewriteTest.class,
//        SemRewriteTest2.class,
        //com.articulate.nlp.semRewrite.SemRewriteTestByUDepRel.class,
        SemRewriteTestTimeDate.class,
        //com.articulate.nlp.semRewrite.SemRewriteToFormulaTest.class,

        SchemaOrgTest.class,
})

public class RunAllUnitSemRewrite extends UnitTestBase {

}