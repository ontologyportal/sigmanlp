package com.articulate.nlp;

import com.articulate.sigma.*;
import com.articulate.sigma.nlg.NLGUtils;
import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.utils.PairMap;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.wordNet.WSD;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import com.articulate.nlp.corpora.COCA;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ***************************************************************
 * This code generates language-logic pairs designed for training
 * a machine learning system.  Several approaches are used
 * - instantiate relations with arguments of appropriate types
 *   and then generate NL paraphrases from them
 * - run through all formulas in SUMO and generate NL paraphrases
 * - build up sentences and logic expressions compositionally
 *
 * The compositional generation is potentially the most comprehensive.
 * It consists of building ever more complex statements that wrap or
 * extend simpler statements.  Currently, this means starting with
 * a simple subject-verb-object construction and adding:
 * - indirect objects
 * - tenses for the verbs
 * - modals
 * - epistemics and authorship
 * - counts and measures of things
 * - social and job roles as well as names of people
 * - negations
 */

public class GenSimpTestData {

    public static boolean debug = true;
    public static KB kb;
    public static KBLite kbLite;
    public static boolean printFrame = false;
    public static final boolean allowImperatives = false;
    public static PrintWriter pw = null;

    public static final int loopMax = 3; // how many features at each level of linguistic composition
    public static final int attMax = 3;
    public static final int modalMax = 3;
    public static final int humanMax = 3; // separate constant to limit number of human names
    public static final int freqLimit = 3; // SUMO terms used in a statement must have an equivalent
                                           // synset with a frequency of at least freqLimit

    public static final Random rand = new Random();

    public static final List<Word> attitudes = new ArrayList<>();
    public static final Set<String> suppress = new HashSet<>( // forms to suppress, usually for testing
            Arrays.asList());

    public static PrintWriter englishFile = null; //generated English sentences
    public static PrintWriter logicFile = null;   //generated logic sentences, one per line,
                                                  // NL/logic should be on same line in the different files
    public static PrintWriter frameFile = null; // LFeatures for the current sentence, to support future processing

    public static long sentCount = 0;
    public static long sentMax = 10000000;
    public static boolean startOfSentence = true;
    public static LFeatureSets lfeatsets;
    public static List<String> numbers = new ArrayList<>();
    public static List<String> requests = new ArrayList<>(); // polite phrase at start of sentence
    public static List<String> endings = new ArrayList<>(); // polite phrase at end of sentence
    public static List<String> others = new ArrayList<>(); // when next noun is same as a previous one
    public static Map<String,String> prepPhrase = new HashMap<>();

    // verb and noun keys with values that are the frequency of coocurence with a given
    // adjective or adverb
    public Map<String, Map<Integer,Set<String>>> freqVerbs = new HashMap<>();
    public Map<String, Map<Integer,Set<String>>> freqNouns = new HashMap<>();
    public static COCA coca = new COCA();


    /** ***************************************************************
     */
    public GenSimpTestData() {

        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        initGSTD();
    }

    public GenSimpTestData(boolean useKBLite) {

        if (useKBLite) {
            kbLite = new KBLite("SUMO");
            KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
            WordNet.initOnce();
        } else {
            KBmanager.getMgr().initializeOnce();
            kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        }
        initGSTD();
    }

    private void initGSTD() {

        lfeatsets = new LFeatureSets(kbLite);
        initNumbers();
        initRequests();
        initAttitudes();
        initOthers();
        initPrepPhrase();
        initEndings();
        lfeatsets.genProcTable();
    }

    /** ***************************************************************
     * estimate the number of sentences that will be produced
     */
    public static void initNumbers() {

        numbers.add("zero");
        numbers.add("one");
        numbers.add("two");
        numbers.add("three");
        numbers.add("four");
        numbers.add("five");
        numbers.add("six");
        numbers.add("seven");
        numbers.add("eight");
        numbers.add("nine");
        numbers.add("ten");
    }

    /** ***************************************************************
     * Politeness wrappers for imperatives
     */
    public static void initRequests() {

        requests.add("Could you please ");
        requests.add("Can you please ");
        requests.add("Would you please ");
        requests.add("Could you ");
        requests.add("Would you ");
        requests.add("Please ");
    }

    /** ***************************************************************
     * Politeness wrappers for imperatives
     */
    public static void initEndings() {

        endings.add("please");
        endings.add("for me please");
        endings.add("for me");
    }

    /** ***************************************************************
     * Politeness wrappers for imperatives
     */
    public static void initOthers() {

        others.add("another ");
        others.add("a different ");
        others.add("the other ");
    }

    /** ***************************************************************
     * Politeness wrappers for imperatives
     */
    public static void initPrepPhrase() {

        prepPhrase.put("vote","for ");
        prepPhrase.put("search","for ");
        prepPhrase.put("partake","of ");
        prepPhrase.put("dreaming","of "); // TODO up
        prepPhrase.put("fall","off of "); // TODO or on, onto, under, outside, inside...
        prepPhrase.put("sing","about "); // TODO for, to, with
        prepPhrase.put("pull","on "); // TODO pull at, pull over, pull out, or just pull a vehicle
        prepPhrase.put("dream","of "); // TODO about
        prepPhrase.put("tell","about "); // TODO of, someone about
        prepPhrase.put("act","as ");
        prepPhrase.put("pretend","to be ");
        prepPhrase.put("act","as ");
        prepPhrase.put("nod","to ");
        prepPhrase.put("hunt","for "); // must have a direct object
        prepPhrase.put("read","about ");  // TODO but also "read a book"
        prepPhrase.put("wading","in ");
    }

