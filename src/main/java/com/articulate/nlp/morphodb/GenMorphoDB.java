package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
        Integer ollamaPort = DEFAULT_OLLAMA_PORT;
        String apiKey;
        String apiKeyEnv;
        String baseUrl;
        String serviceTier;
        Boolean cheapPromptMode;
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
        System.out.println("  Provider-aware usage:");
        System.out.println("    com.articulate.nlp.morphodb.GenMorphoDB <word-type> <gen-function> \\");
        System.out.println("      --provider <ollama|openai|anthropic|claude|openai-compatible> --model <model> \\");
        System.out.println("      [--ollama-port <port>] [--api-key <key>|--api-key-env <ENV_VAR>] [--base-url <url>] \\");
        System.out.println("      [--service-tier <auto|default|flex|priority>] [--cheap-prompt|--full-prompt]");
        System.out.println("word-types supported: noun, verb, adjective, adverb, all");
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
        System.out.println("If no Ollama port is provided, the default 11434 is used.");
        System.out.println("Default prompt mode: full for ollama, cheap for non-ollama providers.");
    }

    private static CliOptions parseCliArgs(String[] args) {

        if (args == null || args.length < 2) {
            return null;
        }
        CliOptions options = new CliOptions();
        options.wordType = args[0].toLowerCase();
        options.genFunction = args[1];

        int index = 2;
        if (index < args.length && !args[index].startsWith("--")) {
            options.model = args[index];
            index++;
            if (index < args.length && isInteger(args[index])) {
                options.ollamaPort = Integer.parseInt(args[index]);
                index++;
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
            System.err.println("Unsupported argument: " + arg);
            return null;
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
        configureLlm(cliOptions);

        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();

        Map<String, Set<String>> nounSynsetHash = new TreeMap<>(WordNet.wn.nounSynsetHash);
        Map<String, Set<String>> verbSynsetHash = new TreeMap<>(WordNet.wn.verbSynsetHash);
        Map<String, Set<String>> adjectiveSynsetHash = new TreeMap<>(WordNet.wn.adjectiveSynsetHash);
        Map<String, Set<String>> adverbSynsetHash = new TreeMap<>(WordNet.wn.adverbSynsetHash);

        GenMorphoUtils.removeNonEnglishWords(nounSynsetHash, "noun set");
        GenMorphoUtils.removeNonEnglishWords(verbSynsetHash, "verb set");
        GenMorphoUtils.removeNonEnglishWords(adjectiveSynsetHash, "adjective set");
        GenMorphoUtils.removeNonEnglishWords(adverbSynsetHash, "adverb set");

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
                break;
        }
    }


}
