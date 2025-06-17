package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.DB;
import java.io.File;
import com.articulate.sigma.Formula;
import com.articulate.sigma.FormulaUtil;
import com.articulate.sigma.wordNet.WordNetUtilities;
import com.articulate.sigma.wordNet.WordNet;
import java.util.*;

/**
 * **************************************************************
 */
public class LFeatureSets {

    public boolean debug = false;
    public boolean excludeCompoundVerbs = true;
    public static final Set<String> verbEx = new HashSet<>(
            Arrays.asList("Acidification","Vending","OrganizationalProcess",
                    "NaturalProcess","Corkage","LinguisticCommunication"));
    public static boolean useCapabilities = false; // include process types from capabilities list

    public class Capability {

        public boolean negated = false; // not a capability
        public String proc = null; // the process or verb
        public String object = null; // SUMO term for direct or indirect object type
        public String caserole = null; // the CaseRole
        public String prep = null; // the preposition to use
        public boolean must = false; // must have the following CaseRole
        public boolean mustNot = false; // must not have the following CaseRole
        public boolean can = false; // can have the following CaseRole
        public String fromParent = null; // which parent Process is the info inherited from, if any
        @Override
        public String toString() {
            return proc + " : " + object + " : " + caserole + " : " + negated;
        }
    }
    public static final Map<String,Set<Capability>> capabilities = new HashMap<>();

    public List<AVPair> modals = null;
    public Map<String, String> genders = null;
    public RandSet humans = null;
    public RandSet socRoles = null;
    public RandSet objects = null;
    public RandSet bodyParts = null;
    public Set<String> prevHumans = new HashSet<>();
    public RandSet processes = null;
    private KBLite kbLite = null;


    public LFeatureSets(KBLite kbLiteParam) {
        WordNet.initOnce();
        kbLite = kbLiteParam;
        //  get capabilities from axioms like
        //  (=> (instance ?GUN Gun) (capability Shooting instrument ?GUN))
        // indirect = collectCapabilities(); // TODO: need to restore and combine this filter with verb frames
        genProcTable();
        if (debug) System.out.println("LFeatureSets(): collect terms");
        genders = readHumans();
        addUnknownsHumansOrgs(genders);
        humans = RandSet.listToEqualPairs(genders.keySet());

        modals = initModals();

        Set<String> roles = kbLite.getInstancesForType("SocialRole");
        //if (debug) System.out.println("LFeatureSets(): SocialRoles: " + roles);
        Collection<AVPair> roleFreqs = findWordFreq(roles);
        socRoles = RandSet.create(roleFreqs);

        Set<String> parts = kbLite.getInstancesForType("BodyPart");
        //if (debug) System.out.println("LFeatureSets(): BodyParts: " + parts);
        Collection<AVPair> bodyFreqs = findWordFreq(parts);
        bodyParts = RandSet.create(bodyFreqs);

        Set<String> artInst = kbLite.getInstancesForType("Artifact");
        Set<String> artClass = kbLite.getChildClasses("Artifact");

        Set<String> processesSet = kbLite.getChildClasses("Process");
        processesSet.removeIf(v -> excludedVerb(v));
        Collection<AVPair> procFreqs = findWordFreq(processesSet);
        processes = RandSet.create(procFreqs);

        if (useCapabilities) {
            RandSet rs = RandSet.listToEqualPairs(capabilities.keySet());
            processes.terms.addAll(rs.terms);
        }

        Set<String> orgInst = kbLite.getInstancesForType("OrganicObject");
        Set<String> orgClass = kbLite.getChildClasses("OrganicObject");

        HashSet<String> objs = new HashSet<>();
        objs.addAll(orgClass);
        objs.addAll(artClass);
        HashSet<String> objs2 = new HashSet<>();
        for (String s : objs)
            if (!s.equals("Human") && !kbLite.isSubclass(s, "Human"))
                objs2.add(s);
        if (debug) System.out.println("LFeatureSets(): OrganicObjects and Artifacts: " + objs);
        Collection<AVPair> objFreqs = findWordFreq(objs2);
        addUnknownsObjects(objFreqs);
        if (debug) System.out.println("LFeatureSets(): create objects");
        objects = RandSet.create(objFreqs);
    }


