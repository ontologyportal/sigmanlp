package com.articulate.nlp.gensentences;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Loads and iterates synthetic sentence generation templates.
 ***************************************************************/
public class Templates {

    public static class RandomFrameMap {
        private final Map<Tense, List<String>> frames;
        private static final Random RAND = new Random();

        public RandomFrameMap(Map<Tense, List<String>> frames) {
            this.frames = frames;
        }

        public String get(Tense tense) {
            if (tense == null || frames == null) {
                return null;
            }
            List<String> values = frames.get(tense);
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.get(RAND.nextInt(values.size()));
        }

        public boolean isEmpty() {
            return frames == null || frames.isEmpty();
        }

        public String getFirstAvailable() {
            if (frames == null || frames.isEmpty()) {
                return "";
            }
            for (Tense tense : Tense.values()) {
                String value = get(tense);
                if (value != null) {
                    return value;
                }
            }
            return "";
        }

        @Override
        public String toString() {
            return String.valueOf(frames);
        }
    }

    public enum Tense {
        NONE("tense_none"),
        FUTURE("tense_future"),
        PAST("tense_past"),
        PRESENT("tense_present");

        private final String jsonKey;

        Tense(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public static Tense fromJsonKey(String jsonKey) {
            if ("tense_default".equals(jsonKey)) {
                return NONE;
            }
            for (Tense key : Tense.values()) {
                if (key.jsonKey.equals(jsonKey)) {
                    return key;
                }
            }
            throw new IllegalArgumentException("Unknown frame key: " + jsonKey);
        }
    }

    public static class Template {
        private final String name;
        private final RandomFrameMap englishFrame;
        private final RandomFrameMap englishFrameQuestion;
        private final Slot[] slots;
        private final VerbSlot[] verbSlots;
        private final String logic;
        private final double modalFreq;
        private final double modalNegFreq;
        private final double questionFreq;
        private final double negFreq;
        private final int numToGen;
        private final boolean modalOn;
        private final boolean questionOn;
        private final boolean tenseOn;
        private final int tenseNoneWeight;
        private final int tensePastWeight;
        private final int tensePresentWeight;
        private final int tenseFutureWeight;

        public Template(String name,
                        RandomFrameMap englishFrame,
                        RandomFrameMap englishFrameQuestion,
                        Slot[] slots,
                        VerbSlot[] verbSlots,
                        String logic,
                        double modalFreq,
                        double modalNegFreq,
                        double questionFreq,
                        double negFreq,
                        int numToGen,
                        boolean modalOn,
                        boolean questionOn,
                        boolean tenseOn,
                        int tenseNoneWeight,
                        int tensePastWeight,
                        int tensePresentWeight,
                        int tenseFutureWeight) {
            this.name = name;
            this.englishFrame = englishFrame;
            this.englishFrameQuestion = englishFrameQuestion;
            this.slots = slots;
            this.verbSlots = verbSlots;
            this.logic = logic;
            this.modalFreq = modalFreq;
            this.modalNegFreq = modalNegFreq;
            this.questionFreq = questionFreq;
            this.negFreq = negFreq;
            this.numToGen = numToGen;
            this.modalOn = modalOn;
            this.questionOn = questionOn;
            this.tenseOn = tenseOn;
            this.tenseNoneWeight = tenseNoneWeight;
            this.tensePastWeight = tensePastWeight;
            this.tensePresentWeight = tensePresentWeight;
            this.tenseFutureWeight = tenseFutureWeight;
        }

        public String getName() {
            return name;
        }

        public RandomFrameMap getEnglishFrame() {
            return englishFrame;
        }

        public RandomFrameMap getEnglishFrameQuestion() {
            return englishFrameQuestion;
        }

        public Slot[] getSlots() {
            return slots;
        }

        public VerbSlot[] getVerbSlots() {
            return verbSlots;
        }

        public String getLogic() {
            return logic;
        }

        /***************************************************************
         * Returns the modal frequency for this template.
         ***************************************************************/
        public double getModalFreq() {
            return modalFreq;
        }

        /***************************************************************
         * Returns the modal negative frequency for this template.
         ***************************************************************/
        public double getModalNegFreq() {
            return modalNegFreq;
        }

        /***************************************************************
         * Returns the question frequency for this template.
         ***************************************************************/
        public double getQuestionFreq() {
            return questionFreq;
        }

