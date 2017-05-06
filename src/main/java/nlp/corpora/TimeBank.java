package nlp.corpora;

import com.articulate.sigma.Formula;
import com.articulate.sigma.SimpleDOMParser;
import com.articulate.sigma.SimpleElement;
import com.articulate.sigma.StringUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.time.*;
import nlp.pipeline.Pipeline;
import semRewrite.datesandnumber.Tokens;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 Copyright 2017 Articulate Software

 Author: Adam Pease apease@articulatesoftware.com

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program ; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 MA  02111-1307 USA
 */
public class TimeBank {

    public static ArrayList<String> suMarkup = new ArrayList<>();
    public static int correct = 0;
    public static int incorrect = 0;
    public static int correctMark = 0;
    public static int incorrectMark = 0;

    public static int sumoCount = 0;

    private static Pipeline p = null;
    public static String anchorDate = "2017-04-21";
    private static Annotation wholeDocument = null;
    public static boolean initialized = false;

    /** ***************************************************************
     */
    private static String removeLeading0(String s) {

        return s.replaceAll("^0*","");
    }

    /** ***************************************************************
     */
    private static boolean isTemporalFunction(Timex t) {

        if (t.toXmlElement().getAttribute("temporalFunction").equals("true"))
            return true;
        else
            return false;
    }

    /** ***************************************************************
     */
    private static String getTimexTag(Timex t) {

        // return (t.toString().substring(0,1+t.toString().indexOf(">",1)));
        return (t.toString());
    }

    /** ***************************************************************
     */
    public static Formula parseDateString(String s) {

        System.out.println("TimeBank.parseDateString(): " + s);
        if (StringUtil.emptyString(s))
            return null;
        if (s.equals("PAST_REF"))
            return new Formula("(ImmediatePastFn " + parseDateString(anchorDate).toString() + ")");
        if (s.equals("FUTURE_REF"))
            return new Formula("(ImmediateFutureFn " + parseDateString(anchorDate).toString() + ")");
        if (s.equals("PRESENT_REF"))
            return new Formula(parseDateString(anchorDate).toString());
        StringBuffer sb = new StringBuffer();
        Formula f = new Formula();
        int dashIndex = s.indexOf('-');
        String year = "";
        if (dashIndex == -1) {
            if (s.matches("\\d+")) {
                sb.insert(0, "(YearFn " + removeLeading0(s));
                sb.append(")");
                f = new Formula(sb.toString());
                return f;
            }
            else
                return null;
        }
        else
            year = s.substring(0,dashIndex);
        int secondDash = s.indexOf('-',dashIndex + 1);
        int tIndex = s.indexOf('T');
        String spacer = "";

        if (year.indexOf("X") == -1) {
            if (sb.length() > 0)
                spacer = " ";
            sb.insert(0, "(YearFn " + removeLeading0(year) + spacer);
            sb.append(")");
        }
        String month = "";
        if (secondDash == -1)
            secondDash = s.length();
        month = s.substring(dashIndex + 1, secondDash);
        //System.out.println("TimeBank.parseDateString(): month: " + month);
        if (month.indexOf("X") != -1)
            return new Formula(sb.toString());
        if (month.indexOf("X") == -1 && !month.startsWith("Q")) {
            if (sb.length() > 0)
                spacer = " ";
            sb.insert(0, "(MonthFn " + removeLeading0(month) + spacer);
            sb.append(")");
        }
        else if (month.startsWith("W")) {
            if (sb.length() > 0)
                spacer = " ";
            sb.insert(0, "(WeekFn " + removeLeading0(month.substring(1)) + spacer);
            sb.append(")");
            return new Formula(sb.toString());
        }
        else if (month.startsWith("Q")) {
            if (sb.length() > 0)
                spacer = " ";
            sb.insert(0, "(QuarterYearFn " + removeLeading0(month.substring(1)) + spacer);
            sb.append(")");
            return new Formula(sb.toString());
        }
        if (secondDash == s.length())
            return new Formula(sb.toString());
        String day = "";
        if (tIndex == -1) {
            if (sb.length() > 0)
                spacer = " ";
            //System.out.println("TimeBank.parseDateString(): secondDash: " + secondDash);
            //System.out.println("TimeBank.parseDateString(): length: " + s.length());
            day = s.substring(secondDash+1,s.length());
            sb.insert(0,"(DayFn " + removeLeading0(day) + spacer);
            sb.append(")");
        }
        else {
            if (s.length() - tIndex > 0) {
                if (s.endsWith("MO")) {
                    if (sb.length() > 0)
                        spacer = " ";
                    sb.insert(0, "(MorningFn " + removeLeading0(day) + spacer);
                    sb.append(")");
                }
                if (s.endsWith("NI")) {
                    if (sb.length() > 0)
                        spacer = " ";
                    sb.insert(0, "(EveningFn " + removeLeading0(day) + spacer);
                    sb.append(")");
                }
            }

            int colonIndex = s.indexOf(':');
            if (colonIndex == -1)
                return new Formula(sb.toString());
            int secondColon = s.indexOf(':',colonIndex+1);
            day = s.substring(secondDash + 1, tIndex);
            if (sb.length() > 0)
                spacer = " ";
            if (!day.equals("XX")) {
                sb.insert(0, "(DayFn " + removeLeading0(day) + spacer);
                sb.append(")");
            }
            if (sb.length() > 0)
                spacer = " ";
            String hour = s.substring(tIndex+1, colonIndex);
            if (!hour.equals("XX")) {
                sb.insert(0, "(HourFn " + removeLeading0(hour) + spacer);
                sb.append(")");
            }
            if (sb.length() > 0)
                spacer = " ";
            if (secondColon == -1) {
                String minute = s.substring(colonIndex + 1, s.length());
                if (!minute.equals("XX")) {
                    sb.insert(0, "(MinuteFn " + removeLeading0(minute) + spacer);
                    sb.append(")");
                }
            }
            else {
                String minute = s.substring(colonIndex + 1, secondColon);
                if (!minute.equals("XX")) {
                    sb.insert(0, "(MinuteFn " + removeLeading0(minute) + spacer);
                    sb.append(")");
                }
                if (sb.length() > 0)
                    spacer = " ";
                String second = s.substring(secondColon, s.length());
                if (!second.equals("XX")) {
                    sb.insert(0, "(SecondFn " + removeLeading0(second) + spacer);
                    sb.append(")");
                }
            }
        }
        //System.out.println("in TimeBank.parseDateString(): " + sb.toString());
        return new Formula(sb.toString());
    }

