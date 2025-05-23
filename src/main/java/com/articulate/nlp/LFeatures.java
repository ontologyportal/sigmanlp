package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;

import java.util.*;

/**
 * **************************************************************
 */
public class LFeatures {

    private static final boolean debug = false;

    public boolean testMode = false;

    private final GenSimpTestData genSimpTestData;
    public boolean attNeg = false; // for propositional attitudes
    public boolean attPlural = false;
    public int attCount = 1;
    public String attSubj = null; // the agent holding the attitude
    public String attitude = "None";
    public String attitudeModifier = ""; // adjective
    public boolean negatedModal = false;
    public boolean negatedBody = false;
    public List<AVPair> modals = null;
    public AVPair modal = new AVPair("None", "none"); // attribute if SUMO ModalAttribute, value is English
    public Map<String, String> genders = null;
    public RandSet humans = null;
    public RandSet socRoles = null;
    public RandSet objects = null;
    public RandSet bodyParts = null;
    public String directPrep = "";
    public String indirectPrep = "";
    public String secondVerb = ""; // the verb word that appears as INFINITIVE or VERB-ing or V-ing in the frame
    public String secondVerbType = ""; // the SUMO type of the second verb
    public String secondVerbSynset = "";
    public String secondVerbModifier= ""; // adverb
    public Set<String> prevHumans = new HashSet<>();
    public String subj = "";
    public String subjName = "";
    public String subjectModifier = ""; // adjective
    public boolean subjectPlural = false;
    public int subjectCount = 1;

    public RandSet processes = null;
    public static boolean useCapabilities = true; // include process types from capabilities list

    public List<String> frames = null;  // verb frames for the current process type
    public String frame = null; // the particular verb frame under consideration.
    public String framePart = null; // the frame that gets "consumed" during processing
    // Note that the frame is destructively modified as we proceed through the sentence
    public String verbSynset = null;
    public String directName = null;  // the direct object
    public String directType = null;  // the direct object
    public boolean directPlural = false;
    public int directCount = 1;
    public String directModifier = ""; // adjective
    public String indirectName = null; // the indirect object
    public String indirectType = null; // the indirect object
    public boolean indirectPlural = false;
    public int indirectCount = 1;
    public String indirectModifier = ""; // adjective
    public boolean question = false;
    public String verb = "";
    public String verbType = ""; // the SUMO class of the verb
    public String adverb = "";
    public int tense = GenSimpTestData.NOTIME;
    public boolean polite = false;  // will a polite phrase be used for a sentence if it's an imperative
    public boolean politeFirst = true; // if true and an imperative and politness used, put it at the beginning of the sentence, otherwise at the end

