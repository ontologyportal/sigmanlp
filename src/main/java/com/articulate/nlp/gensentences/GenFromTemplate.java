package com.articulate.nlp.gensentences;

import com.articulate.nlp.GenUtils;
import com.articulate.nlp.KBLite;
import com.articulate.nlp.LFeatureSets;
import com.articulate.nlp.morphodb.MorphoDB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Performs synthetic sentence generation from JSON templates.
 ***************************************************************/
public class GenFromTemplate {

    public static String outputFileEnglish = "from_template-eng.txt";
    public static String outputFileLogic = "from_template-log.txt";
    public static KBLite kbLite;
    public static LFeatureSets lfeatsets;
    public static MorphoDB morphoDB;
    public static GenVerbHelper verbHelper;
    public static final Set<String> suppress = new HashSet<>(Arrays.asList());
    public static Map<Integer, WeightedSampler> slotSamplers = new HashMap<>();
    public static Map<Integer, WeightedSampler> verbSamplers = new HashMap<>();
    public static List<Templates.WeightedModal> modalValues = new ArrayList<>();

    private static class InstanceValue {
        private final boolean definite;
        private final String name;

        private InstanceValue(boolean definite, String name) {
            this.definite = definite;
            this.name = name;
        }
    }

    private static class SlotValues {
        private final Map<Integer, String> logicSlotValues;
        private final Map<Integer, String> englishSlotValues;
        private final Map<Integer, String> englishVerbValues;
        private final Map<Integer, InstanceValue> instanceValues;
        private final boolean logicNegate;

        private SlotValues(Map<Integer, String> logicSlotValues,
                           Map<Integer, String> englishSlotValues,
                           Map<Integer, String> englishVerbValues,
                           Map<Integer, InstanceValue> instanceValues,
                           boolean logicNegate) {
            this.logicSlotValues = logicSlotValues;
            this.englishSlotValues = englishSlotValues;
            this.englishVerbValues = englishVerbValues;
            this.instanceValues = instanceValues;
            this.logicNegate = logicNegate;
        }
    }

    private static class QuestionHandlingResult {
        private final boolean asQuestion;
        private final String englishFrame;

        private QuestionHandlingResult(boolean asQuestion, String englishFrame) {
            this.asQuestion = asQuestion;
            this.englishFrame = englishFrame;
        }
    }

    private static class FrameSelection {
        private final Templates.Tense selectedTense;
        private final boolean asQuestion;
        private final String englishFrame;

        private FrameSelection(Templates.Tense selectedTense, boolean asQuestion, String englishFrame) {
            this.selectedTense = selectedTense;
            this.asQuestion = asQuestion;
            this.englishFrame = englishFrame;
        }
    }

    private static class EnglishLogicPair {
        private final String english;
        private final String logic;

        private EnglishLogicPair(String english, String logic) {
            this.english = english;
            this.logic = logic;
        }
    }

    /***************************************************************
     * Builds indexed samplers for slot-like arrays (%1, %2... or %v1, %v2...).
     ***************************************************************/
    private static <T> Map<Integer, WeightedSampler> buildIndexedSamplers(T[] slots,
                                                                          BiFunction<Integer, T, WeightedSampler> samplerBuilder) {

        Map<Integer, WeightedSampler> result = new HashMap<>();
        for (int i = 0; i < slots.length; i++) {
            T slot = slots[i];
            if (slot == null) {
                result.put(i + 1, new WeightedSampler());
                continue;
            }
            WeightedSampler sampler = samplerBuilder.apply(i + 1, slot);
            result.put(i + 1, sampler != null ? sampler : new WeightedSampler());
        }
        return result;
    }

