package com.articulate.nlp.morphodb;

import com.articulate.nlp.GenUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/***************************************************************
 * Verb-related morphological generation.
 * 
 * •	Transitivity
 *    o	Transitive/Instransitive/Ditransitive – takes a direct object
 *    o	Impersonal (no subject, like “to rain”)
 *    o	Reflexive (subject and object can be the same, i.e. “He hurt himself”)
 *    o	Reciprocal (Two subjects act on each other i.e. (They hugged each other)
 * •	Lexical aspect
 *    o	Achievement verbs (punctual change) vs Process verbs (unfolding events)
 *  •	Verb conjugations
 *    o	Regular/irregular verbs
 * 
 ***************************************************************/
public class GenVerbMorphoDB {

    private final Map<String, Set<String>> verbSynsetHash;
    private final Map<String, String> verbDocumentationHash;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final List<String> PERSON_PRONOUN_KEYS =
            Arrays.asList("i", "you_singular", "he_she_it", "we", "you_plural", "they");

    public GenVerbMorphoDB(Map<String, Set<String>> verbSynsetHash,
                           Map<String, String> verbDocumentationHash) {
        this.verbSynsetHash = verbSynsetHash;
        this.verbDocumentationHash = verbDocumentationHash;
    }

    /***************************************************************
     * Verb generation entry point.
     ***************************************************************/
    public void generate(String genFunction) {

        switch (genFunction) {
            case "-v":
                genVerbValence();
                break;
            case "-c":
                genVerbCausativity();
                break;
            case "-r":
                genVerbReflexive();
                break;
            case "-p":
                genVerbReciprocal();
                break;
            case "-a":
                genVerbAchievementProcess();
                break;
            case "-t":
                genVerbConjugations();
                break;
            default:
                System.out.println("Unsupported verb generation function: " + genFunction);
                break;
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs across valency categories.
     ***************************************************************/
    private void genVerbValence() {

        String valenceFileName = "VerbValence_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in English verb valency and argument structure. " +
                        "Classify the verb into the most appropriate valency category from the hierarchy below. \n\n" +
                        "VERB\n" +
                        "├── 0-Valent (Impersonal)\n" +
                        "│     ├── Pure Impersonal → \"It rains\", \"It snowed\"\n" +
                        "│     └── Quasi-Impersonal → \"It takes time\", \"It seems that...\"\n" +
                        "│\n" +
                        "├── 1-Valent (Intransitive)\n" +
                        "│     ├── Simple Intransitive → \"He sleeps\", \"The baby cried\"\n" +
                        "│     └── Prepositional Intransitive → \"She talked about politics\"\n" +
                        "│\n" +
                        "├── 2-Valent\n" +
                        "│     ├── Transitive → \"She built a house\", \"He reads books\"\n" +
                        "│     ├── Complex Transitive → \"They elected her president\", \"I found him annoying\"\n" +
                        "│     └── Copular (Linking) → \"She is happy\", \"He became a teacher\"\n" +
                        "│\n" +
                        "└── 3-Valent (Ditransitive)\n" +
                        "      ├── Double-Object → \"She gave him a book\"\n" +
                        "      └── Prepositional Ditransitive → \"She gave a book to him\"\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, valence, subtype, semantic_roles, explanation, usage.\n" +
                        " - valence must be one of: \"0-valent\", \"1-valent\", \"2-valent\", \"3-valent\".\n" +
                        " - subtype must be selected from the hierarchy (pure impersonal, quasi-impersonal, simple intransitive, prepositional intransitive, transitive, complex transitive, copular, double-object, prepositional ditransitive).\n" +
                        " - semantic_roles should summarize the key semantic roles (Agent, Experiencer, Patient/Theme, Recipient/Beneficiary, Instrument, Location, Source/Goal, Cause, Time, Stimulus, etc.). Use comma-separated values.\n" +
                        " - Provide a short explanation referencing typical complements.\n" +
                        " - Give a concise usage example sentence illustrating the classification.\n\n" +
                        "Reference examples:\n" +
                        "\"It rains.\" → 0-valent, pure impersonal, semantic_roles: natural process\n" +
                        "\"Mary opened the door.\" → 2-valent, transitive, semantic_roles: agent, patient\n" +
                        "\"Mary gave John a book.\" → 3-valent, double-object, semantic_roles: agent, recipient, theme\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"valence\": \"<0-valent|1-valent|2-valent|3-valent>\",\n" +
                        "  \"subtype\": \"<specific subtype>\",\n" +
                        "  \"semantic_roles\": \"<comma-separated roles>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbValence() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] valenceArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "valence", "subtype", "semantic_roles", "explanation", "usage"));
                    if (valenceArray != null) {
                        errorInResponse = false;
                        valenceArray[1] = normalizeValenceCategory(valenceArray[1]);
                        valenceArray[2] = normalizeSubtype(valenceArray[2]);
                        valenceArray[3] = "\"" + normalizeSemanticRoles(valenceArray[3]) + "\"";
                        valenceArray[4] = "\"" + valenceArray[4] + "\"";
                        valenceArray[5] = "\"" + valenceArray[5] + "\"";
                        valenceArray = GenUtils.appendToStringArray(valenceArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(valenceFileName, Arrays.toString(valenceArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(valenceFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbValence().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs by reflexive behavior.
     ***************************************************************/
    private void genVerbReflexive() {

        String reflexiveFileName = "VerbReflexive_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in reflexive verb constructions. " +
                        "Determine whether the verb is obligatorily reflexive (must take a reflexive object when its subject acts on itself), " +
                        "optionally reflexive (can appear with or without a reflexive object), or never reflexive " +
                        "(does not permit subject and object to be the same in standard usage).\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, reflexivity, explanation, usage.\n" +
                        " - reflexivity must be one of: \"must be reflexive\", \"can be reflexive\", or \"never reflexive\".\n" +
                        " - Provide a concise explanation citing syntactic or semantic cues.\n" +
                        " - Supply an illustrative usage sentence that matches the classification.\n\n" +
                        "Example classifications:\n" +
                        "\"He prides himself on his work.\" → must be reflexive (obligatory reflexive object).\n" +
                        "\"She dressed (herself).\" → can be reflexive (reflexive optional).\n" +
                        "\"She greeted him.\" → never reflexive (verb does not take reflexive object).\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"reflexivity\": \"<must be reflexive|can be reflexive|never reflexive>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbReflexive() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] reflexiveArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "reflexivity", "explanation", "usage"));
                    if (reflexiveArray != null) {
                        errorInResponse = false;
                        reflexiveArray[1] = normalizeReflexivityCategory(reflexiveArray[1]);
                        reflexiveArray[2] = "\"" + reflexiveArray[2] + "\"";
                        reflexiveArray[3] = "\"" + reflexiveArray[3] + "\"";
                        reflexiveArray = GenUtils.appendToStringArray(reflexiveArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(reflexiveFileName, Arrays.toString(reflexiveArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(reflexiveFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbReflexive().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs as causative or non-causative.
     ***************************************************************/
    private void genVerbCausativity() {

        String causativityFileName = "VerbCausativity_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert in lexical semantics focusing on causativity. " +
                        "Determine whether the verb denotes a causative action (the subject causes a change in a patient), " +
                        "is typically non-causative, or has both causative and non-causative senses. \n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, causativity, explanation, usage.\n" +
                        " - causativity must be one of: \"causative\", \"non-causative\", or \"mixed\" (for verbs with both readings).\n" +
                        " - Provide a concise explanation citing typical syntactic or semantic patterns.\n" +
                        " - Supply an illustrative usage sentence that matches the classification.\n\n" +
                        "Example classifications:\n" +
                        "\"She broke the vase.\" → causative (agent causes change to patient)\n" +
                        "\"The baby slept.\" → non-causative (intransitive change of state)\n" +
                        "\"The door opened.\" / \"She opened the door.\" → mixed (verb alternates between causative and non-causative)\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"causativity\": \"<causative|non-causative|mixed>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbCausativity() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] causativityArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "causativity", "explanation", "usage"));
                    if (causativityArray != null) {
                        errorInResponse = false;
                        causativityArray[1] = normalizeCausativityCategory(causativityArray[1]);
                        causativityArray[2] = "\"" + causativityArray[2] + "\"";
                        causativityArray[3] = "\"" + causativityArray[3] + "\"";
                        causativityArray = GenUtils.appendToStringArray(causativityArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(causativityFileName, Arrays.toString(causativityArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(causativityFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbCausativity().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs as achievements or processes.
     ***************************************************************/
    private void genVerbAchievementProcess() {

        String aspectFileName = "VerbAchievementProcess_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert linguist specializing in lexical aspect (Aktionsart). " +
                        "Classify the verb as either an achievement verb (a single, punctual change of state) or a process verb (an unfolding action with duration). " +
                        "If the verb genuinely alternates between both readings, mark it as mixed. " +
                        "If the classification cannot be determined, label it unknown.\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Consider the most common contemporary English usage.\n" +
                        " - Interpret process verbs as activities or events that extend over time (e.g., \"run\", \"negotiate\").\n" +
                        " - Interpret achievement verbs as punctual changes or instants (e.g., \"recognize\", \"reach\").\n" +
                        " - Return a JSON object with fields: verb, aktionsart, explanation, usage.\n" +
                        " - aktionsart must be one of: \"achievement\", \"process\", \"mixed\", \"unknown\".\n" +
                        " - Provide a concise explanation referencing the temporal profile.\n" +
                        " - Supply one illustrative usage sentence that matches the classification.\n\n" +
                        "Output only valid JSON with this schema:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"aktionsart\": \"<achievement|process|mixed|unknown>\",\n" +
                        "  \"explanation\": \"<short rationale>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbAchievementProcess() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] aspectArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "aktionsart", "explanation", "usage"));
                    if (aspectArray != null) {
                        errorInResponse = false;
                        aspectArray[1] = normalizeAktionsartCategory(aspectArray[1]);
                        aspectArray[2] = "\"" + aspectArray[2] + "\"";
                        aspectArray[3] = "\"" + aspectArray[3] + "\"";
                        aspectArray = GenUtils.appendToStringArray(aspectArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(aspectFileName, Arrays.toString(aspectArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(aspectFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbAchievementProcess().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to classify verbs by reciprocal behavior.
     ***************************************************************/
    private void genVerbReciprocal() {

        String reciprocalFileName = "VerbReciprocal_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert lexicographer specializing in reciprocal verb constructions. " +
                        "Determine whether the verb is obligatorily reciprocal (typically requires two agents acting on each other), " +
                        "optionally reciprocal (can describe mutual action but also allows non-reciprocal use), or never reciprocal " +
                        "(does not naturally describe participants acting on each other).\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Instructions:\n" +
                        " - Return a JSON object with fields: verb, reciprocity, explanation, usage.\n" +
                        " - reciprocity must be one of: \"must be reciprocal\", \"can be reciprocal\", or \"never reciprocal\".\n" +
                        " - Provide a concise explanation referencing argument structure or semantic cues.\n" +
                        " - Supply an illustrative usage sentence that matches the classification.\n\n" +
                        "Example classifications:\n" +
                        "\"They embraced each other tightly.\" → must be reciprocal (verb describes mutual action by default).\n" +
                        "\"They met (each other) at noon.\" → can be reciprocal (reciprocal available but not obligatory).\n" +
                        "\"She tutored him in chemistry.\" → never reciprocal (verb encodes asymmetric instruction).\n\n" +
                        "Output only valid JSON with this shape:\n" +
                        "{\n" +
                        "  \"verb\": \"<verb>\",\n" +
                        "  \"reciprocity\": \"<must be reciprocal|can be reciprocal|never reciprocal>\",\n" +
                        "  \"explanation\": \"<reasoning>\",\n" +
                        "  \"usage\": \"<example sentence>\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbReciprocal() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    String[] reciprocalArray = GenUtils.extractJsonFields(jsonResponse,
                            Arrays.asList("verb", "reciprocity", "explanation", "usage"));
                    if (reciprocalArray != null) {
                        errorInResponse = false;
                        reciprocalArray[1] = normalizeReciprocityCategory(reciprocalArray[1]);
                        reciprocalArray[2] = "\"" + reciprocalArray[2] + "\"";
                        reciprocalArray[3] = "\"" + reciprocalArray[3] + "\"";
                        reciprocalArray = GenUtils.appendToStringArray(reciprocalArray, "\"" + definition + "\"");
                        GenUtils.writeToFile(reciprocalFileName, Arrays.toString(reciprocalArray) + "\n");
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(reciprocalFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbReciprocal().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    /***************************************************************
     * Uses OLLAMA to generate full tense conjugations for verbs.
     ***************************************************************/
    private void genVerbConjugations() {

        String conjugationFileName = "VerbConjugations_" + GenUtils.getOllamaModel() + ".txt";
        for (Map.Entry<String, Set<String>> entry : verbSynsetHash.entrySet()) {
            String term = entry.getKey().replace('_', ' ');
            if (term.length() < 2) {
                continue;
            }
            for (String synsetId : entry.getValue()) {
                String definition = verbDocumentationHash.get(synsetId);
                definition = (definition != null) ? definition.replaceAll("^\"|\"$", "") : null;
                String definitionStatement = (definition == null) ? "" : "Definition: \"" + verbDocumentationHash.get(synsetId) + "\". ";
                String prompt = "You are an expert English grammarian generating complete verb conjugation tables. " +
                        "List the conjugated forms of the verb for the subjects I, you (singular), he/she/it, we, you (plural), and they " +
                        "across the tense/aspect categories below.\n\n" +
                        "Verb: \"" + term + "\".\n" +
                        definitionStatement + "\n\n" +
                        "Tenses to cover in this order:\n" +
                        "  1. Infinitive\n" +
                        "  2. Simple present\n" +
                        "  3. Simple past\n" +
                        "  4. Simple future\n" +
                        "  5. Present progressive (present continuous)\n" +
                        "  6. Past progressive (past continuous)\n" +
                        "  7. Future progressive (future continuous)\n" +
                        "  8. Present perfect\n" +
                        "  9. Past perfect\n" +
                        " 10. Future perfect\n" +
                        " 11. Present perfect progressive\n" +
                        " 12. Past perfect progressive\n" +
                        " 13. Future perfect progressive\n" +
                        " 14. Imperative\n" +
                        " 15. Gerund / present participle\n" +
                        " 16. Past participle\n\n" +
                        "Instructions:\n" +
                        " - Return valid JSON with fields: verb, tenses, notes.\n" +
                        " - verb must match the infinitive/base form provided.\n" +
                        " - tenses must be an array of 16 objects, each containing:\n" +
                        "     • tense: the tense/aspect name\n" +
                        "     • forms: an object with keys i, you_singular, he_she_it, we, you_plural, they\n" +
                        "       (use the same form for all pronouns if a tense does not vary by subject)\n" +
                        "     • Optional fields example and notes are allowed.\n" +
                        " - Use complete example clauses (e.g., \"I am running\") rather than bare verb forms.\n" +
                        " - Provide a brief notes string highlighting any irregularities or alternations.\n" +
                        " - Do not include commentary outside the JSON object.\n\n" +
                        "Example output schema:\n" +
                        "{\n" +
                        "  \"verb\": \"to sample\",\n" +
                        "  \"tenses\": [\n" +
                        "    {\n" +
                        "      \"tense\": \"Simple present\",\n" +
                        "      \"forms\": {\n" +
                        "        \"i\": \"I sample\",\n" +
                        "        \"you_singular\": \"You sample\",\n" +
                        "        \"he_she_it\": \"He samples\",\n" +
                        "        \"we\": \"We sample\",\n" +
                        "        \"you_plural\": \"You sample\",\n" +
                        "        \"they\": \"They sample\"\n" +
                        "      },\n" +
                        "      \"example\": \"I sample the sauce before serving.\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"notes\": \"Third person singular present adds -s.\"\n" +
                        "}";
                if (GenMorphoUtils.debug) {
                    System.out.println("GenVerbMorphoDB.genVerbConjugations() Prompt: " + prompt);
                }
                String llmResponse = GenUtils.askOllama(prompt);
                String jsonResponse = GenUtils.extractFirstJsonObject(llmResponse);
                boolean errorInResponse = true;
                if (jsonResponse != null) {
                    try {
                        JsonNode root = JSON_MAPPER.readTree(jsonResponse);
                        String verbValue = root.path("verb").asText("").trim();
                        ArrayNode tensesNode = normalizeConjugationTenses(root.get("tenses"));
                        if (!verbValue.isEmpty() && tensesNode != null && tensesNode.size() > 0) {
                            errorInResponse = false;
                            ObjectNode record = JSON_MAPPER.createObjectNode();
                            record.put("verb", verbValue);
                            record.put("definition", definition == null ? "" : definition);
                            record.set("tenses", tensesNode);
                            if (root.hasNonNull("notes")) {
                                record.put("notes", root.get("notes").asText(""));
                            }
                            GenUtils.writeToFile(conjugationFileName, JSON_MAPPER.writeValueAsString(record) + "\n");
                        }
                    } catch (Exception ignored) {
                        // fall through to error handling
                    }
                }
                if (errorInResponse) {
                    GenUtils.writeToFile(conjugationFileName,
                            "ERROR! verb: " + term + ". " + definitionStatement +
                                    " - LLM response: " + (llmResponse == null ? "null" : llmResponse.replace("\n", "")) + "\n");
                }

                if (GenMorphoUtils.debug) {
                    System.out.println("\n\nGenVerbMorphoDB.genVerbConjugations().LLMResponse: " + llmResponse + "\n\n**************\n");
                }
            }
        }
    }

    private static ArrayNode normalizeConjugationTenses(JsonNode rawTenses) {

        ArrayNode normalized = JSON_MAPPER.createArrayNode();
        if (rawTenses == null || rawTenses.isNull()) {
            return normalized;
        }
        if (rawTenses.isArray()) {
            for (JsonNode entry : rawTenses) {
                appendNormalizedTense(normalized, entry);
            }
            return normalized;
        }
        if (rawTenses.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = rawTenses.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                ObjectNode entryNode = JSON_MAPPER.createObjectNode();
                entryNode.put("tense", field.getKey());
                entryNode.set("forms", field.getValue());
                appendNormalizedTense(normalized, entryNode);
            }
            return normalized;
        }
        appendNormalizedTense(normalized, rawTenses);
        return normalized;
    }

    private static void appendNormalizedTense(ArrayNode target, JsonNode rawEntry) {

        if (rawEntry == null || rawEntry.isNull()) {
            return;
        }
        String rawTenseName = rawEntry.path("tense").asText(rawEntry.isTextual() ? rawEntry.asText("") : "");
        String tenseName = normalizeTenseName(rawTenseName);
        if (tenseName.isEmpty()) {
            return;
        }
        JsonNode formsNode = rawEntry.get("forms");
        if ((formsNode == null || formsNode.isNull()) && rawEntry.isObject()) {
            ObjectNode derivedForms = JSON_MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = rawEntry.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                if ("tense".equalsIgnoreCase(fieldName) ||
                        "forms".equalsIgnoreCase(fieldName) ||
                        "example".equalsIgnoreCase(fieldName) ||
                        "notes".equalsIgnoreCase(fieldName)) {
                    continue;
                }
                String canonicalPronoun = normalizePronounKey(fieldName);
                if (canonicalPronoun != null) {
                    derivedForms.set(canonicalPronoun, field.getValue());
                }
            }
            if (derivedForms.size() > 0) {
                formsNode = derivedForms;
            }
        }

        ObjectNode normalizedEntry = JSON_MAPPER.createObjectNode();
        normalizedEntry.put("tense", tenseName);
        normalizedEntry.set("forms", normalizeConjugationForms(formsNode));

        JsonNode exampleNode = rawEntry.get("example");
        if (exampleNode != null && !exampleNode.isNull()) {
            normalizedEntry.put("example", exampleNode.asText(""));
        }
        JsonNode notesNode = rawEntry.get("notes");
        if (notesNode != null && !notesNode.isNull()) {
            normalizedEntry.put("notes", notesNode.asText(""));
        }
        target.add(normalizedEntry);
    }

    private static ObjectNode normalizeConjugationForms(JsonNode rawForms) {

        ObjectNode forms = JSON_MAPPER.createObjectNode();
        for (String pronoun : PERSON_PRONOUN_KEYS) {
            forms.put(pronoun, "");
        }
        forms.put("summary", "");

        if (rawForms == null || rawForms.isNull()) {
            return forms;
        }
        if (rawForms.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = rawForms.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String canonicalPronoun = normalizePronounKey(field.getKey());
                if (canonicalPronoun != null) {
                    forms.put(canonicalPronoun, field.getValue().asText(""));
                    continue;
                }
                String lowerFieldName = field.getKey().trim().toLowerCase();
                if ("summary".equals(lowerFieldName) || "note".equals(lowerFieldName) || "notes".equals(lowerFieldName)) {
                    forms.put("summary", field.getValue().asText(""));
                }
            }
            return forms;
        }
        if (rawForms.isArray()) {
            List<String> collected = new ArrayList<>();
            for (JsonNode node : rawForms) {
                collected.add(node.asText(""));
            }
            forms.put("summary", String.join("; ", collected).trim());
            return forms;
        }
        forms.put("summary", rawForms.asText(""));
        return forms;
    }

    private static String normalizeTenseName(String rawName) {

        if (rawName == null) {
            return "";
        }
        String lower = rawName.trim().toLowerCase();
        if (lower.isEmpty()) {
            return "";
        }
        if (lower.contains("infinitive") || lower.contains("base form")) {
            return "Infinitive";
        }
        if (lower.contains("simple present") || lower.contains("present simple")) {
            return "Simple present";
        }
        if (lower.contains("simple past") || lower.contains("past simple")) {
            return "Simple past";
        }
        if (lower.contains("simple future") || lower.contains("future simple")) {
            return "Simple future";
        }
        if ((lower.contains("present progressive") || lower.contains("present continuous"))
                && !lower.contains("perfect")) {
            return "Present progressive";
        }
        if ((lower.contains("past progressive") || lower.contains("past continuous"))
                && !lower.contains("perfect")) {
            return "Past progressive";
        }
        if ((lower.contains("future progressive") || lower.contains("future continuous"))
                && !lower.contains("perfect")) {
            return "Future progressive";
        }
        if (lower.contains("present perfect progressive") || lower.contains("present perfect continuous")) {
            return "Present perfect progressive";
        }
        if (lower.contains("past perfect progressive") || lower.contains("past perfect continuous")) {
            return "Past perfect progressive";
        }
        if (lower.contains("future perfect progressive") || lower.contains("future perfect continuous")) {
            return "Future perfect progressive";
        }
        if (lower.contains("present perfect")) {
            return "Present perfect";
        }
        if (lower.contains("past perfect")) {
            return "Past perfect";
        }
        if (lower.contains("future perfect")) {
            return "Future perfect";
        }
        if (lower.contains("imperative")) {
            return "Imperative";
        }
        if (lower.contains("gerund") || lower.contains("present participle")) {
            return "Gerund / present participle";
        }
        if (lower.contains("past participle")) {
            return "Past participle";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizePronounKey(String rawKey) {

        if (rawKey == null) {
            return null;
        }
        String normalized = rawKey.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        switch (normalized) {
            case "i":
            case "first_person":
            case "first_person_singular":
            case "1st_person_singular":
            case "first_singular":
            case "1sg":
                return "i";
            case "you":
            case "you_singular":
            case "second_person":
            case "second_person_singular":
            case "2nd_person_singular":
            case "singular_you":
            case "2sg":
                return "you_singular";
            case "he":
            case "she":
            case "it":
            case "he_she":
            case "she_he":
            case "he_she_it":
            case "third_person_singular":
            case "3rd_person_singular":
            case "third_singular":
            case "3sg":
                return "he_she_it";
            case "we":
            case "first_person_plural":
            case "1st_person_plural":
            case "first_plural":
            case "1pl":
                return "we";
            case "you_plural":
            case "youplural":
            case "second_person_plural":
            case "2nd_person_plural":
            case "plural_you":
            case "2pl":
            case "yall":
            case "ya_ll":
            case "ye":
                return "you_plural";
            case "they":
            case "third_person_plural":
            case "3rd_person_plural":
            case "third_plural":
            case "3pl":
                return "they";
            default:
                return null;
        }
    }

    private static String normalizeValenceCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.startsWith("0") || lower.contains("0-valent") || lower.contains("impersonal")) {
            return "0-valent";
        }
        if (lower.startsWith("1") || lower.contains("1-valent") || (lower.contains("intrans") && !lower.contains("ditrans"))) {
            return "1-valent";
        }
        if (lower.startsWith("3") || lower.contains("3-valent") || lower.contains("ditrans")) {
            return "3-valent";
        }
        if (lower.startsWith("2") || lower.contains("2-valent") ||
                lower.contains("trans") || lower.contains("copul") || lower.contains("complex")) {
            return "2-valent";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizeSubtype(String rawSubtype) {

        if (rawSubtype == null || rawSubtype.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawSubtype.trim().toLowerCase();
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

    private static String normalizeSemanticRoles(String rawRoles) {

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
            if (part.isEmpty()) {
                continue;
            }
            String role = part.trim();
            if (role.isEmpty()) {
                continue;
            }
            role = role.replaceAll("\\s+", " ");
            String formatted = GenUtils.capitalizeFirstLetter(role);
            normalized.add(formatted);
        }
        return String.join(", ", normalized);
    }

    private static String normalizeCausativityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
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

    private static String normalizeAktionsartCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.contains("mixed") || lower.contains("both") || lower.contains("depends") ||
                lower.contains("context") || lower.contains("either")) {
            return "Mixed";
        }
        if (lower.contains("achiev") || lower.contains("moment") || lower.contains("instant") ||
                lower.contains("punctual") || lower.contains("point") || lower.contains("sudden")) {
            return "Achievement";
        }
        if (lower.contains("process") || lower.contains("activity") || lower.contains("ongoing") ||
                lower.contains("durative") || lower.contains("continuous") || lower.contains("unfold")) {
            return "Process";
        }
        if (lower.contains("unknown") || lower.contains("uncertain") || lower.contains("unclear") ||
                lower.contains("undetermined")) {
            return "Unknown";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizeReflexivityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.contains("must") || lower.contains("oblig") || lower.contains("always")) {
            return "Must be reflexive";
        }
        if (lower.contains("never") || lower.contains("cannot") || lower.contains("can't") ||
                lower.contains("no reflex") || (lower.contains("non") && lower.contains("reflex")) ||
                (lower.contains("not") && lower.contains("reflex")) || lower.contains("without reflex")) {
            return "Never reflexive";
        }
        if (lower.contains("can") || lower.contains("optional") || lower.contains("sometimes") ||
                lower.contains("may") || lower.contains("either") || lower.contains("alternat")) {
            return "Can be reflexive";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }

    private static String normalizeReciprocityCategory(String rawCategory) {

        if (rawCategory == null || rawCategory.trim().isEmpty()) {
            return "Unknown";
        }
        String lower = rawCategory.trim().toLowerCase();
        if (lower.contains("must") || lower.contains("oblig") || lower.contains("always") || lower.contains("inherently")) {
            return "Must be reciprocal";
        }
        if (lower.contains("never") || lower.contains("cannot") || lower.contains("can't") ||
                lower.contains("no reciprocal") || (lower.contains("non") && lower.contains("recip")) ||
                (lower.contains("not") && lower.contains("recip")) || lower.contains("without reciprocal")) {
            return "Never reciprocal";
        }
        if (lower.contains("can") || lower.contains("optional") || lower.contains("sometimes") ||
                lower.contains("may") || lower.contains("either") || lower.contains("alternat") ||
                lower.contains("often")) {
            return "Can be reciprocal";
        }
        return GenUtils.capitalizeFirstLetter(lower);
    }
}
