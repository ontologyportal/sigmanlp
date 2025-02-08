package com.articulate.nlp.pipeline;

/*
Copyright 2014-2015 IPsoft

Author: Andrei Holub andrei.holub@ipsoft.com

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
*/

import com.articulate.nlp.constants.LangLib;
import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;
import java.util.regex.Pattern;

import static edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;

//import edu.stanford.com.articulate.nlp.trees.TreeCoreAnnotations.KBestTreesAnnotation;

public class SentenceUtil {

    /** ***************************************************************
     * Print all the sentences in this document back into strings
     */
    public static List<String> restoreSentences(Annotation document) {

        List<String> result = new ArrayList<String>();
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            result.add(sentence.toString());
        }
        return result;
    }

    /** ***************************************************************
     * Print all the sentences in this document
     * CoreMap is essentially a Map that uses class objects as keys and
     * has values with custom types
     */
    public static void printSentences(Annotation document) {

        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        int count;
        String word, pos, ne;
        List<CoreMap> entity;
        for (CoreMap sentence : sentences) {
            // traversing the words in the current sentence
            // a CoreLabel is a CoreMap with additional token-specific methods
            count = 1;
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                // this is the text of the token
                word = token.get(TextAnnotation.class);
                // this is the POS tag of the token
                pos = token.get(PartOfSpeechAnnotation.class);
                // this is the NER label of the token
                ne = token.get(NamedEntityTagAnnotation.class);
                entity = token.get(MentionsAnnotation.class);
                System.out.println(word + "-" + count + "/" + pos + "/" + ne + "/" + entity);
                count++;
            }

            // this is the parse tree of the current sentence
            // Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);

            // this is the Stanford dependency graph of the current sentence
            SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
            System.out.println(dependencies.toList());
            //System.out.println(dependencies.toPOSList());
            //System.out.println(getFullNamedEntities(sentence));

            /*
            List<ScoredObject<Tree>> scoredTrees = sentence.get(KBestTreesAnnotation.class);
            System.out.println("\nTree Scores:");
            for (ScoredObject<Tree> scoredTree : scoredTrees) {
                //SemanticGraph graph = SemanticGraphFactory.generateUncollapsedDependencies(scoredTree.object());
                System.out.println(scoredTree.score());
            }
            */
        }
    }

    /** ***************************************************************
     * @return a List of Strings which are concatenated tokens forming
     * a single named entity, with the suffix of the number of the
     * head.  For example "I went to New York." would return
     * "NewYork-4".
     */
    public static ArrayList<String> getFullNamedEntities (CoreMap sentence) {

        ArrayList<String> nes = new ArrayList<String>();
        StringBuilder ne = new StringBuilder();
        String neType = "";
        int count = 1;
        int wordCount = 0; // number of words packed into a given ne
        for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
            // this is the text of the token
            String word = token.get(TextAnnotation.class);
            System.out.println(word);
            // this is the NER label of the token
            String type = token.get(NamedEntityTagAnnotation.class);
            if (neType == "") {
                neType = type;
                if (!type.equals("O"))
                    ne.append(word);
                wordCount = 1;
            }
            else if (!neType.equals(type)) {
                if (!neType.equals("O"))
                    nes.add(ne.toString() + "-" + (count-wordCount));
                ne = new StringBuilder();
                if (!type.equals("O")) {
                    ne.append(word);
                    wordCount = 1;
                }
                else
                    wordCount = 0;
                neType = type;
            }
            else {
                if (!type.equals("O"))
                    ne.append(word);
                wordCount++;
            }
            System.out.println(word + "-" + count + "/" + type + "/" + neType + "/" + ne + "/" + wordCount);
            count++;
        }
        return nes;
    }

    /** ***************************************************************
     *  Print the coreference link graph
     *  Each chain stores a set of mentions that link to each other,
     *  along with a method for getting the most representative mention
     *  Both sentence and token offsets start at 1!
     */
    public static void printCorefChain(Annotation document) {

        System.out.println("SentenceUtil.printCorefChain()");
        Map<Integer, CorefChain> graph = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (graph == null)
            return;
        for (CorefChain cc : graph.values()) {
            List<CorefChain.CorefMention> mentions = cc.getMentionsInTextualOrder();
            if (mentions.size() > 1) {
                for (CorefChain.CorefMention ment : mentions) {
                    System.out.println(ment.sentNum + " : " + ment.headIndex + " : " + ment.mentionSpan);
                }
                System.out.println();
            }
        }
        System.out.println("---");
        System.out.println("coref chains");
        for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            System.out.println("\t" + cc);
        }
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("---");
            System.out.println("mentions");
            for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
                System.out.println("\t" + m);
            }
        }
    }

    /** *************************************************************
     */
    private static boolean isVerb(String pennPOS) {

        return pennPOS.startsWith("V");
    }

    /** *************************************************************
     */
    private static boolean isNoun(String pennPOS) {

        return pennPOS.startsWith("N");
    }

    /** *************************************************************
     */
    public static ArrayList<String> toArrayStrings(List<CoreLabel> tokens) {

        ArrayList<String> result = new ArrayList<>();
        for (CoreLabel cl : tokens)
            result.add(cl.originalText());
        return result;
    }

    /** *************************************************************
     * returns a list of strings that add tense, number, etc. information about words in input
     * ex.  tense(PAST, Verb)
     *      number(SINGULAR, Noun)
     *          //TODO: I'm a monster! Refactor me
     */
    public static List<Literal> findPOSInformation(List<CoreLabel> tokens, List<Literal> dependenciesList) {

        List<Literal> posInformation = Lists.newArrayList();
        for (CoreLabel label : tokens) {
            //Pattern auxPattern = Pattern.compile("aux\\(.*, " + label.toString() + "\\)");
            boolean isAux = false;
            for (Literal l : dependenciesList) {
                //if (auxPattern.matcher(dep).find()) {
                if (l.pred.equals("aux") && l.arg2.equals(label.toString())) {
                    isAux = true;
                    break;
                }
            }
            if (!isAux) {
                boolean progressive = false;
                boolean perfect = false;
                String pos = label.get(PartOfSpeechAnnotation.class);
                if (LangLib.POS_VBD.equals(pos)) {
                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PAST, label));
                }
                else if (LangLib.POS_VBP.equals(pos) || LangLib.POS_VBZ.equals(pos)) {
                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PRESENT, label));
                }
                else if (LangLib.POS_VBG.equals(pos) || LangLib.POS_VB.equals(pos) || LangLib.POS_VBN.equals(pos)) {
                    Pattern reverseAuxPattern = Pattern.compile("aux\\(" + label.toString() + ", .*-(\\d+)\\)");
                    for (Literal l : dependenciesList) {
                        //Matcher auxMatcher = reverseAuxPattern.matcher(dep);
                        if (l.pred.equals("aux") && l.arg1.equals(label.toString())) {
                            //int i = Integer.parseInt(auxMatcher.group(1));
                            int i = l.clArg2.index();
                            CoreLabel t = tokens.get(i-1);
                            if (t.get(LemmaAnnotation.class).equals("be")) {
                                if (t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBP) || t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBZ)) {
                                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PRESENT, label));
                                }
                                else if (t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBD)) {
                                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PAST, label));
                                }
                                progressive = true;
                            }
                            else if (t.get(LemmaAnnotation.class).equals("will")) {
                                posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_FUTURE, label));
                            }
                            else if (t.get(LemmaAnnotation.class).equals("have")) {
                                if (t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBP) || t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBZ)) {
                                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PRESENT, label));
                                }
                                else if (t.get(PartOfSpeechAnnotation.class).equals(LangLib.POS_VBD)) {
                                    posInformation.add(makeBinaryRelationship("tense", LangLib.TENSE_PAST, label));
                                }
                                perfect = true;
                            }
                        }
                    }
                }
                else if (LangLib.POS_NN.equals(pos) || LangLib.POS_NNP.equals(pos)) {
                    posInformation.add(makeBinaryRelationship("number", LangLib.NUMBER_SINGULAR, label));
                }
                else if (LangLib.POS_NNS.equals(pos) || LangLib.POS_NNPS.equals(pos)) {
                    posInformation.add(makeBinaryRelationship("number", LangLib.NUMBER_PLURAL, label));
                }

                // if false then Interpreter.lemmatizeResults() is called instead
                if (Interpreter.lemmaLiteral && (isVerb(pos) || isNoun(pos)))
                    posInformation.add(makeBinaryRelationship("lemma", label.lemma(), label));
                if (progressive && perfect) {
                    posInformation.add(makeBinaryRelationship("aspect", LangLib.ASPECT_PROGRESSIVE_PERFECT, label));
                }
                else if (progressive) {
                    posInformation.add(makeBinaryRelationship("aspect", LangLib.ASPECT_PROGRESSIVE, label));
                }
                else if (perfect) {
                    posInformation.add(makeBinaryRelationship("aspect", LangLib.ASPECT_PERFECT, label));
                }
            }
        }
        return posInformation;
    }

    //TODO: see if this exists somewhere else or move to utility class
    /** *************************************************************
     */
    public static Literal makeBinaryRelationship(String relationship, String argument1, CoreLabel cl) {

        Literal l = new Literal();
        l.pred = relationship;
        l.setarg1(argument1);
        l.setarg2(cl);
        return l;
    }

    /** *************************************************************
     */
    public static CoreMap getLastSentence(Annotation document) {

        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        if (!sentences.isEmpty())
            return Iterables.getLast(sentences);
        else
            return null;
    }

    /** ***************************************************************
     */
    public static List<SemanticGraphEdge> toEdgesList(CoreMap sentence) {

        List<SemanticGraphEdge> results = new ArrayList<>();
        SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
        //System.out.println("SentenceUtil.toDependenciesList(): deps: " + dependencies.toList());
        if (dependencies == null) {
            System.out.println("SentenceUtil.toDependenciesList(): no dependencies for " + sentence);
            return null;
        }
        Iterable<SemanticGraphEdge>	e = dependencies.edgeIterable();
        Iterator<SemanticGraphEdge> edges = e.iterator();
        //System.out.println("SentenceUtil.toDependenciesList(): results: " + results);
        while (edges.hasNext()) {
            results.add(edges.next());
        }
        return results;
    }

    /** ***************************************************************
     */
    public static List<Literal> toDepList(CoreMap sentence) {

        List<Literal> result = new ArrayList<>();
        List<SemanticGraphEdge> edges = toEdgesList(sentence);
        Literal l;
        for (SemanticGraphEdge sge : edges) {
            l = new Literal();
            l.pred = sge.getRelation().toString();
            l.clArg1 = sge.getGovernor().backingLabel();
            l.clArg2 = sge.getDependent().backingLabel();
            l.arg1 = l.clArg1.toString();
            l.arg2 = l.clArg2.toString();
            result.add(l);
        }
        return result;
    }

    /** ***************************************************************
     */
    public static List<Literal> toDependenciesList(Annotation document) {

        return toDependenciesList(getLastSentence(document));
    }

    /** ***************************************************************
     */
    public static List<Literal> toDependenciesList(CoreMap sentence) {

        List<Literal> results = new ArrayList<>();
        Collection<TypedDependency> typedDeps;
        SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
        if (dependencies == null) {
            System.out.println("Error in SentenceUtil.toDependenciesList(): no dependencies for " + sentence);
            return null;
        }
        else
            typedDeps = dependencies.typedDependencies();

        Literal l;
        for (TypedDependency dep : typedDeps) {
            l = new Literal();
            l.pred = dep.reln().toString();
            l.clArg1 = dep.gov().backingLabel();
            l.clArg2 = dep.dep().backingLabel();
            l.arg1 = l.clArg1.toString();
            l.arg2 = l.clArg2.toString();
            results.add(l);
        }
        return results;
    }

    /** ***************************************************************
     * Also remove tokens with trailing apostrophe - see dependency manual sec 4.6
     */
    public static List<Literal> toDependenciesList(List<CoreMap> sentences) {

        //System.out.println("SentenceUtil.toDependenciesList(): " + sentences);
        List<Literal> results = new ArrayList<>();
        List<Literal> al;
        for (CoreMap sentence : sentences) {
            al = toDependenciesList(sentence);
            if (al != null)
                results.addAll(al);
        }
        return results;
    }

    /** ***************************************************************
     * Also remove tokens with trailing apostrophe - see dependency manual sec 4.6
     */
    public static CNF toCNFDependenciesList(List<CoreMap> sentences) {

        CNF cnf = new CNF();
        List<Literal> lits = toDependenciesList(sentences);
        for (Literal l : lits) {
            cnf.append(l);
        }
        return cnf;
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        Annotation a = Pipeline.toAnnotation("I went to New York and had cookies and cream in the Empire State Building in January with Mary.");
        printCorefChain(a);
        printSentences(a);
    }
}
