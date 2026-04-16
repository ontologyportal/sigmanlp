package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/***************************************************************
 * Shared flat-schema normalization and recovery helpers used by
 * both generation-time code and compact-time recovery.
 ***************************************************************/
public final class MorphoFlatSchemaUtils {

    private MorphoFlatSchemaUtils() {
    }

    public static ObjectNode buildRecoveredFlatRecord(String fileName,
                                                      String synsetId,
                                                      String lemma,
                                                      String sourceValue,
                                                      Map<String, String> extracted) {

        if (fileName == null || synsetId == null || synsetId.trim().isEmpty()
                || lemma == null || lemma.trim().isEmpty()) {
            return null;
        }
        ObjectNode node = GenMorphoUtils.JSON_MAPPER.createObjectNode();
        node.put("synsetId", synsetId.trim());
        node.put("lemma", lemma.trim());

        if ("IndefiniteArticles.txt".equals(fileName)) {
            String noun = trimmedOrFallback(extracted.get("noun"), sourceValue);
            String article = normalizeArticleValue(extracted.get("article"));
            if (noun.isEmpty() || article.isEmpty()) {
                return null;
            }
            node.put("noun", noun);
            node.put("article", article);
            node.put("article_pattern", articlePatternFor(article, noun));
            return node;
        }
        if ("Countability.txt".equals(fileName)) {
            String noun = trimmedOrFallback(extracted.get("noun"), sourceValue);
            String countability = normalizeCountabilityCategory(extracted.get("countability"));
            if (noun.isEmpty() || countability.isEmpty()) {
                return null;
            }
            node.put("noun", noun);
            node.put("countability", countability);
            return node;
        }
        if ("Humanness.txt".equals(fileName)) {
            String noun = trimmedOrFallback(extracted.get("noun"), sourceValue);
            String classification = normalizeHumannessCategory(extracted.get("classification"));
            if (noun.isEmpty() || classification.isEmpty()) {
                return null;
            }
            node.put("noun", noun);
            node.put("classification", classification);
            return node;
        }
        if ("NounAgentivity.txt".equals(fileName)) {
            String noun = trimmedOrFallback(extracted.get("noun"), sourceValue);
            String agency = normalizeAgencyCategory(extracted.get("agency"));
            String agentType = normalizeAgentType(extracted.get("agent_type"), agency);
            if (noun.isEmpty() || agency.isEmpty() || agentType.isEmpty()) {
                return null;
            }
            node.put("noun", noun);
            node.put("agency", agency);
            node.put("agent_type", agentType);
            return node;
        }
        if ("CollectiveNouns.txt".equals(fileName)) {
            String noun = trimmedOrFallback(extracted.get("noun"), sourceValue);
            String collective = normalizeCollectiveCategory(extracted.get("collective"));
            if (noun.isEmpty() || collective.isEmpty()) {
                return null;
            }
            node.put("noun", noun);
            node.put("collective", collective);
            return node;
        }
        if ("Plurals.txt".equals(fileName)) {
            String singular = trimmedOrFallback(extracted.get("singular"), sourceValue);
            String plural = trimmedOrFallback(extracted.get("plural"), "");
            String type = trimmedOrFallback(extracted.get("type"), "");
            if (singular.isEmpty() || plural.isEmpty() || type.isEmpty()) {
                return null;
            }
            node.put("singular", singular);
            node.put("plural", plural);
            node.put("type", type);
            node.put("plural_pattern", pluralPatternFor(singular, plural));
            return node;
        }
        if ("VerbValence.txt".equals(fileName)) {
            String verb = trimmedOrFallback(extracted.get("verb"), sourceValue);
            String valence = normalizeValenceCategory(extracted.get("valence"));
            String subtype = normalizeSubtype(extracted.get("subtype"));
            String semanticRoles = normalizeSemanticRoles(extracted.get("semantic_roles"));
            if (verb.isEmpty() || valence.isEmpty() || subtype.isEmpty() || semanticRoles.isEmpty()) {
                return null;
            }
            node.put("verb", verb);
            node.put("valence", valence);
            node.put("subtype", subtype);
            node.put("semantic_roles", semanticRoles);
            return node;
        }
        if ("VerbReflexive.txt".equals(fileName)) {
            String verb = trimmedOrFallback(extracted.get("verb"), sourceValue);
            String reflexivity = normalizeReflexivityCategory(extracted.get("reflexivity"));
            if (verb.isEmpty() || reflexivity.isEmpty()) {
                return null;
            }
            node.put("verb", verb);
            node.put("reflexivity", reflexivity);
            return node;
        }
        if ("VerbCausativity.txt".equals(fileName)) {
            String verb = trimmedOrFallback(extracted.get("verb"), sourceValue);
            String causativity = normalizeCausativityCategory(extracted.get("causativity"));
            if (verb.isEmpty() || causativity.isEmpty()) {
                return null;
            }
            node.put("verb", verb);
            node.put("causativity", causativity);
            return node;
        }
        if ("VerbAchievementProcess.txt".equals(fileName)) {
            String verb = trimmedOrFallback(extracted.get("verb"), sourceValue);
            String aktionsart = normalizeAktionsartCategory(extracted.get("aktionsart"));
            if (verb.isEmpty() || aktionsart.isEmpty()) {
                return null;
            }
            node.put("verb", verb);
            node.put("aktionsart", aktionsart);
            return node;
        }
        if ("VerbReciprocal.txt".equals(fileName)) {
            String verb = trimmedOrFallback(extracted.get("verb"), sourceValue);
            String reciprocity = normalizeReciprocityCategory(extracted.get("reciprocity"));
            if (verb.isEmpty() || reciprocity.isEmpty()) {
                return null;
            }
            node.put("verb", verb);
            node.put("reciprocity", reciprocity);
            return node;
        }
        if ("AdjectiveSemanticClasses.txt".equals(fileName)) {
            String adjective = trimmedOrFallback(extracted.get("adjective"), sourceValue);
            String category = normalizeAdjectiveCategory(extracted.get("category"));
            if (adjective.isEmpty() || category.isEmpty()) {
                return null;
            }
            node.put("adjective", adjective);
            node.put("category", category);
            return node;
        }
        if ("AdverbSemanticClasses.txt".equals(fileName)) {
            String adverb = trimmedOrFallback(extracted.get("adverb"), sourceValue);
            String category = normalizeAdverbCategory(extracted.get("category"));
            if (adverb.isEmpty() || category.isEmpty()) {
                return null;
            }
            node.put("adverb", adverb);
            node.put("category", category);
            return node;
        }
        return null;
    }

