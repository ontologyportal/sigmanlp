package com.articulate.nlp.gensentences;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/***************************************************************
 * Weighted sampler for selecting a term from weighted class candidates.
 ***************************************************************/
public class WeightedSampler {

    public List<WeightedClass> weightedClasses = new ArrayList<>();
    public int totalWeight = 0;
    private static final Random rand = new Random();

    public String sampleTerm(String templateName, int slotNum) {

        if (weightedClasses.isEmpty() || totalWeight <= 0) {
            System.err.println("Error: template '" + templateName + "' slot %" + slotNum
                    + " has no positive-weight class to sample.");
            System.exit(1);
        }
        int draw = rand.nextInt(totalWeight);
        int running = 0;
        for (WeightedClass weightedClass : weightedClasses) {
            running += weightedClass.weight;
            if (draw < running) {
                if (weightedClass.candidates == null || weightedClass.candidates.isEmpty()) {
                    System.err.println("Error: template '" + templateName + "' slot %" + slotNum
                            + " selected class '" + weightedClass.className + "' with no candidates.");
                    System.exit(1);
                }
                return weightedClass.candidates.get(rand.nextInt(weightedClass.candidates.size()));
            }
        }
        return weightedClasses.get(weightedClasses.size() - 1).candidates.get(0);
    }
}

/***************************************************************
 * Weighted class candidate used by WeightedSampler.
 ***************************************************************/
class WeightedClass {
    public String className;
    public int weight;
    public List<String> candidates;
}
