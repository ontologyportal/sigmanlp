package com.articulate.nlp;

import java.util.*;
import java.io.*;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
    

public class GenOrientations {
    public static int numToGenerate;
    public static RandSet allSUMOArtifactRandSet;
    public static RandSet allSUMOOrientationRandSet;
    public static Random random;
    public static Map<String, List<String>> allTermFormats;
    public static Map<String, List<String>> allFormats;
    public static KBLite kbLite;
    public static String outputFileEnglish = "orientations-eng.txt";
    public static String outputFileLogic = "orientations-log.txt";
    public static GenUtils genUtils;
    public static int sentenceGeneratedCounter;
    public static String randSUMOOrientation;
    public static String randSUMOArtifact1;
    public static String randSUMOArtifact2;
    //public static List<String> randOreintationFormats;
    public static String logicPhrase;
    public static String englishSentence;
    public static String randEngOrientation;
    public static String randEngArtifact1;
    public static String randEngArtifact2;
    public static String filepath;
    public static List<String> imageList = new ArrayList<>();
    public static ArrayNode rootArray;
    public static List<String> SUMOOrientationExlcusionList; 
    public static int id; 
    public static String key;
    public static int max;
    /** ***************************************************************
     *   Initiates important variables and objects needed
     *   for orientation generation.
     */

    public static void init(String[] args) {

        // parse input variables
        if (args == null || args.length != 2 || args[0].equals("-h")) {
            System.out.println("Usage: GenOrientations <file prefix> <num to generate>");
            System.exit(0);
        }
        numToGenerate = Integer.parseInt(args[1]);


        // load the knowledge base
        kbLite = new KBLite("SUMO");
        System.out.println("Finished loading KBs");
        KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + File.separator + "KBs");
        WordNet.initOnce();
        //Organizing the set of all things you want from SUMO
        Set<String> allSUMOArtifactSet = kbLite.getChildClasses("Artifact");
        System.out.println("Number in allSUMOArtifactSet "+allSUMOArtifactSet.size());
        //Set<String> allSUMOVariableAritySet = kbLite.getAllInstances("VariableArityRelation");
        Set<String> allSUMOOrientationSet = kbLite.getAllInstances("PositionalAttribute");
        SUMOOrientationExlcusionList = new ArrayList<>(Arrays.asList("Downstairs", "North", "East", "South", "West", "Horizontal", "Upstairs", "Vertical"));
        allSUMOOrientationSet.removeAll(SUMOOrientationExlcusionList);
        System.out.println("Number in allSUMOOrientationSet "+allSUMOOrientationSet.size());
        //missing on
        //allSUMORelationsSet.removeAll(allSUMOFunctionsSet);
        //allSUMORelationsSet.removeAll(allSUMOVariableAritySet);
        //allSUMORelationsSet.remove("Function");
        //allSUMORelationsSet.remove("VariableArityRelation");
        //allSUMORelationsSet.remove("documentation");

        //this creates sets randomly from the artifact and orientation set
        allSUMOArtifactRandSet = RandSet.listToEqualPairs(allSUMOArtifactSet);
        allSUMOOrientationRandSet = RandSet.listToEqualPairs(allSUMOOrientationSet);
        allTermFormats = kbLite.getTermFormatMap();
        allFormats = kbLite.getFormatMap();
        random = new Random();
        genUtils = new GenUtils();

