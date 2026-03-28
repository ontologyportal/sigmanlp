package com.articulate.nlp.morphodb.evaluation;

import org.dkpro.statistics.agreement.coding.CodingAnnotationStudy;
import org.dkpro.statistics.agreement.coding.CohenKappaAgreement;
import org.dkpro.statistics.agreement.coding.FleissKappaAgreement;
import org.dkpro.statistics.agreement.coding.KrippendorffAlphaAgreement;
import org.dkpro.statistics.agreement.distance.NominalDistanceFunction;

import java.util.*;

/**
 * Utility class providing inter-rater agreement metrics:
 * Cohen's kappa (pairwise), Fleiss' kappa (multi-rater), and Krippendorff's alpha.
 * Delegates computation to dkpro-statistics.
 *
 * References:
 * Cohen, J. (1960). Educational and Psychological Measurement, 20(1), 37-46.
 * Fleiss, J. L. (1971). Psychological Bulletin, 76(5), 378-382.
 * Krippendorff K. Estimating the reliability, systematic error, and random error of interval data. Educ Psychol Meas. 1970;30:61–70
 */
public class InterRaterStats {

    // ── Shared private helpers ────────────────────────────────────────────────

    /**
     * Build a dkpro CodingAnnotationStudy from a list of annotation sets.
     * Each set maps rater name → assigned category for one item.
     * Absent raters are represented as null.
     */
    private static CodingAnnotationStudy buildStudy(List<Map<String, String>> sets) {
        LinkedHashSet<String> raterNames = new LinkedHashSet<>();
        for (Map<String, String> set : sets) {
            raterNames.addAll(set.keySet());
        }
        List<String> raterList = new ArrayList<>(raterNames);

        CodingAnnotationStudy study = new CodingAnnotationStudy(raterList.size());
        for (Map<String, String> set : sets) {
            Object[] annotations = new Object[raterList.size()];
            for (int i = 0; i < raterList.size(); i++) {
                annotations[i] = set.get(raterList.get(i)); // null if rater absent
            }
            study.addItemAsArray(annotations);
        }
        return study;
    }

    /** Landis & Koch (1977) interpretation thresholds, shared by Cohen's and Fleiss' kappa. */
    private static String interpretLandisKoch(double kappa) {
        if (Double.isNaN(kappa)) return "Not calculated";
        if (kappa >= 0.81) return "Almost perfect agreement";
        if (kappa >= 0.61) return "Substantial agreement";
        if (kappa >= 0.41) return "Moderate agreement";
        if (kappa >= 0.21) return "Fair agreement";
        if (kappa >= 0.00) return "Slight agreement";
        return "Less than chance agreement";
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /** Result of a Cohen's kappa computation between two raters. */
    public static class CohensKappaResult {
        public final double kappa;
        public final double observedAgreement;
        public final double expectedAgreement;
        public final int itemCount;
        public final Set<String> labels;
        public final Map<String, Map<String, Integer>> confusionMatrix;

        CohensKappaResult(double kappa, double observedAgreement, double expectedAgreement,
                          int itemCount, Set<String> labels,
                          Map<String, Map<String, Integer>> confusionMatrix) {
            this.kappa = kappa;
            this.observedAgreement = observedAgreement;
            this.expectedAgreement = expectedAgreement;
            this.itemCount = itemCount;
            this.labels = labels;
            this.confusionMatrix = confusionMatrix;
        }

        public String interpretation() { return interpretLandisKoch(kappa); }

        @Override
        public String toString() {
            return String.format(
                    "Cohen's \u03ba = %.4f  (%s)%n"
                  + "  Observed agreement: %.4f%n"
                  + "  Expected agreement: %.4f%n"
                  + "  Items compared:     %d%n"
                  + "  Distinct labels:    %d",
                    kappa, interpretation(),
                    observedAgreement, expectedAgreement,
                    itemCount, labels.size());
        }
    }

    /** Result of a Fleiss' kappa computation across multiple raters. */
    public static class FleissKappaResult {
        public final double kappa;
        public final double observedAgreement;
        public final double expectedAgreement;
        public final int itemCount;

        FleissKappaResult(double kappa, double observedAgreement, double expectedAgreement, int itemCount) {
            this.kappa = kappa;
            this.observedAgreement = observedAgreement;
            this.expectedAgreement = expectedAgreement;
            this.itemCount = itemCount;
        }

    }

    /** Result of a Krippendorff's alpha computation. */
    public static class KrippendorffAlphaResult {
        public final double alpha;
        public final double observedDisagreement;
        public final double expectedDisagreement;
        public final int itemCount;
        public final int totalComparisons;

        KrippendorffAlphaResult(double alpha, double observedDisagreement, double expectedDisagreement,
                                int itemCount, int totalComparisons) {
            this.alpha = alpha;
            this.observedDisagreement = observedDisagreement;
            this.expectedDisagreement = expectedDisagreement;
            this.itemCount = itemCount;
            this.totalComparisons = totalComparisons;
        }

    }

    // ── Compute methods ───────────────────────────────────────────────────────

    /**
     * Compute Cohen's kappa between two raters.
     *
     * @param rater1 map of itemId → label assigned by rater 1
     * @param rater2 map of itemId → label assigned by rater 2
     * @return CohensKappaResult, or null if no items overlap
     */
    public static CohensKappaResult computeCohensKappa(Map<String, String> rater1,
                                                       Map<String, String> rater2) {
        Set<String> commonItems = new LinkedHashSet<>(rater1.keySet());
        commonItems.retainAll(rater2.keySet());
        if (commonItems.isEmpty()) return null;

        int n = commonItems.size();

        Set<String> allLabels = new TreeSet<>();
        for (String item : commonItems) {
            allLabels.add(rater1.get(item));
            allLabels.add(rater2.get(item));
        }

        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
        for (String label : allLabels) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String label2 : allLabels) row.put(label2, 0);
            confusion.put(label, row);
        }