    /** ***************************************************************

    private static Formula toSUMOFromDate(Timex time) {

        System.out.println("in TimeBank.toSUMOFromDate(): ");
        Formula f = new Formula();
        StringBuffer sb = new StringBuffer();
        if (!time.timexType().equals("DATE"))
            return null;
        Calendar c;
        try {
            c = time.getDate();
        }
        catch (Exception uoe) { // UnsupportedOperationException when date isn't fully specified
            System.out.println(uoe.getMessage());
            if (time.value() != null)
                return parseDateString(time.value());
            else
                return null;
        }
        if (c.isSet(c.YEAR)) {
            sb.insert(0, "(YearFn " + c.get(c.YEAR));
            sb.append(")");
        }
        if (c.isSet(c.MONTH)) {
            sb.insert(0,"(MonthFn " +  c.get(c.MONTH) + " ");
            sb.append(")");
        }
        if (c.isSet(c.DAY_OF_MONTH)) {
            sb.insert(0,"(DayFn " +  c.get(c.DAY_OF_MONTH) + " ");
            sb.append(")");
        }
        if (time.value() != null && time.value().indexOf("T") != -1) {
            if (c.isSet(c.HOUR_OF_DAY)) {
                sb.insert(0, "(HourFn " + c.get(c.HOUR_OF_DAY) + " ");
                sb.append(")");
            }
            if (c.isSet(c.MINUTE)) {
                sb.insert(0, "(MinuteFn " + c.get(c.MINUTE) + " ");
                sb.append(")");
            }
            if (c.isSet(c.SECOND)) {
                sb.insert(0, "(SecondFn " + c.get(c.SECOND) + " ");
                sb.append(")");
            }
        }
        f.read(sb.toString());
        return f;
    } */

