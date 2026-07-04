package com.articulate.nlp;

import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Arrays;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.parsing.Configuration;

/****************************************************************
 * Base class for unit tests which are closer to integration tests because they require a large KB configuration.
 */
public class IntegrationTestBase extends SigmaTestBase {
    //private static final String CONFIG_FILE_PATH = "resources/config_all.xml";
    private static final Class CLASS = IntegrationTestBase.class;
    private static boolean integrationConfigurationInitialized = false;
    protected static KB kbBackup;

    /****************************************************************
     * File object pointing to this test's resources directory.
     */
    public static final File RESOURCES_FILE;

    static  {

        URI uri = null;
        try {
            //ClassLoader classLoader = CLASS.getClassLoader();
            //System.out.println("Integration test base: " + classLoader.getResource("."));
            //uri = classLoader.getResource("../resources").toURI();
            uri = new URI("file:" + System.getenv("ONTOLOGYPORTAL_GIT") + "/sigmanlp/src/test/integration/java/resources");
            System.out.println("Integration test base: " + uri);
        }
        catch (URISyntaxException e) {
            System.err.println("IntegrationTestBase uri:" + uri);
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        RESOURCES_FILE = new File(uri);
    }

    static Long totalKbMgrInitTime = Long.MAX_VALUE;

    /****************************************************************/
    @BeforeClass
    public static void setup() throws IOException {

        long startTime = System.currentTimeMillis();
        if (!integrationConfigurationInitialized || !KBmanager.initialized) {
            Configuration config = buildIntegrationConfiguration();
            System.out.println("IntegrationTestBase.setup(): initializing integration KB.");
            KBmanager.initialized = false;
            KBmanager.initializing = false;
            KBmanager.getMgr().kbs.clear();
            KBmanager.getMgr().initializeOnce(config);
            integrationConfigurationInitialized = true;
        }
        else {
            System.out.println("IntegrationTestBase.setup(): reusing already initialized integration KB.");
        }
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getDefaultKbName());
        if (kb == null)
            throw new IllegalStateException("Could not get default KB after initialization.");
        if (kbBackup == null)
            kbBackup = new KB(kb);
        checkConfiguration();
        long endTime = System.currentTimeMillis();
        if (IntegrationTestBase.totalKbMgrInitTime == Long.MAX_VALUE)
            IntegrationTestBase.totalKbMgrInitTime = endTime - startTime;
    }
    
    private static Configuration buildIntegrationConfiguration() {

        String kbDir = KB_PATH;
        Configuration config = new Configuration();
        config.clearKbConstituentLists();
        config.setPreference("baseDir", System.getenv("SIGMA_HOME"));
        config.setPreference("kbDir", kbDir);
        config.setPreference("inferenceTestDir", kbDir + File.separator + "tests");
        config.setPreference("loadFresh", "true");
        config.setPreference("loadLexicons", "true");
        config.setPreference("cache", "true");
        config.setPreference("cacheDisjoint", "true");
        config.setPreference("cwa", "false");
        config.setPreference("maxPredicateArity", "6");
        config.setPreference("graphVizExec", "/usr/bin/dot");
        String userHome = System.getProperty("user.home");
        String eproverExec = new File("/usr/local/bin/e_ltb_runner").canExecute()
                ? "/usr/local/bin/e_ltb_runner"
                : userHome + File.separator + "Programs" + File.separator + "E" + File.separator + "PROVER" + File.separator + "e_ltb_runner";
        String vampireExec = new File("/usr/local/bin/vampire").canExecute()
                ? "/usr/local/bin/vampire"
                : userHome + File.separator + "Programs" + File.separator + "vampire" + File.separator + "build" + File.separator + "vampire";
        config.setPreference("eproverExec", eproverExec);
        config.setPreference("vampireExec", vampireExec);
        config.setPreference("tptpExec", System.getProperty("user.home") + File.separator + "workspace" + File.separator + "TPTP4X" + File.separator + "tptp4X");
        config.setPreference("systemsDir", System.getProperty("user.home"));
        List<String> constituents = Arrays.asList(
            "english_format.kif",
            "domainEnglishFormat.kif",
            "Merge.kif",
            "Mid-level-ontology.kif",
            "ArabicCulture.kif",
            "Cars.kif",
            "Catalog.kif",
            "Communications.kif",
            "CountriesAndRegions.kif",
            "Dining.kif",
            "Economy.kif",
            "engineering.kif",
            "FinancialOntology.kif",
            "Food.kif",
            "Geography.kif",
            "Government.kif",
            "Hotel.kif",
            "Justice.kif",
            "Languages.kif",
            "Media.kif",
            "MilitaryDevices.kif",
            "Military.kif",
            "MilitaryPersons.kif",
            "MilitaryProcesses.kif",
            "Music.kif",
            "naics.kif",
            "People.kif",
            "QoSontology.kif",
            "Sports.kif",
            "TransnationalIssues.kif",
            "Transportation.kif",
            "TransportDetail.kif",
            "VirusProteinAndCellPart.kif",
            "WMD.kif"
        );
        config.setKbConstituentList("SUMO", constituents);
        return config;
    }

    /****************************************************************
     * Undo all parts of the state that have anything to do with user assertions made during inference.
     * @throws IOException
     */
    public static void resetAllForInference() throws IOException {

        kb = new KB(kbBackup);
        KBmanager.getMgr().kbs.put("SUMO", kb);
        kb.deleteUserAssertions();

        // Remove the assertions in the files.
        File userAssertionsFile = new File(KB_PATH, "SUMO" + KB._userAssertionsString);
        if (userAssertionsFile.exists()) {
            userAssertionsFile.delete();
            userAssertionsFile.createNewFile();
            userAssertionsFile.deleteOnExit();
        }

        String tptpFileName = userAssertionsFile.getAbsolutePath().replace(".kif", ".tptp");
        userAssertionsFile = new File(tptpFileName);
        if (userAssertionsFile.exists()) {
            userAssertionsFile.delete();
            userAssertionsFile.createNewFile();
            userAssertionsFile.deleteOnExit();
        }
    }
}
