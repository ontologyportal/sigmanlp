package com.articulate.nlp;

import com.articulate.sigma.KBmanager;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.*;

import static org.junit.Assert.fail;

/**
 * Base class for fast-running true unit tests.
 */
public class UnitTestBase extends SigmaTestBase {

    private static final String SIGMA_SRC = System.getenv("SIGMA_SRC");
    public static final String CONFIG_FILE_DIR = SIGMA_SRC + File.separator +
            "test/unit/java/resources";
    private static final Class CLASS = UnitTestBase.class;
    public static final int NUM_KIF_FILES = 4;

    static Long totalKbMgrInitTime = Long.MAX_VALUE;

    /***************************************************************
     * */
    @BeforeClass
    public static void setup()  {

        System.out.println("UnitTestBase.setup(): building in-memory unit test configuration.");
        System.out.println("***** UnitTestBase.setup(): only the in-memory unit KB constituents will be loaded. *****");
        long startTime = System.currentTimeMillis();
        SigmaTestBase.doSetUp();
        long endTime = System.currentTimeMillis();
        // Update the init time only if it has its initialized value.
        if (UnitTestBase.totalKbMgrInitTime == Long.MAX_VALUE) {
            UnitTestBase.totalKbMgrInitTime = endTime - startTime;
        }
    }

    /***************************************************************
     * */
    @AfterClass
    public static void checkKBCount() {

        if (KBmanager.getMgr().getKB(KBmanager.getMgr().getDefaultKbName()).constituents.size() > NUM_KIF_FILES) { // include cache file
            System.out.println("FAILURE: This test is running with the wrong configuration. Please investigate immediately, since the problem does not consistently appear.");
            System.out.println("  Because this test is changing the configuration, other tests may fail, even if this one passes.");
            System.out.println("  Nbr kif files: " + KBmanager.getMgr().getKB(KBmanager.getMgr().getDefaultKbName()).constituents.size());
            fail();
        }
    }
}