    /** ***************************************************************

    private static Formula toSUMOFromTime(Timex time) {

        System.out.println("in TimeBank.toSUMOFromTime(): ");
        Formula f = new Formula();
        StringBuffer sb = new StringBuffer();
        if (!time.timexType().equals("TIME"))
            return null;
        Calendar c;
        try {
            c = time.getDate();
        }
        catch (Exception uoe) { // UnsupportedOperationException when date isn't fully specified
            System.out.println(uoe.getMessage());
            if (time.value() != null)
                return parseDateString(time.value());
            else
                return null;
        }
        if (c.isSet(c.YEAR)) {
            sb.insert(0, "(YearFn " + c.get(c.YEAR));
            sb.append(")");
        }
        if (c.isSet(c.MONTH)) {
            sb.insert(0,"(MonthFn " +  c.get(c.MONTH) + " ");
            sb.append(")");
        }
        if (c.isSet(c.DAY_OF_MONTH)) {
            sb.insert(0,"(DayFn " +  c.get(c.DAY_OF_MONTH) + " ");
            sb.append(")");
        }
        if (c.isSet(c.HOUR_OF_DAY)) {
            sb.insert(0,"(HourFn " +  c.get(c.HOUR_OF_DAY) + " ");
            sb.append(")");
        }
        if (c.isSet(c.MINUTE)) {
            sb.insert(0,"(MinuteFn " +  c.get(c.MINUTE) + " ");
            sb.append(")");
        }
        if (c.isSet(c.SECOND)) {
            sb.insert(0,"(SecondFn " +  c.get(c.SECOND) + " ");
            sb.append(")");
        }
        f.read(sb.toString());
        return f;
    } */

    /** ***************************************************************
     */
    private static Formula toSUMOFromDurationString(String tString) {

        Formula f = new Formula();
        if (tString == null)
            return null;
        StringBuffer sb = new StringBuffer();
        // from http://www.timeml.org/timeMLdocs/TimeML.xsd
        //Pattern p = Pattern.compile("P((((\\p{Nd}+|X{1,2})Y)?((\\p{Nd}+|X{1,2})M)?" +
        //        "((\\p{Nd}+|X{1,2})D)?(T((\\p{Nd}+|X{1,2})H)?((\\p{Nd}+|X{1,2})M)?" +
        //        "((\\p{Nd}+|X{1,2})S)?)?)|((\\p{Nd}+|X{1,2}))(W|L|E|C|Q))");
        Pattern p = Pattern.compile("P((T?(\\d+|X{1,2})(Y|M|D|H|M|S)?)*(W|L|E|C|Q)?)");
        // PnYnMnDTnHnMnS
        Matcher m = p.matcher(tString);
        if (m.matches()) {
            //System.out.println("Timebank.toSUMOFromDuration() matching groups");
            for (int i = 1; i < m.groupCount(); i++) {
                System.out.println(i + ": " + m.group(i));
            }
            String count = m.group(3);
            char unit = m.group(4).charAt(0);
            String durationSymbol = "YearDuration";
            switch (unit) {
                case 'Y': break;
                case 'M': if (m.group(1).contains("T"))
                    durationSymbol = "MinuteDuration";
                else
                    durationSymbol = "MonthDuration"; break;
                case 'W': durationSymbol = "WeekDuration"; break;
                case 'D': durationSymbol = "DayDuration"; break;
                case 'H': durationSymbol = "HourDuration"; break;
                case 'S': durationSymbol = "SecondDuration"; break;
            }
            if (count.equals("X"))
                return null;
            else {
                return new Formula("(MeasureFn " + Integer.parseInt(count) + " " + durationSymbol + ")");
            }
        }
        else {
            //System.out.println("Timebank.toSUMOFromDuration() no match");
        }
        f.read(sb.toString());
        return f;
    }

    /** ***************************************************************
     */
    private static Formula toSUMOFromDuration(Timex time) {

        System.out.println("in TimeBank.toSUMOFromDuration(): ");
        if (!time.timexType().equals("DURATION"))
            return null;
        String tString = time.value();
        return toSUMOFromDurationString(tString);
    }

    /** ***************************************************************

    public static Formula toSUMO(Timex time) {

        System.out.println("in TimeBank.toSUMO(): ");
        Formula f = new Formula();
        StringBuffer sb = new StringBuffer();
        String type = time.timexType(); // 'DATE' | 'TIME' | 'DURATION' | 'SET'
        Calendar c;
        if (type.equals("DATE"))
            return toSUMOFromDate(time);
        else if (type.equals("TIME"))
            return toSUMOFromTime(time);
        else if (type.equals("DURATION"))
            return toSUMOFromDuration(time);
        return null;
    } */

    /** ***************************************************************
     */
    public static void init() {

        if (initialized)
            return;
        System.out.println("in TimeBank.init(): ");
        Properties props = new Properties();
        String propString = "tokenize, ssplit, pos, lemma, ner";
        p = new Pipeline(true,propString);
        p.pipeline.addAnnotator(new TimeAnnotator("sutime", props));
        initialized = true;
        System.out.println("in TimeBank.init(): completed initialization");
    }