    /***************************************************************
     * Initializes generation resources.
     ***************************************************************/
    public static void init(String filePrefix, String morphoDbPath) {

        outputFileEnglish = filePrefix + "-eng.txt";
        outputFileLogic = filePrefix + "-log.txt";
        if (kbLite == null) {
            kbLite = new KBLite("SUMO");
            KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + "/KBs");
            WordNet.initOnce();
        }
        lfeatsets = GenUtils.initLFeatureSets(kbLite, suppress);
        morphoDB = MorphoDB.loadMorphoDatabase(morphoDbPath);
        verbHelper = new GenVerbHelper(morphoDB);
        System.out.println("Loaded " + morphoDB.size() + " morphology DB snapshot(s) from: " + morphoDbPath);
        GenUtils.createFileIfDoesNotExists(outputFileEnglish);
        GenUtils.createFileIfDoesNotExists(outputFileLogic);
    }

    /***************************************************************
     * Build one weighted sampler per slot from slot sumo_terms.
     ***************************************************************/
    public static Map<Integer, WeightedSampler> buildSlotSamplers(Templates.Template template) {

        Templates.Slot[] slots = template.getSlots();
        return buildIndexedSamplers(slots, (slotNumber, slot) -> {
            if (slot.getSumoTerms() == null || slot.getSumoTerms().isEmpty()) {
                return new WeightedSampler();
            }
            WeightedSampler slotSampler = new WeightedSampler();
            for (Templates.WeightedSumoTerm sumoTerm : slot.getSumoTerms()) {
                Set<String> candidates = getCandidatesForSlot(slot, sumoTerm);
                if (candidates == null || candidates.isEmpty()) {
                    if (sumoTerm.getWeight() > 0) {
                        System.err.println("Error: template '" + template.getName() + "' slot %" + slotNumber
                                + " class '" + sumoTerm.getName() + "' has no candidates for type="
                                + slot.getType().name().toLowerCase() + " but weight=" + sumoTerm.getWeight() + ".");
                        System.exit(1);
                    }
                    continue;
                }
                slotSampler.withClass(sumoTerm.getName(), sumoTerm.getWeight(), candidates);
            }
            return slotSampler;
        });
    }

    /***************************************************************
     * Gets candidates for a slot based on its type.
     ***************************************************************/
    private static Set<String> getCandidatesForSlot(Templates.Slot slot, Templates.WeightedSumoTerm sumoTerm) {

        if (slot.getType() == Templates.Slot.TermSelectionType.SUBCLASS) {
            Set<String> candidates = kbLite.getChildClasses(sumoTerm.getName());
            if (candidates != null) {
                candidates.add(sumoTerm.getName());
            }
            return candidates;
        }
        else if (slot.getType() == Templates.Slot.TermSelectionType.INSTANCE) {
            return kbLite.getInstancesForType(sumoTerm.getName());
        }
        return null;
    }

    /***************************************************************
     * Build one weighted sampler per verb slot from %v<number> entries.
     ***************************************************************/
    public static Map<Integer, WeightedSampler> buildVerbSamplers(Templates.Template template) {

        Templates.VerbSlot[] verbSlots = template.getVerbSlots();
        return buildIndexedSamplers(verbSlots, (slotNumber, verbSlot) -> {
            if (verbSlot.getVerbs() == null || verbSlot.getVerbs().isEmpty()) {
                return new WeightedSampler();
            }
            WeightedSampler verbSampler = new WeightedSampler();
            for (Templates.WeightedVerb weightedVerb : verbSlot.getVerbs()) {
                verbSampler.withCandidate(weightedVerb.getLemma(), weightedVerb.getWeight(), weightedVerb.getLemma());
            }
            return verbSampler;
        });
    }

    /***************************************************************
     * Replace %1, %2, ... style slots in a template.
     ***************************************************************/
    private static String replaceSlots(String frame, Map<Integer, String> slotValues) {

        Pattern pattern = Pattern.compile("%(\\d+)");
        Matcher matcher = pattern.matcher(frame);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slotNum = Integer.parseInt(matcher.group(1));
            String value = slotValues.get(slotNum);
            if (value == null) {
                value = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /***************************************************************
     * Replaces all %n{...} markers in a single string.
     * When negate=true the marker content is kept; when false
     * it is removed entirely.
     ***************************************************************/
    private static String applyNegationPattern(String input, boolean negate) {

        Pattern pattern = Pattern.compile("%n\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(negate ? matcher.group(1) : ""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /***************************************************************
     * Apply negation markers of the form %n{...} in both the
     * English and logic strings.
     ***************************************************************/
    private static EnglishLogicPair handleNegation(EnglishLogicPair pair, boolean negate) {

        return new EnglishLogicPair(
                applyNegationPattern(pair.english, negate),
                applyNegationPattern(pair.logic, negate));
    }

    /***************************************************************
     * Prepends a sampled modal phrase to English and wraps logic
     * with the corresponding SUMO modal term. Skips questions.
     ***************************************************************/
    private static EnglishLogicPair handleModals(EnglishLogicPair pair,
                                                 boolean asQuestion, Templates.Template template) {

        if (!template.isModalOn() || modalValues.isEmpty()
                || asQuestion || !(Math.random() < template.getModalFreq())) {
            return pair;
        }
        Templates.WeightedModal modal = sampleModal(modalValues);
        if (modal == null) {
            System.err.println("Error: template '" + template.getName()
                    + "' is missing modal values but modal_on=true and modal_freq=" + template.getModalFreq() + ".");
            System.exit(1); 
        }
        String english = modal.getText() + GenUtils.lowercaseFirstLetter(pair.english);
        String logic = "(modalAttribute " + " " + pair.logic + " " + modal.getSumoTerm() + ")";
        if (Math.random() < template.getModalNegFreq()) {
            logic = "(not " + logic + ")";
        }
        return new EnglishLogicPair(english, logic);
    }

    /***************************************************************
     * Selects a WeightedModal by weight from the given list.
     ***************************************************************/
    private static Templates.WeightedModal sampleModal(List<Templates.WeightedModal> modals) {

        int totalWeight = 0;
        for (Templates.WeightedModal modal : modals) {
            totalWeight += modal.getWeight();
        }
        if (totalWeight <= 0) {
            return null;
        }
        int draw = (int) (Math.random() * totalWeight);
        int running = 0;
        for (Templates.WeightedModal modal : modals) {
            running += modal.getWeight();
            if (draw < running) {
                return modal;
            }
        }
        return modals.get(modals.size() - 1);
    }

    /***************************************************************
     * English surface cleanup: collapse whitespace, fix punctuation
     * spacing, trim, and capitalize the first letter.
     ***************************************************************/
    private static String cleanEnglishSurface(String english) {

        if (english == null) {
            return null;
        }
        english = english
                .replace('_', ' ')
                .replaceAll(" {2,}", " ")
                .replaceAll("\\s+([,.;!])", "$1")
                .trim();
        return GenUtils.capitalizeFirstLetter(english);
    }

    /***************************************************************
     * Applies tense handling to logic markers of the form %t{...}.
     * NONE leaves internal text unchanged.
     ***************************************************************/
    private static EnglishLogicPair handleTense(EnglishLogicPair pair, Templates.Tense selectedTense) {

        Pattern pattern = Pattern.compile("%t\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(pair.logic);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String internalText = matcher.group(1);
            String replacement = internalText;
            switch (selectedTense) {
                case FUTURE:
                    replacement = wrapLogicFuture(internalText);
                    break;
                case PRESENT:
                    replacement = wrapLogicPresent(internalText);
                    break;
                case PAST:
                    replacement = wrapLogicPast(internalText);
                    break;
                case NONE:
                    replacement = internalText;
                    break;
                default:
                    System.err.println("Error: unrecognized tense '" + selectedTense.name() + "' for template tense handling.");
                    System.exit(1);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return new EnglishLogicPair(pair.english, result.toString());
    }

    /***************************************************************
     * Builds a weighted tense sampler once per template.
     ***************************************************************/
    private static WeightedSampler buildTenseSampler(Templates.Template template) {

        if (template == null || !template.isTenseOn()) {
            return null;
        }
        WeightedSampler sampler = new WeightedSampler()
                .withCandidate(Templates.Tense.NONE.name(), template.getTenseNoneWeight(), Templates.Tense.NONE.name())
                .withCandidate(Templates.Tense.PAST.name(), template.getTensePastWeight(), Templates.Tense.PAST.name())
                .withCandidate(Templates.Tense.PRESENT.name(), template.getTensePresentWeight(), Templates.Tense.PRESENT.name())
                .withCandidate(Templates.Tense.FUTURE.name(), template.getTenseFutureWeight(), Templates.Tense.FUTURE.name());
        return sampler.totalWeight > 0 ? sampler : null;
    }


    private static String wrapLogicFuture(String internalText) {

        return "(instance ?T TimeDuration) (before Now (BeginFn(WhenFn ?T)) " + //
                        "(holdsDuring ?T (" + internalText + ")) ?T)";
    }

    private static String wrapLogicPresent(String internalText) {

        return "(instance ?T TimeDuration) "+ //
                        "(temporallyBetween (BeginFn (WhenFn ?T)) Now (EndFn (WhenFn ?T))) " + //
                        "(holdsDuring ?T (" + internalText + "))";
    }

    private static String wrapLogicPast(String internalText) {

        return "(instance ?T TimeDuration) (before (EndFn (WhenFn ?T)) Now) " + //
                        "(holdsDuring ?T (" + internalText + "))";
    }

    private static String selectFrameForTense(Templates.RandomFrameMap map, Templates.Tense selectedTense) {

        if (map == null || map.isEmpty()) {
            return null;
        }
        String frame = map.get(selectedTense);
        if (frame != null) {
            return frame;
        }
        return map.get(Templates.Tense.NONE);
    }

    /***************************************************************
     * Resolves question mode and selects the matching frame for tense.
     ***************************************************************/
    private static QuestionHandlingResult handleQuestion(Templates.Template template, Templates.Tense selectedTense) {

        boolean asQuestion = template.isQuestionOn() && Math.random() < template.getQuestionFreq();
        Templates.RandomFrameMap frameMap = asQuestion ? template.getEnglishFrameQuestion() : template.getEnglishFrame();
        String englishFrame = selectFrameForTense(frameMap, selectedTense);
        if (englishFrame == null) {
            System.err.println("Error: template '" + template.getName()
                    + "' is missing a "
                    + (asQuestion ? "frame_question" : "frame")
                    + " for tense '" + selectedTense.name().toLowerCase() + "' and fallback 'tense_none'.");
            System.exit(1);
        }
        return new QuestionHandlingResult(asQuestion, englishFrame);
    }

    /***************************************************************
     * Selects tense and frame for one generated sentence, and
     * validates the frame contains no %n{} markers (English
     * negation must be handled in verb slots).
     ***************************************************************/
    private static FrameSelection selectFrame(Templates.Template template, WeightedSampler tenseSampler) {

        Templates.Tense selectedTense = Templates.Tense.NONE;
        if (template.isTenseOn() && tenseSampler != null) {
            selectedTense = Templates.Tense.valueOf(tenseSampler.sampleTerm());
        }
        QuestionHandlingResult questionHandlingResult = handleQuestion(template, selectedTense);
        return new FrameSelection(selectedTense, questionHandlingResult.asQuestion, questionHandlingResult.englishFrame);
    }

    /***************************************************************
     * Samples slot terms and builds logic/English slot value maps.
     * A slot is %1, %2, etc. and a verb slot is %v1, %v2, etc.
     ***************************************************************/
    private static SlotValues getSlotValues(Templates.Template template,
                                            int slotCount,
                                            Templates.Tense selectedTense,
                                            boolean asQuestion) {

        Map<Integer, String> logicSlotValues = new HashMap<>();
        Map<Integer, String> englishSlotValues = new HashMap<>();
        Map<Integer, String> englishVerbValues = new HashMap<>();
        Map<Integer, InstanceValue> instanceValues = new HashMap<>();
        boolean logicNegate = false;
        for (int slotNum = 1; slotNum <= slotCount; slotNum++) {
            WeightedSampler slotSampler = slotSamplers.get(slotNum);
            if (slotSampler == null) {
                System.err.println("Error: template '" + template.getName()
                        + "' slot %" + slotNum + " has no terms to sample.");
                System.exit(1);
            }
            String term = slotSampler.sampleTerm(template.getName(), slotNum);
            logicSlotValues.put(slotNum, term);
            String termFormat = kbLite.getTermFormat(term);
            if (termFormat == null || termFormat.isEmpty()) {
                termFormat = term;
            }
            englishSlotValues.put(slotNum, termFormat);
            Templates.Slot slot = template.getSlots()[slotNum - 1];
            instanceValues.put(slotNum, resolveInstanceValue(slot, slotNum, termFormat));
        }
        int verbSlotCount = template.getVerbSlots().length;
        for (int verbSlotNum = 1; verbSlotNum <= verbSlotCount; verbSlotNum++) {
            Templates.VerbSlot verbSlot = template.getVerbSlots()[verbSlotNum - 1];
            WeightedSampler verbSampler = verbSamplers.get(verbSlotNum);
            if (verbSlot == null || verbSampler == null) {
                System.err.println("Error: template '" + template.getName()
                        + "' slot %v" + verbSlotNum + " has no verbs to sample.");
                System.exit(1);
            }
            String lemma = verbSampler.sampleTerm(template.getName(), verbSlotNum);
            boolean negateVerb = verbSlot.isNegationOn() && Math.random() < verbSlot.getNegFreq();
            logicNegate = logicNegate || negateVerb;
            GenVerbHelper.VerbFeatures features = new GenVerbHelper.VerbFeatures(
                    lemma,
                    selectedTense,
                    GenVerbHelper.DEFAULT_GRAMMATICAL_PERSON,
                    negateVerb,
                    asQuestion);
            englishVerbValues.put(verbSlotNum, verbHelper.realizeVerbPhrase(features));
        }
        return new SlotValues(logicSlotValues, englishSlotValues, englishVerbValues, instanceValues, logicNegate);
    }

    /***************************************************************
     * Converts a string to UpperCamelCase by splitting on any
     * non-alphanumeric character sequence, capitalizing the first
     * letter of each resulting word, and joining without separators.
     * E.g. "coffee table" → "CoffeeTable", "St. Bernard's" → "StBernards"
     ***************************************************************/
    private static String toUpperCamelCase(String text) {

        String[] words = text.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }

    /***************************************************************
     * Decides whether slot N is definite or indefinite.
     * If definite, derives a KIF-safe constant from the slot's
     * termFormat in UpperCamelCase with "Instance" appended
     * (e.g. "human adult" → "HumanAdultInstance").
     ***************************************************************/
    private static InstanceValue resolveInstanceValue(Templates.Slot slot, int slotNum, String termFormat) {

        String variableName = (slot.getVariable() != null) ? slot.getVariable() : "?V" + slotNum;
        if (slot.getDefiniteFreq() > 0.0 && Math.random() < slot.getDefiniteFreq()) {
            String instanceName = toUpperCamelCase(termFormat) + "Instance";
            return new InstanceValue(true, instanceName);
        }
        return new InstanceValue(false, variableName);
    }

    /***************************************************************
     * Replaces all %?N tokens in a string with the resolved
     * instance/variable name for that slot.
     ***************************************************************/
    private static String replaceInstanceSlots(String input, Map<Integer, InstanceValue> instanceValues) {

        Pattern pattern = Pattern.compile("%\\?(\\d+)");
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slotNum = Integer.parseInt(matcher.group(1));
            InstanceValue iv = instanceValues.get(slotNum);
            String value = iv != null ? iv.name : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static class LogicParts {
        private final String preExists;
        private final String body;

        private LogicParts(String preExists, String body) {
            this.preExists = preExists;
            this.body = body;
        }
    }

    /***************************************************************
     * Builds SUO-KIF logic from the clause list and returns two
     * parts: definite instance assertions (preExists) that must
     * stay outside any modal wrapper, and the quantified body.
     * When selectedTense is not NONE and a %t{} clause is present,
     * ?T is added to the exists variable list so it is properly
     * bound after tense expansion.
     ***************************************************************/
    private static LogicParts buildLogicFromClauses(Templates.Template template,
                                                    SlotValues slotValues,
                                                    Templates.Tense selectedTense) {
        List<String> preExistsClauses = new ArrayList<>();
        List<String> bodyClauses = new ArrayList<>();
        List<String> existsVars = new ArrayList<>();
        boolean hasTenseClause = false;
        Pattern instancePattern = Pattern.compile("^\\(instance %\\?(\\d+)\\s+.*\\)$");
        for (String clause : template.getLogicTemplate().getClauses()) {
            if (clause.contains("%t{")) hasTenseClause = true;
            Matcher m = instancePattern.matcher(clause.trim());
            if (m.matches()) {
                int slotNum = Integer.parseInt(m.group(1));
                InstanceValue iv = slotValues.instanceValues.get(slotNum);
                String resolved = replaceInstanceSlots(clause, slotValues.instanceValues);
                resolved = replaceSlots(resolved, slotValues.logicSlotValues);
                if (iv != null && iv.definite) {
                    preExistsClauses.add(resolved);
                } else {
                    bodyClauses.add(resolved);
                    if (iv != null) existsVars.add(iv.name);
                }
            } else {
                String resolved = replaceInstanceSlots(clause, slotValues.instanceValues);
                resolved = replaceSlots(resolved, slotValues.logicSlotValues);
                bodyClauses.add(resolved);
            }
        }
        if (hasTenseClause && selectedTense != Templates.Tense.NONE) {
            existsVars.add("?T");
        }
        String bodyStr;
        if (bodyClauses.size() == 1) {
            bodyStr = bodyClauses.get(0);
        } else {
            bodyStr = "(and " + String.join(" ", bodyClauses) + ")";
        }
        if (!existsVars.isEmpty()) {
            bodyStr = "(exists (" + String.join(" ", existsVars) + ") " + bodyStr + ")";
        }
        String preExistsStr = String.join(" ", preExistsClauses);
        return new LogicParts(preExistsStr, bodyStr);
    }

    /***************************************************************
     * Processes a single template and writes default outputs.
     ***************************************************************/
    public static void processTemplate(Templates.Template template) {

        slotSamplers = buildSlotSamplers(template);
        verbSamplers = buildVerbSamplers(template);
        WeightedSampler tenseSampler = buildTenseSampler(template);
        modalValues = template.getModalValues();
        int numToGen = template.getNumToGen();
        System.out.println("Generating " + numToGen + " sentence(s) from template '" + template.getName() + "'");
        int slotCount = template.getSlots().length;
        for (int i = 0; i < numToGen; i++) {
            FrameSelection frame = selectFrame(template, tenseSampler);
            SlotValues slotValues = getSlotValues(template, slotCount, frame.selectedTense, frame.asQuestion);
            LogicParts logicParts = buildLogicFromClauses(template, slotValues, frame.selectedTense);
            String english = replaceSlots(frame.englishFrame, slotValues.englishSlotValues);
            english = GenVerbHelper.replaceVerbSlots(english, slotValues.englishVerbValues);
            EnglishLogicPair pair = new EnglishLogicPair(english, logicParts.body);
            pair = handleModals(pair, frame.asQuestion, template);
            pair = handleNegation(pair, slotValues.logicNegate);
            pair = handleTense(pair, frame.selectedTense);
            english = cleanEnglishSurface(pair.english);
            String logic = logicParts.preExists.isEmpty()
                    ? pair.logic
                    : logicParts.preExists + " " + pair.logic;
            GenUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);
        }
    }

    /***************************************************************
     * Entry point for loading and printing template metadata.
     ***************************************************************/
    public static void main(String[] args) {

        String home = System.getProperty("user.home");
        String templatePath = home + "/workspace/sigmanlp/src/main/java/com/articulate/nlp/gensentences/templates.json";
        String morphoDbPath = home + "/.sigmanlp/MorphoDB/llama3_2";
        String filePrefix = "from_template";
        if (args != null && args.length > 0) {
            if ("-h".equals(args[0]) || "--help".equals(args[0])) {
                System.out.println("Usage: GenFromTemplate <file-prefix> [templates-json-file-path] [morphodb-path]");
                return;
            }
            if (args[0] != null && !args[0].trim().isEmpty()) {
                filePrefix = args[0].trim();
            }
            if (args.length > 1) {
                templatePath = args[1];
            }
            if (args.length > 2) {
                morphoDbPath = args[2];
            }
        }
        try {
            init(filePrefix, morphoDbPath);
            Templates templates = Templates.read(templatePath);
            System.out.println("Loaded templates.json with " + templates.size() + " template(s).");
            Templates.Template template;
            int i = 0;
            while ((template = templates.getNext()) != null) {
                System.out.println("Generating from template " + i + " of " + templates.size() + ": " + template.getName());
                processTemplate(template);
                i++;
            }
        }
        catch (IOException ex) {
            System.err.println("Unable to read templates.json: " + ex.getMessage());
        }
    }

}
