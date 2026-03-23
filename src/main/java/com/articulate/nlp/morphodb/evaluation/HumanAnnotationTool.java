package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/***************************************************************
 * Interactive console tool for building a human gold-standard
 * annotation set.  The annotator is shown a word, its WordNet
 * definition, and a numbered list of classification options
 * (mirroring what the LLM was asked).  The annotator presses a
 * single digit to record the classification, or one of the
 * control keys listed on-screen (skip, quit, undo).
 *
 * Output is written incrementally to a JSONL file so that work
 * is never lost, even if the session is interrupted.
 *
 * RESUME IS AUTOMATIC — if the output file already exists, items
 * already annotated are skipped.  You can stop at any time with
 * [q] and pick up exactly where you left off next time.
 *
 * Usage:
 *   java ... HumanAnnotationTool <morphodb_dir> [--max N] [--seed S] [--no-resume]
 *
 * If no property is specified on the command line, the tool shows
 * an interactive menu listing all properties with their current
 * annotation counts so the annotator can pick one.
 *
 * Items and definitions are drawn directly from WordNet (same filtered set
 * used by GenMorphoDB at generation time). SIGMA_HOME must be set.
 * Gold annotations are written to <morphodb_dir>/gold/<property>.jsonl.
 *
 * --max N       after annotating N items this session, ask whether
 *               to continue or stop (default: no limit)
 * --seed S      random seed for reproducibility (default: 42)
 * --no-resume   do NOT auto-resume; start fresh (overwrites output)
 ***************************************************************/
public class HumanAnnotationTool {

    // property definitions
    /** Each property has an ordered list of valid labels. */
    private static final Map<String, String[]> PROPERTY_OPTIONS = new LinkedHashMap<>();
    /** Instructions shown to the annotator (mirrors the LLM prompt). */
    private static final Map<String, String> PROPERTY_INSTRUCTIONS = new LinkedHashMap<>();
    /** The WordNet POS for each property: noun, verb, adjective, or adverb. */
    private static final Map<String, String> PROPERTY_WORD_KEY = new LinkedHashMap<>();

