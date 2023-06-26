/*
 * Copyright 2014-2015 IPsoft
 *
 * Author: Andrei Holub andrei.holub@ipsoft.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program ; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA  02111-1307 USA
 */
package com.articulate.nlp.semRewrite.substitutor;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.semRewrite.Interpreter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

// Note! Be careful to use the right class - there's dcoref too which won't work if you want coref
import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StanfordCorefSubstitutor extends SimpleSubstitutorStorage {

    public static final Set<String> ignorablePronouns = ImmutableSet.of("himself", "herself");
    public static boolean debug = true;

    /** **************************************************************
     */
    public StanfordCorefSubstitutor(Annotation document) {

        if (debug) System.out.println("StanfordCorefSubstitutor(): ");
        initialize(document);
    }

    /** **************************************************************
     * Create the coreference chains and store them by calling SimpleSubstitutorStorage.addGroups()
     */
    private void initialize(Annotation document) {

        if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): ");
        List<CoreLabel> labels = document.get(TokensAnnotation.class);
        Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): corefChains: " + corefChains);
        Map<CoreLabelSequence, CoreLabelSequence> collectedGroups = Maps.newHashMap();

        for (CoreLabel label : labels) {
            if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): label: " + label);
            List<CorefChain.CorefMention> mentions = getMentions(label, corefChains);
            if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): mentions: " + mentions);
            if (mentions.size() > 1) {
                if (!ignorablePronouns.contains(label.originalText())) {
                    int index = label.index();
                    int sentenceIdx = 1 + label.sentIndex();

                    CorefChain.CorefMention firstMention = findRootMention(mentions);
                    if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): root mentions: " + firstMention);
                    if (sentenceIdx != firstMention.sentNum || index < firstMention.startIndex || index >= firstMention.endIndex) {
                        String masterTag = label.tag();
                        if (isSubstitutablePronoun(label))
                            masterTag = "";
                        List<CoreLabel> singleSentence = getSentenceTokens(document, firstMention.sentNum - 1);
                        CoreLabelSequence key = extractTextWithSameTag(singleSentence, firstMention, masterTag);
                        if (!key.isEmpty())
                            collectedGroups.put(new CoreLabelSequence(label), key);
                    }
                }
            }
        }
        if (debug) System.out.println("StanfordCorefSubstitutor.initialize(): collected groups: " + collectedGroups);
        Interpreter.substGroups = collectedGroups; // a hack to save the substitutions
        addGroups(collectedGroups);
    }

    /** ***************************************************************
     * Search for root coreference.
     * Current implementation assumes that first mention is the root.
     */
    private CorefChain.CorefMention findRootMention(List<CorefChain.CorefMention> mentions) {

        CorefChain.CorefMention rootMention = mentions.get(0);
        if (debug) System.out.println("StanfordCorefSubstitutor.findRootMention(): " + rootMention);
        return rootMention;
    }

    /** *************************************************************
     */
    private List<CorefChain.CorefMention> getMentions(final CoreLabel label, Map<Integer, CorefChain> corefs) {

        //if (debug) System.out.println("StanfordCorefSubstitutor.getMentions(): mentions for " +
        //        label +  " in " + corefs);
        List<CorefChain.CorefMention> mentions = new ArrayList<>();
        //if (debug) RelExtract.printCoreLabel(label);
        Integer corefClusterId = label.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
        //if (debug) System.out.println("StanfordCorefSubstitutor.getMentions(): cluster: " + corefClusterId);
        while (mentions.size() <= 1 && corefClusterId != null && corefClusterId.compareTo(0) > 0) {
            if (corefs.containsKey(corefClusterId)) {
                List<CorefChain.CorefMention> candidateMentions = corefs.get(corefClusterId).getMentionsInTextualOrder();
                boolean areMentionsContainLabel = candidateMentions.stream().anyMatch(mention ->
                                mention.sentNum == label.sentIndex() + 1
                                        && mention.startIndex == label.index()
                );
                if (areMentionsContainLabel)
                    mentions = candidateMentions;
            }
            corefClusterId = corefClusterId - 1;
        }

        return mentions;
    }

    /** **************************************************************
     */
    private boolean isSubstitutablePronoun(CoreLabel label) {

        String tag = label.tag();
        return "PRP".equals(tag) || "PRP$".equals(tag);
    }

    /** *************************************************************
     */
    private List<CoreLabel> getSentenceTokens(Annotation document, int sentenceNumber) {

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        CoreMap sentence = sentences.get(sentenceNumber);
        List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
        return tokens;
    }

    /** *************************************************************
     * Takes only monotonic tagged part of sequence.
     * E.g. it only takes "Jon Bov Joni" from sequence "Jon Bov Joni the leader"
     */
    private CoreLabelSequence extractTextWithSameTag(List<CoreLabel> tokens, CorefChain.CorefMention mention, /* @Nullable */String masterTag) {

        List<CoreLabel> out = Lists.newArrayListWithCapacity(mention.endIndex - mention.startIndex);
        String tag = Strings.nullToEmpty(masterTag);
        for (int i = mention.startIndex; i < mention.endIndex; i++) {
            if (Strings.isNullOrEmpty(tag) || "DT".equals(tag)) {
                tag = tokens.get(i - 1).tag();
            }
            CoreLabel coreLabel = tokens.get(i - 1);
            if (tag.equals(coreLabel.tag())) {
                out.add(coreLabel);
            }
            else {
                break;
            }
        }
        return new CoreLabelSequence(out);
    }
}
