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
        DEFAULT("tense_default"),
        FUTURE("tense_future"),
        PAST("tense_past"),
        PRESENT("tense_present");

        private final String jsonKey;

        Tense(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        public static Tense fromJsonKey(String jsonKey) {
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
        private final String logic;
        private final double modalFreq;
        private final double modalNegFreq;
        private final double questionFreq;
        private final double questionNegFreq;
        private final int numToGen;
        private final boolean modalOn;
        private final boolean questionOn;
        private final boolean tenseOn;

        public Template(String name,
                        RandomFrameMap englishFrame,
                        RandomFrameMap englishFrameQuestion,
                        Slot[] slots,
                        String logic,
                        double modalFreq,
                        double modalNegFreq,
                        double questionFreq,
                        double questionNegFreq,
                        int numToGen,
                        boolean modalOn,
                        boolean questionOn,
                        boolean tenseOn) {
            this.name = name;
            this.englishFrame = englishFrame;
            this.englishFrameQuestion = englishFrameQuestion;
            this.slots = slots;
            this.logic = logic;
            this.modalFreq = modalFreq;
            this.modalNegFreq = modalNegFreq;
            this.questionFreq = questionFreq;
            this.questionNegFreq = questionNegFreq;
            this.numToGen = numToGen;
            this.modalOn = modalOn;
            this.questionOn = questionOn;
            this.tenseOn = tenseOn;
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
         * Returns the question negative frequency for this template.
         ***************************************************************/
        public double getQuestionNegFreq() {
            return questionNegFreq;
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

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Template{name='").append(name).append('\'');
            builder.append(", modalFreq=").append(modalFreq);
            builder.append(", modalNegFreq=").append(modalNegFreq);
            builder.append(", questionFreq=").append(questionFreq);
            builder.append(", questionNegFreq=").append(questionNegFreq);
            builder.append(", numToGen=").append(numToGen);
            builder.append(", modalOn=").append(modalOn);
            builder.append(", questionOn=").append(questionOn);
            builder.append(", tenseOn=").append(tenseOn);
            builder.append(", englishFrame=").append(englishFrame);
            builder.append(", englishFrameQuestion=").append(englishFrameQuestion);
            builder.append(", slots=").append(formatSlots());
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

    private final double defaultModalFreq;
    private final double defaultModalNegFreq;
    private final double defaultQuestionFreq;
    private final double defaultQuestionNegFreq;
    private final int defaultNumToGen;
    private final boolean defaultModalOn;
    private final boolean defaultQuestionOn;
    private final boolean defaultTenseOn;
    private final List<Template> templates;
    private int nextIndex;

    private Templates(double defaultModalFreq,
                      double defaultModalNegFreq,
                      double defaultQuestionFreq,
                      double defaultQuestionNegFreq,
                      int defaultNumToGen,
                      boolean defaultModalOn,
                      boolean defaultQuestionOn,
                      boolean defaultTenseOn,
                      List<Template> templates) {
        this.defaultModalFreq = defaultModalFreq;
        this.defaultModalNegFreq = defaultModalNegFreq;
        this.defaultQuestionFreq = defaultQuestionFreq;
        this.defaultQuestionNegFreq = defaultQuestionNegFreq;
        this.defaultNumToGen = defaultNumToGen;
        this.defaultModalOn = defaultModalOn;
        this.defaultQuestionOn = defaultQuestionOn;
        this.defaultTenseOn = defaultTenseOn;
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
        JsonNode question = defaults.path("question");
        JsonNode tense = defaults.path("tense");
        double modalFreq = modal.path("freq").asDouble();
        double modalNegFreq = modal.path("neg_freq").asDouble();
        double questionFreq = question.path("freq").asDouble();
        double questionNegFreq = question.path("neg_freq").asDouble();
        int numToGen = defaults.path("num_to_gen").asInt();
        boolean modalOn = modal.path("on").asBoolean();
        boolean questionOn = question.path("on").asBoolean();
        boolean tenseOn = tense.path("on").asBoolean();
        List<Template> templates = loadTemplates(root, modalFreq, modalNegFreq,
                questionFreq, questionNegFreq, numToGen, modalOn, questionOn, tenseOn);
        return new Templates(modalFreq, modalNegFreq, questionFreq, questionNegFreq, numToGen,
                modalOn, questionOn, tenseOn, templates);
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
                                                double defaultQuestionNegFreq,
                                                int defaultNumToGen,
                                                boolean defaultModalOn,
                                                boolean defaultQuestionOn,
                                                boolean defaultTenseOn) {
        JsonNode templateNodes = root.path("templates");
        List<Template> templates = new ArrayList<>(templateNodes.size());
        for (JsonNode templateNode : templateNodes) {
            String name = templateNode.path("name").asText();
            JsonNode english = templateNode.path("english");
            RandomFrameMap frame = loadFrame(english.path("frame"));
            RandomFrameMap frameQuestion = loadFrame(english.path("frame_question"));
            Slot[] slots = loadSlots(name, english);
            String logic = english.path("logic").asText();
            Double modalFreq = readOptionalDouble(templateNode, "modal", "freq");
            Double modalNegFreq = readOptionalDouble(templateNode, "modal", "neg_freq");
            Double questionFreq = readOptionalDouble(templateNode, "question", "freq");
            Double questionNegFreq = readOptionalDouble(templateNode, "question", "neg_freq");
            Integer numToGen = readOptionalInt(templateNode, "num_to_gen");
            Boolean modalOn = readOptionalBoolean(templateNode, "modal", "on");
            Boolean questionOn = readOptionalBoolean(templateNode, "question", "on");
            Boolean tenseOn = readOptionalBoolean(templateNode, "tense", "on");
            templates.add(new Template(name, frame, frameQuestion, slots, logic,
                    modalFreq != null ? modalFreq : defaultModalFreq,
                    modalNegFreq != null ? modalNegFreq : defaultModalNegFreq,
                    questionFreq != null ? questionFreq : defaultQuestionFreq,
                    questionNegFreq != null ? questionNegFreq : defaultQuestionNegFreq,
                    numToGen != null ? numToGen : defaultNumToGen,
                    modalOn != null ? modalOn : defaultModalOn,
                    questionOn != null ? questionOn : defaultQuestionOn,
                    tenseOn != null ? tenseOn : defaultTenseOn));
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
}