    static {
        // Noun: Countability
        PROPERTY_OPTIONS.put("countability", new String[]{
                "Count noun", "Mass noun", "Proper noun",
                "Count and mass noun", "Unknown"
        });
        PROPERTY_INSTRUCTIONS.put("countability",
                "Classify this noun's countability.\n" +
                "  Count noun  – can be pluralised (a dog / dogs)\n" +
                "  Mass noun   – uncountable substance/concept (water, information)\n" +
                "  Proper noun – name of a unique entity (London, Einstein)\n" +
                "  Count and mass – both uses exist (chicken / some chicken)\n" +
                "  Unknown     – cannot determine");
        PROPERTY_WORD_KEY.put("countability", "noun");

        // Noun: Humanness
        PROPERTY_OPTIONS.put("humanness", new String[]{
                "Human", "Non-human", "Human and non-human"
        });
        PROPERTY_INSTRUCTIONS.put("humanness",
                "Does this noun refer to a human being?\n" +
                "  Human              – always refers to people (doctor, child)\n" +
                "  Non-human          – never refers to people (rock, algorithm)\n" +
                "  Human and non-human – can refer to either (member, agent)");
        PROPERTY_WORD_KEY.put("humanness", "noun");

        // Noun: Agentivity
        PROPERTY_OPTIONS.put("agentivity", new String[]{
                "Agentive", "Non-agentive"
        });
        PROPERTY_INSTRUCTIONS.put("agentivity",
                "Can the referent of this noun perform intentional actions?\n" +
                "  Agentive     – can act intentionally (teacher, robot, committee)\n" +
                "  Non-agentive – cannot act intentionally (table, idea, weather)");
        PROPERTY_WORD_KEY.put("agentivity", "noun");

        // Noun: Collective
        PROPERTY_OPTIONS.put("collective", new String[]{
                "Collective", "Not collective"
        });
        PROPERTY_INSTRUCTIONS.put("collective",
                "Is this noun a collective noun (denoting a group)?\n" +
                "  Collective     – refers to a group acting as one (team, fleet, committee)\n" +
                "  Not collective – refers to a single entity or concept");
        PROPERTY_WORD_KEY.put("collective", "noun");

        // Noun: Indefinite Article
        PROPERTY_OPTIONS.put("indefinite_article", new String[]{
                "a", "an", "none"
        });
        PROPERTY_INSTRUCTIONS.put("indefinite_article",
                "What indefinite article precedes this noun?\n" +
                "  a    – consonant sound  (a dog, a university)\n" +
                "  an   – vowel sound      (an apple, an hour)\n" +
                "  none – proper noun / mass noun that doesn't take an article");
        PROPERTY_WORD_KEY.put("indefinite_article", "noun");

        // Verb: Causativity
        PROPERTY_OPTIONS.put("causativity", new String[]{
                "Causative", "Non-causative", "Mixed", "Unknown"
        });
        PROPERTY_INSTRUCTIONS.put("causativity",
                "Is this verb causative?\n" +
                "  Causative     – subject causes a change in the object (break, kill, melt)\n" +
                "  Non-causative – no patient undergoes a caused change (sleep, arrive, exist)\n" +
                "  Mixed         – both causative and non-causative uses (open, roll)\n" +
                "  Unknown       – cannot determine");
        PROPERTY_WORD_KEY.put("causativity", "verb");

        // Verb: Reflexivity
        PROPERTY_OPTIONS.put("reflexivity", new String[]{
                "Always reflexive", "Can be reflexive", "Never reflexive"
        });
        PROPERTY_INSTRUCTIONS.put("reflexivity",
                "Can this verb be used reflexively (subject = object)?\n" +
                "  Always reflexive – must take a reflexive pronoun (perjure oneself)\n" +
                "  Can be reflexive – optionally reflexive (wash, dress, introduce)\n" +
                "  Never reflexive  – reflexive form is ungrammatical (sleep, arrive)");
        PROPERTY_WORD_KEY.put("reflexivity", "verb");

        // Verb: Reciprocity
        PROPERTY_OPTIONS.put("reciprocity", new String[]{
                "Always reciprocal", "Can be reciprocal", "Never reciprocal"
        });
        PROPERTY_INSTRUCTIONS.put("reciprocity",
                "Can this verb express a reciprocal action (A does X to B and B does X to A)?\n" +
                "  Always reciprocal – requires mutual action (intermarry)\n" +
                "  Can be reciprocal – optionally reciprocal (fight, kiss, meet)\n" +
                "  Never reciprocal  – no reciprocal reading (sleep, create)");
        PROPERTY_WORD_KEY.put("reciprocity", "verb");

        // Verb: Aktionsart (Achievement / Process)
        PROPERTY_OPTIONS.put("aktionsart", new String[]{
                "Achievement", "Process", "Mixed"
        });
        PROPERTY_INSTRUCTIONS.put("aktionsart",
                "Is this verb an achievement or a process?\n" +
                "  Achievement – punctual change of state (arrive, die, find)\n" +
                "  Process     – activity with duration (run, read, swim)\n" +
                "  Mixed       – can be either depending on context");
        PROPERTY_WORD_KEY.put("aktionsart", "verb");

        // Verb: Valence
        PROPERTY_OPTIONS.put("valence", new String[]{
                "0-valent", "1-valent", "2-valent", "3-valent", "Unknown"
        });
        PROPERTY_INSTRUCTIONS.put("valence",
                "How many core arguments does this verb take?\n" +
                "  0-valent – impersonal, no true subject (rain, snow)\n" +
                "  1-valent – intransitive, subject only (sleep, arrive)\n" +
                "  2-valent – transitive, subject + object (eat, read)\n" +
                "  3-valent – ditransitive, subject + object + indirect obj (give, send)\n" +
                "  Unknown  – cannot determine");
        PROPERTY_WORD_KEY.put("valence", "verb");

        // Verb: Conjugation regularity
        PROPERTY_OPTIONS.put("conjugation_regularity", new String[]{
                "Regular", "Irregular"
        });
        PROPERTY_INSTRUCTIONS.put("conjugation_regularity",
                "Is this verb regular or irregular in its conjugation?\n" +
                "  Regular   – follows standard -ed / -ing patterns (walk → walked)\n" +
                "  Irregular – has non-standard past/participle forms (go → went)");
        PROPERTY_WORD_KEY.put("conjugation_regularity", "verb");

        // Adjective: Semantic Category
        PROPERTY_OPTIONS.put("adjective_category", new String[]{
                "Descriptive / Qualitative",
                "Evaluative",
                "Quantitative / Indefinite",
                "Numeral",
                "Demonstrative (Deictic)",
                "Possessive",
                "Interrogative",
                "Distributive",
                "Proper / Nominal",
                "Other"
        });
        PROPERTY_INSTRUCTIONS.put("adjective_category",
                "What semantic category does this adjective belong to?\n" +
                "  Descriptive / Qualitative  – inherent qualities or states (big, red, metallic, tired)\n" +
                "  Evaluative                 – judgment, value, or attitude (good, boring, frightening)\n" +
                "  Quantitative / Indefinite  – vague amount or extent (some, many, enough)\n" +
                "  Numeral                    – exact number or order (one, third, tenth)\n" +
                "  Demonstrative (Deictic)    – points to specific entities (this, that, these, those)\n" +
                "  Possessive                 – ownership or association (my, your, her)\n" +
                "  Interrogative              – used in questions (which, what, whose)\n" +
                "  Distributive               – refer to members individually (each, every, either, neither)\n" +
                "  Proper / Nominal           – derived from proper nouns (French, Shakespearean, Victorian)\n" +
                "  Other                      – none of the above");
        PROPERTY_WORD_KEY.put("adjective_category", "adjective");

        // Adverb: Semantic Category
        PROPERTY_OPTIONS.put("adverb_category", new String[]{
                "Manner",
                "Place / Location",
                "Direction / Path",
                "Time",
                "Duration",
                "Frequency",
                "Sequence",
                "Degree / Intensifier",
                "Approximator / Scalar",
                "Measure / Multiplier",
                "Epistemic",
                "Evidential",
                "Attitudinal / Evaluative",
                "Style / Domain",
                "Focus (additive / restrictive / emphatic)",
                "Negation / Polarity",
                "Affirmative",
                "Connective / Linking",
                "Topic-management / Discourse",
                "Interrogative",
                "Relative"
        });
        PROPERTY_INSTRUCTIONS.put("adverb_category",
                "What semantic category does this adverb belong to?\n" +
                "  Manner                                    – how (quickly, carefully)\n" +
                "  Place / Location                          – where (here, abroad)\n" +
                "  Direction / Path                          – movement or trajectory (away, homeward)\n" +
                "  Time                                      – when (now, tomorrow)\n" +
                "  Duration                                  – how long (briefly, forever)\n" +
                "  Frequency                                 – how often (always, rarely)\n" +
                "  Sequence                                  – orders events (first, then, finally)\n" +
                "  Degree / Intensifier                      – scales intensity (very, extremely)\n" +
                "  Approximator / Scalar                     – near limits (almost, roughly)\n" +
                "  Measure / Multiplier                      – proportional extent (twice, threefold)\n" +
                "  Epistemic                                 – certainty or likelihood (probably, surely)\n" +
                "  Evidential                                – source of information (apparently, reportedly)\n" +
                "  Attitudinal / Evaluative                  – speaker stance (fortunately, sadly)\n" +
                "  Style / Domain                            – limits to a perspective (technically, legally)\n" +
                "  Focus (additive / restrictive / emphatic) – highlights or limits focus (only, even)\n" +
                "  Negation / Polarity                       – expresses denial (not, never)\n" +
                "  Affirmative                               – reinforces truth (yes, indeed)\n" +
                "  Connective / Linking                      – joins clauses (however, therefore)\n" +
                "  Topic-management / Discourse              – manages discourse flow (well, anyway)\n" +
                "  Interrogative                             – introduces a question (how, when)\n" +
                "  Relative                                  – introduces a subordinate clause (when, where, wherever)");
        PROPERTY_WORD_KEY.put("adverb_category", "adverb");
    }

