package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.nlp.morphodb.evaluation.ModelMetadata;
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
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/***************************************************************
 * Entry point that delegates morphological database generation
 * to word-type specific generators.
 ***************************************************************/
public class GenMorphoDB {

    private static final int DEFAULT_OLLAMA_PORT = 11434;
    private static final String ERROR_SOURCE_EXISTING = "existing_error";
    private static final String ERROR_SOURCE_MALFORMED_CONVERTED = "malformed_converted";
    private static final String REQUIRED_FIELD_RECOVERY_AUDIT_FILE = "compact-required-field-recovery-audit.jsonl";
    private static final String RECOVERY_METHOD_REQUIRED_FIELDS_ONLY = "required_fields_only";
    private static final String RECOVERY_METHOD_DROPPED_FIELD_PRUNED = "dropped_field_pruned";
    private static final String[] DROPPABLE_COMPACT_FIELDS =
            new String[]{"explanation", "usage", "definition", "message"};

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
        boolean getErrorPercent;
        boolean normalizeCategorical;
        boolean findFixGaps;
        boolean compareDb;
        String referenceDbPath;
        String verbStart;
        String verbEnd;
        String nounStart;
        String nounEnd;
        String orProviderPreferred;
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
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --normalize-categorical --db-path <path-to-morpho-db>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --normalize-categorical --all-models --db-path <path-to-parent-dir>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --getErrorPercent --db-path <path-to-morpho-db>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --getErrorPercent --all --db-path <path-to-parent-dir>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --db-path <path-to-morpho-db>");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB --add-lemma --all-models --db-path <path-to-parent-dir>");
        System.out.println("  Provider-aware usage:");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> \\");
        System.out.println("      --provider <ollama|openai|anthropic|claude|openai-compatible|google|gemini|openrouter> --model <model> \\");
        System.out.println("      [--ollama-port <port>] [--api-key <key>|--api-key-env <ENV_VAR>] [--base-url <url>] \\");
        System.out.println("      [--service-tier <auto|default|flex|priority>] [--cheap-prompt|--full-prompt] [--verbose] \\");
        System.out.println("      [--or-provider-preferred <provider>]");
        System.out.println("word-types supported: noun, verb, adjective, adverb, all");
        System.out.println("Maintenance flags:");
        System.out.println("  --compact      strip explanation/usage/definition and convert malformed rows into error rows");
        System.out.println("  --normalize-categorical   canonicalize categorical fields in place and mark invalid categorical labels");
        System.out.println("  --getErrorPercent   report existing error rows, malformed->error rows, parse failures, and total error percent");
        System.out.println("  --add-lemma    add the \"lemma\" field to existing records that lack it (no LLM calls)");
        System.out.println("  --dedup        remove duplicate entries, keeping the first occurrence per lemma");
        System.out.println("  --find-fix-gaps    compare each file against its WordNet POS set and list missing lemmas (requires SIGMA_HOME)");
        System.out.println("  --compare-db       compare synsetId/lemma pairs across DBs; requires --reference <ref-db-path> and --db-path [--all-models]");
        System.out.println("  --reference <path> (with --compare-db) reference DB path to compare against");
        System.out.println("                     (with --compact)    prefer matching synsetId from this DB during the dedup pre-pass");
        System.out.println("  --all, --all-models   treat --db-path as parent directory and run maintenance on each direct child model dir");
        System.out.println("  "
                + "You may pass --add-lemma, --compact, and --normalize-categorical together; when combined, --add-lemma runs first.");
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
        System.out.println("Noun parallelization flags (apply to all noun gen-functions):");
        System.out.println("  --noun-start <letter>   Begin noun processing at this letter, inclusive (e.g. a)");
        System.out.println("  --noun-end   <letter>   Stop noun processing at this letter, inclusive (e.g. k)");
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
                + "com.articulate.nlp.morphodb.GenMorphoDB --normalize-categorical --db-path /file/path/to/db/gpt-oss_20b");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --getErrorPercent --db-path /file/path/to/db/gpt-oss_20b");
        System.out.println("  java -Xmx40g -classpath $SIGMANLP_CP "
                + "com.articulate.nlp.morphodb.GenMorphoDB --getErrorPercent --all --db-path /file/path/to/db");
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
            if ("--or-provider-preferred".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --or-provider-preferred.");
                    return null;
                }
                options.orProviderPreferred = args[index + 1];
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
            if ("--getErrorPercent".equals(arg)) {
                options.getErrorPercent = true;
                index++;
                continue;
            }
            if ("--normalize-categorical".equals(arg)) {
                options.normalizeCategorical = true;
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
            if ("--noun-start".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --noun-start.");
                    return null;
                }
                options.nounStart = args[index + 1].toLowerCase();
                index += 2;
                continue;
            }
            if ("--noun-end".equals(arg)) {
                if (index + 1 >= args.length) {
                    System.err.println("Missing value for --noun-end.");
                    return null;
                }
                options.nounEnd = args[index + 1].toLowerCase();
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
            if ("--all-models".equals(arg) || "--all".equals(arg)) {
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

        int maintenanceCount = (options.compact ? 1 : 0)
                + (options.addLemma ? 1 : 0)
                + (options.dedup ? 1 : 0)
                + (options.getErrorPercent ? 1 : 0)
                + (options.normalizeCategorical ? 1 : 0);
        boolean maintenanceRequested = maintenanceCount > 0;
        if (options.allModels && !maintenanceRequested && !options.findFixGaps && !options.compareDb) {
            System.err.println("--all/--all-models is only valid for maintenance operations (--compact, --normalize-categorical, --getErrorPercent, --add-lemma, --dedup, --find-fix-gaps, or --compare-db).");
            return null;
        }
        if (options.getErrorPercent &&
                (options.compact || options.addLemma || options.dedup || options.normalizeCategorical
                        || options.findFixGaps || options.compareDb)) {
            System.err.println("--getErrorPercent must be run by itself (optionally with --all/--all-models and --db-path).");
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
        GenUtils.setOpenRouterProviderPreferred(options.orProviderPreferred);
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
        if ("openrouter".equals(options.provider) && options.orProviderPreferred != null) {
            System.out.println("Using OpenRouter preferred provider: " + options.orProviderPreferred);
        } else if (options.orProviderPreferred != null) {
            System.out.println("Ignoring --or-provider-preferred for provider: " + options.provider +
                    " (only applied when --provider openrouter).");
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
        boolean maintenanceRequested = cliOptions.compact
                || cliOptions.addLemma
                || cliOptions.dedup
                || cliOptions.getErrorPercent
                || cliOptions.normalizeCategorical;

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

        MorphoWordNetUtils.initWordNet();

        Map<String, Set<String>> nounSynsetHash      = MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.nounSynsetHash,      "noun set");
        Map<String, Set<String>> verbSynsetHash      = MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.verbSynsetHash,      "verb set");

        if (cliOptions.nounStart != null || cliOptions.nounEnd != null) {
            NavigableMap<String, Set<String>> navNouns = (TreeMap<String, Set<String>>) nounSynsetHash;
            if (cliOptions.nounStart != null) {
                navNouns = navNouns.tailMap(cliOptions.nounStart, true);
            }
            if (cliOptions.nounEnd != null) {
                String upperBound = cliOptions.nounEnd.substring(0, cliOptions.nounEnd.length() - 1)
                        + (char) (cliOptions.nounEnd.charAt(cliOptions.nounEnd.length() - 1) + 1);
                navNouns = navNouns.headMap(upperBound, false);
            }
            nounSynsetHash = navNouns;
            System.out.println("Noun set size (after range filter): " + nounSynsetHash.size());
        }

        if (cliOptions.verbStart != null || cliOptions.verbEnd != null) {
            NavigableMap<String, Set<String>> navVerbs = (TreeMap<String, Set<String>>) verbSynsetHash;
            if (cliOptions.verbStart != null) {
                navVerbs = navVerbs.tailMap(cliOptions.verbStart, true);
            }
            if (cliOptions.verbEnd != null) {
                String upperBound = cliOptions.verbEnd.substring(0, cliOptions.verbEnd.length() - 1)
                        + (char) (cliOptions.verbEnd.charAt(cliOptions.verbEnd.length() - 1) + 1);
                navVerbs = navVerbs.headMap(upperBound, false);
            }
            verbSynsetHash = navVerbs;
            System.out.println("Verb set size (after range filter): " + verbSynsetHash.size());
        }

        Map<String, Set<String>> adjectiveSynsetHash = MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.adjectiveSynsetHash, "adjective set");
        Map<String, Set<String>> adverbSynsetHash    = MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.adverbSynsetHash,    "adverb set");

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

        if (cliOptions.getErrorPercent) {
            runErrorPercentReport(morphoModelRoot, targetResolution);
            printSkippedMaintenanceTargets(targetResolution.skipped);
            System.out.println("Morphological DB maintenance complete for root: " + morphoModelRoot +
                    (cliOptions.allModels ? " (all-models mode)" : ""));
            return;
        }

        if (cliOptions.addLemma && cliOptions.compact && cliOptions.normalizeCategorical) {
            System.out.println("Maintenance flags provided; running add-lemma, compact cleanup, then categorical normalization.");
        } else if (cliOptions.addLemma && cliOptions.compact) {
            System.out.println("Both maintenance flags provided; running add-lemma first, then compact (dedup, compact, find-fix-gaps).");
        } else if (cliOptions.compact) {
            System.out.println("compact: full cleanup sequence: dedup, compact, find-fix-gaps.");
        } else if (cliOptions.normalizeCategorical) {
            System.out.println("normalize-categorical: canonicalizing categorical fields in place.");
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
                Path auditPath = target.modelRoot.resolve(REQUIRED_FIELD_RECOVERY_AUDIT_FILE);
                BufferedWriter requiredFieldRecoveryAuditWriter = null;
                try {
                    requiredFieldRecoveryAuditWriter = Files.newBufferedWriter(auditPath, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    System.out.println("Failed to create compact recovery audit file: " + auditPath + " (" + e.getMessage() + ")");
                    requiredFieldRecoveryAuditWriter = null;
                }
                for (MorphoFileSpec fileSpec : target.fileSpecs) {
                    CompactStats stats = compactMorphoFile(fileSpec, compactSummaryWriter, requiredFieldRecoveryAuditWriter);
                    modelTotal.add(stats);
                }
                if (requiredFieldRecoveryAuditWriter != null) {
                    try {
                        requiredFieldRecoveryAuditWriter.flush();
                        requiredFieldRecoveryAuditWriter.close();
                        System.out.println("compact: required-field recovery audit file=" + auditPath);
                    } catch (IOException e) {
                        System.out.println("Failed to finalize compact recovery audit file: " + auditPath + " (" + e.getMessage() + ")");
                    }
                }
                total.add(modelTotal);
                totalFilesProcessed += target.fileSpecs.size();
                int modelErrors = modelTotal.existingErrorRows + modelTotal.malformedConvertedRows + modelTotal.garbledRows;
                String modelSummary = String.format("compact MODEL: %s files=%d lines=%d stripped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d refused=%d garbled=%d)",
                        target.modelName,
                        target.fileSpecs.size(),
                        modelTotal.totalLines,
                        modelTotal.strippedFieldRows,
                        modelErrors,
                        formatErrorPct(modelErrors, modelTotal.totalLines),
                        modelTotal.existingErrorRows,
                        modelTotal.malformedConvertedRows,
                        modelTotal.refusedRows,
                        modelTotal.garbledRows);
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
            int totalErrors = total.existingErrorRows + total.malformedConvertedRows + total.garbledRows;
            String totalSummary = String.format("compact TOTAL: models=%d skipped=%d files=%d lines=%d stripped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d refused=%d garbled=%d)",
                    targetResolution.targets.size(),
                    targetResolution.skipped.size(),
                    totalFilesProcessed,
                    total.totalLines,
                    total.strippedFieldRows,
                    totalErrors,
                    formatErrorPct(totalErrors, total.totalLines),
                    total.existingErrorRows,
                    total.malformedConvertedRows,
                    total.refusedRows,
                    total.garbledRows);
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

        if (cliOptions.normalizeCategorical) {
            runCategoricalNormalizationReport(morphoModelRoot, targetResolution);
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
        final String[] requiredPayloadFields;
        final String[] allowedPersistedFields;
        final boolean requiresCompleteConjugation;
        final boolean allowRequiredFieldFragmentRecovery;

        MorphoFileSpec(Path path,
                       String sourceFieldName,
                       String[] requiredPayloadFields,
                       String[] allowedPersistedFields,
                       boolean requiresCompleteConjugation,
                       boolean allowRequiredFieldFragmentRecovery) {
            this.path = path;
            this.sourceFieldName = sourceFieldName;
            this.requiredPayloadFields = requiredPayloadFields == null ? new String[0] : requiredPayloadFields;
            this.allowedPersistedFields = allowedPersistedFields == null ? new String[0] : allowedPersistedFields;
            this.requiresCompleteConjugation = requiresCompleteConjugation;
            this.allowRequiredFieldFragmentRecovery = allowRequiredFieldFragmentRecovery;
        }
    }

    private static class CompactRecoveryResult {
        final com.fasterxml.jackson.databind.node.ObjectNode node;
        final String method;

        CompactRecoveryResult(com.fasterxml.jackson.databind.node.ObjectNode node, String method) {
            this.node = node;
            this.method = method;
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
        int rescuedRows;
        int refusedRows;
        int garbledRows;
        int conjugationCompleteRows;
        int conjugationPartialRows;

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
            rescuedRows += other.rescuedRows;
            refusedRows += other.refusedRows;
            garbledRows += other.garbledRows;
            conjugationCompleteRows += other.conjugationCompleteRows;
            conjugationPartialRows += other.conjugationPartialRows;
        }
    }

    private static class ErrorPercentStats {
        int totalLines;
        int existingErrorRows;
        int malformedConvertedRows;
        int parseFailures;
        int refusedRows;
        int garbledRows;
        int invalidCategorizationRows;
        int conjugationCompleteRows;
        int conjugationPartialRows;

        void add(ErrorPercentStats other) {
            if (other == null) return;
            totalLines += other.totalLines;
            existingErrorRows += other.existingErrorRows;
            malformedConvertedRows += other.malformedConvertedRows;
            parseFailures += other.parseFailures;
            refusedRows += other.refusedRows;
            garbledRows += other.garbledRows;
            invalidCategorizationRows += other.invalidCategorizationRows;
            conjugationCompleteRows += other.conjugationCompleteRows;
            conjugationPartialRows += other.conjugationPartialRows;
        }
    }

    private static class CategoricalNormalizationStats {
        int totalLines;
        int canonicalizedRows;
        int alreadyCanonicalRows;
        int invalidCategorizationRows;
        int parseFailures;
        int existingErrorRows;
        int refusedRows;
        int garbledRows;

        void add(CategoricalNormalizationStats other) {
            if (other == null) return;
            totalLines += other.totalLines;
            canonicalizedRows += other.canonicalizedRows;
            alreadyCanonicalRows += other.alreadyCanonicalRows;
            invalidCategorizationRows += other.invalidCategorizationRows;
            parseFailures += other.parseFailures;
            existingErrorRows += other.existingErrorRows;
            refusedRows += other.refusedRows;
            garbledRows += other.garbledRows;
        }
    }

    private static final String[] LATEX_CLASSIFICATION_COLUMN_PATHS = new String[]{
            "noun/IndefiniteArticles.txt",
            "noun/Countability.txt",
            "noun/Humanness.txt",
            "noun/NounAgentivity.txt",
            "noun/CollectiveNouns.txt",
            "verb/VerbValence.txt",
            "verb/VerbReflexive.txt",
            "verb/VerbCausativity.txt",
            "verb/VerbAchievementProcess.txt",
            "verb/VerbReciprocal.txt",
            "adjective/AdjectiveSemanticClasses.txt",
            "adverb/AdverbSemanticClasses.txt"
    };

    private static List<MorphoFileSpec> getMorphologicalDatabaseFileSpecs(Path modelRoot) {

        List<MorphoFileSpec> specs = new ArrayList<>();
        if (modelRoot == null) {
            return specs;
        }

        specs.add(flatSpec(modelRoot.resolve("noun").resolve("IndefiniteArticles.txt"),
                "noun",
                new String[]{"noun", "article"},
                new String[]{"synsetId", "lemma", "noun", "article", "article_pattern"}));
        specs.add(flatSpec(modelRoot.resolve("noun").resolve("Countability.txt"),
                "noun",
                new String[]{"noun", "countability"},
                new String[]{"synsetId", "lemma", "noun", "countability"}));
        specs.add(flatSpec(modelRoot.resolve("noun").resolve("Plurals.txt"),
                "singular",
                new String[]{"singular", "plural", "type"},
                new String[]{"synsetId", "lemma", "singular", "plural", "type", "plural_pattern"}));
        specs.add(flatSpec(modelRoot.resolve("noun").resolve("Humanness.txt"),
                "noun",
                new String[]{"noun", "classification"},
                new String[]{"synsetId", "lemma", "noun", "classification"}));
        specs.add(flatSpec(modelRoot.resolve("noun").resolve("NounAgentivity.txt"),
                "noun",
                new String[]{"noun", "agency", "agent_type"},
                new String[]{"synsetId", "lemma", "noun", "agency", "agent_type"}));
        specs.add(flatSpec(modelRoot.resolve("noun").resolve("CollectiveNouns.txt"),
                "noun",
                new String[]{"noun", "collective"},
                new String[]{"synsetId", "lemma", "noun", "collective"}));

        specs.add(flatSpec(modelRoot.resolve("verb").resolve("VerbValence.txt"),
                "verb",
                new String[]{"verb", "valence", "subtype", "semantic_roles"},
                new String[]{"synsetId", "lemma", "verb", "valence", "subtype", "semantic_roles"}));
        specs.add(flatSpec(modelRoot.resolve("verb").resolve("VerbReflexive.txt"),
                "verb",
                new String[]{"verb", "reflexivity"},
                new String[]{"synsetId", "lemma", "verb", "reflexivity"}));
        specs.add(flatSpec(modelRoot.resolve("verb").resolve("VerbCausativity.txt"),
                "verb",
                new String[]{"verb", "causativity"},
                new String[]{"synsetId", "lemma", "verb", "causativity"}));
        specs.add(flatSpec(modelRoot.resolve("verb").resolve("VerbAchievementProcess.txt"),
                "verb",
                new String[]{"verb", "aktionsart"},
                new String[]{"synsetId", "lemma", "verb", "aktionsart"}));
        specs.add(flatSpec(modelRoot.resolve("verb").resolve("VerbReciprocal.txt"),
                "verb",
                new String[]{"verb", "reciprocity"},
                new String[]{"synsetId", "lemma", "verb", "reciprocity"}));
        specs.add(new MorphoFileSpec(modelRoot.resolve("verb").resolve("VerbConjugations.txt"),
                "verb",
                new String[]{"verb", "tenses"},
                new String[]{"synsetId", "lemma", "verb", "status", "filledSlotCount", "totalSlotCount",
                        "filledTenseCount", "totalTenseCount", "regularity", "regularity_derived", "tenses", "notes"},
                true,
                false));

        specs.add(flatSpec(modelRoot.resolve("adjective").resolve("AdjectiveSemanticClasses.txt"),
                "adjective",
                new String[]{"adjective", "category"},
                new String[]{"synsetId", "lemma", "adjective", "category"}));
        specs.add(flatSpec(modelRoot.resolve("adverb").resolve("AdverbSemanticClasses.txt"),
                "adverb",
                new String[]{"adverb", "category"},
                new String[]{"synsetId", "lemma", "adverb", "category"}));

        return specs;
    }

    private static MorphoFileSpec flatSpec(Path path,
                                           String sourceFieldName,
                                           String[] requiredPayloadFields,
                                           String[] allowedPersistedFields) {

        return new MorphoFileSpec(path,
                sourceFieldName,
                requiredPayloadFields,
                allowedPersistedFields,
                false,
                true);
    }

    private static CompactStats compactMorphoFile(MorphoFileSpec fileSpec,
                                                  BufferedWriter compactSummaryWriter,
                                                  BufferedWriter requiredFieldRecoveryAuditWriter) {

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
                String originalLine = line;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                boolean rescuedMalformedLine = false;
                if (node == null) {
                    String recoveredSynset = extractJsonLikeField(line, "synsetId");
                    String recoveredLemma = recoverLemmaFromMalformedLine(line, fileSpec.sourceFieldName);
                    String recoveredSource = recoverSourceValueFromMalformedText(line, fileSpec.sourceFieldName, recoveredLemma);
                    CompactRecoveryResult recovery =
                            tryRecoverCompactRecord(fileSpec, line, stats, recoveredSynset, recoveredLemma, recoveredSource);
                    if (recovery != null) {
                        node = recovery.node;
                        rescuedMalformedLine = true;
                        if (RECOVERY_METHOD_REQUIRED_FIELDS_ONLY.equals(recovery.method)) {
                            writeRequiredFieldRecoveryAudit(requiredFieldRecoveryAuditWriter, fileSpec, line, node);
                        }
                    }
                }
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
                    errorNode.put("errorSource", ERROR_SOURCE_MALFORMED_CONVERTED);
                    errorNode.put("rawResponse", sanitizeForJsonField(line));
                    writer.write(GenMorphoUtils.serializeJsonLine(errorNode));
                    writer.newLine();
                    stats.malformedConvertedRows++;
                    continue;
                }
                stats.jsonLines++;
                node = normalizeCompactedNode(fileSpec, node, stats);
                if (node == null) {
                    continue;
                }
                String statusBeforeCanonicalization = node.path("status").asText("");
                if (fileSpec.requiresCompleteConjugation && !isErrorLikeStatus(statusBeforeCanonicalization)) {
                    VerbConjugationUtils.CanonicalizationResult conjugationResult =
                            canonicalizeVerbConjugationNode(fileSpec, node);
                    if (conjugationResult == null
                            || conjugationResult.record == null
                            || conjugationResult.completeness == VerbConjugationUtils.ConjugationCompleteness.ERROR) {
                        com.fasterxml.jackson.databind.node.ObjectNode errorNode =
                                buildCompactErrorNode(fileSpec, node, originalLine);
                        if (errorNode != null) {
                            stats.existingErrorRows++;
                            writer.write(GenMorphoUtils.serializeJsonLine(errorNode));
                            writer.newLine();
                        }
                        else {
                            stats.droppedMissingLemmaRows++;
                        }
                        continue;
                    }
                    node = conjugationResult.record;
                }
                if (rescuedMalformedLine) {
                    stats.rescuedRows++;
                }

                String status = node.path("status").asText("");
                if ("refused".equalsIgnoreCase(status)) {
                    stats.refusedRows++;
                }
                else if ("garbled".equalsIgnoreCase(status)) {
                    stats.garbledRows++;
                }
                else if (fileSpec.requiresCompleteConjugation && isConjugationPartialRow(node)) {
                    stats.conjugationPartialRows++;
                }
                else if ("error".equalsIgnoreCase(status)) {
                    // Case B: try to rescue valid JSON from rawResponse (with or without fences)
                    String rawResp = node.path("rawResponse").asText("").trim();
                    if (!rawResp.isEmpty()) {
                        CompactRecoveryResult recovery = tryRecoverCompactRecord(
                                fileSpec,
                                rawResp,
                                stats,
                                node.path("synsetId").asText("").trim(),
                                node.path("lemma").asText("").trim(),
                                node.path(fileSpec.sourceFieldName).asText("").trim());
                        if (recovery != null) {
                            if (RECOVERY_METHOD_REQUIRED_FIELDS_ONLY.equals(recovery.method)) {
                                writeRequiredFieldRecoveryAudit(requiredFieldRecoveryAuditWriter, fileSpec, rawResp, recovery.node);
                            }
                            writer.write(GenMorphoUtils.serializeJsonLine(recovery.node));
                            writer.newLine();
                            stats.rescuedRows++;
                            if (fileSpec.requiresCompleteConjugation) {
                                if (isConjugationPartialRow(recovery.node)) {
                                    stats.conjugationPartialRows++;
                                } else {
                                    stats.conjugationCompleteRows++;
                                }
                            }
                            continue;
                        }
                    }
                    String rawRespForClassify = node.path("rawResponse").asText("").trim();
                    if (!rawRespForClassify.isEmpty()) {
                        String classified = GenMorphoUtils.classifyNonJsonResponse(rawRespForClassify);
                        if (!"error".equals(classified)) {
                            node.put("status", classified);
                            node.remove("errorSource");
                            if ("refused".equals(classified)) {
                                stats.refusedRows++;
                            } else {
                                stats.garbledRows++;
                            }
                            writer.write(GenMorphoUtils.serializeJsonLine(node));
                            writer.newLine();
                            continue;
                        }
                    }
                    normalizeErrorSource(node);
                    if (ERROR_SOURCE_MALFORMED_CONVERTED.equals(node.path("errorSource").asText("").trim())) {
                        stats.malformedConvertedRows++;
                    }
                    else {
                        stats.existingErrorRows++;
                    }
                }
                else if (fileSpec.requiresCompleteConjugation) {
                    stats.conjugationCompleteRows++;
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
        int errorRows = stats.existingErrorRows + stats.malformedConvertedRows + stats.garbledRows;
        String fileSummary;
        if (fileSpec.requiresCompleteConjugation) {
            int conjugationErrorRows = computeConjugationErrorRows(stats);
            fileSummary = String.format(
                    "compact: %-30s lines=%d stripped=%d dropped=%d complete=%d completePct=%s partial=%d partialPct=%s errors=%d errorPct=%s (existing=%d malformed->error=%d rescued=%d refused=%d garbled=%d)",
                    filePath.getFileName(),
                    stats.totalLines,
                    stats.strippedFieldRows,
                    stats.droppedMissingLemmaRows,
                    stats.conjugationCompleteRows,
                    formatErrorPct(stats.conjugationCompleteRows, stats.totalLines),
                    stats.conjugationPartialRows,
                    formatErrorPct(stats.conjugationPartialRows, stats.totalLines),
                    conjugationErrorRows,
                    formatErrorPct(conjugationErrorRows, stats.totalLines),
                    stats.existingErrorRows,
                    stats.malformedConvertedRows,
                    stats.rescuedRows,
                    stats.refusedRows,
                    stats.garbledRows);
        } else {
            fileSummary = String.format("compact: %-30s lines=%d stripped=%d dropped=%d errors=%d errorPct=%s (existing=%d malformed->error=%d rescued=%d refused=%d garbled=%d)",
                    filePath.getFileName(),
                    stats.totalLines,
                    stats.strippedFieldRows,
                    stats.droppedMissingLemmaRows,
                    errorRows,
                    formatErrorPct(errorRows, stats.totalLines),
                    stats.existingErrorRows,
                    stats.malformedConvertedRows,
                    stats.rescuedRows,
                    stats.refusedRows,
                    stats.garbledRows);
        }
        printCompactLine(compactSummaryWriter, fileSummary);
        return stats;
    }

    private static CompactRecoveryResult tryRecoverCompactRecord(MorphoFileSpec fileSpec,
                                                                 String text,
                                                                 CompactStats stats,
                                                                 String fallbackSynsetId,
                                                                 String fallbackLemma,
                                                                 String fallbackSourceValue) {

        CompactRecoveryResult parsedRecovery = tryRecoverCompactJsonObject(text);
        if (parsedRecovery != null) {
            com.fasterxml.jackson.databind.node.ObjectNode finalized = finalizeRecoveredCompactNode(
                    fileSpec,
                    parsedRecovery.node,
                    stats,
                    fallbackSynsetId,
                    fallbackLemma,
                    fallbackSourceValue);
            if (finalized != null) {
                if (RECOVERY_METHOD_DROPPED_FIELD_PRUNED.equals(parsedRecovery.method) && stats != null) {
                    stats.strippedFieldRows++;
                }
                return new CompactRecoveryResult(finalized, parsedRecovery.method);
            }
        }
        if (fileSpec != null && fileSpec.allowRequiredFieldFragmentRecovery) {
            com.fasterxml.jackson.databind.node.ObjectNode fragmentRecovered =
                    recoverRequiredFieldsOnlyRecord(fileSpec, text, fallbackSynsetId, fallbackLemma, fallbackSourceValue);
            if (fragmentRecovered != null) {
                return new CompactRecoveryResult(fragmentRecovered, RECOVERY_METHOD_REQUIRED_FIELDS_ONLY);
            }
        }
        return null;
    }

    private static CompactRecoveryResult tryRecoverCompactJsonObject(String text) {

        if (text == null) {
            return null;
        }
        String normalized = GenMorphoUtils.fixInvalidJsonEscapes(
                GenMorphoUtils.stripMarkdownFences(text.trim()));
        com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(normalized);
        if (node != null) {
            return new CompactRecoveryResult(node, "json_object");
        }
        String extracted = GenUtils.extractFirstJsonObject(normalized);
        if (extracted == null || extracted.trim().isEmpty()) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ObjectNode parsed = GenMorphoUtils.parseJsonObjectLine(extracted);
        if (parsed != null) {
            return new CompactRecoveryResult(parsed, "extracted_json_object");
        }
        String repaired = repairOverEscapedQuotedStrings(extracted);
        if (!repaired.equals(extracted)) {
            com.fasterxml.jackson.databind.node.ObjectNode repairedNode = GenMorphoUtils.parseJsonObjectLine(repaired);
            if (repairedNode != null) {
                return new CompactRecoveryResult(repairedNode, "repaired_over_escaped_quotes");
            }
        }
        String pruned = pruneDroppedFieldsFromExtractedObject(extracted);
        if (pruned == null || pruned.equals(extracted)) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ObjectNode prunedNode = GenMorphoUtils.parseJsonObjectLine(pruned);
        if (prunedNode != null) {
            return new CompactRecoveryResult(prunedNode, RECOVERY_METHOD_DROPPED_FIELD_PRUNED);
        }
        String repairedPruned = repairOverEscapedQuotedStrings(pruned);
        if (repairedPruned.equals(pruned)) {
            return null;
        }
        prunedNode = GenMorphoUtils.parseJsonObjectLine(repairedPruned);
        if (prunedNode != null) {
            return new CompactRecoveryResult(prunedNode, RECOVERY_METHOD_DROPPED_FIELD_PRUNED);
        }
        return null;
    }

    private static String repairOverEscapedQuotedStrings(String text) {

        if (text == null || text.isEmpty()) {
            return text;
        }
        String repaired = text;
        while (repaired.contains("\\\\\"")) {
            String next = repaired.replace("\\\\\"", "\\\"");
            if (next.equals(repaired)) {
                break;
            }
            repaired = next;
        }
        return repaired;
    }

    private static String pruneDroppedFieldsFromExtractedObject(String text) {

        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String pruned = text;
        boolean changed = false;
        for (String fieldName : DROPPABLE_COMPACT_FIELDS) {
            String next = removeTopLevelField(pruned, fieldName);
            if (!pruned.equals(next)) {
                pruned = next;
                changed = true;
            }
        }
        return changed ? cleanupPrunedJsonObject(pruned) : null;
    }

    private static String removeTopLevelField(String text, String fieldName) {

        if (text == null || text.isEmpty() || fieldName == null || fieldName.isEmpty()) {
            return text;
        }
        int fieldStart = findTopLevelFieldStart(text, fieldName);
        if (fieldStart < 0) {
            return text;
        }
        int fieldNameEnd = fieldStart + fieldName.length() + 2;
        int colonIndex = skipWhitespace(text, fieldNameEnd);
        if (colonIndex < 0 || colonIndex >= text.length() || text.charAt(colonIndex) != ':') {
            return text;
        }
        int valueStart = skipWhitespace(text, colonIndex + 1);
        if (valueStart < 0 || valueStart >= text.length()) {
            return text;
        }
        int valueEnd = findBestEffortJsonValueEnd(text, valueStart);
        if (valueEnd <= valueStart) {
            return text;
        }

        int removalStart = fieldStart;
        int prefixIndex = skipWhitespaceBackward(text, fieldStart - 1);
        if (prefixIndex >= 0 && text.charAt(prefixIndex) == ',') {
            removalStart = prefixIndex;
        }
        int removalEnd = valueEnd;
        if (removalStart == fieldStart) {
            int suffixIndex = skipWhitespace(text, valueEnd);
            if (suffixIndex >= 0 && suffixIndex < text.length() && text.charAt(suffixIndex) == ',') {
                removalEnd = suffixIndex + 1;
            }
        }
        return text.substring(0, removalStart) + text.substring(removalEnd);
    }

    private static int findTopLevelFieldStart(String text, String fieldName) {

        if (text == null || fieldName == null) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                if (depth == 1 && text.startsWith("\"" + fieldName + "\"", i)) {
                    int afterField = skipWhitespace(text, i + fieldName.length() + 2);
                    if (afterField >= 0 && afterField < text.length() && text.charAt(afterField) == ':') {
                        return i;
                    }
                }
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            }
            else if (c == '}' || c == ']') {
                depth = Math.max(0, depth - 1);
            }
        }
        return -1;
    }

    private static int findBestEffortJsonValueEnd(String text, int valueStart) {

        if (text == null || valueStart < 0 || valueStart >= text.length()) {
            return -1;
        }
        char first = text.charAt(valueStart);
        if (first == '"') {
            int stringEnd = findJsonStringEnd(text, valueStart);
            if (stringEnd > valueStart) {
                int afterString = skipWhitespace(text, stringEnd);
                if (afterString >= 0 && afterString < text.length()) {
                    char boundaryChar = text.charAt(afterString);
                    if (boundaryChar == ',' || boundaryChar == '}' || boundaryChar == ']') {
                        return stringEnd;
                    }
                } else if (stringEnd == text.length()) {
                    return stringEnd;
                }
            }
            int boundary = findLikelyFieldBoundary(text, valueStart + 1);
            if (boundary >= 0) {
                return boundary;
            }
            return text.length();
        }
        if (first == '{' || first == '[') {
            int matched = findMatchingBracketEnd(text, valueStart, first, first == '{' ? '}' : ']');
            if (matched > valueStart) {
                return matched;
            }
        }
        int boundary = findLikelyFieldBoundary(text, valueStart);
        return boundary >= 0 ? boundary : text.length();
    }

    private static int findJsonStringEnd(String text, int startQuote) {

        boolean escaping = false;
        for (int i = startQuote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
        }
        return -1;
    }

    private static int findMatchingBracketEnd(String text, int startIndex, char openChar, char closeChar) {

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == openChar) {
                depth++;
                continue;
            }
            if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private static int findLikelyFieldBoundary(String text, int fromIndex) {

        for (int i = fromIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',') {
                int next = skipWhitespace(text, i + 1);
                if (next >= 0 && next < text.length() && text.charAt(next) == '"') {
                    int quoteEnd = findJsonStringEnd(text, next);
                    if (quoteEnd > next) {
                        int colon = skipWhitespace(text, quoteEnd);
                        if (colon >= 0 && colon < text.length() && text.charAt(colon) == ':') {
                            return i;
                        }
                    }
                }
            }
            if (c == '}') {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String text, int startIndex) {

        if (text == null) {
            return -1;
        }
        for (int i = Math.max(0, startIndex); i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int skipWhitespaceBackward(String text, int startIndex) {

        if (text == null) {
            return -1;
        }
        for (int i = Math.min(startIndex, text.length() - 1); i >= 0; i--) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String cleanupPrunedJsonObject(String text) {

        if (text == null) {
            return null;
        }
        String cleaned = text;
        cleaned = cleaned.replaceAll(",\\s*}", "}");
        cleaned = cleaned.replaceAll("\\{\\s*,", "{");
        return cleaned;
    }

    private static boolean isSuccessfulRow(com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (node == null) {
            return false;
        }
        return node.path("status").asText("").trim().isEmpty();
    }

    private static VerbConjugationUtils.CanonicalizationResult canonicalizeVerbConjugationNode(MorphoFileSpec fileSpec,
                                                                                                com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (fileSpec == null || node == null || !fileSpec.requiresCompleteConjugation) {
            return new VerbConjugationUtils.CanonicalizationResult(
                    VerbConjugationUtils.ConjugationCompleteness.ERROR,
                    node,
                    0,
                    VerbConjugationUtils.TOTAL_CANONICAL_SLOT_COUNT,
                    0,
                    VerbConjugationUtils.TOTAL_CANONICAL_TENSE_COUNT);
        }
        return VerbConjugationUtils.canonicalizeRecord(
                node,
                node.path("synsetId").asText("").trim(),
                node.path("lemma").asText("").trim(),
                node.path(fileSpec.sourceFieldName).asText("").trim());
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode buildCompactErrorNode(MorphoFileSpec fileSpec,
                                                                                        com.fasterxml.jackson.databind.node.ObjectNode node,
                                                                                        String rawResponse) {

        if (fileSpec == null || node == null) {
            return null;
        }
        String synsetId = node.path("synsetId").asText("").trim();
        String lemma = recoverLemmaFromNode(node, fileSpec.sourceFieldName);
        if (synsetId.isEmpty() || lemma.isEmpty()) {
            return null;
        }
        String sourceValue = node.path(fileSpec.sourceFieldName).asText("").trim();
        if (sourceValue.isEmpty()) {
            sourceValue = lemma;
        }
        com.fasterxml.jackson.databind.node.ObjectNode errorNode = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        errorNode.put("synsetId", synsetId);
        errorNode.put("lemma", lemma);
        errorNode.put(fileSpec.sourceFieldName, sourceValue);
        errorNode.put("status", "error");
        errorNode.put("errorSource", ERROR_SOURCE_EXISTING);
        errorNode.put("rawResponse", sanitizeForJsonField(rawResponse));
        return errorNode;
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode finalizeRecoveredCompactNode(MorphoFileSpec fileSpec,
                                                                                                com.fasterxml.jackson.databind.node.ObjectNode node,
                                                                                                CompactStats stats,
                                                                                                String fallbackSynsetId,
                                                                                                String fallbackLemma,
                                                                                                String fallbackSourceValue) {

        if (fileSpec == null || node == null) {
            return null;
        }
        node.remove("status");
        node.remove("errorSource");
        node.remove("rawResponse");
        if (node.path("synsetId").asText("").trim().isEmpty() && fallbackSynsetId != null && !fallbackSynsetId.trim().isEmpty()) {
            node.put("synsetId", fallbackSynsetId.trim());
        }
        if (node.path("lemma").asText("").trim().isEmpty() && fallbackLemma != null && !fallbackLemma.trim().isEmpty()) {
            node.put("lemma", fallbackLemma.trim());
        }
        if (node.path(fileSpec.sourceFieldName).asText("").trim().isEmpty()
                && fallbackSourceValue != null && !fallbackSourceValue.trim().isEmpty()) {
            node.put(fileSpec.sourceFieldName, fallbackSourceValue.trim());
        }
        node = normalizeCompactedNode(fileSpec, node, stats);
        if (node == null) {
            return null;
        }
        if (node.path("synsetId").asText("").trim().isEmpty()) {
            return null;
        }
        if (fileSpec.requiresCompleteConjugation) {
            VerbConjugationUtils.CanonicalizationResult result = canonicalizeVerbConjugationNode(fileSpec, node);
            return result == null ? null : result.record;
        }
        if (!hasRequiredPayloadFields(fileSpec, node)) {
            return null;
        }
        if (fileSpec.allowRequiredFieldFragmentRecovery) {
            Map<String, String> extracted = new HashMap<>();
            for (String fieldName : fileSpec.requiredPayloadFields) {
                extracted.put(fieldName, node.path(fieldName).asText(""));
            }
            String sourceValue = node.path(fileSpec.sourceFieldName).asText("").trim();
            com.fasterxml.jackson.databind.node.ObjectNode rebuilt = rebuildFlatSchemaRecoveredNode(
                    fileSpec,
                    node.path("synsetId").asText("").trim(),
                    node.path("lemma").asText("").trim(),
                    sourceValue,
                    extracted);
            if (rebuilt != null) {
                return rebuilt;
            }
            return null;
        }
        return node;
    }

    private static boolean isConjugationPartialRow(com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (node == null) {
            return false;
        }
        return "partial".equalsIgnoreCase(node.path("status").asText(""));
    }

    private static boolean isErrorLikeStatus(String status) {

        if (status == null) {
            return false;
        }
        String lowered = status.trim().toLowerCase(Locale.ROOT);
        return "error".equals(lowered) || "refused".equals(lowered) || "garbled".equals(lowered);
    }

    private static int computeConjugationErrorRows(CompactStats stats) {

        if (stats == null) {
            return 0;
        }
        return Math.max(0, stats.totalLines - stats.conjugationCompleteRows - stats.conjugationPartialRows);
    }

    private static int computeConjugationErrorRows(ErrorPercentStats stats) {

        if (stats == null) {
            return 0;
        }
        return Math.max(0, stats.totalLines - stats.conjugationCompleteRows - stats.conjugationPartialRows);
    }

    private static boolean hasRequiredPayloadFields(MorphoFileSpec fileSpec,
                                                    com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (fileSpec == null || node == null) {
            return false;
        }
        for (String fieldName : fileSpec.requiredPayloadFields) {
            if (fieldName == null || fieldName.trim().isEmpty()) {
                continue;
            }
            com.fasterxml.jackson.databind.JsonNode fieldValue = node.get(fieldName);
            if (fieldValue == null || fieldValue.isNull()) {
                return false;
            }
            if (fieldValue.isTextual() && fieldValue.asText("").trim().isEmpty()) {
                return false;
            }
            if (fieldValue.isArray() && fieldValue.size() == 0) {
                return false;
            }
        }
        String lemma = node.path("lemma").asText("").trim();
        return !lemma.isEmpty();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode recoverRequiredFieldsOnlyRecord(MorphoFileSpec fileSpec,
                                                                                                  String text,
                                                                                                  String fallbackSynsetId,
                                                                                                  String fallbackLemma,
                                                                                                  String fallbackSourceValue) {

        if (fileSpec == null || text == null || text.trim().isEmpty() || !fileSpec.allowRequiredFieldFragmentRecovery) {
            return null;
        }
        String normalized = GenMorphoUtils.fixInvalidJsonEscapes(GenMorphoUtils.stripMarkdownFences(text.trim()));
        Map<String, String> extracted = new HashMap<>();
        for (String fieldName : fileSpec.requiredPayloadFields) {
            String value = extractTopLevelJsonStringField(normalized, fieldName);
            if (value == null) {
                return null;
            }
            extracted.put(fieldName, value);
        }
        String synsetId = extractTopLevelJsonStringField(normalized, "synsetId");
        if (synsetId == null || synsetId.trim().isEmpty()) {
            synsetId = fallbackSynsetId == null ? "" : fallbackSynsetId.trim();
        }
        if (synsetId.isEmpty()) {
            return null;
        }
        String sourceValue = extracted.get(fileSpec.sourceFieldName);
        if ((sourceValue == null || sourceValue.trim().isEmpty()) && fallbackSourceValue != null) {
            sourceValue = fallbackSourceValue.trim();
        }
        if (sourceValue == null || sourceValue.trim().isEmpty()) {
            return null;
        }
        String lemma = extractTopLevelJsonStringField(normalized, "lemma");
        if (lemma == null || lemma.trim().isEmpty()) {
            lemma = fallbackLemma;
        }
        lemma = GenMorphoUtils.normalizeLemma(lemma == null ? sourceValue : lemma);
        if (lemma.isEmpty()) {
            lemma = GenMorphoUtils.normalizeLemma(sourceValue);
        }
        if (lemma.isEmpty()) {
            return null;
        }
        return rebuildFlatSchemaRecoveredNode(fileSpec, synsetId, lemma, sourceValue, extracted);
    }

    private static String extractTopLevelJsonStringField(String text, String fieldName) {

        if (text == null || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        int fieldStart = findTopLevelFieldStart(text, fieldName);
        if (fieldStart < 0) {
            return null;
        }
        int fieldNameEnd = fieldStart + fieldName.length() + 2;
        int colonIndex = skipWhitespace(text, fieldNameEnd);
        if (colonIndex < 0 || colonIndex >= text.length() || text.charAt(colonIndex) != ':') {
            return null;
        }
        int valueStart = skipWhitespace(text, colonIndex + 1);
        if (valueStart < 0 || valueStart >= text.length() || text.charAt(valueStart) != '"') {
            return null;
        }
        int valueEnd = findJsonStringEnd(text, valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        String encoded = text.substring(valueStart + 1, valueEnd - 1);
        if (encoded == null) {
            return null;
        }
        return decodeJsonStringValue(encoded);
    }

    private static String decodeJsonStringValue(String encodedValue) {

        if (encodedValue == null) {
            return null;
        }
        try {
            return GenMorphoUtils.JSON_MAPPER.readValue("\"" + encodedValue + "\"", String.class);
        } catch (IOException e) {
            return null;
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode rebuildFlatSchemaRecoveredNode(MorphoFileSpec fileSpec,
                                                                                                  String synsetId,
                                                                                                  String lemma,
                                                                                                  String sourceValue,
                                                                                                  Map<String, String> extracted) {

        if (fileSpec == null || synsetId == null || synsetId.trim().isEmpty() || lemma == null || lemma.trim().isEmpty()) {
            return null;
        }
        String fileName = fileSpec.path == null || fileSpec.path.getFileName() == null
                ? ""
                : fileSpec.path.getFileName().toString();
        return MorphoFlatSchemaUtils.buildRecoveredFlatRecord(
                fileName,
                synsetId,
                lemma,
                sourceValue,
                extracted);
    }

    private static String recoverSourceValueFromMalformedText(String text,
                                                              String sourceFieldName,
                                                              String recoveredLemma) {

        String sourceValue = extractJsonLikeField(text, sourceFieldName);
        if (sourceValue != null && !sourceValue.trim().isEmpty()) {
            return sourceValue.trim();
        }
        return recoveredLemma == null ? "" : recoveredLemma.trim();
    }

    private static void writeRequiredFieldRecoveryAudit(BufferedWriter auditWriter,
                                                        MorphoFileSpec fileSpec,
                                                        String rawResponse,
                                                        com.fasterxml.jackson.databind.node.ObjectNode recoveredNode) {

        if (auditWriter == null || fileSpec == null || recoveredNode == null) {
            return;
        }
        com.fasterxml.jackson.databind.node.ObjectNode auditNode = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        auditNode.put("file", fileSpec.path == null ? "" : fileSpec.path.toString());
        auditNode.put("synsetId", recoveredNode.path("synsetId").asText(""));
        auditNode.put("lemma", recoveredNode.path("lemma").asText(""));
        auditNode.put("recoveryMethod", RECOVERY_METHOD_REQUIRED_FIELDS_ONLY);
        com.fasterxml.jackson.databind.node.ArrayNode requiredFields = auditNode.putArray("requiredFields");
        for (String fieldName : fileSpec.requiredPayloadFields) {
            requiredFields.add(fieldName);
        }
        auditNode.put("rawResponse", rawResponse == null ? "" : rawResponse);
        auditNode.set("recoveredRow", recoveredNode.deepCopy());
        try {
            auditWriter.write(GenMorphoUtils.serializeJsonLine(auditNode));
            auditWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to write compact required-field recovery audit entry: " + e.getMessage());
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode normalizeCompactedNode(MorphoFileSpec fileSpec,
                                                                                         com.fasterxml.jackson.databind.node.ObjectNode node,
                                                                                         CompactStats stats) {

        if (fileSpec == null || node == null) {
            return null;
        }
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
        if (removedAny && stats != null) {
            stats.strippedFieldRows++;
        }

        String lemma = GenMorphoUtils.normalizeLemma(node.path("lemma").asText(""));
        if (lemma.isEmpty()) {
            String sourceValue = recoverLemmaFromNode(node, fileSpec.sourceFieldName);
            if (!sourceValue.isEmpty()) {
                node.put("lemma", sourceValue);
                lemma = sourceValue;
                if (stats != null) {
                    stats.lemmaAddedRows++;
                }
            }
            else {
                if (stats != null) {
                    stats.droppedMissingLemmaRows++;
                }
                return null;
            }
        } else {
            node.put("lemma", lemma);
        }
        return GenMorphoUtils.prependSynsetIdAndLemma(
                node,
                node.path("synsetId").asText(""),
                lemma);
    }

    private static void normalizeErrorSource(com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (node == null) {
            return;
        }
        if (!"error".equalsIgnoreCase(node.path("status").asText(""))) {
            return;
        }
        String errorSource = node.path("errorSource").asText("").trim();
        if (ERROR_SOURCE_MALFORMED_CONVERTED.equals(errorSource)) {
            return;
        }
        node.put("errorSource", ERROR_SOURCE_EXISTING);
    }

    private static void printErrorPercentLine(BufferedWriter summaryWriter, String line) {

        if (line == null) {
            return;
        }
        System.out.println(line);
        if (summaryWriter == null) {
            return;
        }
        try {
            summaryWriter.write(line);
            summaryWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to write error percent summary line: " + e.getMessage());
        }
    }

    private static void printCategoricalNormalizationLine(BufferedWriter summaryWriter, String line) {

        if (line == null) {
            return;
        }
        System.out.println(line);
        if (summaryWriter == null) {
            return;
        }
        try {
            summaryWriter.write(line);
            summaryWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to write categorical normalization summary line: " + e.getMessage());
        }
    }

    private static void runCategoricalNormalizationReport(Path morphoModelRoot,
                                                          MaintenanceTargetResolution targetResolution) {

        Path summaryPath = morphoModelRoot.resolve("categorical-normalization-summary.txt");
        BufferedWriter summaryWriter = null;
        try {
            summaryWriter = Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8);
            summaryWriter.write("categorical-normalization-summary");
            summaryWriter.newLine();
            summaryWriter.write("root\t" + morphoModelRoot);
            summaryWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to create categorical normalization summary file: " + summaryPath + " (" + e.getMessage() + ")");
            summaryWriter = null;
        }

        CategoricalNormalizationStats total = new CategoricalNormalizationStats();
        int totalFilesProcessed = 0;
        for (MaintenanceTarget target : targetResolution.targets) {
            CategoricalNormalizationStats modelTotal = new CategoricalNormalizationStats();
            int modelFilesProcessed = 0;
            for (MorphoFileSpec fileSpec : target.fileSpecs) {
                MorphoCategoricalSchema.CategorySpec categorySpec = categoricalSpecForFile(fileSpec);
                if (categorySpec == null) {
                    continue;
                }
                CategoricalNormalizationStats stats = normalizeCategoricalFile(fileSpec, categorySpec, summaryWriter);
                modelTotal.add(stats);
                modelFilesProcessed++;
            }
            if (modelFilesProcessed == 0) {
                continue;
            }
            total.add(modelTotal);
            totalFilesProcessed += modelFilesProcessed;
            String modelSummary = String.format(
                    "normalize-categorical MODEL: %s files=%d lines=%d canonicalized=%d alreadyCanonical=%d invalidCategorization=%d parseFailures=%d (existing=%d refused=%d garbled=%d)",
                    target.modelName,
                    modelFilesProcessed,
                    modelTotal.totalLines,
                    modelTotal.canonicalizedRows,
                    modelTotal.alreadyCanonicalRows,
                    modelTotal.invalidCategorizationRows,
                    modelTotal.parseFailures,
                    modelTotal.existingErrorRows,
                    modelTotal.refusedRows,
                    modelTotal.garbledRows);
            printCategoricalNormalizationLine(summaryWriter, modelSummary);
        }

        String totalSummary = String.format(
                "normalize-categorical TOTAL: models=%d skipped=%d files=%d lines=%d canonicalized=%d alreadyCanonical=%d invalidCategorization=%d parseFailures=%d (existing=%d refused=%d garbled=%d)",
                targetResolution.targets.size(),
                targetResolution.skipped.size(),
                totalFilesProcessed,
                total.totalLines,
                total.canonicalizedRows,
                total.alreadyCanonicalRows,
                total.invalidCategorizationRows,
                total.parseFailures,
                total.existingErrorRows,
                total.refusedRows,
                total.garbledRows);
        printCategoricalNormalizationLine(summaryWriter, totalSummary);

        if (summaryWriter != null) {
            try {
                summaryWriter.flush();
                summaryWriter.close();
                System.out.println("normalize-categorical: summary file=" + summaryPath);
            } catch (IOException e) {
                System.out.println("Failed to finalize categorical normalization summary file: " + summaryPath + " (" + e.getMessage() + ")");
            }
        }
    }

    private static CategoricalNormalizationStats normalizeCategoricalFile(MorphoFileSpec fileSpec,
                                                                          MorphoCategoricalSchema.CategorySpec categorySpec,
                                                                          BufferedWriter summaryWriter) {

        CategoricalNormalizationStats stats = new CategoricalNormalizationStats();
        if (fileSpec == null || categorySpec == null || fileSpec.path == null || !Files.exists(fileSpec.path)) {
            return stats;
        }
        Path filePath = fileSpec.path;
        Path tmp = createTempFileInSameDirectory(filePath);
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                stats.totalLines++;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    stats.parseFailures++;
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                String status = node.path("status").asText("");
                if ("error".equalsIgnoreCase(status)) {
                    stats.existingErrorRows++;
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                if ("refused".equalsIgnoreCase(status)) {
                    stats.refusedRows++;
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                if ("garbled".equalsIgnoreCase(status)) {
                    stats.garbledRows++;
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                String fieldName = categorySpec.getFieldName();
                String rawValue = node.path(fieldName).asText("");
                String canonicalValue = categorySpec.canonicalizeStoredValue(rawValue);
                if (!canonicalValue.isEmpty()) {
                    boolean changed = hasInvalidCategorizationMarker(node, fieldName) || !canonicalValue.equals(rawValue);
                    node.put(fieldName, canonicalValue);
                    clearInvalidCategorizationMarkers(node);
                    if (changed) {
                        stats.canonicalizedRows++;
                    } else {
                        stats.alreadyCanonicalRows++;
                    }
                    writer.write(GenMorphoUtils.serializeJsonLine(node));
                    writer.newLine();
                    continue;
                }

                markInvalidCategorization(node, fieldName, rawValue);
                stats.invalidCategorizationRows++;
                writer.write(GenMorphoUtils.serializeJsonLine(node));
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            printCategoricalNormalizationLine(summaryWriter,
                    "Failed while normalizing categorical file " + filePath + ": " + e.getMessage());
            return stats;
        }

        overwriteFile(filePath, tmp);
        String fileSummary = String.format(
                "normalize-categorical: %-30s lines=%d canonicalized=%d alreadyCanonical=%d invalidCategorization=%d parseFailures=%d (existing=%d refused=%d garbled=%d)",
                filePath.getFileName(),
                stats.totalLines,
                stats.canonicalizedRows,
                stats.alreadyCanonicalRows,
                stats.invalidCategorizationRows,
                stats.parseFailures,
                stats.existingErrorRows,
                stats.refusedRows,
                stats.garbledRows);
        printCategoricalNormalizationLine(summaryWriter, fileSummary);
        return stats;
    }

    private static MorphoCategoricalSchema.CategorySpec categoricalSpecForFile(MorphoFileSpec fileSpec) {

        if (fileSpec == null || fileSpec.path == null) {
            return null;
        }
        return MorphoCategoricalSchema.getByRelativePath(relativeMorphoPath(null, fileSpec.path));
    }

    private static boolean hasInvalidCategorizationMarker(com.fasterxml.jackson.databind.node.ObjectNode node,
                                                          String expectedFieldName) {

        if (node == null) {
            return false;
        }
        return MorphoCategoricalSchema.isInvalidCategorizationForField(
                node.path(MorphoCategoricalSchema.CATEGORIZATION_STATUS_FIELD).asText(""),
                node.path(MorphoCategoricalSchema.INVALID_CATEGORIZATION_FIELD_FIELD).asText(""),
                expectedFieldName
        );
    }

    private static void markInvalidCategorization(com.fasterxml.jackson.databind.node.ObjectNode node,
                                                  String fieldName,
                                                  String rawValue) {

        if (node == null) {
            return;
        }
        node.put(MorphoCategoricalSchema.CATEGORIZATION_STATUS_FIELD,
                MorphoCategoricalSchema.INVALID_CATEGORIZATION_STATUS);
        node.put(MorphoCategoricalSchema.INVALID_CATEGORIZATION_FIELD_FIELD, fieldName == null ? "" : fieldName);
        node.put(MorphoCategoricalSchema.INVALID_CATEGORIZATION_RAW_FIELD, rawValue == null ? "" : rawValue);
    }

    private static void clearInvalidCategorizationMarkers(com.fasterxml.jackson.databind.node.ObjectNode node) {

        if (node == null) {
            return;
        }
        node.remove(MorphoCategoricalSchema.CATEGORIZATION_STATUS_FIELD);
        node.remove(MorphoCategoricalSchema.INVALID_CATEGORIZATION_FIELD_FIELD);
        node.remove(MorphoCategoricalSchema.INVALID_CATEGORIZATION_RAW_FIELD);
    }

    private static void runErrorPercentReport(Path morphoModelRoot,
                                              MaintenanceTargetResolution targetResolution) {

        Path summaryPath = morphoModelRoot.resolve("error-percent-summary.txt");
        BufferedWriter summaryWriter = null;
        try {
            summaryWriter = Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8);
            summaryWriter.write("error-percent-summary");
            summaryWriter.newLine();
            summaryWriter.write("root\t" + morphoModelRoot);
            summaryWriter.newLine();
        } catch (IOException e) {
            System.out.println("Failed to create error percent summary file: " + summaryPath + " (" + e.getMessage() + ")");
            summaryWriter = null;
        }

        ErrorPercentStats total = new ErrorPercentStats();
        Map<String, Map<String, Double>> classificationFeaturePctsByModel = new HashMap<>();
        Map<String, Double> latexOverallPctsByModel = new HashMap<>();
        Map<String, Double> pluralErrorPctsByModel = new HashMap<>();
        Map<String, Double> conjugationCompletePctsByModel = new HashMap<>();
        Map<String, Double> conjugationPartialPctsByModel = new HashMap<>();
        Map<String, Double> conjugationErrorPctsByModel = new HashMap<>();
        int totalFilesProcessed = 0;
        for (MaintenanceTarget target : targetResolution.targets) {
            ErrorPercentStats modelTotal = new ErrorPercentStats();
            for (MorphoFileSpec fileSpec : target.fileSpecs) {
                ErrorPercentStats stats = getErrorPercentForMorphoFile(fileSpec, summaryWriter);
                modelTotal.add(stats);
                String relativePath = relativeMorphoPath(target.modelRoot, fileSpec.path);
                if ("noun/Plurals.txt".equals(relativePath)) {
                    pluralErrorPctsByModel.put(target.modelName, computeErrorPctValue(stats));
                } else if ("verb/VerbConjugations.txt".equals(relativePath)) {
                    conjugationCompletePctsByModel.put(target.modelName, computeConjugationCompletePctValue(stats));
                    conjugationPartialPctsByModel.put(target.modelName, computeConjugationPartialPctValue(stats));
                    conjugationErrorPctsByModel.put(target.modelName, computeConjugationErrorPctValue(stats));
                } else {
                    classificationFeaturePctsByModel
                            .computeIfAbsent(target.modelName, ignored -> new HashMap<>())
                            .put(relativePath, computeErrorPctValue(stats));
                }
            }
            total.add(modelTotal);
            totalFilesProcessed += target.fileSpecs.size();
            int modelErrors = computeTotalErrorRows(modelTotal);
            String modelSummary = String.format(
                    "error-percent MODEL: %s files=%d lines=%d errors=%d errorPct=%s (existing=%d malformed->error=%d parseFailures=%d refused=%d garbled=%d invalidCategorization=%d)",
                    target.modelName,
                    target.fileSpecs.size(),
                    modelTotal.totalLines,
                    modelErrors,
                    formatErrorPct(modelErrors, modelTotal.totalLines),
                    modelTotal.existingErrorRows,
                    modelTotal.malformedConvertedRows,
                    modelTotal.parseFailures,
                    modelTotal.refusedRows,
                    modelTotal.garbledRows,
                    modelTotal.invalidCategorizationRows);
            printErrorPercentLine(summaryWriter, modelSummary);
            latexOverallPctsByModel.put(target.modelName, computeErrorPctValue(modelTotal));
        }

        int totalErrors = computeTotalErrorRows(total);
        String totalSummary = String.format(
                "error-percent TOTAL: models=%d skipped=%d files=%d lines=%d errors=%d errorPct=%s (existing=%d malformed->error=%d parseFailures=%d refused=%d garbled=%d invalidCategorization=%d)",
                targetResolution.targets.size(),
                targetResolution.skipped.size(),
                totalFilesProcessed,
                total.totalLines,
                totalErrors,
                formatErrorPct(totalErrors, total.totalLines),
                total.existingErrorRows,
                total.malformedConvertedRows,
                total.parseFailures,
                total.refusedRows,
                total.garbledRows,
                total.invalidCategorizationRows);
        printErrorPercentLine(summaryWriter, totalSummary);

        if (summaryWriter != null) {
            try {
                writeLatexClassificationErrorTable(summaryWriter, classificationFeaturePctsByModel, latexOverallPctsByModel);
                writeLatexGenerationTable(summaryWriter, latexOverallPctsByModel.keySet(),
                        pluralErrorPctsByModel, conjugationCompletePctsByModel, conjugationPartialPctsByModel, conjugationErrorPctsByModel);
                summaryWriter.flush();
                summaryWriter.close();
                System.out.println("error-percent: summary file=" + summaryPath);
            } catch (IOException e) {
                System.out.println("Failed to finalize error percent summary file: " + summaryPath + " (" + e.getMessage() + ")");
            }
        }
    }

    private static ErrorPercentStats getErrorPercentForMorphoFile(MorphoFileSpec fileSpec,
                                                                  BufferedWriter summaryWriter) {

        ErrorPercentStats stats = new ErrorPercentStats();
        if (fileSpec == null || fileSpec.path == null || !Files.exists(fileSpec.path)) {
            return stats;
        }
        Path filePath = fileSpec.path;
        MorphoCategoricalSchema.CategorySpec categorySpec = categoricalSpecForFile(fileSpec);
        String categoryFieldName = categorySpec == null ? null : categorySpec.getFieldName();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                stats.totalLines++;
                com.fasterxml.jackson.databind.node.ObjectNode node = GenMorphoUtils.parseJsonObjectLine(line);
                if (node == null) {
                    stats.parseFailures++;
                    continue;
                }
                String status = node.path("status").asText("").toLowerCase();
                if (status.equals("refused")) {
                    stats.refusedRows++;
                    continue;
                }
                if (status.equals("garbled")) {
                    stats.garbledRows++;
                    continue;
                }
                if (fileSpec.requiresCompleteConjugation && status.equals("partial")) {
                    stats.conjugationPartialRows++;
                    continue;
                }
                if (categoryFieldName != null && hasInvalidCategorizationMarker(node, categoryFieldName)) {
                    stats.invalidCategorizationRows++;
                    continue;
                }
                if (!"error".equals(status)) {
                    if (fileSpec.requiresCompleteConjugation) {
                        stats.conjugationCompleteRows++;
                    }
                    continue;
                }
                String errorSource = node.path("errorSource").asText("").trim();
                if (ERROR_SOURCE_EXISTING.equals(errorSource)) {
                    stats.existingErrorRows++;
                } else if (ERROR_SOURCE_MALFORMED_CONVERTED.equals(errorSource)) {
                    stats.malformedConvertedRows++;
                } else {
                    // Unknown errorSource — count as existing error
                    stats.existingErrorRows++;
                }
            }
        } catch (IOException e) {
            printErrorPercentLine(summaryWriter, "Failed while scanning file " + filePath + ": " + e.getMessage());
            return stats;
        }

        int totalErrors = computeTotalErrorRows(stats);
        String fileSummary;
        if (fileSpec.requiresCompleteConjugation) {
            int conjugationErrors = computeConjugationErrorRows(stats);
            fileSummary = String.format(
                    "error-percent: %-30s lines=%d complete=%d completePct=%s partial=%d partialPct=%s errors=%d errorPct=%s (existing=%d malformed->error=%d parseFailures=%d refused=%d garbled=%d invalidCategorization=%d)",
                    filePath.getFileName(),
                    stats.totalLines,
                    stats.conjugationCompleteRows,
                    formatErrorPct(stats.conjugationCompleteRows, stats.totalLines),
                    stats.conjugationPartialRows,
                    formatErrorPct(stats.conjugationPartialRows, stats.totalLines),
                    conjugationErrors,
                    formatErrorPct(conjugationErrors, stats.totalLines),
                    stats.existingErrorRows,
                    stats.malformedConvertedRows,
                    stats.parseFailures,
                    stats.refusedRows,
                    stats.garbledRows,
                    stats.invalidCategorizationRows);
        } else {
            fileSummary = String.format(
                    "error-percent: %-30s lines=%d errors=%d errorPct=%s (existing=%d malformed->error=%d parseFailures=%d refused=%d garbled=%d invalidCategorization=%d)",
                    filePath.getFileName(),
                    stats.totalLines,
                    totalErrors,
                    formatErrorPct(totalErrors, stats.totalLines),
                    stats.existingErrorRows,
                    stats.malformedConvertedRows,
                    stats.parseFailures,
                    stats.refusedRows,
                    stats.garbledRows,
                    stats.invalidCategorizationRows);
        }
        printErrorPercentLine(summaryWriter, fileSummary);
        return stats;
    }

    private static double computeErrorPctValue(ErrorPercentStats stats) {

        if (stats == null || stats.totalLines <= 0) {
            return Double.NaN;
        }
        int totalErrors = computeTotalErrorRows(stats);
        return (100.0 * totalErrors) / stats.totalLines;
    }

    private static int computeTotalErrorRows(ErrorPercentStats stats) {

        if (stats == null) {
            return 0;
        }
        return stats.existingErrorRows + stats.malformedConvertedRows
                + stats.parseFailures + stats.garbledRows + stats.invalidCategorizationRows;
    }

    private static double computeConjugationCompletePctValue(ErrorPercentStats stats) {

        if (stats == null || stats.totalLines <= 0) {
            return Double.NaN;
        }
        return (100.0 * stats.conjugationCompleteRows) / stats.totalLines;
    }

    private static double computeConjugationPartialPctValue(ErrorPercentStats stats) {

        if (stats == null || stats.totalLines <= 0) {
            return Double.NaN;
        }
        return (100.0 * stats.conjugationPartialRows) / stats.totalLines;
    }

    private static double computeConjugationErrorPctValue(ErrorPercentStats stats) {

        if (stats == null || stats.totalLines <= 0) {
            return Double.NaN;
        }
        return (100.0 * computeConjugationErrorRows(stats)) / stats.totalLines;
    }

    private static String relativeMorphoPath(Path modelRoot, Path filePath) {

        if (filePath == null) {
            return "";
        }
        if (modelRoot == null) {
            Path parent = filePath.getParent();
            String category = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
            String fileName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
            if (category.isEmpty()) {
                return fileName;
            }
            return category + "/" + fileName;
        }
        try {
            return modelRoot.relativize(filePath).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            Path parent = filePath.getParent();
            String category = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
            String fileName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
            if (category.isEmpty()) {
                return fileName;
            }
            return category + "/" + fileName;
        }
    }

    private static void writeLatexClassificationErrorTable(BufferedWriter summaryWriter,
                                                           Map<String, Map<String, Double>> featurePctsByModel,
                                                           Map<String, Double> overallPctsByModel) throws IOException {

        if (summaryWriter == null || overallPctsByModel == null || overallPctsByModel.isEmpty()) {
            return;
        }

        List<String> modelNames = new ArrayList<>(overallPctsByModel.keySet());
        modelNames.sort(GenMorphoDB::compareModelsForLatexTable);

        summaryWriter.newLine();
        summaryWriter.write("\\begin{table*}[t]");
        summaryWriter.newLine();
        summaryWriter.write("\\centering");
        summaryWriter.newLine();
        summaryWriter.write("\\scriptsize");
        summaryWriter.newLine();
        summaryWriter.write("\\caption{Classification error percentage by model and feature}");
        summaryWriter.newLine();
        summaryWriter.write("\\label{tab:classification_error_rates}");
        summaryWriter.newLine();
        summaryWriter.write("\\resizebox{\\textwidth}{!}{%");
        summaryWriter.newLine();
        summaryWriter.write("\\begin{tabular}{lrrrrr|rrrrr|r|r|r}");
        summaryWriter.newLine();
        summaryWriter.write("\\toprule");
        summaryWriter.newLine();
        summaryWriter.write("& \\multicolumn{5}{c}{Nouns} ");
        summaryWriter.newLine();
        summaryWriter.write("& \\multicolumn{5}{c}{Verbs} ");
        summaryWriter.newLine();
        summaryWriter.write("& \\multicolumn{1}{c}{Adjectives} ");
        summaryWriter.newLine();
        summaryWriter.write("& \\multicolumn{1}{c}{Adverbs} ");
        summaryWriter.newLine();
        summaryWriter.write("& \\multicolumn{1}{c}{Overall} \\\\");
        summaryWriter.newLine();
        summaryWriter.write("\\cmidrule(lr){2-6} \\cmidrule(lr){7-11} \\cmidrule(lr){12-12} \\cmidrule(lr){13-13} \\cmidrule(lr){14-14}");
        summaryWriter.newLine();
        summaryWriter.write("Model");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Indefinite Articles}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Countability}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Humanness}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Agentivity}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Collective Nouns}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Valence}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Reflexive}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Causativity}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Achievement Process}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Reciprocal}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Semantic Classes}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Semantic Classes}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Error \\%} \\\\");
        summaryWriter.newLine();
        summaryWriter.write("\\midrule");
        summaryWriter.newLine();
        summaryWriter.newLine();

        for (String modelName : modelNames) {
            Map<String, Double> featurePcts = featurePctsByModel.get(modelName);
            summaryWriter.write("\\texttt{" + escapeLatex(modelName) + "}");
            summaryWriter.newLine();
            for (String columnPath : LATEX_CLASSIFICATION_COLUMN_PATHS) {
                summaryWriter.write("& " + formatLatexPct(featurePcts == null ? null : featurePcts.get(columnPath)));
                summaryWriter.newLine();
            }
            summaryWriter.write("& " + formatLatexPct(overallPctsByModel.get(modelName)) + " \\\\");
            summaryWriter.newLine();
            summaryWriter.newLine();
        }

        summaryWriter.write("\\bottomrule");
        summaryWriter.newLine();
        summaryWriter.write("\\end{tabular}%");
        summaryWriter.newLine();
        summaryWriter.write("}");
        summaryWriter.newLine();
        summaryWriter.write("\\end{table*}");
        summaryWriter.newLine();
    }

    private static void writeLatexGenerationTable(BufferedWriter summaryWriter,
                                                  Set<String> modelNameSet,
                                                  Map<String, Double> pluralErrorPctsByModel,
                                                  Map<String, Double> conjugationCompletePctsByModel,
                                                  Map<String, Double> conjugationPartialPctsByModel,
                                                  Map<String, Double> conjugationErrorPctsByModel) throws IOException {

        if (summaryWriter == null || modelNameSet == null || modelNameSet.isEmpty()) {
            return;
        }

        List<String> modelNames = new ArrayList<>(modelNameSet);
        modelNames.sort(GenMorphoDB::compareModelsForLatexTable);

        summaryWriter.newLine();
        summaryWriter.write("\\begin{table*}[t]");
        summaryWriter.newLine();
        summaryWriter.write("\\centering");
        summaryWriter.newLine();
        summaryWriter.write("\\scriptsize");
        summaryWriter.newLine();
        summaryWriter.write("\\caption{Generation percentages by model and feature}");
        summaryWriter.newLine();
        summaryWriter.write("\\label{tab:generation_rates}");
        summaryWriter.newLine();
        summaryWriter.write("\\resizebox{\\textwidth}{!}{%");
        summaryWriter.newLine();
        summaryWriter.write("\\begin{tabular}{lrrrr}");
        summaryWriter.newLine();
        summaryWriter.write("\\toprule");
        summaryWriter.newLine();
        summaryWriter.write("Model");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Plurals Error \\%}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Conjugations Complete \\%}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Conjugations Partial \\%}");
        summaryWriter.newLine();
        summaryWriter.write("& \\rot{Conjugations Error \\%} \\\\");
        summaryWriter.newLine();
        summaryWriter.write("\\midrule");
        summaryWriter.newLine();
        summaryWriter.newLine();

        for (String modelName : modelNames) {
            summaryWriter.write("\\texttt{" + escapeLatex(modelName) + "}");
            summaryWriter.newLine();
            summaryWriter.write("& " + formatLatexPct(pluralErrorPctsByModel.get(modelName)));
            summaryWriter.newLine();
            summaryWriter.write("& " + formatLatexPct(conjugationCompletePctsByModel.get(modelName)));
            summaryWriter.newLine();
            summaryWriter.write("& " + formatLatexPct(conjugationPartialPctsByModel.get(modelName)));
            summaryWriter.newLine();
            summaryWriter.write("& " + formatLatexPct(conjugationErrorPctsByModel.get(modelName)) + " \\\\");
            summaryWriter.newLine();
            summaryWriter.newLine();
        }

        summaryWriter.write("\\bottomrule");
        summaryWriter.newLine();
        summaryWriter.write("\\end{tabular}%");
        summaryWriter.newLine();
        summaryWriter.write("}");
        summaryWriter.newLine();
        summaryWriter.write("\\end{table*}");
        summaryWriter.newLine();
    }

    private static int compareModelsForLatexTable(String leftModel, String rightModel) {

        ModelMetadata left = ModelMetadata.fromDirName(leftModel);
        ModelMetadata right = ModelMetadata.fromDirName(rightModel);

        double leftSize = left.getParameterBillions();
        double rightSize = right.getParameterBillions();
        boolean leftKnown = leftSize >= 0;
        boolean rightKnown = rightSize >= 0;

        if (leftKnown && rightKnown) {
            int sizeCompare = Double.compare(leftSize, rightSize);
            if (sizeCompare != 0) {
                return sizeCompare;
            }
            return leftModel.compareTo(rightModel);
        }
        if (leftKnown != rightKnown) {
            return leftKnown ? -1 : 1;
        }

        int frontierCompare = Integer.compare(frontierRankForLatex(leftModel), frontierRankForLatex(rightModel));
        if (frontierCompare != 0) {
            return frontierCompare;
        }
        return leftModel.compareTo(rightModel);
    }

    private static int frontierRankForLatex(String modelName) {

        if (modelName == null) {
            return Integer.MAX_VALUE;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("openai__gpt-5-nano")) {
            return 0;
        }
        if (lower.startsWith("openai__gpt-5_2")) {
            return 1;
        }
        return 100;
    }

    private static String formatLatexPct(Double value) {

        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escapeLatex(String text) {

        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\textbackslash{}")
                .replace("_", "\\_")
                .replace("%", "\\%")
                .replace("&", "\\&")
                .replace("#", "\\#")
                .replace("$", "\\$")
                .replace("{", "\\{")
                .replace("}", "\\}");
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

    private static void loadWordNetAndRunFindFixGaps(Path morphoModelRoot, CliOptions cliOptions) {

        MorphoWordNetUtils.initWordNet();
        runFindFixGaps(morphoModelRoot, cliOptions,
                       MorphoWordNetUtils.buildNormalizedLemmaSet(MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.nounSynsetHash,      "noun set")),
                       MorphoWordNetUtils.buildNormalizedLemmaSet(MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.verbSynsetHash,      "verb set")),
                       MorphoWordNetUtils.buildNormalizedLemmaSet(MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.adjectiveSynsetHash, "adjective set")),
                       MorphoWordNetUtils.buildNormalizedLemmaSet(MorphoWordNetUtils.filteredSortedCopy(WordNet.wn.adverbSynsetHash,    "adverb set")));
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
        String extracted = extractLooseJsonStringField(line, fieldName);
        if (extracted != null) {
            return extracted.trim();
        }
        return null;
    }

    private static String extractLooseJsonStringField(String text, String fieldName) {

        if (text == null || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        String quotedField = "\"" + fieldName + "\"";
        int searchStart = 0;
        while (searchStart >= 0 && searchStart < text.length()) {
            int fieldStart = text.indexOf(quotedField, searchStart);
            if (fieldStart < 0) {
                return null;
            }
            int colonIndex = skipWhitespace(text, fieldStart + quotedField.length());
            if (colonIndex < 0 || colonIndex >= text.length() || text.charAt(colonIndex) != ':') {
                searchStart = fieldStart + quotedField.length();
                continue;
            }
            int valueStart = skipWhitespace(text, colonIndex + 1);
            if (valueStart < 0 || valueStart >= text.length() || text.charAt(valueStart) != '"') {
                searchStart = fieldStart + quotedField.length();
                continue;
            }
            int valueEnd = findJsonStringEnd(text, valueStart);
            if (valueEnd <= valueStart) {
                return null;
            }
            return decodeJsonStringValue(text.substring(valueStart + 1, valueEnd - 1));
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
