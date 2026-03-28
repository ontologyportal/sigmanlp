package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import com.articulate.nlp.morphodb.MorphoCategoricalSchema;
import com.articulate.nlp.morphodb.MorphoFlatSchemaUtils;
import com.articulate.nlp.morphodb.MorphoWordNetUtils;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Interactive console tool for reviewing disagreements between current gold
 * annotations and the reference MorphoDB.
 */
public class HumanAnnotationReviewTool {

    private static final String DEFAULT_GOLD_DIR_NAME = "gold";
    private static final String DEFAULT_REFERENCE_DB = "~/.sigmanlp/MorphoDB_Research/openai__gpt-5_2";
    private static final String REVIEW_LOG_DIR_NAME = "review_logs";
    private static final String REVIEW_SOURCE = "reference_disagreement_review";
    private static final String REVIEW_SOURCE_UNDO = "reference_disagreement_review_undo";

    private static final String DECISION_KEEP = "keep";
    private static final String DECISION_ADOPT_REFERENCE = "adopt_reference";
    private static final String DECISION_UNDO = "undo";

    private static final Map<String, String> PROPERTY_INSTRUCTIONS = new LinkedHashMap<>();
    private static final Map<String, String> PROPERTY_WORD_KEY = new LinkedHashMap<>();
    private static final List<String> REVIEW_MODES;

