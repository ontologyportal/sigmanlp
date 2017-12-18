package com.articulate.nlp;

import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Clause;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semconcor.Searcher;
import com.articulate.sigma.*;
import com.articulate.sigma.nlg.NLGUtils;

import java.util.*;

/**
 2015-2017 Articulate Software
 2017-     Infosys

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

 * Extract relation expressions based on SUMO relation &%format expressions
 */
public class RelExtract {

    public static boolean debug = true;

    /** *************************************************************
     */
    public static CNF toCNF(Interpreter interp, String input) {

        if (input.contains("such that"))
            input = input.substring(input.indexOf("such that") + 10,input.length());
        input = Character.toUpperCase(input.charAt(0)) + input.substring(1) + ".";
        //System.out.println(interp.p.toDependencies(result));
        //return interp.interpretGenCNF(input);
        return interp.p.toCNFDependencies(input);
    }

    /** *************************************************************
     */
    public static String removeFormatChars(String s) {

        s = s.replaceAll("&%","");
        s = s.replaceAll("%n\\{[^}]+\\}","");
        s = s.replaceAll("%.","");
        s = s.replaceAll("\\{","");
        s = s.replaceAll("\\}","");
        s = s.replaceAll("  "," ");
        return s;
    }

    /** *************************************************************
     * retain only the literals in the dependency parse that also are
     * from the original format string
     */
    public static CNF formatWordsOnly(CNF cnfinput, String format) {

        List<Literal> literalList = cnfinput.toLiterals();
        CNF cnf = new CNF();
        String[] words = format.split(" ");
        System.out.println("formatWordsOntly(): " + Arrays.toString(words));
        for (String w : words) {
            if (StringUtil.emptyString(w))
                continue;
            for (Literal lit : literalList) {
                if (w.equals(Literal.tokenOnly(lit.arg1)) ||
                    w.equals(Literal.tokenOnly(lit.arg2)))
                    cnf.append(lit);
            }
        }
        return cnf;
    }

    /** *************************************************************
     */
    public static String buildFormulaString(KB kb, String rel) {

        ArrayList<String> sig = kb.kbCache.signatures.get(rel);
        StringBuffer sb = new StringBuffer();
        if (sig != null && sig.size() > 1) {
            sb.append("(exists (");
            int counter = 1;
            for (String s : sig) {
                if (!StringUtil.emptyString(s)) {
                    String varname = s.toUpperCase();
                    if (s.charAt(s.length() - 1) == '+')
                        varname = varname.substring(0, s.length() - 1);
                    sb.append("?" + varname + Integer.toString(counter++) + " ");
                }
            }
            sb.append(") ");
            counter = 1;
            sb.append("(" + rel + " ");
            for (String s : sig) {
                if (!StringUtil.emptyString(s)) {
                    String varname = s.toUpperCase();
                    if (s.charAt(s.length() - 1) == '+')
                        varname = varname.substring(0, s.length() - 1);
                    sb.append("?" + varname + Integer.toString(counter++) + " ");
                }
            }
            sb.append("))");
            return sb.toString();
        }
        return "";
    }

    /** *************************************************************
     * Return a verion of the CNF input where words found in the
     * formatString are turned into word variables like "part*" that
     * requires the word but not its token number to match, and makes
     * all other constants in the CNF into variables.
     */
    public static CNF toVariables(CNF input, String formatString) {

        String[] words = formatString.split(" ");
        CNF result = new CNF();
        for (String w : words) {
            if (StringUtil.emptyString(w))
                continue;
            for (Clause c : input.clauses) {
                for (Literal lit : c.disjuncts) {
                    if (w.equalsIgnoreCase(Literal.tokenOnly(lit.arg1)) && !lit.arg1.endsWith("*"))
                        lit.arg1 = Literal.tokenOnly(lit.arg1) + "*";
                    if (w.equalsIgnoreCase(Literal.tokenOnly(lit.arg2)) && !lit.arg2.endsWith("*"))
                        lit.arg2 = Literal.tokenOnly(lit.arg2) + "*";
                }
            }
        }
        for (Clause c : input.clauses) {
            for (Literal lit : c.disjuncts) {
                if (!lit.arg1.endsWith("*"))
                    lit.arg1 = "?" + lit.arg1;
                if (!lit.arg2.endsWith("*"))
                    lit.arg2 = "?" + lit.arg2;
            }
        }
        return input;
    }

