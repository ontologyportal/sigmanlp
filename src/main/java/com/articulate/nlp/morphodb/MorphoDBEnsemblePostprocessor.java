package com.articulate.nlp.morphodb;

import com.articulate.nlp.morphodb.evaluation.GenerativeEvalUtils;
import com.articulate.nlp.morphodb.evaluation.MorphoDBLoader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Standalone postprocessor that creates ensemble MorphoDB snapshots by voting across
 * existing model directories under ~/.sigmanlp/MorphoDB_Research.
 */
public class MorphoDBEnsemblePostprocessor {

    private static final Path DEFAULT_ROOT = Path.of(System.getProperty("user.home"), ".sigmanlp", "MorphoDB_Research");

    private static final String INVALID_STATUS_FIELD = "categorizationStatus";
    private static final String INVALID_STATUS_VALUE = "invalid-categorization";
    private static final String INVALID_FIELD_FIELD = "invalidCategorizationField";
    private static final String INVALID_RAW_FIELD = "invalidCategorizationRaw";

    private static final String ENSEMBLE_CONSUMER = "ensemble_consumer";
    private static final String ENSEMBLE_HPC = "ensemble_HPC";

    private enum FileType {
        FLAT,
        PLURAL,
        CONJUGATION
    }

    private static final class FileSpec {
        final String relativePath;
        final String wordField;
        final List<String> votedFields;
        final List<String> outputFields;
        final List<String> requiredFields;
        final FileType type;
        final String invalidFieldName;

        FileSpec(String relativePath,
                 String wordField,
                 List<String> votedFields,
                 List<String> outputFields,
                 List<String> requiredFields,
                 FileType type,
                 String invalidFieldName) {
            this.relativePath = relativePath;
            this.wordField = wordField;
            this.votedFields = Collections.unmodifiableList(new ArrayList<>(votedFields));
            this.outputFields = Collections.unmodifiableList(new ArrayList<>(outputFields));
            this.requiredFields = Collections.unmodifiableList(new ArrayList<>(requiredFields));
            this.type = type;
            this.invalidFieldName = invalidFieldName;
        }
    }

    private static final class EnsembleGroup {
        final String outputName;
        final List<String> models;
        final Map<String, Integer> rankByModel;

        EnsembleGroup(String outputName, List<String> models) {
            this.outputName = outputName;
            this.models = Collections.unmodifiableList(new ArrayList<>(models));
            this.rankByModel = new LinkedHashMap<>();
            for (int i = 0; i < models.size(); i++) {
                this.rankByModel.put(models.get(i), i);
            }
        }
    }

    private static final class SourceRow {
        final String modelName;
        final String itemId;
        final JSONObject json;
        final boolean nonSuccess;
        final boolean invalidCategorization;
        final boolean validVote;
        final String voteKey;

        SourceRow(String modelName,
                  String itemId,
                  JSONObject json,
                  boolean nonSuccess,
                  boolean invalidCategorization,
                  boolean validVote,
                  String voteKey) {
            this.modelName = modelName;
            this.itemId = itemId;
            this.json = json;
            this.nonSuccess = nonSuccess;
            this.invalidCategorization = invalidCategorization;
            this.validVote = validVote;
            this.voteKey = voteKey;
        }
    }

    private enum SelectionReason {
        VOTE_WIN,
        TIE_FALLBACK,
        NO_VALID_VOTE_FALLBACK
    }

    private static final class Selection {
        final SourceRow row;
        final SelectionReason reason;

        Selection(SourceRow row, SelectionReason reason) {
            this.row = row;
            this.reason = reason;
        }
    }

    private static final class FileSummary {
        int written;
        int voteWins;
        int tieFallbacks;
        int noValidVoteFallbacks;
        int unrecoverable;
    }