    private static final String BOLD = "\033[1m";
    private static final String CYAN = "\033[36m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String MAGENTA = "\033[35m";
    private static final String DIM = "\033[2m";
    private static final String RESET = "\033[0m";

    static {
        PROPERTY_INSTRUCTIONS.put("countability",
                "Review whether the current gold countability label should stay as-is or be replaced by the reference label.\n" +
                        "  Count noun      – can be pluralised (a dog / dogs)\n" +
                        "  Mass noun       – uncountable substance/concept (water, information)\n" +
                        "  Proper noun     – name of a unique entity (London, Einstein)\n" +
                        "  Count and mass  – both uses exist (chicken / some chicken)\n" +
                        "  Unknown         – cannot determine");
        PROPERTY_WORD_KEY.put("countability", "noun");

        PROPERTY_INSTRUCTIONS.put("humanness",
                "Review whether the current gold humanness label should stay as-is or be replaced by the reference label.\n" +
                        "  Human                – always refers to people (doctor, child)\n" +
                        "  Non-human            – never refers to people (rock, algorithm)\n" +
                        "  Human and non-human  – can refer to either (member, agent)\n" +
                        "  Unknown              – cannot determine");
        PROPERTY_WORD_KEY.put("humanness", "noun");

        PROPERTY_INSTRUCTIONS.put("agentivity",
                "Review whether the current gold agentivity label should stay as-is or be replaced by the reference label.\n" +
                        "  Agentive      – can act intentionally (teacher, robot, committee)\n" +
                        "  Non-agentive  – cannot act intentionally (table, idea, weather)\n" +
                        "  Unknown       – cannot determine");
        PROPERTY_WORD_KEY.put("agentivity", "noun");

        PROPERTY_INSTRUCTIONS.put("collective",
                "Review whether the current gold collective-noun label should stay as-is or be replaced by the reference label.\n" +
                        "  Collective         – refers to a group acting as one (team, fleet, committee)\n" +
                        "  Not collective     – refers to a single entity or concept\n" +
                        "  Context-dependent  – collective in some uses, not others (faculty, staff)\n" +
                        "  Unknown            – cannot determine");
        PROPERTY_WORD_KEY.put("collective", "noun");

        PROPERTY_INSTRUCTIONS.put("indefinite_article",
                "Review whether the current gold indefinite-article label should stay as-is or be replaced by the reference label.\n" +
                        "  a     – consonant sound (a dog, a university)\n" +
                        "  an    – vowel sound (an apple, an hour)\n" +
                        "  none  – proper noun / mass noun that does not take an article");
        PROPERTY_WORD_KEY.put("indefinite_article", "noun");

        PROPERTY_INSTRUCTIONS.put("plural_regularity",
                "Review whether the current gold plural-regularity label should stay as-is or be replaced by the reference label.\n" +
                        "  Regular    – follows the default English plural pattern (dog/dogs, church/churches, city/cities)\n" +
                        "  Irregular  – has a non-default plural or suppletive form (child/children, mouse/mice)\n" +
                        "  Unknown    – cannot determine");
        PROPERTY_WORD_KEY.put("plural_regularity", "noun");

        PROPERTY_INSTRUCTIONS.put("causativity",
                "Review whether the current gold causativity label should stay as-is or be replaced by the reference label.\n" +
                        "  Causative      – subject causes a change in the object (break, kill, melt)\n" +
                        "  Non-causative  – no patient undergoes a caused change (sleep, arrive, exist)\n" +
                        "  Mixed          – both causative and non-causative uses (open, roll)\n" +
                        "  Unknown        – cannot determine");
        PROPERTY_WORD_KEY.put("causativity", "verb");

        PROPERTY_INSTRUCTIONS.put("reflexivity",
                "Review whether the current gold reflexivity label should stay as-is or be replaced by the reference label.\n" +
                        "  Must be reflexive  – requires a reflexive pronoun\n" +
                        "  Can be reflexive   – optionally reflexive\n" +
                        "  Never reflexive    – reflexive form is ungrammatical");
        PROPERTY_WORD_KEY.put("reflexivity", "verb");

        PROPERTY_INSTRUCTIONS.put("reciprocity",
                "Review whether the current gold reciprocity label should stay as-is or be replaced by the reference label.\n" +
                        "  Must be reciprocal  – requires mutual action\n" +
                        "  Can be reciprocal   – optionally reciprocal\n" +
                        "  Never reciprocal    – no reciprocal reading");
        PROPERTY_WORD_KEY.put("reciprocity", "verb");

        PROPERTY_INSTRUCTIONS.put("aktionsart",
                "Review whether the current gold achievement/process label should stay as-is or be replaced by the reference label.\n" +
                        "  Achievement  – punctual change of state (arrive, die, find)\n" +
                        "  Process      – activity with duration (run, read, swim)\n" +
                        "  Mixed        – can be either depending on context\n" +
                        "  Unknown      – cannot determine");
        PROPERTY_WORD_KEY.put("aktionsart", "verb");

        PROPERTY_INSTRUCTIONS.put("valence",
                "Review whether the current gold valence label should stay as-is or be replaced by the reference label.\n" +
                        "  [0] 0-valent  – impersonal, no true subject (rain, snow)\n" +
                        "  [1] 1-valent  – intransitive, subject only\n" +
                        "  [2] 2-valent  – transitive, subject + object\n" +
                        "  [3] 3-valent  – ditransitive, subject + object + indirect object\n" +
                        "  [4] Unknown   – cannot determine");
        PROPERTY_WORD_KEY.put("valence", "verb");

        PROPERTY_INSTRUCTIONS.put("conjugation_regularity",
                "Review whether the current gold conjugation-regularity label should stay as-is or be replaced by the reference label.\n" +
                        "  Regular    – follows standard -ed / -ing patterns\n" +
                        "  Irregular  – has non-standard past/participle forms\n" +
                        "  Unknown    – cannot determine");
        PROPERTY_WORD_KEY.put("conjugation_regularity", "verb");

        PROPERTY_INSTRUCTIONS.put("adjective_category",
                "Review whether the current gold adjective semantic class should stay as-is or be replaced by the reference label.");
        PROPERTY_WORD_KEY.put("adjective_category", "adjective");

        PROPERTY_INSTRUCTIONS.put("adverb_category",
                "Review whether the current gold adverb semantic class should stay as-is or be replaced by the reference label.");
        PROPERTY_WORD_KEY.put("adverb_category", "adverb");

        List<String> modes = new ArrayList<>();
        for (MorphoCategoricalSchema.CategorySpec spec : MorphoCategoricalSchema.getHumanAnnotationCategorySpecs()) {
            modes.add(spec.getModeKey());
        }
        REVIEW_MODES = Collections.unmodifiableList(modes);
    }

    private static class GoldState {
        String itemId;
        String mode;
        String synsetId;
        String lemma;
        String word;
        String definition;
        String label;
    }

    private static class ReferenceState {
        String itemId;
        String label;
        List<String> detailLines = new ArrayList<>();
    }

    private static class ReviewDecision {
        String itemId;
        String mode;
        String goldLabelAtReview;
        String referenceLabelAtReview;
        String decision;
    }

    private static class ReviewItem {
        final String mode;
        final GoldState gold;
        final ReferenceState reference;

        ReviewItem(String mode, GoldState gold, ReferenceState reference) {
            this.mode = mode;
            this.gold = gold;
            this.reference = reference;
        }
    }

    private static class MenuContext {
        final Map<String, List<ReviewItem>> itemsByMode = new LinkedHashMap<>();
        final Map<String, String> loadErrorByMode = new LinkedHashMap<>();
    }

    private static class UndoEntry {
        final ReviewItem item;
        final int listIndex;
        final String decision;
        final Path goldFile;
        final Path reviewLogFile;

        UndoEntry(ReviewItem item,
                  int listIndex,
                  String decision,
                  Path goldFile,
                  Path reviewLogFile) {
            this.item = item;
            this.listIndex = listIndex;
            this.decision = decision;
            this.goldFile = goldFile;
            this.reviewLogFile = reviewLogFile;
        }
    }

    public static void main(String[] args) throws Exception {

        if (args != null && args.length > 0) {
            System.out.println(highlight("Ignoring CLI arguments; using built-in defaults for gold dir and reference DB."));
            System.out.println();
        }

        Path goldDir = Paths.get(DEFAULT_GOLD_DIR_NAME);
        Files.createDirectories(goldDir);
        Files.createDirectories(goldDir.resolve(REVIEW_LOG_DIR_NAME));

        Path referenceDb = GenMorphoUtils.expandHomePath(DEFAULT_REFERENCE_DB);
        String referenceModel = referenceDb.getFileName() == null
                ? DEFAULT_REFERENCE_DB
                : referenceDb.getFileName().toString();

        System.out.println(section("Initializing WordNet") + " " + meta("(requires SIGMA_HOME)..."));
        MorphoWordNetUtils.initWordNet();

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            MenuContext menuContext = buildMenuContext(goldDir, referenceDb, referenceModel);
            String mode = selectMode(console, goldDir, referenceModel, menuContext);
            if (mode == null) {
                return;
            }
            runReviewSession(console, goldDir, mode, referenceModel, menuContext.itemsByMode.getOrDefault(mode, Collections.emptyList()));
        }
    }