    public static String articlePatternFor(String article, String noun) {

        if ("none".equals(article)) {
            return "NA";
        }
        return isIrregularIndefiniteArticle(article, noun) ? "Irregular" : "Regular";
    }

    public static String pluralPatternFor(String singular, String plural) {

        if (!plural.equals(singular) && isIrregularPlural(singular, plural)) {
            return "Irregular";
        }
        return "Regular";
    }

    public static boolean isIrregularIndefiniteArticle(String article, String noun) {

        String normalizedArticle = (article == null) ? "" : article.trim().toLowerCase(Locale.ROOT);
        String normalizedNoun = (noun == null) ? "" : noun.trim();
        if (normalizedNoun.isEmpty()) {
            return true;
        }
        char firstChar = Character.toLowerCase(normalizedNoun.charAt(0));
        return "aeiou".indexOf(firstChar) >= 0
                ? !"an".equals(normalizedArticle)
                : !"a".equals(normalizedArticle);
    }

    public static boolean isIrregularPlural(String singular, String givenPlural) {

        if (singular == null || singular.isEmpty() || givenPlural == null || givenPlural.isEmpty()) {
            return false;
        }
        String expectedPlural = pluralize(singular);
        return !expectedPlural.equalsIgnoreCase(givenPlural);
    }

