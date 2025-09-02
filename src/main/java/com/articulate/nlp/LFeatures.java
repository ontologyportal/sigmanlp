package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.utils.StringUtil;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * **************************************************************
 */
public class LFeatures {

    private static final boolean debug = false;
    public boolean testMode = false;
    private static final Random rand = new Random();
    private static final boolean GEN_ENG_WITH_OLLAMA = true;

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
    public String secondVerbModifier= ""; // adverb to second verb
    public String subj = null;
    public String subjName = "";
    public String subjType = null;
    public String subjectModifier = ""; // adjective
    public boolean subjectPlural = false; // Is the subject plural?
    public AVPair pluralSubj = null;      // Form of the the plural subject
    public int subjectCount = 1;

    // Note that the frame is destructively modified as we proceed through the sentence
    public String frame = null; // the particular verb frame under consideration.
    public String framePart = null; // the frame that gets "consumed" during processing
    public List<String> frames = null;  // verb frames for the current process type

    public String directName = null;  // the direct object
    public String directType = null;  // the direct object
    public String directSUMO = null;
    public boolean directPlural = false;
    public AVPair pluralDirect = null;
    public String directOther = null; // If the subj == dir object, add the word "another"
    public int directCount = 1;
    public String directModifier = ""; // adjective
    public String indirectName = null; // the indirect object
    public String indirectType = null; // the indirect object
    public boolean indirectPlural = false; // Is there indirect object plural?
    public String indirectSUMO = null;
    public AVPair pluralIndirect = null; // Holds the plural form
    public int indirectCount = 1;
    public String indirectModifier = ""; // adjective
    public String indirectCaseRole = null;
    public boolean question = false;
    public boolean exclamation = false;
    public String verb = "";
    public String verbConjugated = "";
    public String verbSynset = null;
    public String verbType = ""; // the SUMO class of the verb
    public String verbFrameCat = "";
    public String adverb = "";
    public String adverbSUMO = "";
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
    public boolean addPleaseToSubj = false;
    public boolean addShouldToSubj = false;
    public boolean addBodyPart = false; // add a body part to the subject
    public String bodyPart = null; // subject body part
    public AVPair pluralBodyPart = null;

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
        directSUMO = null;
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
        addPleaseToSubj = false;
        addShouldToSubj = false;
        addBodyPart = false;
        pluralBodyPart = null;
        pluralSubj = null;
        verbFrameCat = null;
        adverbSUMO = null;
        pluralDirect = null;
        directOther = null;
        pluralIndirect = null;
        indirectSUMO = null;
        question = false;
        exclamation = false;
        indirectCaseRole = null;
        verbConjugated = "";
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
            case 1:  return "PAST PROGRESSIVE";
            case 2:  return "PRESENT";
            case 3:  return "PROGRESSIVE";
            case 4:  return "FUTURE";
            case 5:  return "FUTURE PROGRESSIVE";
            case 6:  return "IMPERATIVE";
        }
        return "";
    }

    public void flushToEnglishLogic(KBLite kbLite) {
        StringBuilder english = new StringBuilder();
        StringBuilder prop = new StringBuilder();

        // ATTITUDE
        if (!attitude.equals("None") && !question) {
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
            if (negatedModal)
                prop.append("(not (modalAttribute ");
            else
                prop.append("(modalAttribute ");
        }

        // DO POLITENESS (IF FIRST)
        if (isImperative() && polite && politeFirst) {
            english.insert(0, politeWord + english);
        }

        // Set up logic for main part of sentence
        if (negatedBody)
            prop.append("(not ");
        prop.append("(exists (?H ?P ?DO ?IO ?V2) (and ");

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
        }

        // SUBJECT
        /****
         * ToDo: The way that subjType is used for subj vs every other PoS is inconsistent.
         * Everywhere else, ___Type is the SUMO term, except here. A good project would
         * be to make it consistent.
         */
        if (GenWordSelector.isFrameLiteStrategy()) {
            if (subjName == null || subjName.equals(""))
                english.append(subj).append(" ");
            else
                english.append(subjName).append(" ");
            prop.append("(instance ").append("?H ").append(subjType).append(")");
        }
        else if (subjType != null && subjType.equals("Human")) {
            if (question) {
                english.setLength(0);
                english.append(subj).append(" ");
            }
            else {
                String var = "?H";
                if (subj.equals("Human")) {
                    english.append(subjName).append(" ");
                    prop.append("(instance ").append(var).append(" Human) ");
                    prop.append("(names \"").append(subjName).append("\" ").append(var).append(") ");
                }
                else if (!subj.equals("You")) { // Its not a human, and its not a "You", so its a social role.
                    english.append(subjName).append(" ");
                    prop.append("(attribute ").append(var).append(" ").append(subj).append(") ");
                } //There is no appended english or logic for subj.equals("You")
            }
            if (subj.equals("You")) {
                if (addPleaseToSubj) {
                    english.append("Please, ");
                }
                else if (addShouldToSubj) {
                    english.append("You should ");
                }
            }
            if (attitude.equals("says") && subj.equals("You") && // remove "that" for says "You ..."
                    english.toString().endsWith("that \"")) {
                english.delete(english.length() - 7, english.length());
                english.append(" \""); // restore space and quote
            }
            else if (addBodyPart) {
                if (!subj.equals("You") && !subj.equals("Human")) { // Social Role a plumber... etc
                    english.deleteCharAt(english.length()-1); // delete trailing space
                    english.append("'s ");
                }
                else if (subj.equals("Human")) {
                    english.append("'s ");
                }
                english.append(bodyPart).append(" ");
                if (pluralBodyPart.attribute.equals("true"))
                    addSUMOplural(prop,bodyPart,pluralBodyPart,"?O");
                else
                    prop.append("(instance ?O ").append(bodyPart).append(") ");
                prop.append("(possesses ?H ?O) ");
            }
        }
        else { // Subject is Something
            if (question) {
                english.append(subj).append(" ");
            }
            else if (subjType.equals("Something")) {
                english.append(subj).append(" ");
                if (subjectPlural) {
                    addSUMOplural(prop, subjName, pluralSubj, "?H");
                }
                else
                    prop.append("(instance ?H ").append(subjName).append(") ");
                if (subjType.equals("Something is")) {
                    english.append("is ");
                }
            }
            else {  // frame must be "It..."
                if (subjType.equals("It is")) {
                    english.append("It is ");
                }
                else {
                    english.append("It ");
                }
            }
        }

        // ADD VERB
        english.append(verbConjugated).append(" ");
        prop.append("(instance ?P ").append(verbType).append(") ");
        if (!"".equals(adverb)) {
            prop.append("(manner ?P ").append(adverbSUMO).append(") ");
        }
        if (verbFrameCat != null && verbFrameCat.startsWith("Something"))
            prop.append("(involvedInEvent ?P ?H) ");
        else if (subj != null && subj.equalsIgnoreCase("What") &&
                !kbLite.isSubclass(verbType,"IntentionalProcess")) {
            prop.append("(involvedInEvent ?P ?H) ");
        }
        else if (subj != null &&
                (kbLite.isSubclass(subj,"AutonomousAgent") ||
                        kbLite.isInstanceOf(subj,"SocialRole") ||
                        subj.equalsIgnoreCase("Who") ||
                        subj.equals("You"))) {
            if (kbLite.isSubclass(verbType,"IntentionalProcess"))
                prop.append("(agent ?P ?H) ");
            else if (kbLite.isSubclass(verbType,"BiologicalProcess"))
                prop.append("(experiencer ?P ?H) ");
            else
                prop.append("(agent ?P ?H) ");
        }
        if (isPast())
            prop.append("(before (EndFn (WhenFn ?P)) Now) ");
        if (isPresent())
            prop.append("(temporallyBetween (BeginFn (WhenFn ?P)) Now (EndFn (WhenFn ?P))) ");
        if (isFuture())
            prop.append("(before Now (BeginFn (WhenFn ?P))) ");


        // ADD DIRECT OBJECT
        if (GenWordSelector.isFrameLiteStrategy()) {
            if (directName != null && !directName.equals("") && directType != null && !directType.equals("")) {
                english.append(directPrep).append(" ").append(directOther).append(directName).append(" ");
                prop.append("(TODO: ADD DIRECT OBJECT LOGIC)");
            }
        }
        else if (directType != null && !directType.equals("")) {
            if (directType != null && directType.equals("Human"))
                prop.append(directSUMO);
            if (kbLite.isSubclass(verbType, "Translocation") &&
                    (kbLite.isSubclass(directType, "Region") || kbLite.isSubclass(directType, "StationaryObject"))) {

                if (directName != null)
                    english.append("to ").append(directOther).append(directName).append(" ");
                else
                    english.append(secondVerb).append(" ");

                if (pluralDirect.attribute.equals("true"))
                    addSUMOplural(prop, directType, pluralDirect, "?DO");
                else
                    prop.append("(instance ?DO ").append(directType).append(") ");
                prop.append("(destination ?P ?DO) ");
            } else if (!"".equals(secondVerbType)) { //&& secondVerbType.equals("Human")
                if (!StringUtil.emptyString(directPrep))
                    english.append(directPrep);
                if (directName != null) {
                    english.append(directOther).append(directName).append(" ");
                    prop.append("(instance ?DO Human) ");
                } else
                    english.append(secondVerb).append(" ");
                prop.append("(instance ?V2 ").append(secondVerbType).append(") ");
                prop.append("(refers ?DO ?V2) ");
            } else if (directType != null) {
                if (directType.equals("Human"))
                    english.append(directPrep).append(directOther).append(directName).append(" ");
                else
                    english.append(directPrep).append(directOther).append(directName).append(" ");
                if (pluralDirect.attribute.equals("true"))
                    addSUMOplural(prop, directType, pluralDirect, "?DO");
                else
                    prop.append("(instance ?DO ").append(directType).append(") ");

                switch (directPrep) {
                    case "to ":
                        prop.append("(destination ?P ?DO) ");
                        break;
                    case "on ":
                        prop.append("(orientation ?P ?DO On) ");
                        break;
                    default:
                        if (kbLite.isSubclass(verbType, "Transfer")) {
                            prop.append("(objectTransferred ?P ?DO) ");
                        } else {
                            prop.append("(patient ?P ?DO) ");
                        }
                        break;
                }
            }
        }

        // INDIRECT OBJECT
        if (GenWordSelector.isFrameLiteStrategy()) {
            if (indirectType != null && !indirectType.equals("")) {
                english.append(indirectPrep).append(" ").append(indirectName);
                prop.append("(NEED TO ADD LOGIC FOR FRAMELITE STRATEGY)");
            }
        }
        else if (indirectType != null && (!"".equals(framePart) && framePart.contains("somebody") || framePart.contains("something"))) {
            english.append(indirectPrep).append(indirectName);
            if (pluralIndirect.attribute.equals("true"))
                addSUMOplural(prop, indirectType, pluralIndirect, "?IO");
            else {
                if (kbLite.isInstanceOf(indirectType, "SocialRole"))
                    prop.append("(attribute ?IO ").append(indirectType).append(") ");
                else
                    prop.append("(instance ?IO ").append(indirectType).append(") ");
            }
            if (framePart.contains("somebody"))
                prop.append(indirectSUMO);
            else
                prop.append("(instance ?IO ").append(indirectType).append(")");
        } else if (framePart.contains("INFINITIVE")) {
            //if (framePart.contains("to"))
            english.append(secondVerb).append(" ");
        }

        // ADD ENDING
        addEndingToEnglishLogic(english, prop);

        // FLUSH STRING BUILDER OBJECT
        englishSentence = english.toString().replaceAll("  "," ");
        englishSentence = capital(englishSentence);
        logicFormula = prop.toString();


        if (GEN_ENG_WITH_OLLAMA) {
            if (subjName == null || subjName.equals(""))
                subjName = subj;
            if (noTense()) {
                tense = PRESENT;
            }
            String prompt = "You are an expert linguist interfacing with a machine that only knows JSON format. " +
                    "This machine gives the following parts of speech (POS) in JSON format. ";
            prompt += "{\n";
            prompt += (negatedBody) ? "  \"negated\": \"negate the sentence\",\n" : "";
            prompt += (question) ? "  \"question\": \"yes\",\n" : "";
            prompt += "  \"subject\": \"" + subjName + "\",\n" +
                    "  \"verb\": \""+verb+"\",\n" +
                    "  \"verb tense\": \"" + printTense() + "\",\n" +
                    "  \"direct object\": \""+directName+"\",\n";
            prompt += (indirectName != null) ? "  \"indirect object\": \""+indirectName+"\"\n" : "";
            prompt += "}" + "\n\nGenerate an English sentence, ";
            prompt += (negatedBody) ? "negated, " : "";
            prompt += (question) ? "as a question, " : "";
            prompt += " and add articles, prepositions and punctuation as needed to make the sentence " +
                    "grammatically correct. " +
                    "Do not change the sentence to make it coherent, this is an academic exercise," +
                    "and some of the sentences will not make logical sense. ";
            prompt += (indirectName != null) ? " Use all parts of speech in the sentence, including the indirect object. " : "";
            prompt += (negatedBody) ? " A negated sentence means the event in the sentence doesn't happen  (i.e., the sentence 'Ken runs.' negated would be 'Ken does not run'." +
                    " The sentence 'Who advertised for a bus?' would be 'Who didn't advertise for a bus?'). " : "";
            prompt += (isProgressive()) ? " Then use the gerund form of the verb.\n\n" : "\n\n";

            prompt += "Give just the final resulting sentence in the following JSON format so it can be read by a machine: " +
                    "\n\n{\n  \"sentence\": \"<generated sentence>\"\n}\n";
            System.out.println("LFeatures.DELETE ME - Prompt: " + prompt);
            String llmResponse = GenUtils.askOllama(prompt);
            System.out.println("\n\nLLMResponse: " + llmResponse);
            // Regex to find the first {"sentence": "..."} JSON object
            String regex = "\\{\\s*\"sentence\"\\s*:\\s*\"([^\"]*)\"\\s*\\}";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(llmResponse);
            if (matcher.find()) {
                System.out.println("LFeatures.DELETE ME -    before sentence: " + englishSentence);
                englishSentence = matcher.group(1);
                System.out.println("LFeatures.DELETE ME - generated sentence: " + englishSentence + "\n\n\n\n");
            }
            else {
                System.out.println("LFeatures could not generate sentence with an LLM. Returing non-LLM generated english sentence: " + englishSentence);
            }
        }
    }


    public void addEndingToEnglishLogic(StringBuilder english, StringBuilder prop) {
        if (polite && !politeFirst)
            english.append(politeWord);
        if (english.toString().endsWith(" "))
            english.delete(english.length() - 1, english.length());
        if (exclamation)
            english.append("!");
        else if (english.indexOf(" ") != -1 &&
                questionWord(english.toString().substring(0, english.toString().indexOf(" "))))
            english.append("?");
        else
            english.append(".");
        if (attitude != null && attitude.equals("says"))
            english.append("\"");
        if (!"".equals(framePart) && framePart.contains("somebody") || framePart.contains("something"))
            prop.append("(").append(indirectCaseRole).append(" ?P ?IO) ");
        else
            prop.append(closeParens());

        if (subj != null && subj.equals("You")) {
            String newProp = prop.toString().replace(" ?H", " You");
            prop.setLength(0);
            prop.append(newProp);
        }
        if (!"".equals(framePart) && framePart.contains("somebody") || framePart.contains("something"))
            prop.append(closeParens());
    }


    /** ***************************************************************
     * Add SUMO content about a plural noun
     * @param prop is the formula to append to
     * @param term is the SUMO type of the noun
     * @param plural is the count of the plural as a String integer in the value field
     * @param var is the variable for the term in the formula
     */
    private static void addSUMOplural(StringBuilder prop, String term, AVPair plural, String var) {
        prop.append("(memberType ").append(var).append(" ").append(term).append(") ");
        prop.append("(memberCount ").append(var).append(" ").append(plural.value).append(") ");
    }


    /** ***************************************************************
     */
    public static String capital(String s) {

        if (debug) System.out.println("capital(): s: " + s);
        if (StringUtil.emptyString(s))
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** ***************************************************************
     */
    public boolean questionWord(String q) {

        return q.equalsIgnoreCase("who") || q.equalsIgnoreCase("what") || q.equalsIgnoreCase("when did") ||
                q.equalsIgnoreCase("did") || q.equalsIgnoreCase("where did") || q.equalsIgnoreCase("why did");
    }

    private String closeParens() {

        StringBuilder result = new StringBuilder();
        if (negatedBody) result.append(")");
        result.append(")) "); // close off the starting 'exists' and 'and'
        if (!modal.attribute.equals("None")) result.append(modal.attribute).append(")");
        if (negatedModal && !modal.attribute.equals("None")) result.append(")");
        if (!attitude.equals("None")) {
            result.append(")))");
            if (attNeg) result.append(")");
        }
        return result.toString();
    }

    public boolean isModalAttitudeOrPolitefirst() {
        return (!attitude.equals("None") || !attitude.equals("says")) ||
                !modal.attribute.equals("None") || (isImperative() && polite && politeFirst);
    }

    @Override
    public String toString() {
        return "LFeatures{" +
                "subj='" + subj + '\'' +
                ", subjName='" + subjName + '\'' +
                ", subjType='" + subjType + '\'' +
                ", subjectModifier='" + subjectModifier + '\'' +
                ", subjectPlural=" + subjectPlural +
                ", pluralSubj=" + String.valueOf(pluralSubj) +
                ", subjectCount=" + subjectCount +
                ", \nverb='" + verb + '\'' +
                ", verbConjugated='" + verbConjugated + '\'' +
                ", verbSynset='" + verbSynset + '\'' +
                ", verbType='" + verbType + '\'' +
                ", verbFrameCat='" + verbFrameCat + '\'' +
                ", adverb='" + adverb + '\'' +
                ", \ndirectName='" + directName + '\'' +
                ", directType='" + directType + '\'' +
                ", directSUMO='" + directSUMO + '\'' +
                ", adverbSUMO='" + adverbSUMO + '\'' +
                ", directPrep='" + directPrep + '\'' +
                ", directPlural=" + directPlural +
                ", pluralDirect=" + String.valueOf(pluralDirect) +
                ", directOther='" + directOther + '\'' +
                ", directCount=" + directCount +
                ", directModifier='" + directModifier + '\'' +
                ", \nindirectName='" + indirectName + '\'' +
                ", indirectType='" + indirectType + '\'' +
                ", indirectPlural=" + indirectPlural +
                ", indirectSUMO='" + indirectSUMO + '\'' +
                ", indirectPrep='" + indirectPrep + '\'' +
                ", pluralIndirect=" + String.valueOf(pluralIndirect) +
                ", indirectCount=" + indirectCount +
                ", indirectModifier='" + indirectModifier + '\'' +
                ", indirectCaseRole='" + indirectCaseRole + '\'' +
                ", \nsecondVerb='" + secondVerb + '\'' +
                ", secondVerbType='" + secondVerbType + '\'' +
                ", secondVerbSynset='" + secondVerbSynset + '\'' +
                ", secondVerbModifier='" + secondVerbModifier + '\'' +
                ", \nattNeg=" + attNeg +
                ", attPlural=" + attPlural +
                ", attCount=" + attCount +
                ", attSubjType='" + attSubjType + '\'' +
                ", attSubj='" + attSubj + '\'' +
                ", attitude='" + attitude + '\'' +
                ", attitudeModifier='" + attitudeModifier + '\'' +
                ", attWord=" + String.valueOf(attWord) +
                ", \nnegatedModal=" + negatedModal +
                ", negatedBody=" + negatedBody +
                ", \nmodal=" + String.valueOf(modal) +
                ", \nframe='" + frame + '\'' +
                ", framePart='" + framePart + '\'' +
                ", frames=" + String.valueOf(frames) +
                ", question=" + question +
                ", exclamation=" + exclamation +
                ", \ntense=" + tense +
                ", hastime=" + hastime +
                ", hasdate=" + hasdate +
                ", hasdatetime=" + hasdatetime +
                ", hasdateORtime=" + hasdateORtime +
                ", dateEng='" + dateEng + '\'' +
                ", timeEng='" + timeEng + '\'' +
                ", yearLog='" + yearLog + '\'' +
                ", monthLog='" + monthLog + '\'' +
                ", dayLog='" + dayLog + '\'' +
                ", hourLog='" + hourLog + '\'' +
                ", \npolite=" + polite +
                ", politeFirst=" + politeFirst +
                ", politeWord='" + politeWord + '\'' +
                ", addPleaseToSubj=" + addPleaseToSubj +
                ", addShouldToSubj=" + addShouldToSubj +
                ", \naddBodyPart=" + addBodyPart +
                ", bodyPart='" + bodyPart + '\'' +
                ", pluralBodyPart=" + String.valueOf(pluralBodyPart) +
                '}';
    }

}
