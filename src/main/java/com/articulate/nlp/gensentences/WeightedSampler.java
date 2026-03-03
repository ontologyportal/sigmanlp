package com.articulate.nlp.gensentences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * @return this sampler for method chaining
     */
    public WeightedSampler addWeightedClass(String className, int weight, Collection<String> candidates) {

        if (weight <= 0 || candidates == null || candidates.isEmpty()) {
            return this;
        }
        WeightedClass weightedClass = new WeightedClass();
        weightedClass.className = className;
        weightedClass.weight = weight;
        weightedClass.candidates = new ArrayList<>(candidates);
        weightedClasses.add(weightedClass);
        totalWeight += weightedClass.weight;
        return this;
    }

    /**
     * Convenience helper for adding a class with exactly one candidate term.
     * @return this sampler for method chaining
     */
    public WeightedSampler addSingleCandidate(String className, int weight, String candidate) {

        if (candidate == null || candidate.trim().isEmpty()) {
            return this;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(candidate);
        return addWeightedClass(className, weight, candidates);
    }

    /**
     * Fluent API alias for addWeightedClass.
     * @return this sampler for method chaining
     */
    public WeightedSampler withClass(String className, int weight, Collection<String> candidates) {
        return addWeightedClass(className, weight, candidates);
    }

    /**
     * Fluent API alias for addSingleCandidate.
     * @return this sampler for method chaining
     */
    public WeightedSampler withCandidate(String className, int weight, String candidate) {
        return addSingleCandidate(className, weight, candidate);
    }

    /**
     * Bulk add candidates from a map where the key serves as both className and candidate.
     * Useful for enum-based samplers where the value is its own identifier.
     * @return this sampler for method chaining
     */
    public WeightedSampler addFromMap(Map<String, Integer> weightMap) {
        if (weightMap == null) {
            return this;
        }
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            addSingleCandidate(entry.getKey(), entry.getValue(), entry.getKey());
        }
        return this;
    }

    /**
     * Creates a WeightedSampler from a map where keys are both className and candidate.
     * @return new WeightedSampler instance
     */
    public static WeightedSampler fromMap(Map<String, Integer> weightMap) {
        return new WeightedSampler().addFromMap(weightMap);
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
