package nlp;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.WSD;
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
 * This class adds WordNet word senses and SUMO terms to tokens. It uses the
 * WSD class, SemCor statistics and WordNet-SUMO mappings. Assumes
 * that the Annotation has already been split into sentences, then tokenized into Lists of CoreLabels.
 *
 * @author apease
 */

public class WSDAnnotator implements Annotator {

    public static class WSDAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    static final Annotator.Requirement WSD_REQUIREMENT = new Annotator.Requirement("wsd");

    /****************************************************************
     */
    public WSDAnnotator(String name, Properties props) {

        KBmanager.getMgr().initializeOnce();
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
                words.add(lemma);
            }
            for (CoreLabel token : tokens) {
                String lemma = token.lemma();
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class); // need to convert to Sigma's integer codes
                String sense = WSD.findWordSenseInContext(lemma, words);
                token.set(WSDAnnotation.class,sense);
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
        return Collections.singleton(WSD_REQUIREMENT);
    }
}