    public LFeatures(GenSimpTestData genSimpTestData) {
        this.genSimpTestData = genSimpTestData;

        //  get capabilities from axioms like
        //  (=> (instance ?GUN Gun) (capability Shooting instrument ?GUN))
        // indirect = collectCapabilities(); // TODO: need to restore and combine this filter with verb frames
        if (debug) System.out.println("LFeatures(): collect terms");
        genders = GenSimpTestData.humans;
        addUnknownsHumansOrgs(genders);
        humans = RandSet.listToEqualPairs(genders.keySet());

        modals = initModals();

        Set<String> roles = GenSimpTestData.kbLite.getInstancesForType("SocialRole");
        //if (debug) System.out.println("LFeatures(): SocialRoles: " + roles);
        Collection<AVPair> roleFreqs = genSimpTestData.findWordFreq(roles);
        socRoles = RandSet.create(roleFreqs);

        Set<String> parts = GenSimpTestData.kbLite.getInstancesForType("BodyPart");
        //if (debug) System.out.println("LFeatures(): BodyParts: " + parts);
        Collection<AVPair> bodyFreqs = genSimpTestData.findWordFreq(parts);
        bodyParts = RandSet.create(bodyFreqs);

        Set<String> artInst = GenSimpTestData.kbLite.getInstancesForType("Artifact");
        Set<String> artClass = GenSimpTestData.kbLite.getChildClasses("Artifact");

        Set<String> processesSet = GenSimpTestData.kbLite.getChildClasses("Process");
        Collection<AVPair> procFreqs = genSimpTestData.findWordFreq(processesSet);
        processes = RandSet.create(procFreqs);

        if (useCapabilities) {
            RandSet rs = RandSet.listToEqualPairs(GenSimpTestData.capabilities.keySet());
            processes.terms.addAll(rs.terms);
        }

        Set<String> orgInst = GenSimpTestData.kbLite.getInstancesForType("OrganicObject");
        Set<String> orgClass = GenSimpTestData.kbLite.getChildClasses("OrganicObject");

        HashSet<String> objs = new HashSet<>();
        objs.addAll(orgClass);
        objs.addAll(artClass);
        HashSet<String> objs2 = new HashSet<>();
        for (String s : objs)
            if (!s.equals("Human") && !GenSimpTestData.kbLite.isSubclass(s, "Human"))
                objs2.add(s);
        if (debug) System.out.println("LFeatures(): OrganicObjects and Artifacts: " + objs);
        Collection<AVPair> objFreqs = genSimpTestData.findWordFreq(objs2);
        addUnknownsObjects(objFreqs);
        if (debug) System.out.println("LFeatures(): create objects");
        objects = RandSet.create(objFreqs);
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

    /***************************************************************
     * clear basic flags in the non-modal part of the sentence
     */
    public void clearSVO() {

        subj = null;
        subjectPlural = false;
        subjectCount = 1;
        directName = null;  // the direct object
        directType = null;  // the direct object
        directPlural = false;
        directPrep = "";
        directCount = 1;
        indirectName = null; // the indirect object
        indirectType = null; // the indirect object
        indirectPlural = false;
        indirectPrep = "";
        indirectCount = 1;
        secondVerb = "";
        secondVerbType = "";
    }

    @Override
    public String toString() {
        return "LFeatures{" +
                "attNeg=" + attNeg +
                ", attPlural=" + attPlural +
                ", attCount=" + attCount +
                ", attSubj='" + attSubj + '\'' +
                ", attitude='" + attitude + '\'' +
                ", attitudeModifier='" + attitudeModifier + '\'' +
                ", negatedModal=" + negatedModal +
                ", negatedBody=" + negatedBody +
                ", directPrep='" + directPrep + '\'' +
                ", indirectPrep='" + indirectPrep + '\'' +
                ", secondVerb='" + secondVerb + '\'' +
                ", secondVerbType='" + secondVerbType + '\'' +
                ", secondVerbSynset='" + secondVerbSynset + '\'' +
                ", secondVerbModifier='" + secondVerbModifier + '\'' +
                ", subj='" + subj + '\'' +
                ", subjName='" + subjName + '\'' +
                ", subjectModifier='" + subjectModifier + '\'' +
                ", subjectPlural=" + subjectPlural +
                ", subjectCount=" + subjectCount +
                ", frame='" + frame + '\'' +
                ", framePart='" + framePart + '\'' +
                ", verbSynset='" + verbSynset + '\'' +
                ", directName='" + directName + '\'' +
                ", directType='" + directType + '\'' +
                ", directPlural=" + directPlural +
                ", directCount=" + directCount +
                ", directModifier='" + directModifier + '\'' +
                ", indirectName='" + indirectName + '\'' +
                ", indirectType='" + indirectType + '\'' +
                ", indirectPlural=" + indirectPlural +
                ", indirectCount=" + indirectCount +
                ", indirectModifier='" + indirectModifier + '\'' +
                ", question=" + question +
                ", verb='" + verb + '\'' +
                ", verbType='" + verbType + '\'' +
                ", adverb='" + adverb + '\'' +
                ", tense=" + tense +
                ", polite=" + polite +
                ", politeFirst=" + politeFirst +
                '}';
    }
}
