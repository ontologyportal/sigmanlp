package com.articulate.nlp.corpora;

import com.articulate.nlp.RelExtract;
import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semRewrite.RHS;
import com.articulate.sigma.StringUtil;

import java.io.File;
import java.util.*;

import static com.articulate.nlp.RelExtract.sentenceExtract;

/**
 * Created by apease on 2/6/18.
 *
 * Read the CoNLL04 relation corpus.
 * Description as per http://cogcomp.org/page/resource_view/43
 * Available relations are:
 * located in, work for, organization based in, live in, and kill
 * [RAP - these should correspond to located, employs (also leader), located (with arg1 as Organization), inhabits or birthplace, SUMO needs a relation for kill]
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

 A relation descriptor has three fields.

 1st field : the element number of the first argument.
 2nd field : the element number of the second argument.
 3rd field : the name of the relation (e.g. kill or birthplace).
 */
public class CoNLL04 {

    public static boolean debug = true;

    public static final List<String> relTypes =
            Arrays.asList("Located_In", "OrgBased_In", "Live_In", "Work_For", "Kill");

    public HashMap<Integer,Sent> sentIndex = new HashMap<>();

    /***************************************************************
     * A CoNLL relation between two tokens.  Must be one of
     * Located_In, OrgBased_In, Live_In, Work_For or Kill
     */
    public class Relation implements Comparable {

        public String toString() {
            return (first + "\t" + second + "\t" + relName + "\n");
        }

        public int first;
        public int second;
        public String relName = "";

        /** ***************************************************************
         */
        @Override
        public boolean equals(Object o) {

            if (!(o instanceof Relation))
                return false;
            Relation r = (Relation) o;
            //System.out.println("does " + this + " equal: " + r);
            if (first != r.first) return false;
            if (second != r.second) return false;
            if (!relName.equals(r.relName)) return false;
            //System.out.println("yes");
            return true;
        }

        /** ***************************************************************
         */
        public int compareTo(Object o) {

            if (!(o instanceof Relation))
                return 0;
            Relation r = (Relation) o;
            if (this.equals(r))
                return 0;
            if (first == r.first) {
                if (second == r.second) {
                    if (relName.equals(r.relName))
                        return 0;
                    else
                        return relName.compareTo(r.relName);
                }
                else return (new Integer(second)).compareTo(r.second);
            }
            else return (new Integer(first)).compareTo(r.first);
        }

        /** ***************************************************************
         */
        public int hashCode() {
            return first + second + relName.hashCode();
        }
    }

    /***************************************************************
     * A class to contain each sentence in CoNLL04, its attributes
     * including the set of Tokens and Relations among them.
     */
    public class Sent {

        public ArrayList<Token> tokens = new ArrayList<>();
        public String sentString = "";
        public Set<Relation> relations = new HashSet<>();

        // a mapping of real token numbers to CoNLL collapsed NER token numbers
        public HashMap<Integer,Integer> tokMap = new HashMap<>();

        public String toString() {

            StringBuffer sb = new StringBuffer();
            sb.append(sentString + "\n");
            for (Token t : tokens)
                sb.append(t.toString());
            sb.append("\n");
            for (Relation r : relations)
                sb.append(r.toString());
            sb.append("\n");
            sb.append(tokMap);
            sb.append("\n");
            return sb.toString();
        }
    }

    /***************************************************************
     * A class to contain each word in a sentence and its attributes
     */
    public class Token {

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
     * see https://en.wikipedia.org/wiki/F1_score for explanation
     */
    public class F1Matrix {

        public int falseNegatives = 0;
        public int truePositives = 0;
        public int trueNegatives = 0;
        public int falsePositives = 0;

        public float precision() { return (float)truePositives/((float)truePositives + (float)falsePositives); }
        public float recall() { return (float)truePositives/((float)falseNegatives + (float)truePositives); }
        public float fOne() { return 2 * (precision() * recall()) / (precision() + recall()); }

        public F1Matrix add(F1Matrix two) {
            F1Matrix result = new F1Matrix();
            result.falseNegatives = falseNegatives + two.falseNegatives;
            result.truePositives  = truePositives +  two.truePositives;
            result.trueNegatives  = trueNegatives +  two.trueNegatives;
            result.falsePositives = falsePositives + two.falsePositives;
            return result;
        }

        public String toString() {
            return ("fn: " + falseNegatives + " tp: " + truePositives + " tn: " + trueNegatives + " fp: " + falsePositives);
        }
    }

    /***************************************************************
     * A hack to remove punctuation terms that interfere with
     * reserved characters in dependency parses.
     */
    @Deprecated
    public static String removeProblems(String s) {

        if (s.equals("-LRB-"))
            return "LPAR";
        if (s.equals("-RRB-"))
            return "RPAR";
        if (s.equals("'"))
            return "APOS";
        s = s.replaceAll("-(.*)-","\\1");
        return s;
    }

