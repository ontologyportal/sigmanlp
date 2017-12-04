package com.articulate.nlp.corpora;

import com.articulate.sigma.StringUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by apease on 9/22/17.
 *
 * https://www.ilexir.co.uk/datasets/index.html
 * Yannakoudakis, Helen and Briscoe, Ted and Medlock, Ben,
 * ‘A New Dataset and Method for Automatically Grading ESOL Texts’,
 * Proceedings of the 49th Annual Meeting of the Association for
 * Computational Linguistics: Human Language Technologies.
 *
 */
public class CLCFCE extends CorpusReader {

    // Key is filename, value is a list of lines of text.  Sentences
    // must not cross a line.

    public static String fileExt = ".xml";
    public static String regExGoodLine = "<p>.+</p>";
    public static String regExRemove = "<c>[^<]+</c>";
    public static String regExReplacement = "";
    public static String corpora = System.getenv("CORPORA");
    public static String dataDir = corpora + File.separator + "fce-released-dataset/dataset";
    public static boolean oneFile = false;
    public static boolean removeHTML = true;

}
