package com.articulate.nlp.morphodb;

import java.util.Map;
import java.util.Set;

/***************************************************************
 *  Adjective-related morphological generation.
 ***************************************************************/
public class GenAdjectiveMorphoDB {

    private final Map<String, Set<String>> adjectiveSynsetHash;
    private final Map<String, String> adjectiveDocumentationHash;

    public GenAdjectiveMorphoDB(Map<String, Set<String>> adjectiveSynsetHash,
                                Map<String, String> adjectiveDocumentationHash) {
        this.adjectiveSynsetHash = adjectiveSynsetHash;
        this.adjectiveDocumentationHash = adjectiveDocumentationHash;
    }

    /***************************************************************
     * Adjective generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        System.out.println("Adjective morphological generation not yet implemented for function: " + genFunction);
    }
}
