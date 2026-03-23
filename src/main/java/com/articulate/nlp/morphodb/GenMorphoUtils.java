package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/***************************************************************
 * Shared utilities for morphological database generation.
 ***************************************************************/
public final class GenMorphoUtils {

    public static boolean debug = false;
    private static final String OUTPUT_ROOT = "MorphologicalDatabase";
    private static final String MORPHO_DB_ROOT_DIR = "MorphoDB";
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, Object> FILE_WRITE_LOCKS = new ConcurrentHashMap<>();

    /***************************************************************
     * Builds the standardized output path for morphology resources.
     ***************************************************************/
    public static String computeOutputFilePath(String wordType, String classificationFileName) {

        if (classificationFileName == null || classificationFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("classificationFileName cannot be null or empty.");
        }
        String normalizedWordType = (wordType == null || wordType.trim().isEmpty())
                ? "misc"
                : wordType.trim().toLowerCase();
        String sanitizedModelName = sanitizeModelName(GenUtils.getMorphoModelDirectoryName());
        Path outputDir = Paths.get(OUTPUT_ROOT, sanitizedModelName, normalizedWordType);
        return outputDir.resolve(classificationFileName).toString();
    }

    /***************************************************************
     * Returns the default root for persisted morphology DB snapshots.
     ***************************************************************/
    public static Path getDefaultMorphoDbRoot() {

        return Paths.get(System.getProperty("user.home"), ".sigmanlp", MORPHO_DB_ROOT_DIR);
    }

    /***************************************************************
     * Normalizes model naming for filesystem paths.
     ***************************************************************/
    public static String sanitizeModelName(String modelName) {

        if (modelName == null || modelName.trim().isEmpty()) {
            return "unknown_model";
        }
        return modelName.trim()
                .replace('.', '_')
                .replace(':', '_');
    }

    /***************************************************************
     * Computes a morphology DB file path for a specific model folder.
     ***************************************************************/
    public static String computeModelOutputFilePath(String modelDirectoryName,
                                                    String wordType,
                                                    String classificationFileName) {

        if (classificationFileName == null || classificationFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("classificationFileName cannot be null or empty.");
        }
        String normalizedWordType = (wordType == null || wordType.trim().isEmpty())
                ? "misc"
                : wordType.trim().toLowerCase();
        String sanitizedModelName = sanitizeModelName(modelDirectoryName);
        Path outputDir = getDefaultMorphoDbRoot().resolve(sanitizedModelName).resolve(normalizedWordType);
        return outputDir.resolve(classificationFileName).toString();
    }

    /***************************************************************
     * Expands leading "~" in filesystem paths to user.home.
     ***************************************************************/
    public static Path expandHomePath(String path) {

        if (path == null) {
            return Paths.get("");
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("~")) {
            String home = System.getProperty("user.home");
            if ("~".equals(trimmed)) {
                return Paths.get(home);
            }
            if (trimmed.startsWith("~/")) {
                return Paths.get(home + trimmed.substring(1));
            }
        }
        return Paths.get(trimmed);
    }

