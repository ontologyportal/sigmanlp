package com.articulate.nlp;


import java.util.*;

/** Generates English/Logic pairs for "is a subclass" and "is an instance of".
 *  The first part of factoring out allAxioms from GenSimpTestData.java
 */
public class GenTaxonomy {
    public static KBLite kbLite;
    public static GenUtils genUtils;
    public static String outputFileEnglish;
    public static String outputFileLogic;
    public static Random rand = new Random();

    /** ***************************************************************
     *   Initiates important variables and objects needed
     *   for taxonomy generation.
     */
    public static void init(String[] args) {
        genUtils = new GenUtils();
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        genUtils.createFileIfDoesNotExists(outputFileEnglish);
        genUtils.createFileIfDoesNotExists(outputFileLogic);
        kbLite = new KBLite("SUMO");
        System.out.println("Finished loading KBs");
    }

    // Some equivalent mappings are in serious need of improvement before they can be used.
    public static List<String> DISALLOWED_PARENTS = Arrays.asList("SubjectiveAssessmentAttribute", "BiologicalAttribute");
    public static void writeRelations(Set<String> relationSet, String connectingString, String relationString) {
        for (String term : relationSet) {
            List<String> termFormatsOfTerm = kbLite.termFormats.get(term);
            List<String> parentsOfTerm = kbLite.parentsOf.get(term);
            if (termFormatsOfTerm == null) continue;
            for (String termFormatOfTerm : termFormatsOfTerm) {
                for (String parent : parentsOfTerm) {
                    if (DISALLOWED_PARENTS.contains(parent)) continue;
                    List<String> termFormatsOfParent = kbLite.termFormats.get(parent);
                    if (termFormatsOfParent == null) continue;
                    // This does a random term format
                    String randomTermFormatOfParent = termFormatsOfParent.get(rand.nextInt(termFormatsOfParent.size()));
                    String english = genUtils.capitalizeFirstLetter(termFormatOfTerm) + connectingString + randomTermFormatOfParent + ".";
                    String logic = "(" + relationString + " " + term + " " + parent + ")";
                    genUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);
                    /* This does it for EVERY termFormat
                    for (String parentTermFormat : termFormatsOfParent) {
                        String english = genUtils.capitalizeFirstLetter(termFormatOfTerm) + connectingString + parentTermFormat + ".";
                        String logic = "(" + relationString + " " + term + " " + parent + ")";
                        genUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);
                    }
                     */
                }
            }
        }
        System.out.println("Finished writing " + relationString);
    }

    public static void main(String[] args) {

        init(args);
        writeRelations(kbLite.subclasses, " is a subclass of ", "subclass");
        writeRelations(kbLite.instances, " is an instance of ", "instance");
        writeRelations(kbLite.subAttributes, " is a sub-attribute of ", "subAttribute");
        writeRelations(kbLite.subrelations, " is a sub-relation of ", "subrelation");
    }

}