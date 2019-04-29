package com.articulate.nlp.semRewrite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import com.articulate.nlp.corpora.SchemaOrgTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        //LocationSubstitutor.class,

        AddQueryObjectQuantifierTest.class,
        CNFNewTest.class,
        CNFTest.class,
        InterpUnitTest.class,
        LexerTest.class,
        LiteralTest.class,
        ProceduresUnitTest.class,
        RHSTest.class,
        RulePrepAboutRefersToUnitTest.class,
        SemRewriteTest.class,
        SemRewriteTest2.class,
        //com.articulate.nlp.semRewrite.SemRewriteTestByUDepRel.class,
        SemRewriteTestTimeDate.class,
        //com.articulate.nlp.semRewrite.SemRewriteToFormulaTest.class,

        SchemaOrgTest.class,
})

public class RunAllUnitSemRewrite {

}