        CodingAnnotationStudy study = new CodingAnnotationStudy(2);
        int agree = 0;
        for (String item : commonItems) {
            String l1 = rater1.get(item);
            String l2 = rater2.get(item);
            confusion.get(l1).merge(l2, 1, Integer::sum);
            if (l1.equals(l2)) agree++;
            study.addItem(l1, l2);
        }

        CohenKappaAgreement cka = new CohenKappaAgreement(study);
        double kappa = cka.calculateAgreement();
        double pe    = cka.calculateExpectedAgreement();
        double po    = (double) agree / n;

        return new CohensKappaResult(kappa, po, pe, n, allLabels, confusion);
    }

    /**
     * Compute Fleiss' kappa for multiple raters.
     *
     * @param annotationSets list of per-item maps (raterName → label)
     * @return FleissKappaResult
     */
    public static FleissKappaResult computeFleissKappa(List<Map<String, String>> annotationSets) {
        if (annotationSets.isEmpty()) throw new IllegalArgumentException("No annotation data provided");

        CodingAnnotationStudy study = buildStudy(annotationSets);
        FleissKappaAgreement fka = new FleissKappaAgreement(study);
        double kappa = fka.calculateAgreement();
        double pe    = fka.calculateExpectedAgreement();
        double po    = kappa * (1.0 - pe) + pe;

        return new FleissKappaResult(kappa, po, pe, annotationSets.size());
    }

    /**
     * Compute Krippendorff's alpha for multiple raters using nominal distance.
     *
     * @param annotationSets list of per-item maps (raterName → label)
     * @return KrippendorffAlphaResult
     */
    public static KrippendorffAlphaResult computeKrippendorffAlpha(List<Map<String, String>> annotationSets) {
        if (annotationSets.isEmpty()) throw new IllegalArgumentException("No annotation data provided");

        CodingAnnotationStudy study = buildStudy(annotationSets);
        KrippendorffAlphaAgreement kaa = new KrippendorffAlphaAgreement(study, new NominalDistanceFunction());
        double alpha = kaa.calculateAgreement();
        double dObs  = kaa.calculateObservedDisagreement();
        double dExp  = kaa.calculateExpectedDisagreement();

        int totalComparisons = 0;
        for (Map<String, String> set : annotationSets) {
            int n = set.size();
            totalComparisons += n * (n - 1) / 2;
        }

        return new KrippendorffAlphaResult(alpha, dObs, dExp, annotationSets.size(), totalComparisons);
    }

}
