package com.articulate.nlp.pipeline;

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
*/

import com.articulate.sigma.utils.AVPair;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import com.articulate.nlp.TimeSUMOAnnotator;
import com.articulate.nlp.WNMultiWordAnnotator;
import com.articulate.nlp.WSDAnnotator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SentimentAdaptor {

    private static boolean init = false;
    private static Pipeline p = null;

    /** ***************************************************************
     */
    public static void initOnce() {

        if (init)
            return;
        init = true;
        KBmanager.getMgr().initializeOnce();
        String propString =  "tokenize, ssplit, pos, lemma, parse, depparse, ner, wsd, wnmw, sentiment";
        p = new Pipeline(true,propString);
    }

    /** ***************************************************************
     */
    public static String showResults(Annotation anno) {

        StringBuffer sb = new StringBuffer();
        if (!anno.containsKey(CoreAnnotations.SentencesAnnotation.class))
            throw new RuntimeException("Unable to find sentences in " + anno + "\n");

        sb.append("SentimentAdaptor.showResults(): annotations at sentence level" + "\n");
        List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            System.out.println("sentiment: " + sentiment + "\t" + sentence);
            Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
            sb.append("tree: " + tree.toString());
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            List<CoreMap> timexAnnsAllLocal = sentence.get(TimeAnnotations.TimexAnnotations.class);
            sb.append("local time: " + timexAnnsAllLocal + "\n");
            for (CoreLabel token : tokens) {
                String orig = token.originalText();
                String lemma = token.lemma();
                String pos = token.tag();
                String sense = token.get(WSDAnnotator.WSDAnnotation.class);
                String sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                String multi = token.get(WNMultiWordAnnotator.WNMultiWordAnnotation.class);
                sb.append(orig);
                if (!StringUtil.emptyString(lemma))
                    sb.append("/" + lemma);
                if (!StringUtil.emptyString(pos))
                    sb.append("/" + pos);
                if (!StringUtil.emptyString(sense))
                    sb.append("/" + sense);
                if (!StringUtil.emptyString(sumo))
                    sb.append("/" + sumo);
                if (!StringUtil.emptyString(multi))
                    sb.append("/" + multi);
                sb.append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** ***************************************************************
     */
    public static void processFile(String filename) {

        initOnce();
        String contents = "";
        try {
            contents = new String(Files.readAllBytes(Paths.get(filename)));
        }
        catch (IOException ioe) {
            System.out.println("error in SentimentAdaptor.processFile()");
            ioe.printStackTrace();
        }
        Annotation wholeDocument = p.annotate(contents);
        showResults(wholeDocument);
    }

    /** ***************************************************************
     * Process one input through the pipeline and return the sentiment results.
     */
    public static ArrayList<AVPair> getSentiment(String sent) {

        initOnce();
        ArrayList<AVPair> result = new ArrayList<AVPair>();
        Annotation wholeDocument = new Annotation(sent);
        p.pipeline.annotate(wholeDocument);
        List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel token : tokens) {
                String sumo = token.get(WSDAnnotator.SUMOAnnotation.class);
                if (!StringUtil.emptyString(sumo)) {
                    AVPair avp = new AVPair();
                    avp.attribute = sumo;
                    avp.value = sentiment;
                    result.add(avp);
                }
            }
        }
        System.out.println("SentimentAdaptor.getSentiment(): " + result);
        return result;
    }

    /** ***************************************************************
     * Process one input through the pipeline and display the results.
     */
    public static void processOneSent(String sent) {

        initOnce();
        Annotation wholeDocument = p.annotate(sent);
        showResults(wholeDocument);
    }

    /** ***************************************************************
     * Process one input at a time through the pipeline and display
     * the results
     */
    public static void interactive() {

        initOnce();
        BufferedReader d = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("type 'quit' (without the quotes) on its own line to quit");
        String line = "";
        try {
            while (!line.equals("quit")) {
                System.out.print("> ");
                line = d.readLine();
                if (!line.equals("quit")) {
                    Annotation wholeDocument = new Annotation(line);
                    p.pipeline.annotate(wholeDocument);
                    System.out.println(showResults(wholeDocument));
                }
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("error in SentimentAdaptor.interactive()");
        }
    }

    /** ***************************************************************
     */
    public static void printHelp() {

        System.out.println("-h             print this help screen");
        System.out.println("-f <file>      process a file");
        System.out.println("-p \"<Sent>\"  process one quoted sentence ");
        System.out.println("-s             return sentiment pair list ");
        System.out.println("-i             interactive mode ");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        if (args.length == 0 || args[0].equals("-h"))
            printHelp();
        else if (args[0].equals("-f") && args.length == 2)
            processFile(args[1]);
        else if (args[0].equals("-p") && args.length == 2)
            processOneSent(args[1]);
        else if (args[0].equals("-s") && args.length == 2)
            getSentiment(args[1]);
        else if (args[0].equals("-i"))
            interactive();

        else {
            printHelp();
        }
    }
}