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
        private final LogicTemplate logicTemplate;
        private final double modalFreq;
        private final double modalNegFreq;
        private final double questionFreq;
        private final int numToGen;
        private final boolean modalOn;
        private final boolean questionOn;
        private final boolean tenseOn;
        private final int tenseNoneWeight;
        private final int tensePastWeight;
        private final int tensePresentWeight;
        private final int tenseFutureWeight;
        private final List<WeightedModal> modalValues;

        public Template(String name,
                        RandomFrameMap englishFrame,
                        RandomFrameMap englishFrameQuestion,
                        Slot[] slots,
                        VerbSlot[] verbSlots,
                        LogicTemplate logicTemplate,
                        double modalFreq,
                        double modalNegFreq,
                        double questionFreq,
                        int numToGen,
                        boolean modalOn,
                        boolean questionOn,
                        boolean tenseOn,
                        int tenseNoneWeight,
                        int tensePastWeight,
                        int tensePresentWeight,
                        int tenseFutureWeight,
                        List<WeightedModal> modalValues) {
            this.name = name;
            this.englishFrame = englishFrame;
            this.englishFrameQuestion = englishFrameQuestion;
            this.slots = slots;
            this.verbSlots = verbSlots;
            this.logicTemplate = logicTemplate;
            this.modalFreq = modalFreq;
            this.modalNegFreq = modalNegFreq;
            this.questionFreq = questionFreq;
            this.numToGen = numToGen;
            this.modalOn = modalOn;
            this.questionOn = questionOn;
            this.tenseOn = tenseOn;
            this.tenseNoneWeight = tenseNoneWeight;
            this.tensePastWeight = tensePastWeight;
            this.tensePresentWeight = tensePresentWeight;
            this.tenseFutureWeight = tenseFutureWeight;
            this.modalValues = modalValues != null ? modalValues : new ArrayList<>();
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

        public LogicTemplate getLogicTemplate() {
            return logicTemplate;
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

        public List<WeightedModal> getModalValues() {
            return modalValues;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Template{name='").append(name).append('\'');
            builder.append(", modalFreq=").append(modalFreq);
            builder.append(", modalNegFreq=").append(modalNegFreq);
            builder.append(", questionFreq=").append(questionFreq);
            builder.append(", numToGen=").append(numToGen);
            builder.append(", modalOn=").append(modalOn);
            builder.append(", questionOn=").append(questionOn);
            builder.append(", tenseOn=").append(tenseOn);
            builder.append(", tenseNoneWeight=").append(tenseNoneWeight);
            builder.append(", tensePastWeight=").append(tensePastWeight);
            builder.append(", tensePresentWeight=").append(tensePresentWeight);
            builder.append(", tenseFutureWeight=").append(tenseFutureWeight);
            builder.append(", modalValues=").append(modalValues);
            builder.append(", englishFrame=").append(englishFrame);
            builder.append(", englishFrameQuestion=").append(englishFrameQuestion);
            builder.append(", slots=").append(formatSlots());
            builder.append(", verbSlots=").append(formatVerbSlots());
            builder.append(", logicTemplate=").append(logicTemplate);
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
                builder.append("{verbs=").append(slot.getVerbs());
                builder.append(", negationOn=").append(slot.isNegationOn());
                builder.append(", negFreq=").append(slot.getNegFreq());
                builder.append('}');
            }
            builder.append(']');
            return builder.toString();
        }
    }

    public static class LogicTemplate {
        private final List<String> clauses;

        public LogicTemplate(List<String> clauses) {
            this.clauses = clauses != null ? clauses : new ArrayList<>();
        }

        public List<String> getClauses() {
            return clauses;
        }

        @Override
        public String toString() {
            return "LogicTemplate{clauses=" + clauses + "}";
        }
    }

    public static class Slot {
        public enum TermSelectionType {
            SUBCLASS,
            INSTANCE,
            POSITIONAL_INSTANCE
        }

        private final List<WeightedSumoTerm> sumoTerms;
        private final TermSelectionType type;
        private final boolean countablePossible;
        private final double countableFreq;
        private final String variable;
        private final double definiteFreq;
        private final double countProbDropoff;
        private final double namedHumanFreq;
        private final List<String> exclude;
        private final double listFreq;
        private final double listProbDropoff;
        private final boolean includePrep;

        public Slot(List<WeightedSumoTerm> sumoTerms, TermSelectionType type,
                    boolean countablePossible, double countableFreq,
                    String variable, double definiteFreq, double countProbDropoff,
                    double namedHumanFreq, List<String> exclude,
                    double listFreq, double listProbDropoff) {
            this(sumoTerms, type, countablePossible, countableFreq, variable, definiteFreq,
                    countProbDropoff, namedHumanFreq, exclude, listFreq, listProbDropoff, true);
        }

        public Slot(List<WeightedSumoTerm> sumoTerms, TermSelectionType type,
                    boolean countablePossible, double countableFreq,
                    String variable, double definiteFreq, double countProbDropoff,
                    double namedHumanFreq, List<String> exclude,
                    double listFreq, double listProbDropoff, boolean includePrep) {
            this.sumoTerms = sumoTerms;
            this.type = type;
            this.countablePossible = countablePossible;
            this.countableFreq = countableFreq;
            this.variable = variable;
            this.definiteFreq = definiteFreq;
            this.countProbDropoff = countProbDropoff;
            this.namedHumanFreq = namedHumanFreq;
            this.exclude = exclude;
            this.listFreq = listFreq;
            this.listProbDropoff = listProbDropoff;
            this.includePrep = includePrep;
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

        /***************************************************************
         * Optional declared variable name (e.g. "?A1"). Null means
         * the generator will auto-assign one.
         ***************************************************************/
        public String getVariable() {
            return variable;
        }

        public double getDefiniteFreq() {
            return definiteFreq;
        }

        public double getCountProbDropoff() {
            return countProbDropoff;
        }

        public double getNamedHumanFreq() {
            return namedHumanFreq;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public double getListFreq() {
            return listFreq;
        }

        public double getListProbDropoff() {
            return listProbDropoff;
        }

        /***************************************************************
         * When false, the positional slot's dependent preposition is
         * suppressed because the template frame already provides it
         * (e.g. "from %3" where %3 should resolve to "upstream", not
         * "upstream from").
         ***************************************************************/
        public boolean isIncludePrep() {
            return includePrep;
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
        private final boolean negationOn;
        private final double negFreq;
        private final String variable;
        private final double definiteFreq;

        public VerbSlot(List<WeightedVerb> verbs, boolean negationOn, double negFreq,
                        String variable, double definiteFreq) {
            this.verbs = verbs;
            this.negationOn = negationOn;
            this.negFreq = negFreq;
            this.variable = variable;
            this.definiteFreq = definiteFreq;
        }

        public List<WeightedVerb> getVerbs() {
            return verbs;
        }

        public boolean isNegationOn() {
            return negationOn;
        }

        public double getNegFreq() {
            return negFreq;
        }

        /** Optional KIF variable name (e.g. "?V1"). Null means auto-generate. */
        public String getVariable() {
            return variable;
        }

        /** Probability [0,1] that the process instance is treated as pre-existing (definite). */
        public double getDefiniteFreq() {
            return definiteFreq;
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

    public static class WeightedModal {
        private final String sumoTerm;
        private final String text;
        private final int weight;

        public WeightedModal(String sumoTerm, String text, int weight) {
            this.sumoTerm = sumoTerm;
            this.text = text;
            this.weight = weight;
        }

        public String getSumoTerm() {
            return sumoTerm;
        }

        public String getText() {
            return text;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return "{sumo_term='" + sumoTerm + "', text='" + text + "', weight=" + weight + "}";
        }
    }

    private final double defaultModalFreq;
    private final double defaultModalNegFreq;
    private final double defaultQuestionFreq;
    private final int defaultNumToGen;
    private final boolean defaultModalOn;
    private final boolean defaultQuestionOn;
    private final boolean defaultTenseOn;
    private final int defaultTenseNoneWeight;
    private final int defaultTensePastWeight;
    private final int defaultTensePresentWeight;
    private final int defaultTenseFutureWeight;
    private final List<WeightedModal> defaultModalValues;
    private final List<Template> templates;
    private final String morphoDbPath;
    private final boolean queryMissingWords;
    private final String morphoDbModel;
    private int nextIndex;

    private Templates(double defaultModalFreq,
                      double defaultModalNegFreq,
                      double defaultQuestionFreq,
                      int defaultNumToGen,
                      boolean defaultModalOn,
                      boolean defaultQuestionOn,
                      boolean defaultTenseOn,
                      int defaultTenseNoneWeight,
                      int defaultTensePastWeight,
                      int defaultTensePresentWeight,
                      int defaultTenseFutureWeight,
                      List<WeightedModal> defaultModalValues,
                      List<Template> templates,
                      String morphoDbPath,
                      boolean queryMissingWords,
                      String morphoDbModel) {
        this.defaultModalFreq = defaultModalFreq;
        this.defaultModalNegFreq = defaultModalNegFreq;
        this.defaultQuestionFreq = defaultQuestionFreq;
        this.defaultNumToGen = defaultNumToGen;
        this.defaultModalOn = defaultModalOn;
        this.defaultQuestionOn = defaultQuestionOn;
        this.defaultTenseOn = defaultTenseOn;
        this.defaultTenseNoneWeight = defaultTenseNoneWeight;
        this.defaultTensePastWeight = defaultTensePastWeight;
        this.defaultTensePresentWeight = defaultTensePresentWeight;
        this.defaultTenseFutureWeight = defaultTenseFutureWeight;
        this.defaultModalValues = defaultModalValues != null ? defaultModalValues : new ArrayList<>();
        this.templates = templates;
        this.morphoDbPath = morphoDbPath;
        this.queryMissingWords = queryMissingWords;
        this.morphoDbModel = morphoDbModel;
        this.nextIndex = 0;
    }

    public String getMorphoDbPath() {
        return morphoDbPath;
    }

    public boolean isQueryMissingWords() {
        return queryMissingWords;
    }

    public String getMorphoDbModel() {
        return morphoDbModel;
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
        int numToGen = defaults.path("num_to_gen").asInt();
        boolean modalOn = modal.path("on").asBoolean();
        boolean questionOn = defaults.path("question_on").asBoolean(true);
        boolean tenseOn = tense.path("on").asBoolean();
        int tenseNoneWeight = tense.path("none").asInt(0);
        int tensePastWeight = tense.path("past").asInt(0);
        int tensePresentWeight = tense.path("present").asInt(0);
        int tenseFutureWeight = tense.path("future").asInt(0);
        List<WeightedModal> defaultModalValues = parseModalValues(modal);
        double defaultCountProbDropoff = defaults.path("count_prob_dropoff").asDouble(0.5);
        if (defaultCountProbDropoff <= 0.0 || defaultCountProbDropoff > 1.0) {
            System.err.println("default_settings count_prob_dropoff " + defaultCountProbDropoff + " must be in (0,1].");
            System.exit(1);
        }
        double defaultNamedHumanFreq = defaults.path("named_human_freq").asDouble(0.0);
        JsonNode morphoDbNode = root.path("resources").path("morpho_db");
        String morphoDbPath       = readOptionalString(morphoDbNode, "path");
        boolean queryMissingWords = Boolean.TRUE.equals(readOptionalBoolean(morphoDbNode, "query_missing_words"));
        String morphoDbModel      = readOptionalString(morphoDbNode, "model");
        List<Template> templates = loadTemplates(root, modalFreq, modalNegFreq,
                questionFreq, numToGen, modalOn, questionOn, tenseOn,
                tenseNoneWeight, tensePastWeight, tensePresentWeight, tenseFutureWeight,
                defaultModalValues, defaultCountProbDropoff, defaultNamedHumanFreq);
        return new Templates(modalFreq, modalNegFreq, questionFreq, numToGen,
                modalOn, questionOn, tenseOn,
                tenseNoneWeight, tensePastWeight, tensePresentWeight, tenseFutureWeight,
                defaultModalValues, templates, morphoDbPath, queryMissingWords, morphoDbModel);
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
                                                int defaultNumToGen,
                                                boolean defaultModalOn,
                                                boolean defaultQuestionOn,
                                                boolean defaultTenseOn,
                                                int defaultTenseNoneWeight,
                                                int defaultTensePastWeight,
                                                int defaultTensePresentWeight,
                                                int defaultTenseFutureWeight,
                                                List<WeightedModal> defaultModalValues,
                                                double defaultCountProbDropoff,
                                                double defaultNamedHumanFreq) {
        JsonNode templateNodes = root.path("templates");
        if (!templateNodes.isArray()) {
            System.err.println("templates.json is missing or has an invalid 'templates' array.");
            System.exit(1);
        }
        List<Template> templates = new ArrayList<>(templateNodes.size());
        for (JsonNode templateNode : templateNodes) {
            String name = templateNode.path("name").asText();
            JsonNode english = templateNode.path("english");
            RandomFrameMap frame = loadFrame(english.path("frame"));
            RandomFrameMap frameQuestion = loadFrame(english.path("frame_question"));
            Slot[] slots = loadSlots(name, english, defaultCountProbDropoff, defaultNamedHumanFreq);
            VerbSlot[] verbSlots = loadVerbSlots(name, english);
            LogicTemplate logicTemplate = parseLogicTemplate(english.path("logic"));
            Double modalFreq = readOptionalDouble(templateNode, "modal", "freq");
            Double modalNegFreq = readOptionalDouble(templateNode, "modal", "neg_freq");
            Double questionFreq = readOptionalDouble(templateNode, "question_freq");
            Integer numToGen = readOptionalInt(templateNode, "num_to_gen");
            Boolean modalOn = readOptionalBoolean(templateNode, "modal", "on");
            Boolean questionOn = readOptionalBoolean(templateNode, "question_on");
            Boolean tenseOn = readOptionalBoolean(templateNode, "tense", "on");
            Integer tenseNoneWeight = readOptionalInt(templateNode, "tense", "none");
            Integer tensePastWeight = readOptionalInt(templateNode, "tense", "past");
            Integer tensePresentWeight = readOptionalInt(templateNode, "tense", "present");
            Integer tenseFutureWeight = readOptionalInt(templateNode, "tense", "future");
            List<WeightedModal> templateModalValues = parseModalValues(templateNode.path("modal"));
            List<WeightedModal> resolvedModalValues = !templateModalValues.isEmpty() ? templateModalValues : defaultModalValues;
            templates.add(new Template(name, frame, frameQuestion, slots, verbSlots, logicTemplate,
                    modalFreq != null ? modalFreq : defaultModalFreq,
                    modalNegFreq != null ? modalNegFreq : defaultModalNegFreq,
                    questionFreq != null ? questionFreq : defaultQuestionFreq,
                    numToGen != null ? numToGen : defaultNumToGen,
                    modalOn != null ? modalOn : defaultModalOn,
                    questionOn != null ? questionOn : defaultQuestionOn,
                    tenseOn != null ? tenseOn : defaultTenseOn,
                    tenseNoneWeight != null ? tenseNoneWeight : defaultTenseNoneWeight,
                    tensePastWeight != null ? tensePastWeight : defaultTensePastWeight,
                    tensePresentWeight != null ? tensePresentWeight : defaultTensePresentWeight,
                    tenseFutureWeight != null ? tenseFutureWeight : defaultTenseFutureWeight,
                    resolvedModalValues));
        }
        return templates;
    }

    /***************************************************************
     * Parses modal values from a modal JSON node.
     ***************************************************************/
    private static List<WeightedModal> parseModalValues(JsonNode modalNode) {
        List<WeightedModal> values = new ArrayList<>();
        if (modalNode == null || modalNode.isMissingNode() || modalNode.isNull()) {
            return values;
        }
        JsonNode valuesNode = modalNode.path("values");
        if (!valuesNode.isArray()) {
            return values;
        }
        for (JsonNode valueNode : valuesNode) {
            String sumoTerm = valueNode.path("sumo_term").asText();
            String text = valueNode.path("text").asText();
            int weight = valueNode.path("weight").asInt(1);
            if (!sumoTerm.isEmpty() && weight > 0) {
                values.add(new WeightedModal(sumoTerm, text, weight));
            }
        }
        return values;
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
    private static Slot[] loadSlots(String templateName, JsonNode english,
                                    double defaultCountProbDropoff, double defaultNamedHumanFreq) {
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
            slotMap.put(index, parseSlot(templateName, field.getKey(), field.getValue(),
                    defaultCountProbDropoff, defaultNamedHumanFreq));
            if (index > maxIndex) {
                maxIndex = index;
            }
        }
        for (int i = 1; i <= maxIndex; i++) {
            if (!slotMap.containsKey(i)) {
                System.err.println("Template '" + templateName + "' has non-contiguous slot indices: missing %" + i + ".");
                System.exit(1);
            }
        }
        Slot[] slots = new Slot[maxIndex];
        for (Map.Entry<Integer, Slot> entry : slotMap.entrySet()) {
            slots[entry.getKey() - 1] = entry.getValue();
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
        for (int i = 1; i <= maxIndex; i++) {
            if (!slotMap.containsKey(i)) {
                System.err.println("Template '" + templateName + "' has non-contiguous verb slot indices: missing %v" + i + ".");
                System.exit(1);
            }
        }
        VerbSlot[] slots = new VerbSlot[maxIndex];
        for (Map.Entry<Integer, VerbSlot> entry : slotMap.entrySet()) {
            slots[entry.getKey() - 1] = entry.getValue();
        }
        return slots;
    }

    /***************************************************************
     * Parses a single slot definition.
     ***************************************************************/
    private static Slot parseSlot(String templateName, String slotKey, JsonNode slotNode,
                                  double defaultCountProbDropoff, double defaultNamedHumanFreq) {
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
                    + " is missing required type (\"subclass\", \"instance\", or \"positional\").");
            System.exit(1);
        }
        String typeString = typeNode.asText().trim().toLowerCase();
        Slot.TermSelectionType type = Slot.TermSelectionType.INSTANCE;
        if ("subclass".equals(typeString)) {
            type = Slot.TermSelectionType.SUBCLASS;
        }
        else if ("instance".equals(typeString)) {
            type = Slot.TermSelectionType.INSTANCE;
        }
        else if ("positional".equals(typeString)) {
            type = Slot.TermSelectionType.POSITIONAL_INSTANCE;
        }
        else {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid type '" + typeNode.asText() + "'. Expected \"subclass\", \"instance\", or \"positional\".");
            System.exit(1);
        }
        // Positional slots don't have countable, variable, definite_freq, or named_human_freq —
        // those concepts don't apply to PositionalAttribute instances (they're not entity instances).
        // Exclusion lists ARE supported on positional slots.
        if (type == Slot.TermSelectionType.POSITIONAL_INSTANCE) {
            List<String> positionalExclude = new ArrayList<>();
            JsonNode positionalExcludeNode = slotNode.get("exclude");
            if (positionalExcludeNode != null && positionalExcludeNode.isArray()) {
                for (JsonNode item : positionalExcludeNode) {
                    if (item.isTextual()) positionalExclude.add(item.asText());
                }
            }
            boolean includePrep = slotNode.path("include_prep").asBoolean(true);
            return new Slot(sumoTerms, type, false, 0.0, null, 0.0, defaultCountProbDropoff, 0.0,
                    positionalExclude, 0.0, defaultCountProbDropoff, includePrep);
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
        String variable = null;
        JsonNode variableNode = slotNode.get("variable");
        if (variableNode != null && variableNode.isTextual()) {
            variable = variableNode.asText();
        }
        double definiteFreq = 0.0;
        JsonNode definiteFreqNode = slotNode.get("definite_freq");
        if (definiteFreqNode != null && definiteFreqNode.isNumber()) {
            definiteFreq = definiteFreqNode.asDouble();
        }
        JsonNode countableCountProbDropoffNode = countableNode.get("count_prob_dropoff");
        double countProbDropoff = (countableCountProbDropoffNode != null && countableCountProbDropoffNode.isNumber())
                ? countableCountProbDropoffNode.asDouble()
                : defaultCountProbDropoff;
        if (countProbDropoff <= 0.0 || countProbDropoff > 1.0) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid count_prob_dropoff " + countProbDropoff + "; expected (0,1].");
            System.exit(1);
        }
        JsonNode listFreqNode = countableNode.get("list_freq");
        double listFreq = (listFreqNode != null && listFreqNode.isNumber())
                ? listFreqNode.asDouble() : 0.0;
        JsonNode listProbDropoffNode = countableNode.get("list_prob_dropoff");
        double listProbDropoff = (listProbDropoffNode != null && listProbDropoffNode.isNumber())
                ? listProbDropoffNode.asDouble() : defaultCountProbDropoff;
        if (listProbDropoff <= 0.0 || listProbDropoff > 1.0) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid list_prob_dropoff " + listProbDropoff + "; expected (0,1].");
            System.exit(1);
        }
        JsonNode namedHumanFreqNode = slotNode.get("named_human_freq");
        double namedHumanFreq = (namedHumanFreqNode != null && namedHumanFreqNode.isNumber())
                ? namedHumanFreqNode.asDouble()
                : defaultNamedHumanFreq;
        List<String> exclude = new ArrayList<>();
        JsonNode excludeNode = slotNode.get("exclude");
        if (excludeNode != null && excludeNode.isArray()) {
            for (JsonNode item : excludeNode) {
                if (item.isTextual()) exclude.add(item.asText());
            }
        }
        return new Slot(sumoTerms, type, countablePossible, countableFreq, variable, definiteFreq,
                countProbDropoff, namedHumanFreq, exclude, listFreq, listProbDropoff);
    }

    /***************************************************************
     * Parses logic from either a clause-array object or a legacy
     * plain string.
     ***************************************************************/
    private static LogicTemplate parseLogicTemplate(JsonNode logicNode) {
        List<String> clauses = new ArrayList<>();
        if (logicNode == null || logicNode.isMissingNode() || logicNode.isNull()) {
            return new LogicTemplate(clauses);
        }
        if (logicNode.isTextual()) {
            clauses.add(logicNode.asText());
            return new LogicTemplate(clauses);
        }
        JsonNode clausesNode = logicNode.path("clauses");
        if (clausesNode.isArray()) {
            for (JsonNode clauseNode : clausesNode) {
                if (clauseNode.isTextual()) {
                    clauses.add(clauseNode.asText());
                }
            }
        }
        return new LogicTemplate(clauses);
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
        JsonNode negationNode = slotNode.get("negation");
        JsonNode negFreqNode = slotNode.get("neg_freq");
        if (verbsNode == null || !verbsNode.isArray()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey + " is missing required verbs array.");
            System.exit(1);
        }
        if (negationNode == null || !negationNode.isTextual()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " is missing required negation setting (\"on\" or \"off\").");
            System.exit(1);
        }
        String negationValue = negationNode.asText().trim().toLowerCase();
        boolean negationOn = false;
        if ("on".equals(negationValue)) {
            negationOn = true;
        }
        else if ("off".equals(negationValue)) {
            negationOn = false;
        }
        else {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid negation value '" + negationNode.asText() + "'. Expected \"on\" or \"off\".");
            System.exit(1);
        }
        if (negFreqNode == null || !negFreqNode.isNumber()) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " is missing required neg_freq (number).");
            System.exit(1);
        }
        double negFreq = negFreqNode.asDouble();
        if (negFreq < 0.0 || negFreq > 1.0) {
            System.err.println("Template '" + templateName + "', slot " + slotKey
                    + " has invalid neg_freq " + negFreq + "; expected range [0.0, 1.0].");
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
        JsonNode variableNode = slotNode.get("variable");
        String variable = (variableNode != null && variableNode.isTextual()) ? variableNode.asText().trim() : null;
        JsonNode definiteFreqNode = slotNode.get("definite_freq");
        double definiteFreq = (definiteFreqNode != null && definiteFreqNode.isNumber()) ? definiteFreqNode.asDouble() : 0.0;
        return new VerbSlot(verbs, negationOn, negFreq, variable, definiteFreq);
    }

    /***************************************************************
     * Reads a nested double override if present.
     ***************************************************************/
    private static Double readOptionalDouble(JsonNode node, String parentField, String field) {
        return node == null ? null : readOptionalDouble(node.get(parentField), field);
    }

    /***************************************************************
     * Reads a double override if present at the current object level.
     ***************************************************************/
    private static Double readOptionalDouble(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isNumber()) {
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
        if (value == null || value.isMissingNode() || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    /***************************************************************
     * Reads a nested integer override if present.
     ***************************************************************/
    private static Integer readOptionalInt(JsonNode node, String parentField, String field) {
        return node == null ? null : readOptionalInt(node.get(parentField), field);
    }

    /***************************************************************
     * Reads a boolean override if present.
     ***************************************************************/
    private static Boolean readOptionalBoolean(JsonNode node, String parentField, String field) {
        return node == null ? null : readOptionalBoolean(node.get(parentField), field);
    }

    /***************************************************************
     * Reads a boolean override if present at the current object level.
     ***************************************************************/
    private static Boolean readOptionalBoolean(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isBoolean()) {
            return null;
        }
        return value.asBoolean();
    }

    /***************************************************************
     * Reads a string override if present at the current object level.
     ***************************************************************/
    private static String readOptionalString(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }
}
