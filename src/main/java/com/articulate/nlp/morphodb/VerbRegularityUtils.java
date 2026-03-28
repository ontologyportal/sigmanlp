package com.articulate.nlp.morphodb;

import com.articulate.nlp.morphodb.evaluation.GenerativeEvalUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared helper for deriving binary verb regularity from normalized forms.
 */
public final class VerbRegularityUtils {

    private enum InflectionLayout {
        WHOLE_LEMMA,
        HEAD_FIRST_WITH_SUFFIX,
        HEAD_LAST_WITH_PREFIX
    }

    private static final class LemmaPattern {
        final String head;
        final String fixedPrefix;
        final String fixedSuffix;
        final InflectionLayout layout;

        LemmaPattern(String head, String fixedPrefix, String fixedSuffix, InflectionLayout layout) {
            this.head = head;
            this.fixedPrefix = fixedPrefix;
            this.fixedSuffix = fixedSuffix;
            this.layout = layout;
        }
    }

    private VerbRegularityUtils() {
    }

    public static String classifyRegularity(String lemma, Map<String, String> normalizedForms) {
        if (normalizedForms == null || normalizedForms.isEmpty()) {
            return "Unknown";
        }
        Set<String> simplePastForms = new LinkedHashSet<>();
        Set<String> pastParticipleForms = new LinkedHashSet<>();
        addIfPresent(simplePastForms, normalizedForms.get(GenerativeEvalUtils.SLOT_SIMPLE_PAST));
        addIfPresent(pastParticipleForms, normalizedForms.get(GenerativeEvalUtils.SLOT_PAST_PARTICIPLE));
        return classifyRegularity(lemma, simplePastForms, pastParticipleForms);
    }

    public static String classifyRegularity(String lemma,
                                            Set<String> simplePastForms,
                                            Set<String> pastParticipleForms) {
        String normalizedLemma = GenMorphoUtils.normalizeLemma(lemma);
        if (normalizedLemma.isEmpty()) {
            return "Unknown";
        }

        Set<String> safeSimplePastForms = simplePastForms == null
                ? Collections.<String>emptySet()
                : simplePastForms;
        Set<String> safePastParticipleForms = pastParticipleForms == null
                ? Collections.<String>emptySet()
                : pastParticipleForms;
        if (safeSimplePastForms.isEmpty() && safePastParticipleForms.isEmpty()) {
            return "Unknown";
        }

        for (String form : safeSimplePastForms) {
            if (!matchesRegularPastOrParticiple(normalizedLemma, form)) {
                return "Irregular";
            }
        }
        for (String form : safePastParticipleForms) {
            if (!matchesRegularPastOrParticiple(normalizedLemma, form)) {
                return "Irregular";
            }
        }
        return "Regular";
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (target == null || value == null) {
            return;
        }
        String normalized = GenMorphoUtils.normalizeLemma(value);
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    private static boolean matchesRegularPastOrParticiple(String lemma, String surface) {
        String normalizedLemma = GenMorphoUtils.normalizeLemma(lemma);
        String normalizedSurface = GenMorphoUtils.normalizeLemma(surface);
        if (normalizedLemma.isEmpty() || normalizedSurface.isEmpty()) {
            return false;
        }
        LemmaPattern pattern = analyzeLemma(normalizedLemma);
        if (pattern.layout == InflectionLayout.HEAD_FIRST_WITH_SUFFIX) {
            if (!normalizedSurface.endsWith(pattern.fixedSuffix)) {
                return false;
            }
            String headSurface = normalizedSurface.substring(0, normalizedSurface.length() - pattern.fixedSuffix.length()).trim();
            return !headSurface.isEmpty() && matchesRegularToken(pattern.head, headSurface);
        }
        if (pattern.layout == InflectionLayout.HEAD_LAST_WITH_PREFIX) {
            if (!normalizedSurface.startsWith(pattern.fixedPrefix)) {
                return false;
            }
            String headSurface = normalizedSurface.substring(pattern.fixedPrefix.length()).trim();
            return !headSurface.isEmpty() && matchesRegularToken(pattern.head, headSurface);
        }
        return matchesRegularToken(pattern.head, normalizedSurface);
    }

    private static boolean matchesRegularToken(String lemmaToken, String surfaceToken) {
        String lemma = normalizeToken(lemmaToken);
        String surface = normalizeToken(surfaceToken);
        if (lemma.isEmpty() || surface.isEmpty()) {
            return false;
        }

        if (surface.equals(lemma + "ed")) {
            return true;
        }
        if (lemma.endsWith("e") && surface.equals(lemma + "d")) {
            return true;
        }
        if (endsWithConsonantY(lemma) && surface.equals(lemma.substring(0, lemma.length() - 1) + "ied")) {
            return true;
        }
        return isDoubledConsonantPast(lemma, surface);
    }

    private static LemmaPattern analyzeLemma(String normalizedLemma) {
        int firstSpace = normalizedLemma.indexOf(' ');
        if (firstSpace >= 0) {
            return new LemmaPattern(
                    normalizedLemma.substring(0, firstSpace),
                    "",
                    normalizedLemma.substring(firstSpace),
                    InflectionLayout.HEAD_FIRST_WITH_SUFFIX
            );
        }

        int lastHyphen = normalizedLemma.lastIndexOf('-');
        if (lastHyphen > 0 && lastHyphen < normalizedLemma.length() - 1) {
            return new LemmaPattern(
                    normalizedLemma.substring(lastHyphen + 1),
                    normalizedLemma.substring(0, lastHyphen + 1),
                    "",
                    InflectionLayout.HEAD_LAST_WITH_PREFIX
            );
        }

        return new LemmaPattern(normalizedLemma, "", "", InflectionLayout.WHOLE_LEMMA);
    }

    private static boolean isDoubledConsonantPast(String lemma, String surface) {
        if (lemma.length() < 3 || surface.length() != lemma.length() + 3) {
            return false;
        }
        char last = lemma.charAt(lemma.length() - 1);
        if (!Character.isLetter(last) || "wxy".indexOf(last) >= 0) {
            return false;
        }
        char penultimate = lemma.charAt(lemma.length() - 2);
        char antepenultimate = lemma.charAt(lemma.length() - 3);
        if (!isConsonant(antepenultimate) || isConsonant(penultimate) || !isConsonant(last)) {
            return false;
        }
        return surface.equals(lemma + last + "ed");
    }

    private static boolean endsWithConsonantY(String value) {
        if (value.length() < 2 || !value.endsWith("y")) {
            return false;
        }
        return isConsonant(value.charAt(value.length() - 2));
    }

    private static boolean isConsonant(char c) {
        char lowered = Character.toLowerCase(c);
        return lowered >= 'a' && lowered <= 'z' && "aeiou".indexOf(lowered) < 0;
    }

    private static String normalizeToken(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ");
    }
}
