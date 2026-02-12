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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Loads and iterates synthetic sentence generation templates.
 ***************************************************************/
public class Templates {

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
        private final Map<Tense, String> frame;
        private final Map<Tense, String> frameQuestion;
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
                        Map<Tense, String> frame,
                        Map<Tense, String> frameQuestion,
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
            this.frame = frame;
            this.frameQuestion = frameQuestion;
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

        public Map<Tense, String> getFrame() {
            return frame;
        }

        public Map<Tense, String> getFrameQuestion() {
            return frameQuestion;
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
            builder.append(", frame=").append(frame);
            builder.append(", frameQuestion=").append(frameQuestion);
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
                builder.append(", countablePossible=").append(slot.isCountablePossible());
                builder.append(", countableFreq=").append(slot.getCountableFreq());
                builder.append('}');
            }
            builder.append(']');
            return builder.toString();
        }
    }

    public static class Slot {
        private final List<String> sumoTerms;
        private final boolean countablePossible;
        private final double countableFreq;

        public Slot(List<String> sumoTerms, boolean countablePossible, double countableFreq) {
            this.sumoTerms = sumoTerms;
            this.countablePossible = countablePossible;
            this.countableFreq = countableFreq;
        }

        public List<String> getSumoTerms() {
            return sumoTerms;
        }

        public boolean isCountablePossible() {
            return countablePossible;
        }

        public double getCountableFreq() {
            return countableFreq;
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
            Map<Tense, String> frame = loadFrame(english.path("frame"));
            Map<Tense, String> frameQuestion = loadFrame(english.path("frame_question"));
            Slot[] slots = loadSlots(english);
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
    private static Map<Tense, String> loadFrame(JsonNode frameNode) {
        Map<Tense, String> frame = new EnumMap<>(Tense.class);
        if (frameNode == null || frameNode.isMissingNode() || frameNode.isNull()) {
            return frame;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = frameNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            Tense key = Tense.fromJsonKey(field.getKey());
            frame.put(key, field.getValue().asText());
        }
        return frame;
    }

    /***************************************************************
     * Loads %1, %2, %3... entries into a flexible array.
     ***************************************************************/
    private static Slot[] loadSlots(JsonNode english) {
        if (english == null || english.isMissingNode() || english.isNull()) {
            System.err.println("templates.json is missing the 'english' section for a template.");
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
            slotMap.put(index, parseSlot(field.getKey(), field.getValue()));
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
    private static Slot parseSlot(String slotKey, JsonNode slotNode) {
        if (slotNode == null || slotNode.isMissingNode() || slotNode.isNull()) {
            System.err.println("Slot " + slotKey + " is missing a definition.");
            System.exit(1);
        }
        JsonNode sumoTermsNode = slotNode.get("sumo_terms");
        if (sumoTermsNode == null || !sumoTermsNode.isArray()) {
            System.err.println("Slot " + slotKey + " is missing required sumo_terms array.");
            System.exit(1);
        }
        List<String> sumoTerms = new ArrayList<>();
        for (JsonNode termNode : sumoTermsNode) {
            if (!termNode.isTextual()) {
                System.err.println("Slot " + slotKey + " has non-string sumo_terms value.");
                System.exit(1);
            }
            sumoTerms.add(termNode.asText());
        }
        JsonNode countableNode = slotNode.get("countable");
        if (countableNode == null || !countableNode.isObject()) {
            System.err.println("Slot " + slotKey + " is missing required countable object.");
            System.exit(1);
        }
        JsonNode possibleNode = countableNode.get("possible");
        JsonNode freqNode = countableNode.get("freq");
        if (possibleNode == null || !possibleNode.isBoolean()
                || freqNode == null || !freqNode.isNumber()) {
            System.err.println("Slot " + slotKey + " countable requires possible (boolean) and freq (number).");
            System.exit(1);
        }
        return new Slot(sumoTerms, possibleNode.asBoolean(), freqNode.asDouble());
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
