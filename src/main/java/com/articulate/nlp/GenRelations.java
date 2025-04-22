package com.articulate.nlp;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;

import java.io.FileWriter;
import java.util.Collection;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.util.Random;

import com.articulate.sigma.*;
import java.util.*;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WSD;

/** Generate synthetic test data, by taking all the relations in SUMO,
 *  and filling in the format statements with relevant data,
 *  with the help of LLM.
 *  The output is two text files with english sentences, and their
 *  logical equivalent.
 */
public class GenRelations {

    public static boolean debug = false;
    public static KB kb;
    public static String outputFileEnglish = "relations-eng.txt";
    public static String outputFileLogic = "relations-log.txt";
    public static boolean EQUIVALENCE_MAPPINGS = false;
    public static boolean RAW_PROMPT = false;
    public static Options options;
    public static String englishSentence;
    public static String englishSentenceQuestion;
    public static String englishSentenceWithArticles;
    public static String logicPhrase;
    public static String logicPhraseWithArticles;
    public static OllamaAPI ollamaAPI;
    public static RandSet allSUMORelationsRandSet;
    public static Random random;
    public static int numToGenerate;
    public static int sentenceGeneratedCounter;
    public static boolean articlesAreValid;
    public static Map<String, List<String>> allTermFormats;
    public static Map<String, List<String>> allFormats;
    public static List<Formula> domainsAll;
    public static boolean isQuestion;
    public static boolean isNegated;
    public static String randRelation;
    public static List<String> randRelationFormats;
    public static GenUtils genUtils;

    public static final int DOMAIN_TYPE = 0;
    public static final int TERM = 1;
    public static final int ARG_NUM = 2;
    public static final int ARG_CLASS = 3;

    /** ***************************************************************
     *   Initiates important variables and objects needed
     *   for relation generation.
     */
    public static void init(String[] args) {

        // parse input variables
        if (args == null || args.length < 3 || args.length > 4 || args[0].equals("-h")) {
            System.out.println("Usage: GenRelations <file prefix> <num to generate> <ollama port number> <optional: -e (for equivalence mappings only)");
            System.exit(0);
        }
        numToGenerate = Integer.parseInt(args[1]);
        if (args.length == 4 && args[3].equals("-e")) {
            EQUIVALENCE_MAPPINGS = true;
            System.out.println("Using ONLY equivalence mappings");
        }
        else {
            System.out.println("Drawing from equivalence and subsuming mappings.");
        }

        // connect to Ollama
        String host = "http://localhost:" + args[2] + "/";
        System.out.println("Connecting to " + host);
        ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setVerbose(false);
        options = new OptionsBuilder().setTemperature(1.0f).build();

        // load the knowledge base
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        System.out.println("Finished loading KBs");
        Set<String> allSUMOFunctionsSet = kb.kbCache.getChildInstances("Function");
        Set<String> allSUMORelationsSet = kb.kbCache.getChildInstances("Relation");
        allSUMORelationsSet.removeAll(allSUMOFunctionsSet);
        allSUMORelationsSet.remove("Function");
        allSUMORelationsRandSet = RandSet.listToEqualPairs(allSUMORelationsSet);
        allTermFormats = kb.getTermFormatMapAll("EnglishLanguage");
        allFormats = kb.getFormatMapAll("EnglishLanguage");
        random = new Random();
        genUtils = new GenUtils();

        // create output files
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        genUtils.createFileIfDoesNotExists(outputFileEnglish);
        genUtils.createFileIfDoesNotExists(outputFileLogic);
    }



    public static void resetGenParams() {

        isQuestion = GenSimpTestData.biasedBoolean(1, 2);
        isNegated = GenSimpTestData.biasedBoolean(1, 2);
        genUtils.resetVariables();
        logicPhrase = "";
        englishSentence = "";
    }

