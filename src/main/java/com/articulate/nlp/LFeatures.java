package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * **************************************************************
 */
public class LFeatures {

    private static final boolean debug = false;
    public boolean testMode = false;
    private static final Random rand = new Random();

    public static final int NOTIME = -1;
    public static final int PAST = 0;         // spoke, docked
    public static final int PASTPROG = 1;     // was speaking, was docking
    public static final int PRESENT = 2;      // speaks, docks
    public static final int PROGRESSIVE = 3;  // is speaking, is docking
    public static final int FUTURE = 4;       // will speak, will dock
    public static final int FUTUREPROG = 5;   // will be speaking, will be docking
    public static final int IMPERATIVE = 6;   // treat imperatives like a tense

    public boolean attNeg = false; // for propositional attitudes
    public boolean attPlural = false;
    public int attCount = 1;
    public String attSubjType = null;
    public String attSubj = null; // the agent holding the attitude
    public String attitude = "None";
    public String attitudeModifier = ""; // adjective
    public LFeatureSets.Word attWord = null;
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
    boolean hastime = false; // has just a time
    boolean hasdate = false; // has just a date
    boolean hasdatetime = false; // has both a date and a time
    boolean hasdateORtime = false;
    String dateEng = null;
    String timeEng = null;
    String yearLog = null;
    String monthLog = null;
    String dayLog = null;
    String hourLog = null;
    public boolean polite = false;  // will a polite phrase be used for a sentence if it's an imperative
    public boolean politeFirst = true; // if true and an imperative and politness used, put it at the beginning of the sentence, otherwise at the end
    public String politeWord = null;

    public String englishSentence;
    public String logicFormula;

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
        hastime = false;
        hasdate = false;
        hasdatetime = false;
        hasdateORtime = false;
        dateEng = null;
        timeEng = null;
        yearLog = null;
        monthLog = null;
        dayLog = null;
        hourLog = null;
    }


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

    public void flushToEnglishLogic() {
        boolean startOfSentence = true;
        StringBuilder english = new StringBuilder();
        StringBuilder prop = new StringBuilder();

        // ATTITUDE
        if (!attitude.equals("None")) {
            startOfSentence = false;
            // English portion
            english.append(attSubj).append(" ");
            String that = "";
            if (rand.nextBoolean() || attitude.equals("desires"))
                that = "that ";
            if (attNeg)
                english.append("doesn't ").append(attWord.root).append(" ").append(that);
            else
                english.append(attWord.present).append(" ").append(that);
            if (attWord.term.equals("says"))
                english.append("\"");

            // Logic portion
            if (attNeg) {
                prop.append("(not (exists (?HA) (and  ");
            }
            else {
                prop.append("(exists (?HA) (and ");
            }
            String var = "?HA";
            if (attSubjType != null && attSubjType.equals("Human")) {
                prop.append("(instance ").append(var).append(" Human) ");
                prop.append("(names \"").append(attSubj).append("\" ").append(var).append(") ");
            }
            else {
                prop.append("(attribute ").append(var).append(" ").append(attSubjType).append(") ");
            }
            prop.append("(").append(attWord.term).append(" ?HA ");
        }

        // MODALS
        if (!modal.attribute.equals("None")) {
            if (negatedModal)
                english.append(modal.value.substring(0,5) + " not" + modal.value.substring(5));
            else
                english.append(modal.value);
            if (startOfSentence)
                english.replace(0,1,english.substring(0,1).toUpperCase());
            if (negatedModal)
                prop.append("(not (modalAttribute ");
            else
                prop.append("(modalAttribute ");
            startOfSentence = false;
        }

        // DO POLITENESS (IF FIRST)
        if (isImperative() && polite && politeFirst) {
            english.insert(0, politeWord + english);
        }

        // Set up logic for main part of sentence
        if ((attitude.equals("None") && modal.attribute.equals("None")) || attitude.equals("says"))
            startOfSentence = true;
        if (negatedBody)
            prop.append("(not ");
        prop.append("(exists (?H ?P ?DO ?IO) (and ");

        // DATE & TIME
        if (hasdateORtime) {
            if (hastime) {
                english.append(timeEng).append(" ");
                prop.append("(instance ?T (HourFn ").append(hourLog).append(")) (during ?P ?T) ");
            }
            if (!hastime && hasdate) {
                english.append(dateEng).append(" ");
                prop.append("(instance ?T (DayFn ").append(dayLog).append(" (MonthFn ").append(monthLog).append(" (YearFn ").append(yearLog).append(")))) (during ?P ?T) ");
            }
            if (!hastime && !hasdate && hasdatetime) { // sometimes add both
                prop.append("(instance ?T (HourFn ").append(hourLog).append(" (DayFn ").append(dayLog).append(" (MonthFn ").append(monthLog).append(" (YearFn ").append(yearLog).append("))))) (during ?P ?T) ");
                english.append(dateEng).append(" ");
            }
            startOfSentence = false;
        }


        englishSentence = english.toString();
        logicFormula = prop.toString();
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
