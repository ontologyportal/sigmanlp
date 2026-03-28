package com.articulate.nlp.morphodb.evaluation;

import com.articulate.nlp.morphodb.GenMorphoUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Shared schema helpers for normalized UniMorph gold files.
 */
final class UniMorphGoldUtils {

    static final String DEFAULT_SOURCE_RELATIVE_PATH = ".sigmanlp/unimorph_4_0/eng/eng";
    static final String NOUN_OUTPUT_FILE = "unimorph_noun_plurals.jsonl";
    static final String VERB_OUTPUT_FILE = "unimorph_verb_conjugations.jsonl";
    static final String MANIFEST_OUTPUT_FILE = "unimorph_manifest.json";
    static final String NOUN_PROPERTY = "noun_plurals";
    static final String VERB_PROPERTY = "verb_conjugations";
    static final List<String> VERB_SLOTS = Arrays.asList(
            GenerativeEvalUtils.SLOT_INFINITIVE,
            GenerativeEvalUtils.SLOT_PRESENT_3SG,
            GenerativeEvalUtils.SLOT_SIMPLE_PAST,
            GenerativeEvalUtils.SLOT_PAST_PARTICIPLE,
            GenerativeEvalUtils.SLOT_GERUND
    );

    private UniMorphGoldUtils() {
    }

    static Path defaultSourcePath() {
        return Paths.get(System.getProperty("user.home"), ".sigmanlp", "unimorph_4_0", "eng", "eng");
    }

    static class NounGoldRecord {
        final String lemma;
        final LinkedHashSet<String> singularVariants = new LinkedHashSet<>();
        final LinkedHashSet<String> pluralVariants = new LinkedHashSet<>();
        final List<JSONObject> rawEvidence = new ArrayList<>();

        NounGoldRecord(String lemma) {
            this.lemma = lemma;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("lemma", lemma);
            json.put("singular_variants", toJsonArray(singularVariants));
            json.put("plural_variants", toJsonArray(pluralVariants));
            JSONArray evidenceArray = new JSONArray();
            for (JSONObject evidence : rawEvidence) {
                evidenceArray.put(new JSONObject(evidence.toString()));
            }
            json.put("raw_evidence", evidenceArray);
            return json;
        }

        static NounGoldRecord fromJson(JSONObject json) {
            NounGoldRecord record = new NounGoldRecord(GenMorphoUtils.normalizeLemma(json.optString("lemma", "")));
            addAll(record.singularVariants, json.optJSONArray("singular_variants"));
            addAll(record.pluralVariants, json.optJSONArray("plural_variants"));
            JSONArray evidenceArray = json.optJSONArray("raw_evidence");
            if (evidenceArray != null) {
                for (int i = 0; i < evidenceArray.length(); i++) {
                    JSONObject evidence = evidenceArray.optJSONObject(i);
                    if (evidence != null) {
                        record.rawEvidence.add(evidence);
                    }
                }
            }
            return record;
        }
    }

    static class VerbGoldRecord {
        final String lemma;
        final Map<String, LinkedHashSet<String>> slotVariants = new LinkedHashMap<>();
        final List<JSONObject> rawEvidence = new ArrayList<>();

        VerbGoldRecord(String lemma) {
            this.lemma = lemma;
            for (String slot : VERB_SLOTS) {
                slotVariants.put(slot, new LinkedHashSet<>());
            }
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("lemma", lemma);
            for (String slot : VERB_SLOTS) {
                json.put(slot + "_variants", toJsonArray(slotVariants.get(slot)));
            }
            JSONArray evidenceArray = new JSONArray();
            for (JSONObject evidence : rawEvidence) {
                evidenceArray.put(new JSONObject(evidence.toString()));
            }
            json.put("raw_evidence", evidenceArray);
            return json;
        }

        static VerbGoldRecord fromJson(JSONObject json) {
            VerbGoldRecord record = new VerbGoldRecord(GenMorphoUtils.normalizeLemma(json.optString("lemma", "")));
            for (String slot : VERB_SLOTS) {
                addAll(record.slotVariants.get(slot), json.optJSONArray(slot + "_variants"));
            }
            JSONArray evidenceArray = json.optJSONArray("raw_evidence");
            if (evidenceArray != null) {
                for (int i = 0; i < evidenceArray.length(); i++) {
                    JSONObject evidence = evidenceArray.optJSONObject(i);
                    if (evidence != null) {
                        record.rawEvidence.add(evidence);
                    }
                }
            }
            return record;
        }
    }

    static void writeNounGold(Path path, Collection<NounGoldRecord> records) throws IOException {
        writeJsonLines(path, sortNouns(records));
    }

    static void writeVerbGold(Path path, Collection<VerbGoldRecord> records) throws IOException {
        writeJsonLines(path, sortVerbs(records));
    }

    static Map<String, NounGoldRecord> loadNounGold(Path path) throws IOException {
        Map<String, NounGoldRecord> result = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JSONObject json = new JSONObject(trimmed);
                NounGoldRecord record = NounGoldRecord.fromJson(json);
                if (!record.lemma.isEmpty()) {
                    result.put(record.lemma, record);
                }
            }
        }
        return result;
    }

    static Map<String, VerbGoldRecord> loadVerbGold(Path path) throws IOException {
        Map<String, VerbGoldRecord> result = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JSONObject json = new JSONObject(trimmed);
                VerbGoldRecord record = VerbGoldRecord.fromJson(json);
                if (!record.lemma.isEmpty()) {
                    result.put(record.lemma, record);
                }
            }
        }
        return result;
    }

    private static void writeJsonLines(Path path, List<JSONObject> objects) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (JSONObject object : objects) {
                writer.write(object.toString());
                writer.newLine();
            }
        }
    }

    private static List<JSONObject> sortNouns(Collection<NounGoldRecord> records) {
        List<NounGoldRecord> sorted = new ArrayList<>(records);
        sorted.sort((left, right) -> left.lemma.compareTo(right.lemma));
        List<JSONObject> jsonObjects = new ArrayList<>();
        for (NounGoldRecord record : sorted) {
            jsonObjects.add(record.toJson());
        }
        return jsonObjects;
    }

    private static List<JSONObject> sortVerbs(Collection<VerbGoldRecord> records) {
        List<VerbGoldRecord> sorted = new ArrayList<>(records);
        sorted.sort((left, right) -> left.lemma.compareTo(right.lemma));
        List<JSONObject> jsonObjects = new ArrayList<>();
        for (VerbGoldRecord record : sorted) {
            jsonObjects.add(record.toJson());
        }
        return jsonObjects;
    }

    private static JSONArray toJsonArray(Collection<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private static void addAll(LinkedHashSet<String> target, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }
}
