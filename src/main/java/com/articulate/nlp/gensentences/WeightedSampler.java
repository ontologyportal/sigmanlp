package com.articulate.nlp.gensentences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/***************************************************************
 * Weighted sampler for selecting a term from weighted class candidates.
 ***************************************************************/
public class WeightedSampler {

    public List<WeightedClass> weightedClasses = new ArrayList<>();
    public int totalWeight = 0;
    private static final Random rand = new Random();

    /**
     * Adds one weighted class with multiple candidate terms.
     * Entries with non-positive weight or empty candidates are ignored.
     */
    public void addWeightedClass(String className, int weight, Collection<String> candidates) {

        if (weight <= 0 || candidates == null || candidates.isEmpty()) {
            return;
        }
        WeightedClass weightedClass = new WeightedClass();
        weightedClass.className = className;
        weightedClass.weight = weight;
        weightedClass.candidates = new ArrayList<>(candidates);
        weightedClasses.add(weightedClass);
        totalWeight += weightedClass.weight;
    }

    /**
     * Convenience helper for adding a class with exactly one candidate term.
     */
    public void addSingleCandidate(String className, int weight, String candidate) {

        if (candidate == null || candidate.trim().isEmpty()) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(candidate);
        addWeightedClass(className, weight, candidates);
    }

    /**
     * Samples one term using generic error context.
     */
    public String sampleTerm() {

        return sampleTerm("unspecified", 0);
    }

    /**
     * Samples one term by:
     * 1) selecting a weighted class by cumulative weight,
     * 2) selecting a random candidate from that class.
     */
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
    /** Logical class label used for diagnostics. */
    public String className;
    /** Weight used during class-level sampling. */
    public int weight;
    /** Concrete terms that can be selected once the class is chosen. */
    public List<String> candidates;
}
