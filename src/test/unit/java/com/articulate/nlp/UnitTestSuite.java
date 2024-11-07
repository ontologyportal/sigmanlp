package com.articulate.nlp;

import com.articulate.nlp.corpora.UnitCorporaTestSuite;
import com.articulate.nlp.imsclient.UnitImsclientTestSuite;
import com.articulate.nlp.pipeline.UnitPiplineTestSuite;
import com.articulate.nlp.semRewrite.UnitSemRewriteTestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

//This software is released under the GNU Public License
//<http://www.gnu.org/copyleft/gpl.html>.
// Copyright 2019 Infosys
// adam.pease@infosys.com

@RunWith(Suite.class)
@Suite.SuiteClasses({
    UnitCorporaTestSuite.class,
    UnitImsclientTestSuite.class,
    UnitPiplineTestSuite.class,
    UnitSemRewriteTestSuite.class
})
public class UnitTestSuite extends UnitTestBase {

}