    private static MenuContext buildMenuContext(Path goldDir,
                                                Path referenceDb,
                                                String referenceModel) {

        MenuContext context = new MenuContext();
        for (String mode : REVIEW_MODES) {
            try {
                List<ReviewItem> items = loadOutstandingReviewItems(mode, goldDir, referenceDb, referenceModel);
                context.itemsByMode.put(mode, items);
            } catch (Exception e) {
                context.itemsByMode.put(mode, Collections.emptyList());
                context.loadErrorByMode.put(mode, e.getMessage());
            }
        }
        return context;
    }

    private static String selectMode(BufferedReader console,
                                     Path goldDir,
                                     String referenceModel,
                                     MenuContext context) throws IOException {

        while (true) {
            printSelectionMenu(goldDir, referenceModel, context);
            String input = console.readLine();
            if (input == null) {
                return null;
            }

            String trimmed = input.trim().toLowerCase(Locale.ROOT);
            if ("q".equals(trimmed)) {
                return null;
            }

            int choice;
            try {
                choice = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                System.out.println("  → " + error("invalid input") + "\n");
                continue;
            }

            if (choice < 1 || choice > REVIEW_MODES.size()) {
                System.out.println("  → " + error("invalid input") + "\n");
                continue;
            }

            String mode = REVIEW_MODES.get(choice - 1);
            String loadError = context.loadErrorByMode.get(mode);
            if (loadError != null) {
                System.out.println("  → " + error(loadError) + "\n");
                continue;
            }
            return mode;
        }
    }

    private static void printSelectionMenu(Path goldDir,
                                           String referenceModel,
                                           MenuContext context) {

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + CYAN + "Morphological Gold-vs-Reference Review Tool" + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(section("Select a property to review:"));
        System.out.println();
        System.out.println(meta("Gold dir:        ") + accent(goldDir.toString()));
        System.out.println(meta("Reference model: ") + accent(referenceModel));
        System.out.println();

        for (int i = 0; i < REVIEW_MODES.size(); i++) {
            String mode = REVIEW_MODES.get(i);
            int differences = context.itemsByMode.getOrDefault(mode, Collections.emptyList()).size();
            String loadError = context.loadErrorByMode.get(mode);
            if (loadError != null) {
                System.out.printf(Locale.ROOT, "  %s)  %s %s%n",
                        highlight(String.format(Locale.ROOT, "%4d", i + 1)),
                        accent(String.format(Locale.ROOT, "%-28s", mode)),
                        error("(load error)"));
            } else {
                System.out.printf(Locale.ROOT, "  %s)  %s %s%n",
                        highlight(String.format(Locale.ROOT, "%4d", i + 1)),
                        accent(String.format(Locale.ROOT, "%-28s", mode)),
                        success(String.format(Locale.ROOT, "%6d differences", differences)));
            }
        }

        System.out.println();
        System.out.println("     " + highlight("q") + ")  " + meta("quit"));
        System.out.println();
        System.out.print("  " + accent("Select [1-" + REVIEW_MODES.size() + "] > "));
        System.out.flush();
    }

