package nlp;

import com.articulate.sigma.*;
import com.articulate.sigma.wordNet.MultiWords;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.DefaultPaths;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import nlp.pipeline.SentenceUtil;

/**
 * This class marks WordNet multi-word strings.
 *
 * @author apease
 */

public class WNMultiWordAnnotator implements Annotator {

    // Each CoreLabel in a multi-word string gets one that provides the entire
    // multi-word in WordNet format.  Individual words will still have their own
    // synsets, so later processing should check tokens for multi-word membership
    public static class WNMultiWordAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    static final Annotator.Requirement WNMW_REQUIREMENT = new Annotator.Requirement("wnmw");

    /****************************************************************
     */
    public WNMultiWordAnnotator(String name, Properties props) {

        KBmanager.getMgr().initializeOnce();
    }

    /** ***************************************************************
     * Find the synset for a multi-word string, if it exists.
     *
     * @param tokens is a List of CoreLabel words.
     * @param startIndex is the first word in the list to look at
     * @param synset is an array of only one element, if a synset is found
     * and empty otherwise
     * @return the index into the next word to be checked, in text,
     * which could be the same as startIndex, if no multi-word was found
     */
    public int findMultiWord(List<CoreLabel> tokens, int startIndex, List<String> synset) {

        System.out.println("INFO in MultiWords.findMultiWord(): text: '" + tokens + "'");
        if (startIndex + 1 < tokens.size())
            return startIndex + findMultiWord(tokens.get(startIndex),
                tokens.subList(startIndex + 1, tokens.size()), synset);
        else
            return startIndex;
    }

    /** ***************************************************************
     *  We need to try both the root form and the original form,
     *  which includes capitalized and lower case versions.
     *  @return the count of words in the multi-word expression
     */
    public int findMultiWord(CoreLabel token, List<CoreLabel> multiWordTail, List<String> synset) {

        String multiWordKey = token.lemma();
        if (!WordNet.wn.getMultiWords().multiWord.containsKey(token.originalText()))
            multiWordKey = token.originalText();
        System.out.println("INFO in MultiWords.findMultiWord(): current word: '" + multiWordKey + "'");
        int wordIndex = 0;
        if (WordNet.wn.getMultiWords().multiWord.containsKey(multiWordKey) && !multiWordTail.isEmpty()) {
            String foundMultiWord = multiWordKey + "_" + multiWordTail.get(wordIndex).originalText();
            //int wordListSize = multiWord.get(word).size();
            System.out.println("INFO in MultiWords.findMultiWord(): current head word: '" + multiWordKey + "'");
            Collection<String> candidates = WordNet.wn.getMultiWords().multiWord.get(multiWordKey);
            while (candidates.size() > 0) {
                ArrayList<String> newCandidates = new ArrayList<String>();
                System.out.println("INFO in MultiWords.findMultiWord(): current multi-word: '" + foundMultiWord + "'");
                System.out.println("INFO in MultiWords.findMultiWord(): candidates: " + candidates);
                for (String candidate : candidates) {
                    System.out.println("INFO in MultiWords.findMultiWord(): candidates.size(): " + candidates.size());
                    if (candidate.equals(foundMultiWord)) {
                        //ArrayList<String> multiResult = new ArrayList<String>();
                        System.out.println("INFO in MultiWords.findMultiWord(): found multi-word: " + foundMultiWord);
                        String sense = WSD.getBestDefaultSense(foundMultiWord);
                        System.out.println("INFO in MultiWords.findMultiWord(): found sense: " + sense);
                        if (!StringUtil.emptyString(sense)) {
                            synset.add(sense);
                            return wordIndex + 2;
                        }
                    }
                    else if (candidate.startsWith(foundMultiWord)) {
                        System.out.println("INFO in MultiWords.findMultiWord(): partial match: '" +
                                candidate + "' with '" + foundMultiWord + "'");
                        newCandidates.add(candidate);
                    }
                }
                if (newCandidates.size() > 0) {
                    System.out.println("INFO in MultiWords.findMultiWord(): new candidates added");
                    if (wordIndex > multiWordTail.size() - 1) {
                        candidates = new ArrayList<String>();  // ran out of words, trigger an exit
                        System.out.println("INFO in MultiWords.findMultiWord(): ran out of words, trigger an exit");
                    }
                    else {
                        candidates = newCandidates;
                        wordIndex++;
                        if (wordIndex < multiWordTail.size())
                            foundMultiWord = foundMultiWord + "_" + multiWordTail.get(wordIndex).originalText();
                        System.out.println("INFO in MultiWords.findMultiWord(): new multi-word: " + foundMultiWord);
                    }
                }
                else {
                    System.out.println("INFO in MultiWords.findMultiWord(): no new candidates");
                    candidates = new ArrayList<String>();
                }
            }
        }
        return 0;
    }

    /****************************************************************
     */
    public void annotate(Annotation annotation) {

        if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Unable to find sentences in " + annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            ArrayList<String> words = new ArrayList<String>();
            for (CoreLabel token : tokens) {
                String lemma = token.lemma();
                String word = token.originalText();
                int i = token.index();
                ArrayList<String> multiWordResult = new ArrayList<String>();
                int wordIndex = findMultiWord(tokens, i, multiWordResult);
                if (wordIndex != i) {
                    String theMultiWord = WordNet.wn.synsetsToWords.get(multiWordResult.get(0)).get(0);
                    for (int index = i; i <= wordIndex; i++) {
                        CoreLabel tok = tokens.get(index);
                        tok.set(WNMultiWordAnnotation.class,theMultiWord);
                    }
                }
            }
        }
    }

    /****************************************************************
     *
     */
    @Override
    public Set<Annotator.Requirement> requires() {

        ArrayList<Annotator.Requirement> al = new ArrayList<>();
        al.add(TOKENIZE_REQUIREMENT);
        al.add(SSPLIT_REQUIREMENT);
        al.add(LEMMA_REQUIREMENT);
        //al.add(NER_REQUIREMENT);
        ArraySet<Annotator.Requirement> result = new ArraySet<>();
        result.addAll(al);
        return result;
    }

    /****************************************************************
     */
    @Override
    public Set<Annotator.Requirement> requirementsSatisfied() {

        return Collections.singleton(WNMW_REQUIREMENT);
    }
}

