package com.articulate.nlp.corpora.wikipedia;

import com.articulate.nlp.corpora.CorpusReader;

import java.io.File;

/** This code is copyright Teknowledge (c) 2003, Articulate Software (c) 2003-2017,
 Infosys (c) 2017-present.

 This software is released under the GNU Public License
 <http://www.gnu.org/copyleft/gpl.html>.

 Please cite the following article in any publication with references:

 Pease A., and Benzmüller C. (2013). Sigma: An Integrated Development Environment
 for Logical Theories. AI Communications 26, pp79-97.  See also
 http://github.com/ontologyportal
 */
public class Wikipedia2Text extends CorpusReader {

    // Key is filename, value is a list of lines of text.  Sentences
    // must not cross a line.

    public static String fileExt = ".xml";
    public static String regExGoodLine = "";
    public static String regExRemove = "";
    public static String regExReplacement = "";
    public static String corpora = System.getenv("CORPORA");
    public static String dataDir = corpora + File.separator + "wikipedia2text-extracted.txt";
    public static boolean oneFile = true;
    public static boolean removeHTML = false;
}
