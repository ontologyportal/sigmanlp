package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import com.articulate.nlp.morphodb.MorphoCategoricalSchema;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Interactive console tool for building categorical gold labels and generative
 * audit files.
 */
public class HumanAnnotationTool {

    private static final Map<String, String[]> PROPERTY_OPTIONS = new LinkedHashMap<>();
    private static final Map<String, String> PROPERTY_INSTRUCTIONS = new LinkedHashMap<>();
    private static final Map<String, String> PROPERTY_WORD_KEY = new LinkedHashMap<>();
    private static final String DEFAULT_GOLD_DIR_NAME = "gold";
    private static final String DEFAULT_REFERENCE_DB = "~/.sigmanlp/MorphoDB_Research/openai__gpt-5_2";

    private static final String[] GENERATIVE_OPTIONS = {"Correct", "Incorrect"};

    private static final String BOLD = "\033[1m";
    private static final String CYAN = "\033[36m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String MAGENTA = "\033[35m";
    private static final String DIM = "\033[2m";
    private static final String RESET = "\033[0m";

    private static void addCategoricalOptions(String modeKey) {
        MorphoCategoricalSchema.CategorySpec spec = MorphoCategoricalSchema.getByModeKey(modeKey);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown categorical mode: " + modeKey);
        }
        PROPERTY_OPTIONS.put(modeKey, spec.getCanonicalOptionsArray());
    }