        /***************************************************************
         * Returns the general negation frequency for this template.
         ***************************************************************/
        public double getNegFreq() {
            return negFreq;
        }

        /***************************************************************
         * Returns the number of sentences to generate for this template.
         ***************************************************************/
        public int getNumToGen() {
            return numToGen;
        }

        /***************************************************************
         * Returns whether modals are enabled for this template.
         ***************************************************************/
        public boolean isModalOn() {
            return modalOn;
        }

        /***************************************************************
         * Returns whether questions are enabled for this template.
         ***************************************************************/
        public boolean isQuestionOn() {
            return questionOn;
        }

        /***************************************************************
         * Returns whether tense variants are enabled for this template.
         ***************************************************************/
        public boolean isTenseOn() {
            return tenseOn;
        }

        public int getTenseNoneWeight() {
            return tenseNoneWeight;
        }

        public int getTensePastWeight() {
            return tensePastWeight;
        }

        public int getTensePresentWeight() {
            return tensePresentWeight;
        }

        public int getTenseFutureWeight() {
            return tenseFutureWeight;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Template{name='").append(name).append('\'');
            builder.append(", modalFreq=").append(modalFreq);
            builder.append(", modalNegFreq=").append(modalNegFreq);
            builder.append(", questionFreq=").append(questionFreq);
            builder.append(", negFreq=").append(negFreq);
            builder.append(", numToGen=").append(numToGen);
            builder.append(", modalOn=").append(modalOn);
            builder.append(", questionOn=").append(questionOn);
            builder.append(", tenseOn=").append(tenseOn);
            builder.append(", tenseNoneWeight=").append(tenseNoneWeight);
            builder.append(", tensePastWeight=").append(tensePastWeight);
            builder.append(", tensePresentWeight=").append(tensePresentWeight);
            builder.append(", tenseFutureWeight=").append(tenseFutureWeight);
            builder.append(", englishFrame=").append(englishFrame);
            builder.append(", englishFrameQuestion=").append(englishFrameQuestion);
            builder.append(", slots=").append(formatSlots());
            builder.append(", verbSlots=").append(formatVerbSlots());
            builder.append(", logic='").append(logic).append('\'');
            builder.append('}');
            return builder.toString();
        }

        private String formatSlots() {
            if (slots == null) {
                return "null";
            }
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (int i = 0; i < slots.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                Slot slot = slots[i];
                if (slot == null) {
                    builder.append("null");
                    continue;
                }
                builder.append("{sumoTerms=").append(slot.getSumoTerms());
                builder.append(", type=").append(slot.getType());
                builder.append(", countablePossible=").append(slot.isCountablePossible());
                builder.append(", countableFreq=").append(slot.getCountableFreq());
                builder.append('}');
            }
            builder.append(']');
            return builder.toString();
        }

        private String formatVerbSlots() {
            if (verbSlots == null) {
                return "null";
            }
            StringBuilder builder = new StringBuilder();
            builder.append('[');
            for (int i = 0; i < verbSlots.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                VerbSlot slot = verbSlots[i];
                if (slot == null) {
                    builder.append("null");
                    continue;
                }
                builder.append("{verbs=").append(slot.getVerbs()).append('}');
            }
            builder.append(']');
            return builder.toString();
        }
    }

    public static class Slot {
        public enum TermSelectionType {
            SUBCLASS,
            INSTANCE
        }

        private final List<WeightedSumoTerm> sumoTerms;
        private final TermSelectionType type;
        private final boolean countablePossible;
        private final double countableFreq;

        public Slot(List<WeightedSumoTerm> sumoTerms, TermSelectionType type, boolean countablePossible, double countableFreq) {
            this.sumoTerms = sumoTerms;
            this.type = type;
            this.countablePossible = countablePossible;
            this.countableFreq = countableFreq;
        }

        public List<WeightedSumoTerm> getSumoTerms() {
            return sumoTerms;
        }

        public TermSelectionType getType() {
            return type;
        }

        public boolean isCountablePossible() {
            return countablePossible;
        }

        public double getCountableFreq() {
            return countableFreq;
        }
    }

    public static class WeightedSumoTerm {
        private final String name;
        private final int weight;

        public WeightedSumoTerm(String name, int weight) {
            this.name = name;
            this.weight = weight;
        }