    private static final List<FileSpec> FILE_SPECS = Collections.unmodifiableList(Arrays.asList(
            flatSpec("noun/IndefiniteArticles.txt", "noun",
                    Arrays.asList("article"),
                    Arrays.asList("noun", "article", "article_pattern"),
                    Arrays.asList("noun", "article"),
                    "article"),
            flatSpec("noun/Countability.txt", "noun",
                    Arrays.asList("countability"),
                    Arrays.asList("noun", "countability"),
                    Arrays.asList("noun", "countability"),
                    "countability"),
            new FileSpec("noun/Plurals.txt",
                    "singular",
                    Arrays.asList("singular", "plural", "type"),
                    Arrays.asList("singular", "plural", "type"),
                    Arrays.asList("singular", "plural", "type"),
                    FileType.PLURAL,
                    null),
            flatSpec("noun/Humanness.txt", "noun",
                    Arrays.asList("classification"),
                    Arrays.asList("noun", "classification"),
                    Arrays.asList("noun", "classification"),
                    "classification"),
            flatSpec("noun/NounAgentivity.txt", "noun",
                    Arrays.asList("agency", "agent_type"),
                    Arrays.asList("noun", "agency", "agent_type"),
                    Arrays.asList("noun", "agency", "agent_type"),
                    "agency"),
            flatSpec("noun/CollectiveNouns.txt", "noun",
                    Arrays.asList("collective"),
                    Arrays.asList("noun", "collective"),
                    Arrays.asList("noun", "collective"),
                    "collective"),
            flatSpec("verb/VerbValence.txt", "verb",
                    Arrays.asList("valence", "subtype", "semantic_roles"),
                    Arrays.asList("verb", "valence", "subtype", "semantic_roles"),
                    Arrays.asList("verb", "valence", "subtype", "semantic_roles"),
                    "valence"),
            flatSpec("verb/VerbReflexive.txt", "verb",
                    Arrays.asList("reflexivity"),
                    Arrays.asList("verb", "reflexivity"),
                    Arrays.asList("verb", "reflexivity"),
                    "reflexivity"),
            flatSpec("verb/VerbCausativity.txt", "verb",
                    Arrays.asList("causativity"),
                    Arrays.asList("verb", "causativity"),
                    Arrays.asList("verb", "causativity"),
                    "causativity"),
            flatSpec("verb/VerbAchievementProcess.txt", "verb",
                    Arrays.asList("aktionsart"),
                    Arrays.asList("verb", "aktionsart"),
                    Arrays.asList("verb", "aktionsart"),
                    "aktionsart"),
            flatSpec("verb/VerbReciprocal.txt", "verb",
                    Arrays.asList("reciprocity"),
                    Arrays.asList("verb", "reciprocity"),
                    Arrays.asList("verb", "reciprocity"),
                    "reciprocity"),
            new FileSpec("verb/VerbConjugations.txt",
                    "verb",
                    Arrays.asList("regularity", "tenses"),
                    Arrays.asList("verb", "regularity", "tenses"),
                    Arrays.asList("verb", "regularity", "tenses"),
                    FileType.CONJUGATION,
                    "regularity"),
            flatSpec("adjective/AdjectiveSemanticClasses.txt", "adjective",
                    Arrays.asList("category"),
                    Arrays.asList("adjective", "category"),
                    Arrays.asList("adjective", "category"),
                    "category"),
            flatSpec("adverb/AdverbSemanticClasses.txt", "adverb",
                    Arrays.asList("category"),
                    Arrays.asList("adverb", "category"),
                    Arrays.asList("adverb", "category"),
                    "category")
    ));

    private static final List<EnsembleGroup> GROUPS = Collections.unmodifiableList(Arrays.asList(
            new EnsembleGroup(ENSEMBLE_CONSUMER, Arrays.asList(
                    "llama3_2",
                    "gemma3_4b",
                    "mistral",
                    "llama3_1_8b",
                    "qwen3-8b",
                    "phi3_14b"
            )),
            new EnsembleGroup(ENSEMBLE_HPC, Arrays.asList(
                    "gpt-oss-20b",
                    "gemma-3-27b-it",
                    "qwen3-32b",
                    "llama-3_3-70b-instruct",
                    "gpt-oss-120b"
            ))
    ));

    public static void main(String[] args) throws Exception {

        if (args != null && args.length > 0) {
            System.out.println("Ignoring CLI arguments and using default MorphoDB root: " + DEFAULT_ROOT);
        }
        buildEnsembles(DEFAULT_ROOT);
    }

    private static FileSpec flatSpec(String relativePath,
                                     String wordField,
                                     List<String> votedFields,
                                     List<String> outputFields,
                                     List<String> requiredFields,
                                     String invalidFieldName) {

        return new FileSpec(relativePath, wordField, votedFields, outputFields, requiredFields, FileType.FLAT, invalidFieldName);
    }

