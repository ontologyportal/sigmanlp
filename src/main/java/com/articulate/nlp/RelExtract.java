package com.articulate.nlp;

import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Clause;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semconcor.Searcher;
import com.articulate.sigma.*;
import com.articulate.sigma.nlg.LanguageFormatter;
import com.articulate.sigma.nlg.NLGUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

import java.io.File;
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

    public static boolean debug = false;
    private static int varnum = 0;

    /** *************************************************************
     * Copy SUMO categories in the outputMap that are associated with
     * tokens into the tokens in the cnf
     */
    public static void addCategories(CNF cnf, HashMap<String,CoreLabel> outputMap) {

        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                for (CoreLabel cl : outputMap.values()) {
                    //if (debug) System.out.println("addCategories(): " + l.clArg1.toString() + " =? " + cl.toString());
                    if (cl.toString().equals(l.clArg1.toString())) {
                        //if (debug) System.out.println("addCategories(): " + l.clArg1.category());
                        l.clArg1.setCategory(cl.category());
                    }
                    if (cl.toString().equals(l.clArg2.toString())) {
                        //if (debug) System.out.println("addCategories(): " + l.clArg1.category());
                        l.clArg2.setCategory(cl.category());
                    }
                }
            }
        }
    }

    /** *************************************************************
     * Remove the standard phrasing at the beginning of a NLG-produced
     * sentence and get the dependency parse for it.  Add a period at the
     * end of the sentence
     */
    public static CNF toCNF(Interpreter interp, String input, HashMap<String,CoreLabel> outputMap) {

        input = LanguageFormatter.removePreamble(input);
        input = Character.toUpperCase(input.charAt(0)) + input.substring(1) + ".";
        if (debug) System.out.println("toCNF(): input " + input);
        //System.out.println(interp.p.toDependencies(result));
        //return interp.interpretGenCNF(input);
        //CNF result = interp.p.toCNFDependencies(input);
        CNF result = interp.p.toCNFEdgeDependencies(input);
        addCategories(result,outputMap);
        if (debug) System.out.println("toCNF():result " + result);
        return result;
    }

    /** *************************************************************
     * TODO: refactor to generate both the positive and negative pattern
     */
    public static String removeFormatChars(String s) {

        s = s.replaceAll("&%","");
        s = s.replaceAll("%n\\{[^}]+\\}","");       // assume only positive patterns
        s = s.replaceAll("%p\\{([^}]+)\\}","$1");
        s = s.replaceAll("%.","");
        s = s.replaceAll("\\{","");
        s = s.replaceAll("\\}","");
        s = s.replaceAll("  "," ");
        return s;
    }

    /** *************************************************************
     * TODO: refactor to generate both the positive and negative pattern
     */
    public static String removePosNeg(String s) {

        s = s.replaceAll("%n\\{[^}]+\\}","");       // assume only positive patterns
        s = s.replaceAll("%p\\{([^}]+)\\}","$1");
        s = s.replaceAll("  "," ");
        s = StringUtil.removeEnclosingQuotes(s);
        return s;
    }

    /** *************************************************************
     * Retain only the literals involving tokens in the dependency parse that also are
     * from the original format string.  Literals involving tokens that are arguments and
     * not from the format string will have a non-empty type (which means a CoreLabel.category()).
     */
    public static CNF formatWordsOnly(CNF cnfinput, String format) {

        if (debug) System.out.println("RelExtract.formatWordsOnly(): " + cnfinput);
        List<Literal> literalList = cnfinput.toLiterals();
        CNF cnf = new CNF();
        String[] wordsAr = format.split(" ");
        ArrayList<String> words = new ArrayList<>();
        words.addAll(Arrays.asList(wordsAr));
        if (debug)  System.out.println("RelExtract.formatWordsOnly(): input: " + Arrays.asList(wordsAr).toString());
        for (Literal lit : literalList) {
            if (debug) System.out.println("RelExtract.formatWordsOnly(): lit: " + lit);
            if (debug) System.out.println("RelExtract.formatWordsOnly(): arg1 cat: " + lit.clArg1.category());
            if (debug) System.out.println("RelExtract.formatWordsOnly(): arg2 cat: " + lit.clArg2.category());
            if (StringUtil.emptyString(lit.clArg1.category())) {
                cnf.append(lit);
            }
            else if (StringUtil.emptyString(lit.clArg2.category())) {
                cnf.append(lit);
            }
        }
        if (debug) System.out.println("RelExtract.formatWordsOnly(): result: " + cnf);
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

        if (debug) System.out.println("RelExtract.toVariables(): input: " + input);
        for (Clause c : input.clauses) {
            for (Literal lit : c.disjuncts) {
                if (StringUtil.emptyString(lit.clArg1.category()))
                    lit.clArg1.set(LanguageFormatter.VariableAnnotation.class,lit.clArg1.value() + "*");
                else
                    lit.clArg1.set(LanguageFormatter.VariableAnnotation.class,"?" + lit.clArg1.value() + "-" + lit.clArg1.index());
                if (StringUtil.emptyString(lit.clArg2.category()))
                    lit.clArg2.set(LanguageFormatter.VariableAnnotation.class,lit.clArg2.value() + "*");
                else
                    lit.clArg2.set(LanguageFormatter.VariableAnnotation.class,"?" + lit.clArg2.value() + "-" + lit.clArg2.index());
                //if (!lit.arg1.endsWith("*"))
                //    lit.setarg1("?" + lit.arg1);
                //if (!lit.arg2.endsWith("*"))
                 //   lit.setarg2("?" + lit.arg2);
            }
        }
        if (debug) System.out.println("RelExtract.toVariables(): result: " + printCNFVariables(input));
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
     * only check for words from the original format statement, which
     * do not have a CoreNLP category() value
     */
    public static boolean stopWordsOnly(CNF cnf) {

        if (debug) System.out.println("stopWordsOnly(): " + cnf);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (l.pred.equals("sumo") || l.pred.equals("isSubclass"))
                    continue;
                //printCoreLabel(l.clArg1);
                if (l.clArg1 != null && l.clArg1.category() == null) {
                    if (!WordNet.wn.isStopWord(l.clArg1.word())) {
                        if (debug) System.out.println("stopWordsOnly(): found non-stop word " + l.clArg1.word());
                        return false;
                    }
                    else if (debug) System.out.println("stopWordsOnly(): found stop word " + l.clArg1.word());
                }

                //printCoreLabel(l.clArg2);
                if (l.clArg2 != null && l.clArg2.category() == null) {
                    if (!WordNet.wn.isStopWord(l.clArg2.word())) {
                        if (debug) System.out.println("stopWordsOnly(): found non-stop word " + l.clArg2.word());
                        return false;
                    }
                    else if (debug) System.out.println("stopWordsOnly(): found stop word " + l.clArg2.word());
                }
            }
        }
        if (debug) System.out.println("stopWordsOnly(): " + cnf + " has only stop words");
        return true;
    }

    /** *************************************************************
     * Make the value() of each CoreLabel equal to its VariableAnnotation,
     * if present.
     */
    public static CNF promoteVariables(CNF pattern) {

        CNF result = new CNF();
        for (Clause c : pattern.clauses) {
            for (Literal l : c.disjuncts) {
                Literal newl = new Literal(l);
                String var1 = l.clArg1.getString(LanguageFormatter.VariableAnnotation.class);
                if (!StringUtil.emptyString(var1))
                    newl.clArg1.setValue(var1);
                newl.arg1 = newl.clArg1.value();
                String var2 = l.clArg2.getString(LanguageFormatter.VariableAnnotation.class);
                if (!StringUtil.emptyString(var2))
                    newl.clArg2.setValue(var2);
                result.append(newl);
                newl.arg2 = newl.clArg2.value();
            }
        }
        return result;
    }

    /** *************************************************************
     * Get a CoreLabel from the pattern in which the value() matches
     * the token
     */
    public static CoreLabel getMatchingCL(String token, CNF pattern) {

        CoreLabel cl = null;
        for (Clause c : pattern.clauses) {
            for (Literal l : c.disjuncts) {
                if (l.clArg1.value().equals(token))
                    return l.clArg1;
                if (l.clArg2.value().equals(token))
                    return l.clArg2;
            }
        }
        return cl;
    }

    /** *************************************************************
     * Add class membership to the instances in the generated formula.
     */
    public static Literal findConstantInCNF(CNF pattern, String pred, String constant) {

        if (debug) System.out.println("findConstantInCNF(): " + pred + ", " + constant);
        for (Clause c : pattern.clauses) {
            for (Literal l : c.disjuncts) {
                if (debug) System.out.println(l);
                if (l.pred.equals(pred) && l.arg2.equals(constant))
                    return l;
            }
        }
        if (debug) System.out.println();
        return null;
    }

    /** *************************************************************
     * Add class membership to the instances in the generated formula.
     */
    public static Formula addFormulaTypes(Formula f, CNF pattern, HashMap<String,String> bindings) {

        Formula fnew = new Formula();
        StringBuffer sb = new StringBuffer();
        sb.append("(and ");
        sb.append(f.toString());
        for (String s : bindings.values()) {
            Literal l = findConstantInCNF(pattern,"sumo",s);
            if (l != null)
                sb.append("(instance " + s + " " + l.arg1 + ")");
        }
        sb.append(")");
        fnew.read(sb.toString());
        return fnew;
    }

    /** *************************************************************
     * Generate a formula, which is a single relation rel with parameters
     * filled in from the set of bindings and the pattern.
     */
    public static Formula generateFormula(CNF pattern, CNF dep, HashMap<String,String> bindings, String rel) {

        Formula f = new Formula();
        ArrayList<String> args = new ArrayList();
        KB kb = KBmanager.getMgr().getKB("SUMO");
        int arity = kb.kbCache.getArity(rel);
        for (int i = 0; i <= arity; i++)
            args.add("");
        for (String s : bindings.keySet()) {
            CoreLabel cl = getMatchingCL(s,pattern);
            int arg = cl.get(LanguageFormatter.RelationArgumentAnnotation.class);
            args.set(arg,bindings.get(s));
        }
        StringBuffer sb = new StringBuffer();
        sb.append("(" + rel + " ");
        sb.append(args.get(1));
        for (int i = 2; i < args.size(); i++)
            sb.append(" " + args.get(i));
        sb.append(")");
        f.read(sb.toString());
        return addFormulaTypes(f,dep,bindings);
    }

    /** *************************************************************
     * print matches for a pattern that corresponds to a particular
     * relation
     */
    public static void searchForOnePattern(String rel, CNF pattern) {

        ArrayList<String> dependencies = null;
        ArrayList<String> sentences = null;
        String dbFilepath = "wikipedia/wiki1";
        pattern = promoteVariables(pattern); // make the variable annotation the value
        if (!stopWordsOnly(pattern)) {
            try {
                CNF noTypes = removeTypes(pattern);
                System.out.println("searchForOnePattern(): no types: " + noTypes);
                dependencies = new ArrayList<String>();
                sentences = new ArrayList<String>();
                Searcher.search(dbFilepath, "", noTypes.toString(), sentences, dependencies);
                if (sentences.size() > 0) {
                    for (int i = 0; i < sentences.size(); i++) {
                        System.out.println("test(): without types: relation: " + rel);
                        System.out.println("cnf: " + noTypes.toString());
                        System.out.println("stop words only: " + stopWordsOnly(noTypes));
                        System.out.println("sentence: " + sentences.get(i));
                        System.out.println("dep: " + dependencies.get(i));
                        System.out.println("pattern: " + noTypes);
                        HashMap<String,String> bindings = Searcher.matchDepBind(noTypes,dependencies.get(i));
                        System.out.println("bindings: " + bindings);
                        CNF depcnf = new CNF(StringUtil.removeEnclosingCharPair(dependencies.get(i).toString(),1,'[',']'));
                        System.out.println(generateFormula(noTypes,depcnf,bindings,rel));
                        System.out.println();
                    }
                }
                dependencies = new ArrayList<String>();
                sentences = new ArrayList<String>();
                System.out.println("searchForOnePattern(): with types: " + pattern);
                Searcher.search(dbFilepath, "", pattern.toString(), sentences, dependencies);
                if (sentences.size() > 0) {
                    for (int i = 0; i < sentences.size(); i++) {
                        System.out.println("test(): relation: " + rel);
                        System.out.println("cnf: " + pattern.toString());
                        System.out.println("stop words only: " + stopWordsOnly(pattern));
                        System.out.println("dep: " + dependencies.get(i));
                        System.out.println("sentence: " + sentences.get(i));
                        System.out.println("dep: " + dependencies.get(i));
                        System.out.println("pattern: " + pattern);
                        HashMap<String,String> bindings = Searcher.matchDepBind(pattern,dependencies.get(i));
                        System.out.println("bindings: " + bindings);
                        CNF depcnf = new CNF(StringUtil.removeEnclosingCharPair(dependencies.get(i).toString(),1,'[',']'));
                        System.out.println(generateFormula(pattern,depcnf,bindings,rel));
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
            System.out.println("test(): stop words only in: " + pattern);
    }

    /** *************************************************************
     */
    public static void test(HashMap<String,CNF> patterns) {

        System.out.println("*************** RelExtract.test() ***********************");
        for (String rel : patterns.keySet()) {
            searchForOnePattern(rel, patterns.get(rel));
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
    public static void addArgsToCNF(CNF cnfResult, HashMap<String,CoreLabel> outputMap) {

        for (Clause c : cnfResult.clauses) {
            for (Literal l : c.disjuncts) {
                if (outputMap.keySet().contains(l.clArg1.toString())) {
                    CoreLabel clOutputMap = outputMap.get(l.clArg1.toString());
                    l.clArg1.set(LanguageFormatter.RelationArgumentAnnotation.class, clOutputMap.get(LanguageFormatter.RelationArgumentAnnotation.class));
                }
                if (outputMap.keySet().contains(l.clArg2.toString())) {
                    CoreLabel clOutputMap = outputMap.get(l.clArg2.toString());
                    l.clArg2.set(LanguageFormatter.RelationArgumentAnnotation.class, clOutputMap.get(LanguageFormatter.RelationArgumentAnnotation.class));
                }
            }
        }
    }

    /** *************************************************************
     * Generate literals with a "sumo" and possibly "isSubclass" predicate
     * that express the SUMO types of dependency tokens
     */
    public static HashSet<Literal> generateSUMO(CoreLabel cl, HashSet<String> typeGenerated) {

        if (debug) System.out.println("RelExtract.generateSUMO(): ");
        if (debug) printCoreLabel(cl);
        HashSet<Literal> result = new HashSet<>();
        String type = "";
        type = cl.category();
        if (StringUtil.emptyString(type)) {
            if (debug) System.out.println("RelExtract.generateSUMO(): no type found for " + cl);
            return null;
        }
        if (typeGenerated.contains(cl.toString()))
            return null;
        typeGenerated.add(cl.toString());
        /* if (!type.endsWith("+")) {
            cl.setValue("?" + cl.value());
            //cl.set(LanguageFormatter.VariableAnnotation.class,cl.value());
            CoreLabel classLabel = new CoreLabel();
            classLabel.setValue(type);
            String classVar = "?CL" + Integer.toString(varnum++);
            result.add(new Literal("sumo",classLabel,cl));
            if (debug) System.out.println("RelExtract.generateSUMO(): result: " + result);
            return result;
        }
        else { */
            CoreLabel clVar = new CoreLabel();
            clVar.setValue("?TYPEVAR" + Integer.toString(varnum));
            clVar.set(LanguageFormatter.VariableAnnotation.class,clVar.value());
            result.add(new Literal("sumo",clVar,cl));
            CoreLabel clType = new CoreLabel();
            clType.setValue(type.substring(0,type.length()));
            result.add(new Literal("isSubclass",clVar,clType));
            varnum++;
        //}
        if (debug) System.out.println("RelExtract.generateSUMO(): result: " + result);
        return result;
    }

    /** *************************************************************
     */
    public static CNF addTypes(CNF cnfInput) {

        HashSet<String> typeGenerated = new HashSet<>();
        HashSet<Literal> sumoLit = new HashSet<>();
        Integer varnum = 0;
        CNF cnfnew = new CNF();
        List<Literal> cnflist = cnfInput.toLiterals();
        for (Literal l : cnflist) {
            cnfnew.append(l);
            HashSet<Literal> temp = generateSUMO(l.clArg1,typeGenerated);
            if (temp != null)
                sumoLit.addAll(temp);
            temp = generateSUMO(l.clArg2,typeGenerated);
            if (temp != null)
                sumoLit.addAll(temp);
        }
        if (sumoLit != null)
            cnfnew.appendAll(sumoLit);
        return cnfnew;
    }

    /** *************************************************************
     * Process one SUMO format statement to create a dependency parse
     * pattern that can match free text.  Use language generation
     * to create a sentence from the format statement, then run
     * dependency parsing, then modify the dependency parse to keep
     * just the essential parts of the pattern and add SUMO type
     * restrictions.
     *
     * Words appearing in the format statement become word variables (word*),
     * words that are the arguments to the relation become free variables
     * (?word) that have an associated sumo type
     */
    public static HashMap<String,CNF> processOneRelation(Formula form,
                  KB kb, Interpreter interp) {

        HashMap<String,CNF> resultSet = new HashMap<String,CNF>();
        String rel = form.getArgument(2);
        if (rel.endsWith("Fn"))
            return null;
        String formatString = form.getArgument(3);
        String oldFormatString = new String(formatString);
        formatString = removePosNeg(formatString);
        if (debug) System.out.println("no pos/neg format String: " + formatString);
        kb.getFormatMap("EnglishLanguage").put(rel,formatString); // hack to alter the format String
        String formulaString = buildFormulaString(kb,rel);
        if (StringUtil.emptyString(formatString))
            return null;

        if (debug) System.out.println();
        if (debug) System.out.println("processOneRelation(): formula: " + formulaString);
        String nlgString = StringUtil.filterHtml(NLGUtils.htmlParaphrase("", formulaString,
                kb.getFormatMap("EnglishLanguage"), kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage"));
        if (debug) System.out.println("nlg: " + nlgString);
        if (debug) System.out.println("output map: " + NLGUtils.outputMap);

        if (StringUtil.emptyString(nlgString))
            return null;
        CNF cnf = toCNF(interp, nlgString, NLGUtils.outputMap);
        kb.getFormatMap("EnglishLanguage").put(rel,oldFormatString); // restore the original
        formatString = removeFormatChars(formatString);
        if (debug) System.out.println("without format chars: " + formatString);
        CNF filtered = formatWordsOnly(cnf, formatString);
        filtered = toVariables(filtered, formatString);
        if (debug) System.out.println(filtered);
        filtered = removeRoot(filtered);
        filtered = removeDet(filtered);
        HashSet<Literal> litSet = new HashSet<>();
        litSet.addAll(filtered.toLiterals());
        if (debug) System.out.println(litSet);
        CNF cnfResult = CNF.fromLiterals(litSet);
        addArgsToCNF(cnfResult,NLGUtils.outputMap);
        if (debug) System.out.println("processOneRelation(): without types: " + cnfResult);
        CNF withTypes = addTypes(cnfResult);
        if (debug) System.out.println("processOneRelation(): with types: " + withTypes);
        if (!stopWordsOnly(cnfResult))
            resultSet.put(rel, withTypes);
        if (debug) System.out.println("processOneRelation(): result: " + resultSet);
        if (debug) System.out.println();
        return resultSet;
    }

    /** *************************************************************
     */
    public static String addRHS(String rel, CNF lhs) {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        int valence = kb.kbCache.valences.get(rel);
        String[] stmt = new String[valence + 1];
        for (Clause c : lhs.clauses) {
            for (Literal l : c.disjuncts) {
                if (l.clArg1.containsKey(LanguageFormatter.RelationArgumentAnnotation.class)) {
                    int arg = l.clArg1.get(LanguageFormatter.RelationArgumentAnnotation.class);
                    if (arg > 0 && arg < valence + 1) {
                        stmt[arg] = l.clArg1.get(LanguageFormatter.VariableAnnotation.class);
                        if (debug) System.out.println("addRHS():added");
                        if (debug) printCoreLabel(l.clArg1);
                    }
                    else {
                        if (debug) System.out.println("Error in addRHS(): arg out of bounds for ");
                        if (debug) printCoreLabel(l.clArg1);
                    }
                }
                if (l.clArg2.containsKey(LanguageFormatter.RelationArgumentAnnotation.class)) {
                    int arg = l.clArg2.get(LanguageFormatter.RelationArgumentAnnotation.class);
                    if (arg > 0 && arg < valence + 1) {
                        stmt[arg] = l.clArg2.get(LanguageFormatter.VariableAnnotation.class);
                        if (debug) System.out.println("addRHS():added");
                        if (debug) printCoreLabel(l.clArg2);
                    }
                    else {
                        if (debug) System.out.println("Error in addRHS(): arg out of bounds for ");
                        if (debug) printCoreLabel(l.clArg2);
                    }
                }
            }
        }
        StringBuffer resultStr = new StringBuffer();
        resultStr.append("{(" + rel + " ");
        for (int i = 1; i < valence + 1; i++) {
            if (i > 1)
                resultStr.append(" ");
            resultStr.append(stmt[i]);
            if (stmt[i] == null)
                return null;
        }
        resultStr.append(")}");
        return resultStr.toString();
    }

    /** *************************************************************
     * Process all the format statements in SUMO to create dependency
     * parse templates that match them.  Each relation should have a
     * single augmented dependency parse pattern that results.
     */
    public static void generateOneRelationPattern(Formula f, KB kb, Interpreter interp) {

        HashMap<String,CNF> temp = processOneRelation(f,kb,interp);
        if (temp == null)
            return;
        String rel = f.getArgument(2);
        if (temp.get(rel) == null)
            return;
        CNF lhs = temp.get(rel);
        String rhs = addRHS(rel,lhs);
        if (rhs != null)
            System.out.println(printCNFVariables(lhs) + " ==> " + rhs + ".\n");
    }

    /** *************************************************************
     * Process all the format statements in SUMO to create dependency
     * parse templates that match them.  Each relation should have a
     * single augmented dependency parse pattern that results.
     */
    public static void generateRelationPatterns() {

        long startTime = System.currentTimeMillis();
        int formCount = 0;
        System.out.println("; RelExtract.generateRelationPatterns()");
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
            varnum = 0;
            generateOneRelationPattern(f,kb,interp);
            formCount++;
            long currentTime = System.currentTimeMillis();
            long avg = (currentTime - startTime) / formCount;
            if (debug) System.out.println("; RelExtract.generateRelationPatterns(): avg time per form (seconds): " + (avg / 1000));
        }
    }

    /** *************************************************************
     * Process all the format statements in SUMO to create dependency
     * parse templates that match them.  Each relation should have a
     * single augmented dependency parse pattern that results. Then
     * search over a corpus for matches.
     */
    public static HashMap<String,CNF> processAndSearch() {

        long startTime = System.currentTimeMillis();
        int formCount = 0;
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
            HashMap<String,CNF> temp = processOneRelation(f,kb,interp);
            if (temp == null)
                continue;
            String rel = f.getArgument(2);
            if (temp.get(rel) == null)
                continue;
            searchForOnePattern(rel,temp.get(rel));
            if (temp != null)
                resultSet.putAll(temp);

            formCount++;
            long currentTime = System.currentTimeMillis();
            long avg = (currentTime - startTime) / formCount;
            System.out.println("process(): avg time per form (seconds): " + (avg / 1000));
        }
        return resultSet;
    }

    /** *************************************************************
     * show the full CoreLabels in each Literal of the CNF
     */
    public static String toCNFLabels(CNF cnf) {

        StringBuffer sb = new StringBuffer();
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                sb.append(l.toLabels());
                sb.append(", ");
            }
        }
        return sb.toString().substring(0,sb.toString().length()-2);
    }

    /** *************************************************************
     * only check for words from the original format statement, which
     * do not have a CoreNLP category() value
     */
    public static String printCNFVariables(CNF cnf) {

        StringBuffer sb = new StringBuffer();
        if (debug) System.out.println("printCNFVariables(): " + cnf);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (!StringUtil.emptyString(sb.toString()))
                    sb.append(", ");
                sb.append(l.pred + "(");
                if (!StringUtil.emptyString(l.clArg1.getString(LanguageFormatter.VariableAnnotation.class)))
                    sb.append(l.clArg1.getString(LanguageFormatter.VariableAnnotation.class));
                else {
                    if (l.clArg1.index() == -1)
                        sb.append(l.clArg1.value());
                    else
                        sb.append(l.clArg1.toString());
                }
                sb.append(",");
                if (!StringUtil.emptyString(l.clArg2.getString(LanguageFormatter.VariableAnnotation.class)))
                    sb.append(l.clArg2.getString(LanguageFormatter.VariableAnnotation.class));
                else {
                    if (l.clArg2.index() == -1)
                        sb.append(l.clArg2.value());
                    else
                        sb.append(l.clArg2.toString());
                }
                sb.append(")");
            }
        }
        return sb.toString();
    }

    /** *************************************************************
     */
    public static void sentenceExtract(String sent) {

        System.out.println("; RelExtract.sentenceExtract()");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        String filename = System.getProperty("user.home") + "/workspace/sumo/WordNetMappings" + File.separator + "Relations.txt";
        interp.initOnce(filename);
        ArrayList<CNF> inputs = Lists.newArrayList(interp.interpretGenCNF(sent));
        ArrayList<String> kifClauses = interp.interpretCNF(inputs);
    }

    /** *************************************************************
     */
    public static void interactive() {

        System.out.println("; RelExtract.sentenceExtract()");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        String filename = System.getProperty("user.home") + "/workspace/sumo/WordNetMappings" + File.separator + "Relations.txt";
        interp.initOnce(filename);
        String input = "";
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter sentence: ");
            input = scanner.nextLine().trim();
            if (!StringUtil.emptyString(input) && !input.equals("exit") && !input.equals("quit")) {
                if (input.equals("reload")) {
                    System.out.println("reloading semantic rewriting rules for relation extraction");
                    interp.loadRules(filename);
                }
                else if (input.equals("debug")) {
                    interp.debug = true;
                    System.out.println("debugging messages on");
                }
                else if (input.equals("nodebug")) {
                    interp.debug = false;
                    System.out.println("debugging messages off");
                }
                else {
                    ArrayList<CNF> inputs = Lists.newArrayList(interp.interpretGenCNF(input));
                    ArrayList<String> kifClauses = interp.interpretCNF(inputs);
                    System.out.println(kifClauses);
                }
            }
        } while (!StringUtil.emptyString(input) && !input.equals("exit") && !input.equals("quit"));
    }

    /** *************************************************************.
     */
    public static void testGenerateRelationPattern() {

        System.out.println("; RelExtract.testGenerateRelationPattern()");
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
        ArrayList<Formula> forms = kb.askWithRestriction(0,"format",2,"wears");
        System.out.println("; RelExtract.testGenerateRelationPattern()" + forms);
        generateOneRelationPattern(forms.get(0),kb,interp);
    }

    /** *************************************************************
     */
    public static void testProcessAndSearch() {

        System.out.println("RelExtract.testProcess()");
        String rel = "engineeringSubcomponent";
        Formula f = new Formula("(format EnglishLanguage engineeringSubcomponent \"%1 is %n a &%component of %2\")");
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
        HashMap<String,CNF> result = processOneRelation(f,kb,interp);
        System.out.println("RelExtract.testProcess(): " + printCNFVariables(result.get(rel)));
        searchForOnePattern(rel,result.get(rel));
    }

    /** *************************************************************
     */
    public static void testProcess() {

        System.out.println("RelExtract.testProcess()");
        Formula f = new Formula("(format EnglishLanguage engineeringSubcomponent \"%1 is %n a &%component of %2\")");
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
        HashMap<String,CNF> result = processOneRelation(f,kb,interp);
        System.out.println("RelExtract.testProcess(): " + toCNFLabels(result.get("engineeringSubcomponent")));
    }

    /** *************************************************************
     */
    public static void testPattern(String rel) {

        System.out.println("RelExtract.testPattern()");
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
        List<Formula> forms = kb.askWithRestriction(0,"format",2,rel);
        if (forms == null || forms.size() < 1) {
            System.out.println("RelExtract.testPattern(): no format for relation " + rel);
            return;
        }
        Formula f = forms.get(0);
        HashMap<String,CNF> result = processOneRelation(f,kb,interp);
        System.out.println("RelExtract.testPattern(): " + toCNFLabels(result.get(rel)));
    }

    /** *************************************************************
     */
    public static void printCoreLabel(CoreLabel cl) {

        System.out.println(toCoreLabelString(cl));
    }

    /** *************************************************************
     * Show the useful fields of a CoreLabel.  We're not concerned
     * with character-level information at our level of analysis.
     */
    public static String toCoreLabelString(CoreLabel cl) {

        StringBuffer sb = new StringBuffer();
        //System.out.println("after: " + cl.after());
        //System.out.println("before: " + cl.before());
        //System.out.println("beginPosition: " + cl.beginPosition());
        sb.append("category: " + cl.category() + "\n");
        //sb.append("docID: " + cl.docID());
        //sb.append("endPosition: " + cl.endPosition());
        sb.append("index: " + cl.index() + "\n");
        sb.append("lemma: " + cl.lemma() + "\n");
        sb.append("ner: " + cl.ner() + "\n");
        sb.append("originalText: " + cl.originalText() + "\n");
        sb.append("sentIndex: " + cl.sentIndex() + "\n");
        sb.append("tag: " + cl.tag() + "\n");
        sb.append("toString: " + cl.toString() + "\n");
        sb.append("value: " + cl.value() + "\n");
        sb.append("word: " + cl.word() + "\n");
        //sb.append("keyset: " + cl.keySet() + "\n");
        sb.append("variable: " + cl.getString(LanguageFormatter.VariableAnnotation.class) + "\n");
        sb.append("arg: " + cl.get(LanguageFormatter.RelationArgumentAnnotation.class) + "\n");
        sb.append("\n");
        return sb.toString();
    }

    /** *************************************************************
     */
    public static void testCoreLabel() {

        String input = "Robert is a tall man.";
        System.out.println("RelExtract.testCoreLabel():");
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
        Annotation anno = interp.p.annotate(input);
        List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
        System.out.println("RelExtract.testCoreLabel(): input: " + input);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel cl : tokens) {
                printCoreLabel(cl);
            }
        }
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

    /***************************************************************
     */
    public static void help() {

        System.out.println("Relation Extraction - commands:");
        System.out.println("    -t <rel>     generaTe pattern for rel");
        System.out.println("    -p           Process all relations and search");
        System.out.println("    -r           generate Relation patterns");
        System.out.println("    -o           generate One relation pattern");
        System.out.println("    -x \"text\"    eXtract relations from one sentence");
        System.out.println("    -i           Interactive relation extraction");
        System.out.println("    -h           show this Help message");
    }

    /** *************************************************************
     */
    public static void main(String[] args) {

        //testCoreLabel();
        //testProcess();
        //testStopWordsOnly();
        //HashMap<String,CNF> resultSet = process();
        //test(resultSet);

        //testProcessAndSearch();
        if (args == null || args.length < 1 || args[0].equals("-h"))
            help();
        else if (args[0].equals("-p"))
            processAndSearch();
        else if (args.length > 1 && args[0].equals("-t"))
            testPattern(args[1]);
        else if (args.length > 1 && args[0].equals("-x"))
            sentenceExtract(StringUtil.removeEnclosingQuotes(args[1]));
        else if (args.length == 1 && args[0].equals("-r"))
            generateRelationPatterns();
        else if (args.length == 1 && args[0].equals("-o"))
            testGenerateRelationPattern();
        else if (args.length == 1 && args[0].equals("-i"))
            interactive();
        else
            help();
    }
}
