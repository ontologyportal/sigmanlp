package com.articulate.nlp.morphodb;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shared WordNet helpers used by MorphoDB generation and evaluation code.
 */
public final class MorphoWordNetUtils {

    public static final class LemmaSets {
        public final Set<String> nounLemmas;
        public final Set<String> verbLemmas;

        public LemmaSets(Set<String> nounLemmas, Set<String> verbLemmas) {
            this.nounLemmas = nounLemmas;
            this.verbLemmas = verbLemmas;
        }
    }

    private MorphoWordNetUtils() {
    }

    public static void initWordNet() {

        String sigmaHome = System.getenv("SIGMA_HOME");
        if (sigmaHome == null || sigmaHome.trim().isEmpty()) {
            throw new IllegalStateException("SIGMA_HOME is required to load filtered WordNet lemma sets");
        }
        KBmanager.getMgr().setPref("kbDir", sigmaHome + File.separator + "KBs");
        WordNet.initOnce();
    }

    public static Map<String, Set<String>> filteredSortedCopy(Map<String, Set<String>> raw, String label) {

        Map<String, Set<String>> copy = new TreeMap<>(raw);
        GenMorphoUtils.removeNonEnglishWords(copy, label);
        return copy;
    }

    public static Set<String> buildNormalizedLemmaSet(Map<String, Set<String>> synsetHash) {

        Set<String> result = new LinkedHashSet<>();
        for (String lemma : synsetHash.keySet()) {
            String normalized = GenMorphoUtils.normalizeLemma(lemma);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    public static LemmaSets loadFilteredNormalizedNounVerbLemmaSets() {

        initWordNet();
        return new LemmaSets(
                buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.nounSynsetHash, "noun set")),
                buildNormalizedLemmaSet(filteredSortedCopy(WordNet.wn.verbSynsetHash, "verb set"))
        );
    }
}