    /** ***************************************************************
     * @return the SUMO expression, if possible, null otherwise.  Store
     * the XML TIMEX markup in the global variable suMarkup
     */
    public static Formula process(String sentence) {

        wholeDocument = new Annotation(sentence);
        wholeDocument.set(CoreAnnotations.DocDateAnnotation.class, anchorDate);
        p.pipeline.annotate(wholeDocument);
        suMarkup = new ArrayList<>();
        List<CoreMap> timexAnnsAll = wholeDocument.get(TimeAnnotations.TimexAnnotations.class);
        if (timexAnnsAll != null) {
            for (CoreMap cm : timexAnnsAll) {
                System.out.println("process(): SU contents: " + cm);
                Timex time = cm.get(TimeAnnotations.TimexAnnotation.class);
                System.out.println("process():  SU timex: " + time);
                suMarkup.add(getTimexTag(time));  // used later in processFile()
                if (time.timexType().equals("DATE") || time.timexType().equals("TIME")) {
                    return parseDateString(time.value());
                }
                else {
                    if (time.timexType().equals("DURATION"))
                        return toSUMOFromDuration(time);
                }
            }
        }
        return null;
    }

    /** ***************************************************************
     */
    public static void test(String input, String expectMarkup, String expectSUMO) {

        System.out.println("-----------------------------------------");
        Formula sumo = process(input);
        if (!StringUtil.emptyString(expectSUMO)) {
            Formula expS = new Formula(expectSUMO);
            if (expS.equals(sumo))
                correct++;
            else
                incorrect++;
        }
        if (!StringUtil.emptyString(expectMarkup)) {
            if (expectMarkup.equals(suMarkup))  // set as a global variable in process()
                correctMark++;
            else
                incorrectMark++;
        }
        System.out.println("-----------------------------------------");
    }

    /** ***************************************************************
     */
    public static void testAll() {

        System.out.println("in TimeBank.testAll(): ");
        init();
        String mark = "";
        String sumo = "";
        String inputSentence = "She was born at 4:35pm on 18 Feb 1997.";
        test(inputSentence,"","");
        inputSentence = "The 20th of July is my birthday!";
        sumo = "(DayFn 20\n" +
                "  (MonthFn 6\n" +
                "    (YearFn 2017)))";
        test(inputSentence,"",sumo);
        inputSentence = "Four days from today I will fly to Brussels.";
        sumo = "(DayFn 25\n" +
                "  (MonthFn 3\n" +
                "    (YearFn 2017)))";
        test(inputSentence,"",sumo);
        inputSentence = "It was 12 long nights for the journey.";
        test(inputSentence,"","");
        inputSentence = "It was 12 days for the journey.";
        test(inputSentence,"","");
        inputSentence = "Four score and seven years ago our forefathers founded a new nation, conceived in liberty.";
        sumo = "(DayFn 21\n" +
                "  (MonthFn 3\n" +
                "    (YearFn 1930)))";
        test(inputSentence,"",sumo);
        inputSentence = "He snored every night.";
        test(inputSentence,"","");
        inputSentence = "We go out to eat once or twice a week.";
        test(inputSentence,"","");
        inputSentence = "Most days I feel like dancing.";
        test(inputSentence,"","");
        inputSentence = "I went to sleep early last night.";
        mark = "<TIMEX3 mod=\"EARLY\" tid=\"t1\" type=\"TIME\" value=\"2017-04-20TNI\">";
        test(inputSentence,mark,""); // <TIMEX2 VAL=“2000-10-31TNI” MOD=“START”>early last night</TIMEX2>
        System.out.println("correct: " + correct);
        System.out.println("incorrect: " + incorrect);
        System.out.println("correctMark: " + correctMark);
        System.out.println("incorrectMark: " + incorrectMark);
    }

    /** ***************************************************************

    public static void processSentence (String sent, String clean) {

        //System.out.println("in TimeBank.processSentence(): " + clean);
        Formula f = process(clean);
    } */

    /** ***************************************************************

    public static void score (SimpleElement te, ArrayList<String> markup) {

        String markupString = te.getChildByFirstTag("TIMEX").toString();
        String expectMarkup = markupString.substring(0,markupString.indexOf(">")+1);
        if (!StringUtil.emptyString(expectMarkup)) {
            if (expectMarkup.equals(markup))  // set as a global variable in process()
                correctMark++;
            else
                incorrectMark++;
        }
    } */