    static {
        addCategoricalOptions("countability");
        PROPERTY_INSTRUCTIONS.put("countability",
                "Classify this noun's countability.\n" +
                        "  Count noun      – can be pluralised (a dog / dogs)\n" +
                        "  Mass noun       – uncountable substance/concept (water, information)\n" +
                        "  Proper noun     – name of a unique entity (London, Einstein)\n" +
                        "  Count and mass  – both uses exist (chicken / some chicken)\n" +
                        "  Unknown         – cannot determine");
        PROPERTY_WORD_KEY.put("countability", "noun");

        addCategoricalOptions("humanness");
        PROPERTY_INSTRUCTIONS.put("humanness",
                "Does this noun refer to a human being?\n" +
                        "  Human                – always refers to people (doctor, child)\n" +
                        "  Non-human            – never refers to people (rock, algorithm)\n" +
                        "  Human and non-human  – can refer to either (member, agent)\n" +
                        "  Unknown              – cannot determine");
        PROPERTY_WORD_KEY.put("humanness", "noun");

        addCategoricalOptions("agentivity");
        PROPERTY_INSTRUCTIONS.put("agentivity",
                "Can the referent of this noun perform intentional actions?\n" +
                        "  Agentive      – can act intentionally (teacher, robot, committee)\n" +
                        "  Non-agentive  – cannot act intentionally (table, idea, weather)\n" +
                        "  Unknown       – cannot determine");
        PROPERTY_WORD_KEY.put("agentivity", "noun");

        addCategoricalOptions("collective");
        PROPERTY_INSTRUCTIONS.put("collective",
                "Is this noun a collective noun (denoting a group)?\n" +
                        "  Collective         – refers to a group acting as one (team, fleet, committee)\n" +
                        "  Not collective     – refers to a single entity or concept\n" +
                        "  Context-dependent  – collective in some uses, not others (faculty, staff)\n" +
                        "  Unknown            – cannot determine");
        PROPERTY_WORD_KEY.put("collective", "noun");

        addCategoricalOptions("indefinite_article");
        PROPERTY_INSTRUCTIONS.put("indefinite_article",
                "What indefinite article precedes this noun?\n" +
                        "  a     – consonant sound (a dog, a university)\n" +
                        "  an    – vowel sound (an apple, an hour)\n" +
                        "  none  – proper noun / mass noun that does not take an article");
        PROPERTY_WORD_KEY.put("indefinite_article", "noun");

        addCategoricalOptions("plural_regularity");
        PROPERTY_INSTRUCTIONS.put("plural_regularity",
                "Is this noun regular or irregular in its plural formation?\n" +
                        "  Regular    – follows the default English plural pattern (dog/dogs, church/churches, city/cities)\n" +
                        "  Irregular  – has a non-default plural or suppletive form (child/children, mouse/mice)\n" +
                        "  Unknown    – cannot determine");
        PROPERTY_WORD_KEY.put("plural_regularity", "noun");

        addCategoricalOptions("causativity");
        PROPERTY_INSTRUCTIONS.put("causativity",
                "Is this verb causative?\n" +
                        "  Causative      – subject causes a change in the object (break, kill, melt)\n" +
                        "  Non-causative  – no patient undergoes a caused change (sleep, arrive, exist)\n" +
                        "  Mixed          – both causative and non-causative uses (open, roll)\n" +
                        "  Unknown        – cannot determine");
        PROPERTY_WORD_KEY.put("causativity", "verb");

        addCategoricalOptions("reflexivity");
        PROPERTY_INSTRUCTIONS.put("reflexivity",
                "Can this verb be used reflexively (subject = object)?\n" +
                        "  Must be reflexive  – requires a reflexive pronoun\n" +
                        "  Can be reflexive   – optionally reflexive\n" +
                        "  Never reflexive    – reflexive form is ungrammatical");
        PROPERTY_WORD_KEY.put("reflexivity", "verb");

        addCategoricalOptions("reciprocity");
        PROPERTY_INSTRUCTIONS.put("reciprocity",
                "Can this verb express a reciprocal action?\n" +
                        "  Must be reciprocal  – requires mutual action\n" +
                        "  Can be reciprocal   – optionally reciprocal\n" +
                        "  Never reciprocal    – no reciprocal reading");
        PROPERTY_WORD_KEY.put("reciprocity", "verb");

        addCategoricalOptions("aktionsart");
        PROPERTY_INSTRUCTIONS.put("aktionsart",
                "Is this verb an achievement or a process?\n" +
                        "  Achievement  – punctual change of state (arrive, die, find)\n" +
                        "  Process      – activity with duration (run, read, swim)\n" +
                        "  Mixed        – can be either depending on context\n" +
                        "  Unknown      – cannot determine");
        PROPERTY_WORD_KEY.put("aktionsart", "verb");

        addCategoricalOptions("valence");
        PROPERTY_INSTRUCTIONS.put("valence",
                "How many core arguments does this verb take?\n" +
                        "  [0] 0-valent  – impersonal, no true subject (rain, snow)\n" +
                        "  [1] 1-valent  – intransitive, subject only\n" +
                        "  [2] 2-valent  – transitive, subject + object\n" +
                        "  [3] 3-valent  – ditransitive, subject + object + indirect object\n" +
                        "  [4] Unknown   – cannot determine");
        PROPERTY_WORD_KEY.put("valence", "verb");

        addCategoricalOptions("conjugation_regularity");
        PROPERTY_INSTRUCTIONS.put("conjugation_regularity",
                "Is this verb regular or irregular in its conjugation?\n" +
                        "  Regular    – follows standard -ed / -ing patterns\n" +
                        "  Irregular  – has non-standard past/participle forms\n" +
                        "  Unknown    – cannot determine");
        PROPERTY_WORD_KEY.put("conjugation_regularity", "verb");

        addCategoricalOptions("adjective_category");
        PROPERTY_INSTRUCTIONS.put("adjective_category",
                "What semantic category does this adjective belong to?\n" +
                        "  Descriptive / Qualitative  – inherent qualities or states\n" +
                        "  Evaluative                 – judgment, value, or attitude\n" +
                        "  Quantitative / Indefinite  – vague amount or extent\n" +
                        "  Numeral                    – exact number or order\n" +
                        "  Demonstrative (Deictic)    – points to specific entities\n" +
                        "  Possessive                 – ownership or association\n" +
                        "  Interrogative              – used in questions\n" +
                        "  Distributive               – refer to members individually\n" +
                        "  Proper / Nominal           – derived from proper nouns\n" +
                        "  Other                      – none of the above\n" +
                        "  Unknown                    – cannot determine");
        PROPERTY_WORD_KEY.put("adjective_category", "adjective");

        addCategoricalOptions("adverb_category");
        PROPERTY_INSTRUCTIONS.put("adverb_category",
                "What semantic category does this adverb belong to?\n" +
                        "  Manner                                    – describes how an action is performed (quickly, carefully)\n" +
                        "  Place / Location                          – indicates where an action occurs (here, abroad)\n" +
                        "  Direction / Path                          – expresses movement or trajectory (away, homeward)\n" +
                        "  Time                                      – situates an event in time (now, tomorrow)\n" +
                        "  Duration                                  – shows how long something lasts (briefly, forever)\n" +
                        "  Frequency                                 – shows how often something occurs (always, rarely)\n" +
                        "  Sequence                                  – orders events (first, then, finally)\n" +
                        "  Degree / Intensifier                      – scales intensity (very, extremely)\n" +
                        "  Approximator / Scalar                     – conveys near limits (almost, roughly)\n" +
                        "  Measure / Multiplier                      – indicates proportional extent (twice, threefold)\n" +
                        "  Epistemic                                 – conveys certainty or likelihood (probably, surely)\n" +
                        "  Evidential                                – signals source of information (apparently, reportedly)\n" +
                        "  Attitudinal / Evaluative                  – expresses speaker stance (fortunately, sadly)\n" +
                        "  Style / Domain                            – limits the statement to a perspective (technically, legally)\n" +
                        "  Focus (additive / restrictive / emphatic) – highlights or limits focus (only, even)\n" +
                        "  Negation / Polarity                       – expresses denial (not, never)\n" +
                        "  Affirmative                               – reinforces truth (yes, indeed)\n" +
                        "  Connective / Linking                      – joins clauses (however, therefore)\n" +
                        "  Topic-management / Discourse              – manages discourse flow (well, anyway)\n" +
                        "  Interrogative                             – introduces a question (how, when)\n" +
                        "  Relative                                  – introduces a subordinate clause (when, where, wherever)\n" +
                        "  Unknown                                   – cannot determine");
        PROPERTY_WORD_KEY.put("adverb_category", "adverb");

        PROPERTY_OPTIONS.put(GenerativeEvalUtils.PLURAL_AUDIT_MODE, GENERATIVE_OPTIONS);
        PROPERTY_INSTRUCTIONS.put(GenerativeEvalUtils.PLURAL_AUDIT_MODE,
                "Audit the reference model's generated plural.\n" +
                        "  Correct    – the plural generation is morphologically correct\n" +
                        "  Incorrect  – the generation is not correct");
        PROPERTY_WORD_KEY.put(GenerativeEvalUtils.PLURAL_AUDIT_MODE, "noun");

        PROPERTY_OPTIONS.put(GenerativeEvalUtils.CONJUGATION_AUDIT_MODE, GENERATIVE_OPTIONS);
        PROPERTY_INSTRUCTIONS.put(GenerativeEvalUtils.CONJUGATION_AUDIT_MODE,
                "Audit the reference model's generated principal parts.\n" +
                        "  Correct    – the displayed principal parts are morphologically correct\n" +
                        "  Incorrect  – one or more displayed forms are not correct");
        PROPERTY_WORD_KEY.put(GenerativeEvalUtils.CONJUGATION_AUDIT_MODE, "verb");
    }