        // create output files
        outputFileEnglish = args[0] + "-eng.txt";
        outputFileLogic = args[0] + "-log.txt";
        genUtils.createFileIfDoesNotExists(outputFileEnglish);
        genUtils.createFileIfDoesNotExists(outputFileLogic);
        //add create .json file here
    }
    /** ***************************************************************
     *   Resets parameters for generation. To be called
     *   at the beginning of each new sentence generation.
     */
    public static void resetGenParams() {

        //isQuestion = GenSimpTestData.biasedBoolean(1, 2);
        //isNegated = GenSimpTestData.biasedBoolean(1, 2);
        genUtils.resetVariables();
        logicPhrase = "";
        englishSentence = "";
        //logicVariables = new ArrayList();
        //logicInstanceFormulas = new ArrayList();
        //logicRelationFormula = "";
    }


    
    
    public static void addEntry(
            String filePath,
            int id, 
            List<String> imageList,
            String languageDescription,
            String logicalDescription
    ) {

        ObjectMapper mapper = new ObjectMapper();
        File file = new File(filePath);

        // Read existing JSON array (or create a new one if file doesn't exist)
        
        try {
            if (file.exists() && file.length() > 0) {
            rootArray = (ArrayNode) mapper.readTree(file);
           
        } else {
            rootArray = mapper.createArrayNode();
        }
            
        } catch (IOException e) {
            System.err.println("An I/O error occurred: " + e.getMessage());
            e.printStackTrace(); // Print the stack trace for debugging
        }

        // Create new JSON object
        //edit format for image list strings
        ObjectNode newEntry = mapper.createObjectNode();
        newEntry.put("id", id);
        newEntry.putPOJO("image_list", imageList);
        newEntry.put("language_description", languageDescription);
        newEntry.put("logical_description", logicalDescription);
        //autoincrement id, check for largest then increment from there use chatgpt

        // Add to array
        rootArray.add(newEntry);

        // Write back to file (pretty-printed)
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootArray);
        } catch (IOException e) {
            System.err.println("An I/O error occurred: " + e.getMessage());
            e.printStackTrace(); // Print the stack trace for debugging
        }
    }
    
    public static String coherenceDetector() {
        Scanner scanner = new Scanner(System.in);
        String input;

        while (true) {
            System.out.print("Enter \"a\" for coherent sentence or \"d\" for an incoherent sentence and then hit enter: ");
            input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("a") || input.equals("d")) {
                return input;
            }

            System.out.println("Invalid input. Please type a or d.");
        }
    }

    public static int getMaxIntValue(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        int max = Integer.MIN_VALUE;
        key = "id";
        try {
            JsonNode root = mapper.readTree(new File(filePath));
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.has(key) && node.get(key).isInt()) {
                        max = Math.max(max, node.get(key).asInt());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("An I/O error occurred: " + e.getMessage());
            e.printStackTrace(); // Print the stack trace for debugging
        }

        return max;
    }
    public static void main(String[] args){
        init(args);
        //System.out.println("Hello World!");
        //System.out.println(allSUMOArtifactRandSet.toString());
        // System.out.println(allSUMOOrientationRandSet.toString());
        sentenceGeneratedCounter = 0;
        //filepath to .json training data
        filepath = "./data.json";
        File file = new File(filepath);
        if (file.exists() && file.length() > 0) {
            id = getMaxIntValue(filepath);
            id ++;
           
        } else {
            id = 1;
        }
        
        while (sentenceGeneratedCounter < numToGenerate) {
            resetGenParams();
            //the below gets the sumo object
            randSUMOOrientation = allSUMOOrientationRandSet.getNext();
            randSUMOArtifact1 = allSUMOArtifactRandSet.getNext();
            randSUMOArtifact2 = allSUMOArtifactRandSet.getNext();
            //the below takes the sumo object and gets a random english format of it (usually only one)
            randEngOrientation = kbLite.getTermFormat(randSUMOOrientation);
            randEngArtifact1 = kbLite.getTermFormat(randSUMOArtifact1);
            randEngArtifact2 = kbLite.getTermFormat(randSUMOArtifact2);
        
            //randOreintationFormats = allFormats.get(randOrientation);formats are specific to relations
            //template "The <artifact1> is <orientation> the <artifact 2>."\
            //need to add prepostion to account for different orientations
            englishSentence = "The " +randEngArtifact1+" is "+randEngOrientation+" the "+randEngArtifact2+".";
            logicPhrase = "(exists (?A1 ?A2) (and (instance ?A1 "+randSUMOArtifact1+") (instance ?A2 "+randSUMOArtifact2+") (orientation ?A1 ?A2 "+randSUMOOrientation+")))";
            System.out.println();
            System.out.println(englishSentence);
            System.out.println();
            //System.out.println(logicPhrase);
            //insert coherence detector here
            String coherence =  coherenceDetector();   
            if (coherence.equals("a")){
                //System.out.println("in if statement");
                GenUtils.writeEnglishLogicPairToFile(englishSentence, logicPhrase, outputFileEnglish, outputFileLogic);
                //add write to .json file here
                String englishSentenceImageFormat = englishSentence.replace(" ","_").replace(".", "");
                //System.out.println(englishSentenceImageFormat);
                // imageList.add("./images/"+englishSentenceImageFormat+"_Test1.png");
                // imageList.add("./images/"+englishSentenceImageFormat +"_Test2.png");
                //need to append image to list, so need to fix input as string and figure out id problem
                /*addEntry(
                    "data.json",
                    2,
                    List.of("./images/book.png"),
                    "A book resting on a table.",
                    "The book object is positioned on a flat surface."
                );
                */
                
                addEntry(filepath, id, imageList, englishSentence, logicPhrase);
                //sentenceGeneratedCounter ++;
                id ++;
                imageList.clear();
            }
            sentenceGeneratedCounter ++;
        }
        //System.out.println(allSUMOOrientationRandSet.toString());
    }

}
/* need to take english sentence from here, combine it with wrappers from word doc (this will change),
feed it into an llm, save image to correct directory and save name to .json file here 
change image list to empty list, create a java file to access json file, read eng sent 
creat image, save image, cerate image title, save to json image list*/