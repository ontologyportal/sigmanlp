package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Entry point that delegates morphological database generation
 * to word-type specific generators.
 ***************************************************************/
public class GenMorphoDB {

    private static final int DEFAULT_OLLAMA_PORT = 11434;

    private static class CliOptions {
        String wordType;
        String genFunction;
        String provider = "ollama";
        String model;
        String morphoDbPath;
        Integer ollamaPort = DEFAULT_OLLAMA_PORT;
        String apiKey;
        String apiKeyEnv;
        String baseUrl;
        String serviceTier;
        Boolean cheapPromptMode;
        boolean verbose;
        boolean allModels;
        boolean compact;
        boolean addLemma;
        boolean dedup;
        boolean findFixGaps;
        boolean compareDb;
        String referenceDbPath;
        String verbStart;
        String verbEnd;
    }


    /***************************************************************
     * Runs every morphology generator for all word types.
     ***************************************************************/
    private static void generateAllClassifications(Map<String, Set<String>> nounSynsetHash,
                                                   Map<String, String> nounDocumentationHash,
                                                   Map<String, Set<String>> verbSynsetHash,
                                                   Map<String, String> verbDocumentationHash,
                                                   Map<String, Set<String>> adjectiveSynsetHash,
                                                   Map<String, String> adjectiveDocumentationHash,
                                                   Map<String, Set<String>> adverbSynsetHash,
                                                   Map<String, String> adverbDocumentationHash) {

        System.out.println("Generating all morphological classifications (noun, verb, adjective, adverb).");

        GenNounMorphoDB nounGenerator = new GenNounMorphoDB(nounSynsetHash, nounDocumentationHash);
        for (String fn : Arrays.asList("-i", "-c", "-p", "-h", "-a", "-l")) {
            System.out.println("Running noun generator with " + fn);
            nounGenerator.generate(fn);
        }

        GenVerbMorphoDB verbGenerator = new GenVerbMorphoDB(verbSynsetHash, verbDocumentationHash);
        for (String fn : Arrays.asList("-v", "-c", "-r", "-p", "-a", "-t")) {
            System.out.println("Running verb generator with " + fn);
            verbGenerator.generate(fn);
        }

        GenAdjectiveMorphoDB adjectiveGenerator = new GenAdjectiveMorphoDB(adjectiveSynsetHash, adjectiveDocumentationHash);
        System.out.println("Running adjective generator with -c");
        adjectiveGenerator.generate("-c");

        GenAdverbMorphoDB adverbGenerator = new GenAdverbMorphoDB(adverbSynsetHash, adverbDocumentationHash);
        System.out.println("Running adverb generator with -c");
        adverbGenerator.generate("-c");

        System.out.println("Completed all morphology generation tasks.");
    }


