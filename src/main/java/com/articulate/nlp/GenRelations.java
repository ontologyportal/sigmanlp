package com.articulate.nlp;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;

import java.io.*;
import java.util.Collection;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
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
    public static KBLite kbLite;
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
    public static HashSet<String> allSUMONumbersSet;
    public static boolean isQuestion;
    public static boolean isNegated;
    public static String randRelation;
    public static List<String> randRelationFormats;
    public static GenUtils genUtils;

    public static final int DOMAIN_TYPE = 0;
    public static final int TERM = 1;
    public static final int ARG_NUM = 2;
    public static final int ARG_CLASS = 3;

    public static ArrayList<String> logicVariables;
    public static ArrayList<String> logicInstanceFormulas;
    public static String logicRelationFormula;

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
        kbLite = new KBLite("SUMO");
        System.out.println("Finished loading KBs");
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
        Set<String> allSUMOFunctionsSet = kbLite.getAllInstances("Function");
        Set<String> allSUMOVariableAritySet = kbLite.getAllInstances("VariableArityRelation");
        Set<String> allSUMORelationsSet = kbLite.getAllInstances("Relation");
        allSUMORelationsSet.removeAll(allSUMOFunctionsSet);
        allSUMORelationsSet.removeAll(allSUMOVariableAritySet);
        allSUMORelationsSet.remove("Function");
        allSUMORelationsSet.remove("VariableArityRelation");
        allSUMORelationsSet.remove("documentation");

        allSUMORelationsRandSet = RandSet.listToEqualPairs(allSUMORelationsSet);
        allTermFormats = kbLite.getTermFormatMap();
        allFormats = kbLite.getFormatMap();
        random = new Random();
        genUtils = new GenUtils();

        // create output files
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        genUtils.createFileIfDoesNotExists(outputFileEnglish);
        genUtils.createFileIfDoesNotExists(outputFileLogic);
    }

    /** ***************************************************************
     *   Resets parameters for generation. To be called
     *   at the beginning of each new sentence generation.
     */
    public static void resetGenParams() {

        isQuestion = GenUtils.biasedBoolean(1, 2);
        isNegated = GenUtils.biasedBoolean(1, 2);
        genUtils.resetVariables();
        logicPhrase = "";
        englishSentence = "";
        logicVariables = new ArrayList();
        logicInstanceFormulas = new ArrayList();
        logicRelationFormula = "";
    }

    /** ***************************************************************
     *   For a domain statement, splits into a string array.
     *   (domain myFormula 1 myClass)
     *   returns
     *   ["domain", "myFormula", "1", "myClass"]
     */
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

    /** ***************************************************************
     *   For a given relation, gets the domain formula associated
     *   with an index.
     *   if the relation is (myRelation arg1 arg2), then getDomainArgNum(1)
     *   will return
     *   ["domain", "arg1", "1", "classOfArg1"]
     */
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

    /** ***************************************************************
     *   Builds the logic formula from constituent logic parts
     */
    public static String buildLogicFormula() {
        String returnFormula = logicRelationFormula;
        if (!logicInstanceFormulas.isEmpty()) {
            for (String f:logicInstanceFormulas) {
                returnFormula = f+returnFormula;
            }
            returnFormula = "(and " + returnFormula + ")";
        }
        if (!logicVariables.isEmpty()) {
            String existentialList = "";
            for (String v:logicVariables) {
                existentialList += v + " ";
            }
            returnFormula = "(exists (" + existentialList.substring(0, existentialList.length()-1) + ")" + returnFormula + ")";
        }
        if (isNegated) {
            returnFormula = "(not " + returnFormula + ")";
        }
        return returnFormula;
    }

    /** ***************************************************************
     *   Handles links in the format statement.
     */
    public static boolean handleLinks() {

        englishSentence = englishSentence.replace("&%", "");
        return true;
    }

    /** ***************************************************************
     *   handles format symbols in the format statement.
     */
    public static boolean handleFormatSymbols() {

        if (isQuestion && !isNegated) {
            if (!englishSentence.contains("%qp{")) {
                return false;
            }
        } else if (isQuestion && isNegated) {
             if (!englishSentence.contains("%qn{")) {
                 return false;
             }
        }
        if (!isQuestion && (englishSentence.contains("%qn{") || englishSentence.contains("%qp{"))) {
            return false;
        }
        if (isNegated) {
            if (!englishSentence.contains("%n")) {
                return false;
            } else{
                // Replaces all occurrences of the string "%n" with "not", but won't replace "%n{"
                englishSentence = englishSentence.replaceAll("%n(?!\\{)", "not");
            }
        }
        if (debug) System.out.println("Is question: " + isQuestion);
        if (debug) System.out.println("Is negated : " + isNegated);
        String formatPrefix = (isNegated) ?
                                ((isQuestion) ? "%qn{" : "%n{") :
                                ((isQuestion) ? "%qp{" : "%p{");

        String regex = java.util.regex.Pattern.quote(formatPrefix) + "(.*?)\\}";
        englishSentence = englishSentence.replaceAll(regex, "$1");

        // delete all the unused negative commands
        englishSentence = englishSentence.replaceAll(" %n\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%n\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%n\\{.+?\\}","");
        englishSentence = englishSentence.replace(" %n "," ");
        englishSentence = englishSentence.replace("%n "," ");
        englishSentence = englishSentence.replace("%n","");
        // delete all unused positive commands
        englishSentence = englishSentence.replaceAll(" %p\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%p\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%p\\{.+?\\}","");
        // delete all unused positive question commands
        englishSentence = englishSentence.replaceAll(" %qp\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qp\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qp\\{.+?\\}","");
        // delete all unused negative question commands
        englishSentence = englishSentence.replaceAll(" %qn\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qn\\{.+?\\} "," ");
        englishSentence = englishSentence.replaceAll("%qn\\{.+?\\}","");
        return true;
    }

    /** ***************************************************************
     *   Handles arguments in the format statement (i.e. %1, %2 ...).
     */
    public static boolean handleArgs() {
        domainsAll = kbLite.askWithRestriction(0, "domain", 1, randRelation);
        List<Formula> domainSubclasses = kbLite.askWithRestriction(0, "domainSubclass", 1, randRelation);
        domainsAll.addAll(domainSubclasses);
        int valence = domainsAll.size();
        if (valence == 0) {
            System.out.println("ERROR in GenRelations.java. Relation " + randRelation + " has no domain statements, but has format.");
            return false;
        }
        logicRelationFormula += "(" + randRelation;
        for (int i = 1; i <= valence; i++) {
            if (englishSentence.contains("%" + i)) {
                String[] d = getDomainArgNum(i + "");
                if (d != null) {
                    String argClass = d[ARG_CLASS];
                    if (debug) System.out.println("argClass: " + argClass);
                    if (d[ARG_CLASS].equals("Formula") || d[ARG_CLASS].equals("Predicate")) {
                        if (debug) System.out.println("Unable to process formula or predicate (they are higher order)");
                        return false;
                    }
                    List<String> childClasses = new ArrayList<>();
                    childClasses.add(argClass);
                    Set<String> childClassesSet = kbLite.getChildClasses(argClass);
                    if (childClassesSet != null) {
                        childClasses.addAll(childClassesSet);
                    }
                    String randomSubclass = childClasses.get(random.nextInt(childClasses.size()));
                    if (kbLite.subclassOf(randomSubclass, "Quantity")) {
                        // TO DO: Handle numbers
                        // System.out.println("Numbers not supported for: " + randRelation + " " + randomSubclass);
                        return false;
                    }
                    if(debug) System.out.println("randomSubclass: " + randomSubclass);
                    List<String> randomSubclassTermFormats = allTermFormats.get(randomSubclass);
                    if (randomSubclassTermFormats != null) {
                        String randomSubclassTermFormat = randomSubclassTermFormats.get(random.nextInt(randomSubclassTermFormats.size()));
                        if (d[DOMAIN_TYPE].equals("domainSubclass")) {
                            logicRelationFormula += " " + randomSubclass;
                            englishSentence = englishSentence.replace("%"+i, randomSubclassTermFormat);
                        } else if (d[DOMAIN_TYPE].equals("domain")) {
                            String randomVariableName = genUtils.randomVariableName();
                            logicVariables.add(randomVariableName);
                            logicInstanceFormulas.add("(instance " + randomVariableName + " " + randomSubclass + ")");
                            logicRelationFormula += " " + randomVariableName;
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
                return false;
            } else {
                System.out.println("ERROR in GenRelations.java. Relation format for " + randRelation + " missing argument %" + i + ". EnglishSentence: " + englishSentence);
                return false;
            }
        }
        logicRelationFormula += ")";
        return true;
    }

    /** ***************************************************************
     *   Handles the % escape character in format statements.
     */
    public static boolean handlePercentsInFormats() {

        if (englishSentence.contains("%") && !englishSentence.contains("%%")) {
            System.out.println("ERROR in GenRelations.java. Relation format has more variables than domains for relation " + randRelation + ". Remaining format statement, after substitution of possible variables: " + englishSentence);
            return false;
        }
        englishSentence = englishSentence.replace("%%", "%");
        // TODO: implement %*. There are only a handful of cases where this is needed.
        return true;
    }

    /** ***************************************************************
     *   Main method. Has the generation loop
     *      1. Setup
     *      2. Get random relation
     *      3. Get random format statement associated with relation
     *      4. Select values for format statement
     *      5. Write english/logic pair to file
     */
    public static void main(String[] args) throws Exception {

        init(args);
        System.out.println("Numbersets: " + allSUMONumbersSet);
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

                    if (handleLinks() && handleFormatSymbols() && handleArgs() && handlePercentsInFormats()) {
                        englishSentence = englishSentence.substring(0, 1).toUpperCase() + englishSentence.substring(1);
                        englishSentence = englishSentence.replaceAll("\\p{Punct}$", "");  // Removes any punctuation that might have been in the format.
                        englishSentence = (isQuestion) ? englishSentence + "?" : englishSentence + ".";  // Add back in the appropriate punctuation.
                        logicPhrase = buildLogicFormula();
                        if (debug) System.out.println("Final English sentence : " + englishSentence);
                        if (debug) System.out.println("Final Logic phrase     : " + logicPhrase);
                        GenUtils.writeEnglishLogicPairToFile(englishSentence, logicPhrase, outputFileEnglish, outputFileLogic);
                        sentenceGeneratedCounter++;
                    }
                }
            } else {
                if (debug) System.out.println("    No format for: " + randRelation);
            }

        }
        System.out.println("\n\n\nSuccessfully generated " + numToGenerate + " sentences. Make it a great day!\n");
    }
}


            // Code to loop through each relation in alphabetical order
           /*
        PrintWriter writer = new PrintWriter("relations_ideas.txt");
             // DELETE ME
        treeSet = new TreeSet<>(allSUMORelationsSet);
        //DELETE ME END
           public static TreeSet<String> treeSet;  // Now it's sorted!

           randRelation = treeSet.pollFirst();

            allSUMORelationsRandSet.remove(randRelation);
            if (randRelation == null || randRelation.equals("")) {
                sentenceGeneratedCounter = 999999;
                writer.close();
                System.exit(0);
            }
            String formatResults = genUtils.runBashCommand("grep \"(format EnglishLanguage " + randRelation + " \" ../sumo/*.kif");
            String documentResults = genUtils.runBashCommand("grep \"(documentation " + randRelation + " EnglishLanguage" + "\" ../sumo/*.kif");
            String domainResults = genUtils.runBashCommand("grep \"(domain " + randRelation + " \" ../sumo/*.kif");
            String domainSubclassResults = genUtils.runBashCommand("grep \"(domainSubclass " + randRelation + " \" ../sumo/*.kif");

            formatResults = Arrays.stream(formatResults.split("\n"))
                    .map(line -> line.contains(":") ? line.split(":", 2)[1] + ":" + line.split(":", 2)[0] : line)
                    .collect(Collectors.joining("\n"));
            documentResults = Arrays.stream(documentResults.split("\n"))
                    .map(line -> line.contains(":") ? line.split(":", 2)[1] + ":" + line.split(":", 2)[0] : line)
                    .collect(Collectors.joining("\n"));
            domainResults = Arrays.stream(domainResults.split("\n"))
                    .map(line -> line.contains(":") ? line.split(":", 2)[1] + ":" + line.split(":", 2)[0] : line)
                    .collect(Collectors.joining("\n"));
            domainSubclassResults = Arrays.stream(domainSubclassResults.split("\n"))
                    .map(line -> line.contains(":") ? line.split(":", 2)[1] + ":" + line.split(":", 2)[0] : line)
                    .collect(Collectors.joining("\n"));

            formatResults = formatResults.replace("\n", "\n;; ");
            documentResults = documentResults.replace("\n", "\n;; ");
            domainResults = domainResults.replace("\n", "\n;; ");
            domainSubclassResults = domainSubclassResults.replace("\n", "\n;; ");
            formatResults = formatResults.replace(":../sumo/", "\t; ");
            documentResults = documentResults.replace(":../sumo/", "\t; ");
            domainResults = domainResults.replace(":../sumo/", "\t; ");
            domainSubclassResults = domainSubclassResults.replace(":../sumo/", "\t; ");

            writer.println(";;;;;;;;;;;;;;;;;;;;;;;; " + randRelation + " ;;;;;;;;;;;;;;;;;;;;;;;;");
            if (!documentResults.contains("Command exited with code 1")) {
                writer.println(";; " + documentResults);
            }
            else {
                writer.println(";; No document statement found for " + randRelation);
            }
            writer.println(";; " + domainResults);
            if (!domainSubclassResults.contains("Command exited with code 1")) {
                writer.println(";; " + domainSubclassResults);
            }
            if (!formatResults.contains("Command exited with code 1")) {
                writer.println(";; " + formatResults);
            }
            else {
                writer.println(";; No format found for " + randRelation);
            }
            writer.println("\n; (format EnglishLanguage " + randRelation + " \"\")\n\n\n\n");
            System.out.println("numGenerated: " + sentenceGeneratedCounter);
            // DELETE END
*/