    /** *************************************************************
     */
    public static CNF removeRoot(CNF input) {

        CNF newcnf = new CNF();
        List<Literal> lits = input.toLiterals();
        for (Literal lit : lits)
            if (!lit.pred.equals("root"))
                newcnf.append(lit);
        return newcnf;
    }

    /** *************************************************************
     */
    public static CNF removeDet(CNF input) {

        CNF newcnf = new CNF();
        List<Literal> lits = input.toLiterals();
        for (Literal lit : lits)
            if (!lit.pred.equals("det"))
                newcnf.append(lit);
        return newcnf;
    }

    /** *************************************************************
     * only allow for word variables (which have a trailing '*')
     */
    public static boolean stopWordsOnly(CNF cnf) {

        //System.out.println("stopWordsOnly(): " + cnf);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (l.pred.equals("sumo") || l.pred.equals("isSubclass"))
                    continue;
                String arg1Tok = Literal.tokenOnly(l.arg1);
                //System.out.println("stopWordsOnly(): " + arg1Tok);
                if (!StringUtil.emptyString(arg1Tok) && l.arg1.endsWith("*")) {
                    arg1Tok = arg1Tok.substring(0,arg1Tok.length());
                    if (!WordNet.wn.isStopWord(arg1Tok)) {
                        //System.out.println("stopWordsOnly(): found non-stop word " + arg1Tok);
                        return false;
                    }
                }
                String arg2Tok = Literal.tokenOnly(l.arg2);
                //System.out.println("stopWordsOnly(): " + arg2Tok);
                if (!StringUtil.emptyString(arg2Tok) && l.arg2.endsWith("*")) {
                    arg2Tok = arg2Tok.substring(0,arg2Tok.length());
                    if (!WordNet.wn.isStopWord(arg2Tok)) {
                        //System.out.println("stopWordsOnly(): found non-stop word " + arg2Tok);
                        return false;
                    }
                }
            }
        }
        //System.out.println("stopWordsOnly(): " + cnf + " has only stop words");
        return true;
    }

    /** *************************************************************
     */
    public static void test(HashMap<String,CNF> patterns) {

        System.out.println("*************** RelExtract.test() ***********************");
        ArrayList<String> dependencies = null;
        ArrayList<String> sentences = null;
        String dbFilepath = "wikipedia/wiki1";
        for (String rel : patterns.keySet()) {
            CNF cnf = patterns.get(rel);
            if (!stopWordsOnly(cnf)) {
                try {
                    CNF noTypes = removeTypes(cnf);
                    dependencies = new ArrayList<String>();
                    sentences = new ArrayList<String>();
                    Searcher.search(dbFilepath, "", noTypes.toString(), sentences, dependencies);
                    if (sentences.size() > 0) {
                        for (int i = 0; i < sentences.size(); i++) {
                            System.out.println("test(): without types: relation: " + rel);
                            System.out.println("cnf: " + noTypes.toString());
                            System.out.println("stop words only: " + stopWordsOnly(noTypes));
                            System.out.println("dep: " + dependencies.get(i));
                            System.out.println("sentence: " + sentences.get(i));
                            System.out.println();
                        }
                    }
                    dependencies = new ArrayList<String>();
                    sentences = new ArrayList<String>();
                    Searcher.search(dbFilepath, "", cnf.toString(), sentences, dependencies);
                    if (sentences.size() > 0) {
                        for (int i = 0; i < sentences.size(); i++) {
                            System.out.println("test(): relation: " + rel);
                            System.out.println("cnf: " + cnf.toString());
                            System.out.println("stop words only: " + stopWordsOnly(cnf));
                            System.out.println("dep: " + dependencies.get(i));
                            System.out.println("sentence: " + sentences.get(i));
                            System.out.println();
                        }
                    }
                }
                catch (Exception e) {
                    System.out.println("Error in RelExtract.test()");
                    e.printStackTrace();
                }
            }
            else
                System.out.println("test(): stop words only in: " + cnf);
        }
    }

    /** *************************************************************
     */
    public static CNF removeTypes(CNF cnfInput) {

        CNF cnfnew = new CNF();
        List<Literal> cnflist = cnfInput.toLiterals();
        for (Literal l : cnflist)
            if (!l.pred.equals("sumo") && !l.pred.equals("isSubclass"))
                cnfnew.append(l);
        return cnfnew;
    }

    /** *************************************************************
     */
    public static CNF addTypes(CNF cnfInput, HashMap<String,String> outputMap,
                               String rel, KB kb) {

        int varnum = 0;
        CNF cnfnew = new CNF();
        List<Literal> cnflist = cnfInput.toLiterals();
        for (Literal l : cnflist)
            cnfnew.append(l);
        for (String key : outputMap.keySet()) {
            String arg = Integer.toString(Literal.tokenNum(key));
            int argnum = Integer.parseInt(arg);
            String type = "";
            if (debug) System.out.println("addTypes(): " + kb.kbCache.signatures.get(rel));
            if (debug) System.out.println("addTypes(): " + arg);
            if (kb.kbCache.signatures.get(rel) != null && kb.kbCache.signatures.get(rel).size() > argnum)
                type = kb.kbCache.signatures.get(rel).get(argnum);
            if (StringUtil.emptyString(type)) {
                System.out.println("RelExtract.addTypes(): no type signature found for " + rel +
                     " for argument number " + arg);
                continue;
            }
            if (!type.endsWith("+")) {
                cnfnew.append(new Literal("sumo(" + type + "," + outputMap.get(key) + ")"));
            }
            else {
                cnfnew.append(new Literal("sumo(?TYPEVAR" + Integer.toString(varnum) + "," + outputMap.get(key) + ")"));
                cnfnew.append(new Literal("isSubclass(" + type.substring(0,type.length()-1) + ",?TYPEVAR" + Integer.toString(varnum) + ")"));
                varnum++;
            }
        }
        return cnfnew;
    }

    /** *************************************************************
     */
    public static HashMap<String,CNF> process() {

        System.out.println("RelExtract.process()");
        HashMap<String,CNF> resultSet = new HashMap<String,CNF>();
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        ArrayList<Formula> forms = kb.ask("arg",0,"format");
        for (Formula f : forms) {
            String rel = f.getArgument(2);
            if (rel.endsWith("Fn"))
                continue;
            String formatString = f.getArgument(3);
            String formulaString = buildFormulaString(kb,rel);
            if (StringUtil.emptyString(formatString))
                continue;

            System.out.println();
            System.out.println("formula: " + formulaString);
            String nlgString = StringUtil.filterHtml(NLGUtils.htmlParaphrase("", formulaString,
                        kb.getFormatMap("EnglishLanguage"), kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage"));
            System.out.println("nlg: " + nlgString);
            System.out.println("output map: " + NLGUtils.outputMap);

            if (StringUtil.emptyString(nlgString))
                continue;
            CNF cnf = toCNF(interp, nlgString);
            formatString = removeFormatChars(formatString);
            CNF filtered = formatWordsOnly(cnf, formatString);
            filtered = toVariables(filtered,formatString);
            System.out.println(filtered);
            filtered = removeRoot(filtered);
            filtered = removeDet(filtered);
            HashSet<Literal> litSet = new HashSet<>();
            litSet.addAll(filtered.toLiterals());
            System.out.println(litSet);
            CNF cnfResult = CNF.fromLiterals(litSet);
            System.out.println("without types: " + cnfResult);
            CNF withTypes = addTypes(cnfResult, NLGUtils.outputMap,rel,kb);
            System.out.println("with types: " + withTypes);
            if (!stopWordsOnly(cnfResult))
                resultSet.put(rel,withTypes);
            System.out.println();
        }
        return resultSet;
    }

    /** *************************************************************
     */
    public static void testProcess() {

        System.out.println("RelExtract.process()");
        HashMap<String,CNF> resultSet = new HashMap<String,CNF>();
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        ArrayList<Formula> forms = kb.ask("arg",0,"format");
        Formula f = new Formula("(format EnglishLanguage subOrganization \"%1 is %n a part of the organization %2\")");
        String rel = f.getArgument(2);
        String formatString = f.getArgument(3);
        String formulaString = buildFormulaString(kb,rel);
        if (StringUtil.emptyString(formatString))
            return;

        System.out.println();
        System.out.println("formula: " + formulaString);
        String nlgString = StringUtil.filterHtml(NLGUtils.htmlParaphrase("", formulaString,
                kb.getFormatMap("EnglishLanguage"), kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage"));
        System.out.println("nlg: " + nlgString);
        System.out.println("output map: " + NLGUtils.outputMap);

        if (StringUtil.emptyString(nlgString))
            return;
        CNF cnf = toCNF(interp, nlgString);
        formatString = removeFormatChars(formatString);
        CNF filtered = formatWordsOnly(cnf, formatString);
        filtered = toVariables(filtered,formatString);
        System.out.println(filtered);
        filtered = removeRoot(filtered);
        filtered = removeDet(filtered);
        HashSet<Literal> litSet = new HashSet<>();
        litSet.addAll(filtered.toLiterals());
        System.out.println(litSet);
        CNF cnfResult = CNF.fromLiterals(litSet);
        System.out.println("without types: " + cnfResult);
        CNF withTypes = addTypes(cnfResult, NLGUtils.outputMap,rel,kb);
        System.out.println("with types: " + withTypes);
        if (!stopWordsOnly(cnfResult))
            resultSet.put(rel,withTypes);
        System.out.println();
    }

    /** *************************************************************
     */
    public static String processPosNeg(String format) {

        StringBuffer sb = new StringBuffer();
        ArrayList<String> ar = TFIDF.splitToArrayList(format);
        for (String s : ar) {
            if (s.startsWith("%p") || s.startsWith("%n")) {
                if (s.startsWith("%p{"))
                    sb.append(s.substring(3,s.length()-1) + " ");
            }
            else
                sb.append(s + " ");
        }
        return sb.toString().trim().replaceAll("  "," ");
    }

    /** *************************************************************
     * assume no adjacent arguments
     */
    public static ArrayList<AVPair> alignFormat(String sentence, String format) {

        ArrayList<AVPair> result = new ArrayList<AVPair>(); // attribute is from format, value from sentence
        format = processPosNeg(format);
        ArrayList<String> arFormat = TFIDF.splitToArrayList(format);
        ArrayList<String> arSent = TFIDF.splitToArrayList(sentence);
        int index = 0;
        boolean done = false;
        for (int i = 0; i < arSent.size(); i++) {
        }
        return result;
    }

    /** *************************************************************
     */
    public static void testStopWordsOnly() {

        KBmanager.getMgr().initializeOnce();
        System.out.println("testStopWordsOnly(): not all should be true");
        String c = "cc(?relation-2,and*), cop(?disjoint-8,are*).";
        CNF cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
        c = "case(?human-10,of*), cop(?name-7,is*).";
        cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
        c = "nmod:of(sale*,?transaction-9), dep(brokers*,sale*), compound(brokers*,?agent-2), case(?transaction-9,of*).";
        cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
    }

    /** *************************************************************
     */
    public static void main(String[] args) {

        testProcess();
        //testStopWordsOnly();
        //HashMap<String,CNF> resultSet = process();
        //test(resultSet);
    }
}