        public String getName() {
            return name;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return "{name='" + name + "', weight=" + weight + "}";
        }
    }

    public static class VerbSlot {
        private final List<WeightedVerb> verbs;

        public VerbSlot(List<WeightedVerb> verbs) {
            this.verbs = verbs;
        }

        public List<WeightedVerb> getVerbs() {
            return verbs;
        }
    }

    public static class WeightedVerb {
        private final String lemma;
        private final int weight;

        public WeightedVerb(String lemma, int weight) {
            this.lemma = lemma;
            this.weight = weight;
        }

        public String getLemma() {
            return lemma;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return "{lemma='" + lemma + "', weight=" + weight + "}";
        }
    }

    private final double defaultModalFreq;
    private final double defaultModalNegFreq;
    private final double defaultQuestionFreq;
    private final double defaultNegFreq;
    private final int defaultNumToGen;
    private final boolean defaultModalOn;
    private final boolean defaultQuestionOn;
    private final boolean defaultTenseOn;
    private final int defaultTenseNoneWeight;
    private final int defaultTensePastWeight;
    private final int defaultTensePresentWeight;
    private final int defaultTenseFutureWeight;
    private final List<Template> templates;
    private int nextIndex;

    private Templates(double defaultModalFreq,
                      double defaultModalNegFreq,
                      double defaultQuestionFreq,
                      double defaultNegFreq,
                      int defaultNumToGen,
                      boolean defaultModalOn,
                      boolean defaultQuestionOn,
                      boolean defaultTenseOn,
                      int defaultTenseNoneWeight,
                      int defaultTensePastWeight,
                      int defaultTensePresentWeight,
                      int defaultTenseFutureWeight,
                      List<Template> templates) {
        this.defaultModalFreq = defaultModalFreq;
        this.defaultModalNegFreq = defaultModalNegFreq;
        this.defaultQuestionFreq = defaultQuestionFreq;
        this.defaultNegFreq = defaultNegFreq;
        this.defaultNumToGen = defaultNumToGen;
        this.defaultModalOn = defaultModalOn;
        this.defaultQuestionOn = defaultQuestionOn;
        this.defaultTenseOn = defaultTenseOn;
        this.defaultTenseNoneWeight = defaultTenseNoneWeight;
        this.defaultTensePastWeight = defaultTensePastWeight;
        this.defaultTensePresentWeight = defaultTensePresentWeight;
        this.defaultTenseFutureWeight = defaultTenseFutureWeight;
        this.templates = templates;
        this.nextIndex = 0;
    }

    /***************************************************************
     * Reads a templates.json file and builds a Templates object.
     ***************************************************************/
    public static Templates read(String templateFilePath) throws IOException {
        JsonNode root = readTemplatesFile(templateFilePath);
        JsonNode defaults = root.path("default_settings");
        JsonNode modal = defaults.path("modal");
        JsonNode tense = defaults.path("tense");
        double modalFreq = modal.path("freq").asDouble();
        double modalNegFreq = modal.path("neg_freq").asDouble();
        double questionFreq = defaults.path("question_freq").asDouble();
        double negFreq = defaults.path("neg_freq").asDouble();
        int numToGen = defaults.path("num_to_gen").asInt();
        boolean modalOn = modal.path("on").asBoolean();
        boolean questionOn = defaults.path("question_on").asBoolean(true);
        boolean tenseOn = tense.path("on").asBoolean();
        int tenseNoneWeight = tense.path("none").asInt(0);
        int tensePastWeight = tense.path("past").asInt(0);
        int tensePresentWeight = tense.path("present").asInt(0);
        int tenseFutureWeight = tense.path("future").asInt(0);
        List<Template> templates = loadTemplates(root, modalFreq, modalNegFreq,
                questionFreq, negFreq, numToGen, modalOn, questionOn, tenseOn,
                tenseNoneWeight, tensePastWeight, tensePresentWeight, tenseFutureWeight);
        return new Templates(modalFreq, modalNegFreq, questionFreq, negFreq, numToGen,
                modalOn, questionOn, tenseOn,
                tenseNoneWeight, tensePastWeight, tensePresentWeight, tenseFutureWeight,
                templates);
    }

    /***************************************************************
     * Returns the number of templates loaded.
     ***************************************************************/
    public int size() {
        return templates.size();
    }

