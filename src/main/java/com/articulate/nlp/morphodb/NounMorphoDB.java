package com.articulate.nlp.morphodb;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/***************************************************************
 * Read-only noun morphology database loaded from persisted files.
 ***************************************************************/
public class NounMorphoDB {

    public final Map<String, List<ObjectNode>> indefiniteArticles;
    public final Map<String, List<ObjectNode>> countability;
    public final Map<String, List<ObjectNode>> plurals;
    public final Map<String, List<ObjectNode>> humanness;
    public final Map<String, List<ObjectNode>> agentivity;
    public final Map<String, List<ObjectNode>> collectiveNouns;

    public NounMorphoDB(Map<String, List<ObjectNode>> indefiniteArticles,
                        Map<String, List<ObjectNode>> countability,
                        Map<String, List<ObjectNode>> plurals,
                        Map<String, List<ObjectNode>> humanness,
                        Map<String, List<ObjectNode>> agentivity,
                        Map<String, List<ObjectNode>> collectiveNouns) {
        this.indefiniteArticles = indefiniteArticles;
        this.countability = countability;
        this.plurals = plurals;
        this.humanness = humanness;
        this.agentivity = agentivity;
        this.collectiveNouns = collectiveNouns;
    }

    public static NounMorphoDB load(String morphoDbPath) {

        return new NounMorphoDB(
                loadIndefiniteArticles(morphoDbPath),
                loadCountability(morphoDbPath),
                loadPlurals(morphoDbPath),
                loadHumanness(morphoDbPath),
                loadAgentivity(morphoDbPath),
                loadCollectiveNouns(morphoDbPath));
    }

    private static Map<String, List<ObjectNode>> loadIndefiniteArticles(String morphoDbPath) {

        return loadNounClassifications("IndefiniteArticles.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadCountability(String morphoDbPath) {

        return loadNounClassifications("Countability.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadPlurals(String morphoDbPath) {

        return loadNounClassifications("Plurals.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadHumanness(String morphoDbPath) {

        return loadNounClassifications("Humanness.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadAgentivity(String morphoDbPath) {

        return loadNounClassifications("NounAgentivity.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadCollectiveNouns(String morphoDbPath) {

        return loadNounClassifications("CollectiveNouns.txt", morphoDbPath);
    }

    private static Map<String, List<ObjectNode>> loadNounClassifications(String fileName, String morphoDbPath) {

        Path root = GenMorphoUtils.expandHomePath(morphoDbPath);
        return GenMorphoUtils.loadClassificationObjects(root.resolve("noun").resolve(fileName).toString());
    }
}
