package com.articulate.nlp.corpora.wikipedia;

import com.articulate.nlp.pipeline.Pipeline;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

Extract sentences from Wikimedia formatted text roughly to straight text.
Filter by the complexity of the sentence.
 */
public class SimpleSentenceExtractor {

    public static ArrayList<String> knownKeys = new ArrayList<>(
            Arrays.asList("Citation needed","Cite book","Cite web"));

    /****************************************************************
     */
    public static char oppositeChar(char ch) {

        switch (ch) {
            case '[':
                return ']';
            case '{':
                return '}';
            case '<':
                return '>';
            case '\'':
                return '\'';
            case '=':
                return '=';
        }
        return ch;
    }

    /****************************************************************
     */
    public static void processKeyword(StringBuffer sb, StringBuffer nested,
                                      char ch, String keyword) {

    }

    /****************************************************************
     */
    public static void processNest(StringBuffer sb, StringBuffer nested, char ch) {

        //System.out.println("processNext(): nested: " + nested);
        String nestedStr = nested.toString();
        String keyword = null;
        if (nestedStr.contains("|")) {
            keyword = nestedStr.substring(0,nestedStr.indexOf("|"));
            //System.out.println("processNext(): keyword: " + keyword);
            if (knownKeys.contains(keyword))
                processKeyword(sb,nested,ch,keyword);
            else {
                sb.append(" " + nestedStr.substring(
                                   nestedStr.lastIndexOf("|")+1, nestedStr.length()) + " ");
            }
        }
        else {
            String surround = "";
            if (ch == '\'')
                surround = "'";
            sb.append(" " + surround + nestedStr + surround + " ");
        }
        nested.delete(0, nested.length());
    }

    /****************************************************************
     */
    public static String readFile(String filename) {

        char lastChar = 0;
        int nestLevel = 0;
        char nestChar = ' '; // [,<,{,'
        boolean inQuotes = false;
        StringBuffer sb = new StringBuffer();
        StringBuffer nestedSb = new StringBuffer();
        try {
            InputStream in = new FileInputStream(filename);
            Reader reader = new InputStreamReader(in);
            Reader buffer = new BufferedReader(reader);
            int r;
            while ((r = buffer.read()) != -1) {
                //System.out.println("main(): nested: " + nestedSb);
                //System.out.println("main(): level: " + nestLevel);
                //System.out.println("main(): nest char: " + nestChar);
                //System.out.println("main(): sb: " + sb);
                char ch = (char) r;
                if (ch == '[' || ch == '<' || ch == '{' || ch == '=') {
                    //System.out.println("main(): here 1: ");
                    nestChar = ch;
                    if (lastChar == '\'')
                        nestLevel = 1;
                    else if (nestLevel == 0 || ch == lastChar)
                        nestLevel++;
                }
                else if (ch == '\'') {
                    if (lastChar == ']') {
                        continue;
                    }
                    if ((nestLevel == 0 || ch == lastChar) && !inQuotes) {
                        nestLevel++;
                        nestChar = ch;
                    }
                    else {
                        if (ch == oppositeChar(nestChar))
                            nestLevel--;
                        if (nestLevel == 0)
                            processNest(sb,nestedSb,ch);
                    }
                }
                else if (ch == ']' || ch == '>' || ch == '}' || ch == '=') {
                    //System.out.println("main(): here 2: ");
                    if (ch == oppositeChar(nestChar))
                        nestLevel--;
                    if (nestLevel == 0)
                        processNest(sb,nestedSb,ch);
                    lastChar = ' ';
                }
                else if (nestLevel > 0) {
                    if (lastChar == '\'') // reading other than a quote now, but last char was quote
                        inQuotes = true;
                    nestedSb.append(ch);
                }
                else
                    sb.append(ch);
                lastChar = ch;
            }
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        String sbStr = sb.toString();
        sbStr = sbStr.replaceAll("''",""); // total hack cleanup
        sbStr = sbStr.replaceAll("  "," "); // total hack cleanup
        return sbStr;
    }

    /****************************************************************
     * http://www.evanjones.ca/software/wikipedia2text.html
     * http://www.evanjones.ca/software/wikipedia2text-extracted.txt.bz2 plain text of 10M words
     */
    public static void readMediaWiki(int limit) {

        System.out.println("use number of tokens as command line parameter");
        String file = "/home/apease/corpora/wikipedia/out/3a/00/Abbotts_Ann.txt";
        //String file = "/home/apease/corpora/wikipedia/Abbotts_Ann_mini2.txt";
        String textVersion = readFile(file);
        System.out.println(textVersion);
        Pipeline p = new Pipeline(true,"tokenize, ssplit, pos");
        Annotation wholeDocument = new Annotation(textVersion);
        p.pipeline.annotate(wholeDocument);
        List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            if (tokens.size() > 2 && tokens.size() < limit)
                System.out.println(sentence + "\n");
        }
    }

    /****************************************************************
     */
    private static boolean initialCapital(List<CoreLabel> tokens) {

        return Character.isUpperCase(tokens.get(0).originalText().charAt(0));
    }

    /****************************************************************
     * exclude questions
     */
    private static boolean endPunctuation(List<CoreLabel> tokens) {

        String last = tokens.get(tokens.size()-1).originalText();
        return (last.equals(".") || last.equals("!"));
    }

    /****************************************************************
     */
    public static void extractOneLine(String line, Pipeline p, int limit) {

        Annotation wholeDocument = new Annotation(line);
        p.pipeline.annotate(wholeDocument);
        List<CoreMap> sentences = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            if (tokens.size() > 2 && tokens.size() < limit &&
                    initialCapital(tokens) && endPunctuation(tokens))
                System.out.println(sentence + "\n");
        }
    }

    /****************************************************************
     * http://www.evanjones.ca/software/wikipedia2text.html
     * http://www.evanjones.ca/software/wikipedia2text-extracted.txt.bz2 plain text of 10M words
     */
    public static void readWikiText(int limit) {

        Pipeline p = new Pipeline(true,"tokenize, ssplit, pos");
        try {
            String corporaDir = System.getenv("CORPORA");
            InputStream in = new FileInputStream(corporaDir + "/wikipedia/wikipedia2text-extracted.txt");
            Reader reader = new InputStreamReader(in);
            LineNumberReader lnr = new LineNumberReader(reader);
            String line = null;
            while ((line = lnr.readLine()) != null) {
                extractOneLine(line,p,limit);
            }
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    /****************************************************************
     */
    public static void main(String[] args) {

        int limit = 15;
        if (args != null && args.length > 0) {
            if (Integer.parseInt(args[0]) > 0)
                limit = Integer.parseInt(args[0]);
        }
        readWikiText(limit);
    }
}
