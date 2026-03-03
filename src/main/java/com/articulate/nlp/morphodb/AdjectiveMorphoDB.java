package com.articulate.nlp.morphodb;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/***************************************************************
 * Read-only adjective morphology database loaded from persisted files.
 ***************************************************************/
public class AdjectiveMorphoDB {

    public final Map<String, List<ObjectNode>> semanticClasses;

    public AdjectiveMorphoDB(Map<String, List<ObjectNode>> semanticClasses) {
        this.semanticClasses = semanticClasses;
    }

    public static AdjectiveMorphoDB load(String morphoDbPath) {

        return new AdjectiveMorphoDB(loadSemanticClasses(morphoDbPath));
    }

    private static Map<String, List<ObjectNode>> loadSemanticClasses(String morphoDbPath) {

        Path root = GenMorphoUtils.expandHomePath(morphoDbPath);
        return GenMorphoUtils.loadClassificationObjects(
                root.resolve("adjective").resolve("AdjectiveSemanticClasses.txt").toString());
    }
}