    public static String normalizeCountabilityCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        if (normalized.contains("count") && normalized.contains("mass")) {
            return "Count and mass noun";
        }
        if (normalized.contains("count")) {
            return "Count noun";
        }
        if (normalized.contains("uncount") || normalized.contains("mass")
                || normalized.contains("noncount") || normalized.contains("non-count")) {
            return "Mass noun";
        }
        if (normalized.contains("proper")) {
            return "Proper noun";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    public static String normalizeHumannessCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("both")
                || spacesNormalized.contains("human and non")
                || spacesNormalized.contains("human or non")
                || spacesNormalized.contains("human/non")
                || spacesNormalized.contains("either human or non")) {
            return "Human and non-human";
        }
        if (spacesNormalized.contains("non human") || spacesNormalized.contains("nonhuman")
                || spacesNormalized.contains("not human")) {
            return "Non-human";
        }
        if (spacesNormalized.contains("human") || spacesNormalized.contains("person") || spacesNormalized.contains("people")) {
            return "Human";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear")
                || spacesNormalized.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    public static String normalizeCollectiveCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("context") || spacesNormalized.contains("sometimes")
                || spacesNormalized.contains("can be") || spacesNormalized.contains("depends")) {
            return "Context-dependent";
        }
        boolean mentionsCollective = spacesNormalized.contains("collective") || spacesNormalized.contains("group noun");
        boolean negatedCollective = spacesNormalized.contains("not collective")
                || spacesNormalized.contains("non collective")
                || spacesNormalized.contains("no collective")
                || spacesNormalized.contains("not a collective")
                || spacesNormalized.contains("never collective");
        if (negatedCollective || spacesNormalized.contains("ordinary") || spacesNormalized.contains("regular noun")) {
            return "Not collective";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain")
                || spacesNormalized.contains("unclear") || spacesNormalized.contains("undetermined")
                || spacesNormalized.contains("indeterminate")) {
            return "Unknown";
        }
        if (mentionsCollective) {
            return "Collective";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    public static String normalizeAgencyCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("non agent") || spacesNormalized.contains("not agent")
                || spacesNormalized.contains("nonagent") || spacesNormalized.contains("cannot act")
                || spacesNormalized.contains("inert") || spacesNormalized.contains("non acting")
                || spacesNormalized.contains("non-active")) {
            return "Non-agentive";
        }
        if (spacesNormalized.contains("agentive") || spacesNormalized.contains("agent")
                || spacesNormalized.contains("actor") || spacesNormalized.contains("doer")
                || spacesNormalized.contains("can act") || spacesNormalized.contains("acting entity")) {
            return "Agentive";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain")
                || spacesNormalized.contains("unclear") || spacesNormalized.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    public static String normalizeAgentType(String rawType, String agencyCategory) {

        if (agencyCategory == null || !"Agentive".equalsIgnoreCase(agencyCategory)) {
            if (rawType == null || rawType.trim().isEmpty()) {
                return "Not applicable";
            }
            String lower = rawType.trim().toLowerCase(Locale.ROOT);
            if (lower.contains("unknown") || lower.contains("uncertain") || lower.contains("unspecified")) {
                return "Unknown agent type";
            }
            return "Not applicable";
        }
        if (rawType == null) {
            return "Unknown agent type";
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown agent type";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("mixed") || spacesNormalized.contains("both")
                || (spacesNormalized.contains("animate") && spacesNormalized.contains("inanimate"))) {
            return "Mixed agent type";
        }
        if (spacesNormalized.contains("animate") || spacesNormalized.contains("living")
                || spacesNormalized.contains("life form") || spacesNormalized.contains("human")
                || spacesNormalized.contains("animal") || spacesNormalized.contains("person")
                || spacesNormalized.contains("creature") || spacesNormalized.contains("organism")) {
            return "Animate agent";
        }
        if (spacesNormalized.contains("inanimate") || spacesNormalized.contains("institution")
                || spacesNormalized.contains("organization") || spacesNormalized.contains("collective")
                || spacesNormalized.contains("corporate") || spacesNormalized.contains("company")
                || spacesNormalized.contains("government") || spacesNormalized.contains("software")
                || spacesNormalized.contains("machine") || spacesNormalized.contains("device")
                || spacesNormalized.contains("non living") || spacesNormalized.contains("nonliving")
                || spacesNormalized.contains("artificial")) {
            return "Inanimate agent";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("uncertain")
                || spacesNormalized.contains("unspecified")) {
            return "Unknown agent type";
        }
        return GenUtils.capitalizeFirstLetter(normalized);
    }

    public static String normalizeValenceCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("0") || lower.contains("0-valent") || lower.contains("impersonal")) {
            return "0-valent";
        }
        if (lower.startsWith("1") || lower.contains("1-valent") || (lower.contains("intrans") && !lower.contains("ditrans"))) {
            return "1-valent";
        }
        if (lower.startsWith("3") || lower.contains("3-valent") || lower.contains("ditrans")) {
            return "3-valent";
        }
        if (lower.startsWith("2") || lower.contains("2-valent")
                || lower.contains("trans") || lower.contains("copul") || lower.contains("complex")) {
            return "2-valent";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeSubtype(String rawSubtype) {

        if (rawSubtype == null || rawSubtype.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawSubtype.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("pure")) {
            return "Pure impersonal";
        }
        if (lower.contains("quasi")) {
            return "Quasi-impersonal";
        }
        if (lower.contains("prepositional") && lower.contains("intrans")) {
            return "Prepositional intransitive";
        }
        if (lower.contains("simple") && lower.contains("intrans")) {
            return "Simple intransitive";
        }
        if (lower.contains("complex") && lower.contains("trans")) {
            return "Complex transitive";
        }
        if (lower.contains("copul")) {
            return "Copular";
        }
        if (lower.contains("double")) {
            return "Double-object";
        }
        if (lower.contains("prepositional") && lower.contains("di")) {
            return "Prepositional ditransitive";
        }
        if (lower.contains("trans")) {
            return "Transitive";
        }
        if (lower.contains("intrans")) {
            return "Simple intransitive";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeSemanticRoles(String rawRoles) {

        if (rawRoles == null) {
            return "";
        }
        String cleaned = rawRoles.replaceAll("[\\[\\]]", "")
                .replaceAll("\\s*;\\s*", ",")
                .replaceAll("\\s*\\+\\s*", ",")
                .replaceAll("\\s+/\\s*", "/")
                .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String[] parts = cleaned.split("\\s*,\\s*");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            normalized.add(GenUtils.capitalizeFirstLetter(part.trim().replaceAll("\\s+", " ")));
        }
        return String.join(", ", normalized);
    }

    public static String normalizeCausativityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("mixed") || lower.contains("both") || lower.contains("alternat")) {
            return "Mixed";
        }
        if (lower.contains("non") || lower.contains("intrans") || lower.contains("stative")) {
            return "Non-causative";
        }
        if (lower.contains("caus")) {
            return "Causative";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeAktionsartCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("mixed") || lower.contains("both") || lower.contains("depends")
                || lower.contains("context") || lower.contains("either")) {
            return "Mixed";
        }
        if (lower.contains("achiev") || lower.contains("moment") || lower.contains("instant")
                || lower.contains("punctual") || lower.contains("point") || lower.contains("sudden")) {
            return "Achievement";
        }
        if (lower.contains("process") || lower.contains("activity") || lower.contains("ongoing")
                || lower.contains("durative") || lower.contains("continuous") || lower.contains("unfold")) {
            return "Process";
        }
        if (lower.contains("unknown") || lower.contains("uncertain") || lower.contains("unclear")
                || lower.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeReflexivityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("must") || lower.contains("oblig") || lower.contains("always")) {
            return "Must be reflexive";
        }
        if (lower.contains("never") || lower.contains("cannot") || lower.contains("can't")
                || lower.contains("no reflex") || (lower.contains("non") && lower.contains("reflex"))
                || (lower.contains("not") && lower.contains("reflex")) || lower.contains("without reflex")) {
            return "Never reflexive";
        }
        if (lower.contains("can") || lower.contains("optional") || lower.contains("sometimes")
                || lower.contains("may") || lower.contains("either") || lower.contains("alternat")) {
            return "Can be reflexive";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeReciprocityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("must") || lower.contains("oblig") || lower.contains("always") || lower.contains("inherently")) {
            return "Must be reciprocal";
        }
        if (lower.contains("never") || lower.contains("cannot") || lower.contains("can't")
                || lower.contains("no reciprocal") || (lower.contains("non") && lower.contains("recip"))
                || (lower.contains("not") && lower.contains("recip")) || lower.contains("without reciprocal")) {
            return "Never reciprocal";
        }
        if (lower.contains("can") || lower.contains("optional") || lower.contains("sometimes")
                || lower.contains("may") || lower.contains("either") || lower.contains("alternat")
                || lower.contains("often")) {
            return "Can be reciprocal";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    public static String normalizeAdjectiveCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        if (normalized.matches("\\d+")) {
            switch (normalized) {
                case "1":
                    return "Descriptive / Qualitative";
                case "2":
                    return "Evaluative";
                case "3":
                    return "Quantitative / Indefinite";
                case "4":
                    return "Numeral";
                case "5":
                    return "Demonstrative (Deictic)";
                case "6":
                    return "Possessive";
                case "7":
                    return "Interrogative";
                case "8":
                    return "Distributive";
                case "9":
                    return "Proper / Nominal";
                case "10":
                    return "Other";
                default:
                    return rawCategory.trim();
            }
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("descriptive") || spacesNormalized.contains("qualitative")
                || spacesNormalized.contains("quality") || spacesNormalized.contains("stative")) {
            return "Descriptive / Qualitative";
        }
        if (spacesNormalized.contains("evaluative") || spacesNormalized.contains("value")
                || spacesNormalized.contains("judg") || spacesNormalized.contains("affective")) {
            return "Evaluative";
        }
        if (spacesNormalized.contains("quant") || spacesNormalized.contains("indefinite")
                || spacesNormalized.contains("amount") || spacesNormalized.contains("degree")) {
            return "Quantitative / Indefinite";
        }
        if (spacesNormalized.contains("numeral") || spacesNormalized.contains("number")
                || spacesNormalized.contains("ordinal") || spacesNormalized.contains("cardinal")) {
            return "Numeral";
        }
        if (spacesNormalized.contains("demonstrative") || spacesNormalized.contains("deictic")
                || spacesNormalized.contains("pointing") || spacesNormalized.contains("this") || spacesNormalized.contains("that")) {
            return "Demonstrative (Deictic)";
        }
        if (spacesNormalized.contains("possessive") || spacesNormalized.contains("ownership")
                || spacesNormalized.contains("genitive")) {
            return "Possessive";
        }
        if (spacesNormalized.contains("interrogative") || spacesNormalized.contains("question")) {
            return "Interrogative";
        }
        if (spacesNormalized.contains("distributive") || spacesNormalized.contains("each")
                || spacesNormalized.contains("every") || spacesNormalized.contains("either") || spacesNormalized.contains("neither")) {
            return "Distributive";
        }
        if (spacesNormalized.contains("proper") || spacesNormalized.contains("nominal")
                || spacesNormalized.contains("origin") || spacesNormalized.contains("demonym")
                || spacesNormalized.contains("derived from")) {
            return "Proper / Nominal";
        }
        if (spacesNormalized.contains("other") || spacesNormalized.contains("misc")
                || spacesNormalized.contains("catch all") || spacesNormalized.contains("catch-all")) {
            return "Other";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(rawCategory.trim());
    }

    public static String normalizeAdverbCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        if (normalized.matches("\\d+")) {
            switch (normalized) {
                case "1":
                    return "Manner";
                case "2":
                    return "Place / Location";
                case "3":
                    return "Direction / Path";
                case "4":
                    return "Time";
                case "5":
                    return "Duration";
                case "6":
                    return "Frequency";
                case "7":
                    return "Sequence";
                case "8":
                    return "Degree / Intensifier";
                case "9":
                    return "Approximator / Scalar";
                case "10":
                    return "Measure / Multiplier";
                case "11":
                    return "Epistemic";
                case "12":
                    return "Evidential";
                case "13":
                    return "Attitudinal / Evaluative";
                case "14":
                    return "Style / Domain";
                case "15":
                    return "Focus (additive / restrictive / emphatic)";
                case "16":
                    return "Negation / Polarity";
                case "17":
                    return "Affirmative";
                case "18":
                    return "Connective / Linking";
                case "19":
                    return "Topic-management / Discourse";
                case "20":
                    return "Interrogative";
                case "21":
                    return "Relative";
                default:
                    return rawCategory.trim();
            }
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("manner") || spacesNormalized.contains("how")) {
            return "Manner";
        }
        if (spacesNormalized.contains("location") || spacesNormalized.contains("place")
                || spacesNormalized.contains("locative")) {
            return "Place / Location";
        }
        if (spacesNormalized.contains("direction") || spacesNormalized.contains("path")
                || spacesNormalized.contains("trajectory")) {
            return "Direction / Path";
        }
        if (spacesNormalized.contains("time") || spacesNormalized.contains("temporal")) {
            return "Time";
        }
        if (spacesNormalized.contains("duration") || spacesNormalized.contains("length")) {
            return "Duration";
        }
        if (spacesNormalized.contains("frequency") || spacesNormalized.contains("often")
                || spacesNormalized.contains("habitual")) {
            return "Frequency";
        }
        if (spacesNormalized.contains("sequence") || spacesNormalized.contains("order")
                || spacesNormalized.contains("next")) {
            return "Sequence";
        }
        if (spacesNormalized.contains("degree") || spacesNormalized.contains("intensifier")
                || spacesNormalized.contains("intensity")) {
            return "Degree / Intensifier";
        }
        if (spacesNormalized.contains("approx") || spacesNormalized.contains("scalar")
                || spacesNormalized.contains("nearly") || spacesNormalized.contains("almost")) {
            return "Approximator / Scalar";
        }
        if (spacesNormalized.contains("measure") || spacesNormalized.contains("multiplier")
                || spacesNormalized.contains("fold") || spacesNormalized.contains("times")) {
            return "Measure / Multiplier";
        }
        if (spacesNormalized.contains("epistemic") || spacesNormalized.contains("certainty")
                || spacesNormalized.contains("likelihood")) {
            return "Epistemic";
        }
        if (spacesNormalized.contains("evidential") || spacesNormalized.contains("evidence")
                || spacesNormalized.contains("reported") || spacesNormalized.contains("apparently")) {
            return "Evidential";
        }
        if (spacesNormalized.contains("attitudinal") || spacesNormalized.contains("evaluative")
                || spacesNormalized.contains("stance") || spacesNormalized.contains("emotion")) {
            return "Attitudinal / Evaluative";
        }
        if (spacesNormalized.contains("style") || spacesNormalized.contains("domain")
                || spacesNormalized.contains("register") || spacesNormalized.contains("perspective")) {
            return "Style / Domain";
        }
        if (spacesNormalized.contains("focus") || spacesNormalized.contains("additive")
                || spacesNormalized.contains("restrictive") || spacesNormalized.contains("emphatic")) {
            return "Focus (additive / restrictive / emphatic)";
        }
        if (spacesNormalized.contains("negation") || spacesNormalized.contains("negative")
                || spacesNormalized.contains("polarity") || spacesNormalized.contains("not")
                || spacesNormalized.contains("never")) {
            return "Negation / Polarity";
        }
        if (spacesNormalized.contains("affirmative") || spacesNormalized.contains("positive")
                || spacesNormalized.contains("certainly") || spacesNormalized.contains("indeed")) {
            return "Affirmative";
        }
        if (spacesNormalized.contains("connective") || spacesNormalized.contains("linking")
                || spacesNormalized.contains("conjunct") || spacesNormalized.contains("therefore")) {
            return "Connective / Linking";
        }
        if (spacesNormalized.contains("topic") || spacesNormalized.contains("discourse")
                || spacesNormalized.contains("pragmatic") || spacesNormalized.contains("turn taking")) {
            return "Topic-management / Discourse";
        }
        if (spacesNormalized.contains("interrogative") || spacesNormalized.contains("question")) {
            return "Interrogative";
        }
        if (spacesNormalized.contains("relative") || spacesNormalized.contains("relativizer")
                || spacesNormalized.contains("subordinate")) {
            return "Relative";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(rawCategory.trim());
    }

    private static String normalizeArticleValue(String rawArticle) {

        if (rawArticle == null) {
            return "";
        }
        return rawArticle.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimmedOrFallback(String value, String fallback) {

        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String pluralize(String word) {

        String normalized = word == null ? "" : word.toLowerCase(Locale.ROOT);
        int len = normalized.length();
        if (len > 1 && normalized.endsWith("y") && GenMorphoUtils.isConsonant(normalized.charAt(len - 2))) {
            return normalized.substring(0, len - 1) + "ies";
        }
        if (normalized.endsWith("s") || normalized.endsWith("sh") || normalized.endsWith("ch")
                || normalized.endsWith("x") || normalized.endsWith("z")) {
            return normalized + "es";
        }
        if (normalized.endsWith("fe")) {
            return normalized.substring(0, len - 2) + "ves";
        }
        if (normalized.endsWith("f")) {
            return normalized.substring(0, len - 1) + "ves";
        }
        if (normalized.endsWith("o")) {
            return normalized + "es";
        }
        return normalized + "s";
    }
}
