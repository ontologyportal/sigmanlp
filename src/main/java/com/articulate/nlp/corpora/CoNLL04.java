package com.articulate.nlp.corpora;

import com.articulate.nlp.RelExtract;
import com.articulate.sigma.StringUtil;

import java.io.File;
import java.util.ArrayList;

import static com.articulate.nlp.RelExtract.sentenceExtract;

/**
 * Created by apease on 2/6/18.
 *
 * Read the CoNLL04 relation corpus.
 * Description as per http://cogcomp.org/page/resource_view/43
 * Available relations are:
 * located in, work for, organization based in, live in, and kill
 *
 * The format of each block is:

 a sentence in table format
 empty line
 relation descriptors (may be empty or more than one line)
 empty line
 In the table of a sentence, each row represents an element (a single word or consecutive words) in the sentence. Meaningful columns include:
 col-1: sentence number
 col-2: Entity class label (B-Unknown, B-Peop, or B-Loc, which means other_ent, person, location)
 col-3: Element order number
 col-5: Part-of-speech tags
 col-6: Words

 A relation descriptor has three fileds.

 1st field : the element number of the first argument.
 2nd field : the element number of the second argument.
 3rd field : the name of the relation (e.g. kill or birthplace).
 */
public class CoNLL04 {

    /***************************************************************
     */
    private class Relation {

        public String toString() {
            return (first + "\t" + second + "\t" + relName + "\n");
        }

        public int first;
        public int second;
        public String relName = "";
    }

    /***************************************************************
     */
    private class Sent {

        ArrayList<Token> tokens = new ArrayList<>();
        public String sentString = "";
        ArrayList<Relation> relations = new ArrayList<>();

        public String toString() {

            StringBuffer sb = new StringBuffer();
            sb.append(sentString + "\n");
            for (Token t : tokens)
                sb.append(t.toString());
            sb.append("\n");
            for (Relation r : relations)
                sb.append(r.toString());
            sb.append("\n");
            return sb.toString();
        }
    }

    /***************************************************************
     */
    private class Token {

        public String toString() {
            return (sentNum + "\t" + NER + "\t" + tokNum + "\t" + POS + "\t" + tokString + "\n");
        }

        public int sentNum;
        public String NER = "";
        public int tokNum;
        public String POS = "";
        public String tokString = "";
    }

    /***************************************************************
     */
    public static ArrayList<Sent> sentences = new ArrayList<>();

    /***************************************************************
     */
    public void parse() {

        ArrayList<String> lines = CorpusReader.readFile(System.getenv("CORPORA") + File.separator + "conll04-little.corp");
        boolean inSent = true; // in the sentence or in the relation list
        Sent sent = new Sent();
        StringBuffer sentAccum = new StringBuffer();
        for (String s : lines) {
            if (StringUtil.emptyString(s)) {
                if (inSent) { // transition out of a sentence: all tokens done
                    sent.sentString = sentAccum.toString();
                    sentAccum = new StringBuffer();
                }
                if (!inSent) { // transition out of relation list: all relations read
                    sentences.add(sent);
                    sent = new Sent();
                }
                inSent = !inSent;
            }
            else {
                String[] tabbed = s.split("\\t");
                if (inSent) {
                    Token t = new Token();
                    t.sentNum = Integer.parseInt(tabbed[0]);
                    t.NER = tabbed[1];
                    t.tokNum = Integer.parseInt(tabbed[2]);
                    t.POS = tabbed[4];
                    String tokStr = tabbed[5];
                    if (tokStr.contains("/"))
                        tokStr = tokStr.replaceAll("/"," ");
                    if (tokStr.equals("COMMA"))
                        tokStr = ",";
                    t.tokString = tokStr;
                    sent.tokens.add(t);

                    if (!StringUtil.emptyString(sentAccum.toString()))
                        sentAccum.append(" ");
                    sentAccum.append(tokStr);
                }
                if (!inSent) {
                    Relation rel = new Relation();
                    rel.first = Integer.parseInt(tabbed[0]);
                    rel.second = Integer.parseInt(tabbed[1]);
                    rel.relName = tabbed[2];
                    sent.relations.add(rel);
                }
            }
        }
    }

    /***************************************************************
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        for (Sent s : sentences) {
            sb.append(s.toString());
        }
        return sb.toString();
    }

    /***************************************************************
     */
    public String relsToString(ArrayList<Relation> rels) {

        StringBuffer sb = new StringBuffer();
        for (Relation r : rels) {
            sb.append(r.toString());
        }
        return sb.toString();
    }

    /***************************************************************
     */
    public void extractAll() {

        for (Sent s : sentences) {
            if (s.relations.size() > 0) {
                System.out.println("\n" + s.sentString);
                System.out.println("CoNLL: relations: " + RelExtract.sentenceExtract(s.sentString));
                System.out.println("CoNLL: expected relations: " + relsToString(s.relations));
            }
        }
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        RelExtract.initOnce();
        CoNLL04 coNLL = new CoNLL04();
        coNLL.parse();
        //System.out.println(coNLL.toString());
        coNLL.extractAll();
    }
}
