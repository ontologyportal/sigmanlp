package com.articulate.nlp;

import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.parsing.Configuration;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/****************************************************************
 * Base class for integration tests that require a SUMO KB.
 *
 * Important: these NLP tests expect a small deterministic SUMO load,
 * not the user's local ~/.sigmakee/KBs/config.xml and not full SUMO.
 */
public class IntegrationTestBase extends SigmaTestBase {

    private static final Class CLASS = IntegrationTestBase.class;

    /****************************************************************
     * File object pointing to this test's resources directory.
     */
    public static final File RESOURCES_FILE;

    static Long totalKbMgrInitTime = Long.MAX_VALUE;

    static {

        URI uri = null;
        try {
            uri = new URI("file:" + System.getenv("ONTOLOGYPORTAL_GIT") +
                    "/sigmanlp/src/test/integration/java/resources");
            System.out.println("Integration test base: " + uri);
        }
        catch (URISyntaxException e) {
            System.err.println("IntegrationTestBase uri:" + uri);
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        RESOURCES_FILE = new File(uri);
    }

    /****************************************************************/
    @BeforeClass
    public static void setup() throws IOException {

        long startTime = System.currentTimeMillis();
        System.out.println("IntegrationTestBase.setup(): using in-memory NLP integration configuration.");
        System.out.println("SIGMA_HOME = " + System.getenv("SIGMA_HOME"));
        System.out.println("KB_PATH = " + KB_PATH);
        System.out.println("IntegrationTestBase.setup(): KBmanager.initialized before setup = " + KBmanager.initialized);
        // KBmanager.configuration = buildIntegrationConfiguration();
        // if (!KBmanager.initialized) {
        //     System.out.println("IntegrationTestBase.setup(): KBmanager was already initialized; clearing SUMO before deterministic integration load.");
        //     KBmanager.getMgr().kbs.remove("SUMO");
        //     KBmanager.initialized = false;
        // }
        KBmanager.getMgr().initializeOnce(buildIntegrationConfiguration());
        kb = KBmanager.getMgr().getKB("SUMO");
        if (kb == null) throw new IllegalStateException("SUMO KB is not initialized.");
        checkConfiguration();
        long endTime = System.currentTimeMillis();
        if (IntegrationTestBase.totalKbMgrInitTime == Long.MAX_VALUE) IntegrationTestBase.totalKbMgrInitTime = endTime - startTime;
    }

    /****************************************************************
     * Build the small deterministic config expected by NLP integration tests.
     */
    private static Configuration buildIntegrationConfiguration() {

        String sigmaHome = System.getenv("SIGMA_HOME");
        if (sigmaHome == null || sigmaHome.isEmpty()) sigmaHome = System.getProperty("user.home") + File.separator + ".sigmakee";
        String kbDir = KB_PATH;
        String userHome = System.getProperty("user.home");
        String catalinaHome = System.getenv("CATALINA_HOME");
        if (catalinaHome == null || catalinaHome.isEmpty()) catalinaHome = userHome;
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
        String leoExec = new File("/usr/local/bin/leo3").canExecute()
                ? "/usr/local/bin/leo3"
                : userHome + File.separator + "Programs" + File.separator + "Leo-III" +
                File.separator + "bin" + File.separator + "leo3";
        String tptpExec = new File("/usr/local/bin/tptp4X").canExecute()
                ? "/usr/local/bin/tptp4X"
                : userHome + File.separator + "workspace" + File.separator + "TPTP4X" +
                File.separator + "tptp4X";
        config.setPreference("adminBrowserLimit", "200");
        config.setPreference("baseDir", sigmaHome);
        config.setPreference("cache", "no");
        config.setPreference("cacheDisjoint", "false");
        config.setPreference("cwa", "false");
        config.setPreference("eproverExec", eproverExec);
        config.setPreference("vampireExec", vampireExec);
        config.setPreference("leoExec", leoExec);
        config.setPreference("tptpExec", tptpExec);
        config.setPreference("jeditExec", "/usr/share/jedit/jedit.jar");
        config.setPreference("eprover", eproverExec);
        config.setPreference("vampire", vampireExec);
        config.setPreference("leoExecutable", leoExec);
        config.setPreference("jedit", "/usr/share/jedit/jedit");
        config.setPreference("graphDir", sigmaHome + File.separator + "graph");
        config.setPreference("graphVizDir", "/usr/bin/dot");
        config.setPreference("graphVizExec", "/usr/bin/dot");
        config.setPreference("hostname", "localhost");
        config.setPreference("https", "false");
        config.setPreference("inferenceTestDir", kbDir + File.separator + "tests");
        config.setPreference("kbDir", kbDir);
        config.setPreference("loadFresh", "true");
        config.setPreference("loadLexicons", "true");
        config.setPreference("logDir", sigmaHome + File.separator + "logs");
        config.setPreference("logLevel", "warning");
        config.setPreference("maxPredicateArity", "6");
        config.setPreference("port", "8080");
        config.setPreference("termFormats", "no");
        config.setPreference("typePrefix", "yes");
        config.setPreference("userBrowserLimit", "25");
        config.setPreference("verbnetDir", "");
        config.setPreference("ollamaHost", "http://127.0.0.1:11434");
        config.setPreference("showCachedFormulas", "no");
        config.setPreference("showcached", "no");
        config.setPreference("systemsDir", userHome);
        config.setPreference("isAws", "false");
        config.setPreference("sumokbname", "SUMO");
        config.setPreference("testOutputDir", catalinaHome + File.separator + "webapps" + File.separator + "sigma" + File.separator + "tests");
        config.setPreference("TPTPDisplay", "no");
        config.setPreference("tptpHomeDir", userHome);
        config.setPreference("TPTP", "yes");
        List<String> constituents = Arrays.asList(
                "english_format.kif",
                "domainEnglishFormat.kif",
                "Merge.kif",
                "Mid-level-ontology.kif"
        );
        config.setKbConstituentList("SUMO", constituents);
        System.out.println("IntegrationTestBase.setup(): SUMO constituents = " + constituents);
        return config;
    }

    /****************************************************************
     * Undo all parts of the state that have anything to do with user assertions made during inference.
     */
    public static void resetAllForInference() throws IOException {

        kb = KBmanager.getMgr().getKB("SUMO");
        if (kb == null) throw new IllegalStateException("SUMO KB is not initialized.");
        kb.deleteUserAssertions();
        KBmanager.getMgr().kbs.put("SUMO", kb);
        File userAssertionsFile = new File(KB_PATH, "SUMO" + KB._userAssertionsString);
        deleteAndRecreateIfPresent(userAssertionsFile);
        File tptpAssertionsFile = new File(userAssertionsFile.getAbsolutePath().replace(".kif", ".tptp"));
        deleteAndRecreateIfPresent(tptpAssertionsFile);
    }

    /****************************************************************/
    private static void deleteAndRecreateIfPresent(File file) throws IOException {

        if (file.exists()) {
            if (!file.delete()) System.err.println("WARN resetAllForInference(): failed to delete " + file.getAbsolutePath());
            else {
                file.createNewFile();
                file.deleteOnExit();
            }
        }
    }
}