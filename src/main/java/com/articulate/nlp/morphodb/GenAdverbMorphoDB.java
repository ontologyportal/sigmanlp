package com.articulate.nlp.morphodb;

import java.util.Map;
import java.util.Set;

/***************************************************************
 *  Adverb-related morphological generation.
 ***************************************************************/
public class GenAdverbMorphoDB {

    private final Map<String, Set<String>> adverbSynsetHash;
    private final Map<String, String> adverbDocumentationHash;

    public GenAdverbMorphoDB(Map<String, Set<String>> adverbSynsetHash,
                             Map<String, String> adverbDocumentationHash) {
        this.adverbSynsetHash = adverbSynsetHash;
        this.adverbDocumentationHash = adverbDocumentationHash;
    }

    /***************************************************************
     * Adverb generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        System.out.println("Adverb morphological generation not yet implemented for function: " + genFunction);
    }
}
