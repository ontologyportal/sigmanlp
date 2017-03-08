package nlp.pipeline;

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

import com.articulate.sigma.WSD;
import com.articulate.sigma.KBmanager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import nlp.WSDAnnotator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Pipeline {

    StanfordCoreNLP pipeline;

    /** ***************************************************************
     */
    public Pipeline() {

        this(false);
    }

    /** ***************************************************************
     */
    public Pipeline(boolean useDefaultPCFGModel) {

        Properties props = new Properties();
        // props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, depparse, dcoref, entitymentions");
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, depparse");
        //props.setProperty("parse.kbest", "2");
        props.setProperty("depparse.language","English");
        props.put("depparse.model", "edu/stanford/nlp/models/parser/nndep/english_SD.gz");

        if (!useDefaultPCFGModel && !Strings.isNullOrEmpty(KBmanager.getMgr().getPref("englishPCFG"))) {
            props.put("parse.model", KBmanager.getMgr().getPref("englishPCFG"));
            props.put("parser.model", KBmanager.getMgr().getPref("englishPCFG"));
            props.put("parse.flags", "");
        }
        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
        pipeline = new StanfordCoreNLP(props);
    }

    /** ***************************************************************
     */
    public Pipeline(boolean useDefaultPCFGModel, String propString) {

        Properties props = new Properties();
        // props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, depparse, dcoref, entitymentions");
        props.put("annotators", propString);
        //props.setProperty("parse.kbest", "2");
        //props.setProperty("depparse.language","English");
        props.put("customAnnotatorClass.wsd","nlp.WSDAnnotator");
        //props.put("depparse.model", "edu/stanford/nlp/models/parser/nndep/english_SD.gz");

        //if (!useDefaultPCFGModel && !Strings.isNullOrEmpty(KBmanager.getMgr().getPref("englishPCFG"))) {
        //    props.put("parse.model", KBmanager.getMgr().getPref("englishPCFG"));
        //    props.put("parser.model", KBmanager.getMgr().getPref("englishPCFG"));
        //    props.put("parse.flags", "");
        //}
        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
        pipeline = new StanfordCoreNLP(props);
    }

    /** ***************************************************************
     */
    public Annotation annotate(String text) {
        
        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);
        // run all Annotators on this text
        pipeline.annotate(document);
        return document;
    }

    /** ***************************************************************
     */
    public static Annotation toAnnotation(String input) {

        Pipeline pipeline = new Pipeline(true);
        return pipeline.annotate(input);
    }

    /** ***************************************************************
     */
    public static Annotation toAnnotation(List<String> inputs) {

        return toAnnotation(String.join(" ", inputs));
    }

    /** ***************************************************************
     */
    public List<String> toDependencies(String input) {

        Annotation wholeDocument = annotate(input);
        CoreMap lastSentence = SentenceUtil.getLastSentence(wholeDocument);
        List<String> dependencies = SentenceUtil.toDependenciesList(ImmutableList.of(lastSentence));
        return dependencies;
    }

    /** ***************************************************************
     */
    public static void showResults(Annotation anno) {

        if (!anno.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Unable to find sentences in " + anno);

        List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
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
                String sense = token.get(WSDAnnotator.WSDAnnotation.class);
                System.out.print(lemma + "/" + sense + " ");
            }
            System.out.println();
        }
    }

    /** ***************************************************************
     */
    public static void processFile(String filename) {

        KBmanager.getMgr().initializeOnce();
        String propString =  "tokenize, ssplit, pos, lemma, wsd";
        Pipeline p = new Pipeline(true,propString);
        String contents = "";
        try {
            contents = new String(Files.readAllBytes(Paths.get(filename)));
        }
        catch (IOException ioe) {
            System.out.println("error in Pipeline.processFile()");
            ioe.printStackTrace();
        }
        Annotation wholeDocument = p.annotate(contents);
        showResults(wholeDocument);
        //List<String> sents = SentenceUtil.restoreSentences(wholeDocument);
        //ArrayList<String> SUMOs = new ArrayList<>();
        //for (String sent : sents) {
            //System.out.println(sent + WSD.collectSUMOFromWords(sent));
            //System.out.println(WSD.collectWordSenses(sent));
            //System.out.println();
        //}
    }

    /** ***************************************************************
     */
    public static void printHelp() {

        System.out.println("-h           print this help screen");
        System.out.println("-f <file>    process a file");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        if (args[0].equals("-h"))
            printHelp();
        else if (args[0].equals("-f") && args.length == 2) {
            processFile(args[1]);
        }
        else {
            Annotation a = Pipeline.toAnnotation("Amelia also wrote books, most of them were about her flights.");
            SentenceUtil.printSentences(a);
        }
    }
}