    /***************************************************************
     * Create a mapping from CoNLL token numbers to likely CoreNLP
     * token numbers.  For example "Sam Smith likes steak" will be
     * Sam_Smith-1 likes-2 steak-3 under CoNLL but Sam-1 Smith-2
     * likes-3 steak-4 under CoreNLP.
     * Note that tokParse() will remove slashes, denoting a
     * multi-word, in the token string
     * so we have to look at the part of speech element.
     * @return is a side effect on sent, setting its tokMap
     */
    public void makeTokenMap(Sent sent) {

        int coreNLPnum = 0;
        for (Token tok : sent.tokens) {
            if (tok.POS.contains("/")) {
                String[] parts = tok.POS.split("/");
                for (int i = coreNLPnum; i < coreNLPnum + parts.length; i++) {
                    sent.tokMap.put(i, tok.tokNum);
                    //System.out.println("makeTokenMap(): putting: " + i + "," + tok.tokNum);
                }
                coreNLPnum = coreNLPnum + parts.length;
            }
            else
                coreNLPnum++;
            sent.tokMap.put(coreNLPnum, tok.tokNum);
        }
    }

    /***************************************************************
     */
    public Token tokparse(String[] tabbed) {

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
        //tokStr = removeProblems(tokStr); AP - shouldn't be needed with CoreLabel based Literals
        t.tokString = tokStr;
        return t;
    }

