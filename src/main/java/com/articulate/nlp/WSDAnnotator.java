package com.articulate.nlp;

import com.articulate.sigma.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;

import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

/*
Copyright 2017 Articulate Software

Author: Adam Pease apease@articulatesoftware.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA

 This class adds WordNet word senses and SUMO terms to tokens. It uses the
 WSD class, SemCor statistics and WordNet-SUMO mappings. Assumes
 that the Annotation has already been split into sentences, then tokenized into Lists of CoreLabels.
 */

public class WSDAnnotator implements Annotator {

    public static class WSDAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static class SUMOAnnotation implements CoreAnnotation<String> {
        public Class<String> getType() {
            return String.class;
        }
    }

    public static boolean debug = false;
    //static final Annotator.Requirement WSD_REQUIREMENT = new Annotator.Requirement("wsd");

    /****************************************************************
     */
    public WSDAnnotator(String name, Properties props) {

        //KBmanager.getMgr().initializeOnce();
    }

    /****************************************************************
     */
    public void annotate(Annotation annotation) {

        if (debug) System.out.println("WSDAnnotator.annotate():");
        if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Error in WSDAnnotator.annotate(): Unable to find sentences in " + annotation);

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
                if (lemma.length() == 1 && Pattern.matches("\\p{Punct}", lemma)) // skip punctuation
                    continue;
                if (token.originalText().matches("-...-")) // skip parentheses and bracket codes like -LRB-
                    continue;
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class); // need to convert to Sigma's integer codes
                char num = WordNetUtilities.posPennToNumber(pos);
                if (token.get(WNMultiWordAnnotator.WNMWSpanAnnotation.class) != null) // skip multiwords
                    continue;
                if (num == '1' || num == '2' || num == '3' || num == '4') {
                    String sense = WSD.findWordSenseInContextWithPos(lemma, words, Integer.parseInt(Character.toString(num)),true);
                    if (!StringUtil.emptyString(sense)) {
                        token.set(WSDAnnotation.class, sense);
                        if (debug) System.out.println("WSDAnnotator.annotate(): adding sense: " + sense);
                        String linkedSUMO = WordNet.wn.getSUMOMapping(sense);
                        if (!StringUtil.emptyString(linkedSUMO)) {
                            String SUMO = WordNetUtilities.getBareSUMOTerm(WordNet.wn.getSUMOMapping(sense));
                            if (!StringUtil.emptyString(SUMO)) {
                                token.set(SUMOAnnotation.class, SUMO);
                                if (debug) System.out.println("WSDAnnotator.annotate(): adding SUMO: " + SUMO);
                            }
                        }
                    }
                }
            }
        }
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>> requires() {

        return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
                CoreAnnotations.TokensAnnotation.class,
                CoreAnnotations.SentencesAnnotation.class,
                CoreAnnotations.LemmaAnnotation.class)));
    }

    /****************************************************************
     */
    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {

        return Collections.singleton(WSDAnnotator.WSDAnnotation.class);
    }
}