    /***************************************************************
     * Emits CLI usage details so future generators inherit a single
     * source of truth.
     ***************************************************************/
    private static void printHelp() {

        System.out.println("Usage:");
        System.out.println("  Legacy Ollama usage (still supported):");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> <model> [ollama-port]");
        System.out.println("Maintenance usage:");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --compact --db-path <path-to-morpho-db>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --compact --all-models --db-path <path-to-parent-dir>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --db-path <path-to-morpho-db>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --all-models --db-path <path-to-parent-dir>");
        System.out.println("  Provider-aware usage:");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> \\");
        System.out.println("      --provider <ollama|openai|anthropic|claude|openai-compatible|google|gemini|openrouter> --model <model> \\");
        System.out.println("      [--ollama-port <port>] [--api-key <key>|--api-key-env <ENV_VAR>] [--base-url <url>] \\");
        System.out.println("      [--service-tier <auto|default|flex|priority>] [--cheap-prompt|--full-prompt] [--verbose]");
        System.out.println("word-types supported: noun, verb, adjective, adverb, all");
        System.out.println("Maintenance flags:");
        System.out.println("  --compact      strip explanation/usage/definition and convert malformed rows into error rows");
        System.out.println("  --add-lemma    add the \"lemma\" field to existing records that lack it (no LLM calls)");
        System.out.println("  --dedup        remove duplicate entries, keeping the first occurrence per lemma");
        System.out.println("  --find-fix-gaps    compare each file against its WordNet POS set and list missing lemmas (requires SIGMA_HOME)");
        System.out.println("  --compare-db       compare synsetId/lemma pairs across DBs; requires --reference <ref-db-path> and --db-path [--all-models]");
        System.out.println("  --reference <path> (with --compare-db) reference DB path to compare against");
        System.out.println("                     (with --compact)    prefer matching synsetId from this DB during the dedup pre-pass");
        System.out.println("  --all-models   treat --db-path as parent directory and run maintenance on each direct child model dir");
        System.out.println("  "
                + "You may pass --add-lemma and --compact together; when both are set, --add-lemma runs first.");
        System.out.println("  "
                + "Maintenance works on noun, verb, adjective, and adverb tables in the current morphed model.");
        System.out.println("Noun gen-functions:");
        System.out.println("  -i to generate indefinite articles");
        System.out.println("  -c to generate countability classifications");
        System.out.println("  -p to generate plurals");
        System.out.println("  -h to classify human vs non-human nouns");
        System.out.println("  -a to classify nouns by agentivity (can the referent perform actions?)");
        System.out.println("  -l to classify collective nouns");
        System.out.println("Verb gen-functions:");
        System.out.println("  -v to classify verbs by valence");
        System.out.println("  -c to classify verbs by causativity");
        System.out.println("  -r to classify verbs by reflexive behavior");
        System.out.println("  -p to classify verbs by reciprocal behavior");
        System.out.println("  -a to classify verbs as achievements vs processes");
        System.out.println("  -t to generate full conjugation tables");
        System.out.println("Verb parallelization flags (apply to all verb gen-functions):");
        System.out.println("  --verb-start <letter>   Begin verb processing at this letter, inclusive (e.g. a)");
        System.out.println("  --verb-end   <letter>   Stop verb processing at this letter, inclusive (e.g. k)");
        System.out.println("Adjective gen-functions:");
        System.out.println("  -c to classify adjectives by semantic category");
        System.out.println("Adverb gen-functions:");
        System.out.println("  -c to classify adverbs by semantic category");
        System.out.println("All gen-functions:");
        System.out.println("  -a to run every morphology generator");
        System.out.println("Examples:");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i llama3.2 11434");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i --provider openai --model gpt-4o --api-key-env OPENAI_API_KEY");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB all -a --provider anthropic --model claude-3-7-sonnet-latest --api-key-env ANTHROPIC_API_KEY");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i --provider openai --model gpt-4o --api-key-env OPENAI_API_KEY --cheap-prompt");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB all -a --provider openai --model gpt-5 --api-key-env OPENAI_API_KEY --service-tier flex --cheap-prompt");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i --provider google --model gemini-2.5-flash-preview-04-17 --api-key-env GEMINI_API_KEY --cheap-prompt");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB noun -i --provider openrouter --model <openrouter-model-slug> --api-key-env OPENROUTER_API_KEY --cheap-prompt");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --compact --db-path /file/path/to/db/gpt-oss_20b");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --db-path /file/path/to/db/gpt-oss_20b");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --compact --all-models --db-path /file/path/to/db");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --all-models --db-path /file/path/to/db");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --dedup --db-path /file/path/to/db/gemma3_270m");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --dedup --all-models --db-path /file/path/to/db");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --find-fix-gaps --db-path /file/path/to/db/gemma3_270m");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --find-fix-gaps --all-models --db-path /file/path/to/db");
        System.out.println("If no Ollama port is provided, the default 11434 is used.");
        System.out.println("Default prompt mode: full for ollama, cheap for non-ollama providers.");
        System.out.println("--verbose prints per-item prompts and raw LLM responses.");
    }

    private static CliOptions parseCliArgs(String[] args) {

        if (args == null || args.length == 0) {
            return null;
        }
        CliOptions options = new CliOptions();
        int index = 0;
        if (index < args.length && !args[index].startsWith("--")) {
            options.wordType = args[index].toLowerCase();
            index++;
            if (index < args.length && !args[index].startsWith("--")) {
                options.genFunction = args[index];
                index++;
            }
            if (index < args.length && !args[index].startsWith("--")) {
                options.model = args[index];
                index++;
                if (index < args.length && isInteger(args[index])) {
                    options.ollamaPort = Integer.parseInt(args[index]);
                    index++;
                }
            }
        }

        while (index < args.length) {
            String arg = args[index];
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return null;
            }
            if ("--provider".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --provider.");
                    return null;
                }
                options.provider = args[index + 1];
                index += 2;
                continue;
            }
            if ("--model".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --model.");
                    return null;
                }
                options.model = args[index + 1];
                index += 2;
                continue;
            }
            if ("--ollama-port".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --ollama-port.");
                    return null;
                }
                if (!isInteger(args[index + 1])) {
                    System.err.println("Invalid --ollama-port value: " + args[index + 1]);
                    return null;
                }
                options.ollamaPort = Integer.parseInt(args[index + 1]);
                index += 2;
                continue;
            }
            if ("--api-key".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --api-key.");
                    return null;
                }
                options.apiKey = args[index + 1];
                index += 2;
                continue;
            }
            if ("--api-key-env".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --api-key-env.");
                    return null;
                }
                options.apiKeyEnv = args[index + 1];
                index += 2;
                continue;
            }
            if ("--base-url".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --base-url.");
                    return null;
                }
                options.baseUrl = args[index + 1];
                index += 2;
                continue;
            }
            if ("--service-tier".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --service-tier.");
                    return null;
                }
                options.serviceTier = args[index + 1];
                index += 2;
                continue;
            }
            if ("--cheap-prompt".equals(arg)) {
                options.cheapPromptMode = true;
                index++;
                continue;
            }
            if ("--full-prompt".equals(arg)) {
                options.cheapPromptMode = false;
                index++;
                continue;
            }
            if ("--verbose".equals(arg)) {
                options.verbose = true;
                index++;
                continue;
            }
            if ("--compact".equals(arg)) {
                options.compact = true;
                index++;
                continue;
            }
            if ("--add-lemma".equals(arg)) {
                options.addLemma = true;
                index++;
                continue;
            }
            if ("--dedup".equals(arg)) {
                options.dedup = true;
                index++;
                continue;
            }
            if ("--find-fix-gaps".equals(arg)) {
                options.findFixGaps = true;
                index++;
                continue;
            }
            if ("--verb-start".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --verb-start.");
                    return null;
                }
                options.verbStart = args[index + 1].toLowerCase();
                index += 2;
                continue;
            }
            if ("--verb-end".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --verb-end.");
                    return null;
                }
                options.verbEnd = args[index + 1].toLowerCase();
                index += 2;
                continue;
            }
            if ("--compare-db".equals(arg)) {
                options.compareDb = true;
                index++;
                continue;
            }
            if ("--reference".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --reference.");
                    return null;
                }
                options.referenceDbPath = args[index + 1];
                index += 2;
                continue;
            }
            if ("--all-models".equals(arg)) {
                options.allModels = true;
                index++;
                continue;
            }
            if ("--db-path".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --db-path.");
                    return null;
                }
                options.morphoDbPath = args[index + 1];
                index += 2;
                continue;
            }
            System.err.println("Unsupported argument: " + arg);
            return null;
        }

        int maintenanceCount = (options.compact ? 1 : 0) + (options.addLemma ? 1 : 0) + (options.dedup ? 1 : 0);
        boolean maintenanceRequested = maintenanceCount > 0;
        if (options.allModels && !maintenanceRequested && !options.findFixGaps && !options.compareDb) {
            System.err.println("--all-models is only valid for maintenance operations (--compact, --add-lemma, --dedup, --find-fix-gaps, or --compare-db).");
            return null;
        }
        if (maintenanceRequested && (options.morphoDbPath == null || options.morphoDbPath.trim().isEmpty())) {
            System.err.println("Missing --db-path for maintenance operations.");
            System.err.println("Pass --db-path <path-to-morpho-db>.");
            return null;
        }
        if (maintenanceRequested) {
            return options;
        }
        if (options.findFixGaps) {
            if (options.morphoDbPath == null || options.morphoDbPath.trim().isEmpty()) {
                System.err.println("Missing --db-path for --find-fix-gaps.");
                System.err.println("Pass --db-path <path-to-morpho-db>.");
                return null;
            }
            return options;
        }
        if (options.compareDb) {
            if (options.referenceDbPath == null || options.referenceDbPath.trim().isEmpty()) {
                System.err.println("--compare-db requires --reference <path-to-reference-db>.");
                return null;
            }
            if (options.morphoDbPath == null || options.morphoDbPath.trim().isEmpty()) {
                System.err.println("--compare-db requires --db-path <path-to-target-db>.");
                return null;
            }
            return options;
        }

        String normalizedProvider = GenUtils.normalizeLLMProvider(options.provider);
        if (normalizedProvider == null) {
            System.err.println("Unsupported provider: " + options.provider);
            return null;
        }
        options.provider = normalizedProvider;

        if (options.serviceTier != null && !options.serviceTier.trim().isEmpty()) {
            String normalizedServiceTier = GenUtils.normalizeOpenAIServiceTier(options.serviceTier);
            if (normalizedServiceTier == null) {
                System.err.println("Unsupported --service-tier value: " + options.serviceTier);
                System.err.println("Supported values: auto, default, flex, priority.");
                return null;
            }
            options.serviceTier = normalizedServiceTier;
        } else {
            options.serviceTier = null;
        }

        if (options.model == null || options.model.trim().isEmpty()) {
            System.err.println("Missing model. Provide legacy positional <model> or --model <model>.");
            return null;
        }
        options.model = options.model.trim();

        if (options.wordType == null || options.genFunction == null) {
            System.err.println("Missing word type and generation function.");
            return null;
        }

        if (!"ollama".equals(options.provider)) {
            String resolvedApiKey = resolveApiKey(options);
            if (resolvedApiKey == null || resolvedApiKey.trim().isEmpty()) {
                String envVar = GenUtils.getDefaultApiKeyEnvVar(options.provider);
                if (envVar == null || envVar.trim().isEmpty()) {
                    envVar = "<API_KEY_ENV_VAR>";
                }
                System.err.println("Provider \"" + options.provider + "\" requires an API key.");
                System.err.println("Pass --api-key, --api-key-env, or set " + envVar + ".");
                return null;
            }
            options.apiKey = resolvedApiKey.trim();
        }
        return options;
    }

    private static String resolveApiKey(CliOptions options) {

        if (options.apiKey != null && !options.apiKey.trim().isEmpty()) {
            return options.apiKey.trim();
        }
        if (options.apiKeyEnv != null && !options.apiKeyEnv.trim().isEmpty()) {
            String apiKeyFromNamedEnv = System.getenv(options.apiKeyEnv.trim());
            if (apiKeyFromNamedEnv != null && !apiKeyFromNamedEnv.trim().isEmpty()) {
                return apiKeyFromNamedEnv.trim();
            }
        }
        String defaultEnv = GenUtils.getDefaultApiKeyEnvVar(options.provider);
        if (defaultEnv != null && !defaultEnv.trim().isEmpty()) {
            String apiKeyFromDefaultEnv = System.getenv(defaultEnv);
            if (apiKeyFromDefaultEnv != null && !apiKeyFromDefaultEnv.trim().isEmpty()) {
                return apiKeyFromDefaultEnv.trim();
            }
        }
        return null;
    }

    private static boolean isInteger(String text) {

        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(text.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static void configureLlm(CliOptions options) {

        GenUtils.setLLMProvider(options.provider);
        GenUtils.setLLMModel(options.model);
        GenUtils.setLLMBaseUrl(options.baseUrl);
        GenUtils.setLLMServiceTier(options.serviceTier);
        boolean cheapPromptMode = options.cheapPromptMode != null
                ? options.cheapPromptMode
                : !"ollama".equals(options.provider);
        GenUtils.setCheapPromptMode(cheapPromptMode);
        GenMorphoUtils.debug = options.verbose;
        if ("ollama".equals(options.provider)) {
            GenUtils.setOllamaPort(options.ollamaPort == null ? DEFAULT_OLLAMA_PORT : options.ollamaPort);
            GenUtils.setLLMApiKey(null);
            System.out.println("Using provider: ollama");
            System.out.println("Using model: " + GenUtils.getLLMModel());
            System.out.println("Using Ollama port: " + options.ollamaPort);
        } else {
            GenUtils.setLLMApiKey(options.apiKey);
            System.out.println("Using provider: " + GenUtils.getLLMProvider());
            System.out.println("Using model: " + GenUtils.getLLMModel());
            if (options.baseUrl != null && !options.baseUrl.trim().isEmpty()) {
                System.out.println("Using base URL override: " + options.baseUrl);
            }
            if (options.apiKeyEnv != null && !options.apiKeyEnv.trim().isEmpty()) {
                System.out.println("Using API key from environment variable: " + options.apiKeyEnv.trim());
            } else {
                String defaultEnv = GenUtils.getDefaultApiKeyEnvVar(options.provider);
                if (defaultEnv != null) {
                    String fromEnv = System.getenv(defaultEnv);
                    if (fromEnv != null && !fromEnv.trim().isEmpty()) {
                        System.out.println("Using API key from default environment variable: " + defaultEnv);
                    } else {
                        System.out.println("Using API key provided by --api-key.");
                    }
                } else {
                    System.out.println("Using API key provided by --api-key.");
                }
            }
        }
        if ("openai".equals(options.provider) && options.serviceTier != null) {
            System.out.println("Using OpenAI service tier: " + options.serviceTier);
        } else if (options.serviceTier != null) {
            System.out.println("Ignoring --service-tier for provider: " + options.provider +
                    " (only applied when --provider openai).");
        }
        System.out.println("Using cheap prompt mode: " + GenUtils.isCheapPromptMode());
        System.out.println("Using verbose logging: " + GenMorphoUtils.debug);
        System.out.println("MorphoDB model directory: " + GenUtils.getMorphoModelDirectoryName());
    }

    
    /***************************************************************
     * Entry point: validates CLI args, preps WordNet data, and
     * dispatches to word-type generators.
     ***************************************************************/
    public static void main(String[] args) {

        System.out.println("Starting Generate Morphological Database");

        if (args.length == 0) {
            printHelp();
            return;
        }
        if ("-h".equals(args[0]) || "--help".equals(args[0])) {
            printHelp();
            return;
        }

        CliOptions cliOptions = parseCliArgs(args);
        if (cliOptions == null) {
            printHelp();
            return;
        }
        String wordType = cliOptions.wordType;
        String genFunction = cliOptions.genFunction;
        Path morphoModelRoot = resolveMorphoModelRoot(cliOptions);
        boolean maintenanceRequested = cliOptions.compact || cliOptions.addLemma || cliOptions.dedup;

        if (maintenanceRequested) {
            runMorphologicalMaintenance(morphoModelRoot, cliOptions);
            if (cliOptions.compact) {
                System.out.println("Running --find-fix-gaps automatically after --compact...");
                loadWordNetAndRunFindFixGaps(morphoModelRoot, cliOptions);
            }
            return;
        }

        if (cliOptions.findFixGaps) {
            loadWordNetAndRunFindFixGaps(morphoModelRoot, cliOptions);
            return;
        }

        if (cliOptions.compareDb) {
            Path referenceRoot = Paths.get(cliOptions.referenceDbPath.trim());
            runCompareDb(referenceRoot, cliOptions);
            return;
        }

        configureLlm(cliOptions);

        initWordNet();

        Map<String, Set<String>> nounSynsetHash      = filteredSortedCopy(WordNet.wn.nounSynsetHash,      "noun set");
        Map<String, Set<String>> verbSynsetHash      = filteredSortedCopy(WordNet.wn.verbSynsetHash,      "verb set");

        if (cliOptions.verbStart != null || cliOptions.verbEnd != null) {
            NavigableMap<String, Set<String>> navVerbs = (TreeMap<String, Set<String>>) verbSynsetHash;
            if (cliOptions.verbStart != null) {
                navVerbs = navVerbs.tailMap(cliOptions.verbStart, true);
            }
            if (cliOptions.verbEnd != null) {
                String upperBound = String.valueOf((char) (cliOptions.verbEnd.charAt(0) + 1));
                navVerbs = navVerbs.headMap(upperBound, false);
            }
            verbSynsetHash = navVerbs;
            System.out.println("Verb set size (after range filter): " + verbSynsetHash.size());
        }

        Map<String, Set<String>> adjectiveSynsetHash = filteredSortedCopy(WordNet.wn.adjectiveSynsetHash, "adjective set");
        Map<String, Set<String>> adverbSynsetHash    = filteredSortedCopy(WordNet.wn.adverbSynsetHash,    "adverb set");

        System.out.println("Noun set size      : " + nounSynsetHash.size());
        System.out.println("Verb set size      : " + verbSynsetHash.size());
        System.out.println("Adjective set size : " + adjectiveSynsetHash.size());
        System.out.println("Adverb set size    : " + adverbSynsetHash.size());

        switch (wordType) {
            case "noun":
            case "nouns": {
                GenNounMorphoDB nounGenerator = new GenNounMorphoDB(nounSynsetHash, WordNet.wn.nounDocumentationHash);
                nounGenerator.generate(genFunction);
                break;
            }
            case "verb":
            case "verbs": {
                GenVerbMorphoDB verbGenerator = new GenVerbMorphoDB(verbSynsetHash, WordNet.wn.verbDocumentationHash);
                verbGenerator.generate(genFunction);
                break;
            }
            case "adjective":
            case "adjectives": {
                GenAdjectiveMorphoDB adjectiveGenerator =
                        new GenAdjectiveMorphoDB(adjectiveSynsetHash, WordNet.wn.adjectiveDocumentationHash);
                adjectiveGenerator.generate(genFunction);
                break;
            }
            case "adverb":
            case "adverbs": {
                GenAdverbMorphoDB adverbGenerator =
                        new GenAdverbMorphoDB(adverbSynsetHash, WordNet.wn.adverbDocumentationHash);
                adverbGenerator.generate(genFunction);
                break;
            }
            case "all": {
                if (!"-a".equals(genFunction)) {
                    System.out.println("To run every morphology generator, specify word type \"all\" with gen-function \"-a\".");
                    printHelp();
                    break;
                }
                generateAllClassifications(
                        nounSynsetHash, WordNet.wn.nounDocumentationHash,
                        verbSynsetHash, WordNet.wn.verbDocumentationHash,
                        adjectiveSynsetHash, WordNet.wn.adjectiveDocumentationHash,
                        adverbSynsetHash, WordNet.wn.adverbDocumentationHash);
                break;
            }
            default:
                System.out.println("Unsupported word type: " + wordType);
                System.out.println("Please specify a word type (noun, verb, adjective, adverb).");
                printHelp();
                return;
        }

    }

    private static class MaintenanceTarget {
        final String modelName;
        final Path modelRoot;
        final List<MorphoFileSpec> fileSpecs;

        MaintenanceTarget(String modelName, Path modelRoot, List<MorphoFileSpec> fileSpecs) {
            this.modelName = modelName;
            this.modelRoot = modelRoot;
            this.fileSpecs = fileSpecs;
        }
    }

    private static class MaintenanceTargetResolution {
        final List<MaintenanceTarget> targets = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    private static String modelLabelFromPath(Path path) {

        if (path == null) {
            return "";
        }
        Path fileName = path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }
        return path.toString();
    }

    private static List<MorphoFileSpec> getExistingMorphologicalDatabaseFileSpecs(Path modelRoot) {

        List<MorphoFileSpec> existing = new ArrayList<>();
        for (MorphoFileSpec fileSpec : getMorphologicalDatabaseFileSpecs(modelRoot)) {
            if (fileSpec != null && fileSpec.path != null && Files.exists(fileSpec.path)) {
                existing.add(fileSpec);
            }
        }
        return existing;
    }

    private static MaintenanceTargetResolution resolveMaintenanceTargets(Path rootPath, boolean allModels) {

        MaintenanceTargetResolution result = new MaintenanceTargetResolution();
        if (rootPath == null) {
            return result;
        }

        if (!allModels) {
            List<MorphoFileSpec> existingFileSpecs = getExistingMorphologicalDatabaseFileSpecs(rootPath);
            if (existingFileSpecs.isEmpty()) {
                result.skipped.add(modelLabelFromPath(rootPath) + " (" + rootPath + "): no morphology files found");
                return result;
            }
            result.targets.add(new MaintenanceTarget(modelLabelFromPath(rootPath), rootPath, existingFileSpecs));
            return result;
        }

        if (!Files.exists(rootPath)) {
            result.skipped.add(rootPath + ": directory does not exist");
            return result;
        }
        if (!Files.isDirectory(rootPath)) {
            result.skipped.add(rootPath + ": not a directory");
            return result;
        }

        List<Path> childDirs = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(rootPath)) {
            stream.filter(Files::isDirectory).forEach(childDirs::add);
        } catch (IOException e) {
            result.skipped.add(rootPath + ": unable to enumerate child directories (" + e.getMessage() + ")");
            return result;
        }
        childDirs.sort(Comparator.comparing(GenMorphoDB::modelLabelFromPath));

        for (Path childDir : childDirs) {
            List<MorphoFileSpec> existingFileSpecs = getExistingMorphologicalDatabaseFileSpecs(childDir);
            if (existingFileSpecs.isEmpty()) {
                result.skipped.add(modelLabelFromPath(childDir) + " (" + childDir + "): no morphology files found");
                continue;
            }
            result.targets.add(new MaintenanceTarget(modelLabelFromPath(childDir), childDir, existingFileSpecs));
        }
        return result;
    }

    private static void printSkippedMaintenanceTargets(List<String> skipped) {

        if (skipped == null || skipped.isEmpty()) {
            return;
        }
        System.out.println("Skipped model directories:");
        for (String skippedEntry : skipped) {
            System.out.println("  - " + skippedEntry);
        }
    }

    private static void printCompactLine(BufferedWriter compactSummaryWriter, String line) {

        if (line == null) {
            return;
        }
        System.out.println(line);
        if (compactSummaryWriter == null) {
            return;
        }
        try {
            compactSummaryWriter.write(line);
            compactSummaryWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to write compact summary line: " + e.getMessage());
        }
    }

    private static String formatErrorPct(int errors, int lines) {

        if (lines <= 0) {
            return "0.00%";
        }
        double pct = (100.0 * errors) / lines;
        return String.format("%.2f%%", pct);
    }

    private static void runMorphologicalMaintenance(Path morphoModelRoot, CliOptions cliOptions) {

        if (morphoModelRoot == null) {
            System.out.println("Unable to determine morphological DB root path.");
            return;
        }
        System.out.println("Running morphological DB maintenance in: " + morphoModelRoot +
                (cliOptions.allModels ? " (all-models mode)" : ""));
        MaintenanceTargetResolution targetResolution =
                resolveMaintenanceTargets(morphoModelRoot, cliOptions.allModels);
        if (targetResolution.targets.isEmpty()) {
            System.out.println("No valid morphological model directories found for maintenance at: " + morphoModelRoot);
            printSkippedMaintenanceTargets(targetResolution.skipped);
            return;
        }

        if (cliOptions.addLemma && cliOptions.compact) {
            System.out.println("Both maintenance flags provided; running add-lemma first, then compact (dedup, compact, find-fix-gaps).");
        } else if (cliOptions.compact) {
            System.out.println("compact: full cleanup sequence: dedup, compact, find-fix-gaps.");
        }

        if (cliOptions.addLemma) {
            if (cliOptions.allModels) {
                Path summaryPath = morphoModelRoot.resolve("add-lemma-unchanged-summary.txt");
                try (BufferedWriter unchangedSummaryWriter =
                             Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8)) {
                    unchangedSummaryWriter.write("model\tfile\tline\treason\tcontent");
                    unchangedSummaryWriter.newLine();
                    for (MaintenanceTarget target : targetResolution.targets) {
                        addLemmaToAllMorphoFiles(target.fileSpecs, unchangedSummaryWriter, target.modelName);
                    }
                    unchangedSummaryWriter.flush();
                    System.out.println("add-lemma: unchanged summary file=" + summaryPath);
                } catch (IOException e) {
                    System.out.println("Failed to create unchanged summary file: " + summaryPath + " (" + e.getMessage() + ")");
                }
            } else {
                for (MaintenanceTarget target : targetResolution.targets) {
                    addLemmaToAllMorphoFiles(target.fileSpecs);
                }
            }
        }

        if (cliOptions.compact) {
            System.out.println("compact: running dedup pre-pass...");
            Path referenceRoot = null;
            if (cliOptions.referenceDbPath != null && !cliOptions.referenceDbPath.trim().isEmpty()) {
                referenceRoot = Paths.get(cliOptions.referenceDbPath.trim());
                System.out.println("compact: using reference DB for dedup: " + referenceRoot);
            }
            runDedupPass(targetResolution.targets, cliOptions.allModels, referenceRoot);
            Path compactSummaryPath = morphoModelRoot.resolve("compact-summary.txt");
            BufferedWriter compactSummaryWriter = null;
            try {
                compactSummaryWriter = Files.newBufferedWriter(compactSummaryPath, StandardCharsets.UTF_8);
                compactSummaryWriter.write("compact-summary");
                compactSummaryWriter.newLine();
                compactSummaryWriter.write("root\t" + morphoModelRoot);
                compactSummaryWriter.newLine();
            } catch (IOException e) {
                System.out.println("Failed to create compact summary file: " + compactSummaryPath + " (" + e.getMessage() + ")");
                compactSummaryWriter = null;
            }
            CompactStats total = new CompactStats();
            int totalFilesProcessed = 0;
            for (MaintenanceTarget target : targetResolution.targets) {
                CompactStats modelTotal = new CompactStats();
                for (MorphoFileSpec fileSpec : target.fileSpecs) {
                    CompactStats stats = compactMorphoFile(fileSpec, compactSummaryWriter);
                    modelTotal.add(stats);
                }
                total.add(modelTotal);
                totalFilesProcessed += target.fileSpecs.size();
                int modelErrors = modelTotal.existingErrorRows + modelTotal.malformedConvertedRows;
                String modelSummary = String.format("compact MODEL: %s files=%d lines=%d stripped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d)",
                        target.modelName,
                        target.fileSpecs.size(),
                        modelTotal.totalLines,
                        modelTotal.strippedFieldRows,
                        modelErrors,
                        formatErrorPct(modelErrors, modelTotal.totalLines),
                        modelTotal.existingErrorRows,
                        modelTotal.malformedConvertedRows);
                printCompactLine(compactSummaryWriter, modelSummary);
                if (modelTotal.malformedUnrecoverableRows > 0) {
                    String modelMalformedSummary = String.format("compact MODEL: %s dropped malformed rows without recoverable lemma=%d",
                            target.modelName,
                            modelTotal.malformedUnrecoverableRows);
                    printCompactLine(compactSummaryWriter, modelMalformedSummary);
                }
                if (modelTotal.lemmaAddedRows > 0) {
                    String modelLemmaSummary = String.format("compact MODEL: %s lemma added to existing JSON rows=%d",
                            target.modelName,
                            modelTotal.lemmaAddedRows);
                    printCompactLine(compactSummaryWriter, modelLemmaSummary);
                }
                if (modelTotal.droppedMissingLemmaRows > 0) {
                    String modelDroppedSummary = String.format("compact MODEL: %s dropped rows without lemma=%d",
                            target.modelName,
                            modelTotal.droppedMissingLemmaRows);
                    printCompactLine(compactSummaryWriter, modelDroppedSummary);
                }
            }
            int totalErrors = total.existingErrorRows + total.malformedConvertedRows;
            String totalSummary = String.format("compact TOTAL: models=%d skipped=%d files=%d lines=%d stripped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d)",
                    targetResolution.targets.size(),
                    targetResolution.skipped.size(),
                    totalFilesProcessed,
                    total.totalLines,
                    total.strippedFieldRows,
                    totalErrors,
                    formatErrorPct(totalErrors, total.totalLines),
                    total.existingErrorRows,
                    total.malformedConvertedRows);
            printCompactLine(compactSummaryWriter, totalSummary);
            if (total.malformedUnrecoverableRows > 0) {
                String totalMalformedSummary = String.format("compact TOTAL: dropped malformed rows without recoverable lemma=%d",
                        total.malformedUnrecoverableRows);
                printCompactLine(compactSummaryWriter, totalMalformedSummary);
            }
            if (total.lemmaAddedRows > 0) {
                String totalLemmaSummary = String.format("compact TOTAL: lemma added to existing JSON rows=%d", total.lemmaAddedRows);
                printCompactLine(compactSummaryWriter, totalLemmaSummary);
            }
            if (total.droppedMissingLemmaRows > 0) {
                String totalDroppedSummary = String.format("compact TOTAL: dropped rows without lemma=%d", total.droppedMissingLemmaRows);
                printCompactLine(compactSummaryWriter, totalDroppedSummary);
            }
            if (compactSummaryWriter != null) {
                try {
                    compactSummaryWriter.flush();
                    compactSummaryWriter.close();
                    System.out.println("compact: summary file=" + compactSummaryPath);
                } catch (IOException e) {
                    System.out.println("Failed to finalize compact summary file: " + compactSummaryPath + " (" + e.getMessage() + ")");
                }
            }
        }

        if (cliOptions.dedup) {
            Path dedupReferenceRoot = null;
            if (cliOptions.referenceDbPath != null && !cliOptions.referenceDbPath.trim().isEmpty()) {
                dedupReferenceRoot = Paths.get(cliOptions.referenceDbPath.trim());
                System.out.println("dedup: using reference DB: " + dedupReferenceRoot);
            }
            runDedupPass(targetResolution.targets, cliOptions.allModels, dedupReferenceRoot);
        }

        printSkippedMaintenanceTargets(targetResolution.skipped);
        System.out.println("Morphological DB maintenance complete for root: " + morphoModelRoot +
                (cliOptions.allModels ? " (all-models mode)" : ""));
    }

    private static Path resolveMorphoModelRoot(CliOptions options) {

        if (options != null && options.morphoDbPath != null && !options.morphoDbPath.trim().isEmpty()) {
            return Paths.get(options.morphoDbPath.trim());
        }
        if (options == null || options.model == null || options.model.trim().isEmpty()) {
            return null;
        }
        String modelDirectoryName = "ollama".equals(options.provider)
                ? options.model
                : options.provider + "__" + options.model;
        String sanitizedModelDirectory = GenMorphoUtils.sanitizeModelName(modelDirectoryName);
        return Paths.get("MorphologicalDatabase", sanitizedModelDirectory);
    }

    private static class MorphoFileSpec {
        final Path path;
        final String sourceFieldName;

        MorphoFileSpec(Path path, String sourceFieldName) {
            this.path = path;
            this.sourceFieldName = sourceFieldName;
        }
    }

    private static class CompactStats {
        int totalLines;
        int jsonLines;
        int strippedFieldRows;
        int existingErrorRows;
        int malformedConvertedRows;
        int malformedUnrecoverableRows;
        int lemmaAddedRows;
        int droppedMissingLemmaRows;

        void add(CompactStats other) {
            if (other == null) return;
            totalLines += other.totalLines;
            jsonLines += other.jsonLines;
            strippedFieldRows += other.strippedFieldRows;
            existingErrorRows += other.existingErrorRows;
            malformedConvertedRows += other.malformedConvertedRows;
            malformedUnrecoverableRows += other.malformedUnrecoverableRows;
            lemmaAddedRows += other.lemmaAddedRows;
            droppedMissingLemmaRows += other.droppedMissingLemmaRows;
        }
    }

    private static List<MorphoFileSpec> getMorphologicalDatabaseFileSpecs(Path modelRoot) {

        List<MorphoFileSpec> specs = new ArrayList<>();
        if (modelRoot == null) {
            return specs;
        }

        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("IndefiniteArticles.txt"), "noun"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("Countability.txt"), "noun"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("Plurals.txt"), "singular"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("Humanness.txt"), "noun"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("NounAgentivity.txt"), "noun"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("noun").resolve("CollectiveNouns.txt"), "noun"));

        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbValence.txt"), "verb"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbReflexive.txt"), "verb"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbCausativity.txt"), "verb"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbAchievementProcess.txt"), "verb"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbReciprocal.txt"), "verb"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbConjugations.txt"), "verb"));

        specs.add(new MorphoFileSpec(modelRoot.resolve("adjective").resolve("AdjectiveSemanticClasses.txt"), "adjective"));
        specs.add(new MorphoFileSpec(modelRoot.resolve("adverb").resolve("AdverbSemanticClasses.txt"), "adverb"));

        return specs;
    }

    private static CompactStats compactMorphoFile(MorphoFileSpec fileSpec,
                                                  BufferedWriter compactSummaryWriter) {

        CompactStats stats = new CompactStats();
        if (fileSpec == null || fileSpec.path == null || !Files.exists(fileSpec.path)) {
            return stats;
        }
        Path filePath = fileSpec.path;
        Path tmp = createTempFileInSameDirectory(filePath);
        int lineNo = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                stats.totalLines++;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    String recoveredLemma = recoverLemmaFromMalformedLine(line, fileSpec.sourceFieldName);
                    if (recoveredLemma.isEmpty()) {
                        stats.malformedUnrecoverableRows++;
                        stats.droppedMissingLemmaRows++;
                        continue;
                    }
                    String recoveredSynset = extractJsonLikeField(line, "synsetId");
                    com.fasterxml.jackson.databind.node.ObjectNode errorNode =
                            GenMorphoUtils.JSON_MAPPER.createObjectNode();
                    errorNode.put("synsetId", recoveredSynset == null ? "" : recoveredSynset);
                    errorNode.put("lemma", recoveredLemma);
                    errorNode.put(fileSpec.sourceFieldName, recoveredLemma);
                    errorNode.put("status", "error");
                    errorNode.put("rawResponse", sanitizeForJsonField(line));
                    writer.write(GenMorphoUtils.serializeJsonLine(errorNode));
                    writer.newLine();
                    stats.malformedConvertedRows++;
                    continue;
                }
                stats.jsonLines++;
                boolean removedAny = false;
                if (node.remove("explanation") != null) {
                    removedAny = true;
                }
                if (node.remove("usage") != null) {
                    removedAny = true;
                }
                if (node.remove("definition") != null) {
                    removedAny = true;
                }
                if (node.remove("message") != null) {
                    removedAny = true;
                }
                if (removedAny) {
                    stats.strippedFieldRows++;
                }

                String lemma = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
                if (lemma.isEmpty()) {
                    String sourceValue = recoverLemmaFromNode(node, fileSpec.sourceFieldName);
                    if (!sourceValue.isEmpty()) {
                        node.put("lemma", sourceValue);
                        lemma = sourceValue;
                        stats.lemmaAddedRows++;
                    }
                    else {
                        stats.droppedMissingLemmaRows++;
                        continue;
                    }
                } else {
                    node.put("lemma", lemma);
                }
                node = GenMorphoUtils.prependSynsetIdAndLemma(
                        node,
                        node.path("synsetId").asText(""),
                        lemma);

                if ("error".equalsIgnoreCase(node.path("status").asText(""))) {
                    stats.existingErrorRows++;
                }
                writer.write(GenMorphoUtils.serializeJsonLine(node));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            printCompactLine(compactSummaryWriter, "Failed while compacting file " + filePath + ": " + e.getMessage());
            return stats;
        }
        overwriteFile(filePath, tmp);
        int errorRows = stats.existingErrorRows + stats.malformedConvertedRows;
        String fileSummary = String.format("compact: %-30s lines=%d stripped=%d dropped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d)",
                filePath.getFileName(),
                stats.totalLines,
                stats.strippedFieldRows,
                stats.droppedMissingLemmaRows,
                errorRows,
                formatErrorPct(errorRows, stats.totalLines),
                stats.existingErrorRows,
                stats.malformedConvertedRows);
        printCompactLine(compactSummaryWriter, fileSummary);
        return stats;
    }

    /**
     * Removes duplicate entries from a morpho file, keeping the first occurrence of each normalized lemma.
     * Returns int[3]: {totalLines, keptLines, droppedLines}.
     */
    private static void runDedupPass(List<MaintenanceTarget> targets, boolean allModels, Path referenceRoot) {

        int totalLines = 0, totalKept = 0, totalDropped = 0, totalUpgrades = 0;
        for (MaintenanceTarget target : targets) {
            int modelLines = 0, modelKept = 0, modelDropped = 0, modelUpgrades = 0;
            for (MorphoFileSpec fileSpec : target.fileSpecs) {
                Map<String, String> refMap = null;
                if (referenceRoot != null) {
                    Path refFilePath = referenceRoot
                            .resolve(fileSpec.path.getParent().getFileName())
                            .resolve(fileSpec.path.getFileName());
                    refMap = loadLemmaToSynsetIdMap(refFilePath);
                }
                int[] stats = dedupMorphoFile(fileSpec, refMap);
                modelLines    += stats[0];
                modelKept     += stats[1];
                modelDropped  += stats[2];
                modelUpgrades += stats[3];
                if (stats[2] > 0 || stats[3] > 0) {
                    System.out.printf("dedup: %-40s  total=%d  kept=%d  dropped=%d  ref-upgrades=%d%n",
                            fileSpec.path.getFileName(), stats[0], stats[1], stats[2], stats[3]);
                }
            }
            totalLines    += modelLines;
            totalKept     += modelKept;
            totalDropped  += modelDropped;
            totalUpgrades += modelUpgrades;
            if (allModels) {
                System.out.printf("dedup MODEL: %-30s  files=%d  total=%d  kept=%d  dropped=%d  ref-upgrades=%d%n",
                        target.modelName, target.fileSpecs.size(), modelLines, modelKept, modelDropped, modelUpgrades);
            }
        }
        System.out.printf("dedup TOTAL: models=%d  total=%d  kept=%d  dropped=%d  ref-upgrades=%d%n",
                targets.size(), totalLines, totalKept, totalDropped, totalUpgrades);
    }

    /***************************************************************
     * Removes duplicate lemmas from a morpho file.
     * When refLemmaToSynsetId is non-null, among duplicates for the
     * same lemma the row whose synsetId matches the reference is
     * preferred; otherwise the first-in-file row is kept.
     * Returns int[4]: {totalLines, keptLines, droppedLines, refUpgrades}.
     ***************************************************************/
    private static int[] dedupMorphoFile(MorphoFileSpec fileSpec, Map<String, String> refLemmaToSynsetId) {

        int[] result = {0, 0, 0, 0};
        if (fileSpec == null || fileSpec.path == null || !Files.exists(fileSpec.path)) {
            return result;
        }
        Path filePath = fileSpec.path;
        Path tmp = createTempFileInSameDirectory(filePath);

        Map<String, String> lemmaToSelectedLine = new HashMap<>();
        List<String> lemmaOrder = new ArrayList<>();
        List<String> unparseable = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                result[0]++;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    unparseable.add(line);
                    result[1]++;
                    continue;
                }
                String lemma = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
                if (lemma.isEmpty()) {
                    result[2]++;
                    continue;
                }
                if (!lemmaToSelectedLine.containsKey(lemma)) {
                    lemmaToSelectedLine.put(lemma, line);
                    lemmaOrder.add(lemma);
                } else if (refLemmaToSynsetId != null && refLemmaToSynsetId.containsKey(lemma)) {
                    String refSynsetId = refLemmaToSynsetId.get(lemma);
                    String currentSynsetId = GenMorphoUtils.parseJsonObjectLine(
                            lemmaToSelectedLine.get(lemma)).path("synsetId").asText("").trim();
                    String thisSynsetId = node.path("synsetId").asText("").trim();
                    if (!refSynsetId.equals(currentSynsetId) && refSynsetId.equals(thisSynsetId)) {
                        lemmaToSelectedLine.put(lemma, line);
                        result[3]++;
                    } else {
                        result[2]++;
                    }
                } else {
                    result[2]++;
                }
            }
        } catch (IOException e) {
            System.out.println("dedup: Failed while reading file " + filePath + ": " + e.getMessage());
            return result;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (String unparsedLine : unparseable) {
                writer.write(unparsedLine);
                writer.newLine();
            }
            for (String lemma : lemmaOrder) {
                writer.write(lemmaToSelectedLine.get(lemma));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            System.out.println("dedup: Failed while writing file " + filePath + ": " + e.getMessage());
            return result;
        }

        overwriteFile(filePath, tmp);
        return result;
    }

    private static Set<String> buildNormalizedLemmaSet(Map<String, Set<String>> synsetHash) {

        Set<String> result = new HashSet<>();
        for (String lemma : synsetHash.keySet()) {
            String normalized = GenMorphoUtils.normalizeLemma(lemma);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static void initWordNet() {

        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
    }

    private static Map<String, Set<String>> filteredSortedCopy(Map<String, Set<String>> raw, String label) {

        Map<String, Set<String>> copy = new TreeMap<>(raw);
        GenMorphoUtils.removeNonEnglishWords(copy, label);
        return copy;
    }

    private static void loadWordNetAndRunFindFixGaps(Path morphoModelRoot, CliOptions cliOptions) {

        initWordNet();
        runFindFixGaps(morphoModelRoot, cliOptions,
                       buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.nounSynsetHash,      "noun set")),
                       buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.verbSynsetHash,      "verb set")),
                       buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.adjectiveSynsetHash, "adjective set")),
                       buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.adverbSynsetHash,    "adverb set")));
    }

    private static void runFindFixGaps(Path morphoModelRoot, CliOptions cliOptions,
                                    Set<String> nounLemmas, Set<String> verbLemmas,
                                    Set<String> adjLemmas,  Set<String> advLemmas) {

        if (morphoModelRoot == null) {
            System.out.println("Unable to determine morphological DB root path.");
            return;
        }
        System.out.println("Finding gaps in: " + morphoModelRoot +
                (cliOptions.allModels ? " (all-models mode)" : ""));
        MaintenanceTargetResolution targetResolution =
                resolveMaintenanceTargets(morphoModelRoot, cliOptions.allModels);
        if (targetResolution.targets.isEmpty()) {
            System.out.println("No valid morphological model directories found at: " + morphoModelRoot);
            printSkippedMaintenanceTargets(targetResolution.skipped);
            return;
        }
        for (MaintenanceTarget target : targetResolution.targets) {
            if (cliOptions.allModels) {
                System.out.println("find-gaps MODEL: " + target.modelName);
            }
            Path gapsFile = target.modelRoot.resolve("find-gaps.txt");
            try (BufferedWriter gapsWriter = Files.newBufferedWriter(gapsFile, StandardCharsets.UTF_8)) {
                for (MorphoFileSpec fileSpec : target.fileSpecs) {
                    Set<String> expected = posExpectedLemmas(fileSpec.sourceFieldName,
                            nounLemmas, verbLemmas, adjLemmas, advLemmas);
                    findFixGapsForFile(fileSpec, expected, gapsWriter);
                }
            } catch (IOException e) {
                System.out.println("find-gaps: Failed writing " + gapsFile + ": " + e.getMessage());
            }
            System.out.println("  -> gap details written to: " + gapsFile);
        }
        printSkippedMaintenanceTargets(targetResolution.skipped);
    }

    private static Set<String> posExpectedLemmas(String sourceFieldName,
                                                  Set<String> nounLemmas, Set<String> verbLemmas,
                                                  Set<String> adjLemmas,  Set<String> advLemmas) {
        switch (sourceFieldName) {
            case "verb":      return verbLemmas;
            case "adjective": return adjLemmas;
            case "adverb":    return advLemmas;
            default:          return nounLemmas; // "noun" and "singular"
        }
    }

    private static void findFixGapsForFile(MorphoFileSpec fileSpec, Set<String> expectedLemmas,
                                         BufferedWriter gapsWriter) {

        if (fileSpec == null || fileSpec.path == null || !Files.exists(fileSpec.path)) {
            System.out.printf("find-fix-gaps: %-40s  (file not found)%n", fileSpec != null ? fileSpec.path : "null");
            return;
        }
        Path filePath = fileSpec.path;
        Path tmp = createTempFileInSameDirectory(filePath);
        Set<String> keptLemmas  = new HashSet<>();
        Set<String> extraLemmas = new TreeSet<>();
        int droppedNoLemma = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    // Malformed — keep and let --compact handle it
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                String lemma = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
                if (lemma.isEmpty()) {
                    droppedNoLemma++;
                    continue;
                }
                if (!expectedLemmas.contains(lemma)) {
                    extraLemmas.add(lemma);
                    continue;
                }
                keptLemmas.add(lemma);
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            System.out.println("find-fix-gaps: Failed reading/writing " + filePath + ": " + e.getMessage());
            return;
        }
        overwriteFile(filePath, tmp);
        Set<String> missing = new TreeSet<>(expectedLemmas);
        missing.removeAll(keptLemmas);
        System.out.printf("find-fix-gaps: %-40s  expected=%d  kept=%d  missing=%d  removed=%d  droppedNoLemma=%d%n",
                filePath.getFileName(),
                expectedLemmas.size(), keptLemmas.size(),
                missing.size(), extraLemmas.size(), droppedNoLemma);
        try {
            gapsWriter.write("=== " + filePath.getFileName() +
                    "  expected=" + expectedLemmas.size() +
                    "  kept=" + keptLemmas.size() +
                    "  missing=" + missing.size() +
                    "  removed=" + extraLemmas.size() + " ===");
            gapsWriter.newLine();
            if (!missing.isEmpty()) {
                gapsWriter.write("MISSING:");
                gapsWriter.newLine();
                for (String lemma : missing) {
                    gapsWriter.write("  " + lemma);
                    gapsWriter.newLine();
                }
            }
            if (!extraLemmas.isEmpty()) {
                gapsWriter.write("REMOVED (extra):");
                gapsWriter.newLine();
                for (String lemma : extraLemmas) {
                    gapsWriter.write("  " + lemma);
                    gapsWriter.newLine();
                }
            }
            gapsWriter.newLine();
        } catch (IOException e) {
            System.out.println("find-fix-gaps: Failed writing gap details for " + filePath.getFileName() + ": " + e.getMessage());
        }
    }

    private static String recoverLemmaFromNode(com.fasterxml.jackson.databind.node.ObjectNode node, String sourceFieldName) {

        if (node == null) {
            return "";
        }
        String lemma = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
        if (!lemma.isEmpty()) {
            return lemma;
        }
        lemma = GenMorphoUtils.normalizeLemma(node.path(sourceFieldName).asText(""));
        if (!lemma.isEmpty()) {
            return lemma;
        }
        for (String fallback : Arrays.asList("noun", "singular", "verb", "adjective", "adverb")) {
            lemma = GenMorphoUtils.normalizeLemma(node.path(fallback).asText(""));
            if (!lemma.isEmpty()) {
                return lemma;
            }
        }
        return "";
    }

    private static String extractJsonLikeField(String line, String fieldName) {

        if (line == null || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(line);
        if (m.find()) {
            String value = m.group(1);
            return value == null ? null : value.trim();
        }
        return null;
    }

    private static String recoverLemmaFromMalformedLine(String line, String sourceFieldName) {

        String lemma = GenMorphoUtils.normalizeLemma(extractJsonLikeField(line, "lemma"));
        if (!lemma.isEmpty()) {
            return lemma;
        }
        lemma = GenMorphoUtils.normalizeLemma(extractJsonLikeField(line, sourceFieldName));
        if (!lemma.isEmpty()) {
            return lemma;
        }
        for (String fallback : Arrays.asList("noun", "singular", "verb", "adjective", "adverb")) {
            lemma = GenMorphoUtils.normalizeLemma(extractJsonLikeField(line, fallback));
            if (!lemma.isEmpty()) {
                return lemma;
            }
        }
        return "";
    }

    private static String sanitizeForJsonField(String line) {

        if (line == null) {
            return "";
        }
        return line.replace("\n", " ").replace("\r", " ").trim();
    }

    private static Path createTempFileInSameDirectory(Path source) {
        try {
            return Files.createTempFile(source.getParent(), source.getFileName().toString(), ".tmp");
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary file for " + source, e);
        }
    }

    private static void overwriteFile(Path target, Path sourceTmp) {
        try {
            Files.move(sourceTmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Unable to overwrite file " + target, e);
        }
    }

    private static Path addLemmaUnchangedSummaryPath(List<MorphoFileSpec> fileSpecs) {

        if (fileSpecs == null || fileSpecs.isEmpty() || fileSpecs.get(0) == null || fileSpecs.get(0).path == null) {
            return Paths.get("add-lemma-unchanged-summary.txt");
        }
        Path firstPath = fileSpecs.get(0).path;
        Path modelRoot = firstPath.getParent();
        if (modelRoot != null) {
            modelRoot = modelRoot.getParent();
        }
        if (modelRoot == null) {
            modelRoot = firstPath.getParent();
        }
        if (modelRoot == null) {
            modelRoot = firstPath.toAbsolutePath().getParent();
        }
        if (modelRoot == null) {
            return Paths.get("add-lemma-unchanged-summary.txt");
        }
        return modelRoot.resolve("add-lemma-unchanged-summary.txt");
    }

    private static void writeUnchangedLine(BufferedWriter unchangedWriter,
                                           String modelName,
                                           String sourceFileName,
                                           int lineNumber,
                                           String reason,
                                           String lineContent) throws IOException {

        String safeReason = (reason == null) ? "" : reason;
        String safeLine = (lineContent == null) ? "" : lineContent;
        String safeSource = (sourceFileName == null) ? "" : sourceFileName;
        String safeModel = (modelName == null) ? "" : modelName;
        if (safeModel.isEmpty()) {
            unchangedWriter.write(safeSource + "\t" + lineNumber + "\t" + safeReason + "\t" + safeLine);
        } else {
            unchangedWriter.write(safeModel + "\t" + safeSource + "\t" + lineNumber + "\t" + safeReason + "\t" + safeLine);
        }
        unchangedWriter.newLine();
    }

    /***************************************************************
     * Adds the "lemma" field to all morphological DB files that lack
     * it, deriving the value from the type-specific source field
     * (e.g. "noun", "singular", "verb", "adjective", "adverb").
     * Existing "lemma" fields are preserved unchanged.
     ***************************************************************/
    private static void addLemmaToAllMorphoFiles(List<MorphoFileSpec> fileSpecs) {

        if (fileSpecs == null || fileSpecs.isEmpty()) return;
        Path summaryPath = addLemmaUnchangedSummaryPath(fileSpecs);
        try (BufferedWriter unchangedSummaryWriter = Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8)) {
            unchangedSummaryWriter.write("file\tline\treason\tcontent");
            unchangedSummaryWriter.newLine();
            addLemmaToAllMorphoFiles(fileSpecs, unchangedSummaryWriter, null);
            unchangedSummaryWriter.flush();
            System.out.println("add-lemma: unchanged summary file=" + summaryPath);
        } catch (IOException e) {
            System.out.println("Failed to create unchanged summary file: " + summaryPath + " (" + e.getMessage() + ")");
        }
    }

    private static void addLemmaToAllMorphoFiles(List<MorphoFileSpec> fileSpecs,
                                                  BufferedWriter unchangedSummaryWriter,
                                                  String modelName) {

        if (fileSpecs == null || fileSpecs.isEmpty() || unchangedSummaryWriter == null) return;
        for (MorphoFileSpec fileSpec : fileSpecs) {
            addLemmaFieldToMorphoFile(fileSpec.path, fileSpec.sourceFieldName, unchangedSummaryWriter, modelName);
        }
    }

    /***************************************************************
     * Reads each JSON line in the file. If a "lemma" field is absent,
     * derives its value from the named sourceFieldName (normalized),
     * inserts it after "synsetId", and rewrites the file in place.
     * Lines that already have a "lemma" field are written unchanged.
     ***************************************************************/
    private static void addLemmaFieldToMorphoFile(Path filePath,
                                                  String sourceFieldName,
                                                  BufferedWriter unchangedWriter,
                                                  String modelName) {

        if (filePath == null || !Files.exists(filePath)) {
            return;
        }
        Path tmp = createTempFileInSameDirectory(filePath);
        String sourceFileName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
        int kept = 0;
        int added = 0;
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    writer.write(line);
                    writer.newLine();
                    writeUnchangedLine(unchangedWriter, modelName, sourceFileName, lineNumber, "malformed_json", line);
                    kept++;
                    continue;
                }
                if (!node.path("lemma").asText("").trim().isEmpty()) {
                    // lemma already present — write unchanged
                    String serialized = GenMorphoUtils.serializeJsonLine(node);
                    writer.write(serialized);
                    writer.newLine();
                    writeUnchangedLine(unchangedWriter, modelName, sourceFileName, lineNumber, "lemma_present", serialized);
                    kept++;
                    continue;
                }
                String sourceValue = node.path(sourceFieldName).asText("").trim();
                if ("singular".equals(sourceFieldName)) {
                    String nounValue = node.path("noun").asText("").trim();
                    if (!sourceValue.isEmpty() && !nounValue.isEmpty()) {
                        String normalizedSingular = GenMorphoUtils.normalizeLemma(sourceValue);
                        String normalizedNoun = GenMorphoUtils.normalizeLemma(nounValue);
                        if (!normalizedSingular.isEmpty() && !normalizedNoun.isEmpty()
                                && !normalizedSingular.equals(normalizedNoun)) {
                            String serialized = GenMorphoUtils.serializeJsonLine(node);
                            writeUnchangedLine(unchangedWriter, modelName, sourceFileName, lineNumber,
                                    "mismatch_singular_noun_prefer_singular", serialized);
                        }
                    }
                    if (sourceValue.isEmpty() && !nounValue.isEmpty()) {
                        sourceValue = nounValue;
                    }
                }
                if (sourceValue.isEmpty()) {
                    // no source value to derive from — write unchanged
                    String serialized = GenMorphoUtils.serializeJsonLine(node);
                    writer.write(serialized);
                    writer.newLine();
                    writeUnchangedLine(unchangedWriter, modelName, sourceFileName, lineNumber, "missing_" + sourceFieldName, serialized);
                    kept++;
                    continue;
                }
                String lemma = GenMorphoUtils.normalizeLemma(sourceValue);
                com.fasterxml.jackson.databind.node.ObjectNode rebuilt =
                        GenMorphoUtils.prependSynsetIdAndLemma(
                                node,
                                node.path("synsetId").asText(""),
                                lemma);
                writer.write(GenMorphoUtils.serializeJsonLine(rebuilt));
                writer.newLine();
                added++;
            }
            writer.flush();
            System.out.println("add-lemma: " + filePath.getFileName() + " unchanged=" + kept + " updated=" + added);
        } catch (IOException e) {
            System.out.println("Failed while adding lemma field to " + filePath + ": " + e.getMessage());
            return;
        }
        overwriteFile(filePath, tmp);
    }


    /***************************************************************
     * Loads a morphoDB file and returns a map of normalized lemma
     * to synsetId. Used by the reference-aware dedup pre-pass.
     ***************************************************************/
    private static Map<String, String> loadLemmaToSynsetIdMap(Path filePath) {

        Map<String, String> map = new HashMap<>();
        if (filePath == null || !Files.exists(filePath)) {
            return map;
        }
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    continue;
                }
                String synsetId = node.path("synsetId").asText("").trim();
                String lemma    = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
                if (!synsetId.isEmpty() && !lemma.isEmpty()) {
                    map.put(lemma, synsetId);
                }
            }
        } catch (IOException e) {
            System.out.println("dedup: failed to read reference file " + filePath + ": " + e.getMessage());
        }
        return map;
    }


    /***************************************************************
     * Compares synset_id/lemma pairs in every morphology file of
     * one or more target DBs against the corresponding file in the
     * reference DB and prints per-file counts to stdout.
     ***************************************************************/
    private static void runCompareDb(Path referenceRoot, CliOptions cliOptions) {

        Path targetRoot = Paths.get(cliOptions.morphoDbPath.trim());
        System.out.println("compare-db reference : " + referenceRoot);
        System.out.println("compare-db target    : " + targetRoot
                + (cliOptions.allModels ? " (all-models mode)" : ""));

        List<MorphoFileSpec> refSpecs = getExistingMorphologicalDatabaseFileSpecs(referenceRoot);
        if (refSpecs.isEmpty()) {
            System.out.println("compare-db: no morphology files found in reference: " + referenceRoot);
            return;
        }

        MaintenanceTargetResolution targetResolution = resolveMaintenanceTargets(targetRoot, cliOptions.allModels);
        printSkippedMaintenanceTargets(targetResolution.skipped);

        if (targetResolution.targets.isEmpty()) {
            System.out.println("compare-db: no target models found under: " + targetRoot);
            return;
        }

        for (MaintenanceTarget target : targetResolution.targets) {
            System.out.println("compare-db MODEL: " + target.modelName);
            int totalOnlyInRef = 0, totalOnlyInTarget = 0, totalCommon = 0;

            for (MorphoFileSpec targetSpec : target.fileSpecs) {
                String fileName = targetSpec.path.getFileName().toString();
                MorphoFileSpec refSpec = findRefSpecByFilename(refSpecs, fileName);
                if (refSpec == null) {
                    System.out.printf("  compare-db: %-42s  (not present in reference)%n", fileName);
                    continue;
                }

                Set<String> refPairs    = loadSynsetIdLemmaPairs(refSpec.path);
                Set<String> targetPairs = loadSynsetIdLemmaPairs(targetSpec.path);

                Set<String> onlyInRef = new TreeSet<>(refPairs);
                onlyInRef.removeAll(targetPairs);

                Set<String> onlyInTarget = new TreeSet<>(targetPairs);
                onlyInTarget.removeAll(refPairs);

                int common = refPairs.size() - onlyInRef.size();
                totalOnlyInRef    += onlyInRef.size();
                totalOnlyInTarget += onlyInTarget.size();
                totalCommon       += common;

                System.out.printf("  compare-db: %-42s  ref=%d  target=%d  only-in-ref=%d  only-in-target=%d  common=%d%n",
                        fileName, refPairs.size(), targetPairs.size(),
                        onlyInRef.size(), onlyInTarget.size(), common);
            }

            for (MorphoFileSpec refSpec : refSpecs) {
                String fileName = refSpec.path.getFileName().toString();
                if (findRefSpecByFilename(target.fileSpecs, fileName) == null) {
                    System.out.printf("  compare-db: %-42s  (not present in target)%n", fileName);
                }
            }

            System.out.printf("compare-db MODEL TOTAL: %-30s  only-in-ref=%d  only-in-target=%d  common=%d%n",
                    target.modelName, totalOnlyInRef, totalOnlyInTarget, totalCommon);
        }
    }

    private static MorphoFileSpec findRefSpecByFilename(List<MorphoFileSpec> specs, String fileName) {

        for (MorphoFileSpec spec : specs) {
            if (spec.path != null && fileName.equals(spec.path.getFileName().toString())) {
                return spec;
            }
        }
        return null;
    }

    private static Set<String> loadSynsetIdLemmaPairs(Path filePath) {

        Set<String> pairs = new HashSet<>();
        if (filePath == null || !Files.exists(filePath)) {
            return pairs;
        }
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    continue;
                }
                String synsetId = node.path("synsetId").asText("").trim();
                String lemma    = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
                if (synsetId.isEmpty() || lemma.isEmpty()) {
                    continue;
                }
                pairs.add(synsetId + "|" + lemma);
            }
        } catch (IOException e) {
            System.out.println("compare-db: failed to read " + filePath + ": " + e.getMessage());
        }
        return pairs;
    }


}
