package com.articulate.nlp.gensentences;

import com.articulate.nlp.GenUtils;
import com.articulate.nlp.KBLite;
import com.articulate.nlp.LFeatureSets;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.wordNet.WordNet;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***************************************************************
 * Performs synthetic sentence generation from JSON templates.
 ***************************************************************/
public class GenFromTemplate {

    public static String outputFileEnglish = "from_template-eng.txt";
    public static String outputFileLogic = "from_template-log.txt";
    public static KBLite kbLite;
    public static LFeatureSets lfeatsets;
    public static final Set<String> suppress = new HashSet<>(Arrays.asList());
    public static Map<Integer, WeightedSampler> slotSamplers = new HashMap<>();

    /***************************************************************
     * Initializes generation resources.
     ***************************************************************/
    public static void init(String filePrefix) {

        outputFileEnglish = filePrefix + "-eng.txt";
        outputFileLogic = filePrefix + "-log.txt";
        if (kbLite == null) {
            kbLite = new KBLite("SUMO");
            KBmanager.getMgr().setPref("kbDir", System.getenv("SIGMA_HOME") + "/KBs");
            WordNet.initOnce();
        }
        lfeatsets = GenUtils.initLFeatureSets(kbLite, suppress);
        GenUtils.createFileIfDoesNotExists(outputFileEnglish);
        GenUtils.createFileIfDoesNotExists(outputFileLogic);
    }

    /***************************************************************
     * Build one weighted sampler per slot from slot sumo_terms.
     ***************************************************************/
    public static Map<Integer, WeightedSampler> buildSlotSamplers(Templates.Template template) {

        Map<Integer, WeightedSampler> result = new HashMap<>();
        Templates.Slot[] slots = template.getSlots();
        for (int i = 0; i < slots.length; i++) {
            Templates.Slot slot = slots[i];
            if (slot == null || slot.getSumoTerms() == null || slot.getSumoTerms().isEmpty()) {
                result.put(i + 1, new WeightedSampler());
                continue;
            }
            WeightedSampler slotSampler = new WeightedSampler();
            for (Templates.WeightedSumoTerm sumoTerm : slot.getSumoTerms()) {
                if (sumoTerm.getWeight() <= 0) {
                    continue;
                }
                Set<String> candidates = null;
                if (slot.getType() == Templates.Slot.TermSelectionType.SUBCLASS) {
                    candidates = kbLite.getChildClasses(sumoTerm.getName());
                    if (candidates != null) {
                        candidates.add(sumoTerm.getName());
                    }
                }
                else if (slot.getType() == Templates.Slot.TermSelectionType.INSTANCE) {
                    candidates = kbLite.getInstancesForType(sumoTerm.getName());
                }
                if (candidates == null || candidates.isEmpty()) {
                    System.err.println("Error: template '" + template.getName() + "' slot %" + (i + 1)
                            + " class '" + sumoTerm.getName() + "' has no candidates for type="
                            + slot.getType().name().toLowerCase() + " but weight=" + sumoTerm.getWeight() + ".");
                    System.exit(1);
                }
                WeightedClass weightedClass = new WeightedClass();
                weightedClass.className = sumoTerm.getName();
                weightedClass.weight = sumoTerm.getWeight();
                weightedClass.candidates = new java.util.ArrayList<>(candidates);
                slotSampler.weightedClasses.add(weightedClass);
                slotSampler.totalWeight += weightedClass.weight;
            }
            result.put(i + 1, slotSampler);
        }
        return result;
    }

    /***************************************************************
     * Replace %1, %2, ... style slots in a template.
     ***************************************************************/
    private static String replaceSlots(String frame, Map<Integer, String> slotValues) {

        Pattern pattern = Pattern.compile("%(\\d+)");
        Matcher matcher = pattern.matcher(frame);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int slotNum = Integer.parseInt(matcher.group(1));
            String value = slotValues.get(slotNum);
            if (value == null) {
                value = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /***************************************************************
     * Processes a single template and writes default outputs.
     ***************************************************************/
    public static void processTemplate(Templates.Template template) {

        slotSamplers = buildSlotSamplers(template);
        int numToGen = template.getNumToGen();
        System.out.println("Generating " + numToGen + " sentence(s) from template '" + template.getName() + "'");
        String logicFrame = template.getLogic();
        int slotCount = template.getSlots().length;
        for (int i = 0; i < numToGen; i++) {
            String englishFrame = template.getEnglishFrame().get(Templates.Tense.DEFAULT);
            if (englishFrame == null) {
                System.err.println("Error: template '" + template.getName()
                        + "' is missing english.frame.tense_default.");
                System.exit(1);
            }
            Map<Integer, String> logicSlotValues = new HashMap<>();
            Map<Integer, String> englishSlotValues = new HashMap<>();
            for (int slotNum = 1; slotNum <= slotCount; slotNum++) {
                WeightedSampler slotSampler = slotSamplers.get(slotNum);
                if (slotSampler == null) {
                    System.err.println("Error: template '" + template.getName()
                            + "' slot %" + slotNum + " has no terms to sample.");
                    System.exit(1);
                }
                String term = slotSampler.sampleTerm(template.getName(), slotNum);
                logicSlotValues.put(slotNum, term);
                String termFormat = kbLite.getTermFormat(term);
                if (termFormat == null || termFormat.isEmpty()) {
                    termFormat = term;
                }
                englishSlotValues.put(slotNum, termFormat);
            }
            String logic = replaceSlots(logicFrame, logicSlotValues);
            String english = replaceSlots(englishFrame, englishSlotValues);
            GenUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);
        }
    }

    /***************************************************************
     * Entry point for loading and printing template metadata.
     ***************************************************************/
    public static void main(String[] args) {

        String home = System.getProperty("user.home");
        String templatePath = home + "/workspace/sigmanlp/src/main/java/com/articulate/nlp/gensentences/templates.json";
        String filePrefix = "from_template";
        if (args != null && args.length > 0) {
            if ("-h".equals(args[0]) || "--help".equals(args[0])) {
                System.out.println("Usage: GenFromTemplate <file-prefix> [templates-json-file-path]");
                return;
            }
            if (args[0] != null && !args[0].trim().isEmpty()) {
                filePrefix = args[0].trim();
            }
            if (args.length > 1) {
                templatePath = args[1];
            }
        }
        try {
            init(filePrefix);
            Templates templates = Templates.read(templatePath);
            System.out.println("Loaded templates.json with " + templates.size() + " template(s).");
            Templates.Template template;
            int i = 0;
            while ((template = templates.getNext()) != null) {
                System.out.println("Generating from template " + i + " of " + templates.size() + ": " + template.getName()
                + " (slots=" + template.getSlots().length + ")");
                processTemplate(template);
                i++;
            }
        }
        catch (IOException ex) {
            System.err.println("Unable to read templates.json: " + ex.getMessage());
        }
    }

}
