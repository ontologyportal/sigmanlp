package com.articulate.nlp;

import com.articulate.nlp.corpora.DBPedia;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KBmanager;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by apease on 7/2/18.
 */
public class DBPediaTest {

    /****************************************************************
     */
    @Before
    public void setUpInterpreter() throws IOException {

        KBmanager.getMgr().initializeOnce();
        DBPedia.initOnce();
    }

    /** *************************************************************
     */
    @Test
    public void test1() {

    }
}