    private static class Item {
        String itemId;
        String synsetId;
        String lemma;
        String word;
        String definition;
        String sampleStratum;
        String referenceModel;
        JSONObject referenceOutput;
        List<String> detailLines = new ArrayList<>();
    }

    private static class UndoEntry {
        String itemId;
        String synsetId;
        String lemma;
        String word;
        int listIndex;

        UndoEntry(String itemId, String synsetId, String lemma, String word, int listIndex) {
            this.itemId = itemId;
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.word = word;
            this.listIndex = listIndex;
        }
    }

    private static class PosItemPool {
        final List<Item> items;
        final int removedCount;

        PosItemPool(List<Item> items, int removedCount) {
            this.items = items;
            this.removedCount = removedCount;
        }
    }

    private static class MenuContext {
        final Map<String, List<Item>> itemsByMode = new LinkedHashMap<>();
        final Map<String, Integer> removedCountByPos = new LinkedHashMap<>();
        final Map<String, String> loadErrorByMode = new LinkedHashMap<>();
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> cli = parseArgs(args);
        if (cli.containsKey("help") || cli.containsKey("h")) {
            printUsage();
            return;
        }

        int maxPerSession = parseInt(cli.get("max"), -1);
        long seed = parseLong(cli.get("seed"), 42L);
        boolean autoResume = !cli.containsKey("no-resume");
        Path goldDir = Paths.get(DEFAULT_GOLD_DIR_NAME);
        Files.createDirectories(goldDir);

        System.out.println(section("Initializing WordNet") + " " + meta("(requires SIGMA_HOME)..."));
        initWordNet();

        MenuContext menuContext = buildMenuContext(seed);
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        String mode = selectMode(console, goldDir, menuContext);
        if (mode == null) {
            return;
        }

        boolean generativeMode = isGenerativeAuditMode(mode);
        Path outputPath = goldDir.resolve(mode + ".jsonl");

        if (!autoResume) {
            backupAndDeleteIfExists(outputPath);
        }

        List<Item> items = menuContext.itemsByMode.get(mode);
        if (items == null) {
            System.err.println("No annotation items available for mode: " + mode);
            return;
        }

        Set<String> completedIds = Files.exists(outputPath) ? loadAnnotatedIds(outputPath) : new LinkedHashSet<>();
        int previousCount = completedIds.size();
        int remaining = 0;
        for (Item item : items) {
            if (!completedIds.contains(item.itemId)) {
                remaining++;
            }
        }

        String[] options = PROPERTY_OPTIONS.get(mode);
        String instructions = PROPERTY_INSTRUCTIONS.get(mode);
        boolean isValence = "valence".equals(mode);

        printHeader(mode, outputPath, items.size(), previousCount, remaining, maxPerSession, instructions, generativeMode, options.length, isValence);

        if (remaining == 0) {
            System.out.println(success("All items in this set are already annotated."));
            return;
        }

