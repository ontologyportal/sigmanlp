package com.articulate.nlp;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.nlg.LanguageFormatter;
import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.WordNetUtilities;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static com.articulate.nlp.GenSimpTestData.*;

public class GenSimpTestDataTest extends IntegrationTestBase {

    public static LFeatures lfeat = null;
    public static GenSimpTestData gstd;

    /** *************************************************************
     */
    @BeforeClass
    public static void init() {

        System.out.println("GenSimpTestDataTest.init()");
        gstd = new GenSimpTestData();
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        GenSimpTestData.initNumbers();
        gstd.genProcTable();
        lfeat = new LFeatures(gstd);
        String fname = "test";
        FileWriter fweng;
        FileWriter fwlog;
        try {
            fweng = new FileWriter(fname + "-eng.txt");
            GenSimpTestData.englishFile = new PrintWriter(fweng);
            fwlog = new FileWriter(fname + "-log.txt");
            GenSimpTestData.logicFile = new PrintWriter(fwlog);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** ***************************************************************
     */
    public void testVerb(String term, boolean negated, int tense, String word,
                         boolean plural, String expected, LFeatures lfeat) {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("test: " + term);
        GenSimpTestData gstd = new GenSimpTestData();
        StringBuilder english = new StringBuilder();
        lfeat.testMode = true;
        lfeat.tense = tense;
        String v = gstd.verbForm(term,negated, word, plural, english, lfeat);
        System.out.println("testVerb(): verb form: " + v);
        if (!v.equalsIgnoreCase(expected)) {
            if (!v.replace("won't","will not").equalsIgnoreCase(expected)) {
                System.err.println("Error!: found " + v + " Expected: " + expected);
                assertTrue(v.equalsIgnoreCase(expected));
            }
            else
                System.out.println("Success");
        }
        else
            System.out.println("Success");
    }

    /** ***************************************************************
     */
    public void testNoun(String term, String word,
                         boolean plural, String expected, LFeatures lfeat) {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("test: " + term);
        GenSimpTestData gstd = new GenSimpTestData();
        StringBuilder english = new StringBuilder();
        AVPair avp = new AVPair();
        String v = gstd.nounFormFromTerm(term,avp,"");
        System.out.println("testNoun(): noun form: " + v);
        if (!v.toLowerCase().contains(expected)) {
            System.err.println("Error!: found " + v + " Expected: " + expected);
            assertTrue(v.equalsIgnoreCase(expected));
        }
        else
            System.out.println("Success");
    }

    /** ***************************************************************
     */
    public void testCapability(String proc, String role, String obj, boolean expected) {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("test: " + proc);
        GenSimpTestData gstd = new GenSimpTestData();
        StringBuilder english = new StringBuilder();
        AVPair avp = new AVPair();
        System.out.println("testCapability(): (proc,role,obj): " + proc + ", " + role + ", " + obj);
        if (capabilities.containsKey(proc))
            System.out.println("testCapability(): capabilities: " + capabilities.get(proc));
        else
            System.out.println("testCapability(): proc not found");
        boolean actual = gstd.checkCapabilities(proc,role,obj);
        if (actual != expected) {
            System.err.println("Error!: found " + actual + " Expected: " + expected);
            assertTrue(actual == expected);
        }
        else
            System.out.println("Success");
    }

    /** ***************************************************************
     */
    @Test
    public void testVerbs() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testVerbs()");
        String t = "Trespassing";
        List<String> synsets = WordNetUtilities.getEquivalentVerbSynsetsFromSUMO(t);
        System.out.println("testVerb(): equiv synsets size: " + synsets.size() + " for term: " + t);
        synsets = WordNetUtilities.getVerbSynsetsFromSUMO(t);
        System.out.println("testVerb(): synsets size: " + synsets.size() + " for term: " + t);
    }

    /** ***************************************************************
     */
    @Test public void testTrespass() { testVerb("Trespassing", false, PRESENT, "trespass", false, "Trespasses", lfeat); }
    @Test public void testTrespass2() { testVerb("Trespassing", false, PROGRESSIVE, "trespass", false, "is trespassing", lfeat); }
    @Test public void testBuying() {  testVerb("Buying",false, PRESENT, "buy", false, "Buys", lfeat);}
    @Test public void testBuying2() { testVerb("Buying",false, PAST, "buy", false, "Bought", lfeat);}
    @Test public void testWalking() { testVerb("Walking",false, PAST, "pad", false, "Padded", lfeat);}
    @Test public void testRequesting() { testVerb("Requesting",false, PRESENT, "wish", false, "Wishes", lfeat);}

    @Test public void testProcessYou() { lfeat.subj = "You";
        testVerb("Process",false, PRESENT, "process", false, "Process", lfeat);
        lfeat.subj = ""; }

    @Test public void testProcess() { lfeat.subj = "";
        testVerb("Process",false, PRESENT, "process", false, "Processes", lfeat);}
    @Test public void testLooking() { testVerb("Looking",false, PROGRESSIVE, "catch", false, "Is catching", lfeat);}
    @Test public void testSoccer() { testVerb("Soccer",false, PROGRESSIVE, "soccer", false, "Is playing soccer", lfeat);}

    @Test public void testListening() { testVerb("Listening",false, PAST, "hear", false, "Heard", lfeat);}
    @Test public void testApologizing() { testVerb("Apologizing",false, PROGRESSIVE, "apologize", false, "Is apologizing", lfeat);}
    @Test public void testIntentional() { testVerb("IntentionalProcess",false, PAST, "proceed", false, "Proceeded", lfeat);}
    @Test public void testSeeing() { testVerb("Seeing",false, PRESENT, "watch", false, "Watches", lfeat);}
    @Test public void testBegin() { testVerb("Process",false, PROGRESSIVE, "begin", false, "Is beginning", lfeat);}
    @Test public void testBegun() { testVerb("Process",false, PAST, "begin", false, "Begun", lfeat);}
    @Test public void testDistill() { testVerb("Distilling",false, PRESENT, "distill", false, "Distills", lfeat);}
    @Test public void testPunching() { testVerb("Punching",false, PRESENT, "punch", false, "Punches", lfeat);}
    @Test public void testGame() { testVerb("Game",false, PRESENT, "play", false, "Plays", lfeat);}
    @Test public void testFreezing() { testVerb("Freezing",false, PAST, "freeze", false, "Froze", lfeat);}
    // TODO: need exception to make this "John has frozen" rather than "John frozen"

    @Test public void testDivide() { testVerb("Separating",false, NOTIME, "divide", true, "Divides", lfeat);}
    @Test public void testBeckon() { testVerb("Waving",true, FUTUREPROG, "beckon", true, "will not be beckoning", lfeat);}
    @Test public void testHear() { testVerb("Hearing",false, PASTPROG, "hear", true, "were hearing", lfeat);}
    @Test public void testMelt() { testVerb("Melting",false, PASTPROG, "melt", true, "were melting", lfeat);}
    @Test public void testTaking() { testVerb("Driving",false, FUTURE, "take", true, "will take", lfeat);}
    @Test public void testWind() { testVerb("Wind",true, FUTURE, "blow", false, "will not blow", lfeat);}
    @Test public void testInterp() { testVerb("Interpreting",false, PROGRESSIVE, "rede", false, "is reding", lfeat);}

    /** ***************************************************************
     */
    @Ignore // it's randomly plural or singular which breaks the test
    @Test public void testMonkey() { testNoun("Monkey","monkey",true, "monkies", lfeat);}

    /** ***************************************************************
     */
    @Test public void testEat1() { testCapability("Eating","patient","ArtWork",false);}

    /** ***************************************************************
     */
    @Test public void testWading1() { testCapability("Wading","patient","Book",false);}

    /** ***************************************************************
     */
    @Test public void testChimney1() { testCapability("Eating","objectTransferred","Chimney",false);}

    /** ***************************************************************
     */
    @Test
    public void testSell() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testSell()");
        LFeatures lfeat = new LFeatures(gstd);
        lfeat.testMode = true;
        lfeat.subjName = "John";
        lfeat.subj = "Human";
        lfeat.verbType = "Selling";
        lfeat.verb = "sell";
        lfeat.verbSynset = "202242464";
        lfeat.tense = PRESENT;
        lfeat.directType = "Shoe";
        StringBuilder english = new StringBuilder();
        StringBuilder prop = new StringBuilder();
        gstd.genProc(english,prop,lfeat);
        if (english.toString().contains("sells a") || english.toString().contains("selling a") ||
                english.toString().contains("sell a") || english.toString().contains("sold a"))
            System.out.println("Success! : " + english);
        else {
            System.out.println("Fail : " + english);
            assertTrue(english.toString().contains("sells a"));
        }
    }

