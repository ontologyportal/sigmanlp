package com.articulate.nlp.morphodb;

import java.util.Map;
import java.util.Set;

/***************************************************************
 * Verb-related morphological generation.
 ***************************************************************/
public class GenVerbMorphoDB {

    private final Map<String, Set<String>> verbSynsetHash;
    private final Map<String, String> verbDocumentationHash;

    public GenVerbMorphoDB(Map<String, Set<String>> verbSynsetHash,
                           Map<String, String> verbDocumentationHash) {
        this.verbSynsetHash = verbSynsetHash;
        this.verbDocumentationHash = verbDocumentationHash;
    }

    /***************************************************************
     * Verb generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        System.out.println("Verb morphological generation not yet implemented for function: " + genFunction);
    }
}