    private static void runReviewSession(BufferedReader console,
                                         Path goldDir,
                                         String mode,
                                         String referenceModel,
                                         List<ReviewItem> items) throws IOException {

        Path goldFile = goldDir.resolve(mode + ".jsonl");
        Path reviewLogFile = goldDir.resolve(REVIEW_LOG_DIR_NAME).resolve(mode + ".jsonl");

        if (items == null || items.isEmpty()) {
            System.out.println();
            System.out.println(success("No outstanding disagreements for " + mode + "."));
            System.out.println();
            return;
        }

        printHeader(mode, goldFile, reviewLogFile, items.size(), PROPERTY_INSTRUCTIONS.get(mode));

        List<UndoEntry> undoStack = new ArrayList<>();
        int reviewedThisSession = 0;
        int skippedThisSession = 0;
        long sessionStart = System.currentTimeMillis();

        for (int idx = 0; idx < items.size(); idx++) {
            ReviewItem item = items.get(idx);
            displayItem(item, idx + 1, items.size(), reviewedThisSession, skippedThisSession);

            String input = console.readLine();
            if (input == null) {
                break;
            }
            input = input.trim().toLowerCase(Locale.ROOT);

            if ("q".equals(input)) {
                printSessionSummary(mode, reviewedThisSession, skippedThisSession, reviewLogFile.toString(), sessionStart);
                return;
            }
            if ("s".equals(input)) {
                skippedThisSession++;
                System.out.println("  → " + meta("skipped") + "\n");
                continue;
            }
            if ("?".equals(input)) {
                printLiveStats(reviewedThisSession, skippedThisSession, items.size(), idx, sessionStart);
                idx--;
                continue;
            }
            if ("r".equals(input)) {
                System.out.println();
                System.out.println(PROPERTY_INSTRUCTIONS.get(mode));
                System.out.println();
                idx--;
                continue;
            }
            if ("u".equals(input)) {
                if (undoStack.isEmpty()) {
                    System.out.println("  → " + highlight("nothing to undo") + "\n");
                    idx--;
                    continue;
                }
                UndoEntry last = undoStack.remove(undoStack.size() - 1);
                undoLastDecision(last, referenceModel);
                reviewedThisSession = Math.max(0, reviewedThisSession - 1);
                System.out.println("  → " + highlight("undid ") + accent(last.decision)
                        + highlight(" for ") + accent("\"" + last.item.gold.word + "\"") + "\n");
                idx = last.listIndex - 1;
                continue;
            }

            if ("k".equals(input) || "1".equals(input)) {
                appendReviewDecision(reviewLogFile, buildReviewDecisionJson(item, referenceModel, DECISION_KEEP, null));
                undoStack.add(new UndoEntry(item, idx, DECISION_KEEP, goldFile, reviewLogFile));
                reviewedThisSession++;
                System.out.println("  → " + success("kept gold label") + "\n");
                continue;
            }
            if ("a".equals(input) || "2".equals(input)) {
                appendJsonLine(goldFile, buildAdoptReferenceGoldJson(item, referenceModel));
                appendReviewDecision(reviewLogFile, buildReviewDecisionJson(item, referenceModel, DECISION_ADOPT_REFERENCE, null));
                undoStack.add(new UndoEntry(item, idx, DECISION_ADOPT_REFERENCE, goldFile, reviewLogFile));
                reviewedThisSession++;
                System.out.println("  → " + success("adopted reference label") + "\n");
                continue;
            }

            System.out.println("  → " + error("invalid input") + "\n");
            idx--;
        }

        printSessionSummary(mode, reviewedThisSession, skippedThisSession, reviewLogFile.toString(), sessionStart);
    }

    private static void undoLastDecision(UndoEntry undoEntry, String referenceModel) throws IOException {

        appendReviewDecision(undoEntry.reviewLogFile,
                buildReviewDecisionJson(undoEntry.item, referenceModel, DECISION_UNDO, undoEntry.decision));
        if (DECISION_ADOPT_REFERENCE.equals(undoEntry.decision)) {
            appendJsonLine(undoEntry.goldFile, buildUndoGoldJson(undoEntry.item, referenceModel));
        }
    }

    private static JSONObject buildReviewDecisionJson(ReviewItem item,
                                                      String referenceModel,
                                                      String decision,
                                                      String undoneDecision) {

        JSONObject root = new JSONObject();
        root.put("item_id", item.gold.itemId);
        root.put("property", item.mode);
        root.put("synsetId", item.gold.synsetId);
        root.put("lemma", item.gold.lemma);
        root.put("word", item.gold.word);
        root.put("gold_label_at_review", item.gold.label);
        root.put("reference_label_at_review", item.reference.label);
        root.put("decision", decision);
        root.put("reference_model", referenceModel);
        root.put("timestamp", System.currentTimeMillis());
        if (undoneDecision != null && !undoneDecision.trim().isEmpty()) {
            root.put("undone_decision", undoneDecision);
        }
        return root;
    }