    public static String[] parseDomainFormula(String formula) {

        String trimmed = formula.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        } else {
            System.out.println("Formula must start with '(' and end with ')'. Formula: " + formula);
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 4) {
            System.out.println("domain formula must contain exactly four elements inside parentheses. Formula: " + formula);
            return null;
        }
        return parts;
    }

    public static String[] getDomainArgNum(String argNum) {

        for (Formula d : domainsAll) {
            if (debug) System.out.println("    domain :" + d.getFormula());
            String[] d_array = parseDomainFormula(d.getFormula());
            if (d_array == null) {
                System.out.println("ERROR in GenRelations. Could not parse domain " + d.getFormula());
                return null;
            }
            if (d_array[ARG_NUM].equals(argNum)) {
                return d_array;
            }
        }
        return null;
    }


    public static boolean handleLinks() {

        englishSentence = englishSentence.replace("&%", "");
        return true;
    }

    public static boolean handleFormatSymbols() {

        if (isQuestion && !isNegated) {
            if (!(englishSentence.contains("%qp{"))) {
                isQuestion = false;
            }
        } else if (isQuestion && isNegated) {
             if (!englishSentence.contains("%qn{")) {
                 isQuestion = false;
             }
        }
        if (!isQuestion && isNegated) {
            // Replaces all occurrences of the string "%n" with "not", but won't replace "%n{"
            englishSentence = englishSentence.replaceAll("%n(?!\\{)", "not");
        }
        if (debug) System.out.println("Is question: " + isQuestion);
        if (debug) System.out.println("Is negated : " + isNegated);
        String formatPrefix = (isNegated) ?
                                ((isQuestion) ? "%qn{" : "%n{") :
                                ((isQuestion) ? "%qp{" : "%p{");

        String regex = java.util.regex.Pattern.quote(formatPrefix) + "(.*?)\\}";
        englishSentence = englishSentence.replaceAll(regex, "$1");

        // delete all the unused negative commands
        englishSentence = englishSentence.replace(" %n "," ");
        englishSentence = englishSentence.replace("%n "," ");
        englishSentence = englishSentence.replaceAll(" %n\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%n\\{.+?\\} "," ");
        // delete all unused positive commands
        englishSentence = englishSentence.replaceAll(" %p\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%p\\{.+?\\} "," ");
        // delete all unused positive question commands
        englishSentence = englishSentence.replaceAll(" %qp\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qp\\{.+?\\} "," ");
        // delete all unused negative question commands
        englishSentence = englishSentence.replaceAll(" %qn\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qn\\{.+?\\} "," ");
        return true;
    }

    public static boolean handleArgs() {
        domainsAll = kb.askWithRestriction(0, "domain", 1, randRelation);
        List<Formula> domainSubclasses = kb.askWithRestriction(0, "domainSubclass", 1, randRelation);
        domainsAll.addAll(domainSubclasses);
        int valence = domainsAll.size();
        if (valence == 0) {
            System.out.println("ERROR in GenRelations.java. Relation " + randRelation + " has no domain statements, but has format.");
            return false;
        }
        logicPhrase = (isNegated) ? "(not ": "";
        logicPhrase += "(" + randRelation + " ";
        for (int i = 1; i <= valence; i++) {
            if (englishSentence.contains("%" + i)) {
                String[] d = getDomainArgNum(i + "");
                if (d != null) {
                    String argClass = d[ARG_CLASS];
                    if (d[ARG_CLASS].equals("Formula") || d[ARG_CLASS].equals("Predicate")) {
                        if (debug) System.out.println("Unable to process formula or predicate (they are higher order)");
                        return false;
                    }
                    if (debug) System.out.println("argClass: " + argClass);
                    List<String> childClasses = new ArrayList<>();
                    childClasses.add(argClass);
                    Set<String> childClassesSet = kb.kbCache.getChildClasses(argClass);
                    if (childClassesSet != null) {
                        childClasses.addAll(childClassesSet);
                    }
                    String randomSubclass = childClasses.get(random.nextInt(childClasses.size()));
                    if(debug) System.out.println("randomSubclass: " + randomSubclass);
                    List<String> randomSubclassTermFormats = allTermFormats.get(randomSubclass);
                    if (randomSubclassTermFormats != null) {
                        String randomSubclassTermFormat = randomSubclassTermFormats.get(random.nextInt(randomSubclassTermFormats.size()));
                        if (d[DOMAIN_TYPE].equals("domainSubclass")) {
                            logicPhrase += " " + randomSubclass;
                            englishSentence = englishSentence.replace("%"+i, randomSubclassTermFormat);
                        } else if (d[DOMAIN_TYPE].equals("domain")) {
                            String randomVariableName = genUtils.randomVariableName();
                            logicPhrase = "(instance " + randomVariableName + " " + randomSubclass + ")" + logicPhrase;
                            logicPhrase += " " + randomVariableName;
                            englishSentence = englishSentence.replace("%"+i, "the " + randomSubclassTermFormat);
                        } else {
                            System.out.println("ERROR in GenRelations.java. Unknown type (its not domain or domainSubclass) in formula: " + d[DOMAIN_TYPE]);
                        }
                    } else {
                        System.out.println("ERROR in GenRelations.java. No TermFormat for: " + randomSubclass);
                        return false;
                    }
                } else {
                    System.out.println("ERROR in GenRelations.java. Could not find domain or domainSubclass with argnum of " + i + " for relation " + randRelation);
                    return false;
                }
            } else if (isQuestion) {
                //TODO: Finish this!!!!
                logicPhrase += "?X" + i + " ";
            } else {
                System.out.println("ERROR in GenRelations.java. Relation format for " + randRelation + " missing argument %" + i);
                return false;
            }
        }
        logicPhrase = (isNegated) ? logicPhrase + "))" : logicPhrase + ")";
        return true;
    }


    public static boolean handlePercents() {
        if (englishSentence.contains("%") && !englishSentence.contains("%%")) {
            System.out.println("ERROR in GenRelations.java. Relation format has more variables than domains for relation " + randRelation);
            return false;
        }
        englishSentence = englishSentence.replace("%%", "%");
        // TODO: implement %*. There are only a handful of cases where this is needed.
        return true;
    }

    public static void main(String[] args) throws Exception {

        init(args);
        System.out.println("Finished initialization, beginning sentence generation.");
        sentenceGeneratedCounter = 0;
        while (sentenceGeneratedCounter < numToGenerate) {
            resetGenParams();
            randRelation = allSUMORelationsRandSet.getNext();
            randRelationFormats = allFormats.get(randRelation);
            if (randRelationFormats != null) {
                englishSentence = randRelationFormats.get(random.nextInt(randRelationFormats.size()));
                if (englishSentence != null) {
                    if (debug) System.out.println("\n\n--------------------Next sentence--------------------- \n");
                    if (debug) System.out.println(randRelation);
                    for (String format : randRelationFormats) {
                        if (debug) System.out.println("    format: " + format);
                    }
                    if (debug) System.out.println("    random Format chosen: " + englishSentence);

                    if (handleLinks() && handleFormatSymbols() && handleArgs() && handlePercents()) {
                        englishSentence = englishSentence.substring(0, 1).toUpperCase() + englishSentence.substring(1);
                        englishSentence = (isQuestion) ? englishSentence + "?" : englishSentence + ".";
                        if (debug) System.out.println("Final English sentence : " + englishSentence);
                        if (debug) System.out.println("Final Logic phrase     : " + logicPhrase);
                        GenUtils.writeEnglishLogicPairToFile(englishSentence, logicPhrase, outputFileEnglish, outputFileLogic);
                        sentenceGeneratedCounter++;
                        if (englishSentence.contains("%1")) {
                            System.exit(0);
                        }
                    }
                }
            } else {
                if (debug) System.out.println("    No format for: " + randRelation);
            }

        }
        System.out.println("\n\n\nSuccessfully generated " + numToGenerate + " sentences. Make it a great day!\n");
    }
}