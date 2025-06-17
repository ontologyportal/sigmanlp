package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;

import java.util.*;

/**
 * **************************************************************
 */
public class LFeatureSets {

    public List<AVPair> modals = null;
    public Map<String, String> genders = null;
    public RandSet humans = null;
    public RandSet socRoles = null;
    public RandSet objects = null;
    public RandSet bodyParts = null;
    public Set<String> prevHumans = new HashSet<>();
    public RandSet processes = null;
    public List<String> frames = null;  // verb frames for the current process type


    public LFeatureSets(GenSimpTestData genSimpTestData) {
        this.genSimpTestData = genSimpTestData;

        //  get capabilities from axioms like
        //  (=> (instance ?GUN Gun) (capability Shooting instrument ?GUN))
        // indirect = collectCapabilities(); // TODO: need to restore and combine this filter with verb frames
        if (debug) System.out.println("LFeatureSets(): collect terms");
        genders = GenSimpTestData.humans;
        addUnknownsHumansOrgs(genders);
        humans = RandSet.listToEqualPairs(genders.keySet());

        modals = initModals();

        Set<String> roles = GenSimpTestData.kbLite.getInstancesForType("SocialRole");
        //if (debug) System.out.println("LFeatureSets(): SocialRoles: " + roles);
        Collection<AVPair> roleFreqs = genSimpTestData.findWordFreq(roles);
        socRoles = RandSet.create(roleFreqs);

        Set<String> parts = GenSimpTestData.kbLite.getInstancesForType("BodyPart");
        //if (debug) System.out.println("LFeatureSets(): BodyParts: " + parts);
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
        if (debug) System.out.println("LFeatureSets(): OrganicObjects and Artifacts: " + objs);
        Collection<AVPair> objFreqs = genSimpTestData.findWordFreq(objs2);
        addUnknownsObjects(objFreqs);
        if (debug) System.out.println("LFeatureSets(): create objects");
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

}