    /** ***************************************************************
     */
    public static boolean equalTimex (String mark, String tag) {

        Pattern p = Pattern.compile("type=\"([^\"]+)\"");
        Matcher m = p.matcher(mark);
        if (!m.find())
            return false;
        String t1type = m.group(1);
        m = p.matcher(tag);
        if (!m.find())
            return false;
        String t2type = m.group(1);
        if (!t2type.equals(t1type)) {
            if (! (mark.equals("SET") && tag.equals("DURATION")))
                return false;
        }

        p = Pattern.compile("value=\"([^\"]+)\"");
        m = p.matcher(mark);
        if (!m.find())
            return false;
        String t1value = m.group(1);
        m = p.matcher(tag);
        if (!m.find())
            return false;
        String t2value = m.group(1);
        if (!t2value.equals(t1value))
            return false;

        p = Pattern.compile(">([^<]+)</TIMEX3");
        m = p.matcher(mark);
        if (!m.find())
            return false;
        String t1cont = m.group(1);
        m = p.matcher(tag);
        if (!m.find())
            return false;
        String t2cont = m.group(1);
        if (!t2cont.equals(t1cont))
            return false;

        return true;
    }

    /** ***************************************************************
     * Check to see that the tags generated by SUtime match the tags in
     * the answer key.  Compare only the type, value and contents of the tag
     */
    public static void score (ArrayList<String> tags, ArrayList<String> suMarkup, Formula f) {

        if (tags.size() > 0)
            System.out.println("score(): markup: " + tags);
        if (suMarkup.size() > 0)
            System.out.println("score(): SU: " + suMarkup);
        System.out.println("score(): anchor: " + anchorDate);
        if (f != null && !StringUtil.emptyString(f.theFormula))
            sumoCount++;
        else
            System.out.println("no sumo");
        for (String tag : tags) {
            if (!tag.startsWith("<TIMEX3"))
                continue;
            boolean found = false;
            for (String mark : suMarkup) {
                if (!StringUtil.emptyString(tag)) {
                    if (equalTimex(mark,tag))  // set as a global variable in process()
                        found = true;
                }
            }
            if (found) {
                correctMark++;
                System.out.println("score(): equal ");
            }
            else {
                incorrectMark++;
                System.out.println("score(): not equal ");
            }
        }
    }

    /** ***************************************************************
     * files can either be arranged with
     * <TimeML>
     *     <BODY>
     *         <bn_episode_trans>
     *             <TEXT></TEXT>
     *         </bn_episode_trans>
     *     </BODY>
     * </TimeML>
     *
     * or
     * <TimeML>
     *    <TEXT></TEXT>
     * </TimeML>

    public static void process (SimpleElement wholeDoc) {

        System.out.println("process(): " + wholeDoc);
        SimpleElement se = wholeDoc.getChildByFirstTag("TimeML");
        if (se != null)
            se = se.getChildByFirstTag("BODY");
        else
            se = wholeDoc.getChildByFirstTag("BODY");
        if (se != null)
            se = se.getChildByFirstTag("bn_episode_trans");
        else
            se = wholeDoc.getChildByFirstTag("bn_episode_trans");
        SimpleElement text;
        if (se != null)
            text = se.getChildByFirstTag("TEXT");
        else
            text = wholeDoc.getChildByFirstTag("TEXT");
        System.out.println("process(): " + text);
        ArrayList<SimpleElement> elems = text.getChildElements();
        for (SimpleElement simp :  elems) {
            if (simp.getTagName().equals("turn")) {
                ArrayList<SimpleElement> telems = simp.getChildElements();
                for (SimpleElement te :  telems) {
                    if (te.getTagName().equals("s")) {
                        String sent = te.getText();
                        String clean = StringUtil.removeHTML(sent);
                        processSentence(sent,clean);
                        score(te,markup);
                    }
                }
            }
            else if (simp.getTagName().equals("s")) {
                String sent = simp.getText();
                String clean = StringUtil.removeHTML(sent);
                processSentence(sent,clean);
                score(simp,markup);
            }
        }
    } */