    /** ***************************************************************
     * generate new SUMO termFormat and instance statements for names
     */
    public Map<String,String> readHumans() {

        Map<String,String> result = new HashMap<>();
        List<List<String>> fn = DB.readSpreadsheet(kbLite.kbDir +
                File.separator + "WordNetMappings/FirstNames.csv", null, false, ',');
        fn.remove(0);  // remove the header

        String firstName, g;
        for (List<String> ar : fn) {
            firstName = ar.get(0);
            g = ar.get(1);
            result.put(firstName,g);
        }
        return result;
    }

    /***************************************************************
     * add UNK words to the list of objects
     */
    public void addUnknownsObjects(Collection<AVPair> objFreqs) {

        int count = (int) Math.round(Math.log(200) + 1.0); // say that
        // some unknowns occur same as words that show up 200 times in the Brown Corpus - WorndNet cntlist file
        for (int i = 1; i <= 3; i++) {
            AVPair avp = null;
            avp = new AVPair("UNK_FAC_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_GPE_" + i, "30");
            objFreqs.add(avp);
            avp = new AVPair("UNK_LANGUAGE_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_LAW_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_LOC_" + i, "30");
            objFreqs.add(avp);
            avp = new AVPair("UNK_NORP_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_ORG_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_PRODUCT_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_WORK_OF_ART_" + i, "10");
            objFreqs.add(avp);
            avp = new AVPair("UNK_noun_" + i, "10");
            objFreqs.add(avp);
        }
    }

    /***************************************************************
     * add UNK words to the list of objects
     */
    public void addUnknownsHumansOrgs(Map<String, String> humans) {

        for (int i = 1; i <= 3; i++) {
            humans.put("UNK_ORG_" + i,"N");
            humans.put("UNK_PERSON_" + i,"M");
        }
    }

    /** ***************************************************************
     */
    public List<AVPair> initModals() {

        List<AVPair> modals = new ArrayList<>();
        for (int i = 0; i < 50; i++)
            modals.add(new AVPair("None",""));
        if (!GenSimpTestData.suppress.contains("modal")) {
            modals.add(new AVPair("Necessity", "it is necessary that "));
            modals.add(new AVPair("Possibility", "it is possible that "));
            modals.add(new AVPair("Obligation", "it is obligatory that "));
            modals.add(new AVPair("Permission", "it is permitted that "));
            modals.add(new AVPair("Prohibition", "it is prohibited that "));
            modals.add(new AVPair("Likely", "it is likely that "));
            modals.add(new AVPair("Unlikely", "it is unlikely that "));
        }
        return modals;
    }

    /** ***************************************************************
     * @param terms a collection of SUMO terms
     * @return an ArrayList of AVPair with a value of log of frequency
     *          derived from the equivalent synsets the terms map to. Attribute
     *          of each AVPair is a SUMO term.  If we didn't use the log
     *          of frequency we'd practically just get "to be" every time
     *          Used in LFeatures.java
     */
    public List<AVPair> findWordFreq(Collection<String> terms) {

        List<AVPair> avpList = new ArrayList<>();
        List<String> resultWords;
        AVPair avp;
        for (String term : terms) {
            List<String> synsets = WordNetUtilities.getEquivalentSynsetsFromSUMO(term);
            int count;
            if (synsets == null || synsets.isEmpty())
                count = 1;
            else {
                int freq = 0;
                for (String s : synsets) {
                    resultWords = WordNet.wn.getWordsFromSynset(s);
                    int f = 0;
                    if (WordNet.wn.senseFrequencies.containsKey(s))
                        f = WordNet.wn.senseFrequencies.get(s);
                    if (f > freq)
                        freq = f;
                }
                count = (int) Math.round(Math.log(freq) + 1.0) + 1;
            }
            avp = new AVPair(term,Integer.toString(count));
            avpList.add(avp);
        }
        return avpList;
    }

    /** ***************************************************************
     * negated, proc, object, caserole, prep, mustTrans, mustNotTrans, canTrans
     */
    public void genProcTable() {

        System.out.println("GenSimpTestData.genProcTable(): start");
        Collection<Capability> caps = collectCapabilities();
        extendCapabilities(caps);
    }

    /** ***************************************************************
     * generate subclasses for each capability
     */
    public void extendCapabilities(Collection<Capability> caps) {

        Set<Capability> hs;
        Set<String> childClasses;
        for (Capability c : caps) {
            childClasses = kbLite.getChildClasses(c.proc);
            if (childClasses != null) {
                childClasses.add(c.proc); // add the original class
                for (String cls : childClasses) { // add each of the subclasses
                    c.proc = cls;
                    hs = new HashSet<>();
                    if (capabilities.containsKey(c.proc))
                        hs = capabilities.get(c.proc);
                    hs.add(c);
                    capabilities.put(cls, hs);
                }
            }
            else {
                hs = new HashSet<>();
                if (capabilities.containsKey(c.proc))
                    hs = capabilities.get(c.proc);
                hs.add(c);
                capabilities.put(c.proc, hs);
            }
        }
    }

