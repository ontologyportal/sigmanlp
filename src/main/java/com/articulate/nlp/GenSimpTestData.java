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

    public static boolean debug = false;
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

    public static final Set<String> suppress = new HashSet<>( // forms to suppress, usually for testing
            Arrays.asList());

    public static PrintWriter englishFile = null; //generated English sentences
    public static PrintWriter logicFile = null;   //generated logic sentences, one per line,
                                                  // NL/logic should be on same line in the different files
    public static PrintWriter frameFile = null; // LFeatures for the current sentence, to support future processing

    public static long sentMax = 10000000;
    public static LFeatureSets lfeatsets;

    // verb and noun keys with values that are the frequency of coocurence with a given
    // adjective or adverb
    public Map<String, Map<Integer,Set<String>>> freqVerbs = new HashMap<>();
    public Map<String, Map<Integer,Set<String>>> freqNouns = new HashMap<>();


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
        lfeatsets.initNumbers();
        lfeatsets.initRequests();
        lfeatsets.initAttitudes(suppress.contains("attitude"));
        lfeatsets.initOthers();
        lfeatsets.initPrepPhrase();
        lfeatsets.initEndings();
        lfeatsets.genProcTable();
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
     * generate new SUMO statements for names
     */
    public String genSUMOForHuman(String name, String var) {

        StringBuilder sb = new StringBuilder();
        if (name != null) {
            String gender = "Male";
            String g = lfeatsets.genders.get(name);
            if (g != null) {
                if (g.equalsIgnoreCase("F"))
                    gender = "Female";
                //sb.append("(instance " + var + " Human) ");
                sb.append("(attribute ").append(var).append(" ").append(gender).append(") ");
                sb.append("(names \"").append(name).append("\" ").append(var).append(") ");
            }
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
    public static void progressPrint(int sentCount, int badSentCount) {

        if ((sentCount % 100) != 0) return;
        if (!debug) System.out.print("\r\33[2K");
        double value = ((double) sentCount / (double) sentMax) * 100;
        System.out.print(String.format("%.2f", value));
        System.out.print("% complete. ");
        System.out.print(sentCount + " of total " + sentMax);
        System.out.print(". Discarded sentence attempts: " + badSentCount);
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
     * Create action sentences from a subject, preposition, direct object,
     * preposition and indirect object.  Indirect object and its preposition
     * can be left out.  Actions will eventually be past and future tense or
     * wrapped in modals.
     */
    public void runGenSentence() {

        System.out.println("GenSimpTestData.runGenSentence(): start");
        LFeatures lfeat;
        int sentCount = 0;
        int badSentCount = 0;
        while (sentCount < sentMax) {
            progressPrint(sentCount, badSentCount);
            lfeat = new LFeatures();
            lfeatsets.prevHumans.clear();
            if (genSentence(lfeat)) {
                lfeat.flushToEnglishLogic(kbLite);
                printSentenceToFiles(lfeat.englishSentence, lfeat.logicFormula, lfeat.toString());
                sentCount++;
            }
            else {
                badSentCount++;
            }
        }
        System.out.println("Finished generating " + sentCount + " good sentences. The number of malformed sentences during generation: " + badSentCount);
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
    public String verbForm(String term, boolean negated, String word, boolean plural, String nounType, LFeatures lfeat) {

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
                if (lfeat.addShouldToSubj) {
                    if (rand.nextBoolean())
                        neg = "not ";
                    else {
                        neg = "n't ";
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
            return neg + word + nounForm;
        if ("".equals(nounForm)) // only modify if we don't use an auxilliary
            root = WordNet.wn.verbRootForm(word,word.toLowerCase());
        if (root == null)
            root = word;

        if (debug) System.out.println("verbForm(): word: " + word);
        if (debug) System.out.println("verbForm(): root: " + root);
        if (debug) System.out.println("verbForm(): nounForm: " + nounForm);
        String copula = conjCopula(negated,lfeat.tense,plural);
        if (nounType != null && nounType.endsWith("is ") && copula.startsWith("is"))
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
                    return root.substring(0, root.length() - 1) + "ies";
                return root + es + nounForm;
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
            return root.substring(0,root.length()-1) + "ies";
        if (!lfeat.noTense())
            System.out.println("Error in verbForm(): time is unallowed value: " + lfeat.tense);
        if (negated)
            return "doesn't " + root + nounForm;
        else {
            if ("".equals(nounForm))
                return root + es;
            else
                return root + nounForm + es;
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
                    return "the " + word;
                else
                    return word;
            }

        }
        boolean subst = kbLite.isSubclass(term,"Substance");
        if (subst) {
            if (rand.nextBoolean()) {
                AVPair quant = getQuantity(term);
                if (quant == null)
                    return "some " + word;
                else {
                    avp.attribute = quant.attribute;
                    avp.value = quant.value;
                    return avp.attribute + "of " + word;
                }
            }
            else
                return "some " + word;
        }
        String number = "";
        if (biasedBoolean(1,5) && kbLite.isSubclass(term,"CorpuscularObject")) { // occasionally make this a plural or count
            int index = rand.nextInt(lfeatsets.numbers.size());  // how many of the object
            avp.value = Integer.toString(index);
            if (rand.nextBoolean())
                number = lfeatsets.numbers.get(index);  // sometimes spell out the number as a word...
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
            return number + " " + word;
        if (word.matches("^[aeiouAEIOH].*")) // pronouncing "U" alone is like a consonant, H like a vowel
            return "an " + word;
        return "a " + word;
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
     */
    private void addBodyPart(LFeatures lfeat) {

        lfeat.bodyPart = GenWordSelector.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "BodyPart");
        if (lfeat.bodyPart == null)
            lfeat.bodyPart = lfeatsets.bodyParts.getNext();
        lfeat.pluralBodyPart = new AVPair();
        lfeat.bodyPart = nounFormFromTerm(lfeat.bodyPart,lfeat.pluralBodyPart,"");
    }

    /** ***************************************************************
     * Generate the subject of the sentence conforming to the verb frame
     * for a human
     */
    public void generateHumanSubject(LFeatures lfeat) {

        StringBuilder type = new StringBuilder();
        StringBuilder name = new StringBuilder();
        if (biasedBoolean(1,5) ) { // It's a question.
            lfeat.subj = "Who";
            lfeat.subjName = "";
            lfeat.question = true;
        }
        else if (lfeat.isModalAttitudeOrPolitefirst()
                && allowImperatives) {
            generateHuman(true, type, name, lfeat);
            lfeat.subj = type.toString();
            lfeat.subjName = name.toString();
        }
        else {
            generateHuman(false, type, name, lfeat);
            lfeat.subj = type.toString();
            lfeat.subjName = name.toString();
        }
        if (lfeat.subj.equals("You")) {
            if (biasedBoolean(1,5)) {
                lfeat.addPleaseToSubj = true;
            }
            else if (biasedBoolean(1,5)) {
                lfeat.addShouldToSubj = true;
            }
        }
        if (lfeat.framePart.startsWith("Somebody's (body part)")) {
            lfeat.addBodyPart = true;
            addBodyPart(lfeat);
        }
        removeFrameSubject(lfeat);
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
    public void generateThingSubject(LFeatures lfeat) {

        if (biasedBoolean(4,5) && !lfeat.isModalAttitudeOrPolitefirst()) {
            lfeat.subj = "what";
            lfeat.question = true;
        }
        else if (lfeat.framePart.startsWith("Something")) {
            lfeat.subjType = "Something";
            lfeat.subjName = GenWordSelector.getNounFromVerb(lfeatsets, lfeat, kbLite);
            lfeat.pluralSubj = new AVPair();
            lfeat.subj = nounFormFromTerm(lfeat.subjName,lfeat.pluralSubj,"");
            if (lfeat.pluralSubj.attribute.equals("true")) {
                lfeat.subjectPlural = true;
            }
            if (lfeat.framePart.startsWith("Something is")) {
                lfeat.subjType = "Something is";
            }
            else
                lfeat.framePart = lfeat.framePart.substring(10);
        }
        else {  // frame must be "It..."
            if (lfeat.framePart.startsWith("It is")) {
                lfeat.subjType = "It is";
            }
            else {
                lfeat.subjType = "It";
            }
        }
        removeFrameSubject(lfeat);
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
    public void generateSubject(LFeatures lfeat) {

        if (lfeat.framePart.startsWith("It") || lfeat.framePart.startsWith("Something")) {
            generateThingSubject(lfeat);
        }
        else { // Somebody
            lfeat.subjType = "Human";
            generateHumanSubject(lfeat);
        }
    }

    /** **********************************************************************
     *  Adds the previously selected verb to the sentence in its proper form.
     */
    public boolean generateVerb(LFeatures lfeat) {

        boolean negated = lfeat.negatedBody;
        String word = lfeat.verb;
        String verb = verbForm(lfeat.verbType,negated,word,lfeat.subjectPlural, lfeat.subjType, lfeat);
        String adverb = "";
        lfeat.adverbSUMO = "";
        if (!"".equals(lfeat.adverb)) {
            adverb = lfeat.adverb + " ";
            lfeat.adverbSUMO = WSD.getBestDefaultSUMOsense(lfeat.adverb,4);
            lfeat.adverbSUMO = WordNetUtilities.getBareSUMOTerm(lfeat.adverbSUMO);
        }
        if (lfeat.subj != null && !lfeat.subj.isEmpty() && lfeat.subj.equals("You")) {
            verb = verb.toLowerCase();

            if (adverb != null && !adverb.isEmpty()) {
                adverb = Character.toUpperCase(adverb.charAt(0)) + adverb.substring(1);
            } else {
                System.out.println("Error: adverb is null or empty");
            }
        }
        lfeat.verbConjugated = verb;
        if (lfeat.framePart.startsWith("It") ) {
            lfeat.verbFrameCat = "It";
        }
        else if (lfeat.framePart.startsWith("Something")) {
            lfeat.verbFrameCat = "Something";
        }
        else if (!(lfeat.subj != null && lfeat.subj.equalsIgnoreCase("What") &&
                        !kbLite.isSubclass(lfeat.verbType,"IntentionalProcess"))
                && !(lfeat.subj != null &&
                        (kbLite.isSubclass(lfeat.subj,"AutonomousAgent") ||
                        kbLite.isInstanceOf(lfeat.subj,"SocialRole") ||
                        lfeat.subj.equalsIgnoreCase("Who") ||
                        lfeat.subj.equals("You")))){
            return false;
        }
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
        if (debug) System.out.println("generateVerb(): lfeat.framePart: " + lfeat.framePart);
        return true;
    }


    /** ***************************************************************
     * Get a person or thing.  Fill in directName, directtype, preposition
     * as a side effect in lfeat
     */
    public void getDirect(LFeatures lfeat) {

        if (debug) System.out.println("getDirect(): lfeat.framePart: " + lfeat.framePart);
        if (lfeat.framePart.trim().startsWith("somebody") || lfeat.framePart.trim().startsWith("to somebody") ) {
            if (rand.nextBoolean()) {
                lfeat.directName = lfeatsets.humans.getNext();
                lfeat.directType = "Human";
            }
            else {
                lfeat.directType = GenWordSelector.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
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
            lfeat.directType = GenWordSelector.getNounFromNounAndVerb(lfeatsets, lfeat, kbLite);
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
                lfeat.directPrep = "whether ";
                lfeat.secondVerb = "to " + lfeat.secondVerb;
            }
            else
                lfeat.secondVerb = "to " + lfeat.secondVerb;
            int index = lfeat.framePart.indexOf("INFINITIVE");
            if (index + 10 < lfeat.framePart.length())
                lfeat.framePart = lfeat.framePart.substring(index + 10);
            else
                lfeat.framePart = "";
        }
        else if (lfeat.framePart.trim().startsWith("VERB-ing")) {
            getVerb(lfeat,true);
            int tenseTemp = lfeat.tense; // This is a bit of a hack. Ideally verbForm would be changed to distinguish first vs. second verb.
            lfeat.tense = LFeatures.PROGRESSIVE;
            lfeat.secondVerb = verbForm(lfeat.secondVerbType,false,lfeat.secondVerb,false, lfeat.directType, lfeat);
            lfeat.tense = tenseTemp; // End of the bit of the hack.
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
     * generates a Direct Object, returns false if error.
     */
    public boolean generateDirectObject(LFeatures lfeat) {

        if (debug) System.out.println("generateDirectObject(1): prep: " + lfeat.directPrep);
        if (debug) System.out.println("generateDirectObject(1): type: " + lfeat.directType);
        if ("".equals(lfeat.framePart)) {
            return false;
        }
        String role = "patient";
        lfeat.directOther = ""; // if there
        if (StringUtil.emptyString(lfeat.directType)) // allow pre-set objects for testing
            getDirect(lfeat);
        else
            System.out.println("generateDirectObject(): non-empty object, using specified input " + lfeat.directType);
        if (lfeat.directType != null && lfeat.directType.equals(lfeat.subj))
            if (!lfeat.directType.equals("Human") ||
                    (lfeat.directType.equals("Human") && lfeat.directName.equals(lfeat.subjName)))
                lfeat.directOther = RandSet.listToEqualPairs(lfeatsets.others).getNext() + " ";
        if (lfeat.directType != null && lfeat.directType.equals("Human")) {
            lfeat.directSUMO = genSUMOForHuman(lfeat.directName, "?DO");
        }
        lfeat.pluralDirect = new AVPair();
        if ("".equals(lfeat.secondVerbType) && kbLite.isSubclass(lfeat.verbType, "Translocation") &&
                (kbLite.isSubclass(lfeat.directType,"Region") || kbLite.isSubclass(lfeat.directType,"StationaryObject"))) {
            lfeat.directName = nounFormFromTerm(lfeat.directType, lfeat.pluralDirect, lfeat.directOther);
            role = "destination";
        }
        else if (lfeat.directType != null) {
            if (debug) System.out.println("generateDirectObject(4): prep: " + lfeat.directPrep);
            if (!lfeat.directType.equals("Human"))
                lfeat.directName = nounFormFromTerm(lfeat.directType, lfeat.pluralDirect, lfeat.directOther);

            switch (lfeat.directPrep) {
                case "to ":
                    role = "destination";
                    break;
                case "on ":
                    role = "orientation";
                    break;
                default:
                    if (kbLite.isSubclass(lfeat.verbType,"Transfer")) {
                        role = "objectTransferred";
                    }  break;
            }
        }
        if (!checkCapabilities(lfeat.verbType,role,lfeat.directType)) {
            System.out.println("generateDirectObject() rejecting (proc,role,obj): " + lfeat.verbType + ", " + role + ", " + lfeat.directType);
            return false;
        }
        if (debug) System.out.println("generateDirectObject(5): plural: " + lfeat.pluralDirect);
        if (debug) System.out.println("generateDirectObject(5): lfeat.framePart: " + lfeat.framePart);
        return true;
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
            System.out.println("Error in getPrepFromFrame(): empty frame: (verb, verbType) = (" + lfeat.verb + ", " + lfeat.verbType + ")");
            return;
        }
        int fnum = WordNetUtilities.verbFrameNum(lfeat.framePart);
        if (debug) System.out.println("getPrepFromFrame(): frame num: " + fnum);
        Pattern pattern = Pattern.compile("(to |on |from |with |of |that |into |whether )", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(lfeat.framePart);
        boolean matchFound = matcher.find();
        if (matchFound) {
            String prep = matcher.group(1).trim() + " ";
            if ((fnum >=15 && fnum <=19) || fnum==30 || fnum==31) {
                lfeat.indirectPrep = prep;
            }
            else {
                lfeat.directPrep = prep;
                if (lfeatsets.prepPhrase.containsKey(lfeat.verb) && StringUtil.emptyString(lfeat.directPrep))
                    lfeat.directPrep = lfeatsets.prepPhrase.get(lfeat.verb);
            }
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
    public void generateIndirectObject(LFeatures lfeat) {

        if (!StringUtil.emptyString(lfeat.framePart) && lfeat.indirectPrep.equals(""))
            getPrepFromFrame(lfeat);
        if (!"".equals(lfeat.indirectPrep))
            lfeat.indirectPrep = lfeat.directPrep + " ";
        if (debug) System.out.println("generateIndirectObject(): frame: " + lfeat.framePart);
        if (debug) System.out.println("generateIndirectObject(): indirect prep: " + lfeat.indirectPrep);
        if (!"".equals(lfeat.framePart) && lfeat.framePart.contains("somebody") || lfeat.framePart.contains("something")) {
            getIndirect(lfeat);
            if (!StringUtil.emptyString(lfeat.indirectPrep))
                lfeat.indirectCaseRole = getCaseRoleFromPrep(lfeat.indirectPrep);
            if (StringUtil.emptyString(lfeat.indirectCaseRole))
                lfeat.indirectCaseRole = "patient";
            lfeat.pluralIndirect = new AVPair();
            if (!lfeat.indirectType.equals("Human"))
                lfeat.indirectName = nounFormFromTerm(lfeat.indirectType, lfeat.pluralIndirect, "");
            if (debug) System.out.println("generateIndirectObject(): plural: " + lfeat.pluralIndirect);

            if (lfeat.framePart.contains("somebody")) {
                lfeat.indirectSUMO = genSUMOForHuman(lfeat.indirectName, "?IO");
            }

            if (lfeat.polite && !lfeat.politeFirst) {
                lfeat.politeWord = RandSet.listToEqualPairs(lfeatsets.endings).getNext();
            }
            if (lfeat.polite) {
                lfeat.exclamation = true;
            }
        }
        else if (lfeat.framePart.contains("INFINITIVE")) {
            getVerb(lfeat,true);
            if (debug) System.out.println("generateIndirectObject(): word: " + lfeat.secondVerb);
            if (debug) System.out.println("generateIndirectObject(): frame: " + lfeat.framePart);
            if (lfeat.framePart.contains("to"))
                lfeat.secondVerb = "to " + lfeat.secondVerb;
            if (debug) System.out.println("generateIndirectObject(2): word: " + lfeat.secondVerb);

            if (lfeat.subj.equals("You") && !lfeat.addPleaseToSubj && rand.nextBoolean()) {
                lfeat.exclamation = true;
            }
        }
        else {  // close off the formula without an indirect object
            if (debug) System.out.println("generateIndirectObject(): attitude: " + lfeat.attitude);
            if (!StringUtil.emptyString(lfeat.subj) && lfeat.subj.equals("You") && rand.nextBoolean()) {
                lfeat.exclamation = true;
            }
        }
    }

    /** ************************************************************************
     *  Prints sentence with logic to a file
     */
    public void printSentenceToFiles(String english, String prop, String lfeatString) {
        englishFile.println(english);
        logicFile.println(prop);
        frameFile.println(lfeatString);
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
                lfeat.indirectType = GenWordSelector.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
                if (lfeat.indirectType == null)
                    lfeat.indirectType = lfeatsets.socRoles.getNext();
            }
        }
        else if (lfeat.framePart.endsWith("something")) {
            lfeat.indirectType = GenWordSelector.getNounFromNounAndVerb(lfeatsets, lfeat, kbLite);
        }
        if (debug) System.out.println("getIndirect(): type: " + lfeat.indirectType);
    }

    
    /** ***************************************************************
     * Skip frames not currently handled
     */
    public boolean skipFrame(String frame) {

        return frame.contains("PP") || frame.contains("CLAUSE") || frame.contains("V-ing") || frame.contains("Adjective");
    }


    /** ***************************************************************
     *  TODO: Use GenWordSelector and LLM to get the best adverb.
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
    public void addTimeDate(LFeatures lfeat) {

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
        lfeat.hastime = biasedBoolean(2,5); // sometimes add a time
        lfeat.hasdate = !lfeat.hastime && biasedBoolean(2,5); // sometimes add a date
        lfeat.hasdatetime = !lfeat.hastime && !lfeat.hasdate; // sometimes add both
        if (lfeat.hastime) {
            lfeat.timeEng = "at " + t.format(DateTimeFormatter.ofPattern("ha"));
            lfeat.hourLog = hour + "";
        }
        if (!lfeat.hastime && lfeat.hasdate) {
            lfeat.dateEng = "on " + d.format(DateTimeFormatter.ofPattern(dateOption));
            lfeat.dayLog = day + "";  lfeat.monthLog = month + "";  lfeat.yearLog = theYear + "";
        }
        if (!lfeat.hastime && !lfeat.hasdate && lfeat.hasdatetime) { // sometimes add both
            lfeat.dateEng = "on " + ldt.format(DateTimeFormatter.ofPattern(dateOption + " 'at' ha"));
            lfeat.hourLog = hour + "";  lfeat.dayLog = day + "";  lfeat.monthLog = month + "";  lfeat.yearLog = year + "";
        }
    }

    /** ***************************************************************
     */
    private void getTense(LFeatures lfeat) {
        lfeat.tense = LFeatures.getRandomTense();
        if (lfeat.isImperative() && rand.nextBoolean()) {
            lfeat.polite = true;
            if (rand.nextBoolean()) {
                lfeat.politeFirst = false; // put at end
            }
            else {
                lfeat.politeWord = RandSet.listToEqualPairs(lfeatsets.requests).getNext();
            }
        }
    }

    /** ***************************************************************
     */
    private void getFrame(LFeatures lfeat) {

        lfeat.frames = WordNetUtilities.getVerbFramesForWord(lfeat.verbSynset, lfeat.verb);
        if (lfeat.frames == null || lfeat.frames.isEmpty()) {
            if (debug) System.out.println("getFrame() no frames for word: " + lfeat.verb);
            lfeat.framePart = null;
            return;
        }
        if (debug) System.out.println("getFrame() frames: " + lfeat.frames);
        if (debug) System.out.println("getFrame() tense: " + lfeat.printTense());
        if (debug) System.out.println("getFrame) synset: " + lfeat.verbSynset);
        if (debug) System.out.println("getFrame() word: " + lfeat.verb);

        String frame = null;
        int count = 0;
        do {
            frame = lfeat.frames.get(rand.nextInt(lfeat.frames.size()));
            count++;
        } while (count < (lfeat.frames.size() * 2) && (StringUtil.emptyString(frame) || skipFrame(frame)));
        if (count >= (lfeat.frames.size() * 2) || StringUtil.emptyString(frame) || skipFrame(frame))
            return;
        //Strip tense in some frames.
        frame = (frame.equals("Is is ----ing")) ? "It ----s" : frame;
        if (debug) System.out.println("getFrame() set frame to: " + frame);
        lfeat.framePart = frame;
        lfeat.frame = frame;
    }

    /** **************************************************************************
     * Create a single action sentence from a subject, preposition, direct object,
     * preposition and indirect object based on WordNet verb frames
     */
    public boolean genSentence(LFeatures lfeat) {

        if (!suppress.contains("attitude")) {
            genAttitudes(lfeat);
        }
        if (!suppress.contains("modal")){
            genWithModals(lfeat);
        }
        int tryCount = 0;
        do {
            lfeat.clearSVO();
            getVerb(lfeat,false);
            getTense(lfeat);
            getFrame(lfeat);  // Get a verb frame from WordNet
            if (lfeat.framePart != null) { // An optimization would be to remove all the verbs that have null frameParts from lfeatset.
                getPrepFromFrame(lfeat);
                lfeat.negatedBody = biasedBoolean(2,10);  // make it negated one time out of 5
                lfeat.indirectType = GenWordSelector.getNounFromNounAndVerb(lfeatsets, lfeat, kbLite);
                if (biasedBoolean(1,10) && !lfeat.isModalAttitudeOrPolitefirst()) {
                    lfeat.hasdateORtime = true;
                    addTimeDate(lfeat);
                }
                generateSubject(lfeat);
                if (generateVerb(lfeat))  {
                    if (generateDirectObject(lfeat)){
                        generateIndirectObject(lfeat);
                        return true;
                    }
                    //else {
                      //  System.out.println("DELETEME: runGenSentence() generateDirectObject failed: " + lfeat);
                    //}
                }
//                else {
  //                  System.out.println("DELETEME: runGenSentence() generateVerb failed: " + lfeat);
    //            }
            } else {
                System.out.println("DELETEME: runGenSentence() no acceptable verb frames: " + lfeat.verb);
                if (debug) System.out.println("runGenSentence() no acceptable verb frames found for word: " + lfeat.verb);
            }
        } while (tryCount++ < 10);
        return false;
    }

    /** ***************************************************************
     * Generate a person's name, or a SocialRole, or the diectic "You"
     *
     * @param allowYou is whether to allow returning the "You (understood)" form
     * @param type the type of the human, whether "Human" or
     *             "Attribute" or subAttribute, as a side effect.
     * @param name the name of the named human, as a side effect.
     *
     * lfeatsets.prevHumans are names or roles from previous parts of the
     *                 sentence that should not be repeated, modified
     *                 as a side effect.
     */
    public void generateHuman(boolean allowYou,
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
                socialRole = GenWordSelector.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
                if (socialRole == null)
                    socialRole = lfeatsets.socRoles.getNext();
                type.append(socialRole);
                plural = new AVPair();
                name.append(nounFormFromTerm(type.toString(),plural,""));
                if (lfeatsets.prevHumans.contains(type))  // don't allow the same name or role twice in a sentence
                    found = true;
                else
                    lfeatsets.prevHumans.add(type.toString());
            }
            else {  // a named human
                name.append(lfeatsets.humans.getNext());
                type.append("Human");
            }
            if (lfeatsets.prevHumans.contains(name)) // don't allow the same name or role twice in a sentence
                found = true;
            else
                lfeatsets.prevHumans.add(name.toString());
        } while (found);
        if (!StringUtil.emptyString(lfeat.framePart) && lfeat.framePart.length() > 9 &&
                lfeat.framePart.toLowerCase().startsWith("somebody"))
            lfeat.framePart = lfeat.framePart.substring(9);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): type: " + type);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): frame: " + lfeat.framePart);
        if (debug) System.out.println("GenSimpTestData.generateHuman(): name: " + name);
    }




    /** ***************************************************************
     * use None, knows, believes, says, desires for attitudes
     */
    public void genAttitudes(LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.genAttitudes(): ========================= start ");
        if (debug) System.out.println("GenSimpTestData.genAttitudes(): ");
        if (debug) System.out.println("GenSimpTestData.genAttitudes(): human list size: " + lfeatsets.humans.size());

        LFeatureSets.Word attWord = lfeatsets.attitudes.get(rand.nextInt(lfeatsets.attitudes.size()));
        lfeat.attitude = attWord.term;
        lfeat.attWord = attWord;
        if (!lfeat.attitude.equals("None")) {
            StringBuilder type = new StringBuilder();
            StringBuilder name = new StringBuilder();
            lfeat.attNeg = rand.nextBoolean();
            generateHuman(false,type,name,lfeat);
            lfeat.attSubj = name.toString(); // the subject of the propositional attitude
            lfeat.attSubjType = type.toString();
        }
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
    public void genWithModals(LFeatures lfeat) {

        if (debug) System.out.println("GenSimpTestData.genWithModals() start");
        AVPair modal = lfeatsets.modals.get(rand.nextInt(lfeatsets.modals.size()));
        lfeat.modal = modal;
        lfeat.negatedModal = biasedBoolean(2,10); // make it negated one time out of 5
        if (debug) System.out.println("genWithModals(): " + modal);
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

        for (String fstr : kb.formulas.keySet()) {
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
                    englishFile = new PrintWriter(fweng, true);
                    fwlog = new FileWriter(args[1] + "-log.txt");
                    logicFile = new PrintWriter(fwlog, true);
                    fwframe = new FileWriter(args[1] + "-frame.txt");
                    frameFile = new PrintWriter(fwframe, true);
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
                if (args.length > 0 && args[0].equals("-hu")) {
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
                if (args.length > 0 && args[0].equals("-t")) {
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
             public static COCA coca = new COCA();

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
            role = GenWordSelector.getNounInClassFromVerb(lfeatsets, lfeat, kbLite, "SocialRole");
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