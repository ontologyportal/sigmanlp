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

        System.out.println("IntegrationTestBase.setup(): using default KBmanager configuration.");
        System.out.println("SIGMA_HOME = " + System.getenv("SIGMA_HOME"));
        System.out.println("Expected runtime config = " +
                new File(System.getenv("SIGMA_HOME"), "KBs/config.xml").getCanonicalPath());

        if (!KBmanager.initialized) {
            KBmanager.getMgr().initializeOnce();
        }

        kb = KBmanager.getMgr().getKB("SUMO");
        kbBackup = new KB(kb);

        checkConfiguration();

        long endTime = System.currentTimeMillis();

        if (IntegrationTestBase.totalKbMgrInitTime == Long.MAX_VALUE) {
            IntegrationTestBase.totalKbMgrInitTime = endTime - startTime;
        }
    }
    
    private static Configuration buildIntegrationConfiguration() {

        String sigmaHome = System.getenv("SIGMA_HOME");
        String kbDir = KB_PATH;
        String userHome = System.getProperty("user.home");

        Configuration config = new Configuration();
        config.clearKbConstituentLists();

        String eproverExec = new File("/usr/local/bin/e_ltb_runner").canExecute()
                ? "/usr/local/bin/e_ltb_runner"
                : userHome + File.separator + "Programs" + File.separator + "E" +
                File.separator + "PROVER" + File.separator + "e_ltb_runner";

        String vampireExec = new File("/usr/local/bin/vampire").canExecute()
                ? "/usr/local/bin/vampire"
                : userHome + File.separator + "Programs" + File.separator + "vampire" +
                File.separator + "build" + File.separator + "vampire";

        config.setPreference("adminBrowserLimit", "200");
        config.setPreference("baseDir", sigmaHome);
        config.setPreference("cache", "yes");
        config.setPreference("celtdir", "");
        config.setPreference("editDir", kbDir);
        config.setPreference("eprover", eproverExec);
        config.setPreference("eproverExec", eproverExec);
        config.setPreference("graphVizDir", "/usr/bin");
        config.setPreference("graphVizExec", "/usr/bin/dot");
        config.setPreference("holdsPrefix", "no");
        config.setPreference("hostname", "localhost");
        config.setPreference("https", "false");
        config.setPreference("imageFormat", "png");
        config.setPreference("inferenceTestDir", kbDir + File.separator + "tests");
        config.setPreference("jedit", "/usr/share/jedit/jedit");
        config.setPreference("kbDir", kbDir);
        config.setPreference("leoExecutable", userHome + File.separator + "leo");
        config.setPreference("lineNumberCommand", "");
        config.setPreference("loadCELT", "no");
        config.setPreference("loadFresh", "false");
        config.setPreference("logDir", sigmaHome + File.separator + "logs");
        config.setPreference("logLevel", "warning");
        config.setPreference("port", "8080");
        config.setPreference("prolog", "");
        config.setPreference("reportDup", "no");
        config.setPreference("reportFnError", "no");
        config.setPreference("showcached", "no");
        config.setPreference("sumokbname", "SUMO");
        config.setPreference("systemsDir", userHome);
        config.setPreference("termFormats", "no");
        config.setPreference("testOutputDir", System.getenv("CATALINA_HOME") +
                File.separator + "webapps" + File.separator + "sigma" +
                File.separator + "tests");
        config.setPreference("TPTPDisplay", "no");
        config.setPreference("tptpHomeDir", userHome);
        config.setPreference("TPTP", "yes");
        config.setPreference("typePrefix", "yes");
        config.setPreference("userBrowserLimit", "25");
        config.setPreference("vampire", vampireExec);
        config.setPreference("vampireExec", vampireExec);

        List<String> constituents = Arrays.asList(
                "english_format.kif",
                "domainEnglishFormat.kif",
                "ArabicCulture.kif",
                "Anatomy.kif",
                "arteries.kif",
                "Biography.kif",
                "Cars.kif",
                "Catalog.kif",
                "Communications.kif",
                "CountriesAndRegions.kif",
                "Dining.kif",
                "Economy.kif",
                "emotion.kif",
                "engineering.kif",
                "Facebook.kif",
                "FinancialOntology.kif",
                "Food.kif",
                "Geography.kif",
                "Government.kif",
                "Hotel.kif",
                "Justice.kif",
                "Languages.kif",
                "Law.kif",
                "Media.kif",
                "Medicine.kif",
                "Merge.kif",
                "Mid-level-ontology.kif",
                "MilitaryDevices.kif",
                "Military.kif",
                "MilitaryPersons.kif",
                "MilitaryProcesses.kif",
                "Music.kif",
                "naics.kif",
                "People.kif",
                "pictureList.kif",
                "pictureList-ImageNet.kif",
                "QoSontology.kif",
                "Sports.kif",
                "TransnationalIssues.kif",
                "Transportation.kif",
                "TransportDetail.kif",
                "UXExperimentalTerms.kif",
                "VirusProteinAndCellPart.kif",
                "Weather.kif",
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