    private static JSONObject buildAdoptReferenceGoldJson(ReviewItem item, String referenceModel) {

        JSONObject root = new JSONObject();
        root.put("synsetId", item.gold.synsetId);
        root.put("lemma", item.gold.lemma);
        root.put("word", item.gold.word);
        root.put("definition", item.gold.definition);
        root.put("property", item.mode);
        root.put("human_label", item.reference.label);
        root.put("timestamp", System.currentTimeMillis());
        root.put("review_source", REVIEW_SOURCE);
        root.put("reference_model", referenceModel);
        root.put("previous_human_label", item.gold.label);
        return root;
    }

    private static JSONObject buildUndoGoldJson(ReviewItem item, String referenceModel) {

        JSONObject root = new JSONObject();
        root.put("synsetId", item.gold.synsetId);
        root.put("lemma", item.gold.lemma);
        root.put("word", item.gold.word);
        root.put("definition", item.gold.definition);
        root.put("property", item.mode);
        root.put("human_label", item.gold.label);
        root.put("timestamp", System.currentTimeMillis());
        root.put("review_source", REVIEW_SOURCE_UNDO);
        root.put("reference_model", referenceModel);
        root.put("previous_human_label", item.reference.label);
        return root;
    }

    private static void appendReviewDecision(Path reviewLogFile, JSONObject jsonObject) throws IOException {

        appendJsonLine(reviewLogFile, jsonObject);
    }

