package com.articulate.nlp;


import com.articulate.sigma.nlg.NLGUtils;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Set;
import java.util.Arrays;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * Utility methods useful for synthetically generating sentences.
 */
public class GenUtils {

    boolean debug = false;
    HashSet<String> previousVariables = new HashSet<>();
    private static final int MAX_VARIABLE_LENGTH = 2;
    static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String NUMBERS = "0123456789";
    private static final String DEFAULT_LLM_PROVIDER = "ollama";
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_OPENAI_SERVICE_TIER = "auto";
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1";
    private static final int REMOTE_MAX_ATTEMPTS = 3;
    private static final long REMOTE_RETRY_DELAY_MS = 10000L;
    private static final int REMOTE_REQUEST_TIMEOUT_MS = 300000;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    static String OLLAMA_MODEL = "llama3.2";
    static String LLM_PROVIDER = DEFAULT_LLM_PROVIDER;
    static String LLM_MODEL = OLLAMA_MODEL;
    static String LLM_API_KEY = null;
    static String LLM_BASE_URL = null;
    static String LLM_SERVICE_TIER = null;
    static boolean CHEAP_PROMPT_MODE = false;
    static int OLLAMA_PORT = Integer.parseInt(System.getProperty("OLLAMA_PORT", "11434"));
    public static OllamaAPI ollamaAPI;
    public static Options options;
    private static Process ollamaProcess = null;
    private static final Random rand = new Random();
    private static final int OLLAMA_MAX_ATTEMPTS = 6;
    private static final long OLLAMA_RETRY_DELAY_MS = 30 * 1000L;

    /** ***************************************************************
     *   Creates a random unique variable name.
     *   The random variable half the time ends with a number.
     */
    public String randomVariableName() {
        Random random = new Random();
        boolean isUnique = false;
        String newVariable = "";
        while(!isUnique) {
            double biasFactor = 0.75; // higher = more bias toward shorter strings
            double r = random.nextDouble();
            int length = 1 + (int)(Math.log(1 - r) / Math.log(biasFactor)) % MAX_VARIABLE_LENGTH;
            StringBuilder sb = new StringBuilder();
            sb.append("?");
            for (int i = 0; i < length; i++) {
                int index = random.nextInt(CHARACTERS.length());
                sb.append(CHARACTERS.charAt(index));
            }
            if (random.nextBoolean()) {
                int index = random.nextInt(NUMBERS.length());
                sb.append(NUMBERS.charAt(index));
            }
            newVariable = sb.toString();
            if (debug) System.out.println("New Variable: " + newVariable);
            if (!previousVariables.contains(newVariable)) {
                isUnique = true;
                previousVariables.add(newVariable);
            }
        }
        return newVariable;
    }

    public void resetVariables() {
        previousVariables.clear();
    }