    /** ***************************************************************
     */
    public static ArrayList<String> getTimexTagsFromString (String st) {

        ArrayList<String> result = new ArrayList<String>();
        Pattern p = Pattern.compile("(<TIMEX3[^>]+>[^<]+</TIMEX3>)");
        Matcher m = p.matcher(st);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    /** ***************************************************************
     * Process a single TimeBank file, opened on a BufferedReader.
     * Extract one sentence at a time.
     */
    public static void getAnchorDate(String tag) {

        System.out.println("in getAnchorDate(): ");
        if (tag.contains("CREATION_TIME")) {
            Pattern p = Pattern.compile("value=\"([^\"]+)");
            Matcher m = p.matcher(tag);
            if (m.find()) {
                String newAnchorDate = m.group(1);
                if (!newAnchorDate.equals(anchorDate)) {
                    anchorDate = newAnchorDate;
                    System.out.println("getAnchorDate(): " + anchorDate);
                }
            }
        }
    }

    /** ***************************************************************
     * Process a single TimeBank file, opened on a BufferedReader.
     * Extract one sentence at a time.
     */
    public static void processFile (BufferedReader br) {

        StringBuffer sb = new StringBuffer();
        StringBuffer sent = new StringBuffer();
        try {
            while (br.ready()) {
                char c = (char) br.read();
                if ((sb.length() == 0 && c == '<') || sb.length() > 0)
                    sb.append(c);
                if (sb.toString().trim().equals("<s>")) {
                    while (br.ready() && !sent.toString().endsWith("</s>"))
                        sent.append((char) br.read());

                    String sentStr = sent.toString().substring(0,sent.toString().length()-4);
                    String clean = StringUtil.removeHTML(sentStr);
                    System.out.println("TimeBank.processFile(): " + clean);
                    ArrayList<String> tags = getTimexTagsFromString(sentStr);
                    Formula f = process(clean);
                    if (f != null && !StringUtil.emptyString(f.theFormula))
                        System.out.println("processFile(): SUMO: " + f.toString());
                    score(tags,suMarkup,f);
                    System.out.println("------------------");
                    sb = new StringBuffer();
                    sent = new StringBuffer();
                }
                else if (sb.toString().trim().equals("<TIMEX3")) { // timex not wrapped in <s> is an anchor
                    while (br.ready() && !sent.toString().endsWith("</TIMEX3>"))
                        sent.append((char) br.read());
                    String sentStr = "<TIMEX3" + sent.toString().substring(0,sent.toString().length()-4);
                    getAnchorDate(sentStr);
                    System.out.println("------------------");
                    sb = new StringBuffer();
                    sent = new StringBuffer();
                }
                else if (sb.length() > 7)
                    sb = new StringBuffer();
            }
        }
        catch (IOException ioe) {
            System.out.println("Error in TimeBank.processFile(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }

    /** ***************************************************************
     * Gather a set of .tml files from a given directory, then pass
     * control to process() to extract and test temporal data
     */
    public static void testTimeBankNew(String dir) {

        init();
        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".tml")) {
                    File f = new File(filePath.toString());
                    if (!f.exists())
                        return;
                    try {
                        System.out.println("TimeBank.testTimeBankNew(): file " + f);
                        BufferedReader br = new BufferedReader(new FileReader(f));
                        processFile(br);
                    }
                    catch (java.io.FileNotFoundException ioe) {
                        System.out.println("Error in TimeBank.testTimeBankNew(): " + ioe.getMessage());
                        ioe.printStackTrace();
                    }
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in TimeBank.testTimeBankNew(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
        System.out.println("correct: " + correct);
        System.out.println("incorrect: " + incorrect);
        System.out.println("correctMark: " + correctMark);
        System.out.println("incorrectMark: " + incorrectMark);
        System.out.println("sumoCount: " + sumoCount);
    }

    /** ***************************************************************
     * Gather a set of .tml files from a given directory, then pass
     * control to process() to extract and test temporal data

    public static void testTimeBank(String dir) {

        init();
        try {
            Files.walk(Paths.get(dir)).forEach(filePath -> {
                if (Files.isRegularFile(filePath) &&
                        filePath.toString().endsWith(".tml")) {
                    SimpleElement se = SimpleDOMParser.readFile(filePath.toString());
                    process(se);
                }
            });
        }
        catch (IOException ioe) {
            System.out.println("Error in OntoNotes.readNameFiles(): " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }
*/
    /** ***************************************************************
     * extract temporal markup from a paragraph of text

    public static void processParagraph(String para) {

        System.out.println("TimeBank.processParagraph()\n " + para);
        Annotation wholeDocument = new Annotation(para);
        wholeDocument.set(CoreAnnotations.DocDateAnnotation.class, anchorDate);
        p.pipeline.annotate(wholeDocument);
        //List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);

        //System.out.println("text:" + wholeDocument.get(CoreAnnotations.TextAnnotation.class));
        List<CoreMap> timexAnnsAll = wholeDocument.get(TimeAnnotations.TimexAnnotations.class);
        if (timexAnnsAll != null) {
            for (CoreMap cm : timexAnnsAll) {
                List<CoreLabel> tokens = cm.get(CoreAnnotations.TokensAnnotation.class);
                System.out.println("contents: " + cm);
                Timex time = cm.get(TimeAnnotations.TimexAnnotation.class);
                System.out.println("SU timex: " + time);
                markup.add(getTimexTag(time));
                System.out.println("SU markup: " + markup);
                System.out.println("SU type: " + time.timexType());
                System.out.println("SU value: " + time.value());
            }
        }
    } */

    /** ***************************************************************
     * Read a set of lines and process to extract temporal data.
     * Assumes that a blank line is a reasonable break to process the
     * preceding group of text.

    public static void processLines(ArrayList<String> lines) {

        StringBuffer sb = new StringBuffer();
        for (String s : lines) {
            if (StringUtil.emptyString(s) && sb.length() > 0) {
                processParagraph(sb.toString());
                sb = new StringBuffer();
            }
            else {
                sb.append(s);
            }
        }
    } */

    /** ***************************************************************
     * Read one particular text file to extract temporal data

    public static void processText(String filename) {

        System.out.println(" TimeBank.processText() reading file " + filename);
        ArrayList<String> result = new ArrayList<>();
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {
                line = StringUtil.removeHTML(line);
                result.add(line);
            }
            processLines(result);
        }
        catch (IOException i) {
            System.out.println("Error in TimeBank.processText() reading file " + filename + ": " + i.getMessage());
            i.printStackTrace();
        }
    } */

    /** ***************************************************************
     */
    public static void testText() {

        anchorDate = "2015-06-01";
        //String filename = "/home/apease/Infosys/SampleDocuments/IBEW.txt";
       // String filename = "/home/apease/Infosys/SampleDocuments/BofAonlineBankingAgreement.txt";
        //String filename = "/home/apease/corpora/timebank_1_2/data/timeml/ABC19980108.1830.0711.tml";
        String filename = "/home/apease/Infosys/SampleDocuments/accountingPnP-Wegner.txt";

        init();
        //processText(filename);
    }

    /** ***************************************************************
     */
    public static void testParseDateString() {

        System.out.println("test parse date string: " + parseDateString("2017-04-21TEV"));
    }

    /** ***************************************************************
     */
    public static void interactive() {

        BufferedReader d = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("type 'quit' (without the quotes) on its own line to quit");
        String line = "";
        try {
            while (!line.equals("quit")) {
                System.out.print("> ");
                line = d.readLine();
                if (!line.equals("quit"))
                    System.out.println(process(line));
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("error in TimeBank.interactive()");
        }
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        //testAll();

        if (args.length == 0 || args[0].equals("-h")) {
            System.out.println("-c test corpus ");
            System.out.println("-h show this help ");
            System.out.println("-t convert one value string to SUMO ");
            System.out.println("-t convert one duration string to SUMO ");
            System.out.println("-p one quoted sentence into TIMEX tags and SUMO ");
            System.out.println("-i interactive mode ");
        }
        else if (args[0].equals("-c")) {
            testTimeBankNew("/home/apease/corpora/timebank_1_2/data/extra");
        }
        else if (args[0].equals("-t")) {
            System.out.println("convert " + args[1]);
            System.out.println(parseDateString(args[1]));
        }
        else if (args[0].equals("-d")) {
            System.out.println("convert " + args[1]);
            System.out.println(toSUMOFromDurationString(args[1]));
        }
        else if (args[0].equals("-p")) {
            init();
            System.out.println("anchor date: " + anchorDate);
            System.out.println("convert " + args[1]);
            System.out.println(process(args[1]));
        }
        else if (args[0].equals("-i")) {
            init();
            System.out.println("anchor date: " + anchorDate);
            interactive();
        }
        //testTimeBankNew("/home/apease/corpora/timebank_1_2/test");
        //testText();
        //testParseDateString();
    }
}