    /***************************************************************
     * Builds (and ensures) the output file path for morphology resources.
     ***************************************************************/
    public static String resolveOutputFile(String wordType, String classificationFileName) {

        String outputFilePath = computeOutputFilePath(wordType, classificationFileName);
        Path outputDir = Paths.get(outputFilePath).getParent();
        try {
            if (outputDir != null) {
                Files.createDirectories(outputDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to create output directory: " + outputDir, e);
        }
        return outputFilePath;
    }

    /***************************************************************
     * Normalizes a lemma string to a canonical lowercase form used as
     * the primary key in all morphology DB files.
     ***************************************************************/
    public static String normalizeLemma(String lemma) {

        if (lemma == null) return "";
        return lemma.trim().toLowerCase().replace('_', ' ').replaceAll("\\s+", " ");
    }

    /***************************************************************
     * Extracts the value of a named field from a serialized JSON line.
     * Returns null if the line is not a JSON object or the field is absent/empty.
     ***************************************************************/
    private static String extractFieldFromSerializedLine(String line, String fieldName) {

        if (line == null || !line.trim().startsWith("{")) return null;
        try {
            JsonNode node = JSON_MAPPER.readTree(line.trim());
            String value = node.path(fieldName).asText("").trim();
            return value.isEmpty() ? null : value;
        } catch (IOException ignored) {
            return null;
        }
    }

    /***************************************************************
     * Filters out non-English words and numbers from the provided map.
     ***************************************************************/
    public static void removeNonEnglishWords(Map<String, Set<String>> wordMap, String wordMapName) {

        Iterator<Map.Entry<String, Set<String>>> iterator = wordMap.entrySet().iterator();
        int numDeleted = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            String key = entry.getKey();
            if (key.length() < 2 || !key.matches("^[a-zA-Z_.\\'-]+$")) {
                iterator.remove();
                numDeleted++;
            }
        }
        System.out.println("Non-English words removed from " + wordMapName + ": " + numDeleted);
    }

    /***************************************************************
     * Utility to determine whether the supplied character is a consonant.
     ***************************************************************/
    public static boolean isConsonant(char c) {

        return "bcdfghjklmnpqrstvwxyz".indexOf(Character.toLowerCase(c)) != -1;
    }

    /***************************************************************
     * Convenience method for debugging synset hashes.
     ***************************************************************/
    public static void printSynsetHash(Map<String, Set<String>> wordHash, Map<String, String> documentationHash) {

        for (Map.Entry<String, Set<String>> entry : wordHash.entrySet()) {
            String key = entry.getKey();
            Set<String> values = entry.getValue();
            System.out.println(key + " : " + values);
            for (String value : values) {
                System.out.println("    " + value + ": " + documentationHash.get(value));
            }
        }
    }

    /***************************************************************
     * Returns a map of normalized lemma -> serialized lines already present
     * in the output file. Lines without a "lemma" field are ignored.
     ***************************************************************/
    public static Map<String, List<String>> loadExistingClassifications(String outputFilePath) {

        Map<String, List<String>> existing = new HashMap<>();
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return existing;
        }
        Path path = Paths.get(outputFilePath);
        if (!Files.exists(path)) {
            return existing;
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("{")) {
                    continue;
                }
                String raw = extractFieldFromSerializedLine(trimmed, "lemma");
                if (raw == null) continue;
                String lemma = normalizeLemma(raw);
                if (lemma.isEmpty()) continue;
                existing.computeIfAbsent(lemma, k -> new ArrayList<>()).add(trimmed);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to read morphology output file: " + outputFilePath, e);
        }
        return existing;
    }

    /***************************************************************
     * Loads morphology output into a synsetId -> JSON object index.
     ***************************************************************/
    public static Map<String, List<ObjectNode>> loadClassificationObjects(String outputFilePath) {

        Map<String, List<ObjectNode>> parsed = new HashMap<>();
        if (outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return parsed;
        }
        Path path = Paths.get(outputFilePath);
        if (!Files.exists(path)) {
            return parsed;
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ObjectNode node = parseJsonObjectLine(line);
                if (node == null) {
                    continue;
                }
                String synsetId = node.path("synsetId").asText("").trim();
                if (synsetId.isEmpty()) {
                    System.err.println("Missing synsetId in morphology output file '" + outputFilePath + "' line: " + line);
                    System.exit(1);
                }
                parsed.computeIfAbsent(synsetId, key -> new ArrayList<>()).add(node);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse morphology output file: " + outputFilePath, e);
        }
        return parsed;
    }

    /***************************************************************
     * Determines whether a synset has already been processed.
     ***************************************************************/
    public static boolean alreadyClassified(Map<String, List<String>> existing, String synsetId) {

        if (existing == null || synsetId == null || synsetId.trim().isEmpty()) {
            return false;
        }
        List<String> serializedLines = existing.get(synsetId);
        return serializedLines != null && !serializedLines.isEmpty();
    }

    /***************************************************************
     * Adds the serialized array line to the in-memory index for the supplied synset.
     ***************************************************************/
    public static void cacheClassification(Map<String, List<String>> existing, String synsetId, String serializedLine) {

        if (existing == null || synsetId == null || synsetId.trim().isEmpty() || serializedLine == null) {
            return;
        }
        existing.computeIfAbsent(synsetId, key -> new ArrayList<>()).add(serializedLine);
    }

    private static String extractSynsetIdFromSerializedLine(String serializedLine) {

        if (!serializedLine.startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(serializedLine);
            JsonNode valueNode = node.get("synsetId");
            if (valueNode == null || valueNode.isNull()) {
                return null;
            }
            String value = valueNode.asText("").trim();
            return value.isEmpty() ? null : value;
        } catch (IOException ignored) {
            // ignore malformed JSON rows
        }
        return null;
    }

    /***************************************************************
     * Converts the first JSON object found in the LLM response into an ObjectNode.
     * Ensures that the required fields are present and non-null.
     ***************************************************************/
    public static ObjectNode extractRequiredJsonObject(String llmResponse, List<String> requiredFields) {

        if (llmResponse == null) {
            return null;
        }
        String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
        if (jsonResponse == null) {
            return null;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(jsonResponse);
            if (!(node instanceof ObjectNode)) {
                return null;
            }
            ObjectNode objectNode = (ObjectNode) node;
            if (requiredFields != null) {
                for (String field : requiredFields) {
                    if (field == null) {
                        continue;
                    }
                    JsonNode valueNode = objectNode.get(field);
                    if (valueNode == null || valueNode.isNull()) {
                        return null;
                    }
                }
            }
            return objectNode;
        } catch (IOException e) {
            return null;
        }
    }

    /***************************************************************
     * Returns a copy of the supplied node with synsetId inserted first.
     ***************************************************************/
    public static ObjectNode prependSynsetId(ObjectNode node, String synsetId) {

        ObjectNode augmented = JSON_MAPPER.createObjectNode();
        augmented.put("synsetId", synsetId == null ? "" : synsetId);
        if (node == null) {
            return augmented;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!"synsetId".equals(field.getKey())) {
                augmented.set(field.getKey(), field.getValue());
            }
        }
        return augmented;
    }

    /***************************************************************
     * Returns a copy of the supplied node with canonical field order:
     * synsetId first, lemma second, then all remaining fields.
     ***************************************************************/
    public static ObjectNode prependSynsetIdAndLemma(ObjectNode node, String synsetId, String lemma) {

        ObjectNode augmented = JSON_MAPPER.createObjectNode();
        augmented.put("synsetId", synsetId == null ? "" : synsetId);
        augmented.put("lemma", lemma == null ? "" : lemma);
        if (node == null) {
            return augmented;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!"synsetId".equals(field.getKey()) && !"lemma".equals(field.getKey())) {
                augmented.set(field.getKey(), field.getValue());
            }
        }
        return augmented;
    }

    /***************************************************************
     * Standardizes error output so classification attempts are tracked.
     ***************************************************************/
    public static String buildErrorRecord(String termFieldName, String termValue, String lemma,
                                          String synsetId, String definition,
                                          String llmResponse, String errorMessage) {

        ObjectNode errorNode = JSON_MAPPER.createObjectNode();
        errorNode.put("synsetId", synsetId == null ? "" : synsetId);
        errorNode.put("lemma", lemma == null ? "" : lemma);
        String resolvedFieldName = (termFieldName == null || termFieldName.trim().isEmpty())
                ? "term"
                : termFieldName;
        errorNode.put(resolvedFieldName, termValue == null ? "" : termValue);
        errorNode.put("status", "error");
        errorNode.put("message", errorMessage == null ? "LLM response missing required fields." : errorMessage);
        errorNode.put("rawResponse", llmResponse == null
                ? ""
                : llmResponse.replace("\n", " ").replace("\r", " ").trim());
        return serializeJsonLine(errorNode);
    }

    /***************************************************************
     * Serializes the supplied JSON node on a single line for file output.
     ***************************************************************/
    public static String serializeJsonLine(ObjectNode node) {

        if (node == null) {
            throw new IllegalArgumentException("node cannot be null");
        }
        try {
            return JSON_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Unable to serialize JSON.", e);
        }
    }

    /***************************************************************
     * Appends strict low-token output instructions when cheap-prompt mode is enabled.
     ***************************************************************/
    public static String applyCheapPromptDirective(String prompt, List<String> jsonFields) {

        if (prompt == null || !GenUtils.isCheapPromptMode()) {
            return prompt;
        }
        String fields = "";
        if (jsonFields != null && !jsonFields.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < jsonFields.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(jsonFields.get(i));
            }
            fields = builder.toString();
        }
        return prompt +
                "\n\nCHEAP PROMPT MODE:\n" +
                "- Return JSON only.\n" +
                "- Include only these fields: " + fields + ".\n" +
                "- Do not include explanation or usage unless explicitly listed.\n" +
                "- Keep each value concise.";
    }

    /***************************************************************
     * Appends a single JSON line to an existing DB file, using two
     * layers of locking:
     *   1. Per-file synchronized block — intra-JVM thread safety
     *      (Java FileLock is NOT visible across threads in the same JVM).
     *   2. FileChannel.lock() — exclusive advisory lock across processes.
     * Does nothing if the file does not already exist (never creates files).
     ***************************************************************/
    public static void appendJsonLine(String filePath, ObjectNode node) {

        Object jvmLock = FILE_WRITE_LOCKS.computeIfAbsent(filePath, k -> new Object());
        synchronized (jvmLock) {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) return;
            String line = serializeJsonLine(node) + System.lineSeparator();
            try (FileChannel channel = FileChannel.open(path,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 FileLock fileLock = channel.lock()) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                System.out.println("MorphoDB: failed to persist to " + filePath
                        + ": " + e.getMessage());
            }
        }
    }

    static ObjectNode parseJsonObjectLine(String serializedLine) {

        if (serializedLine == null) {
            return null;
        }
        String trimmed = serializedLine.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(trimmed);
            return (node instanceof ObjectNode) ? (ObjectNode) node : null;
        } catch (IOException e) {
            return null;
        }
    }
}