    /** ***************************************************************
     * generate new SUMO termFormat statements for constants in a file
     */
    public static void genTermFormatFromNames(String fname) {

        try {
            File constituent = new File(fname);
            String canonicalPath = constituent.getCanonicalPath();
            KIF kif = new KIF(canonicalPath);
            kif.setParseMode(KIF.RELAXED_PARSE_MODE);
            kif.readFile(fname);
            String t, s;
            for (Formula f : kif.formulaMap.values()) {
                if (f.car().equals("instance")) {
                    t = f.getStringArgument(1);
                    s = StringUtil.camelCaseToSep(t);
                    System.out.println("(termFormat EnglishLanguage " + t + " \"" + s + "\")");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** ***************************************************************
     * generate new SUMO termFormat and instance statements for names
     */
    public static void generateAllHumans() {

        String g;
        for (String firstName : lfeatsets.genders.keySet()) {
            g = lfeatsets.genders.get(firstName);
            if (firstName != null) {
                String gender = "Male";
                if (g.toUpperCase().equals("F"))
                    gender = "Female";
                System.out.println("(instance " + firstName + " Human)");
                System.out.println("(attribute " + firstName + " " + gender + ")");
                System.out.println("(names \"" + firstName + "\" " + firstName + ")");
            }
        }
    }

    /** ***************************************************************
     * generate new SUMO statements for names
     */
    public String genSUMOForHuman(String name, String var) {

        StringBuilder sb = new StringBuilder();
        if (name != null) {
            String gender = "Male";
            String g = lfeatsets.genders.get(name);
            if (g.equalsIgnoreCase("F"))
                gender = "Female";
            //sb.append("(instance " + var + " Human) ");
            sb.append("(attribute ").append(var).append(" ").append(gender).append(") ");
            sb.append("(names \"").append(name).append("\" ").append(var).append(") ");
        }
        return sb.toString();
    }

    /** ***************************************************************
     * generate missing SUMO termFormat statements
     */
    public static void genMissingTermFormats() {

        System.out.println("GenSimpTestData.genMissingTermFormats(): start");
        List<Formula> res;
        for (String s : kbLite.terms) {
            res = kbLite.askWithRestriction(0,"termFormat",2,s);
            if (res == null || res.isEmpty()) {
                System.out.println("ERROR IN GenSimpTestData.genMissingTermFormats: MISSING TERM FORMAT FOR: " + s);
                boolean inst = false;
                if (kbLite.isInstance(s))
                    inst = true;
                String news = StringUtil.camelCaseToSep(s,true,inst);
                System.out.println("(termFormat EnglishLanguage " + s + " \"" + news + "\")");
            }
        }
    }

    /** ***************************************************************
     * print all SUMO axioms in the current knowledge base along with
     * their natural language paraphrases
     */
    public static void allAxioms() {

        System.out.println("GenSimpTestData.allAxioms()");
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        String form;
        for (Formula f : kb.formulaMap.values()) {
            form = f.getFormula();
            if (!StringUtil.emptyString(form) && !form.contains("\"") &&
                    !Formula.DOC_PREDICATES.contains(f.car())) {
                logicFile.println(form.replace("\n", "").replace("\r", ""));
                String actual = GenUtils.toEnglish(form, kb);
                englishFile.println(StringUtil.filterHtml(actual));
            }
        }
    }

    /** ***************************************************************
     */
    public static void progressPrint() {

        if ((sentCount % 100) != 0) return;
        if (!debug) System.out.print("\r\33[2K");
        double value = ((double) sentCount / (double) sentMax) * 100;
        System.out.print(String.format("%.2f", value));
        System.out.print("% complete. ");
        System.out.print(sentCount + " of total " + sentMax);
        if (debug) System.out.println();
    }


    /** ***************************************************************
     */
    public class Preposition {
        public String procType = null;
        public String prep = null;
        public String noun = null;
        @Override
        public String toString() {
            return procType + ": " + prep + ": " + noun;
        }
    }

    /** ***************************************************************
     * @param term is a SUMO term
     * get a random term format if there is more than one
     *
     */
    public String getTermFormat(String term) {

        ArrayList<Formula> forms = (ArrayList) kb.askWithTwoRestrictions(0,"termFormat",1,"EnglishLanguage",2,term);
        if (forms == null || forms.size() == 0)
            return null;
        int rint = rand.nextInt(forms.size());
        return StringUtil.removeEnclosingQuotes(forms.get(rint).getStringArgument(3));
    }

    /** ***************************************************************
     * Information about a process
     */
    public class ProcInfo {
        public String term = null;
        public String synset = null; // the synset equivalent to the SUMO term
        public Map<String,String> words = new HashMap<>(); // word is the key, value is transitivity string
        public String noun = null;
    }

    /** ***************************************************************
     * @return objects
     */
    public void addArguments(Collection<String> col, Collection<Preposition> objects) {

        Preposition p;
        for (String s : col) {
            p = this.new Preposition();
            p.prep = "";
            p.procType = s;
            objects.add(p);
        }
    }

    /** ***************************************************************
     * Initialize the grammatical forms of propositional attitudes
     */
    public void initAttitudes() {

        for (int i = 0; i < 50; i++)
            attitudes.add(new Word("None","","",""));
        if (!suppress.contains("attitude")) {
            attitudes.add(new Word("knows", "know", "knows", "knew"));
            attitudes.add(new Word("believes", "believe", "believes", "believed"));
            attitudes.add(new Word("says", "say", "says", "said"));
            attitudes.add(new Word("desires", "desire", "desires", "desired"));
        }
    }


    /** ***************************************************************
     * @return modifications to the parameter as a side effect
     */
    public void constrainTerms(Collection<String> terms) {

        if (debug) System.out.println("constrainTerms(): ");
        Set<String> newProcList = new HashSet<>(terms);
        terms.clear();
        List<String> synsets;
        int freq;
        for (String proc : newProcList) {
            if (debug) System.out.println("constrainTerms(): proc list size: " + newProcList.size() + " for terms: " + terms);
            synsets = WordNetUtilities.getEquivalentSynsetsFromSUMO(proc);
            int maxInt = 0;
            for (String s : synsets) {
                if (WordNet.wn.senseFrequencies.containsKey(s)) {
                    freq = WordNet.wn.senseFrequencies.get(s);
                    if (freq > maxInt)
                        maxInt = freq;
                }
            }
            if (maxInt > freqLimit)
                terms.add(proc);
        }
    }

    /** ***************************************************************
     */
    public class Word {
        public String term = null;
        public String root = null;
        public String present = null;
        public String part = null;
        public String trans = null; // transitivity string
        public String derivedFromParentClass = ""; // is the term a subclass of the authored relationship

        public Word(String t, String r, String pr, String pa) {
            term = t; root = r; present = pr; part = pa;
        }
    }


    /** ***************************************************************
     * Create action sentences from a subject, preposition, direct object,
     * preposition and indirect object.  Indirect object and its preposition
     * can be left out.  Actions will eventually be past and future tense or
     * wrapped in modals.
     */
    public void runGenSentence() {

        System.out.println("GenSimpTestData.runGenSentence(): start");

        StringBuilder english, prop;
        LFeatures lfeat;

        while (sentCount < sentMax) {
            english = new StringBuilder();
            prop = new StringBuilder();
            lfeat = new LFeatures();
            if (!suppress.contains("attitude")) {
                genAttitudes(english, prop, lfeat);
            }
            if (!suppress.contains("modal")){
                genWithModals(english, prop, lfeat);
            }
            int tryCount = 0;
            StringBuilder prop1, english1;
            do {
                english1 = new StringBuilder(english);
                prop1 = new StringBuilder(prop);
                getVerb(lfeat,false);
                progressPrint();
                genProc(english1, prop1, lfeat);
            } while (tryCount++ < 10 && prop1.toString().equals(""));
        }
    }


    /** ***************************************************************
     * @return the correct version of the copula for tense, number and
     * negation including randomized contractions of negation
     */
    public String conjCopula(boolean negated, int time, boolean plural) {

        String cop = "is";
        String neg = "";
        boolean cont = rand.nextBoolean() && negated;
        switch (time) {
            case LFeatures.NOTIME: break;
            case LFeatures.PAST: cop = "was"; if (plural) cop = "were"; if (negated) cop = cop + " not"; break;
            case LFeatures.PASTPROG:  cop = "was"; if (plural) cop = "were"; if (negated) cop = cop + " not"; break;
            case LFeatures.PRESENT:  cop = "is"; if (plural) cop = "are"; if (negated) cop = cop + " not"; break;
            case LFeatures.PROGRESSIVE:  cop = "is"; if (plural) cop = "are"; if (negated) cop = cop + " not"; break;
            case LFeatures.FUTURE: cop = "will be"; if (negated) cop = "will not be"; break;
            case LFeatures.FUTUREPROG: cop = "will be"; if (negated) cop = "will not be"; break;
        }
        if (cont && time < LFeatures.FUTURE)
            return cop.replace(" not","n't ");
        if (cont)
            return cop.replace("will not","won't") + " ";
        return cop + " ";
    }

    /** ***************************************************************
     * Handle the auxilliary construction of "play X" when X is a Game
     * @param term is a SUMO term
     */
    public String verbForm(String term, boolean negated, String word, boolean plural, StringBuilder english,
                           LFeatures lfeat) {

        String adverb = "";
        if (!"".equals(lfeat.adverb)) {
            adverb = lfeat.adverb + " ";
        }
        if ((!adverb.isEmpty()) && (lfeat.subj != null && lfeat.subj.equals("You"))) {
            lfeat.verb = lfeat.verb.toLowerCase();
            adverb = Character.toUpperCase(adverb.charAt(0)) + adverb.substring(1);
        }
        String result = "";
        if (debug) System.out.println("verbForm(): (term,word): " + term + ", " + word);
        if (debug) System.out.println("verbForm(): tense: " + lfeat.printTense());
        if (debug) System.out.println("verbForm(): plural: " + plural);
        if (debug) System.out.println("verbForm(): negated: " + negated);
        if (debug) System.out.println("verbForm(): subj: " + lfeat.subj);
        if (lfeat == null) {
            System.out.println("Error! verbForm(): null lfeat");
            return "";
        }
        if (debug) System.out.println("verbForm(): subj: " + lfeat.subj);
        if (!StringUtil.emptyString(lfeat.subj) && lfeat.subj.equals("You")) {
            if (debug) System.out.println("verbForm(): using imperative tense for 'you'");
            lfeat.tense = LFeatures.IMPERATIVE;
        }
        String root = "";
        String nounForm = "";
        if (kbLite.isSubclass(term,"Game") && !term.equals("Game")) {
            nounForm = " " + word;
            root = "play";
        }
        if (word == null) {
            System.out.println("verbForm(): null input or no term format for " + term);
            return null;
        }
        String neg = "";
        if (negated) {
            if (lfeat.subj != null && lfeat.subj.equals("You")) {
                if (english.toString().contains("should")) {
                    if (rand.nextBoolean())
                        neg = "not ";
                    else {
                        neg = "n't ";
                        english.deleteCharAt(english.length()-1);
                    }
                }
                else if (rand.nextBoolean())
                    neg = "don't ";
                else
                    neg = "do not ";
            }
            else {
                switch (lfeat.tense) {
                    case LFeatures.PAST:
                        neg = "has not ";
                        break;
                    case LFeatures.FUTURE:
                        neg = "will not ";
                        break;
                    default:
                        neg = "not ";
                        break;
                }
            }
        }
        if (debug) System.out.println("verbForm(): neg: " + neg);
        if (!StringUtil.emptyString(lfeat.subj) && lfeat.subj.equals("You"))
            return capital(neg + word) + nounForm;
        if ("".equals(nounForm)) // only modify if we don't use an auxilliary
            root = WordNet.wn.verbRootForm(word,word.toLowerCase());
        if (root == null)
            root = word;

        if (debug) System.out.println("verbForm(): word: " + word);
        if (debug) System.out.println("verbForm(): root: " + root);
        if (debug) System.out.println("verbForm(): nounForm: " + nounForm);
        String copula = conjCopula(negated,lfeat.tense,plural);
        if (english.toString().endsWith("is ") && copula.startsWith("is"))
            copula = copula.substring(2);
        if (copula.endsWith("won't"))
            copula = copula + " ";
        if (lfeat.isProgressive()) {
            if (WordNet.wn.exceptVerbProgHash.containsKey(root)) {
                word = copula + WordNet.wn.exceptVerbProgHash.get(root);
                return word + nounForm;
            }
            else {
                if (root.endsWith("e") && !root.endsWith("ee"))
                    result = copula + root.substring(0,root.length()-1) + "ing" + nounForm;
                else if (root.matches(".*[aeiou][aeiou][bcdfglmnprstvz]$") || root.matches(".*[bcdfglmnprstvz]er$"))
                    result = copula + root + "ing" + nounForm;
                else if (root.matches(".*[aeiou][bcdfglmnprstvz]$"))
                    result = copula + root + root.substring(root.length()-1) + "ing" + nounForm;
                else
                    result = copula + root + "ing" + nounForm;
                if (debug) System.out.println("verbForm(): result: " + result);
                return result;
            }
        }
        if (lfeat.isPast()) {
            if (WordNet.wn.exceptionVerbPastHash.containsKey(root))
                word = WordNet.wn.exceptionVerbPastHash.get(root);
            else {
                if (root.endsWith("e"))
                    word = root + "d";
                else
                  word = root + "ed";
            }
            return neg + word + nounForm;
        }
        String es = "s";
        if (plural)
            es = "";
        if (lfeat.isPresent() || lfeat.noTense()) {
            if (root.endsWith("ch") || root.endsWith("sh") || root.endsWith("ss") || root.equals("go"))
                es = "es";
            if (root.endsWith("e"))
                es = "s";
            if (negated) {
                return "doesn't " + root + nounForm;
            }
            else {
                if (root.endsWith("y") && !root.matches(".*[aeiou]y$"))
                    return capital(root.substring(0, root.length() - 1) + "ies");
                return capital(root) + es + nounForm;
            }
        }
        if (lfeat.isFuture()) {
            if (neg.startsWith("do"))
                neg = "not ";
            if (rand.nextBoolean() && !StringUtil.emptyString(neg))
                return "won't " + root + nounForm;
            if (!negated)
                return "will " + root + nounForm;
            return neg + root + nounForm;
        }
        if (root.endsWith("y") && !root.matches(".*[aeiou]y$") && !negated && "".equals(nounForm))
            return capital(root.substring(0,root.length()-1) + "ies");
        if (!lfeat.noTense())
            System.out.println("Error in verbForm(): time is unallowed value: " + lfeat.tense);
        if (negated)
            return "doesn't " + root + nounForm;
        else {
            if ("".equals(nounForm))
                return capital(root) + es;
            else
                return capital(root) + nounForm + es;
        }
    }

    /** ***************************************************************
     * @param term is a SUMO term
     * @return English in the attribute and SUMO in the value
     */
    public AVPair getQuantity(String term) {

        if (debug) System.out.println("getQuantity(): term: " + term);
        float val = rand.nextFloat() * 20;
        List<Formula> forms = kbLite.askWithRestriction(0,"roomTempState",1,term);
        if (debug) System.out.println("getQuantity(): forms: " + forms);
        if (forms == null || forms.isEmpty())
            return null;
        Formula f = forms.get(0);
        String state = f.getStringArgument(2);
        if (StringUtil.emptyString(state))
            return null;
        String unitType = "UnitOfMass";
        if (state.equals("Liquid") || state.equals("Gas"))
            unitType = "UnitOfVolume";
        if (debug) System.out.println("getQuantity(): unitType: " + unitType);
        Set<String> units = kbLite.getAllInstances(unitType);
        if (debug) System.out.println("getQuantity(): units: " + units);
        String unit = (String) units.toArray()[rand.nextInt(units.size())];
        String unitEng = kbLite.getTermFormat("EnglishLanguage",unit);
        if (WordNet.wn.exceptionNounPluralHash.containsKey(unitEng))
            unitEng = WordNet.wn.exceptionNounPluralHash.get(unitEng);
        else
            unitEng = unitEng + "s";
        int decimalPlaces = rand.nextInt(5);
        String english = String.format("%." + Integer.toString(decimalPlaces) + "f", val) + " " + unitEng + " ";
        String sumo = "(measure ?DO (MeasureFn " + Float.toString(val) + " " + unit + ")) ";
        AVPair result = new AVPair(english,sumo);
        if (debug) System.out.println("getQuantity(): term: " + term);
        return result;
    }

    /** ***************************************************************
     * @param term is a SUMO term
     * @param avp is a hack to return whether there was a plural, and its count
     */
    public String nounFormFromTerm(String term, AVPair avp, String other) {

        if (term.startsWith("UNK"))
            return term;
        String word = kbLite.getTermFormat(term);
        if (word == null) {
            System.out.println("nounFormFromTerm(): no term format for " + term);
            return null;
        }
        if (kbLite.isInstance(term)) {
            if (kbLite.isInstanceOf(term,"Human"))
                return word;
            else {
                if (StringUtil.emptyString(other))
                    return capital("the ") + word;
                else
                    return capital(word);
            }

        }
        boolean subst = kbLite.isSubclass(term,"Substance");
        if (subst) {
            if (rand.nextBoolean()) {
                AVPair quant = getQuantity(term);
                if (quant == null)
                    return capital("some ") + word;
                else {
                    avp.attribute = quant.attribute;
                    avp.value = quant.value;
                    return capital(avp.attribute) + "of " + word;
                }
            }
            else
                return capital("some ") + word;
        }
        String number = "";
        if (biasedBoolean(1,5) && kbLite.isSubclass(term,"CorpuscularObject")) { // occasionally make this a plural or count
            int index = rand.nextInt(numbers.size());  // how many of the object
            avp.value = Integer.toString(index);
            if (rand.nextBoolean())
                number = numbers.get(index);  // sometimes spell out the number as a word...
            else
                number = Integer.toString(index);  // ...and sometimes just use the number
            String suffix = "";
            if (index != 1) {
                avp.attribute = "true";
                if (word.contains(" ")) { // handle multi-word nouns
                    String headword = word.substring(word.lastIndexOf(" ")+1);
                    if (debug) System.out.println("nounFormFromTerm(): headword: " + headword);
                    if (WordNet.wn.exceptionNounPluralHash.containsKey(headword)) {
                        String newheadword = WordNet.wn.exceptionNounPluralHash.get(headword);
                        word = word.replace(headword,newheadword);
                    }
                    else
                        word = word + "s";
                }
                else if (WordNet.wn.exceptionNounPluralHash.containsKey(word))
                    word = WordNet.wn.exceptionNounPluralHash.get(word);
                else if (word.endsWith("y")) {
                    if (word.endsWith("ey"))
                        word = word.substring(0, word.length() - 2) + "ies";
                    else
                        word = word.substring(0, word.length() - 1) + "ies";
                }
                else if (word.endsWith("ss"))
                    word = word + "es";
                else
                    word = word + "s";
            }
            else
                avp.attribute = "false";
        }
        else
            avp.attribute = "false";
        if (avp.attribute.equals("true"))
            return capital(number) + " " + word;
        if (word.matches("^[aeiouAEIOH].*")) // pronouncing "U" alone is like a consonant, H like a vowel
            return capital("an ") + word;
        return capital("a ") + word;
    }

    /** ***************************************************************
     */
    public static String capital(String s) {

        if (debug) System.out.println("capital(): startOfSentence: " + startOfSentence);
        if (debug) System.out.println("capital(): s: " + s);
        if (StringUtil.emptyString(s))
            return s;
        if (!startOfSentence)
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** ***************************************************************
     */
    public static void capitalize(StringBuilder s) {

        s.replace(0,1,Character.toString(Character.toUpperCase(s.charAt(0))));
    }

    /** ***************************************************************
     * Generate a boolean true value randomly num out of max times.
     * So biasedBoolean(8,10) generates a true most of the time
     * (8 out of 10 times on average)
     */
    public static boolean biasedBoolean(int num, int max) {

        int val = rand.nextInt(max);
        return val < num;
    }

    /** ***************************************************************
     * Add SUMO content about a plural noun
     * @param prop is the formula to append to
     * @param term is the SUMO type of the noun
     * @param plural is the count of the plural as a String integer in the value field
     * @param var is the variable for the term in the formula
     */
    private static void addSUMOplural(StringBuilder prop, String term, AVPair plural, String var) {

        if (debug) System.out.println("addSUMOplural(): prop: " + prop);
        //prop.append("(instance " + var + " Collection) ");
        prop.append("(memberType ").append(var).append(" ").append(term).append(") ");
        prop.append("(memberCount ").append(var).append(" ").append(plural.value).append(") ");
    }

    /** ***************************************************************
     * Add SUMO content about a plural noun
     * @param prop is the formula to append to
     */
    private void addBodyPart(StringBuilder english, StringBuilder prop, LFeatures lfeat) {

        String bodyPart = WordPairFrequency.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "BodyPart");
        if (bodyPart == null)
            bodyPart = lfeatsets.bodyParts.getNext();
        AVPair plural = new AVPair();
        english.append(capital(nounFormFromTerm(bodyPart,plural,""))).append(" ");
        if (plural.attribute.equals("true"))
            addSUMOplural(prop,bodyPart,plural,"?O");
        else
            prop.append("(instance ?O ").append(bodyPart).append(") ");
        prop.append("(possesses ?H ?O) ");
    }

    /** ***************************************************************
     * Generate the subject of the sentence conforming to the verb frame
     * for a human
     */
    public void generateHumanSubject(StringBuilder english, StringBuilder prop,
                                     LFeatures lfeat) {

        if (debug) System.out.println("human subject for (prop,synset,word): " +
                prop + ", " + lfeat.verbSynset + ", " + lfeat.verb);
        if (debug) System.out.println("generateHumanSubject(): startOfSentence: " + startOfSentence);
        StringBuilder type = new StringBuilder();
        StringBuilder name = new StringBuilder();
        if (biasedBoolean(1,5) ) { // It's a question.
            lfeat.subj = "Who";
            lfeat.subjName = "";
            lfeat.question = true;
            english.delete(0,english.length());
            english.append(capital(lfeat.subj)).append(" ");
            startOfSentence = false;
            if (debug) System.out.println("generateHumanSubject(): question: " + english);
        }
        else if ((lfeat.attitude.equals("None") || lfeat.attitude.equals("says")) &&
                lfeat.modal.attribute.equals("None") && english.length() == 0 &&
                allowImperatives) {
            generateHuman(english, prop, true, "?H", type, name, lfeat);
            lfeat.subj = type.toString();
            lfeat.subjName = name.toString();
        }
        else {
            generateHuman(english, prop, false, "?H", type, name, lfeat);
            lfeat.subj = type.toString();
            lfeat.subjName = name.toString();
        }
        if (!lfeat.subj.equals("You"))
            startOfSentence = false;
        else {
            if (biasedBoolean(1,5)) {
                english.append("Please, ");
                startOfSentence = false;
            }
            else if (biasedBoolean(1,5)) {
                english.append("You should ");
                startOfSentence = false;
            }
        }
        if (lfeat.attitude.equals("says") && lfeat.subj.equals("You") && // remove "that" for says "You ..."
                english.toString().endsWith("that \"")) {
            english.delete(english.length() - 7, english.length());
            english.append(" \""); // restore space and quote
        }
        else if (kbLite.isInstanceOf(lfeat.subj,"SocialRole")) { // a plumber... etc
            if (lfeat.framePart.startsWith("Somebody's (body part)")) {
                if (lfeat.subj.equals("You"))
                    english.append(capital("your"));
                else {
                    english.deleteCharAt(english.length()-1); // delete trailing space
                    english.append("'s ");
                }
                addBodyPart(english,prop,lfeat);
            }
            else {
                // english.append(capital(nounFormFromTerm(lfeat.subj)) + " "); // already created in generateHuman
            }
        }
        else {
            if (lfeat.framePart.startsWith("Somebody's (body part)")) {
                // english.append(capital(lfeat.subj) + "'s ");
                english.append("'s ");
                addBodyPart(english,prop,lfeat);
            }
            else {
                if (!lfeat.subj.equals("You")) {  // if subj is you-understood, don't generate subject
                    //english.append(capital(lfeat.subj) + " ");
                    //prop.append("(instance ?H Human) ");
                    //prop.append("(names \"" + lfeat.subj + "\" ?H) ");
                }
            }
        }
        removeFrameSubject(lfeat);
        if (debug) System.out.println("generateHumanSubject(2): startOfSentence: " + startOfSentence);
    }

    /** ***************************************************************
     */
    public void removeFrameSubject(LFeatures lfeat) {

        if (lfeat.framePart.startsWith("Something is"))
            lfeat.framePart = lfeat.framePart.substring(13);
        else if (lfeat.framePart.startsWith("It is"))
            lfeat.framePart = lfeat.framePart.substring(5);
        else if (lfeat.framePart.startsWith("Something"))
            lfeat.framePart = lfeat.framePart.substring(10);
        else if (lfeat.framePart.startsWith("It"))
            lfeat.framePart = lfeat.framePart.substring(3);
        else if (lfeat.framePart.startsWith("Somebody's (body part)"))
            lfeat.framePart = lfeat.framePart.substring(23);
        else if (lfeat.framePart.startsWith("Somebody"))
            lfeat.framePart = lfeat.framePart.substring(9);
    }

    /** ***************************************************************
     * Generate the subject of the sentence conforming to the verb frame
     * for a thing
     */
    public void generateThingSubject(StringBuilder english, StringBuilder prop,
                                LFeatures lfeat) {

        if (debug) System.out.println("non-human subject for (prop,synset,word): " +
                prop + ", " + lfeat.verbSynset + ", " + lfeat.verb);
        if (biasedBoolean(4,5) && english.length() == 0) {
            lfeat.subj = "what";
            lfeat.question = true;
            english.append(capital(lfeat.subj)).append(" ");
            if (debug) System.out.println("generateThingSubject(): question: " + english);
        }
        else if (lfeat.framePart.startsWith("Something")) {
            // Thompson START
            String term = WordPairFrequency.getNounFromVerb(lfeatsets, lfeat);
            /*
            Original code:
            String term = lfeatsets.objects.getNext();
             */
            // Thompson END
            lfeat.subj = term;
            AVPair plural = new AVPair();
            english.append(capital(nounFormFromTerm(term,plural,""))).append(" ");
            if (plural.attribute.equals("true")) {
                addSUMOplural(prop, term, plural, "?H");
                lfeat.subjectPlural = true;
            }
            else
                prop.append("(instance ?H ").append(term).append(") ");
            if (lfeat.framePart.startsWith("Something is")) {
                english.append("is ");
            }
            else
                lfeat.framePart = lfeat.framePart.substring(10);
        }
        else {  // frame must be "It..."
            if (lfeat.framePart.startsWith("It is")) {
                english.append("It is ");
            }
            else {
                english.append("It ");
            }
        }
        removeFrameSubject(lfeat);
        startOfSentence = false;
    }

    /** ***************************************************************
     */
    public boolean questionWord(String q) {

        return q.equalsIgnoreCase("who") || q.equalsIgnoreCase("what") || q.equalsIgnoreCase("when did") ||
                q.equalsIgnoreCase("did") || q.equalsIgnoreCase("where did") || q.equalsIgnoreCase("why did");
    }

    /** ***************************************************************
     * Generate the subject of the sentence conforming to the verb frame
     */
    public void generateSubject(StringBuilder english, StringBuilder prop,
                                  LFeatures lfeat) {

        if (debug) System.out.println("generateSubject(): english: " + english);
        if (debug) System.out.println("generateSubject(): attitude: " + lfeat.attitude);
        if (debug) System.out.println("generateSubject(): modal: " + lfeat.modal);
        if (debug) System.out.println("generateSubject(): startOfSentence: " + startOfSentence);
        if (debug) System.out.println("generateSubject(): lfeat.framePart(1): " + lfeat.framePart);
        if (!StringUtil.emptyString(lfeat.subj)) {  // for testing, allow for a pre-set subject
            System.out.println("generateSubject(): non-empty subj " + lfeat.subj + " returning");
            english.append(lfeat.subjName).append(" ");
            removeFrameSubject(lfeat);
            return;
        }
        if (lfeat.framePart.startsWith("It") || lfeat.framePart.startsWith("Something")) {
            generateThingSubject(english, prop, lfeat);
        }
        else { // Somebody
            generateHumanSubject(english, prop, lfeat);
        }
        if (debug) System.out.println("generateSubject(): english: " + english);
        if (debug) System.out.println("generateSubject(): lfeat.framePart: " + lfeat.framePart);
        if (debug) System.out.println("generateSubject(): startOfSentence: " + startOfSentence);
    }

    /** ***************************************************************
     */
    public void generateVerb(boolean negated,StringBuilder english, StringBuilder prop,
                                String proc, String word, LFeatures lfeat) {

        if (debug) System.out.println("generateVerb(): word,proc,subj: " + word + ", " + proc + ", " + lfeat.subj);
        if (debug) System.out.println("generateVerb(): kb.isSubclass(proc,\"IntentionalProcess\"): " + kbLite.isSubclass(proc,"IntentionalProcess"));
        String verb = verbForm(proc,negated,word,lfeat.subjectPlural,english,lfeat);
        String adverb = "";
        String adverbSUMO = "";
        if (!"".equals(lfeat.adverb)) {
            adverb = lfeat.adverb + " ";
            adverbSUMO = WSD.getBestDefaultSUMOsense(lfeat.adverb,4);
            adverbSUMO = WordNetUtilities.getBareSUMOTerm(adverbSUMO);
        }
        if (lfeat.subj != null && !lfeat.subj.isEmpty() && lfeat.subj.equals("You")) {
            verb = verb.toLowerCase();

            if (adverb != null && !adverb.isEmpty()) {
                adverb = Character.toUpperCase(adverb.charAt(0)) + adverb.substring(1);
            } else {
                System.out.println("Error: adverb is null or empty");
            }
        }
        english.append(verb).append(" ");
        prop.append("(instance ?P ").append(proc).append(") ");
        if (!"".equals(lfeat.adverb)) {
            prop.append("(manner ?P ").append(adverbSUMO).append(") ");
        }
        if (lfeat.framePart.startsWith("It") ) {
            System.out.println("non-human subject for (prop,synset,word): " +
                    prop + ", " + lfeat.verbSynset + ", " + lfeat.verb);
        }
        else if (lfeat.framePart.startsWith("Something"))
            prop.append("(involvedInEvent ?P ?H) ");
        else if (lfeat.subj != null && lfeat.subj.equalsIgnoreCase("What") &&
                !kbLite.isSubclass(proc,"IntentionalProcess")) {
            prop.append("(involvedInEvent ?P ?H) ");
        }
        else if (lfeat.subj != null &&
                  (kbLite.isSubclass(lfeat.subj,"AutonomousAgent") ||
                   kbLite.isInstanceOf(lfeat.subj,"SocialRole") ||
                   lfeat.subj.equalsIgnoreCase("Who") ||
                   lfeat.subj.equals("You"))) {
            if (kbLite.isSubclass(proc,"IntentionalProcess"))
                prop.append("(agent ?P ?H) ");
            else if (kbLite.isSubclass(proc,"BiologicalProcess"))
                prop.append("(experiencer ?P ?H) ");
            else
                prop.append("(agent ?P ?H) ");
        }
        else {
            if (debug) System.out.println("ERROR generateVerb() non-Agent, non-Role subject " + lfeat.subj + " for action " + proc);
            prop.delete(0,prop.length());
            return;
        }
        // if (time == -1) do nothing extra
        if (lfeat.isPast())
            prop.append("(before (EndFn (WhenFn ?P)) Now) ");
        if (lfeat.isPresent())
            prop.append("(temporallyBetween (BeginFn (WhenFn ?P)) Now (EndFn (WhenFn ?P))) ");
        if (lfeat.isFuture())
            prop.append("(before Now (BeginFn (WhenFn ?P))) ");
        if (lfeat.framePart.startsWith("----s")) {
            if (lfeat.framePart.length() < 6)
                lfeat.framePart = "";
            else
                lfeat.framePart = lfeat.framePart.substring(6);
        }
        else if (lfeat.framePart.trim().startsWith("----ing")) {
            if (lfeat.framePart.trim().length() < 8)
                lfeat.framePart = "";
            else
                lfeat.framePart = lfeat.framePart.trim().substring(8);
        }
        else
            if (debug) System.out.println("Error in generateVerb(): bad format in frame: " + lfeat.framePart);
        if (debug) System.out.println("generateVerb(): english: " + english);
        if (debug) System.out.println("generateVerb(): prop: " + prop);
        if (debug) System.out.println("generateVerb(): lfeat.framePart: " + lfeat.framePart);
    }


    /** ***************************************************************
     * Get a person or thing.  Fill in directName, directtype, preposition
     * as a side effect in lfeat
     */
    public void getDirect(StringBuilder english, LFeatures lfeat) {

        if (debug) System.out.println("getDirect(): lfeat.framePart: " + lfeat.framePart);
        if (lfeat.framePart.trim().startsWith("somebody") || lfeat.framePart.trim().startsWith("to somebody") ) {
            if (rand.nextBoolean()) {
                lfeat.directName = lfeatsets.humans.getNext();
                lfeat.directType = "Human";
            }
            else {
                lfeat.directType = WordPairFrequency.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
                if (lfeat.directType == null)
                    lfeat.directType = lfeatsets.socRoles.getNext();
            }
            if (lfeat.framePart.trim().startsWith("to somebody"))
                lfeat.directPrep = "to ";
            int index = lfeat.framePart.indexOf("somebody");
            if (index + 10 < lfeat.framePart.length())
                lfeat.framePart = lfeat.framePart.substring(index + 9);
            else
                lfeat.framePart = "";
        }
        else if (lfeat.framePart.trim().startsWith("something") || lfeat.framePart.trim().startsWith("on something")) {
            // Thompson Start
            lfeat.directType = WordPairFrequency.getNounFromNounAndVerb(lfeatsets, lfeat);
            /* Original code
            lfeat.directType = lfeatsets.objects.getNext();
            */
            // Thompson End
            if (lfeat.framePart.contains("on something"))
                lfeat.directPrep = "on ";
            int index = lfeat.framePart.indexOf("something");
            if (index + 10 < lfeat.framePart.length())
                lfeat.framePart = lfeat.framePart.substring(index + 10);
            else
                lfeat.framePart = "";
        }
        else if (lfeat.framePart.trim().startsWith("to INFINITIVE") || lfeat.framePart.trim().startsWith("INFINITIVE") ||
                lfeat.framePart.trim().startsWith("whether INFINITIVE")) {
            getVerb(lfeat,true);
            if (lfeat.framePart.trim().startsWith("to")) {
                lfeat.directPrep = "to ";
            }
            else if (lfeat.framePart.trim().startsWith("whether")) {
                lfeat.directPrep = "whether";
                lfeat.secondVerb = "to " + lfeat.secondVerb;
            }
            else
                lfeat.secondVerb = "to " + lfeat.secondVerb;
//            lfeat.secondVerbType = lfeat.secondVerbType;
            int index = lfeat.framePart.indexOf("INFINITIVE");
            if (index + 10 < lfeat.framePart.length())
                lfeat.framePart = lfeat.framePart.substring(index + 10);
            else
                lfeat.framePart = "";
        }
        else if (lfeat.framePart.trim().startsWith("VERB-ing")) {
            getVerb(lfeat,true);
            lfeat.tense = LFeatures.PROGRESSIVE;
            lfeat.secondVerb = verbForm(lfeat.secondVerbType,false,lfeat.secondVerb,false,english,lfeat);
            if (lfeat.secondVerb.startsWith("is "))
                lfeat.secondVerb = lfeat.secondVerb.substring(3);
            lfeat.framePart = "";
        }
        if (debug) System.out.println("getDirect(2): lfeat.directType: " + lfeat.directType);
        if (debug) System.out.println("getDirect(2): lfeat.directName: " + lfeat.directName);
        if (debug) System.out.println("getDirect(2): lfeat.framePart: " + lfeat.framePart);
        if (debug) System.out.println("getDirect(2): lfeat.prep: " + lfeat.directPrep);
    }

    /** ***************************************************************
     */
    public void addSecondVerb(StringBuilder english, StringBuilder prop,
                                     LFeatures lfeat) {

        if (!StringUtil.emptyString(lfeat.directPrep))
            english.append(lfeat.directPrep).append(" ");
        english.append(lfeat.secondVerb).append(" ");
        prop.append("(instance ?V2 ").append(lfeat.secondVerbType).append(") ");
        prop.append("(refers ?DO ?V2) ");
    }

    /** ***************************************************************
     * @param prop will be empty on return if the sentence so far is rejected
     */
    public void generateDirectObject(StringBuilder english, StringBuilder prop,
                             LFeatures lfeat) {

        // can I generate possessives?

        if (debug) System.out.println("generateDirectObject(1): english: " + english);
        if (debug) System.out.println("generateDirectObject(1): prep: " + lfeat.directPrep);
        if (debug) System.out.println("generateDirectObject(1): type: " + lfeat.directType);
        if ("".equals(lfeat.framePart)) {
            prop.delete(0,prop.length());
            return;
        }
        String role = "patient";
        String other = ""; // if there
        if (StringUtil.emptyString(lfeat.directType)) // allow pre-set objects for testing
            getDirect(english,lfeat);
        else
            System.out.println("generateDirectObject(): non-empty object, using specified input " + lfeat.directType);
        if (lfeat.directType != null && lfeat.directType.equals(lfeat.subj))
            if (!lfeat.directType.equals("Human") ||
                    (lfeat.directType.equals("Human") && lfeat.directName.equals(lfeat.subjName)))
                other = RandSet.listToEqualPairs(others).getNext() + " ";
        if (lfeat.directType != null && lfeat.directType.equals("Human"))
            prop.append(genSUMOForHuman(lfeat.directName, "?DO"));
        AVPair plural = new AVPair();
        if (!"".equals(lfeat.secondVerbType)) {
            addSecondVerb(english,prop,lfeat);
            if (debug) System.out.println("generateDirectObject(2): added second verb, english: " + english);
        }
        else if (kbLite.isSubclass(lfeat.verbType, "Translocation") &&
                (kbLite.isSubclass(lfeat.directType,"Region") || kbLite.isSubclass(lfeat.directType,"StationaryObject"))) {
            //    (kb.isSubclass(dprep.noun,"Region") || kb.isSubclass(dprep.noun,"StationaryObject"))) {
            if (debug) System.out.println("generateDirectObject(3): location, region or object: " + english);
            if (lfeat.directType.equals("Human"))
                english.append("to ").append(other).append(nounFormFromTerm(lfeat.directName,plural,other)).append(" ");
            else
                english.append("to ").append(other).append(nounFormFromTerm(lfeat.directType,plural,other)).append(" ");
            if (plural.attribute.equals("true"))
                addSUMOplural(prop,lfeat.directType,plural,"?DO");
            else
                prop.append("(instance ?DO ").append(lfeat.directType).append(") ");
            prop.append("(destination ?P ?DO) ");
            role = "destination";
        }
        else if (lfeat.directType != null) {
            if (debug) System.out.println("generateDirectObject(4): some other type, english: " + english);
            if (debug) System.out.println("generateDirectObject(4): prep: " + lfeat.directPrep);
            if (lfeat.directType.equals("Human"))
                english.append(lfeat.directPrep).append(other).append(lfeat.directName).append(" ");
            else
                english.append(lfeat.directPrep).append(other).append(nounFormFromTerm(lfeat.directType,plural,other)).append(" ");
            if (plural.attribute.equals("true"))
                addSUMOplural(prop,lfeat.directType,plural,"?DO");
            else
                prop.append("(instance ?DO ").append(lfeat.directType).append(") ");

            switch (lfeat.directPrep) {
                case "to ":
                    prop.append("(destination ?P ?DO) ");
                    role = "destination";
                    break;
                case "on ":
                    prop.append("(orientation ?P ?DO On) ");
                    role = "orientation";
                    break;
                default:
                    if (kbLite.isSubclass(lfeat.verbType,"Transfer")) {
                        prop.append("(objectTransferred ?P ?DO) ");
                        role = "objectTransferred";
                    }
                    else {
                        prop.append("(patient ?P ?DO) ");
                    }   break;
            }
        }
        if (!checkCapabilities(lfeat.verbType,role,lfeat.directType)) {
            System.out.println("generateDirectObject() rejecting (proc,role,obj): " + lfeat.verbType + ", " + role + ", " + lfeat.directType);
            prop.delete(0,prop.length());
            english.delete(0,english.length());
            return;
        }
        if (debug) System.out.println("generateDirectObject(5): english: " + english);
        if (debug) System.out.println("generateDirectObject(5): prop: " + prop);
        if (debug) System.out.println("generateDirectObject(5): plural: " + plural);
        if (debug) System.out.println("generateDirectObject(5): lfeat.framePart: " + lfeat.framePart);
    }

    /** ***************************************************************
     */
    private String closeParens(LFeatures lfeat) {

        StringBuilder result = new StringBuilder();
        if (debug) System.out.println("closeParens(): lfeat.attitude: " + lfeat.attitude);
        if (debug) System.out.println("closeParens(): lfeat.attNeg: " + lfeat.attNeg);
        if (debug) System.out.println("closeParens(): lfeat.modal.attribute: " + lfeat.modal.attribute);
        if (debug) System.out.println("closeParens(): lfeat.negatedModal: " + lfeat.negatedModal);
        if (debug) System.out.println("closeParens(): lfeat.negatedBody: " + lfeat.negatedBody);
        if (lfeat.negatedBody) result.append(")");
        result.append(")) "); // close off the starting 'exists' and 'and'
        if (!lfeat.modal.attribute.equals("None")) result.append(lfeat.modal.attribute).append(")");
        if (lfeat.negatedModal && !lfeat.modal.attribute.equals("None")) result.append(")");
        if (!lfeat.attitude.equals("None")) {
            result.append(")))");
            if (lfeat.attNeg) result.append(")");
        }
        return result.toString();
    }

    /** ***************************************************************
     * extract prepositions and auxiliaries from a verb frame.  Put in the right
     * direct or indirect preposition field in lfeat
     * 15-19, 30, 31 are preps to indirect objects
     */
    public static void getPrepFromFrame(LFeatures lfeat) {

        if (debug) System.out.println("getPrepFromFrame(): frame: " + lfeat.frame);
        if (StringUtil.emptyString(lfeat.framePart)) {
            System.out.println("Error in getPrepFromFrame(): empty frame");
            return;
        }
        int fnum = WordNetUtilities.verbFrameNum(lfeat.framePart);
        if (debug) System.out.println("getPrepFromFrame(): frame num: " + fnum);
        Pattern pattern = Pattern.compile("(to |on |from |with |of |that |into |whether )", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(lfeat.framePart);
        boolean matchFound = matcher.find();
        if (matchFound) {
            String prep = matcher.group(1).trim() + " ";
            if ((fnum >=15 && fnum <=19) || fnum==30 || fnum==31)
                lfeat.indirectPrep = prep;
            else
                lfeat.directPrep = prep;
        }
    }

    /** ***************************************************************
     * extract prepositions and auxiliaries from a verb frame
     */
    public static String getCaseRoleFromPrep(String prep) {

        if (prep.equals("to "))
            return ("destination");
        if (prep.equals("from "))
            return ("origin");
        if (prep.equals("with "))
            return ("instrument");
        if (prep.equals("of "))
            return ("patient");
        if (prep.equals("on "))
            return ("destination");
        return "involvedInEvent";
    }

    /** ***************************************************************
     * Also handle the INFINITIVE verb frame
     */
    public boolean generateIndirectObject(int indCount,
                                          StringBuilder english, StringBuilder prop,
                                          LFeatures lfeat,
                                          boolean onceWithoutInd) {

        if (debug) System.out.println("generateIndirectObject(): sentCount: " + sentCount);
        if (!StringUtil.emptyString(lfeat.framePart))
            getPrepFromFrame(lfeat);
        if (!"".equals(lfeat.indirectPrep))
            lfeat.indirectPrep = lfeat.directPrep + " ";
        if (debug) System.out.println("generateIndirectObject(): frame: " + lfeat.framePart);
        if (debug) System.out.println("generateIndirectObject(): indirect prep: " + lfeat.indirectPrep);
        if (!"".equals(lfeat.framePart) && lfeat.framePart.contains("somebody") || lfeat.framePart.contains("something")) {
            String prep = null;
            getIndirect(lfeat);
            if (!StringUtil.emptyString(lfeat.indirectPrep))
                prep = getCaseRoleFromPrep(lfeat.indirectPrep);
            if (StringUtil.emptyString(prep))
                prep = "patient";
            AVPair plural = new AVPair();
            if (lfeat.indirectType.equals("Human"))
                english.append(lfeat.indirectPrep).append(lfeat.indirectName);
            else
                english.append(lfeat.indirectPrep).append(nounFormFromTerm(lfeat.indirectType,plural,""));
            if (debug) System.out.println("generateIndirectObject(): plural: " + plural);

            if (plural.attribute.equals("true"))
                addSUMOplural(prop,lfeat.indirectType,plural,"?IO");
            else {
                if (kbLite.isInstanceOf(lfeat.indirectType,"SocialRole"))
                    prop.append("(attribute ?IO ").append(lfeat.indirectType).append(") ");
                else
                    prop.append("(instance ?IO ").append(lfeat.indirectType).append(") ");
            }
            if (lfeat.framePart.contains("somebody"))
                prop.append(genSUMOForHuman(lfeat.indirectName,"?IO"));
            else
                prop.append("(instance ?IO ").append(lfeat.indirectType).append(")");

            if (english.toString().endsWith(" "))
                english.delete(english.length()-1,english.length());
            if (lfeat.polite && !lfeat.politeFirst)
                english.append(RandSet.listToEqualPairs(endings).getNext());
            if (lfeat.polite) //lfeat.subj.equals("You") && !english.toString().startsWith("Please") && rand.nextBoolean())
                english.append("!");
            else if (english.indexOf(" ") != -1 &&
                    questionWord(english.toString().substring(0,english.toString().indexOf(" "))))
                english.append("?");
            else
                english.append(".");
            if (lfeat.attitude != null && lfeat.attitude.equals("says")) {
                english.append("\"");
            }
            prop.append("(").append(prep).append(" ?P ?IO) ");

            if (lfeat.subj != null && lfeat.subj.equals("You")) {
                String newProp = prop.toString().replace(" ?H"," You");
                prop.setLength(0);
                prop.append(newProp);
            }
            prop.append(closeParens(lfeat));
            onceWithoutInd = false;
            if (debug) System.out.println("generateIndirectObject(): " + english);
            //if (KButilities.isValidFormula(kb,prop.toString())) {
            String finalEnglish = english.toString().replaceAll("  "," ");
            //System.out.println("writing english");
            englishFile.println(finalEnglish);
            //System.out.println("writing logic");
            logicFile.println(prop);
            frameFile.println(lfeat);
            sentCount++;
        }
        else if (lfeat.framePart.contains("INFINITIVE")) {
            getVerb(lfeat,true);
            if (debug) System.out.println("generateIndirectObject(): word: " + lfeat.secondVerb);
            if (debug) System.out.println("generateIndirectObject(): frame: " + lfeat.framePart);
            if (lfeat.framePart.contains("to"))
                lfeat.secondVerb = "to " + lfeat.secondVerb;
            if (debug) System.out.println("generateIndirectObject(2): word: " + lfeat.secondVerb);
            english.append(lfeat.secondVerb).append(" ");
            if (lfeat.polite && !lfeat.politeFirst)
                english.append(RandSet.listToEqualPairs(endings).getNext());
            if (english.toString().endsWith(" "))
                english.delete(english.length()-1,english.length());
            if (lfeat.subj.equals("You") && !english.toString().startsWith("Please") && rand.nextBoolean())
                english.append("!");
            else if (english.indexOf(" ") != -1 &&
                    questionWord(english.toString().substring(0,english.toString().indexOf(" "))))
                english.append("?");
            else
                english.append(".");
            if (lfeat.attitude != null && lfeat.attitude.equals("says")) {
                english.append("\"");
            }
            prop.append(closeParens(lfeat));
            if (lfeat.subj != null && lfeat.subj.equals("You")) {
                String newProp = prop.toString().replace(" ?H"," You");
                prop.setLength(0);
                prop.append(newProp);
            }
            if (debug) System.out.println("generateIndirectObject(): " + english);
            //if (KButilities.isValidFormula(kb,prop.toString())) {
            //    if (debug) System.out.println("generateIndirectObject(): valid formula: " + Formula.textFormat(prop.toString()));
            String finalEnglish = english.toString().replaceAll("  "," ");
            //System.out.println("writing english");
            englishFile.println(finalEnglish);
            //System.out.println("writing logic");
            logicFile.println(prop);
            frameFile.println(lfeat);
            sentCount++;
        }
        else {  // close off the formula without an indirect object
            if (debug) System.out.println("generateIndirectObject(): attitude: " + lfeat.attitude);
            if (lfeat.polite && !lfeat.politeFirst)
                english.append(RandSet.listToEqualPairs(endings).getNext());
            if (english.toString().endsWith(" "))
                english.delete(english.length()-1,english.length());
            if (!StringUtil.emptyString(lfeat.subj) && lfeat.subj.equals("You") && rand.nextBoolean())
                english.append("!");
            else if (english.indexOf(" ") != -1 &&
                    questionWord(english.toString().substring(0,english.toString().indexOf(" "))))
                english.append("?");
            else
                english.append(".");
            if (lfeat.attitude != null && lfeat.attitude.equals("says"))
                english.append("\"");
            prop.append(closeParens(lfeat));
            if (lfeat.subj != null && lfeat.subj.equals("You")) {
                String newProp = prop.toString().replace(" ?H"," You");
                prop.setLength(0);
                prop.append(newProp);
            }
            indCount = 0;
            if (!onceWithoutInd) {
                if (debug) System.out.println("====== generateIndirectObject(): " + english);
                //if (KButilities.isValidFormula(kb,prop.toString())) {
                if (debug) System.out.println("generateIndirectObject(): valid formula: " + Formula.textFormat(prop.toString()));
                String finalEnglish = english.toString().replaceAll("  "," ");
                //System.out.println("writing english");
                englishFile.println(finalEnglish);
                //System.out.println("writing logic");
                logicFile.println(prop);
                frameFile.println(lfeat);
                sentCount++;
            }
            onceWithoutInd = true;
        }
        return onceWithoutInd;
    }


    /** ***************************************************************
     * Get a person or thing
     */
    public void getIndirect(LFeatures lfeat) {

        if (debug) System.out.println("getIndirect(): frame: " + lfeat.framePart);
        if (lfeat.framePart.endsWith("somebody")) {
            if (rand.nextBoolean()) {
                lfeat.indirectName = lfeatsets.humans.getNext();
                lfeat.indirectType = "Human";
            }
            else {
                lfeat.indirectType = WordPairFrequency.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
                if (lfeat.indirectType == null)
                    lfeat.indirectType = lfeatsets.socRoles.getNext();
            }
        }
        else if (lfeat.framePart.endsWith("something")) {
            lfeat.indirectType = WordPairFrequency.getNounFromNounAndVerb(lfeatsets, lfeat);
        }
        if (debug) System.out.println("getIndirect(): type: " + lfeat.indirectType);
    }

    /** ***************************************************************
     * Strip tense in some frames
     */
    public String stripTenseFromFrame(String frame) {

        if (frame.equals("Is is ----ing"))
            return "It ----s";
        return frame;
    }

    /** ***************************************************************
     * Skip frames not currently handled
     */
    public boolean skipFrame(String frame) {

        return frame.contains("PP") || frame.contains("CLAUSE") || frame.contains("V-ing") || frame.contains("Adjective");
    }


    /** ***************************************************************
     *  TODO: Use WordPairFrequency an LLM to get the best adverb.
     */
    private void getAdverb(LFeatures lfeat, boolean second) {

        String word = "";
        String adverb = "";
        /* if (second)
            word = lfeat.secondVerb;
        else
            word = lfeat.verb;
        if (debug) System.out.println("getAdverb(): verb: " + word);
        if (coca.freqVerbs.keySet().contains(word)) {
            Map<Integer,Set<String>> oneVerb = coca.freqVerbs.get(word);
            int total = 0;
            for (int i : oneVerb.keySet())
                total = total + (i * oneVerb.get(i).size());
            int index = rand.nextInt(total);
            total = 0;
            int increment;
            List<String> ar;
            for (int i : oneVerb.keySet()) {
                increment = (i * oneVerb.get(i).size());
                if (index >= total && index < increment) {
                    if (oneVerb.get(i).size() == 1)
                        adverb = oneVerb.get(i).iterator().next();
                    else {
                        int size = oneVerb.get(i).size();
                        ar = new ArrayList(Arrays.asList(oneVerb.get(i).toArray()));
                        adverb = ar.get(rand.nextInt(size));
                    }
                }
                total = total + increment;
            }
            if (debug) System.out.println("adverb(): found adverb: " + adverb);

         */
            if (second)
                lfeat.secondVerbModifier = adverb;
            else
                lfeat.adverb = adverb;
        
    }

    /** ***************************************************************
     * Get a randomized next verb
     * @return an AVPair with an attribute of the SUMO term and the value
     * of the 9-digit synset concatenated with a "-" and root of the verb
     */
    public void getVerb(LFeatures lfeat, boolean second) {

        if (lfeat.testMode && !StringUtil.emptyString(lfeat.verb)) { // for testing
            System.out.println("getVerb(): non empty verb: " + lfeat.verb + " returning");
            return;
        }
        String proc = "";
        String word = "";
        String synset = "";
        List<String> synsets = null;
        List<String> words;
        do {
            do {
                proc = lfeatsets.processes.getNext(); // processes is a RandSet
                synsets = WordNetUtilities.getEquivalentVerbSynsetsFromSUMO(proc);
                if (synsets == null || synsets.isEmpty()) // keep searching for processes with equivalent synsets
                    if (debug) System.out.println("getVerb(): no equivalent synsets for: " + proc);
            } while (synsets.isEmpty());
            if (debug) System.out.println("getVerb(): checking process: " + proc);
            if (debug) System.out.println("getVerb(): synsets size: " + synsets.size() + " for term: " + proc);
            synset = synsets.get(rand.nextInt(synsets.size()));
            words = WordNet.wn.getWordsFromSynset(synset);
            int count = 0;
            do {
                count++;
                word = words.get(rand.nextInt(words.size()));
            } while (count < words.size() && word.contains("_"));
        } while (word.contains("_"));  // if all the words in a synset are compound words, start over with a new Process
        if (debug) System.out.println("getVerb(): return word: " + word);
        if (second) {
            lfeat.secondVerbType = proc;
            lfeat.secondVerb = word;
            lfeat.secondVerbSynset = synset;
        }
        else {
            lfeat.verbType = proc;
            lfeat.verb = word;
            lfeat.verbSynset = synset;
        }
        if (rand.nextBoolean())  // try to generate an adverb half the time, most will fail anyway due to missing verb data
            getAdverb(lfeat,second);
        else
        if (debug) System.out.println("getVerb(): no adverb this time for " + word);
    }

    /** ***************************************************************
     */
    public static String getFormattedDate(LocalDate date) {

        int day = date.getDayOfMonth();
        if (!((day > 10) && (day < 19)))
            switch (day % 10) {
                case 1:
                    return "d'st' 'of' MMMM yyyy";
                case 2:
                    return "d'nd' 'of' MMMM yyyy";
                case 3:
                    return "d'rd' 'of' MMMM yyyy";
                default:
                    return "d'th' 'of' MMMM yyyy";
            }
        return "d'th' 'of' MMMM yyyy";
    }

    /** ***************************************************************
     * Create action sentences from a subject, preposition, direct object,
     * preposition and indirect object based on WordNet verb frames
     */
    public void addTimeDate(StringBuilder english,
                        StringBuilder prop,
                        LFeatures lfeat) {

        boolean hastime = false;
        boolean hasdate = false;
        int month = rand.nextInt(12)+1;
        int yearMult = 1;
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        if (lfeat.isPast() || lfeat.isPastProgressive()) {
            yearMult = -1;
        }
        int theYear = (rand.nextInt(100) * yearMult) + year;
        LocalDate d = LocalDate.of(theYear,month,1);
        YearMonth ym = YearMonth.from(d);
        LocalDate endOfMonth = ym.atEndOfMonth();
        int day = rand.nextInt(endOfMonth.getDayOfMonth()-1) + 1;
        d = d.withDayOfMonth(day);
        int hour = rand.nextInt(24);
        LocalTime t = LocalTime.of(hour,0,0);
        LocalDateTime ldt = LocalDateTime.of(year,month,day,hour,0,0);
        ArrayList<String> dateOptions = new ArrayList<>();
        dateOptions.add("d MMM uuuu"); dateOptions.add("dd-MM-yyyy"); dateOptions.add("EEE, d MMM yyyy");
        dateOptions.add(getFormattedDate(d));
        String dateOption = dateOptions.get(rand.nextInt(dateOptions.size()));
        if (biasedBoolean(2,5)) { // sometimes add a time
            english.append(capital("at "));
            DateTimeFormatter format = DateTimeFormatter.ofPattern("ha");
            english.append(t.format(format)).append(" ");
            prop.append("(instance ?T (HourFn ").append(hour).append(")) (during ?P ?T) ");
            hastime = true;
            startOfSentence = false;
        }
        if (!hastime && biasedBoolean(2,5)) { // sometimes add a date
            DateTimeFormatter format = DateTimeFormatter.ofPattern(dateOption);
            english.append(capital("on "));
            english.append(d.format(format)).append(" ");
            prop.append("(instance ?T (DayFn ").append(day).append(" (MonthFn ").append(month).append(" (YearFn ").append(theYear).append(")))) (during ?P ?T) ");
            hasdate = true;
            startOfSentence = false;
        }
        if (!hastime && !hasdate) { // sometimes add both
            english.append(capital("on "));
            DateTimeFormatter format = DateTimeFormatter.ofPattern(dateOption + " 'at' ha");
            prop.append("(instance ?T (HourFn ").append(hour).append(" (DayFn ").append(day).append(" (MonthFn ").append(month).append(" (YearFn ").append(year).append("))))) (during ?P ?T) ");
            english.append(ldt.format(format)).append(" ");
            startOfSentence = false;
        }
        if (debug) System.out.println("addTimeDate() startOfSentence: " + startOfSentence);
    }

    /** ***************************************************************
     */
    private void getFrame(LFeatures lfeat) {

        String frame = null;
        int count = 0;
        do {
            frame = lfeat.frames.get(rand.nextInt(lfeat.frames.size()));
            count++;
        } while (count < (lfeat.frames.size() * 2) && (StringUtil.emptyString(frame) || skipFrame(frame)));
        if (count >= (lfeat.frames.size() * 2) || StringUtil.emptyString(frame) || skipFrame(frame))
            return;
        frame = stripTenseFromFrame(frame);
        if (debug) System.out.println("getFrame() set frame to: " + frame);
        lfeat.framePart = frame;
        lfeat.frame = frame;
    }

    /** ***************************************************************
     * Create action sentences from a subject, preposition, direct object,
     * preposition and indirect object based on WordNet verb frames
     */
    public void genProc(StringBuilder english,
                        StringBuilder prop,
                        LFeatures lfeat) {

        lfeat.tense = LFeatures.getRandomTense();
        if (lfeat.isImperative() && rand.nextBoolean()) {
            lfeat.polite = true;
            if (rand.nextBoolean())
                lfeat.politeFirst = false; // put at end
            else
                english.insert(0,RandSet.listToEqualPairs(requests).getNext() + english);
        }
        System.out.println("Verb : " + lfeat.verb);
        System.out.println("Verb synset: " + lfeat.verbSynset);
        System.out.println("Verb synset: " + lfeat);
        lfeat.frames = WordNetUtilities.getVerbFramesForWord(lfeat.verbSynset, lfeat.verb);
        if (lfeat.frames == null || lfeat.frames.isEmpty()) {
            if (debug) System.out.println("genProc() no frames for word: " + lfeat.verb);
            return;
        }
        System.out.println("ARE YOU EVER MAKING IT HERE!!!!!????????????????????????????????");
        if (debug) System.out.println("genProc() frames: " + lfeat.frames);
        if (debug) System.out.println("genProc() time: " + lfeat.printTense());
        if (debug) System.out.println("genProc() synset: " + lfeat.verbSynset);

        if (debug) System.out.println("genProc() word: " + lfeat.verb);
        getFrame(lfeat);
        if (lfeat.framePart == null) {
            if (debug) System.out.println("genProc() no acceptable frames for word: " + lfeat.verb);
            return;
        }
        if ((lfeat.attitude.equals("None") && lfeat.modal.attribute.equals("None")) ||
                lfeat.attitude.equals("says"))
            startOfSentence = true;
        if (!lfeat.testMode)
            lfeat.clearSVO(); // clear flags set for each sentence
        getPrepFromFrame(lfeat);
        if (debug) System.out.println("genProc() verb: '" + lfeat.verb + "'");
        if (debug) System.out.println("genProc() prepPhrases: " + prepPhrase);
        if (debug) System.out.println("genProc() preposition: " + lfeat.directPrep);
        if (debug) System.out.println("genProc() contains key: " + prepPhrase.containsKey(lfeat.verb));
        if (debug) System.out.println("genProc() empty prep: " + StringUtil.emptyString(lfeat.directPrep));
        if (debug) System.out.println("genProc() new prep: " + prepPhrase.get(lfeat.verb));
        if (prepPhrase.containsKey(lfeat.verb) && StringUtil.emptyString(lfeat.directPrep))
            lfeat.directPrep = prepPhrase.get(lfeat.verb);
        if (debug) System.out.println("genProc() frame: " + lfeat.framePart);
        if (debug) System.out.println("genProc() startOfSentence: " + startOfSentence);
        if (debug) System.out.println("genProc() preposition: " + lfeat.directPrep);
        lfeat.negatedBody = biasedBoolean(2,10);  // make it negated one time out of 5
        int indCount = 0;
        boolean onceWithoutInd = false;
        lfeat.indirectType = WordPairFrequency.getNounFromNounAndVerb(lfeatsets, lfeat);
        if (lfeat.negatedBody)
            prop.append("(not ");
        prop.append("(exists (?H ?P ?DO ?IO) (and ");
        if (biasedBoolean(1,10) && english.length() == 0)
            addTimeDate(english,prop,lfeat);
        if (debug) System.out.println("genProc(2) startOfSentence: " + startOfSentence);

        generateSubject(english, prop, lfeat);
        generateVerb(lfeat.negatedBody, english, prop, lfeat.verbType, lfeat.verb, lfeat);
        if (prop.toString().equals("")) {
            if (debug) System.out.println("genProc(): return b/c empty prop for " + english);
            return;
        }
        startOfSentence = false;
        generateDirectObject(english, prop, lfeat);
        if (StringUtil.emptyString(prop.toString())) {
            if (debug) System.out.println("genProc(): return b/c empty prop for " + english);
            return;
        }
        generateIndirectObject(indCount, english, prop, lfeat, onceWithoutInd);
        lfeat.framePart = lfeat.frame;  // recreate frame destroyed during generation
        if (debug)
            System.out.println("====================\n genProc(): end " + english);
    }

    /** ***************************************************************
     * Generate a person's name, or a SocialRole, or the diectic "You"
     *
     * @param english the English for the named human or role, as a
     *                side effect.
     * @param prop the SUMO for the named human or role, as a side
     *             effect.
     * @param allowYou is whether to allow returning the "You (understood)" form
     * @param var the existentially quantified variable for the
     *            human, an input.
     * @param type the type of the human, whether "Human" or
     *             "Attribute" or subAttribute, as a side effect.
     * @param name the name of the named human, as a side effect.
     *
     * lfeatsets.prevHumans are names or roles from previous parts of the
     *                 sentence that should not be repeated, modified
     *                 as a side effect.
     */
    public void generateHuman(StringBuilder english,
                              StringBuilder prop,
                              boolean allowYou,
                              String var,
                              StringBuilder type,
                              StringBuilder name,
                              LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.generateHuman(): allow You (understood): " + allowYou);
        boolean found = false;
        String socialRole;
        AVPair plural;
        do {
            int val = rand.nextInt(10);
            if (allowYou && val < 2) { // "You" - no appended English or SUMO
                type.append("You");
                if (debug) System.out.println("GenSimpTestData.generateHuman(): generated a You (understood)");
            }
            else if (val < 6) { // a role
                socialRole = WordPairFrequency.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
                if (socialRole == null)
                    socialRole = lfeatsets.socRoles.getNext();
                type.append(socialRole);
                prop.append("(attribute ").append(var).append(" ").append(type).append(") ");
                plural = new AVPair();
                english.append(capital(nounFormFromTerm(type.toString(),plural,""))).append(" ");
                if (lfeatsets.prevHumans.contains(type))  // don't allow the same name or role twice in a sentence
                    found = true;
                else
                    lfeatsets.prevHumans.add(type.toString());
            }
            else {  // a named human
                name.append(lfeatsets.humans.getNext());
                type.append("Human");
                prop.append("(instance ").append(var).append(" Human) ");
                prop.append("(names \"").append(name).append("\" ").append(var).append(") ");
                english.append(name).append(" ");
                if (lfeatsets.prevHumans.contains(name)) // don't allow the same name or role twice in a sentence
                    found = true;
                else
                    lfeatsets.prevHumans.add(name.toString());
            }
        } while (found);
        if (!StringUtil.emptyString(lfeat.framePart) && lfeat.framePart.length() > 9 &&
                lfeat.framePart.toLowerCase().startsWith("somebody"))
            lfeat.framePart = lfeat.framePart.substring(9);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): type: " + type);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): frame: " + lfeat.framePart);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): name: " + name);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): english: " + english);
    }




    /** ***************************************************************
     * use None, knows, believes, says, desires for attitudes
     */
    public void genAttitudes(StringBuilder english, StringBuilder prop, LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.genAttitudes(): ");
        if (debug) System.out.println("GenSimpTestData.genAttitudes(): human list size: " + lfeatsets.humans.size());

        String that;
        startOfSentence = true;
        Word attWord = attitudes.get(rand.nextInt(attitudes.size()));
        lfeat.attitude = attWord.term;
        if (debug) System.out.println("GenSimpTestData.genAttitudes(): ========================= start ");
        if (!attWord.term.equals("None")) {
            StringBuilder type = new StringBuilder();
            StringBuilder name = new StringBuilder();
            lfeat.attNeg = rand.nextBoolean();
            if (lfeat.attNeg)
                prop.append("(not (exists (?HA) (and  ");
            else
                prop.append("(exists (?HA) (and ");
            generateHuman(english,prop,false,"?HA",type,name,lfeat);
            prop.append("(").append(attWord.term).append(" ?HA ");
            lfeat.attSubj = name.toString(); // the subject of the propositional attitude
            startOfSentence = false;
            if (rand.nextBoolean() || lfeat.attitude.equals("desires"))
                that = "that ";
            else
                that = "";
            if (lfeat.attNeg)
                english.append("doesn't ").append(attWord.root).append(" ").append(that);
            else
                english.append(attWord.present).append(" ").append(that);
            if (attWord.term.equals("says"))
                english.append("\"");
        }
        if (debug) System.out.println("GenSimpTestData.genAttitudes(): ========= end english: " + english);
    }

    /** ***************************************************************
     */
    public String negatedModal(String modal,boolean negated) {

        if (!negated)
            return modal;
        else {
            return modal.substring(0,5) + " not" + modal.substring(5);
        }
    }

    /** ***************************************************************
     * Create action sentences from a subject, preposition, direct object,
     * preposition and indirect object.  Indirect object and its preposition
     * can be left out.  Actions will eventually be past and future tense or
     * wrapped in modals.
     */
    public void genWithModals(StringBuilder english,
                              StringBuilder prop,
                              LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.genWithModals() start");
        AVPair modal = lfeatsets.modals.get(rand.nextInt(lfeatsets.modals.size()));
        lfeat.modal = modal;
        lfeat.negatedModal = biasedBoolean(2,10); // make it negated one time out of 5
        if (debug) System.out.println("genWithModals(): " + modal);
        if (!lfeat.modal.attribute.equals("None")) {
            if (lfeat.negatedModal)
                prop.append("(not (modalAttribute ");
            else
                prop.append("(modalAttribute ");
            english.append(negatedModal(modal.value,lfeat.negatedModal));
            if (startOfSentence)
                english.replace(0,1,english.substring(0,1).toUpperCase());
            startOfSentence = false;
        }
        if (debug) System.out.println("GenSimpTestData.genWithModals() end: english: " + english);
    }



    /** ***************************************************************
     * negated, proc, object, caserole, prep, mustTrans, mustNotTrans, canTrans
     * @return true if ok
     */
    public boolean checkCapabilities(String proc, String role, String obj) {

        if (debug) System.out.println("checkCapabilities(): starting");
        if (debug) System.out.println("checkCapabilities(): (proc,role,obj): " + proc + ", " + role + ", " + obj);
        if (lfeatsets.capabilities.containsKey(proc)) {
            if (debug) System.out.println("checkCapabilities(): found capabilities: " + lfeatsets.capabilities.get(proc));
            Set<LFeatureSets.Capability> caps = lfeatsets.capabilities.get(proc);
            for (LFeatureSets.Capability c : caps) {
                if (c.caserole.equals(role) && (c.object.equals(obj) || kbLite.isSubclass(obj,c.object)) && !c.negated && c.must) {
                    if (debug) System.out.println("checkCapabilities(): approved");
                    return true;
                }
                if (c.caserole.equals(role) && !c.object.equals(obj) & !kbLite.isSubclass(obj,c.object) && c.must) {
                    if (debug) System.out.println("checkCapabilities(): rejected, object is not a " + c.object);
                    return false;
                }
                if (c.caserole.equals(role) && c.object.equals(obj) && c.mustNot) {
                    if (debug) System.out.println("checkCapabilities(): rejected.  Conflict with: " + c);
                    return false;
                }
                if (c.caserole.equals(role) && !c.object.equals(obj) && !kbLite.isSubclass(obj,c.object) && !c.negated && c.must) {
                    if (debug) System.out.println("checkCapabilities(): rejected.  Conflict with: " + c);
                    return false;
                }
                if (c.caserole.equals(role) && c.object.equals(obj) && c.negated) {
                    if (debug) System.out.println("checkCapabilities(): rejected.  Conflict with: " + c);
                    return false;
                }
            }
        }
        else {
            if (debug) System.out.println("checkCapabilities(): " + proc + " not found.");
        }
        if (debug) System.out.println("checkCapabilities(): approved by default");
        return true;
    }


    /** ***************************************************************
     * generate NL paraphrases for all non-ground formulas
     */
    public static void englishAxioms() {

//        Formula f;
        for (String fstr : kb.formulas.keySet()) {
//            f = new Formula(fstr);
            if (!Formula.isGround(fstr)) {
                System.out.println(fstr);
                System.out.println(StringUtil.removeHTML(NLGUtils.htmlParaphrase("", fstr, kb.getFormatMap("EnglishLanguage"),
                        kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage")));
            }
        }
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Sentence generation");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -tf - generate any missing termFormat statements");
        System.out.println("  -hu - generate SUOKIF from a list of gendered names");
        System.out.println("  -t - run tests");
        System.out.println("  -a <filename> - generate logic/language pairs for all statements in KB");
        System.out.println("  -g <filename> - generate ground statement pairs for all relations");
        System.out.println("  -i - generate English for all non-ground formulas");
        System.out.println("  -s <filename> <optional count> - generate NL/logic compositional <count> sentences to <filename> (no extension)");
        // System.out.println("  -p <filename> <optional count> - parallel generation of NL/logic compositional <count> sentences to <filename> (no extension)");
        System.out.println("  -n - generate term formats from term names in a file");
        System.out.println("  -u - other utility");
    }

    /** ***************************************************************
     * init and call main routine.
     */
    public static void main(String args[]) {

        KBmanager.prefOverride.put("TPTP","no");
        try {
            if (args == null || args.length == 0 || args[0].equals("-h"))
                showHelp();
            else {
                FileWriter fweng;
                FileWriter fwlog;
                FileWriter fwframe;
                if (args.length > 1) {
                    fweng = new FileWriter(args[1] + "-eng.txt");
                    englishFile = new PrintWriter(fweng);
                    fwlog = new FileWriter(args[1] + "-log.txt");
                    logicFile = new PrintWriter(fwlog);
                    fwframe = new FileWriter(args[1] + "-frame.txt");
                    frameFile = new PrintWriter(fwframe);
                }
                else {
                    if (args[0].equals("-s") || args[0].equals("-a") ||args[0].equals("-g")) {
                        System.out.println("Missing filename parameter for option");
                        System.exit(1);
                    }
                }
                if (args.length > 1 && args[0].equals("-s")) { // create NL/logic synthetically
                    if (args.length > 2)
                        sentMax = Integer.parseInt(args[2]);
                    GenSimpTestData gstd = new GenSimpTestData(true);
                    gstd.runGenSentence();
                    englishFile.close();
                    logicFile.close();
                    frameFile.close();
                }
                /*if (args.length > 1 && args[0].equals("-p")) { // create NL/logic synthetically
                    if (args.length > 2)
                        sentMax = Integer.parseInt(args[2]);
                    GenSimpTestData gstd = new GenSimpTestData(true);
                    gstd.parallelGenSentence();
                    englishFile.close();
                    logicFile.close();
                    frameFile.close();
                }*/
                if (args.length > 0 && args[0].equals("-g")) { // generate ground statements
                    GenGroundStatements ggs = new GenGroundStatements();
                    ggs.generate(englishFile, logicFile);
                    englishFile.close();
                    logicFile.close();
                }
                if (args.length > 0 && args[0].equals("-i")) { // generate English for all non-ground statements
                    KBmanager.getMgr().initializeOnce();
                    kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
                    englishAxioms();
                }
                if (args.length > 0 && args[0].equals("-u")) {
//                    GenSimpTestData gstd = new GenSimpTestData();
                }
                if (args.length > 0 && args[0].equals("-a")) { // generate NL/logic for all existing axioms
                    allAxioms();
                    englishFile.close();
                    logicFile.close();
                }
                if (args.length > 0 && args[0].equals("-tf"))
                    genMissingTermFormats();
                if (args.length > 0 && args[0].equals("-hu"))
                    generateAllHumans();
                if (args.length > 0 && args[0].equals("-t")) {
                    //testTypes();
                    //testNLG();
                    //testGiving();

                    //testPutting();
                    //testToy();
                    GenSimpTestData gstd = new GenSimpTestData();
                    KBmanager.getMgr().initializeOnce();
                    kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
                    String word = "Sheep";
                    System.out.println("exceptions: " + WordNet.wn.exceptionNounPluralHash);
                    for (int i = 0; i < 10; i++)
                        System.out.println("noun form: " + gstd.nounFormFromTerm(word,new AVPair(),""));
                    if (WordNet.wn.exceptionNounPluralHash.containsKey(word))
                        System.out.println("noun form for sheep: " + WordNet.wn.exceptionNounPluralHash.get(word));
                    LFeatures lfeat = new LFeatures();
                    lfeat.frame = "Something ----s something Adjective/Noun";
                    lfeat.framePart = lfeat.frame;
                    System.out.println("frame: ");
                    GenSimpTestData.getPrepFromFrame(lfeat);
                    //gstd.testVerbs();
                    //gstd.testLFeatures();
                }
                if (args.length > 1 && args[0].equals("-n")) {
                    genTermFormatFromNames(args[1]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }
}

// Unfinished methods, TODO:
    /*
        /** ***************************************************************
         * Get frequency-sorted co-occurrences of adj/noun and adv/verb pairs
         * TODO: Once Ollama generation is complete, this won't be necessary.
         *
    public static void initModifiers() {


        String prefix = System.getenv("CORPORA") + File.separator + "COCA" + File.separator;
        File n = new File(prefix + "nouns.txt");
        File v = new File(prefix + "verbs.txt");
        if (!n.exists() || !v.exists())
            coca.pairFreq(prefix);

        coca.freqNouns = PairMap.readMap(n.getAbsolutePath());
        coca.freqVerbs = PairMap.readMap(v.getAbsolutePath());
        COCA.filterModifiers(coca.freqVerbs,coca.freqNouns);


    }
    */

/** ***************************************************************
 * How many occurrences remaining in the frame of 'something' and 'someone'
 */
    /*
    Never used
    public int countSomes(String frame) {

        String str = "frame";
        String something = "something";
        String somebody = "somebody";
        return (str.split(something,-1).length-1) + (str.split(somebody,-1).length-1);
    }

     */


/** ***************************************************************
 */
    /*
       Never used
    public List<String> getVerbFramesForTerm(String term) {

        List<String> frames = new ArrayList<>();
        List<String> synsets = WordNetUtilities.getEquivalentVerbSynsetsFromSUMO(term);
        if (debug) System.out.println("GenSimpTestData.getVerbFramesForTerm(): synsets size: " +
                synsets.size() + " for term: " + term);
        if (synsets.isEmpty())
            return frames;
            //synsets = WordNetUtilities.getVerbSynsetsFromSUMO(term);
        List<String> words;
        List<String> newframes;
        for (String s : synsets) {
            words = WordNet.wn.getWordsFromSynset(s);
            for (String w : words) {
                newframes = WordNetUtilities.getVerbFramesForWord(s,w);
                if (newframes != null)
                    frames.addAll(newframes);
            }
        }
        return frames;
    }
    */



/** ***************************************************************
 * @return the word part of 9-digit synset concatenated with a "-" and root of the verb
 */
    /*
    Never used
    private String getWordPart(String s) {

        if (s.length() < 11) {
            System.out.println("Error in getWordPart(): bad input: " + s);
            return "";
        }
        return s.substring(10);
    }
     */

/** ***************************************************************
 * @return the synset part of 9-digit synset concatenated with a "-" and root of the verb
 */
    /*
    Never used
    private String getSynsetPart(String s) {

        if (s.length() < 11) {
            System.out.println("Error in getSynsetPart(): bad input: " + s);
            return "";
        }
        return s.substring(0,9);
    }
    */


/** ***************************************************************
 * Create action sentences from a subject, preposition, direct object,
 * preposition and indirect object.  Indirect object and its preposition
 * can be left out.  Actions can be past and future tense or
 * wrapped in modals.
 */
    /*
    Not used
     */
    /*
    public void genWithRoles(StringBuilder english,
                             StringBuilder prop,
                             LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.genWithRoles()");
        int humCount = 0;
        String role;
        StringBuilder prop1, english1;
        for (int i = 0; i < humanMax; i++) {
            role = WordPairFrequency.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
            if (role == null)
                role = lfeatsets.socRoles.getNext();
            if (lfeat.subj.equals(role)) continue;
            if (humCount++ > loopMax) break;
            lfeat.subj = role;
            int tryCount = 0;
            do {
                prop1 = new StringBuilder(prop);
                english1 = new StringBuilder(english);
                getVerb(lfeat,false);
                genProc(english1, prop1, lfeat);
            } while (tryCount++ < 10 && prop1.equals(""));
        }
    }
    */


/** ***************************************************************
 * find attributes in SUMO that have equivalences to WordNet
 */
    /*
    Never called
    public void showAttributes() {

        Set<String> attribs = kbLite.getAllInstances("Attribute");
        List<String> synsets;
        for (String s : attribs) {
            synsets = WordNetUtilities.getEquivalentSynsetsFromSUMO(s);
            if (debug) System.out.println("term and synset: " + s + ", " + synsets);
        }
    }
     */


/** ***************************************************************
 * estimate the number of sentences that will be produced

 public static long estimateSentCount(LFeatures lfeat) {

 long count = 2; // include negation
 if (!suppress.contains("attitude"))
 count = count * attMax;
 if (!suppress.contains("modal"))
 count = count * modalMax * 2; //include negation
 count = count * (humanMax + lfeatsets.socRoles.size());
 count = count * loopMax; // lfeat.intProc.size();
 count = count * loopMax; // lfeat.direct.size();
 count = count * loopMax; // lfeat.indirect.size();
 return count;
 }
 */



/** ***************************************************************
 Not used
 public void genWithHumans(StringBuilder english,
 StringBuilder prop,
 LFeatures lfeat) {


 if (debug) System.out.println("GenSimpTestData.genWithHumans()");
 int humCount = 0;
 lfeatsets.humans.clearReturns();
 for (int i = 0; i < humanMax; i++) {
 lfeat.subj = lfeatsets.humans.getNext();
 if (lfeat.subj.equals(lfeat.attSubj)) continue;
 if (humCount++ > humanMax) break;
 int tryCount = 0;
 StringBuilder prop1, english1;
 do {
 prop1 = new StringBuilder(prop);
 english1 = new StringBuilder(english);
 getVerb(lfeat,false);
 genProc(english1, prop1, lfeat);
 } while (tryCount++ < 10 && prop1.equals(""));
 }
 }
 */

/** ***************************************************************
 * Create action sentences, possibly with modals.  Indirect object and its preposition
 * can be left out.
 *
 * To be implemented later
 public void parallelGenSentence() {

 System.out.println("GenSimpTestData.parallelGenSentence(): start");
 kbLite = new KBLite("SUMO");
 System.out.println("GenSimpTestData.parallelGenSentence(): finished loading KBs");

 List<Integer> numbers = new ArrayList<>();
 for (int i =0; i < sentMax; i++)
 numbers.add(i);
 //ArrayList<String> terms = new ArrayList<>();
 //if (debug) System.out.println("GenSimpTestData.parallelGenSentence():  lfeat.direct: " + lfeat.direct);
 //System.exit(1);
 //for (Preposition p : lfeat.direct)
 //    terms.add(p.procType);
 numbers.parallelStream().forEach(number -> {
 LFeatures lfeat = new LFeatures();
 genAttitudes(lfeat);
 });
 }
 */