    // data structures
    /** One item to annotate. */
    private static class Item {
        String synsetId;
        String lemma;  // normalized key for deduplication and comparison
        String word;   // display form (may differ in case from lemma)
        String definition;

        Item(String synsetId, String lemma, String word, String definition) {
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.word = word;
            this.definition = definition;
        }
    }

    // ANSI colour helpers
    private static final String BOLD   = "\033[1m";
    private static final String CYAN   = "\033[36m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN  = "\033[32m";
    private static final String DIM    = "\033[2m";
    private static final String RESET  = "\033[0m";

    // main logic
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            printUsage();
            return;
        }

        // parse CLI arguments
        String morphoDBDir = args[0];
        String propertyArg = null;
        int maxPerSession = -1;   // -1 = no limit
        long seed = 42;
        boolean autoResume = true;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--max":
                    if (i + 1 < args.length) maxPerSession = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    if (i + 1 < args.length) seed = Long.parseLong(args[++i]);
                    break;
                case "--no-resume":
                    autoResume = false;
                    break;
                default:
                    // First non-flag argument after morphoDBDir is the property
                    if (!args[i].startsWith("--") && propertyArg == null) {
                        propertyArg = args[i].toLowerCase();
                    }
                    break;
            }
        }

        // initialize WordNet
        System.out.println("Initializing WordNet (requires SIGMA_HOME)...");
        initWordNet();

        // validate MorphoDB directory
        Path morphoDBPath = Paths.get(morphoDBDir);
        if (!Files.isDirectory(morphoDBPath)) {
            System.err.println("ERROR: MorphoDB directory not found: " + morphoDBDir);
            return;
        }

        // gold output directory
        Path goldDir = morphoDBPath.resolve("gold");
        Files.createDirectories(goldDir);

        // interactive property selection if not specified
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        String property;
        if (propertyArg != null && PROPERTY_OPTIONS.containsKey(propertyArg)) {
            property = propertyArg;
        } else {
            if (propertyArg != null) {
                System.err.println("Unknown property: " + propertyArg);
                System.err.println();
            }
            property = showPropertyMenu(morphoDBPath, goldDir, console);
            if (property == null) return;  // user quit
        }

        // resolve output path
        String outPath = goldDir.resolve(property + ".jsonl").toString();

        String[] options = PROPERTY_OPTIONS.get(property);
        String instructions = PROPERTY_INSTRUCTIONS.get(property);
        String wordKey = PROPERTY_WORD_KEY.get(property);

        // load input items from WordNet
        List<Item> items = loadItemsFromWordNet(wordKey);
        System.out.println("Loaded " + items.size() + " unique items from WordNet");

        // shuffle for randomised presentation order
        Collections.shuffle(items, new Random(seed));

        // auto-resume: load already-annotated lemmas
        Set<String> alreadyDone = new HashSet<>();
        int previousCount = 0;
        if (autoResume && Files.exists(Paths.get(outPath))) {
            alreadyDone = loadAnnotatedLemmas(outPath);
            previousCount = alreadyDone.size();
            System.out.println("Auto-resuming: " + previousCount
                    + " items already annotated");
        } else if (!autoResume && Files.exists(Paths.get(outPath))) {
            String backup = outPath + ".bak." + System.currentTimeMillis();
            Files.copy(Paths.get(outPath), Paths.get(backup));
            Files.delete(Paths.get(outPath));
            System.out.println("Starting fresh. Old annotations backed up to " + backup);
        }

        // Count how many remain
        int remaining = 0;
        for (Item item : items) {
            if (!alreadyDone.contains(item.lemma)) remaining++;
        }

        // annotation session state
        List<UndoEntry> undoStack = new ArrayList<>();
        int annotatedThisSession = 0;
        int skippedThisSession = 0;
        long sessionStart = System.currentTimeMillis();

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  Morphological Gold-Standard Annotation Tool" + RESET);
        System.out.println(BOLD + "  Property:       " + CYAN + property + RESET);
        System.out.println(BOLD + "  Total items:    " + items.size() + RESET);
        System.out.println(BOLD + "  Already done:   " + previousCount + RESET);
        System.out.println(BOLD + "  Remaining:      " + remaining + RESET);
        if (maxPerSession > 0) {
            System.out.println(BOLD + "  Session goal:   " + maxPerSession
                    + " (will ask to continue after)" + RESET);
        }
        System.out.println(BOLD + "  Output file:    " + DIM + outPath + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println(instructions);
        System.out.println();
        System.out.println("Controls:  [1-" + options.length + "] select  |  "
                + "[s] skip  |  [u] undo last  |  [q] quit & save");
        System.out.println("           [?] show stats   |  "
                + "[r] re-show instructions");
        System.out.println(DIM + "  (resume is automatic — quit any time, "
                + "just re-run to continue)" + RESET);
        System.out.println("───────────────────────────────────────────────────────────\n");

        if (remaining == 0) {
            System.out.println(GREEN + "All items in this set are already annotated!" + RESET);
            return;
        }

        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new FileWriter(outPath, true)))) {   // append mode

            int displayNum = previousCount;

            for (int idx = 0; idx < items.size(); idx++) {
                Item item = items.get(idx);

                // Skip already-annotated items (from previous sessions)
                if (alreadyDone.contains(item.lemma)) {
                    continue;
                }

                // soft stop: ask to continue after --max
                if (maxPerSession > 0 && annotatedThisSession > 0
                        && annotatedThisSession % maxPerSession == 0) {
                    System.out.println();
                    System.out.println(YELLOW + "  You've annotated " + annotatedThisSession
                            + " items this session (goal: " + maxPerSession + ")." + RESET);
                    System.out.print("  Continue annotating? [y/n] > ");
                    System.out.flush();
                    String answer = console.readLine();
                    if (answer == null || answer.trim().toLowerCase().startsWith("n")) {
                        printSessionSummary(annotatedThisSession, skippedThisSession,
                                previousCount, outPath, sessionStart);
                        return;
                    }
                    System.out.println("  → Continuing...\n");
                }

                displayNum++;

                // display item
                System.out.println(instructions);
                System.out.println();
                System.out.println(BOLD + "[" + displayNum + " | "
                        + annotatedThisSession + " this session]  "
                        + CYAN + item.word + RESET
                        + "  " + DIM + "(synset " + item.synsetId + ")" + RESET);
                System.out.println("  " + item.definition);
                System.out.println();
                for (int o = 0; o < options.length; o++) {
                    System.out.println("    " + YELLOW + (o + 1) + RESET
                            + ") " + options[o]);
                }
                System.out.println();
                System.out.print("  > ");
                System.out.flush();

                // read input
                String input = console.readLine();
                if (input == null) {
                    break;
                }
                input = input.trim().toLowerCase();

                // handle control keys
                if ("q".equals(input)) {
                    printSessionSummary(annotatedThisSession, skippedThisSession,
                            previousCount, outPath, sessionStart);
                    return;
                }
                if ("s".equals(input)) {
                    skippedThisSession++;
                    System.out.println("  → " + DIM + "skipped" + RESET + "\n");
                    continue;
                }
                if ("?".equals(input)) {
                    printLiveStats(annotatedThisSession, skippedThisSession,
                            previousCount, remaining, sessionStart);
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
                        System.out.println("  → nothing to undo this session\n");
                        idx--;
                        displayNum--;
                        continue;
                    }
                    UndoEntry last = undoStack.remove(undoStack.size() - 1);
                    annotatedThisSession--;
                    displayNum--;
                    alreadyDone.remove(last.lemma);
                    JSONObject undoMarker = new JSONObject();
                    undoMarker.put("synsetId", last.synsetId);
                    undoMarker.put("lemma", last.lemma);
                    undoMarker.put("human_label", "__UNDONE__");
                    undoMarker.put("timestamp", System.currentTimeMillis());
                    out.println(undoMarker.toString());
                    out.flush();

                    System.out.println("  → undid annotation for \""
                            + last.word + "\".  It will reappear.\n");
                    idx = last.listIndex - 1;
                    continue;
                }

                // parse choice number
                int choice;
                try {
                    choice = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println("  → invalid input — enter a number "
                            + "1-" + options.length + ", or s/u/q/?/r\n");
                    idx--;
                    displayNum--;
                    continue;
                }
                if (choice < 1 || choice > options.length) {
                    System.out.println("  → out of range (1-"
                            + options.length + ")\n");
                    idx--;
                    displayNum--;
                    continue;
                }

                String label = options[choice - 1];

                // write annotation
                JSONObject annotation = new JSONObject();
                annotation.put("synsetId", item.synsetId);
                annotation.put("lemma", item.lemma);
                annotation.put("word", item.word);
                annotation.put("definition", item.definition);
                annotation.put("property", property);
                annotation.put("human_label", label);
                annotation.put("timestamp", System.currentTimeMillis());

                out.println(annotation.toString());
                out.flush();

                alreadyDone.add(item.lemma);
                undoStack.add(new UndoEntry(item.synsetId, item.lemma, item.word, idx));
                annotatedThisSession++;

                System.out.println("  → " + GREEN + label + RESET + "\n");
            }

            // Reached end of all items
            printSessionSummary(annotatedThisSession, skippedThisSession,
                    previousCount, outPath, sessionStart);
            System.out.println(GREEN + "\n  All items annotated!" + RESET);
        }
    }

    // interactive property menu
    /**
     * Show an interactive menu of all properties with their current
     * annotation counts.  Returns the selected property name, or
     * null if the user quits.
     */
    private static String showPropertyMenu(Path morphoDBPath, Path goldDir,
                                           BufferedReader console) throws IOException {

        List<String> propertyNames = new ArrayList<>(PROPERTY_OPTIONS.keySet());

        System.out.println();
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println(BOLD + "  Morphological Gold-Standard Annotation Tool" + RESET);
        System.out.println(BOLD + "═══════════════════════════════════════════════════════════" + RESET);
        System.out.println();
        System.out.println("Select a property to annotate:");
        System.out.println();

        // Pre-compute WordNet item counts per POS (avoid reloading for each property)
        Map<String, Integer> posItemCount = new HashMap<>();
        for (String pos : new String[]{"noun", "verb", "adjective", "adverb"}) {
            posItemCount.put(pos, loadItemsFromWordNet(pos).size());
        }

        // Count items from WordNet and gold file for each property
        for (int i = 0; i < propertyNames.size(); i++) {
            String prop = propertyNames.get(i);

            // Count total items available from WordNet
            int totalItems = posItemCount.getOrDefault(PROPERTY_WORD_KEY.get(prop), 0);

            // Count already-annotated items
            Path goldFile = goldDir.resolve(prop + ".jsonl");
            int annotated = 0;
            if (Files.exists(goldFile)) {
                annotated = loadAnnotatedLemmas(goldFile.toString()).size();
            }

            // Format display
            String status;
            if (annotated == 0) {
                status = YELLOW + "0" + RESET + " / " + totalItems;
            } else {
                double pct = 100.0 * annotated / totalItems;
                status = GREEN + annotated + RESET + " / " + totalItems
                        + String.format("  (%.0f%%)", pct);
            }

            System.out.printf("    " + YELLOW + "%2d" + RESET + ")  %-28s %s%n",
                    i + 1, prop, status);
        }

        System.out.println();
        System.out.println("     " + DIM + "q)  quit" + RESET);
        System.out.println();
        System.out.print("  Select [1-" + propertyNames.size() + "] > ");
        System.out.flush();

        String input = console.readLine();
        if (input == null || input.trim().toLowerCase().equals("q")) {
            return null;
        }

        try {
            int choice = Integer.parseInt(input.trim());
            if (choice >= 1 && choice <= propertyNames.size()) {
                return propertyNames.get(choice - 1);
            }
        } catch (NumberFormatException e) {
            // Try matching by name
            String typed = input.trim().toLowerCase();
            if (PROPERTY_OPTIONS.containsKey(typed)) {
                return typed;
            }
        }

        System.err.println("Invalid selection.");
        return null;
    }

    // undo tracking
    private static class UndoEntry {
        String synsetId;
        String lemma;
        String word;
        int listIndex;

        UndoEntry(String synsetId, String lemma, String word, int listIndex) {
            this.synsetId = synsetId;
            this.lemma = lemma;
            this.word = word;
            this.listIndex = listIndex;
        }
    }

    // helpers
    private static void initWordNet() {
        KBmanager.getMgr().setPref("kbDir",
                System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
    }

    /**
     * Build the item list directly from WordNet, mirroring the
     * filteredSortedCopy() method used by GenMorphoDB at generation time:
     * wrap in a TreeMap (alphabetical sort) then remove non-English words.
     */
    private static List<Item> loadItemsFromWordNet(String pos) {
        Map<String, Set<String>> rawSynsetHash;
        Map<String, String> docHash;
        switch (pos) {
            case "noun":
                rawSynsetHash = WordNet.wn.nounSynsetHash;
                docHash       = WordNet.wn.nounDocumentationHash;
                break;
            case "verb":
                rawSynsetHash = WordNet.wn.verbSynsetHash;
                docHash       = WordNet.wn.verbDocumentationHash;
                break;
            case "adjective":
                rawSynsetHash = WordNet.wn.adjectiveSynsetHash;
                docHash       = WordNet.wn.adjectiveDocumentationHash;
                break;
            case "adverb":
                rawSynsetHash = WordNet.wn.adverbSynsetHash;
                docHash       = WordNet.wn.adverbDocumentationHash;
                break;
            default:
                return new ArrayList<>();
        }
        Map<String, Set<String>> synsetHash = new TreeMap<>(rawSynsetHash);
        GenMorphoUtils.removeNonEnglishWords(synsetHash, pos + " set");

        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : synsetHash.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String term  = entry.getKey().replace('_', ' ');
            String lemma = GenMorphoUtils.normalizeLemma(term);
            if (lemma.isEmpty() || seen.contains(lemma)) continue;
            seen.add(lemma);
            String synsetId = Collections.min(entry.getValue());
            String definition = docHash.get(synsetId);
            if (definition != null) definition = definition.replaceAll("^\"|\"$", "");
            if (definition == null || definition.trim().isEmpty()) definition = "(no definition)";
            items.add(new Item(synsetId, lemma, term, definition));
        }
        return items;
    }

    /**
     * Load the set of lemmas already annotated in an output file.
     * Handles the "__UNDONE__" marker: if the LAST entry for a lemma
     * is __UNDONE__, that lemma is treated as NOT annotated.
     *
     * For backward compatibility with older output files that lack
     * a "lemma" field, falls back to deriving the lemma from "word"
     * via normalization.
     */
    private static Set<String> loadAnnotatedLemmas(String path) throws IOException {
        Map<String, String> lastLabel = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject obj = new JSONObject(line);
                    String lemma = obj.optString("lemma", "");
                    if (lemma.isEmpty()) {
                        String word = obj.optString("word", "");
                        lemma = com.articulate.nlp.morphodb.GenMorphoUtils.normalizeLemma(word);
                    }
                    String label = obj.optString("human_label", "");
                    if (!lemma.isEmpty()) {
                        lastLabel.put(lemma, label);
                    }
                } catch (Exception e) {
                    // skip
                }
            }
        }
        Set<String> lemmas = new HashSet<>();
        for (Map.Entry<String, String> entry : lastLabel.entrySet()) {
            if (!"__UNDONE__".equals(entry.getValue())) {
                lemmas.add(entry.getKey());
            }
        }
        return lemmas;
    }

    private static void printLiveStats(int thisSession, int skipped,
                                       int previous, int totalRemaining,
                                       long sessionStart) {
        long elapsed = System.currentTimeMillis() - sessionStart;
        double mins = elapsed / 60000.0;
        double rate = thisSession > 0 ? mins / thisSession : 0;
        int grandTotal = previous + thisSession;

        System.out.println();
        System.out.println("  ┌─── Session Stats ────────────────────────┐");
        System.out.printf("  │  This session:   %5d annotated, %d skipped%n",
                thisSession, skipped);
        System.out.printf("  │  All-time total: %5d annotated%n", grandTotal);
        System.out.printf("  │  Remaining:      %5d%n",
                totalRemaining - thisSession);
        if (thisSession > 0) {
            System.out.printf("  │  Pace:           %.1f sec / item%n",
                    rate * 60);
            int est = (int) ((totalRemaining - thisSession) * rate);
            System.out.printf("  │  Est. remaining: %d min%n", est);
        }
        System.out.println("  └──────────────────────────────────────────┘");
        System.out.println();
    }

    private static void printSessionSummary(int thisSession, int skipped,
                                            int previous, String outPath,
                                            long sessionStart) {
        long elapsed = System.currentTimeMillis() - sessionStart;
        double mins = elapsed / 60000.0;
        int grandTotal = previous + thisSession;

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Session complete.");
        System.out.printf("  This session:   %d annotated, %d skipped (%.1f min)%n",
                thisSession, skipped, mins);
        System.out.printf("  All-time total: %d annotated%n", grandTotal);
        if (thisSession > 0) {
            System.out.printf("  Pace:           %.1f sec / item%n",
                    (elapsed / 1000.0) / thisSession);
        }
        System.out.println("  Output file:    " + outPath);
        System.out.println("  (Re-run to continue where you left off)");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    private static void printUsage() {
        System.out.println("Morphological Gold-Standard Annotation Tool");
        System.out.println("============================================");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java ... HumanAnnotationTool <morphodb_dir> [property] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <morphodb_dir>   Path where gold annotations are saved");
        System.out.println("                   (gold files written to <morphodb_dir>/gold/)");
        System.out.println("  [property]       Optional: property to annotate (see list below).");
        System.out.println("                   If omitted, an interactive menu is shown.");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  SIGMA_HOME must be set — WordNet is loaded at startup");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --max N        After N annotations, ask whether to continue");
        System.out.println("                 (default: no limit — annotate until you quit)");
        System.out.println("  --seed S       Random seed for shuffle order (default: 42)");
        System.out.println("  --no-resume    Start fresh instead of auto-resuming");
        System.out.println();
        System.out.println("Supported properties:");
        for (String p : PROPERTY_OPTIONS.keySet()) {
            String[] opts = PROPERTY_OPTIONS.get(p);
            System.out.printf("  %-25s  (%s)%n", p, String.join(", ", opts));
        }
        System.out.println();
        System.out.println("Gold annotations are saved to: <morphodb_dir>/gold/<property>.jsonl");
        System.out.println("Resume is AUTOMATIC.  Stop any time with [q] and re-run to continue.");
        System.out.println();
        System.out.println("Statistical guidance:");
        System.out.println("  ~200 items  → usable but wide confidence intervals");
        System.out.println("  ~500 items  → solid (recommended minimum)");
        System.out.println("  ~1000 items → very precise");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -cp $CP com.articulate.nlp.morphodb.evaluation.HumanAnnotationTool \\");
        System.out.println("    MorphoDB");
        System.out.println();
        System.out.println("  java -cp $CP com.articulate.nlp.morphodb.evaluation.HumanAnnotationTool \\");
        System.out.println("    MorphoDB countability --max 50");
    }
}