        List<UndoEntry> undoStack = new ArrayList<>();
        int annotatedThisSession = 0;
        int skippedThisSession = 0;
        long sessionStart = System.currentTimeMillis();

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outputPath.toFile(), true)))) {
            int displayNum = previousCount;

            for (int idx = 0; idx < items.size(); idx++) {
                Item item = items.get(idx);
                if (completedIds.contains(item.itemId)) {
                    continue;
                }

                if (maxPerSession > 0 && annotatedThisSession > 0 && annotatedThisSession % maxPerSession == 0) {
                    System.out.println();
                    System.out.println(highlight("  You have annotated " + annotatedThisSession + " items this session."));
                    System.out.print("  " + accent("Continue annotating? [y/n] > "));
                    String answer = console.readLine();
                    if (answer == null || answer.trim().toLowerCase(Locale.ROOT).startsWith("n")) {
                        printSessionSummary(annotatedThisSession, skippedThisSession, previousCount, outputPath.toString(), sessionStart);
                        return;
                    }
                    System.out.println();
                }

                displayNum++;
                displayItem(item, mode, instructions, options, isValence, annotatedThisSession, displayNum);

                String input = console.readLine();
                if (input == null) {
                    break;
                }
                input = input.trim().toLowerCase(Locale.ROOT);

                if ("q".equals(input)) {
                    printSessionSummary(annotatedThisSession, skippedThisSession, previousCount, outputPath.toString(), sessionStart);
                    return;
                }
                if ("s".equals(input)) {
                    skippedThisSession++;
                    System.out.println("  → " + meta("skipped") + "\n");
                    continue;
                }
                if ("?".equals(input)) {
                    printLiveStats(annotatedThisSession, skippedThisSession, previousCount, remaining, sessionStart);
                    idx--;
                    displayNum--;
                    continue;
                }
                if ("r".equals(input)) {
                    System.out.println("\n" + instructions + "\n");
                    idx--;
                    displayNum--;
                    continue;
                }
                if ("u".equals(input)) {
                    if (undoStack.isEmpty()) {
                        System.out.println("  → " + highlight("nothing to undo") + "\n");
                        idx--;
                        displayNum--;
                        continue;
                    }
                    UndoEntry last = undoStack.remove(undoStack.size() - 1);
                    annotatedThisSession--;
                    displayNum--;
                    completedIds.remove(last.itemId);
                    JSONObject undoMarker = new JSONObject();
                    undoMarker.put("audit_id", last.itemId);
                    undoMarker.put("synsetId", last.synsetId);
                    undoMarker.put("lemma", last.lemma);
                    undoMarker.put("human_label", "__UNDONE__");
                    undoMarker.put("timestamp", System.currentTimeMillis());
                    out.println(undoMarker.toString());
                    out.flush();
                    System.out.println("  → " + highlight("undid annotation for ") + accent("\"" + last.word + "\"") + "\n");
                    idx = last.listIndex - 1;
                    continue;
                }

                String label = generativeMode
                        ? parseGenerativeChoice(input)
                        : parseCategoricalChoice(input, options, isValence);

                if (label == null) {
                    System.out.println("  → " + error("invalid input") + "\n");
                    idx--;
                    displayNum--;
                    continue;
                }

                JSONObject annotation = buildAnnotation(mode, item, label);
                out.println(annotation.toString());
                out.flush();

                completedIds.add(item.itemId);
                undoStack.add(new UndoEntry(item.itemId, item.synsetId, item.lemma, item.word, idx));
                annotatedThisSession++;
                System.out.println("  → " + success(label) + "\n");
            }
        }

        printSessionSummary(annotatedThisSession, skippedThisSession, previousCount, outputPath.toString(), sessionStart);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                result.put(key, args[++i]);
            } else {
                result.put(key, "true");
            }
        }
        return result;
    }

    private static boolean isGenerativeAuditMode(String mode) {
        return GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(mode)
                || GenerativeEvalUtils.CONJUGATION_AUDIT_MODE.equals(mode);
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

    private static String styleProgressPercent(int percent) {
        if (percent >= 75) {
            return success(percent + "%");
        }
        if (percent >= 25) {
            return highlight(percent + "%");
        }
        return error(percent + "%");
    }

    private static void initWordNet() {
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
    }

    private static void backupAndDeleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        String backup = path.toString() + ".bak." + System.currentTimeMillis();
        Files.copy(path, Paths.get(backup));
        Files.delete(path);
        System.out.println(success("Backed up ") + meta(path.toString()) + success(" to ") + meta(backup));
    }

    private static MenuContext buildMenuContext(long seed) {
        MenuContext context = new MenuContext();

        Map<String, PosItemPool> pools = new LinkedHashMap<>();
        for (String pos : Arrays.asList("noun", "verb", "adjective", "adverb")) {
            PosItemPool pool = loadItemsFromWordNetPool(pos, seed);
            pools.put(pos, pool);
            context.removedCountByPos.put(pos, pool.removedCount);
        }

        Path referenceDb = GenMorphoUtils.expandHomePath(DEFAULT_REFERENCE_DB);
        String referenceModel = referenceDb.getFileName() == null ? DEFAULT_REFERENCE_DB : referenceDb.getFileName().toString();

        for (String mode : PROPERTY_OPTIONS.keySet()) {
            if (isGenerativeAuditMode(mode)) {
                try {
                    context.itemsByMode.put(mode, loadGenerativeAuditQueue(mode, referenceDb, referenceModel, seed));
                } catch (IOException e) {
                    context.itemsByMode.put(mode, Collections.emptyList());
                    context.loadErrorByMode.put(mode, e.getMessage());
                }
            } else {
                String pos = PROPERTY_WORD_KEY.get(mode);
                PosItemPool pool = pools.get(pos);
                context.itemsByMode.put(mode, pool == null ? Collections.emptyList() : pool.items);
            }
        }

        return context;
    }

    private static String selectMode(BufferedReader console, Path goldDir, MenuContext context) throws IOException {
        List<String> modes = new ArrayList<>(PROPERTY_OPTIONS.keySet());

        while (true) {
            printSelectionMenu(goldDir, modes, context);
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

            if (choice < 1 || choice > modes.size()) {
                System.out.println("  → " + error("invalid input") + "\n");
                continue;
            }

            String mode = modes.get(choice - 1);
            String loadError = context.loadErrorByMode.get(mode);
            if (loadError != null) {
                System.out.println("  → " + error(loadError) + "\n");
                continue;
            }

            return mode;
        }
    }

    private static void printSelectionMenu(Path goldDir, List<String> modes, MenuContext context) throws IOException {
        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + CYAN + "Morphological Gold-Standard Annotation Tool" + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(section("Select a property to annotate:"));
        System.out.println();
        System.out.println(meta("Non-English words removed from noun set: ") + highlight(String.valueOf(context.removedCountByPos.getOrDefault("noun", 0))));
        System.out.println(meta("Non-English words removed from verb set: ") + highlight(String.valueOf(context.removedCountByPos.getOrDefault("verb", 0))));
        System.out.println(meta("Non-English words removed from adjective set: ") + highlight(String.valueOf(context.removedCountByPos.getOrDefault("adjective", 0))));
        System.out.println(meta("Non-English words removed from adverb set: ") + highlight(String.valueOf(context.removedCountByPos.getOrDefault("adverb", 0))));

        for (int i = 0; i < modes.size(); i++) {
            String mode = modes.get(i);
            int total = context.itemsByMode.getOrDefault(mode, Collections.emptyList()).size();
            int completed = countCompletedIds(goldDir.resolve(mode + ".jsonl"));
            int percent = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
            System.out.printf(Locale.ROOT, "  %s)  %s %s / %d  (%s)%n",
                    highlight(String.format(Locale.ROOT, "%4d", i + 1)),
                    accent(String.format(Locale.ROOT, "%-28s", mode)),
                    success(String.format(Locale.ROOT, "%6d", completed)),
                    total,
                    styleProgressPercent(percent));
        }

        System.out.println();
        System.out.println("     " + highlight("q") + ")  " + meta("quit"));
        System.out.println();
        System.out.print("  " + accent("Select [1-" + modes.size() + "] > "));
        System.out.flush();
    }

    private static int countCompletedIds(Path path) throws IOException {
        return Files.exists(path) ? loadAnnotatedIds(path).size() : 0;
    }

    private static PosItemPool loadItemsFromWordNetPool(String pos, long seed) {
        Map<String, Set<String>> rawSynsetHash;
        Map<String, String> documentationHash;
        switch (pos) {
            case "noun":
                rawSynsetHash = WordNet.wn.nounSynsetHash;
                documentationHash = WordNet.wn.nounDocumentationHash;
                break;
            case "verb":
                rawSynsetHash = WordNet.wn.verbSynsetHash;
                documentationHash = WordNet.wn.verbDocumentationHash;
                break;
            case "adjective":
                rawSynsetHash = WordNet.wn.adjectiveSynsetHash;
                documentationHash = WordNet.wn.adjectiveDocumentationHash;
                break;
            case "adverb":
                rawSynsetHash = WordNet.wn.adverbSynsetHash;
                documentationHash = WordNet.wn.adverbDocumentationHash;
                break;
            default:
                return new PosItemPool(new ArrayList<>(), 0);
        }

        Map<String, Set<String>> synsetHash = new TreeMap<>(rawSynsetHash);
        int removedCount = removeNonEnglishWordsQuietly(synsetHash);

        List<Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : synsetHash.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String term = entry.getKey().replace('_', ' ');
            String lemma = GenMorphoUtils.normalizeLemma(term);
            if (lemma.isEmpty() || seen.contains(lemma)) {
                continue;
            }
            seen.add(lemma);
            String synsetId = Collections.min(entry.getValue());
            Item item = new Item();
            item.itemId = lemma;
            item.synsetId = synsetId;
            item.lemma = lemma;
            item.word = term;
            item.definition = documentationHash.getOrDefault(synsetId, "(no definition)").replaceAll("^\"|\"$", "");
            items.add(item);
        }

        Collections.shuffle(items, new Random(seed + 31L * pos.hashCode()));
        return new PosItemPool(items, removedCount);
    }

    private static int removeNonEnglishWordsQuietly(Map<String, Set<String>> wordMap) {
        int removed = 0;
        List<String> keysToRemove = new ArrayList<>();
        for (String key : wordMap.keySet()) {
            if (key.length() < 2 || !key.matches("^[a-zA-Z_.\\'-]+$")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            if (wordMap.remove(key) != null) {
                removed++;
            }
        }
        return removed;
    }

    private static List<Item> loadGenerativeAuditQueue(String mode,
                                                       Path referenceDb,
                                                       String referenceModel,
                                                       long seed) throws IOException {
        if (!Files.isDirectory(referenceDb)) {
            throw new IOException("Reference model directory not found: " + referenceDb);
        }
        Map<String, Item> candidateById = loadGenerativeCandidateMap(mode, referenceDb, referenceModel);
        List<Item> items = new ArrayList<>(candidateById.values());
        items.sort(Comparator.comparing(item -> item.itemId));
        Collections.shuffle(items, new Random(seed + 31L * mode.hashCode()));
        return items;
    }

    private static Map<String, Item> loadGenerativeCandidateMap(String mode,
                                                                Path referenceDb,
                                                                String referenceModel) throws IOException {
        String relativeFile = GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(mode)
                ? "noun/Plurals.txt"
                : "verb/VerbConjugations.txt";
        Path inputFile = referenceDb.resolve(relativeFile);
        if (!Files.exists(inputFile)) {
            throw new IOException("Reference file not found: " + inputFile);
        }

        MorphoDBLoader.JsonRecordLoadResult loadResult = MorphoDBLoader.loadJsonRecords(inputFile.toString());
        Map<String, Item> items = new LinkedHashMap<>();

        for (JSONObject json : loadResult.records) {
            Item item = GenerativeEvalUtils.PLURAL_AUDIT_MODE.equals(mode)
                    ? buildPluralAuditItem(json, referenceModel)
                    : buildConjugationAuditItem(json, referenceModel);
            if (item != null) {
                items.put(item.itemId, item);
            }
        }

        return items;
    }

    private static Item buildPluralAuditItem(JSONObject json, String referenceModel) {
        GenerativeEvalUtils.PluralRecord record = GenerativeEvalUtils.extractPluralRecord(json);
        if (record == null) {
            return null;
        }

        Item item = new Item();
        item.itemId = record.auditId;
        item.synsetId = record.synsetId;
        item.lemma = record.lemma;
        item.word = record.singularRaw.isEmpty() ? record.lemma : record.singularRaw;
        item.definition = lookupDefinition("noun", record.synsetId);
        item.sampleStratum = record.sampleStratum;
        item.referenceModel = referenceModel;
        item.referenceOutput = GenerativeEvalUtils.toReferenceOutput(record);
        item.detailLines.add("Singular:          " + record.singularNormalized);
        item.detailLines.add("Plural:            " + record.pluralNormalized);
        return item;
    }

    private static Item buildConjugationAuditItem(JSONObject json, String referenceModel) {
        GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
        if (record == null) {
            return null;
        }

        Item item = new Item();
        item.itemId = record.auditId;
        item.synsetId = record.synsetId;
        item.lemma = record.lemma;
        item.word = json.optString("verb", record.normalizedForms.get(GenerativeEvalUtils.SLOT_INFINITIVE));
        item.definition = lookupDefinition("verb", record.synsetId);
        item.sampleStratum = record.sampleStratum;
        item.referenceModel = referenceModel;
        item.referenceOutput = GenerativeEvalUtils.toReferenceOutput(record);
        item.detailLines.add("Regularity:      " + record.regularity);
        item.detailLines.add("Infinitive:      " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_INFINITIVE));
        item.detailLines.add("3sg present:     " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_PRESENT_3SG));
        item.detailLines.add("Simple past:     " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_SIMPLE_PAST));
        item.detailLines.add("Past participle: " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE));
        item.detailLines.add("Gerund:          " + record.normalizedForms.get(GenerativeEvalUtils.SLOT_GERUND));
        return item;
    }


    private static JSONObject buildAnnotation(String mode, Item item, String label) {
        JSONObject annotation = new JSONObject();
        annotation.put("audit_id", item.itemId);
        annotation.put("synsetId", item.synsetId);
        annotation.put("lemma", item.lemma);
        annotation.put("word", item.word);
        annotation.put("definition", item.definition);
        annotation.put("property", mode);
        annotation.put("human_label", label);
        annotation.put("timestamp", System.currentTimeMillis());

        if (isGenerativeAuditMode(mode)) {
            annotation.put("reference_model", item.referenceModel);
            annotation.put("sample_stratum", item.sampleStratum);
            annotation.put("reference_output", item.referenceOutput);
        }

        return annotation;
    }

    private static void displayItem(Item item,
                                    String mode,
                                    String instructions,
                                    String[] options,
                                    boolean isValence,
                                    int annotatedThisSession,
                                    int displayNum) {
        System.out.println(instructions);
        System.out.println();
        System.out.println(BOLD + "[" + displayNum + " | " + annotatedThisSession + " this session]  "
                + CYAN + item.word + RESET + "  " + DIM + "(synset " + item.synsetId + ")" + RESET);
        System.out.println("  " + meta(item.definition));

        if (isGenerativeAuditMode(mode)) {
            System.out.println("  " + meta("Reference model: ") + accent(item.referenceModel)
                    + meta(" | stratum: ") + highlight(item.sampleStratum));
            for (String line : item.detailLines) {
                System.out.println("  " + accent(line));
            }
        } else if (isValence) {
            String frameLines = getVerbFrameLines(item);
            if (frameLines != null) {
                System.out.println(meta(frameLines));
            }
        }

        System.out.println();
        if (isGenerativeAuditMode(mode)) {
            System.out.println("    " + YELLOW + "y" + RESET + ") Correct");
            System.out.println("    " + YELLOW + "n" + RESET + ") Incorrect");
            System.out.println();
            System.out.print("  " + accent("[y/n/s/u/q/?/r] > "));
        } else {
            for (int i = 0; i < options.length; i++) {
                int displayKey = isValence ? i : (i + 1);
                System.out.println("    " + YELLOW + displayKey + RESET + ") " + accent(options[i]));
            }
            System.out.println();
            String range = isValence ? "0-" + (options.length - 1) : "1-" + options.length;
            System.out.print("  " + accent("[" + range + "/s/u/q/?/r] > "));
        }
        System.out.flush();
    }

    private static String parseGenerativeChoice(String input) {
        if ("y".equals(input)) {
            return "Correct";
        }
        if ("n".equals(input)) {
            return "Incorrect";
        }
        return null;
    }

    private static String parseCategoricalChoice(String input, String[] options, boolean isValence) {
        int choice;
        try {
            choice = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return null;
        }

        if (isValence) {
            if (choice < 0 || choice >= options.length) {
                return null;
            }
            return options[choice];
        }

        if (choice < 1 || choice > options.length) {
            return null;
        }
        return options[choice - 1];
    }

    private static void printHeader(String mode,
                                    Path outputPath,
                                    int totalItems,
                                    int previousCount,
                                    int remaining,
                                    int maxPerSession,
                                    String instructions,
                                    boolean generativeMode,
                                    int optionCount,
                                    boolean isValence) {
        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + CYAN + "Morphological Gold-Standard Annotation Tool" + RESET);
        System.out.println(BOLD + "  " + meta("Mode:         ") + accent(mode) + RESET);
        System.out.println(BOLD + "  " + meta("Total items:  ") + success(String.valueOf(totalItems)) + RESET);
        System.out.println(BOLD + "  " + meta("Already done: ") + success(String.valueOf(previousCount)) + RESET);
        System.out.println(BOLD + "  " + meta("Remaining:    ") + highlight(String.valueOf(remaining)) + RESET);
        if (maxPerSession > 0) {
            System.out.println(BOLD + "  " + meta("Session goal: ") + highlight(String.valueOf(maxPerSession)) + RESET);
        }
        System.out.println(BOLD + "  " + meta("Output file:  ") + meta(outputPath.toString()) + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(section("Instructions"));
        System.out.println(instructions);
        System.out.println();
        if (generativeMode) {
            System.out.println(section("Controls") + "  " + highlight("[y]") + " correct  |  "
                    + highlight("[n]") + " incorrect  |  " + highlight("[s]") + " skip  |  "
                    + highlight("[u]") + " undo  |  " + highlight("[q]") + " quit");
        } else {
            String range = isValence ? "0-" + (optionCount - 1) : "1-" + optionCount;
            System.out.println(section("Controls") + "  " + highlight("[" + range + "]") + " select  |  "
                    + highlight("[s]") + " skip  |  " + highlight("[u]") + " undo  |  "
                    + highlight("[q]") + " quit");
        }
        System.out.println("           " + highlight("[?]") + " show stats   |  " + highlight("[r]") + " re-show instructions");
        System.out.println(meta("───────────────────────────────────────────────────────────"));
        System.out.println();
    }

    private static List<Item> loadItemsFromWordNet(String pos) {
        Map<String, Set<String>> rawSynsetHash;
        Map<String, String> documentationHash;
        switch (pos) {
            case "noun":
                rawSynsetHash = WordNet.wn.nounSynsetHash;
                documentationHash = WordNet.wn.nounDocumentationHash;
                break;
            case "verb":
                rawSynsetHash = WordNet.wn.verbSynsetHash;
                documentationHash = WordNet.wn.verbDocumentationHash;
                break;
            case "adjective":
                rawSynsetHash = WordNet.wn.adjectiveSynsetHash;
                documentationHash = WordNet.wn.adjectiveDocumentationHash;
                break;
            case "adverb":
                rawSynsetHash = WordNet.wn.adverbSynsetHash;
                documentationHash = WordNet.wn.adverbDocumentationHash;
                break;
            default:
                return new ArrayList<>();
        }

        Map<String, Set<String>> synsetHash = new TreeMap<>(rawSynsetHash);
        GenMorphoUtils.removeNonEnglishWords(synsetHash, pos + " set");

        List<Item> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : synsetHash.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String term = entry.getKey().replace('_', ' ');
            String lemma = GenMorphoUtils.normalizeLemma(term);
            if (lemma.isEmpty() || seen.contains(lemma)) {
                continue;
            }
            seen.add(lemma);
            String synsetId = Collections.min(entry.getValue());
            Item item = new Item();
            item.itemId = lemma;
            item.synsetId = synsetId;
            item.lemma = lemma;
            item.word = term;
            item.definition = documentationHash.getOrDefault(synsetId, "(no definition)").replaceAll("^\"|\"$", "");
            items.add(item);
        }
        return items;
    }

    private static String lookupDefinition(String pos, String synsetId) {
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
        return definition.replaceAll("^\"|\"$", "");
    }

    private static String getVerbFrameLines(Item item) {
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

    private static Set<String> loadAnnotatedIds(Path path) throws IOException {
        Map<String, String> lastLabelById = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject obj = new JSONObject(trimmed);
                    String id = obj.optString("audit_id", "");
                    if (id.isEmpty()) {
                        String lemma = obj.optString("lemma", "");
                        id = lemma.isEmpty()
                                ? GenMorphoUtils.normalizeLemma(obj.optString("word", ""))
                                : lemma;
                    }
                    if (id.isEmpty()) {
                        continue;
                    }
                    lastLabelById.put(id, obj.optString("human_label", ""));
                } catch (Exception ignored) {
                }
            }
        }

        Set<String> completed = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : lastLabelById.entrySet()) {
            if (!"__UNDONE__".equals(entry.getValue())) {
                completed.add(entry.getKey());
            }
        }
        return completed;
    }

    private static void printLiveStats(int thisSession,
                                       int skipped,
                                       int previous,
                                       int totalRemaining,
                                       long sessionStart) {
        long elapsed = System.currentTimeMillis() - sessionStart;
        double minutes = elapsed / 60000.0;
        double minutesPerItem = thisSession > 0 ? minutes / thisSession : 0.0;
        int grandTotal = previous + thisSession;

        System.out.println();
        System.out.println(section("  ┌─── Session Stats ────────────────────────┐"));
        System.out.printf(Locale.ROOT, "  │  %s %s annotated, %s skipped%n",
                meta("This session:  "),
                success(String.format(Locale.ROOT, "%5d", thisSession)),
                highlight(String.valueOf(skipped)));
        System.out.printf(Locale.ROOT, "  │  %s %s annotated%n",
                meta("All-time total:"),
                success(String.format(Locale.ROOT, "%5d", grandTotal)));
        System.out.printf(Locale.ROOT, "  │  %s %s%n",
                meta("Remaining:     "),
                highlight(String.format(Locale.ROOT, "%5d", totalRemaining - thisSession)));
        if (thisSession > 0) {
            System.out.printf(Locale.ROOT, "  │  %s %s%n",
                    meta("Pace:          "),
                    accent(String.format(Locale.ROOT, "%.1f sec / item", minutesPerItem * 60.0)));
        }
        System.out.println(section("  └──────────────────────────────────────────┘"));
        System.out.println();
    }

    private static void printSessionSummary(int thisSession,
                                            int skipped,
                                            int previous,
                                            String outPath,
                                            long sessionStart) {
        long elapsed = System.currentTimeMillis() - sessionStart;
        double minutes = elapsed / 60000.0;
        int grandTotal = previous + thisSession;

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  " + GREEN + "Session complete." + RESET);
        System.out.printf(Locale.ROOT, "  %s %s annotated, %s skipped (%s)%n",
                meta("This session:  "),
                success(String.valueOf(thisSession)),
                highlight(String.valueOf(skipped)),
                accent(String.format(Locale.ROOT, "%.1f min", minutes)));
        System.out.printf(Locale.ROOT, "  %s %s annotated%n",
                meta("All-time total:"),
                success(String.valueOf(grandTotal)));
        if (thisSession > 0) {
            System.out.printf(Locale.ROOT, "  %s %s%n",
                    meta("Pace:          "),
                    accent(String.format(Locale.ROOT, "%.1f sec / item", (elapsed / 1000.0) / thisSession)));
        }
        System.out.println("  " + meta("Output file:   ") + meta(outPath));
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(raw.trim());
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return Long.parseLong(raw.trim());
    }

    private static void printUsage() {
        System.out.println(BOLD + CYAN + "MorphoDB Human Annotation Tool" + RESET);
        System.out.println(section("Usage"));
        System.out.println("  " + accent("java ... HumanAnnotationTool [options]"));
        System.out.println();
        System.out.println(section("Behavior"));
        System.out.println("  - " + meta("Writes gold JSONL files to ") + accent("./gold") + meta(" relative to the current working directory"));
        System.out.println("  - " + meta("Prompts for the property from an interactive menu"));
        System.out.println("  - " + meta("Generative audits always use ") + accent(DEFAULT_REFERENCE_DB));
        System.out.println();
        System.out.println(section("General options"));
        System.out.println("  " + highlight("--max <N>") + "              " + meta("Ask whether to continue after N annotations"));
        System.out.println("  " + highlight("--seed <S>") + "             " + meta("Random seed (default: 42)"));
        System.out.println("  " + highlight("--no-resume") + "            " + meta("Back up any existing audit/gold file and start fresh"));
    }
}
