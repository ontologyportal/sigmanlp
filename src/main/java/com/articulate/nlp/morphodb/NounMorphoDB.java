package com.articulate.nlp.morphodb;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.HashMap;
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
    private final Map<String, String> articleByNoun;
    private final Map<String, String> pluralByNoun;

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
        this.articleByNoun = buildArticleByNounIndex(indefiniteArticles);
        this.pluralByNoun = buildPluralByNounIndex(plurals);
    }

    /***************************************************************
     * Builds a lowercase noun → article ("a"/"an") secondary index
     * from the synsetId-keyed indefiniteArticles map.
     ***************************************************************/
    private static Map<String, String> buildArticleByNounIndex(Map<String, List<ObjectNode>> indefiniteArticles) {

        Map<String, String> index = new HashMap<>();
        for (List<ObjectNode> nodes : indefiniteArticles.values()) {
            for (ObjectNode node : nodes) {
                String noun = node.path("noun").asText("").trim().toLowerCase();
                String article = node.path("article").asText("").trim().toLowerCase();
                if (!noun.isEmpty() && ("a".equals(article) || "an".equals(article))) {
                    index.putIfAbsent(noun, article);
                }
            }
        }
        return index;
    }

    /***************************************************************
     * Returns "a" or "an" for the given noun surface string, or
     * null if the noun is not in the database.
     ***************************************************************/
    public String getIndefiniteArticle(String noun) {

        if (noun == null || noun.isEmpty()) {
            return null;
        }
        return articleByNoun.get(noun.trim().toLowerCase());
    }

    /***************************************************************
     * Builds a lowercase singular → plural secondary index from the
     * synsetId-keyed plurals map. Entries with plural "none" are skipped.
     ***************************************************************/
    private static Map<String, String> buildPluralByNounIndex(Map<String, List<ObjectNode>> plurals) {

        Map<String, String> index = new HashMap<>();
        for (List<ObjectNode> nodes : plurals.values()) {
            for (ObjectNode node : nodes) {
                String singular = node.path("singular").asText("").trim().toLowerCase();
                String plural = node.path("plural").asText("").trim();
                if (!singular.isEmpty() && !plural.isEmpty() && !"none".equalsIgnoreCase(plural)) {
                    index.putIfAbsent(singular, plural);
                }
            }
        }
        return index;
    }

    /***************************************************************
     * Returns the plural form for the given noun surface string, or
     * null if the noun is not in the database.
     ***************************************************************/
    public String getPlural(String noun) {

        if (noun == null || noun.isEmpty()) {
            return null;
        }
        return pluralByNoun.get(noun.trim().toLowerCase());
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
