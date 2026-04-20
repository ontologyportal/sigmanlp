package com.articulate.nlp.semanticRetrieval.buildEmbeddingIndex;

import com.articulate.nlp.KBLite;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

/***************************************************************
 * Exports a deterministic JSONL snapshot of the SUMO ontology for
 * downstream semantic retrieval tooling.
 ***************************************************************/
public class SUMOJsonlExporter {

    private static final String DEFAULT_KB_NAME = "SUMO";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /***************************************************************
     * Immutable DTO for a single ontology export row.
     ***************************************************************/
    public static class OntologyExportRow {

        public final long rowId;
        public final String parentClass;
        public final String sumoType;
        public final List<String> englishEquivalents;
        public final String definition;

        public OntologyExportRow(long rowId,
                                 String parentClass,
                                 String sumoType,
                                 List<String> englishEquivalents,
                                 String definition) {

            this.rowId = rowId;
            this.parentClass = parentClass;
            this.sumoType = sumoType;
            this.englishEquivalents = Collections.unmodifiableList(new ArrayList<>(englishEquivalents));
            this.definition = definition;
        }

        /***************************************************************
         * Serialize the row to the JSON shape expected by the downstream
         * Python consumer.
         ***************************************************************/
        public com.fasterxml.jackson.databind.node.ObjectNode toJsonNode(ObjectMapper mapper) {

            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.put("row_id", rowId);
            if (parentClass == null) {
                node.putNull("parent_class");
            }
            else {
                node.put("parent_class", parentClass);
            }
            node.put("sumo_type", sumoType);
            com.fasterxml.jackson.databind.node.ArrayNode englishArray = node.putArray("english_equivalents");
            for (String equivalent : englishEquivalents) {
                englishArray.add(equivalent);
            }
            if (definition == null) {
                node.putNull("definition");
            }
            else {
                node.put("definition", definition);
            }
            return node;
        }
    }

    /***************************************************************
     * Usage:
     * java com.articulate.nlp.semanticRetrieval.buildEmbeddingIndex.SUMOJsonlExporter output.jsonl [kbName]
     ***************************************************************/
    public static void main(String[] args) throws IOException {

        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: SUMOJsonlExporter <output.jsonl> [kbName]");
            return;
        }

        Path outputFile = Paths.get(args[0]);
        String kbName = args.length == 2 ? args[1] : DEFAULT_KB_NAME;
        exportOntologyJsonl(outputFile, kbName);
    }

    /***************************************************************
     * Build the export rows and write them as JSON Lines.
     ***************************************************************/
    public static void exportOntologyJsonl(Path outputFile, String kbName) throws IOException {

        KBLite kbLite = new KBLite(kbName);
        List<OntologyExportRow> rows = buildRows(kbLite);

        Path parentDir = outputFile.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (OntologyExportRow row : rows) {
                writer.write(JSON_MAPPER.writeValueAsString(row.toJsonNode(JSON_MAPPER)));
                writer.newLine();
            }
        }

        System.out.println("SUMOJsonlExporter: wrote " + rows.size() + " rows to " + outputFile.toAbsolutePath());
    }

    /***************************************************************
     * Build one export row per SUMO type from KBLite.
     *
     * If a SUMO type has multiple direct parents, only the first parent
     * encountered in KBLite.parentsOf is exported.
     ***************************************************************/
    public static List<OntologyExportRow> buildRows(KBLite kbLite) {

        TreeSet<String> sumoTypes = new TreeSet<>();
        sumoTypes.addAll(kbLite.terms);
        sumoTypes.addAll(kbLite.relations);
        sumoTypes.addAll(kbLite.functions);

        List<OntologyExportRow> rows = new ArrayList<>();
        long nextRowId = 1L;
        for (String sumoType : sumoTypes) {
            if (sumoType == null || sumoType.trim().isEmpty()) {
                continue;
            }
            if (sumoType.startsWith("(") || sumoType.startsWith("?")) {
                continue;
            }
            String parentClass = getFirstParentTerm(kbLite, sumoType);
            List<String> englishEquivalents = normalizeTermFormats(kbLite.getTermFormatMap().get(sumoType));
            String definition = normalizeDocumentation(kbLite.getDocumentation(sumoType));

            rows.add(new OntologyExportRow(
                    nextRowId++,
                    parentClass,
                    sumoType,
                    englishEquivalents,
                    definition));
        }
        return rows;
    }

    private static String getFirstParentTerm(KBLite kbLite, String sumoType) {

        List<String> rawParents = kbLite.parentsOf.get(sumoType);
        if (rawParents == null || rawParents.isEmpty()) {
            return null;
        }

        for (String parentTerm : rawParents) {
            if (parentTerm != null && !parentTerm.trim().isEmpty()) {
                return parentTerm;
            }
        }
        return null;
    }

    private static List<String> normalizeTermFormats(List<String> rawEquivalents) {

        if (rawEquivalents == null || rawEquivalents.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalizedEquivalents = new LinkedHashSet<>();
        for (String rawEquivalent : rawEquivalents) {
            if (rawEquivalent != null && !rawEquivalent.trim().isEmpty()) {
                normalizedEquivalents.add(rawEquivalent);
            }
        }
        return new ArrayList<>(normalizedEquivalents);
    }

    private static String normalizeDocumentation(String rawDefinition) {

        if (rawDefinition == null) {
            return null;
        }

        String normalized = rawDefinition.trim();
        normalized = normalized.replaceAll("^\"|\"$", "");
        normalized = normalized.replace("&%", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