    /***************************************************************
     * Returns the next template or null if none remain.
     ***************************************************************/
    public Template getNext() {
        if (nextIndex >= templates.size()) {
            return null;
        }
        return templates.get(nextIndex++);
    }

    /***************************************************************
     * Loads templates.json from a filesystem path.
     ***************************************************************/
    private static JsonNode readTemplatesFile(String templateFilePath) throws IOException {
        if (templateFilePath == null || templateFilePath.trim().isEmpty()) {
            throw new IOException("Template file path argument is empty.");
        }
        Path filePath = Path.of(templateFilePath);
        if (!Files.exists(filePath)) {
            throw new IOException("Template file not found: " + filePath.toAbsolutePath());
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        return mapper.readTree(filePath.toFile());
    }

    /***************************************************************
     * Parses templates into Template instances.
     ***************************************************************/
    private static List<Template> loadTemplates(JsonNode root,
                                                double defaultModalFreq,
                                                double defaultModalNegFreq,
                                                double defaultQuestionFreq,
                                                double defaultNegFreq,
                                                int defaultNumToGen,
                                                boolean defaultModalOn,
                                                boolean defaultQuestionOn,
                                                boolean defaultTenseOn,
                                                int defaultTenseNoneWeight,
                                                int defaultTensePastWeight,
                                                int defaultTensePresentWeight,
                                                int defaultTenseFutureWeight) {
        JsonNode templateNodes = root.path("templates");
        List<Template> templates = new ArrayList<>(templateNodes.size());
        for (JsonNode templateNode : templateNodes) {
            String name = templateNode.path("name").asText();
            JsonNode english = templateNode.path("english");
            RandomFrameMap frame = loadFrame(english.path("frame"));
            RandomFrameMap frameQuestion = loadFrame(english.path("frame_question"));
            Slot[] slots = loadSlots(name, english);
            VerbSlot[] verbSlots = loadVerbSlots(name, english);
            String logic = english.path("logic").asText();
            Double modalFreq = readOptionalDouble(templateNode, "modal", "freq");
            Double modalNegFreq = readOptionalDouble(templateNode, "modal", "neg_freq");
            Double questionFreq = readOptionalDouble(templateNode, "question_freq");
            Double negFreq = readOptionalDouble(templateNode, "neg_freq");
            Integer numToGen = readOptionalInt(templateNode, "num_to_gen");
            Boolean modalOn = readOptionalBoolean(templateNode, "modal", "on");
            Boolean questionOn = readOptionalBoolean(templateNode, "question_on");
            Boolean tenseOn = readOptionalBoolean(templateNode, "tense", "on");
            Integer tenseNoneWeight = readOptionalInt(templateNode, "tense", "none");
            Integer tensePastWeight = readOptionalInt(templateNode, "tense", "past");
            Integer tensePresentWeight = readOptionalInt(templateNode, "tense", "present");
            Integer tenseFutureWeight = readOptionalInt(templateNode, "tense", "future");
            templates.add(new Template(name, frame, frameQuestion, slots, verbSlots, logic,
                    modalFreq != null ? modalFreq : defaultModalFreq,
                    modalNegFreq != null ? modalNegFreq : defaultModalNegFreq,
                    questionFreq != null ? questionFreq : defaultQuestionFreq,
                    negFreq != null ? negFreq : defaultNegFreq,
                    numToGen != null ? numToGen : defaultNumToGen,
                    modalOn != null ? modalOn : defaultModalOn,
                    questionOn != null ? questionOn : defaultQuestionOn,
                    tenseOn != null ? tenseOn : defaultTenseOn,
                    tenseNoneWeight != null ? tenseNoneWeight : defaultTenseNoneWeight,
                    tensePastWeight != null ? tensePastWeight : defaultTensePastWeight,
                    tensePresentWeight != null ? tensePresentWeight : defaultTensePresentWeight,
                    tenseFutureWeight != null ? tenseFutureWeight : defaultTenseFutureWeight));
        }
        return templates;
    }

    /***************************************************************
     * Loads frame or frame_question text into a map keyed by tense.
     ***************************************************************/
    private static RandomFrameMap loadFrame(JsonNode frameNode) {
        Map<Tense, List<String>> frame = new EnumMap<>(Tense.class);
        if (frameNode == null || frameNode.isMissingNode() || frameNode.isNull()) {
            return new RandomFrameMap(frame);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = frameNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            Tense key = Tense.fromJsonKey(field.getKey());
            JsonNode valueNode = field.getValue();
            List<String> values = new ArrayList<>();
            if (valueNode.isTextual()) { // Backward compatible with old single-string format.
                values.add(valueNode.asText());
            }
            else if (valueNode.isArray()) {
                for (JsonNode textNode : valueNode) {
                    if (textNode.isTextual()) {
                        values.add(textNode.asText());
                    }
                }
            }
            if (!values.isEmpty()) {
                frame.put(key, values);
            }
        }
        return new RandomFrameMap(frame);
    }

    /***************************************************************
     * Loads %1, %2, %3... entries into a flexible array.
     ***************************************************************/
    private static Slot[] loadSlots(String templateName, JsonNode english) {
        if (english == null || english.isMissingNode() || english.isNull()) {
            System.err.println("Template '" + templateName + "' is missing the 'english' section.");
            System.exit(1);
        }
        Pattern pattern = Pattern.compile("^%(\\d+)$");
        Map<Integer, Slot> slotMap = new HashMap<>();
        int maxIndex = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = english.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            Matcher matcher = pattern.matcher(field.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            slotMap.put(index, parseSlot(templateName, field.getKey(), field.getValue()));
            if (index > maxIndex) {
                maxIndex = index;
            }
        }
        Slot[] slots = new Slot[maxIndex];
        for (Map.Entry<Integer, Slot> entry : slotMap.entrySet()) {
            int index = entry.getKey();
            if (index >= 1 && index <= maxIndex) {
                slots[index - 1] = entry.getValue();
            }
        }
        return slots;
    }

    /***************************************************************
     * Loads %v1, %v2, %v3... entries into a flexible array.
     ***************************************************************/
    private static VerbSlot[] loadVerbSlots(String templateName, JsonNode english) {
        if (english == null || english.isMissingNode() || english.isNull()) {
            System.err.println("Template '" + templateName + "' is missing the 'english' section.");
            System.exit(1);
        }
        Pattern pattern = Pattern.compile("^%v(\\d+)$");
        Map<Integer, VerbSlot> slotMap = new HashMap<>();
        int maxIndex = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = english.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            Matcher matcher = pattern.matcher(field.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            slotMap.put(index, parseVerbSlot(templateName, field.getKey(), field.getValue()));
            if (index > maxIndex) {
                maxIndex = index;
            }
        }
        VerbSlot[] slots = new VerbSlot[maxIndex];
        for (Map.Entry<Integer, VerbSlot> entry : slotMap.entrySet()) {
            int index = entry.getKey();
            if (index >= 1 && index <= maxIndex) {
                slots[index - 1] = entry.getValue();
            }
        }
        return slots;
    }

    /***************************************************************
     * Parses a single slot definition.
     ***************************************************************/
    private static Slot parseSlot(String templateName, String slotKey, JsonNode slotNode) {
        if (slotNode == null || slotNode.isMissingNode() || slotNode.isNull()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing a definition.");
            System.exit(1);
        }
        JsonNode sumoTermsNode = slotNode.get("sumo_terms");
        if (sumoTermsNode == null || !sumoTermsNode.isArray()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing required sumo_terms array.");
            System.exit(1);
        }
        List<WeightedSumoTerm> sumoTerms = new ArrayList<>();
        for (JsonNode termNode : sumoTermsNode) {
            if (termNode == null || !termNode.isObject()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey
                        + " has invalid sumo_terms item " + termNode
                        + "; expected object with name and weight.");
                System.exit(1);
            }
            JsonNode nameNode = termNode.get("name");
            JsonNode weightNode = termNode.get("weight");
            if (nameNode == null || !nameNode.isTextual()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey
                        + " has sumo_terms item missing name (string).");
                System.exit(1);
            }
            if (weightNode == null || !weightNode.isIntegralNumber()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey + " sumo_terms item '" + nameNode.asText()
                        + "' is missing weight (integer).");
                System.exit(1);
            }
            int weight = weightNode.asInt();
            if (weight < 0) {
                System.err.println("Template '" + templateName + "', slot " + slotKey + " sumo_terms item '" + nameNode.asText()
                        + "' has invalid weight " + weight + "; weight must be >= 0.");
                System.exit(1);
            }
            sumoTerms.add(new WeightedSumoTerm(nameNode.asText(), weight));
        }
        JsonNode typeNode = slotNode.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " is missing required type (\"subclass\" or \"instance\").");
            System.exit(1);
        }
        String typeString = typeNode.asText().trim().toLowerCase();
        Slot.TermSelectionType type;
        if ("subclass".equals(typeString)) {
            type = Slot.TermSelectionType.SUBCLASS;
        }
        else if ("instance".equals(typeString)) {
            type = Slot.TermSelectionType.INSTANCE;
        }
        else {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid type '" + typeNode.asText() + "'. Expected \"subclass\" or \"instance\".");
            System.exit(1);
            return null;
        }
        JsonNode countableNode = slotNode.get("countable");
        if (countableNode == null || !countableNode.isObject()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing required countable object.");
            System.exit(1);
        }
        JsonNode possibleNode = countableNode.get("possible");
        JsonNode freqNode = countableNode.get("freq");
        if (possibleNode == null || !possibleNode.isBoolean()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " countable requires possible (boolean).");
            System.exit(1);
        }
        boolean countablePossible = possibleNode.asBoolean();
        double countableFreq = 0.0;
        if (countablePossible) {
            if (freqNode == null || !freqNode.isNumber()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey
                        + " countable requires freq (number) when possible=true.");
                System.exit(1);
            }
            countableFreq = freqNode.asDouble();
        }
        else if (freqNode != null && freqNode.isNumber()) {
            countableFreq = freqNode.asDouble();
        }
        return new Slot(sumoTerms, type, countablePossible, countableFreq);
    }

    /***************************************************************
     * Parses a single verb slot definition.
     ***************************************************************/
    private static VerbSlot parseVerbSlot(String templateName, String slotKey, JsonNode slotNode) {
        if (slotNode == null || slotNode.isMissingNode() || slotNode.isNull()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing a definition.");
            System.exit(1);
        }
        JsonNode verbsNode = slotNode.get("verbs");
        if (verbsNode == null || !verbsNode.isArray()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing required verbs array.");
            System.exit(1);
        }
        List<WeightedVerb> verbs = new ArrayList<>();
        for (JsonNode verbNode : verbsNode) {
            if (verbNode == null || !verbNode.isObject()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey
                        + " has invalid verbs item " + verbNode
                        + "; expected object with lemma and weight.");
                System.exit(1);
            }
            JsonNode lemmaNode = verbNode.get("lemma");
            JsonNode weightNode = verbNode.get("weight");
            if (lemmaNode == null || !lemmaNode.isTextual()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey
                        + " has verbs item missing lemma (string).");
                System.exit(1);
            }
            if (weightNode == null || !weightNode.isIntegralNumber()) {
                System.err.println("Template '" + templateName + "', slot " + slotKey + " verbs item '" + lemmaNode.asText()
                        + "' is missing weight (integer).");
                System.exit(1);
            }
            int weight = weightNode.asInt();
            if (weight < 0) {
                System.err.println("Template '" + templateName + "', slot " + slotKey + " verbs item '" + lemmaNode.asText()
                        + "' has invalid weight " + weight + "; weight must be >= 0.");
                System.exit(1);
            }
            verbs.add(new WeightedVerb(lemmaNode.asText(), weight));
        }
        return new VerbSlot(verbs);
    }

    /***************************************************************
     * Reads a nested double override if present.
     ***************************************************************/
    private static Double readOptionalDouble(JsonNode node, String parentField, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode parent = node.get(parentField);
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode value = parent.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    /***************************************************************
     * Reads a double override if present at the current object level.
     ***************************************************************/
    private static Double readOptionalDouble(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asDouble();
    }

    /***************************************************************
     * Reads an integer override if present.
     ***************************************************************/
    private static Integer readOptionalInt(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    /***************************************************************
     * Reads a nested integer override if present.
     ***************************************************************/
    private static Integer readOptionalInt(JsonNode node, String parentField, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode parent = node.get(parentField);
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode value = parent.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    /***************************************************************
     * Reads a boolean override if present.
     ***************************************************************/
    private static Boolean readOptionalBoolean(JsonNode node, String parentField, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode parent = node.get(parentField);
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode value = parent.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    /***************************************************************
     * Reads a boolean override if present at the current object level.
     ***************************************************************/
    private static Boolean readOptionalBoolean(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }
}