    /** ***************************************************************
     *   Creates a file if one doesn't exist already.
     */
    public static void createFileIfDoesNotExists(String fileName) {

        Path filePath = Paths.get(fileName);
        if (Files.exists(filePath)) {
            return;
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /** **************************************************************************
     * Gets the best SUMO mapping for a word. Chooses a random equivalent mapping,
     * if no equivalent mapping exists, return null.
     */
    public static String getBestSUMOMapping(Set<String> synsetOfTerm) {

        ArrayList<String> equivalentTerms = new ArrayList();
        for (String synset:synsetOfTerm) {
            String sumoMapping = WordNet.wn.getSUMOMapping(synset);
            if (sumoMapping != null) {
                sumoMapping = sumoMapping.substring(2);
                if (sumoMapping.charAt(sumoMapping.length() - 1) == '=') {
                    equivalentTerms.add(sumoMapping.substring(0, sumoMapping.length() - 1));
                }
            }
        }
        if (!equivalentTerms.isEmpty()) {
            // TODO: Do wordsense disambiguation
            Random rand = new Random();
            return equivalentTerms.get(rand.nextInt(equivalentTerms.size()));
        }
        return null;
    }

    public static String capitalizeFirstLetter(String sentenceToCapitalize) {

            if (sentenceToCapitalize == null || sentenceToCapitalize.isEmpty()) {
                return sentenceToCapitalize;
            }
            return sentenceToCapitalize.substring(0, 1).toUpperCase() + sentenceToCapitalize.substring(1);
    }

    public static String lowercaseFirstLetter(String s) {

        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    /** ***************************************************************
     * Generate a boolean true value randomly num out of max times.
     * So biasedBoolean(8,10) generates a true most of the time
     * (8 out of 10 times on average)
     */
    public static boolean biasedBoolean(int num, int max) {

        int val = rand.nextInt(max);
        return val < num;
    }

    /** ***************************************************************
     * Initialize LFeatureSets and default suppression flags used by generators.
     */
    public static LFeatureSets initLFeatureSets(KBLite kbLite, Set<String> suppress) {

        suppress.add("modal");
        suppress.add("attitude");
        LFeatureSets lfeatsets = new LFeatureSets(kbLite);
        lfeatsets.initNumbers();
        lfeatsets.initRequests();
        lfeatsets.initAttitudes(suppress.contains("attitude"));
        lfeatsets.initOthers();
        lfeatsets.initPrepPhrase();
        lfeatsets.initEndings();
        lfeatsets.genProcTable();
        return lfeatsets;
    }


    /** ***************************************************************
     *  Writes to a file, locks file during write, so thread safe.
     */
    public static void writeToFile(String fileName, String stringToWrite) {
        FileChannel fileChannel1 = null;
        FileLock lock1 = null;
        try {
            createFileIfDoesNotExists(fileName);
            fileChannel1 = FileChannel.open(Paths.get(fileName), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            lock1 = fileChannel1.lock();
            ByteBuffer buffer1 = ByteBuffer.wrap(stringToWrite.getBytes());
            fileChannel1.write(buffer1);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (lock1 != null) lock1.release();
                if (fileChannel1 != null) fileChannel1.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /** ***************************************************************
     *   Writes an english sentence and logic sentence to their
     *   respective files.
     */
    public static void writeEnglishLogicPairToFile(String english, String logic, String outputFileEnglish, String outputFileLogic) {

        FileChannel fileChannel1 = null;
        FileChannel fileChannel2 = null;
        FileLock lock1 = null;
        FileLock lock2 = null;
        english = english + "\n";
        logic = logic + "\n";

        try {
            fileChannel1 = FileChannel.open(Paths.get(outputFileEnglish), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            fileChannel2 = FileChannel.open(Paths.get(outputFileLogic), StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            lock1 = fileChannel1.lock();
            lock2 = fileChannel2.lock();

            ByteBuffer buffer1 = ByteBuffer.wrap(english.getBytes());
            ByteBuffer buffer2 = ByteBuffer.wrap(logic.getBytes());

            fileChannel1.write(buffer1);
            fileChannel2.write(buffer2);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (lock1 != null) lock1.release();
                if (fileChannel1 != null) fileChannel1.close();
                if (lock2 != null) lock2.release();
                if (fileChannel2 != null) fileChannel2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /** ***************************************************************
     * generate new SUMO statements for relations and output English
     * paraphrase
     */
    public static String toEnglish(String form, KB kb) {

        return NLGUtils.htmlParaphrase("", form, kb.getFormatMap("EnglishLanguage"),
                kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage");
    }

    /** ***************************************************************
     *   Runs a bash command
     */
    public static String runBashCommand(String command) {

        StringBuilder output = new StringBuilder();
        try {
            // Run the command using bash -c
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);  // combine stderr with stdout
            Process process = builder.start();
            // Read the output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command exited with code ").append(exitCode).append("\n");
            }

        } catch (IOException | InterruptedException e) {
            output.append("Error: ").append(e.getMessage()).append("\n");
        }
        return output.toString().trim();
    }

    /**
     * Checks if the Ollama server is listening on a specified port.
     * @param port the port to check
     * @return true if the server is running, false otherwise
     */
    public static boolean isOllamaServerRunning(int port) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/api/version");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }


    /**
     * Starts the Ollama server on the specified port if it is not already running.
     * @param port the port to check and use for the server
     * @throws IOException if there is an error starting the server process
     * @throws InterruptedException if the process is interrupted
     */
    public static void startOllamaServer(int port) {
        try {
            if (!isOllamaServerRunning(port)) {
                System.out.println("GenUtils: Starting Ollama Server on port: " + port);
                ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
                pb.environment().put("OLLAMA_HOST", "127.0.0.1:" + port);
                pb.inheritIO();
                ollamaProcess = pb.start();
                Thread.sleep(10000);
                if (!isOllamaServerRunning(port)) {
                    System.err.println("Failed to start Ollama server on port " + port);
                    System.exit(1);
                }
                setOllamaPort(port);
                System.out.println("GenUtils: Ollama server started on port: " + port);
            } else { System.out.println("GenUtils: Ollama server already running on port: " + port);}

            System.out.println("GenUtils: Creating connection to Ollama server using model: " + LLM_MODEL);
            ollamaAPI = new OllamaAPI("http://localhost:" + port + "/");
            ollamaAPI.setVerbose(false);
            options = new OptionsBuilder().setTemperature(0.0f).setNumPredict(4000).build();
            System.out.println("GenUtils: Connected to Ollama server");
        } catch (Exception e) {
            System.err.println("Failed to start Ollama server on port " + port);
            System.err.println("aError: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void stopOllamaServer() {
        if (ollamaProcess != null && ollamaProcess.isAlive()) {
            ollamaProcess.destroy();
            try { ollamaProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
            if (ollamaProcess.isAlive()) ollamaProcess.destroyForcibly();
        }
        ollamaProcess = null;
    }

    public static void setOllamaModel(String newModel) {

        setLLMModel(newModel);
    }

    public static void setLLMModel(String newModel) {

        if (newModel == null || newModel.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM model must not be null or empty.");
        }
        String trimmedModel = newModel.trim();
        OLLAMA_MODEL = trimmedModel;
        LLM_MODEL = trimmedModel;
    }

    public static void setLLMProvider(String providerName) {

        String normalizedProvider = normalizeLLMProvider(providerName);
        if (normalizedProvider == null) {
            throw new IllegalArgumentException("Unsupported LLM provider: " + providerName);
        }
        LLM_PROVIDER = normalizedProvider;
    }

    public static String getLLMProvider() {

        return LLM_PROVIDER;
    }

    public static String normalizeLLMProvider(String providerName) {

        if (providerName == null || providerName.trim().isEmpty()) {
            return DEFAULT_LLM_PROVIDER;
        }
        String normalized = providerName.trim().toLowerCase();
        if ("chatgpt".equals(normalized)) {
            return "openai";
        }
        if ("claude".equals(normalized)) {
            return "anthropic";
        }
        if ("openai_compatible".equals(normalized) || "openai-compatible".equals(normalized) ||
                "compatible".equals(normalized)) {
            return "openai-compatible";
        }
        if ("openai".equals(normalized) || "anthropic".equals(normalized) || "ollama".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public static void setLLMApiKey(String apiKey) {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            LLM_API_KEY = null;
            return;
        }
        LLM_API_KEY = apiKey.trim();
    }

    public static String getLLMApiKey() {

        return LLM_API_KEY;
    }

    public static void setLLMBaseUrl(String baseUrl) {

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            LLM_BASE_URL = null;
            return;
        }
        LLM_BASE_URL = trimTrailingSlash(baseUrl.trim());
    }

    public static String getLLMBaseUrl() {

        return LLM_BASE_URL;
    }

    public static void setCheapPromptMode(boolean enabled) {

        CHEAP_PROMPT_MODE = enabled;
    }

    public static boolean isCheapPromptMode() {

        return CHEAP_PROMPT_MODE;
    }

    public static void setLLMServiceTier(String serviceTier) {

        if (serviceTier == null || serviceTier.trim().isEmpty()) {
            LLM_SERVICE_TIER = null;
            return;
        }
        String normalizedServiceTier = normalizeOpenAIServiceTier(serviceTier);
        if (normalizedServiceTier == null) {
            throw new IllegalArgumentException("Unsupported OpenAI service tier: " + serviceTier +
                    ". Supported values: auto, default, flex, priority.");
        }
        LLM_SERVICE_TIER = normalizedServiceTier;
    }

    public static String getLLMServiceTier() {

        return LLM_SERVICE_TIER;
    }

    public static String normalizeOpenAIServiceTier(String serviceTier) {

        if (serviceTier == null || serviceTier.trim().isEmpty()) {
            return null;
        }
        String normalized = serviceTier.trim().toLowerCase();
        if ("auto".equals(normalized) || "default".equals(normalized) ||
                "flex".equals(normalized) || "priority".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public static void setOllamaPort(Integer port) {

        if (port == null) {
            throw new IllegalArgumentException("OLLAMA_PORT must not be null");
        }
        OLLAMA_PORT = port;
    }

    public static String getOllamaModel() {

        return LLM_MODEL;
    }

    public static String getLLMModel() {

        return LLM_MODEL;
    }

    public static String getMorphoModelDirectoryName() {

        String model = getLLMModel();
        if ("ollama".equals(getLLMProvider())) {
            return model;
        }
        return getLLMProvider() + "__" + model;
    }

    public static String getDefaultApiKeyEnvVar(String providerName) {

        String normalizedProvider = normalizeLLMProvider(providerName);
        if ("openai".equals(normalizedProvider) || "openai-compatible".equals(normalizedProvider)) {
            return "OPENAI_API_KEY";
        }
        if ("anthropic".equals(normalizedProvider)) {
            return "ANTHROPIC_API_KEY";
        }
        return null;
    }

    /** ***************************************************************
     *   Sends a prompt to the configured LLM provider and returns the response.
     */
    public static String askLLM(String prompt) {

        String provider = getLLMProvider();
        if ("ollama".equals(provider)) {
            return askOllama(prompt);
        }
        String response;
        if ("openai".equals(provider) || "openai-compatible".equals(provider)) {
            response = askOpenAICompatible(prompt);
        } else if ("anthropic".equals(provider)) {
            response = askAnthropic(prompt);
        } else {
            throw new IllegalStateException("Unsupported LLM provider: " + provider);
        }
        System.out.println("GenUtils.askLLM() [" + provider + "] Prompt: " + prompt);
        System.out.println("GenUtils.askLLM() [" + provider + "] Response: " + response);
        return response;
    }

    public static class StringBuilderStreamHandler implements OllamaStreamHandler {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void accept(String s) {
            if (s != null) sb.append(s);
        }

        public String getText() {
            return sb.toString();
        }
    }

    /** ***************************************************************
     *   Sends a prompt to the Ollama server and returns the response.
     */
    public static String askOllama(String prompt) {

        for (int attempt = 1; attempt <= OLLAMA_MAX_ATTEMPTS; attempt++) {
            try {
                if (ollamaAPI == null) {
                    startOllamaServer(OLLAMA_PORT);
                }
                ollamaAPI.setRequestTimeoutSeconds(600);
                StringBuilderStreamHandler handler = new StringBuilderStreamHandler();
                OllamaResult result = ollamaAPI.generate(getLLMModel(), prompt, false, options, handler);
                Object response = result.getResponse();
                if (response == null) {
                    throw new IllegalStateException("Ollama returned a null response.");
                }
                String text = response.toString();
                if (text.isBlank()) {
                    throw new IllegalStateException("Ollama returned an empty response.");
                }
                return text;
            } catch (Exception e) {
                System.out.println("Error in GenUtils.askOllama() attempt " + attempt +
                        " of " + OLLAMA_MAX_ATTEMPTS + ": " + e.getMessage());
                System.out.println("Erroring Prompt: " + prompt);
                e.printStackTrace();
                boolean serverHealthy = isOllamaServerRunning(OLLAMA_PORT);
                if (!serverHealthy) {
                    System.out.println("Ollama server appears unhealthy. Restarting...");
                    stopOllamaServer();
                    startOllamaServer(OLLAMA_PORT);
                } else {
                    System.out.println("Ollama server responds to health check. Retrying without restart.");
                }
                if (attempt < OLLAMA_MAX_ATTEMPTS) {
                    System.out.println("Retrying Ollama request in 30 seconds...");
                    try {
                        Thread.sleep(OLLAMA_RETRY_DELAY_MS);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        System.err.println("GenUtils.askOllama(): Exhausted all retries without receiving a response. Exiting.");
        System.exit(1);
        throw new IllegalStateException("GenUtils.askOllama(): System exit failed to terminate process.");
    }

    private static String askOpenAICompatible(String prompt) {

        String apiKey = resolveApiKey();
        String baseUrl = (LLM_BASE_URL == null || LLM_BASE_URL.trim().isEmpty())
                ? DEFAULT_OPENAI_BASE_URL
                : LLM_BASE_URL;
        String endpoint = resolveOpenAIEndpoint(baseUrl);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        String provider = getLLMProvider();
        String effectiveServiceTier = resolveOpenAIServiceTierForRequest();
        for (int attempt = 1; attempt <= REMOTE_MAX_ATTEMPTS; attempt++) {
            try {
                String payload = buildOpenAIRequestPayload(prompt, effectiveServiceTier);
                String responseBody = postJson(endpoint, payload, headers);
                JsonNode root = JSON_MAPPER.readTree(responseBody);
                JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
                if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
                    throw new IllegalStateException("No message content in OpenAI response.");
                }
                return contentNode.asText();
            } catch (Exception e) {
                if ("flex".equals(effectiveServiceTier) && shouldFallbackToDefaultServiceTier(e)) {
                    System.out.println("OpenAI flex capacity unavailable on attempt " + attempt +
                            ". Retrying with service_tier=" + DEFAULT_OPENAI_SERVICE_TIER + ".");
                    effectiveServiceTier = DEFAULT_OPENAI_SERVICE_TIER;
                }
                System.out.println("Error in GenUtils.askLLM() [" + provider + "] attempt " + attempt +
                        " of " + REMOTE_MAX_ATTEMPTS + ": " + e.getMessage());
                if (attempt < REMOTE_MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }
        System.err.println("GenUtils.askLLM(): Exhausted " + provider + " retries without receiving a response. Exiting.");
        System.exit(1);
        throw new IllegalStateException("GenUtils.askLLM(): System exit failed to terminate process."); // required for compiler.
    }

    private static String askAnthropic(String prompt) {

        String apiKey = resolveApiKey();
        String baseUrl = (LLM_BASE_URL == null || LLM_BASE_URL.trim().isEmpty())
                ? DEFAULT_ANTHROPIC_BASE_URL
                : LLM_BASE_URL;
        String endpoint = resolveAnthropicEndpoint(baseUrl);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        for (int attempt = 1; attempt <= REMOTE_MAX_ATTEMPTS; attempt++) {
            try {
                String payload = buildAnthropicRequestPayload(prompt);
                String responseBody = postJson(endpoint, payload, headers);
                JsonNode root = JSON_MAPPER.readTree(responseBody);
                JsonNode contentArray = root.path("content");
                if (contentArray == null || !contentArray.isArray() || contentArray.size() == 0) {
                    throw new IllegalStateException("No content blocks in Anthropic response.");
                }
                JsonNode textNode = contentArray.get(0).path("text");
                if (textNode == null || textNode.isMissingNode() || textNode.isNull()) {
                    throw new IllegalStateException("No text content in Anthropic response.");
                }
                return textNode.asText();
            } catch (Exception e) {
                System.out.println("Error in GenUtils.askLLM() [anthropic] attempt " + attempt +
                        " of " + REMOTE_MAX_ATTEMPTS + ": " + e.getMessage());
                if (attempt < REMOTE_MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }
        System.err.println("GenUtils.askLLM(): Exhausted Anthropic retries without receiving a response. Exiting.");
        System.exit(1);
        throw new IllegalStateException("GenUtils.askLLM(): System exit failed to terminate process."); // required for compiler.
    }

    private static void sleepBeforeRetry() {

        try {
            Thread.sleep(REMOTE_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String resolveApiKey() {

        if (LLM_API_KEY != null && !LLM_API_KEY.trim().isEmpty()) {
            return LLM_API_KEY.trim();
        }
        String envVar = getDefaultApiKeyEnvVar(getLLMProvider());
        if (envVar != null) {
            String apiKeyFromEnv = System.getenv(envVar);
            if (apiKeyFromEnv != null && !apiKeyFromEnv.trim().isEmpty()) {
                return apiKeyFromEnv.trim();
            }
        }
        throw new IllegalStateException("No API key configured for provider " + getLLMProvider() +
                ". Use --api-key, --api-key-env, or set " + envVar + ".");
    }

    private static String trimTrailingSlash(String text) {

        if (text == null) {
            return null;
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '/') {
            end--;
        }
        return text.substring(0, end);
    }

    private static String resolveOpenAIEndpoint(String baseUrl) {

        String trimmedBase = trimTrailingSlash(baseUrl);
        if (trimmedBase.endsWith("/chat/completions")) {
            return trimmedBase;
        }
        return trimmedBase + "/chat/completions";
    }

    private static String resolveAnthropicEndpoint(String baseUrl) {

        String trimmedBase = trimTrailingSlash(baseUrl);
        if (trimmedBase.endsWith("/messages")) {
            return trimmedBase;
        }
        return trimmedBase + "/messages";
    }

    private static String buildOpenAIRequestPayload(String prompt, String serviceTier) throws IOException {

        ObjectNode root = JSON_MAPPER.createObjectNode()
                .put("model", getLLMModel());
        // Newer OpenAI models may reject explicit temperature values.
        if (!"openai".equals(getLLMProvider())) {
            root.put("temperature", 0);
        }
        if (serviceTier != null && !serviceTier.trim().isEmpty()) {
            root.put("service_tier", serviceTier);
        }
        root.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", prompt);
        return JSON_MAPPER.writeValueAsString(root);
    }

    private static String resolveOpenAIServiceTierForRequest() {

        if (!"openai".equals(getLLMProvider())) {
            return null;
        }
        return getLLMServiceTier();
    }

    private static boolean shouldFallbackToDefaultServiceTier(Exception exception) {

        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        String message = exception.getMessage().toLowerCase();
        return message.contains("http 429") || message.contains("http 503");
    }

    private static String buildAnthropicRequestPayload(String prompt) throws IOException {

        ObjectNode root = JSON_MAPPER.createObjectNode()
                .put("model", getLLMModel())
                .put("max_tokens", 1024)
                .put("temperature", 0);
        root.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", prompt);
        return JSON_MAPPER.writeValueAsString(root);
    }

    private static String postJson(String endpoint, String payload, Map<String, String> headers) throws IOException {

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(REMOTE_REQUEST_TIMEOUT_MS);
            connection.setReadTimeout(REMOTE_REQUEST_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readStream(responseStream);
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP " + statusCode + " from " + endpoint + ": " + responseBody);
            }
            return responseBody;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {

        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }


    /**
     *  Finds the first balanced JSON object substring (starting with '{' and ending with '}').
     *  This handles nested braces and ignores braces in strings.
     */
    public static String extractFirstJsonObject(String text) {

        int braceCount = 0;
        boolean inString = false;
        char stringChar = 0;
        int startIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (braceCount == 0) {
                    startIndex = i;
                }
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && startIndex != -1) {
                    return text.substring(startIndex, i + 1);
                } else if (braceCount < 0) {
                    // Unbalanced braces
                    return null;
                }
            }
        }
        // No balanced JSON object found
        return null;
    }


    /** *********************************************************************
     *    Extracts the fields from a JSON object
     *    Usage example: extractJsonFields(Arrays.asList("article", "noun", "explanation", "usage"))
     */
    public static String[] extractJsonFields(String jsonString, List<String> fieldsToExtract) {
        try {
            jsonString = GenUtils.extractFirstJsonObject(jsonString);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
            String[] results = new String[fieldsToExtract.size()];

            for (int i = 0; i < fieldsToExtract.size(); i++) {
                String fieldName = fieldsToExtract.get(i).toLowerCase();
                JsonNode valueNode = root.get(fieldName);
                // If any field is missing or null, return null immediately
                if (valueNode == null || valueNode.isNull()) {
                    return null;
                }
                results[i] = valueNode.asText();
            }
            return results;
        } catch (Exception e) {
            // Parsing error or unexpected error
            return null;
        }
    }

    /** ***************************************************************
     * Converts a non-negative integer to its English words representation.
     * Handles 0–999,999. Numbers >= 1,000,000 fall back to digit form.
     * Examples: 2 → "two", 21 → "twenty-one", 115 → "one hundred fifteen".
     */
    public static String spellOutNumber(int n) {

        if (n < 0) return String.valueOf(n);
        if (n == 0) return "zero";
        final String[] ONES = {
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
        };
        final String[] TENS = {
            "", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety"
        };
        if (n < 20) return ONES[n];
        if (n < 100) {
            String t = TENS[n / 10];
            return (n % 10 == 0) ? t : t + "-" + ONES[n % 10];
        }
        if (n < 1000) {
            String hundreds = ONES[n / 100] + " hundred";
            int remainder = n % 100;
            return (remainder == 0) ? hundreds : hundreds + " " + spellOutNumber(remainder);
        }
        if (n < 1_000_000) {
            String thousands = spellOutNumber(n / 1000) + " thousand";
            int remainder = n % 1000;
            return (remainder == 0) ? thousands : thousands + " " + spellOutNumber(remainder);
        }
        return String.valueOf(n);
    }

    /** *****************************************************************************
     *
     * Adds an element at the end of a String Array.
     */
    public static String[] appendToStringArray(String[] array, String element) {
        if (array == null) {
            return new String[] { element };
        }
        String[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = element;
        return newArray;
    }

}
