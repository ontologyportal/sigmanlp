package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;

import java.util.*;

/**
 * **************************************************************
 */
public class LFeatures {

    private static final boolean debug = false;
    private static final Random rand = new Random();

    public static final int NOTIME = -1;
    public static final int PAST = 0;         // spoke, docked
    public static final int PASTPROG = 1;     // was speaking, was docking
    public static final int PRESENT = 2;      // speaks, docks
    public static final int PROGRESSIVE = 3;  // is speaking, is docking
    public static final int FUTURE = 4;       // will speak, will dock
    public static final int FUTUREPROG = 5;   // will be speaking, will be docking
    public static final int IMPERATIVE = 6;   // treat imperatives like a tense

    public boolean testMode = false;

    public boolean attNeg = false; // for propositional attitudes
    public boolean attPlural = false;
    public int attCount = 1;
    public String attSubj = null; // the agent holding the attitude
    public String attitude = "None";
    public String attitudeModifier = ""; // adjective
    public boolean negatedModal = false;
    public boolean negatedBody = false;
    public AVPair modal = new AVPair("None", "none"); // attribute if SUMO ModalAttribute, value is English
    public String directPrep = "";
    public String indirectPrep = "";
    public String secondVerb = ""; // the verb word that appears as INFINITIVE or VERB-ing or V-ing in the frame
    public String secondVerbType = ""; // the SUMO type of the second verb
    public String secondVerbSynset = "";
    public String secondVerbModifier= ""; // adverb
    public String subj = null;
    public String subjName = "";
    public String subjectModifier = ""; // adjective
    public boolean subjectPlural = false;
    public int subjectCount = 1;
    
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

    // Note that the frame is destructively modified as we proceed through the sentence
    public String frame = null; // the particular verb frame under consideration.
    public String framePart = null; // the frame that gets "consumed" during processing
    public List<String> frames = null;  // verb frames for the current process type
    
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
    public int tense = -1; // GenSimpTestData.NOTIME;
    public boolean polite = false;  // will a polite phrase be used for a sentence if it's an imperative
    public boolean politeFirst = true; // if true and an imperative and politness used, put it at the beginning of the sentence, otherwise at the end




    /** ***************************************************************
     * Tense methods
     */
    public boolean isProgressive() {
        return tense == PROGRESSIVE || tense == PASTPROG || tense == FUTUREPROG;
    }
    public boolean isPastProgressive() { return tense == PASTPROG; }
    public boolean isPast() { return tense == PAST; }
    public boolean isPresent() { return tense == PRESENT; }
    public boolean isFuture() { return tense == FUTURE; }
    public boolean isImperative() { return tense == IMPERATIVE; }
    public boolean noTense() { return tense == NOTIME; }
    public static int getRandomTense() { return rand.nextInt(IMPERATIVE+1) - 1; }

    public String printTense() {

        switch (tense) {
            case -1: return "NOTIME";
            case 0:  return "PAST";
            case 1:  return "PASTPROG";
            case 2:  return "PRESENT";
            case 3:  return "PROGRESSIVE";
            case 4:  return "FUTURE";
            case 5:  return "FUTUREPROG";
            case 6:  return "IMPERATIVE";
        }
        return "";
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
