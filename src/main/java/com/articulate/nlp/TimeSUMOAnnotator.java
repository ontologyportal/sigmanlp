package com.articulate.nlp;

import com.articulate.sigma.*;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import com.articulate.nlp.corpora.TimeBank;

import java.util.*;

/**
 * This class marks time expressions in SUMO, derived from SUTime.
 *
 * @author apease
 */

public class TimeSUMOAnnotator implements Annotator {

    public static class TimeSUMOAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static boolean debug = false;

    //static final Requirement TSUMO_REQUIREMENT = new Requirement("tsumo");

    /****************************************************************
     */
    public TimeSUMOAnnotator(String name, Properties props) {

        KBmanager.getMgr().initializeOnce();
    }

    /****************************************************************
     */
    public void annotate(Annotation annotation) {

        if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Error in TimeSUMOAnnotator.annotate(): Unable to find sentences in " + annotation);
        if (debug) System.out.println("TimeSUMOAnnotator.annotate()");
        /* List<CoreMap> timexAnnsAll = annotation.get(TimeAnnotations.TimexAnnotations.class);
        System.out.println("TimeSUMOAnnotator.annotate(): doc time annotations: " + timexAnnsAll);
        if (timexAnnsAll != null) {
            for (CoreMap token : timexAnnsAll) {
                Formula f = TimeBank.processSUtoken(token);
                System.out.println("TimeSUMOAnnotator.annotate(): SUtoken: " + token);
                if (!StringUtil.emptyString(f.toString())) {
                    token.set(TimeSUMOAnnotation.class, f.toString());
                    System.out.println("TimeSUMOAnnotator.annotate(): SUMO: " + f.toString());
                }
            }
        } */
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences != null) {
            for (CoreMap sentence : sentences) {
                List<CoreMap> timexAnnsAll = sentence.get(TimeAnnotations.TimexAnnotations.class);
                if (debug) System.out.println("TimeSUMOAnnotator.annotate(): sentence time annotations: " + timexAnnsAll);
                if (timexAnnsAll != null) {
                    for (CoreMap token : timexAnnsAll) {
                        Formula f = TimeBank.processSUtoken(token);
                        if (debug) System.out.println("TimeSUMOAnnotator.annotate(): SUtoken: " + token);
                        if (f != null && !StringUtil.emptyString(f.toString())) {
                            token.set(TimeSUMOAnnotation.class, f.toString());
                            if (debug) System.out.println("TimeSUMOAnnotator.annotate(): SUMO: " + f.toString());
                        }
                    }
                }
            }
        }
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>>  requires() {

        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.SentencesAnnotation.class,
                CoreAnnotations.LemmaAnnotation.class,
                NERAnnotator.NERAnnotation.class)));
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {

        return Collections.singleton(TimeSUMOAnnotator.TimeSUMOAnnotation.class);
    }
}

