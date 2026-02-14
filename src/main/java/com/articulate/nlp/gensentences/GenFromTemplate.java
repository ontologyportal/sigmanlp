package com.articulate.nlp.gensentences;

import com.articulate.nlp.GenUtils;
import com.articulate.nlp.KBLite;
import com.articulate.nlp.LFeatureSets;
import com.articulate.nlp.morphodb.MorphoDB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
    public static final String DEFAULT_VERB_TENSE = "Simple present";
    public static final String DEFAULT_GRAMMATICAL_PERSON = "he_she_it";
    public static final Set<String> suppress = new HashSet<>(Arrays.asList());
    public static Map<Integer, WeightedSampler> slotSamplers = new HashMap<>();
    public static Map<Integer, WeightedSampler> verbSamplers = new HashMap<>();

    private static class SlotValues {
        private final Map<Integer, String> logicSlotValues;
        private final Map<Integer, String> englishSlotValues;
        private final Map<Integer, String> englishVerbValues;

        private SlotValues(Map<Integer, String> logicSlotValues,
                           Map<Integer, String> englishSlotValues,
                           Map<Integer, String> englishVerbValues) {
            this.logicSlotValues = logicSlotValues;
            this.englishSlotValues = englishSlotValues;
            this.englishVerbValues = englishVerbValues;
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
                if (sumoTerm.getWeight() <= 0) {
                    continue;
                }
                Set<String> candidates = null;
                if (slot.getType() == Templates.Slot.TermSelectionType.SUBCLASS) {
                    candidates = kbLite.getChildClasses(sumoTerm.getName());
                    if (candidates != null) {
                        candidates.add(sumoTerm.getName());
                    }
                }
                else if (slot.getType() == Templates.Slot.TermSelectionType.INSTANCE) {
                    candidates = kbLite.getInstancesForType(sumoTerm.getName());
                }
                if (candidates == null || candidates.isEmpty()) {
                    System.err.println("Error: template '" + template.getName() + "' slot %" + slotNumber
                            + " class '" + sumoTerm.getName() + "' has no candidates for type="
                            + slot.getType().name().toLowerCase() + " but weight=" + sumoTerm.getWeight() + ".");
                    System.exit(1);
                }
                slotSampler.addWeightedClass(sumoTerm.getName(), sumoTerm.getWeight(), candidates);
            }
            return slotSampler;
        });
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
                if (weightedVerb.getWeight() <= 0) {
                    continue;
                }
                verbSampler.addSingleCandidate(weightedVerb.getLemma(), weightedVerb.getWeight(),
                        weightedVerb.getLemma());
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
     * Apply negation markers of the form %n{...} based on one decision.
     ***************************************************************/
    private static String handleNegation(String frame, boolean negate) {

        Pattern pattern = Pattern.compile("%n\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(frame);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = negate ? matcher.group(1) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString()
                .replaceAll(" {2,}", " ")
                .replaceAll("\\s+([,.;!?])", "$1")
                .trim();
    }

    /***************************************************************
     * Applies tense handling to logic markers of the form %t{...}.
     * NONE leaves internal text unchanged.
     ***************************************************************/
    private static String handleTense(String frame, Templates.Tense selectedTense) {

        Pattern pattern = Pattern.compile("%t\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(frame);
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
        return result.toString();
    }

    /***************************************************************
     * Builds a weighted tense sampler once per template.
     ***************************************************************/
    private static WeightedSampler buildTenseSampler(Templates.Template template) {

        if (template == null || !template.isTenseOn()) {
            return null;
        }
        WeightedSampler sampler = new WeightedSampler();
        sampler.addSingleCandidate(Templates.Tense.NONE.name(), template.getTenseNoneWeight(),
                Templates.Tense.NONE.name());
        sampler.addSingleCandidate(Templates.Tense.PAST.name(), template.getTensePastWeight(),
                Templates.Tense.PAST.name());
        sampler.addSingleCandidate(Templates.Tense.PRESENT.name(), template.getTensePresentWeight(),
                Templates.Tense.PRESENT.name());
        sampler.addSingleCandidate(Templates.Tense.FUTURE.name(), template.getTenseFutureWeight(),
                Templates.Tense.FUTURE.name());
        if (sampler.totalWeight <= 0) {
            return null;
        }
        return sampler;
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

    /***************************************************************
     * Maps selected template tense to MorphoDB tense labels.
     ***************************************************************/
    private static String getMorphoVerbTense(Templates.Tense selectedTense) {

        if (selectedTense == null) {
            return DEFAULT_VERB_TENSE;
        }
        switch (selectedTense) {
            case PAST:
                return "Simple past";
            case FUTURE:
                return "Simple future";
            case PRESENT:
                return "Simple present";
            case NONE:
            default:
                return DEFAULT_VERB_TENSE;
        }
    }

    /***************************************************************
     * Replace %v1, %v2, ... verb slots in a template.
     ***************************************************************/
    private static String handleVerbs(String frame, Map<Integer, String> verbValues) {

        Pattern pattern = Pattern.compile("%v(\\d+)");
        Matcher matcher = pattern.matcher(frame);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slotNum = Integer.parseInt(matcher.group(1));
            String value = verbValues.get(slotNum);
            if (value == null) {
                value = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /***************************************************************
     * Resolves whether to generate a question and selects the matching frame.
     ***************************************************************/
    private static String handleQuestion(Templates.Template template) {

        boolean asQuestion = Math.random() < template.getQuestionFreq();
        String englishFrame;
        if (asQuestion) {
            englishFrame = template.getEnglishFrameQuestion().get(Templates.Tense.NONE);
        }
        else {
            englishFrame = template.getEnglishFrame().get(Templates.Tense.NONE);
        }
        if (englishFrame == null) {
            System.err.println("Error: template '" + template.getName()
                    + "' is missing "
                    + (asQuestion ? "english.frame_question.tense_none" : "english.frame.tense_none") + ".");
            System.exit(1);
        }
        return englishFrame;
    }

    /***************************************************************
     * Samples slot terms and builds logic/English slot value maps.
     * A slot is %1, %2, etc. and a verb slot is %v1, %v2, etc.
     ***************************************************************/
    private static SlotValues getSlotValues(Templates.Template template,
                                            int slotCount,
                                            Templates.Tense selectedTense) {

        Map<Integer, String> logicSlotValues = new HashMap<>();
        Map<Integer, String> englishSlotValues = new HashMap<>();
        Map<Integer, String> englishVerbValues = new HashMap<>();
        String morphoVerbTense = getMorphoVerbTense(selectedTense);
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
        }
        int verbSlotCount = template.getVerbSlots().length;
        for (int verbSlotNum = 1; verbSlotNum <= verbSlotCount; verbSlotNum++) {
            WeightedSampler verbSampler = verbSamplers.get(verbSlotNum);
            if (verbSampler == null) {
                System.err.println("Error: template '" + template.getName()
                        + "' slot %v" + verbSlotNum + " has no verbs to sample.");
                System.exit(1);
            }
            String lemma = verbSampler.sampleTerm(template.getName(), verbSlotNum);
            String conjugated = morphoDB.getVerbConjugation(lemma, morphoVerbTense, DEFAULT_GRAMMATICAL_PERSON);
            if (conjugated == null || conjugated.trim().isEmpty()) {
                System.out.println("Warning: unable to find conjugation for verb '" + lemma + "' with tense='"
                        + morphoVerbTense + "' and grammatical person='" + DEFAULT_GRAMMATICAL_PERSON
                        + "'. Using lemma as fallback.");
                conjugated = lemma;
            }
            englishVerbValues.put(verbSlotNum, conjugated);
        }
        return new SlotValues(logicSlotValues, englishSlotValues, englishVerbValues);
    }

    /***************************************************************
     * Processes a single template and writes default outputs.
     ***************************************************************/
    public static void processTemplate(Templates.Template template) {

        slotSamplers = buildSlotSamplers(template);
        verbSamplers = buildVerbSamplers(template);
        WeightedSampler tenseSampler = buildTenseSampler(template);
        int numToGen = template.getNumToGen();
        System.out.println("Generating " + numToGen + " sentence(s) from template '" + template.getName() + "'");
        String logicFrame = template.getLogic();
        int slotCount = template.getSlots().length;
        for (int i = 0; i < numToGen; i++) {
            Templates.Tense selectedTense = Templates.Tense.NONE;
            if (template.isTenseOn() && tenseSampler != null) {
                selectedTense = Templates.Tense.valueOf(tenseSampler.sampleTerm());
            }
            String englishFrame = handleQuestion(template);
            SlotValues slotValues = getSlotValues(template, slotCount, selectedTense);
            boolean negate = Math.random() < template.getNegFreq();
            String logic = replaceSlots(logicFrame, slotValues.logicSlotValues);
            logic = handleNegation(logic, negate);
            logic = handleTense(logic, selectedTense);
            String english = replaceSlots(englishFrame, slotValues.englishSlotValues);
            english = handleVerbs(english, slotValues.englishVerbValues);
            english = handleNegation(english, negate);
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