    /** ***************************************************************
     * Collect capability axioms of a specific form: Antecedent must be
     * a single (instance ?X ?Y) literal.  Consequent must be a single
     * (capability ?A ?B ?X) literal.  Returns ?Y - class of the thing,
     * ?A - the Process type and ?B the role that ?Y plays in the process.
     * Also accept (requiredRole ?A ?B ?C) and (prohibitedRole ?A ?B ?C)
     * forms
     * @return Capability objects
     */
    public Set<Capability> collectCapabilities() {

        Set<Capability> result = new HashSet<>();
        List<Formula> forms2 = kbLite.ask("arg",0,"requiredRole");
        System.out.println("collectCapabilities(): requiredRoles: " + forms2);
        Capability p;
        for (Formula f : forms2) {
            //System.out.println("collectCapabilities(): form: " + f);
            p = this.new Capability();
            p.proc = f.getStringArgument(1);
            p.caserole = f.getStringArgument(2);
            p.object = f.getStringArgument(3);
            p.must = true;
            result.add(p);
        }

        List<Formula> forms3 = kbLite.ask("arg",0,"prohibitedRole");
        for (Formula f : forms3) {
            //System.out.println("collectCapabilities(): form: " + f);
            p = this.new Capability();
            p.proc = f.getStringArgument(1);
            p.caserole = f.getStringArgument(2);
            p.object = f.getStringArgument(3);
            p.mustNot = true;
            result.add(p);
        }

        List<Formula> forms = kbLite.ask("cons",0,"capability");
        String ant, antClass, cons, kind, consClass, rel;
        Formula fant, fcons, fneg;
        for (Formula f : forms) {
            //System.out.println("collectCapabilities(): form: " + f);
            ant = FormulaUtil.antecedent(f);
            fant = new Formula(ant);
            if (fant.isSimpleClause(null) && fant.car().equals("instance")) {
                antClass = fant.getStringArgument(2); // the thing that plays a role
                cons = FormulaUtil.consequent(f);
                fcons = new Formula(cons);
                kind = fcons.getStringArgument(0); // capability or requiredRole
                if (fcons.isSimpleClause(null)) {
                    consClass = fcons.getStringArgument(1);  // the process type
                    rel = fcons.getStringArgument(2);  // the role it plays
                    p = this.new Capability();
                    p.proc = consClass;
                    p.caserole = rel;
                    p.object = antClass;
                    if (forms2.contains(f))
                        p.must = true;
                    else
                        p.can = true;
                    result.add(p);
                }
                else if (fcons.isSimpleNegatedClause(null)) {
                    fneg = fcons.getArgument(1);
                    consClass = fneg.getStringArgument(1);  // the process type
                    rel = fneg.getStringArgument(2);  // the role it plays
                    p = this.new Capability();
                    p.proc = consClass;
                    p.caserole = rel;
                    p.object = antClass;
                    if (forms2.contains(f))
                        p.must = true;
                    else
                        p.can = true;
                    p.negated = true;
                    result.add(p);
                }
            }
        }
        return result;
    }


    /** ***************************************************************
     *  too hard grammatically for now to have compound verbs
     */
    public boolean excludedVerb(String v) {

        if (debug) System.out.println("excludedVerb(): checking: " + v);
        if (excludeCompoundVerbs && compoundVerb(v))  // exclude compound verbs for now since the morphology is too difficult
            return true;
        if (verbEx.contains(v)) // check for specifically excluded verbs and their SUMO subclasses
            return true;
        for (String s : verbEx) {
            if (kbLite.isSubclass(v,s))
                return true;
        }
        if (debug) System.out.println("excludedVerb(): not excluded: " + v);
        return false;
    }

    /** ***************************************************************
     * also return true if there's no termFormat for the process
     */
    public boolean compoundVerb(String term) {

        String word = kbLite.getTermFormat("EnglishLanguage",term);
        if (word == null || word.contains(" ")) {
            if (debug) System.out.println("compoundVerb(): or null: " + word + " " + term);
            return true;
        }
        return false;
    }

}