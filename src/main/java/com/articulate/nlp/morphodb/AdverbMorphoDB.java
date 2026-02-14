package com.articulate.nlp.morphodb;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/***************************************************************
 * Read-only adverb morphology database loaded from persisted files.
 ***************************************************************/
public class AdverbMorphoDB {

    public final Map<String, List<ObjectNode>> semanticClasses;

    public AdverbMorphoDB(Map<String, List<ObjectNode>> semanticClasses) {
        this.semanticClasses = semanticClasses;
    }

    public static AdverbMorphoDB load(String morphoDbPath) {

        return new AdverbMorphoDB(loadSemanticClasses(morphoDbPath));
    }

    private static Map<String, List<ObjectNode>> loadSemanticClasses(String morphoDbPath) {

        Path root = GenMorphoUtils.expandHomePath(morphoDbPath);
        return GenMorphoUtils.loadClassificationObjects(
                root.resolve("adverb").resolve("AdverbSemanticClasses.txt").toString());
    }
}