    /** ***************************************************************
     */
    @Test
    public void testTaste() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testTaste()");
        LFeatures lfeat = new LFeatures(gstd);
        lfeat.testMode = true;
        lfeat.subjName = "CollegeSenior";
        lfeat.subj = "Human";
        lfeat.verbType = "Tasting";
        lfeat.verb = "taste";
        lfeat.verbSynset = "202191546";
        lfeat.directType = "Building";
        lfeat.tense = FUTUREPROG;
        StringBuilder english = new StringBuilder();
        StringBuilder prop = new StringBuilder("(exists (?H ?P ?DO ?IO) (and ");
        gstd.genProc(english,prop,lfeat);
        if (StringUtil.emptyString(english.toString().trim()))
            System.out.println("Success! : " + english);
        else {
            System.out.println("Fail : '" + english + "'");
            assertTrue(StringUtil.emptyString(english.toString()));
        }
    }

    /** ***************************************************************
     */
    @Test
    public void testVoteFor() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testVoteFor()");
        LFeatures lfeat = new LFeatures(gstd);
        lfeat.testMode = true;
        lfeat.subjName = "John";
        lfeat.subj = "Human";
        lfeat.verbType = "Voting";
        lfeat.verb = "vote";
        lfeat.verbSynset = "202462580";
        lfeat.tense = PRESENT;
        StringBuilder english = new StringBuilder();
        StringBuilder prop = new StringBuilder("(exists (?H ?P ?DO ?IO) (and ");
        gstd.genProc(english,prop,lfeat);
        if (english.toString().toLowerCase().contains("vote for") || english.toString().toLowerCase().contains("voting for") ||
                english.toString().toLowerCase().contains("voted for") || english.toString().toLowerCase().contains("votes for"))
            System.out.println("Success! : " + english);
        else {
            System.out.println("Fail : " + english);
            assertTrue(english.toString().contains("vote for"));
        }
    }

    /** ***************************************************************
     */
    @Test
    public void testNLG() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testNLG()");
        String s = "(=> (and (valence ?REL ?NUMBER) (instance ?REL Predicate)) (forall (@ROW) (=> (?REL @ROW) (equal (ListLengthFn (ListFn @ROW)) ?NUMBER))))";
        String actual = GenUtils.toEnglish(s, kb);
        System.out.println("Strike:" + kb.getTermFormat("EnglishLanguage","BaseballStrike"));
        System.out.println("Strike2:" + kb.getTermFormatMap("EnglishLanguage").get("BaseballStrike"));
        System.out.println("Strike3:" + LanguageFormatter.translateWord(kb.getTermFormatMap("EnglishLanguage"),"BaseballStrike"));
        System.out.println(actual);
    }

    /** ***************************************************************
     */
    @Test
    public void testLFeatures() {

        System.out.println("GenSimpTestData.testLFeatures()");
        //System.out.println("testLFeatures(): objects: " + lfeat.objects.terms);
    }

    /** ***************************************************************
     */
    @Test
    public void testGiving() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testGiving()");
        String t = "Giving";
        List<String> synsets = WordNetUtilities.getEquivalentVerbSynsetsFromSUMO(t);
        System.out.println("testGiving(): equiv synsets size: " + synsets.size() + " for term: " + t);
        synsets = WordNetUtilities.getVerbSynsetsFromSUMO(t);
        System.out.println("testGiving(): synsets size: " + synsets.size() + " for term: " + t);
    }

    /** ***************************************************************
     */
    @Test
    public void testToy() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testToy()");
        String word = "toy";
        lfeat.tense = PAST;
        StringBuilder english = new StringBuilder();
        GenSimpTestData gstd = new GenSimpTestData();
        System.out.println("testToy(): past tense Game/toy: " + gstd.verbForm("Game",false,word,false, english,lfeat));
        word = "soccer";
        System.out.println("testToy(): past tense Soccer/soccer: " + gstd.verbForm("Soccer",false,word,false, english,lfeat));
        lfeat.tense = PRESENT;
        System.out.println("testToy(): present tense Game/toy: " + gstd.verbForm("Game",false,word,false, english,lfeat));
        word = "soccer";
        System.out.println("testToy(): present tense Soccer/soccer: " + gstd.verbForm("Soccer",false,word,false, english,lfeat));
        word = "walking";
        System.out.println("testToy(): present tense Walking/walking: " + gstd.verbForm("Walking",false,word,false, english,lfeat));
    }

    /** ***************************************************************
     */
    @Test
    public void testPutting() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testPutting()");
        String t = "Putting";
        List<String> synsets = WordNetUtilities.getEquivalentVerbSynsetsFromSUMO(t);
        System.out.println("testPutting(): equiv synsets size: " + synsets.size() + " for term: " + t);

        synsets = WordNetUtilities.getVerbSynsetsFromSUMO(t);
        System.out.println("testPutting(): synsets size: " + synsets.size() + " for term: " + t);
    }

    /** ***************************************************************
     */
    @Test
    public void testTypes() {

        System.out.println();
        System.out.println("======================= ");
        System.out.println("GenSimpTestData.testTypes()");
        String t = "Object";
        Set<String> hinsts = kb.kbCache.getInstancesForType(t);
        //System.out.println(hinsts);
        System.out.println("Object has instance BinaryPredicate: " + hinsts.contains("BinaryPredicate"));
        System.out.println("signature of 'half': " + kb.kbCache.getSignature("half"));
    }

}
