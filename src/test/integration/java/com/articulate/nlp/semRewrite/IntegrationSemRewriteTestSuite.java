/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit4Suite.java to edit this template
 */

package com.articulate.nlp.semRewrite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 *
 * @author <a href="mailto:tdnorbra@nps.edu?subject=com.articulate.nlp.semRewrite.IntegrationSemRewriteTestSuite">Terry Norbraten, NPS MOVES</a>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    com.articulate.nlp.semRewrite.CoreLabelTest.class,
    com.articulate.nlp.semRewrite.CommonCNFtest.class,
    com.articulate.nlp.semRewrite.SentenceUtilTest.class,
    com.articulate.nlp.semRewrite.UnificationTest.class,
    com.articulate.nlp.semRewrite.CoNLLTest.class,
    com.articulate.nlp.semRewrite.NPtypeTest.class,
    com.articulate.nlp.semRewrite.InterpreterWSDBatchTest.class,
    com.articulate.nlp.semRewrite.InterpreterPreprocessTest.class,
    com.articulate.nlp.semRewrite.ProceduresTest.class,
    com.articulate.nlp.semRewrite.RulePrepAboutRefersToIntegrationTest.class,
    com.articulate.nlp.semRewrite.RelExtractTest.class,
    com.articulate.nlp.semRewrite.RunAllSemRewriteIntegTest.class,
    com.articulate.nlp.semRewrite.InterpreterGenCNFTest.class,
    com.articulate.nlp.semRewrite.InterpreterWSDTest.class,
    com.articulate.nlp.semRewrite.CNFIntegTest.class
})
public class IntegrationSemRewriteTestSuite {

} // end class file IntegrationSemRewriteTestSuite.java