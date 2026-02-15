package com.articulate.nlp.gensentences;

import com.articulate.nlp.morphodb.MorphoDB;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Helper for verb phrase realization and verb slot replacement.
 ***************************************************************/
public class GenVerbHelper {

    public static final String DEFAULT_VERB_TENSE = "Simple present";
    public static final String DEFAULT_GRAMMATICAL_PERSON = "he_she_it";

    private final MorphoDB morphoDB;

    public GenVerbHelper(MorphoDB morphoDB) {
        this.morphoDB = morphoDB;
    }

    /***************************************************************
     * Features used to realize one verb phrase.
     ***************************************************************/
    public static class VerbFeatures {
        public final String lemma;
        public final Templates.Tense tense;
        public final String grammaticalPerson;
        public final boolean negated;
        public final boolean question;

        public VerbFeatures(String lemma,
                            Templates.Tense tense,
                            String grammaticalPerson,
                            boolean negated,
                            boolean question) {
            this.lemma = lemma;
            this.tense = tense;
            this.grammaticalPerson = grammaticalPerson;
            this.negated = negated;
            this.question = question;
        }
    }

    /***************************************************************
     * Maps selected template tense to MorphoDB tense labels.
     ***************************************************************/
    public static String getMorphoVerbTense(Templates.Tense selectedTense) {

        if (selectedTense == null) {
            return DEFAULT_VERB_TENSE;
        }
        switch (selectedTense) {
            case PAST:
                return "Simple past";
            case FUTURE:
                return "Simple future";
            case PRESENT:
                return "Simple present";
            case NONE:
            default:
                return DEFAULT_VERB_TENSE;
        }
    }

    private static String normalizeLemma(String lemma) {

        if (lemma == null) {
            return "";
        }
        String normalized = lemma.trim().toLowerCase();
        if (normalized.startsWith("to ")) {
            normalized = normalized.substring(3).trim();
        }
        return normalized;
    }

    /***************************************************************
     * VerbFeatures -> surface phrase realizer (incremental scope).
     ***************************************************************/
    public String realizeVerbPhrase(VerbFeatures features) {

        String lemma = normalizeLemma(features.lemma);
        if (lemma.isEmpty()) {
            return "";
        }
        Templates.Tense tense = features.tense != null ? features.tense : Templates.Tense.NONE;
        if ("be".equals(lemma)) {
            return realizeBeVerb(features, tense);
        }
        return realizeNonBeVerb(features, lemma, tense);
    }

    private String realizeBeVerb(VerbFeatures features, Templates.Tense tense) {

        if (tense == Templates.Tense.FUTURE) {
            if (features.question) {
                return features.negated ? "not be" : "be";
            }
            return features.negated ? "will not be" : "will be";
        }
        String morphoVerbTense = getMorphoVerbTense(tense);
        String conjugated = morphoDB.getVerbConjugation("be", morphoVerbTense, features.grammaticalPerson);
        if (conjugated == null || conjugated.trim().isEmpty()) {
            conjugated = "is";
        }
        if (features.negated) {
            return conjugated + " not";
        }
        return conjugated;
    }

    private String realizeNonBeVerb(VerbFeatures features, String lemma, Templates.Tense tense) {

        if (features.question) {
            if (tense == Templates.Tense.FUTURE) {
                return features.negated ? "not " + lemma : lemma;
            }
            if (features.negated) {
                return "not " + lemma;
            }
            return lemma;
        }
        if (features.negated) {
            if (tense == Templates.Tense.PAST) {
                return "did not " + lemma;
            }
            if (tense == Templates.Tense.FUTURE) {
                return "will not " + lemma;
            }
            return "does not " + lemma;
        }
        String morphoVerbTense = getMorphoVerbTense(tense);
        String conjugated = morphoDB.getVerbConjugation(lemma, morphoVerbTense, features.grammaticalPerson);
        if (conjugated == null || conjugated.trim().isEmpty()) {
            return lemma;
        }
        return conjugated;
    }

    /***************************************************************
     * Replace %v1, %v2, ... verb slots in a template.
     ***************************************************************/
    public static String replaceVerbSlots(String frame, Map<Integer, String> verbValues) {

        Pattern pattern = Pattern.compile("%v(\\d+)");
        Matcher matcher = pattern.matcher(frame);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slotNum = Integer.parseInt(matcher.group(1));
            String value = verbValues.get(slotNum);
            if (value == null) {
                value = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