    /***************************************************************
     * Parse CoNLL04 corpus a line at a time into Sent, Token and
     * Relation objects
     */
    public void parse() {

        String filename = System.getenv("CORPORA") + File.separator + "conll04.corp";
        System.out.println("CoNLL04.parse(): reading corpus from " + filename);
        ArrayList<String> lines = CorpusReader.readFile(filename);
        boolean inSent = true; // in the sentence or in the relation list
        Sent sent = new Sent();
        int sentnum = 0;
        StringBuffer sentAccum = new StringBuffer();
        for (String s : lines) {
            if (StringUtil.emptyString(s)) {
                if (inSent) { // transition out of a sentence: all tokens done
                    sent.sentString = sentAccum.toString();
                    sentAccum = new StringBuffer();
                }
                if (!inSent) { // transition out of relation list: all relations read
                    if (sent.sentString.endsWith(".") || sent.sentString.endsWith("!") ||
                            sent.sentString.endsWith("?")) { // ignore things like titles that aren't a sentence
                        sentences.add(sent);
                        sentIndex.put(sentnum,sent);
                        makeTokenMap(sent);
                    }
                    sent = new Sent();
                }
                inSent = !inSent;
            }
            else {
                String[] tabbed = s.split("\\t");
                if (inSent) {
                    Token t = tokparse(tabbed);
                    sentnum = t.sentNum;
                    sent.tokens.add(t);
                    if (!StringUtil.emptyString(sentAccum.toString()))
                        sentAccum.append(" ");
                    sentAccum.append(t.tokString);
                }
                if (!inSent) {
                    Relation rel = new Relation();
                    rel.first = Integer.parseInt(tabbed[0]);
                    rel.second = Integer.parseInt(tabbed[1]);
                    rel.relName = tabbed[2];
                    if (relTypes.contains(rel.relName)) // must be an allowed relation type
                        sent.relations.add(rel);
                    else
                        System.out.println("Error in CoNLL04.parse(): bad relation type " + rel.relName +
                            " in relation " + rel + " in sentence " + sent);
                }
            }
        }
        System.out.println("CoNLL04.parse(): complete reading corpus of " + sentences.size() + " sentences");
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
    public String relsToString(Set<Relation> rels, Sent s) {

        if (rels == null && rels.size() == 0)
            return "";
        StringBuffer sb = new StringBuffer();
        for (Relation r : rels) {
            Token t1 = s.tokens.get(r.first);
            sb.append(t1.tokString + " ");
            sb.append(r.relName + " ");
            Token t2 = s.tokens.get(r.second);
            sb.append(t2.tokString);
            sb.append(", ");
        }
        if (sb.length() == 0)
            return "";
        return sb.toString().substring(0,sb.length()-2);
    }

    /***************************************************************
     */
    public String convertRelName(String pred) {

        if (pred.equals("located"))
            return "Located_In";
        else if (pred.equals("orgLocated"))
            return "OrgBased_In";
        else if (pred.equals("inhabits"))
            return "Live_In";
        else if (pred.equals("birthplace"))
            return "Live_In";
        else if (pred.equals("employs"))
            return "Work_For";
        else if (pred.equals("killed"))
            return "Kill";
        else {
            System.out.println("Error in CoNLL04.convertRelName(): unknown relation " + pred);
            return pred;
        }
    }

    /***************************************************************
     */
    public Set<Relation> toCoNLLRels(ArrayList<RHS> kifClauses, Sent s) {

        if (debug) System.out.println("toCoNLLRels(): tok map: " + s.tokMap);
        Set<Relation> rels = new HashSet<>();
        for (RHS rhs : kifClauses) {
            Literal lit = rhs.toLiteral();
            if (debug) System.out.println("toCoNLLRels(): literal: " + lit);
            if (debug) System.out.println("toCoNLLRels(): arg1: " + lit.clArg1);
            if (debug) System.out.println("toCoNLLRels(): arg2: " + lit.clArg2);
            Relation rel = new Relation();
            rel.relName = convertRelName(lit.pred);
            int tok1 = lit.clArg1.index();
            if (tok1 == -1)
                tok1 = Literal.tokenNum(lit.arg1);
            if (s.tokMap.keySet().contains(tok1))
                tok1 = s.tokMap.get(tok1);
            rel.first = tok1;
            int tok2 = lit.clArg2.index();
            if (tok2 == -1)
                tok2 = Literal.tokenNum(lit.arg2);
            if (s.tokMap.keySet().contains(tok2))
                tok2 = s.tokMap.get(tok2);
            rel.second = tok2;

            if (rel.relName.equals("Work_For")) { // employs is the inverse so swap args
                int temp = rel.first;
                rel.first = rel.second;
                rel.second = temp;
            }
            rels.add(rel);
            if (debug) System.out.println("toCoNLLRels(): as Relation: " + rel);
        }
        return rels;
    }

    /***************************************************************
     */
    public F1Matrix score(Set<Relation> rels, Set<Relation> conllRels) {

        if (debug) System.out.println("score(): found rels   : " + rels);
        if (debug) System.out.println("score(): expected rels: " + conllRels);
        F1Matrix f1mat = new F1Matrix();
        for (Relation r : rels) {
            if (conllRels.contains(r))
                f1mat.truePositives++;
            else
                f1mat.falsePositives++;
        }
        for (Relation r : conllRels) {
            if (!rels.contains(r))
                f1mat.falseNegatives++;
        }
        return f1mat;
    }

    /***************************************************************
     * Remove all statements that are not relations between named
     * entities, for which we approximate that NER have initial
     * capital letters.
     */
    public static ArrayList<RHS> pruneNonNER(ArrayList<RHS> kifClauses) {

        ArrayList<RHS> result = new ArrayList<>();
        for (RHS rhs : kifClauses) {
            CNF cnf = rhs.cnf;
            if (cnf == null)
                continue;
            Collection<Literal> lits = cnf.toLiterals();
            if (lits == null || lits.size() == 0 || lits.size() > 1)
                continue;
            Literal lit = (Literal) lits.toArray()[0];
            if (Character.isUpperCase(lit.arg1.charAt(0)) &&
                    Character.isUpperCase(lit.arg2.charAt(0)))
                result.add(rhs);
        }
        return result;
    }

    /***************************************************************
     */
    public void extractAll() {

        F1Matrix total = new F1Matrix();
        long startTime = System.currentTimeMillis();
        int totalGroundTruth = 0;
        int totalExtracted = 0;
        for (Sent s : sentences) {
            if (s.relations.size() > 0) { // test only sentences with marked relations
                int sentNum = s.tokens.get(0).sentNum;
                System.out.println("\nextractAll(): " + s.tokens.get(0).sentNum + " : " + s.sentString);
                ArrayList<RHS> kifClauses = null;
                try {
                    kifClauses = RelExtract.sentenceExtract(s.sentString);
                    kifClauses = pruneNonNER(kifClauses);
                    if (kifClauses.size() > 0)
                        totalExtracted++;
                    System.out.println("CoNLL: generated relations: " + kifClauses);
                    Set<Relation> rels = toCoNLLRels(kifClauses,s);
                    F1Matrix mat = score(rels,s.relations);
                    System.out.println("CoNLL: score: " + mat);

                    total = total.add(mat);
                    System.out.println("CoNLL: cumulative score: " + total);
                    System.out.println("CoNLL: generated relations: " + relsToString(rels,s));
                    System.out.println("CoNLL: expected relations: " + relsToString(s.relations,s));
                    if (s.relations.size() > 0)
                        totalGroundTruth++;
                }
                catch (Exception e) { e.printStackTrace(); }
            }
        }
        System.out.println("CoNLL04.extractAll(): expected: " + totalGroundTruth + " found: " + totalExtracted);
        double seconds = ((System.currentTimeMillis() - startTime) / 1000.0);
        System.out.println("time to process: " + seconds + " seconds (not counting init)");
        System.out.println("time to process: " + (seconds / sentences.size()) + " seconds per sentence");
        System.out.println("total score: " + total);
        System.out.println("precision:   " + total.precision());
        System.out.println("recall:      " + total.recall());
        System.out.println("F1:          " + total.fOne());
    }

    /***************************************************************
     */
    public static void main(String[] args) {

        debug = false;
        Interpreter.replaceInstances = false;
        Interpreter.debug = false;
        RelExtract.initOnce();
        CoNLL04 coNLL = new CoNLL04();
        coNLL.parse();
        //System.out.println(coNLL.toString());
        coNLL.extractAll();
    }
}