    private static void appendJsonLine(Path path, JSONObject jsonObject) throws IOException {

        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            writer.write(jsonObject.toString());
            writer.newLine();
        }
    }

    private static List<ReviewItem> loadOutstandingReviewItems(String mode,
                                                               Path goldDir,
                                                               Path referenceDb,
                                                               String referenceModel) throws IOException {

        Map<String, GoldState> goldStateById = loadCurrentGoldState(goldDir.resolve(mode + ".jsonl"), mode);
        if (goldStateById.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ReferenceState> referenceStateById = loadReferenceStates(mode, referenceDb, referenceModel);
        if (referenceStateById.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ReviewDecision> latestReviewDecisionById =
                loadLatestReviewDecisions(goldDir.resolve(REVIEW_LOG_DIR_NAME).resolve(mode + ".jsonl"));

        List<ReviewItem> items = new ArrayList<>();
        for (GoldState goldState : goldStateById.values()) {
            ReferenceState referenceState = referenceStateById.get(goldState.itemId);
            if (referenceState == null || referenceState.label == null || referenceState.label.trim().isEmpty()) {
                continue;
            }
            if (referenceState.label.equals(goldState.label)) {
                continue;
            }
            ReviewDecision latestDecision = latestReviewDecisionById.get(goldState.itemId);
            if (shouldSuppress(goldState.label, referenceState.label, latestDecision)) {
                continue;
            }
            items.add(new ReviewItem(mode, goldState, referenceState));
        }

        items.sort(Comparator
                .comparing((ReviewItem item) -> safeLower(item.gold.lemma))
                .thenComparing(item -> safeLower(item.gold.word)));
        return items;
    }

    private static boolean shouldSuppress(String currentGoldLabel,
                                          String currentReferenceLabel,
                                          ReviewDecision latestDecision) {

        if (latestDecision == null) {
            return false;
        }
        if (!DECISION_KEEP.equals(latestDecision.decision)
                && !DECISION_ADOPT_REFERENCE.equals(latestDecision.decision)) {
            return false;
        }
        return Objects.equals(currentGoldLabel, latestDecision.goldLabelAtReview)
                && Objects.equals(currentReferenceLabel, latestDecision.referenceLabelAtReview);
    }

    private static Map<String, GoldState> loadCurrentGoldState(Path goldFile, String mode) throws IOException {

        Map<String, GoldState> states = new LinkedHashMap<>();
        if (goldFile == null || !Files.exists(goldFile)) {
            return states;
        }

        try (BufferedReader reader = Files.newBufferedReader(goldFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    String itemId = extractItemId(json);
                    if (itemId.isEmpty()) {
                        continue;
                    }

                    String label = json.optString("human_label", "").trim();
                    if (label.isEmpty()) {
                        continue;
                    }

                    if ("__UNDONE__".equals(label)) {
                        states.remove(itemId);
                        continue;
                    }

                    GoldState state = new GoldState();
                    state.itemId = itemId;
                    state.mode = mode;
                    state.synsetId = json.optString("synsetId", "");
                    state.lemma = json.optString("lemma", itemId).trim();
                    if (state.lemma.isEmpty()) {
                        state.lemma = itemId;
                    }
                    state.word = json.optString("word", state.lemma).trim();
                    if (state.word.isEmpty()) {
                        state.word = state.lemma;
                    }
                    state.definition = json.optString("definition", "").trim();
                    if (state.definition.isEmpty()) {
                        state.definition = lookupDefinition(PROPERTY_WORD_KEY.get(mode), state.synsetId);
                    }
                    state.label = label;
                    states.put(itemId, state);
                } catch (Exception ignored) {
                }
            }
        }

        return states;
    }

    private static Map<String, ReviewDecision> loadLatestReviewDecisions(Path reviewLogFile) throws IOException {

        Map<String, ReviewDecision> decisions = new LinkedHashMap<>();
        if (reviewLogFile == null || !Files.exists(reviewLogFile)) {
            return decisions;
        }

        try (BufferedReader reader = Files.newBufferedReader(reviewLogFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    String itemId = extractItemId(json);
                    if (itemId.isEmpty()) {
                        continue;
                    }

                    ReviewDecision decision = new ReviewDecision();
                    decision.itemId = itemId;
                    decision.mode = json.optString("property", "");
                    decision.goldLabelAtReview = json.optString("gold_label_at_review", "");
                    decision.referenceLabelAtReview = json.optString("reference_label_at_review", "");
                    decision.decision = json.optString("decision", "");
                    decisions.put(itemId, decision);
                } catch (Exception ignored) {
                }
            }
        }

        return decisions;
    }

    private static Map<String, ReferenceState> loadReferenceStates(String mode,
                                                                   Path referenceDb,
                                                                   String referenceModel) throws IOException {

        if ("plural_regularity".equals(mode)) {
            return loadPluralRegularityReferenceStates(referenceDb);
        }
        if ("conjugation_regularity".equals(mode)) {
            return loadConjugationRegularityReferenceStates(referenceDb, referenceModel);
        }

        MorphoCategoricalSchema.CategorySpec spec = MorphoCategoricalSchema.getByModeKey(mode);
        if (spec == null || spec.getRelativePath() == null || spec.getRelativePath().trim().isEmpty()) {
            return Collections.emptyMap();
        }

        Path inputFile = referenceDb.resolve(spec.getRelativePath());
        if (!Files.exists(inputFile)) {
            throw new IOException("Reference file not found: " + inputFile);
        }

        MorphoDBLoader.LoadResult loadResult =
                MorphoDBLoader.loadClassificationsWithStats(inputFile.toString(), spec.getFieldName(), spec.getFieldName());
        Map<String, ReferenceState> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : loadResult.classifications.entrySet()) {
            ReferenceState state = new ReferenceState();
            state.itemId = entry.getKey();
            state.label = entry.getValue();
            result.put(state.itemId, state);
        }
        return result;
    }

    private static Map<String, ReferenceState> loadPluralRegularityReferenceStates(Path referenceDb) throws IOException {

        Path inputFile = referenceDb.resolve("noun/Plurals.txt");
        if (!Files.exists(inputFile)) {
            throw new IOException("Reference file not found: " + inputFile);
        }

        Map<String, ReferenceState> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    if (MorphoDBLoader.isNonSuccessRecord(json)) {
                        continue;
                    }
                    String itemId = MorphoDBLoader.extractNormalizedLemma(json);
                    if (itemId.isEmpty()) {
                        continue;
                    }

                    String label = resolvePluralRegularityLabel(json);
                    if (label == null || label.isEmpty()) {
                        continue;
                    }

                    String singular = json.optString("singular", json.optString("lemma", "")).trim();
                    String plural = json.optString("plural", "").trim();
                    String type = json.optString("type", "").trim();

                    ReferenceState state = new ReferenceState();
                    state.itemId = itemId;
                    state.label = label;
                    if (!singular.isEmpty()) {
                        state.detailLines.add("Singular:          " + singular);
                    }
                    if (!plural.isEmpty()) {
                        state.detailLines.add("Plural:            " + plural);
                    }
                    if (!type.isEmpty()) {
                        state.detailLines.add("Type:              " + type);
                    }
                    state.detailLines.add("Resolved regularity: " + label);
                    result.put(itemId, state);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private static String resolvePluralRegularityLabel(JSONObject json) {

        if (json == null) {
            return null;
        }
        String pluralPattern = json.optString("plural_pattern", "").trim();
        if ("Regular".equalsIgnoreCase(pluralPattern) || "Irregular".equalsIgnoreCase(pluralPattern)) {
            return capitalizeWord(pluralPattern);
        }

        String singular = json.optString("singular", json.optString("lemma", "")).trim();
        String plural = json.optString("plural", "").trim();
        if (singular.isEmpty() || plural.isEmpty()) {
            return null;
        }
        String derived = MorphoFlatSchemaUtils.pluralPatternFor(singular, plural);
        if ("Regular".equalsIgnoreCase(derived) || "Irregular".equalsIgnoreCase(derived)) {
            return capitalizeWord(derived);
        }
        return null;
    }

    private static Map<String, ReferenceState> loadConjugationRegularityReferenceStates(Path referenceDb,
                                                                                        String referenceModel) throws IOException {

        Path inputFile = referenceDb.resolve("verb/VerbConjugations.txt");
        if (!Files.exists(inputFile)) {
            throw new IOException("Reference file not found: " + inputFile);
        }

        Map<String, ReferenceState> result = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    if (MorphoDBLoader.isNonSuccessRecord(json)
                            || MorphoDBLoader.isInvalidCategorizationForField(json, "regularity")) {
                        continue;
                    }

                    GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
                    if (record == null || record.lemma == null || record.lemma.trim().isEmpty()
                            || record.regularity == null || record.regularity.trim().isEmpty()) {
                        continue;
                    }

                    ReferenceState state = new ReferenceState();
                    state.itemId = record.lemma;
                    state.label = record.regularity;
                    state.detailLines.add("Reference model:   " + referenceModel);
                    state.detailLines.add("Regularity:        " + record.regularity);
                    state.detailLines.add("Infinitive:        " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_INFINITIVE));
                    state.detailLines.add("3sg present:       " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_PRESENT_3SG));
                    state.detailLines.add("Simple past:       " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_SIMPLE_PAST));
                    state.detailLines.add("Past participle:   " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE));
                    state.detailLines.add("Gerund:            " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_GERUND));
                    result.put(state.itemId, state);
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private static String extractItemId(JSONObject json) {

        if (json == null) {
            return "";
        }
        String itemId = json.optString("item_id", "").trim();
        if (itemId.isEmpty()) {
            itemId = json.optString("audit_id", "").trim();
        }
        if (itemId.isEmpty()) {
            itemId = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
        }
        if (itemId.isEmpty()) {
            itemId = GenMorphoUtils.normalizeLemma(json.optString("word", ""));
        }
        return itemId;
    }

    private static void displayItem(ReviewItem item,
                                    int displayNum,
                                    int totalItems,
                                    int reviewedThisSession,
                                    int skippedThisSession) {

        System.out.println(BOLD + "[" + displayNum + "/" + totalItems + " | "
                + reviewedThisSession + " reviewed | " + skippedThisSession + " skipped]  "
                + CYAN + item.gold.word + RESET + "  " + DIM + "(synset " + item.gold.synsetId + ")" + RESET);
        System.out.println("  " + meta(item.gold.definition));
        System.out.println("  " + meta("Property:        ") + accent(item.mode));
        System.out.println("  " + meta("Current gold:    ") + highlight(item.gold.label));
        System.out.println("  " + meta("Reference label: ") + success(item.reference.label));

        if ("valence".equals(item.mode)) {
            String frameLines = getVerbFrameLines(item.gold);
            if (frameLines != null) {
                System.out.println(meta(frameLines));
            }
        }
        for (String detailLine : item.reference.detailLines) {
            System.out.println("  " + accent(detailLine));
        }

        System.out.println();
        System.out.print("  " + accent("[1/k] keep gold / [2/a] adopt reference / [s]kip / [u]ndo / [q] menu / [?] stats / [r] instructions > "));
        System.out.flush();
    }

    private static void printHeader(String mode,
                                    Path goldFile,
                                    Path reviewLogFile,
                                    int totalItems,
                                    String instructions) {

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + CYAN + "Morphological Gold-vs-Reference Review Tool" + RESET);
        System.out.println(BOLD + "  " + meta("Mode:         ") + accent(mode) + RESET);
        System.out.println(BOLD + "  " + meta("Differences:  ") + highlight(String.valueOf(totalItems)) + RESET);
        System.out.println(BOLD + "  " + meta("Gold file:    ") + meta(goldFile.toString()) + RESET);
        System.out.println(BOLD + "  " + meta("Review log:   ") + meta(reviewLogFile.toString()) + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(section("Instructions"));
        System.out.println(instructions);
        System.out.println();
        System.out.println(section("Controls") + "  " + highlight("[1]") + " keep gold  |  "
                + highlight("[2]") + " adopt reference  |  " + highlight("[s]") + " skip  |  "
                + highlight("[u]") + " undo  |  " + highlight("[q]") + " back to menu");
        System.out.println("           " + highlight("[k/a]") + " also work   |  " + highlight("[?]") + " show stats   |  " + highlight("[r]") + " re-show instructions");
        System.out.println(meta("───────────────────────────────────────────────────────────"));
        System.out.println();
    }

    private static void printLiveStats(int reviewed,
                                       int skipped,
                                       int totalItems,
                                       int currentIndex,
                                       long sessionStart) {

        long elapsed = System.currentTimeMillis() - sessionStart;
        double minutes = elapsed / 60000.0;
        double minutesPerItem = reviewed > 0 ? minutes / reviewed : 0.0;
        int remaining = Math.max(0, totalItems - currentIndex);

        System.out.println();
        System.out.println(section("  ┌─── Session Stats ────────────────────────┐"));
        System.out.printf(Locale.ROOT, "  │  %s %s reviewed, %s skipped%n",
                meta("This session:  "),
                success(String.format(Locale.ROOT, "%5d", reviewed)),
                highlight(String.valueOf(skipped)));
        System.out.printf(Locale.ROOT, "  │  %s %s%n",
                meta("Remaining:     "),
                highlight(String.format(Locale.ROOT, "%5d", remaining)));
        if (reviewed > 0) {
            System.out.printf(Locale.ROOT, "  │  %s %s%n",
                    meta("Pace:          "),
                    accent(String.format(Locale.ROOT, "%.1f sec / item", minutesPerItem * 60.0)));
        }
        System.out.println(section("  └──────────────────────────────────────────┘"));
        System.out.println();
    }

    private static void printSessionSummary(String mode,
                                            int reviewed,
                                            int skipped,
                                            String reviewLogPath,
                                            long sessionStart) {

        long elapsed = System.currentTimeMillis() - sessionStart;
        double minutes = elapsed / 60000.0;

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + GREEN + "Review session complete." + RESET);
        System.out.println(BOLD + "  " + meta("Mode:         ") + accent(mode) + RESET);
        System.out.printf(Locale.ROOT, "  %s %s reviewed, %s skipped (%s)%n",
                meta("This session:  "),
                success(String.valueOf(reviewed)),
                highlight(String.valueOf(skipped)),
                accent(String.format(Locale.ROOT, "%.1f min", minutes)));
        System.out.println("  " + meta("Review log:   ") + meta(reviewLogPath));
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
    }

    private static String lookupDefinition(String pos, String synsetId) {

        if (synsetId == null || synsetId.trim().isEmpty()) {
            return "(no definition)";
        }
        Map<String, String> docs;
        switch (pos) {
            case "noun":
                docs = WordNet.wn.nounDocumentationHash;
                break;
            case "verb":
                docs = WordNet.wn.verbDocumentationHash;
                break;
            case "adjective":
                docs = WordNet.wn.adjectiveDocumentationHash;
                break;
            case "adverb":
                docs = WordNet.wn.adverbDocumentationHash;
                break;
            default:
                return "(no definition)";
        }
        String definition = docs.getOrDefault(synsetId, "(no definition)");
        return definition == null || definition.trim().isEmpty() ? "(no definition)" : definition;
    }

    private static String getVerbFrameLines(GoldState item) {

        if (item == null || item.synsetId == null || item.synsetId.trim().isEmpty()
                || item.word == null || item.word.trim().isEmpty()) {
            return null;
        }
        List<String> frames = WordNetUtilities.getVerbFramesForWord("2" + item.synsetId, item.word);
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("  WordNet verb frames:");
        for (String frame : frames) {
            sb.append("\n    ").append(frame).append("  [").append(inferValenceFromFrame(frame)).append("]");
        }
        return sb.toString();
    }

    private static String inferValenceFromFrame(String frame) {

        if (frame == null || frame.isBlank()) {
            return "(cannot determine valence)";
        }
        String lower = frame.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("it ")) {
            return "0-valent";
        }
        int count = countOccurrences(lower, "somebody") + countOccurrences(lower, "something");
        if (count == 0 && lower.contains("body part")) {
            count = 1;
        }
        return count == 0 ? "(cannot determine valence)" : count + "-valent";
    }

    private static int countOccurrences(String text, String target) {

        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static String safeLower(String text) {

        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String capitalizeWord(String text) {

        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String accent(String text) {
        return CYAN + text + RESET;
    }

    private static String highlight(String text) {
        return YELLOW + text + RESET;
    }

    private static String success(String text) {
        return GREEN + text + RESET;
    }

    private static String error(String text) {
        return RED + text + RESET;
    }

    private static String meta(String text) {
        return DIM + text + RESET;
    }

    private static String section(String text) {
        return BOLD + MAGENTA + text + RESET;
    }
}
