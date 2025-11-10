package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;

import java.util.Arrays;
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

        switch (genFunction) {
            case "-c":
                genAdverbSemanticClasses();
                break;
            default:
                System.out.println("Adverb morphological generation not yet implemented for function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify adverbs by discourse and semantic role.
     ***************************************************************/
    private void genAdverbSemanticClasses() {

        String adverbFileName = GenMorphoUtils.resolveOutputFile("adverb", "AdverbSemanticClasses.txt");
        for (Map.Entry<String, Set<String>> entry : adverbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = adverbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" :
                        "Definition: \"" + adverbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English adverb classes. " +
                        "Assign the adverb to exactly one category from the list below.\n\n" +
                        "Categories:\n" +
                        " 1. Manner - describes how an action is performed (quickly, carefully)\n" +
                        " 2. Place / Location - indicates where an action occurs (here, abroad)\n" +
                        " 3. Direction / Path - expresses movement or trajectory (away, homeward)\n" +
                        " 4. Time - situates an event in time (now, tomorrow)\n" +
                        " 5. Duration - shows how long something lasts (briefly, forever)\n" +
                        " 6. Frequency - shows how often something occurs (always, rarely)\n" +
                        " 7. Sequence - orders events (first, then, finally)\n" +
                        " 8. Degree / Intensifier - scales intensity (very, extremely)\n" +
                        " 9. Approximator / Scalar - conveys near limits (almost, roughly)\n" +
                        "10. Measure / Multiplier - indicates proportional extent (twice, threefold)\n" +
                        "11. Epistemic - conveys certainty or likelihood (probably, surely)\n" +
                        "12. Evidential - signals source of information (apparently, reportedly)\n" +
                        "13. Attitudinal / Evaluative - expresses speaker stance (fortunately, sadly)\n" +
                        "14. Style / Domain - limits the statement to a perspective (technically, legally)\n" +
                        "15. Focus (additive / restrictive / emphatic) - highlights or limits focus (only, even)\n" +
                        "16. Negation / Polarity - expresses denial (not, never)\n" +
                        "17. Affirmative - reinforces truth (yes, indeed)\n" +
                        "18. Connective / Linking - joins clauses (however, therefore)\n" +
                        "19. Topic-management / Discourse - manages discourse flow (well, anyway)\n" +
                        "20. Interrogative - introduces a question (how, when)\n" +
                        "21. Relative - introduces a subordinate clause (when, where, wherever)\n\n" +
                        "Adverb: \"" + term + "\".\n" +
                        definitionStatement + "\n" +
                        "Instructions:\n" +
                        " - Consider the adverb's default dictionary sense.\n" +
                        " - If it has multiple functions, choose the primary or most canonical role.\n" +
                        " - Output valid JSON only, with fields adverb, category, explanation, usage.\n" +
                        " - \"category\" must match one of the twenty-one category names above exactly.\n" +
                        " - Provide a short explanation mentioning the key semantic cue.\n" +
                        " - Provide a single usage sentence illustrating the classification.\n\n" +
                        "JSON schema:\n" +
                        "{\n" +
                        "  \"adverb\": \"<adverb>\",\n" +
                        "  \"category\": \"<one of the twenty-one categories>\",\n" +
                        "  \"explanation\": \"<short rationale>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenAdverbMorphoDB.genAdverbSemanticClasses() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] adverbArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("adverb", "category", "explanation", "usage"));
                    if (adverbArray != null) {
                        errorInResponse = false;
                        adverbArray[1] = normalizeAdverbCategory(adverbArray[1]);
                        adverbArray[2] = "\"" + adverbArray[2] + "\"";
                        adverbArray[3] = "\"" + adverbArray[3] + "\"";
                        adverbArray = GenUtils.appendToStringArray(adverbArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(adverbFileName, Arrays.toString(adverbArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(adverbFileName,
                            "ERROR! adverb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenAdverbMorphoDB.genAdverbSemanticClasses().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static String normalizeAdverbCategory(String rawCategory) {

        if (rawCategory == null) {
            return "Unknown";
        }
        String normalized = rawCategory.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return "Unknown";
        }
        String spacesNormalized = normalized.replace('-', ' ');
        if (spacesNormalized.contains("manner") || spacesNormalized.contains("how")) {
            return "Manner";
        }
        if (spacesNormalized.contains("location") || spacesNormalized.contains("place") ||
                spacesNormalized.contains("locative")) {
            return "Place / Location";
        }
        if (spacesNormalized.contains("direction") || spacesNormalized.contains("path") ||
                spacesNormalized.contains("trajectory")) {
            return "Direction / Path";
        }
        if (spacesNormalized.contains("time") || spacesNormalized.contains("temporal")) {
            return "Time";
        }
        if (spacesNormalized.contains("duration") || spacesNormalized.contains("length")) {
            return "Duration";
        }
        if (spacesNormalized.contains("frequency") || spacesNormalized.contains("often") ||
                spacesNormalized.contains("habitual")) {
            return "Frequency";
        }
        if (spacesNormalized.contains("sequence") || spacesNormalized.contains("order") ||
                spacesNormalized.contains("next")) {
            return "Sequence";
        }
        if (spacesNormalized.contains("degree") || spacesNormalized.contains("intensifier") ||
                spacesNormalized.contains("intensity")) {
            return "Degree / Intensifier";
        }
        if (spacesNormalized.contains("approx") || spacesNormalized.contains("scalar") ||
                spacesNormalized.contains("nearly") || spacesNormalized.contains("almost")) {
            return "Approximator / Scalar";
        }
        if (spacesNormalized.contains("measure") || spacesNormalized.contains("multiplier") ||
                spacesNormalized.contains("fold") || spacesNormalized.contains("times")) {
            return "Measure / Multiplier";
        }
        if (spacesNormalized.contains("epistemic") || spacesNormalized.contains("certainty") ||
                spacesNormalized.contains("likelihood")) {
            return "Epistemic";
        }
        if (spacesNormalized.contains("evidential") || spacesNormalized.contains("evidence") ||
                spacesNormalized.contains("reported") || spacesNormalized.contains("apparently")) {
            return "Evidential";
        }
        if (spacesNormalized.contains("attitudinal") || spacesNormalized.contains("evaluative") ||
                spacesNormalized.contains("stance") || spacesNormalized.contains("emotion")) {
            return "Attitudinal / Evaluative";
        }
        if (spacesNormalized.contains("style") || spacesNormalized.contains("domain") ||
                spacesNormalized.contains("register") || spacesNormalized.contains("perspective")) {
            return "Style / Domain";
        }
        if (spacesNormalized.contains("focus") || spacesNormalized.contains("additive") ||
                spacesNormalized.contains("restrictive") || spacesNormalized.contains("emphatic")) {
            return "Focus (additive / restrictive / emphatic)";
        }
        if (spacesNormalized.contains("negation") || spacesNormalized.contains("negative") ||
                spacesNormalized.contains("polarity") || spacesNormalized.contains("not") ||
                spacesNormalized.contains("never")) {
            return "Negation / Polarity";
        }
        if (spacesNormalized.contains("affirmative") || spacesNormalized.contains("positive") ||
                spacesNormalized.contains("certainly") || spacesNormalized.contains("indeed")) {
            return "Affirmative";
        }
        if (spacesNormalized.contains("connective") || spacesNormalized.contains("linking") ||
                spacesNormalized.contains("conjunct") || spacesNormalized.contains("therefore")) {
            return "Connective / Linking";
        }
        if (spacesNormalized.contains("topic") || spacesNormalized.contains("discourse") ||
                spacesNormalized.contains("pragmatic") || spacesNormalized.contains("turn taking")) {
            return "Topic-management / Discourse";
        }
        if (spacesNormalized.contains("interrogative") || spacesNormalized.contains("question")) {
            return "Interrogative";
        }
        if (spacesNormalized.contains("relative") || spacesNormalized.contains("relativizer") ||
                spacesNormalized.contains("subordinate")) {
            return "Relative";
        }
        if (spacesNormalized.contains("unknown") || spacesNormalized.contains("unclear")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(rawCategory.trim());
    }
}