    private static void buildEnsembles(Path root) throws IOException {

        Path resolvedRoot = root.toAbsolutePath().normalize();
        validateInputs(resolvedRoot);

        Map<EnsembleGroup, Path> tempDirs = new LinkedHashMap<>();
        Map<EnsembleGroup, Map<String, FileSummary>> summaries = new LinkedHashMap<>();

        try {
            for (EnsembleGroup group : GROUPS) {
                Path tempDir = createTempOutputDir(resolvedRoot, group.outputName);
                tempDirs.put(group, tempDir);
                summaries.put(group, buildGroupEnsemble(resolvedRoot, group, tempDir));
            }

            replaceOutputs(resolvedRoot, tempDirs);
            printSummaries(resolvedRoot, summaries);
        } catch (Exception e) {
            cleanupTemps(tempDirs.values());
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed building ensemble MorphoDBs: " + e.getMessage(), e);
        }
    }

    private static void validateInputs(Path root) throws IOException {

        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            errors.add("MorphoDB root not found: " + root);
        }

        for (EnsembleGroup group : GROUPS) {
            for (String model : group.models) {
                Path modelRoot = root.resolve(model);
                if (!Files.isDirectory(modelRoot)) {
                    errors.add("Missing model directory: " + modelRoot);
                    continue;
                }
                for (FileSpec spec : FILE_SPECS) {
                    Path file = modelRoot.resolve(spec.relativePath);
                    if (!Files.exists(file)) {
                        errors.add("Missing required file: " + file);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            for (String error : errors) {
                System.out.println(error);
            }
            throw new IOException("Ensemble postprocessor validation failed.");
        }
    }

    private static Path createTempOutputDir(Path root, String outputName) throws IOException {

        Path tempDir = root.resolve("." + outputName + ".tmp-" + System.nanoTime());
        Files.createDirectories(tempDir);
        return tempDir;
    }

    private static Map<String, FileSummary> buildGroupEnsemble(Path root,
                                                               EnsembleGroup group,
                                                               Path tempDir) throws IOException {

        Map<String, FileSummary> summaries = new LinkedHashMap<>();
        for (FileSpec spec : FILE_SPECS) {
            FileSummary summary = writeEnsembleFile(root, group, tempDir, spec);
            summaries.put(spec.relativePath, summary);
        }
        return summaries;
    }

    private static FileSummary writeEnsembleFile(Path root,
                                                 EnsembleGroup group,
                                                 Path tempDir,
                                                 FileSpec spec) throws IOException {

        Map<String, Map<String, SourceRow>> byModel = new LinkedHashMap<>();
        TreeSet<String> itemIds = new TreeSet<>();
        for (String model : group.models) {
            Path inputFile = root.resolve(model).resolve(spec.relativePath);
            Map<String, SourceRow> rows = loadLatestRows(inputFile, model, spec);
            byModel.put(model, rows);
            itemIds.addAll(rows.keySet());
        }

        FileSummary summary = new FileSummary();
        Path outputFile = tempDir.resolve(spec.relativePath);
        Files.createDirectories(outputFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String itemId : itemIds) {
                List<SourceRow> itemRows = new ArrayList<>();
                for (String model : group.models) {
                    SourceRow row = byModel.get(model).get(itemId);
                    if (row != null) {
                        itemRows.add(row);
                    }
                }

                Selection selection = selectRow(group, itemRows);
                if (selection == null || selection.row == null) {
                    summary.unrecoverable++;
                    continue;
                }

                JSONObject outputJson = buildOutputJson(selection.row, spec);
                if (outputJson == null || outputJson.length() == 0) {
                    summary.unrecoverable++;
                    continue;
                }

                writer.write(outputJson.toString());
                writer.newLine();
                summary.written++;

                if (selection.reason == SelectionReason.VOTE_WIN) {
                    summary.voteWins++;
                } else if (selection.reason == SelectionReason.TIE_FALLBACK) {
                    summary.tieFallbacks++;
                } else if (selection.reason == SelectionReason.NO_VALID_VOTE_FALLBACK) {
                    summary.noValidVoteFallbacks++;
                }
            }
        }
        return summary;
    }

    private static Map<String, SourceRow> loadLatestRows(Path file,
                                                         String modelName,
                                                         FileSpec spec) throws IOException {

        Map<String, SourceRow> rows = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject json = new JSONObject(trimmed);
                    String itemId = buildItemId(json);
                    if (itemId.isEmpty()) {
                        continue;
                    }
                    rows.put(itemId, buildSourceRow(modelName, itemId, json, spec));
                } catch (Exception ignored) {
                }
            }
        }
        return rows;
    }

    private static SourceRow buildSourceRow(String modelName,
                                            String itemId,
                                            JSONObject json,
                                            FileSpec spec) {

        boolean nonSuccess = MorphoDBLoader.isNonSuccessRecord(json);
        boolean invalidCategorization = isInvalidCategorizationForSpec(json, spec);
        boolean validVote = false;
        String voteKey = null;

        if (!nonSuccess && !invalidCategorization) {
            switch (spec.type) {
                case FLAT:
                    validVote = hasAllRequiredFields(json, spec.requiredFields);
                    if (validVote) {
                        voteKey = buildFlatVoteKey(json, spec);
                    }
                    break;
                case PLURAL:
                    validVote = hasAllRequiredFields(json, spec.requiredFields) && buildPluralVoteKey(json) != null;
                    if (validVote) {
                        voteKey = buildPluralVoteKey(json);
                    }
                    break;
                case CONJUGATION:
                    validVote = hasAllRequiredFields(json, Arrays.asList("verb", "regularity", "tenses"));
                    if (validVote) {
                        voteKey = buildConjugationVoteKey(json);
                        validVote = voteKey != null;
                    }
                    break;
                default:
                    break;
            }
        }

        return new SourceRow(modelName, itemId, json, nonSuccess, invalidCategorization, validVote, voteKey);
    }

    private static boolean hasAllRequiredFields(JSONObject json, List<String> fields) {

        for (String field : fields) {
            Object value = json.opt(field);
            if (!hasMeaningfulValue(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasMeaningfulValue(Object value) {

        if (value == null || value == JSONObject.NULL) {
            return false;
        }
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).length() > 0;
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).length() > 0;
        }
        return true;
    }

    private static boolean isInvalidCategorizationForSpec(JSONObject json, FileSpec spec) {

        return spec.invalidFieldName != null
                && INVALID_STATUS_VALUE.equals(json.optString(INVALID_STATUS_FIELD, ""))
                && spec.invalidFieldName.equals(json.optString(INVALID_FIELD_FIELD, ""));
    }

    private static String buildItemId(JSONObject json) {

        String synsetId = json.optString("synsetId", "").trim();
        String normalizedLemma = GenMorphoUtils.normalizeLemma(json.optString("lemma", ""));
        if (synsetId.isEmpty() || normalizedLemma.isEmpty()) {
            return "";
        }
        return synsetId + "|" + normalizedLemma;
    }

    private static String buildFlatVoteKey(JSONObject json, FileSpec spec) {

        StringBuilder sb = new StringBuilder();
        for (String field : spec.votedFields) {
            sb.append(field).append('=').append(canonicalizeValue(json.opt(field))).append('|');
        }
        return sb.toString();
    }

    private static String buildPluralVoteKey(JSONObject json) {

        GenerativeEvalUtils.PluralRecord record = GenerativeEvalUtils.extractPluralRecord(json);
        if (record == null) {
            return null;
        }
        String type = json.optString("type", "").trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return null;
        }
        return record.singularNormalized + "|" + record.pluralNormalized + "|" + collapseWhitespace(type);
    }

    private static String buildConjugationVoteKey(JSONObject json) {

        GenerativeEvalUtils.ConjugationRecord record = GenerativeEvalUtils.extractConjugationRecord(json);
        if (record == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(collapseWhitespace(record.regularity.toLowerCase(Locale.ROOT)));
        for (String slot : GenerativeEvalUtils.CONJUGATION_SLOTS) {
            sb.append('|').append(slot).append('=').append(canonicalizeValue(record.normalizedForms.get(slot)));
        }
        return sb.toString();
    }

    private static String canonicalizeValue(Object value) {

        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof String) {
            return collapseWhitespace(((String) value).trim().toLowerCase(Locale.ROOT));
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<String> values = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                values.add(canonicalizeValue(array.opt(i)));
            }
            Collections.sort(values);
            return "[" + String.join(",", values) + "]";
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            TreeSet<String> keys = new TreeSet<>(object.keySet());
            List<String> parts = new ArrayList<>();
            for (String key : keys) {
                parts.add(key + ":" + canonicalizeValue(object.opt(key)));
            }
            return "{" + String.join(",", parts) + "}";
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String value) {

        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static Selection selectRow(EnsembleGroup group, List<SourceRow> itemRows) {

        if (itemRows == null || itemRows.isEmpty()) {
            return null;
        }

        List<SourceRow> validRows = new ArrayList<>();
        for (SourceRow row : itemRows) {
            if (row.validVote && row.voteKey != null && !row.voteKey.isEmpty()) {
                validRows.add(row);
            }
        }

        if (!validRows.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            int maxCount = 0;
            for (SourceRow row : validRows) {
                int count = counts.getOrDefault(row.voteKey, 0) + 1;
                counts.put(row.voteKey, count);
                if (count > maxCount) {
                    maxCount = count;
                }
            }

            List<String> winners = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() == maxCount) {
                    winners.add(entry.getKey());
                }
            }

            if (winners.size() == 1) {
                String winner = winners.get(0);
                List<SourceRow> supporters = new ArrayList<>();
                for (SourceRow row : validRows) {
                    if (winner.equals(row.voteKey)) {
                        supporters.add(row);
                    }
                }
                return new Selection(selectLargest(group, supporters), SelectionReason.VOTE_WIN);
            }

            return new Selection(selectLargest(group, itemRows), SelectionReason.TIE_FALLBACK);
        }

        return new Selection(selectLargest(group, itemRows), SelectionReason.NO_VALID_VOTE_FALLBACK);
    }

    private static SourceRow selectLargest(EnsembleGroup group, Collection<SourceRow> rows) {

        SourceRow best = null;
        int bestRank = Integer.MIN_VALUE;
        for (SourceRow row : rows) {
            int rank = group.rankByModel.getOrDefault(row.modelName, Integer.MIN_VALUE);
            if (best == null || rank > bestRank) {
                best = row;
                bestRank = rank;
            }
        }
        return best;
    }

    private static JSONObject buildOutputJson(SourceRow row, FileSpec spec) {

        if (row == null || row.json == null) {
            return null;
        }
        if (row.nonSuccess) {
            return buildMinimalStatusJson(row, spec);
        }
        if (row.invalidCategorization) {
            return buildMinimalInvalidCategorizationJson(row, spec);
        }

        switch (spec.type) {
            case FLAT:
                return projectFlatJson(row.json, spec);
            case PLURAL:
                return projectPluralJson(row.json);
            case CONJUGATION:
                return projectConjugationJson(row.json);
            default:
                return null;
        }
    }

    private static JSONObject projectFlatJson(JSONObject source, FileSpec spec) {

        JSONObject out = new JSONObject();
        copyIdentityFields(source, out);
        for (String field : spec.outputFields) {
            copyFieldIfPresent(source, out, field);
        }
        return out;
    }

    private static JSONObject projectPluralJson(JSONObject source) {

        JSONObject out = new JSONObject();
        copyIdentityFields(source, out);
        String singular = source.optString("singular", source.optString("lemma", "")).trim();
        if (!singular.isEmpty()) {
            out.put("singular", singular);
        }
        copyFieldIfPresent(source, out, "plural");
        copyFieldIfPresent(source, out, "type");
        return out;
    }

    private static JSONObject projectConjugationJson(JSONObject source) {

        JSONObject out = new JSONObject();
        copyIdentityFields(source, out);
        String verb = source.optString("verb", source.optString("lemma", "")).trim();
        if (!verb.isEmpty()) {
            out.put("verb", verb);
        }
        copyFieldIfPresent(source, out, "regularity");

        JSONArray tenses = source.optJSONArray("tenses");
        if (tenses != null) {
            JSONArray stripped = stripTenses(tenses);
            if (stripped.length() > 0) {
                out.put("tenses", stripped);
            }
        }
        return out;
    }

    private static JSONArray stripTenses(JSONArray sourceTenses) {

        JSONArray out = new JSONArray();
        for (int i = 0; i < sourceTenses.length(); i++) {
            JSONObject tense = sourceTenses.optJSONObject(i);
            if (tense == null) {
                continue;
            }
            String tenseName = tense.optString("tense", "").trim();
            JSONObject forms = tense.optJSONObject("forms");
            if (tenseName.isEmpty() || forms == null || forms.length() == 0) {
                continue;
            }
            JSONObject stripped = new JSONObject();
            stripped.put("tense", tenseName);
            stripped.put("forms", new JSONObject(forms.toString()));
            out.put(stripped);
        }
        return out;
    }

    private static JSONObject buildMinimalStatusJson(SourceRow row, FileSpec spec) {

        JSONObject out = new JSONObject();
        copyIdentityFields(row.json, out);
        copySurfaceFieldIfPresent(row.json, out, spec.wordField);
        String status = row.json.optString("status", "").trim();
        if (!status.isEmpty()) {
            out.put("status", status);
        }
        return out;
    }

    private static JSONObject buildMinimalInvalidCategorizationJson(SourceRow row, FileSpec spec) {

        JSONObject out = new JSONObject();
        copyIdentityFields(row.json, out);
        copySurfaceFieldIfPresent(row.json, out, spec.wordField);
        out.put(INVALID_STATUS_FIELD, INVALID_STATUS_VALUE);
        out.put(INVALID_FIELD_FIELD, row.json.optString(INVALID_FIELD_FIELD, spec.invalidFieldName));
        copyFieldIfPresent(row.json, out, INVALID_RAW_FIELD);
        return out;
    }

    private static void copyIdentityFields(JSONObject source, JSONObject out) {

        copyFieldIfPresent(source, out, "synsetId");
        copyFieldIfPresent(source, out, "lemma");
    }

    private static void copySurfaceFieldIfPresent(JSONObject source, JSONObject out, String fieldName) {

        if (fieldName == null || fieldName.trim().isEmpty()) {
            return;
        }
        if ("singular".equals(fieldName)) {
            String singular = source.optString("singular", source.optString("lemma", "")).trim();
            if (!singular.isEmpty()) {
                out.put("singular", singular);
            }
            return;
        }
        copyFieldIfPresent(source, out, fieldName);
    }

    private static void copyFieldIfPresent(JSONObject source, JSONObject out, String fieldName) {

        Object value = source.opt(fieldName);
        if (!hasMeaningfulValue(value)) {
            return;
        }
        out.put(fieldName, deepCopyJsonValue(value));
    }

    private static Object deepCopyJsonValue(Object value) {

        if (value instanceof JSONObject) {
            return new JSONObject(value.toString());
        }
        if (value instanceof JSONArray) {
            return new JSONArray(value.toString());
        }
        return value;
    }

    private static void replaceOutputs(Path root,
                                       Map<EnsembleGroup, Path> tempDirs) throws IOException {
        for (EnsembleGroup group : GROUPS) {
            Path tempDir = tempDirs.get(group);
            Path targetDir = root.resolve(group.outputName);
            if (Files.exists(targetDir)) {
                deleteRecursively(targetDir);
            }
            try {
                Files.move(tempDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempDir, targetDir);
            }
        }
    }

    private static void cleanupTemps(Collection<Path> tempDirs) {

        for (Path tempDir : tempDirs) {
            try {
                if (tempDir != null && Files.exists(tempDir)) {
                    deleteRecursively(tempDir);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {

        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed deleting " + path + ": " + e.getMessage(), e);
                    }
                });
    }

    private static void printSummaries(Path root,
                                       Map<EnsembleGroup, Map<String, FileSummary>> summaries) {

        System.out.println("Ensemble MorphoDB postprocessor completed.");
        System.out.println("Output root: " + root);
        for (EnsembleGroup group : GROUPS) {
            System.out.println();
            System.out.println("[" + group.outputName + "]");
            Map<String, FileSummary> byFile = summaries.get(group);
            for (FileSpec spec : FILE_SPECS) {
                FileSummary summary = byFile.get(spec.relativePath);
                System.out.printf(Locale.ROOT,
                        "  %-34s written=%d  voteWins=%d  tieFallbacks=%d  noValidVoteFallbacks=%d  unrecoverable=%d%n",
                        spec.relativePath,
                        summary.written,
                        summary.voteWins,
                        summary.tieFallbacks,
                        summary.noValidVoteFallbacks,
                        summary.unrecoverable);
            }
        }
    }
}
