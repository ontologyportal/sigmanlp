package com.articulate.nlp.semRewrite;

/*
Copyright 2014-2015 IPsoft
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
*/

import com.articulate.nlp.RelExtract;
import com.articulate.sigma.StringUtil;
import com.articulate.sigma.nlg.LanguageFormatter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Dependency;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** *************************************************************
 * pred(arg1,arg2).  
 * arg1 and/or arg2 can be variables which are denoted by a 
 * leading '?'
 * The literal can be negated.  It also has flags for whether it
 * needs to be preserved, rather than consumed, during unification,
 * and whether it has already been successfully bound to another
 * literal during unification.
 */
public class Literal {

    public static boolean debug = false;

    public boolean negated = false;
    public boolean preserve = false;
    public boolean bound = false; // bound clauses in a delete rule get deleted
    public String pred;
    public String arg1;
    public CoreLabel clArg1 = new CoreLabel();
    public String arg2;
    public CoreLabel clArg2 = new CoreLabel();

    // from http://universaldependencies.org/u/dep/index.html
    public static final List<String> dependencyTags = Arrays.asList("acl",
            "advcl", "advmod", "amod", "appos", "aux", "auxpass", "case", "cc",
            "ccomp", "clf", "compound", "conj", "cop", "csubj", "dep",
            "det", "discourse", "dislocated", "dobj", "expl", "fixed", "flat",
            "goeswith", "iobj", "list", "mark", "nmod", "nsubj", "nsubjpass", "nummod",
            "obj", "obl", "orphan", "parataxis", "punct", "reparandum",
            "root", "vocative", "xcomp");

    public static final List<String> augmentTags = Arrays.asList("sumo",
            "sumoInstance", "isCELTclass", "unit", "valueToken", "value",
            "isSubclass", "isInstanceOf", "isSubAttribute", "time", "year",
            "month", "day", "hour", "minute", "second", "names", "attribute");

    /****************************************************************
     */
    public Literal() {
    }

    /****************************************************************
     */
    public Literal(Dependency d) {

        clArg1 = (CoreLabel) d.governor();
        arg1 = clArg1.value() + "-" + clArg1.index();
        clArg2 = (CoreLabel) d.dependent();
        arg2 = clArg2.value() + "-" + clArg2.index();
        pred = (String) d.name();
    }

    /****************************************************************
     * remove commas from numbers and apostrophes in contractions and
     * possessives before parsing
     */
    public Literal(String s) {

        if (StringUtil.emptyString(s))
            return;
        if (debug)System.out.println("Literal() initial string " + s);
        if (s.contains("(,"))
            s = s.replace("(,","(COMMA");
        if (s.contains(",,"))
            s = s.replace(",,",",COMMA");
        s = removeNumberWithComma(s);
        s = removeApostrophe(s);
        //System.out.println("Literal() before parse " + s);
        try {
            Lexer lex = new Lexer(s + ".");
            lex.look();
            Literal lit = Literal.parse(lex, 0);
            if (debug) System.out.println("Literal() parsed: " + lit);
            negated = lit.negated;
            pred = lit.pred;
            //if (!acceptedPredicate(pred))
            //    System.out.println("Error in Literal(): unknown pred in: " + lit);

            arg1 = lit.arg1;
            clArg1 = new CoreLabel();
            int targ1 = tokenNum(arg1);
            clArg1.setIndex(targ1);
            if (targ1 == -1) {
                clArg1.setValue(arg1);
                clArg1.setWord(arg1);
                clArg1.setOriginalText(arg1);
            }
            else {
                clArg1.setValue(tokenOnly(arg1));
                clArg1.setWord(tokenOnly(arg1));
                clArg1.setOriginalText(tokenOnly(arg1));
            }
            if (isVariable(arg1))
                clArg1.set(LanguageFormatter.VariableAnnotation.class,arg1);

            arg2 = lit.arg2;
            clArg2 = new CoreLabel();
            int targ2 = tokenNum(arg2);
            clArg2.setIndex(targ2);
            if (targ2 == -1) {
                clArg2.setValue(arg2);
                clArg2.setWord(arg2);
                clArg2.setOriginalText(arg2);
            }
            else {
                clArg2.setValue(tokenOnly(arg2));
                clArg2.setWord(tokenOnly(arg2));
                clArg2.setOriginalText(tokenOnly(arg2));
            }
            if (isVariable(arg2))
                clArg2.set(LanguageFormatter.VariableAnnotation.class,arg2);
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Literal() " + message);
            ex.printStackTrace();
        }
    }

    /****************************************************************
     */
    public Literal(String p, CoreLabel cl1, CoreLabel cl2) {

        clArg1 = cl1;
        clArg2 = cl2;
        pred = p;
        arg1 = cl1.toString();
        arg2 = cl2.toString();
        negated = false;
        preserve = false;
        bound = false;
    }

    /****************************************************************
     */
    public Literal(Literal l) {

        clArg1 = new CoreLabel(l.clArg1);
        clArg2 = new CoreLabel(l.clArg2);
        pred = l.pred;
        arg1 = l.arg1;
        arg2 = l.arg2;
        negated = l.negated;
        preserve = l.preserve;
        bound = l.bound;
    }

    /****************************************************************
     */
    public void setarg1(String s) {

        arg1 = s;
        clArg1 = new CoreLabel();
        clArg1.setValue(tokenOnly(s));
        clArg1.setIndex(tokenNum(s));
    }

    /****************************************************************
     */
    public void setarg2(String s) {

        arg2 = s;
        clArg2 = new CoreLabel();
        clArg2.setValue(tokenOnly(s));
        clArg2.setIndex(tokenNum(s));
    }

    /****************************************************************
     */
    public static ArrayList<String> stringToLiteralList(String depParse) {

        ArrayList<String> result = new ArrayList<String>();
        Lexer lex = new Lexer(StringUtil.removeEnclosingCharPair(depParse, 1, '[', ']'));
        CNF depcnf = CNF.parseSimple(lex);

        for (Clause c : depcnf.clauses) {
            for (Literal l : c.disjuncts) {
                result.add(l.toString());
            }
        }
        return result;
    }

    /****************************************************************
     * @return the input string if no number that has a comma otherwise return the matched number
     */
    public static String removeNumberWithComma(String s) {

        if (debug) System.out.println("removeNumberWithComma() before processing " + s);
        Pattern p = Pattern.compile("[\\(\\,_](\\d{1,3},\\d\\d\\d[^\\d])");
        Matcher m = p.matcher(s);
        while (m.find()) {
            String res = m.group();
            String newres = res.replaceAll(",","");
            s = s.replace(res,newres);
            //System.out.println("removeNumberWithComma(): " + s);
            m = p.matcher(s);
        }
        if (debug) System.out.println("removeNumberWithComma() after processing " + s);
        return s;
    }

    /****************************************************************
     * @return the input string if no apostrophe found
     */
    public static String removeApostrophe(String s) {

        if (debug) System.out.println("removeApostrophe() before processing " + s);
        Pattern p = Pattern.compile("[\\(\\,]'[^\\)\\,]+");
        Matcher m = p.matcher(s);
        while (m.find()) {
            String res = m.group();
            String newres = res.replaceAll("'","");
            s = s.replace(res,newres);
            //System.out.println("removeNumberWithComma(): " + s);
            m = p.matcher(s);
        }
        if (debug) System.out.println("removeApostrophe() after processing " + s);
        return s;
    }

    /** ***************************************************************
     */
    public static boolean isVariable(String s) {

        if (StringUtil.emptyString(s))
            return false;
        if (s.startsWith("?") || s.endsWith("*"))
            return true;
        return false;
    }

    /** ***************************************************************
     * a variable in the form ?var
     */
    public static boolean isFreeVariable(String s) {

        if (StringUtil.emptyString(s))
            return false;
        if (s.startsWith("?"))
            return true;
        return false;
    }

    /** ***************************************************************
     * a variable in the from var*
     */
    public static boolean isWordVariable(String s) {

        if (StringUtil.emptyString(s))
            return false;
        if (s.endsWith("*"))
            return true;
        return false;
    }

    /****************************************************************
     */
    private static boolean acceptedPredicate(String p) {

        return (dependencyTags.contains(p) || augmentTags.contains(p) ||
            p.matches("(conj|nmod|prep_)\\:?\\w+"));
    }

    /** ***************************************************************
     * @return the token minus the token number suffix, ? variable prefix
     * and/or * variable suffix.  Variables with a '*' suffix are assumed
     * not to have a token number
     */
    public static String tokenOnly(String s) {

        if (StringUtil.emptyString(s))
            return s;
        if (s.endsWith("*"))
            return s.substring(0,s.length()-1);
        Pattern p = Pattern.compile("\\??(.+)-(\\d+)");
        Matcher m = p.matcher(s);
        if (m.matches()) {
            return m.group(1);
        }
        return s;
    }

    /** ***************************************************************
     * @return true if the string has a token number suffix
     */
    public static boolean isToken(String s) {

        if (StringUtil.emptyString(s))
            return false;
        Pattern p = Pattern.compile(".+-(\\d+)");
        Matcher m = p.matcher(s);
        if (m.matches())
            return true;
        else
            return false;
    }

    /** ***************************************************************
     * @return the token number of the constant or -1 if it is a variable
     * such as ?foo or foo* or a token that doesn't have a token number
     */
    public static int tokenNum(String s) {

        if (StringUtil.emptyString(s))
            return -1;
        Pattern p = Pattern.compile(".+-(\\d+)");
        Matcher m = p.matcher(s);
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /** ***************************************************************
     */
    public String toString() {
        
        StringBuffer sb = new StringBuffer();
        if (bound)
            sb.append("X");
        if (negated)
            sb.append("~");
        if (preserve)
            sb.append("+");
        sb.append(pred + "(" + arg1 + "," + arg2 + ")");
        return sb.toString();
    }

    /** ***************************************************************
     */
    public String toLabels() {

        StringBuffer sb = new StringBuffer();
        if (bound)
            sb.append("X");
        if (negated)
            sb.append("~");
        if (preserve)
            sb.append("+");
        String firstArg = arg1;
        if (!StringUtil.emptyString(clArg1.getString(LanguageFormatter.VariableAnnotation.class)))
            firstArg = clArg1.getString(LanguageFormatter.VariableAnnotation.class);
        String secondArg = arg2;
        if (!StringUtil.emptyString(clArg2.getString(LanguageFormatter.VariableAnnotation.class)))
            secondArg = clArg2.getString(LanguageFormatter.VariableAnnotation.class);
        sb.append(pred + "(" + firstArg + "," + secondArg + ")");
        RelExtract.printCoreLabel(clArg1);
        RelExtract.printCoreLabel(clArg2);

        return sb.toString();
    }
    
    /** ***************************************************************
     */
    public Literal deepCopy() {
        
        Literal newc = new Literal();
        newc.negated = negated;
        newc.preserve = preserve;
        newc.bound = bound;
        newc.pred = pred;
        newc.arg1 = arg1;
        newc.arg2 = arg2;
        return newc;
    }
    
    /** ***************************************************************
     */
    @Override
    public boolean equals(Object o) {
    
        if (!(o instanceof Literal))
            return false;
        Literal c = (Literal) o;
        if (negated != c.negated)
            return false;
        if (!pred.equals(c.pred))
            return false;
        if (!arg1.equals(c.arg1))
            return false;
        if (!arg2.equals(c.arg2))
            return false;
        return true;
    }
    
    /** ***************************************************************
     * @return true if the clause does not contain any variables
     */
    public boolean isGround() {
        
        if (!arg1.startsWith("?") && !arg2.startsWith("?"))
            return true;
        else
            return false;
    }
    
    /** *************************************************************
     * If the tokens in the literal are derived from words parsed
     * from the Stanford dependency parser, and therefore in the form
     * word-xx, where xx are digits, prepend a '?' to signify that
     * it's a variable.
     */
    public void preProcessQuestionWords(List<String> qwords) {
        
        for (String s: qwords) {
            System.out.println("INFO in Literal.preProcessQuestionWords(): " + s + " " + arg1 + " " + arg2);
            if (arg1.toLowerCase().matches(s.toLowerCase() + "-\\d+"))
                arg1 = "?" + arg1;
            if (arg2.toLowerCase().matches(s.toLowerCase() + "-\\d+"))
                arg2 = "?" + arg2;
        }
    }
    
    /** ***************************************************************
     * Apply variable substitutions to a literal  
     */
    public void applyBindingSelf(HashMap<String,String> bindings) {
        
        if (arg1.startsWith("?")) {
            if (bindings.containsKey(arg1))
                arg1 = bindings.get(arg1);
        }
        if (arg2.startsWith("?")) {
            if (bindings.containsKey(arg2))
                arg2 = bindings.get(arg2);
        }
    }
    
    /** ***************************************************************
     * @return a literal after applying variable substitutions to a literal  
     */
    public Literal applyBindings(HashMap<String,String> bindings) {
        
        //System.out.println("INFO in Literal.applyBindings(): this: " + this);
        //System.out.println("INFO in Literal.applyBindings(): bindings: " + bindings);
        Literal c = new Literal();
        c.pred = pred;
        c.negated = negated;
        c.preserve = preserve;
        if (StringUtil.emptyString(arg1) || StringUtil.emptyString(arg2)) {
            System.out.println("Error in Literal.applyBindings(): Empty argument(s): " + this);
            c.arg1 = arg1;
            c.arg2 = arg2;
            return c;
        }
        if (arg1.startsWith("?")) {
            if (bindings.containsKey(arg1))
                c.arg1 = bindings.get(arg1);
            else
                c.arg1 = arg1;
        }
        else
            c.arg1 = arg1;
        if (arg2.startsWith("?")) {
            if (bindings.containsKey(arg2))
                c.arg2 = bindings.get(arg2);
            else
                c.arg2 = arg2;
        }
        else
            c.arg2 = arg2;
        //System.out.println("INFO in Literal.applyBindings(): returning this: " + c);
        return c;
    }
    
    /** ***************************************************************
     * @return a boolean indicating whether a variable occurs in a literal.
     * This is a degenerate case of general case of occurs check during
     * unification, since we have no functions and argument lists are 
     * always of length 2.
     */
    private static boolean occursCheck(String t, Literal c) {
        
        if (t.equals(c.arg1) || t.equals(c.arg2))
            return true;
        else
            return false;
    }
    
    /** ***************************************************************
     * @return false if there are wildcards and they don't match (or 
     * there's an error) and true if there are no wildcards.  Match
     * is case-insensitive.  Wildcards only allow for ignoring the
     * word-number suffix as in wildcard-5 would match wildcard*.
     */
    private static boolean wildcardMatch(String t1, String t2) {
        
        //System.out.println("INFO in Literal.wildcardMatch(): attempting to match: " + t1 + " " + t2);
        String s1 = t1;
        String s2 = t2;
        if (!t1.contains("*") && !t2.contains("*")) // no wildcards case should fall through
            return true;
        if (t1.contains("*") && t2.contains("*")) {
            System.out.println("Error in Literal.wildcardMatch(): both arguments have wildcards: " + t1 + " " + t2);
            return false;
        }
        if (t2.contains("*")) {
            s1 = t2;
            s2 = t1;
        }
        if (s1.indexOf('*') > -1 && s2.indexOf('-') > -1) {  // when wildcard, both have to be matching variables
                                                             // except for suffix
            if (!s1.substring(0,s1.lastIndexOf('*')).equalsIgnoreCase(s2.substring(0,s2.lastIndexOf('-'))))
                return false;
        }
        return true;
    }
        
    /** ***************************************************************
     * Unify all terms in term1 with the corresponding terms in term2 with a
     * common substitution. Note that unlike general unification, we have
     * a fixed argument list of 2.   
     * @return the set of substitutions with the variable as the key and
     * the binding as the value in the HashMap.
     */
    public HashMap<String,String> mguTermList(Literal l2) {

        if (debug) System.out.println("INFO in Literal.mguTermList(): attempting to unify " + this + " and " + l2);
        HashMap<String,String> subst = new HashMap<String,String>();
        
        if (!pred.equals(l2.pred)) 
            return null;        
        for (int arg = 1; arg < 3; arg++) {           
            String t1 = arg1; // Pop the first term pair to unify off the lists            
            String t2 = l2.arg1; // (removes and returns the denoted elements).
            if (arg == 2) {
                t1 = arg2;            
                t2 = l2.arg2;
            }
            if (debug) System.out.println("INFO in Literal.mguTermList(): attempting to unify arguments " + t1 + " and " + t2);
            if (t1.startsWith("?")) {
                if (debug) System.out.println("INFO in Literal.mguTermList(): here 1");
                if (t1.equals(t2))
                    // We could always test this upfront, but that would
                    // require an expensive check every time. 
                    // We descend recursively anyway, so we only check this on
                    // the terminal case.  
                    continue;
                if (occursCheck(t1,l2))
                    return null;
                // We now create a new substitution that binds t2 to t1, and
                // apply it to the remaining unification problem. We know
                // that every variable will only ever be bound once, because
                // we eliminate all occurrences of it in this step - remember
                // that by the failed occurs-check, t2 cannot contain t1.
                HashMap<String,String> newBinding = new HashMap<String,String>();
                if (!wildcardMatch(t1,t2)) 
                    return null;
                newBinding.put(t1,t2);                
                applyBindingSelf(newBinding);
                l2 = l2.applyBindings(newBinding);
                subst.put(t1, t2);
            }
            else if (t2.startsWith("?")) {
                if (debug) System.out.println("INFO in Literal.mguTermList(): here 2");
                // Symmetric case - We know that t1!=t2, so we can drop this check
                if (occursCheck(t2, this))
                    return null;
                HashMap<String,String> newBinding = new HashMap<String,String>();
                if (!wildcardMatch(t1,t2)) 
                    return null;
                newBinding.put(t2, t1);          
                applyBindingSelf(newBinding);
                l2 = l2.applyBindings(newBinding);
                subst.put(t2, t1);
            }
            else {
                if (debug) System.out.println("INFO in Literal.mguTermList(): t1 " + t1 + " t2 " + t2);
                if (!t1.equals(t2)) {
                    // TODO: add test for common parent of SUMO terms
                    // if (kb.containsTerm(t1) && kb.containsTerm(t2)) {
                    //    kbCache.findCommonParent(t1,t2)
                    // }
                    if (t1.indexOf('*') > -1 && t2.indexOf('-') > -1) {
                        if (!t1.substring(0,t1.lastIndexOf('*')).equalsIgnoreCase(t2.substring(0,t2.lastIndexOf('-'))))
                            return null;
                    }
                    else if (t2.indexOf('*') > -1 && t1.indexOf('-') > -1) {
                        if (!t2.substring(0,t2.lastIndexOf('*')).equalsIgnoreCase(t1.substring(0,t1.lastIndexOf('-'))))
                            return null;
                    }
                    else
                        return null;
                }
            }
        }
        if (debug) System.out.println("INFO in Literal.mguTermList(): subst on exit: " + subst);
        return subst;
    }
    
    /** ***************************************************************
     * @param lex is a Lexer which has been initialized with the 
     * textual version of the Literal
     * @param startLine is the line in the text file at which the
     * literal appears.  If it is in a large rule the start line
     * could be different than the actual line of text for the literal.
     * If the literal is just from a string rather than directly from
     * a text file then the startLine will be 0.
     * @return a Literal corresponding to the input text passed to the
     * Lexer.  Note that the predicate in this literal must already 
     * have been read
     */
    public static Literal parse(Lexer lex, int startLine) {

        if (debug) System.out.println("INFO in Literal.parse(0): " + lex.line);
        String errStart = "Parsing error in " + RuleSet.filename;
        String errStr;
        Literal cl = new Literal();
        try {
            if (debug) System.out.println("INFO in Literal.parse(1): " + lex.look());
            if (lex.testTok(Lexer.Plus)) {
                cl.preserve = true;
                lex.next();
            }
            if (debug) System.out.println("INFO in Literal.parse(2): " + lex.look());
            cl.pred = lex.next();
            /* if (!acceptedPredicate(cl.pred)) {
                System.out.println("Error in Literal.parse(): unknown pred '" + cl.pred + "' in: " + cl);
                errStr = (errStart + ": bad predicate '" + lex.look() + "' near line " + startLine + " on input " + lex.line);
                System.out.println(errStr);
                //throw new ParseException(errStr, startLine);
            } */
            if (debug) System.out.println("INFO in Literal.parse(3): " + lex.look());
            if (!lex.testTok(Lexer.OpenPar)) {
                errStr = (errStart + ": Invalid token '" + lex.look() + "' near line " + startLine + " on input " + lex.line);
                throw new ParseException(errStr, startLine);
            }
            lex.next();
            if (debug) System.out.println("INFO in Literal.parse(4): " + lex.look());
            cl.arg1 = lex.next();
            if (debug) System.out.println("INFO in Literal.parse(5): " + lex.look());
            if (!lex.testTok(Lexer.Comma)) {
                errStr = (errStart + ": Invalid token '" + lex.look() + "' near line " + startLine + " on input " + lex.line);
                throw new ParseException(errStr, startLine);
            }
            lex.next();
            if (debug) System.out.println("INFO in Literal.parse(6): " + lex.look());
            cl.arg2 = lex.next();
            if (debug) System.out.println("INFO in Literal.parse(7): " + lex.look());
            if (!lex.testTok(Lexer.ClosePar)) {
                errStr = (errStart + ": Invalid token '" + lex.look() + "' near line " + startLine + " on input " + lex.line);
                throw new ParseException(errStr, startLine);
            } 
            lex.next();
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Literal.parse(8) " + message);
            ex.printStackTrace();
        }    
        //System.out.println("INFO in Literal.parse(9): returning " + cl);
        return cl;
    }

    /** *************************************************************
     * A test method for getArg
     */
    public static void testGetArg() {

        String s1 = "sumo(Human,Mary-1)";
        String s2 = "sumo(?O,Mary-1)";
        try {
            Literal l = new Literal(s1);
            System.out.println("INFO in Literal.testGetArg(): " + l);
            System.out.println("INFO in Literal.testGetArg(): " + l.arg1);
            System.out.println("INFO in Literal.testGetArg(): " + Literal.tokenNum(l.arg1));
            System.out.println("INFO in Literal.testGetArg(): " + l.arg2);
            System.out.println("INFO in Literal.testGetArg(): " + Literal.tokenNum(l.arg2));
            l = new Literal(s2);
            System.out.println("INFO in Literal.testGetArg(): " + l);
            System.out.println("INFO in Literal.testGetArg(): " + l.arg1);
            System.out.println("INFO in Literal.testGetArg(): " + Literal.tokenNum(l.arg1));
            System.out.println("INFO in Literal.testGetArg(): " + l.arg2);
            System.out.println("INFO in Literal.testGetArg(): " + Literal.tokenNum(l.arg2));
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Clause.parse() " + message);
            ex.printStackTrace();
        }
    }

    /** *************************************************************
     * A test method for unification
     */
    public static void testUnify() {
        
        String s1 = "sumo(Human,Mary-1)";
        String s2 = "sumo(?O,Mary-1)";
        Literal c1 = null;
        Literal c2 = null;
        try {
            Lexer lex = new Lexer(s1);
            lex.look();
            c1 = Literal.parse(lex, 0);
            lex.look();
            lex = new Lexer(s2);
            c2 = Literal.parse(lex, 0);
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Clause.parse() " + message);
            ex.printStackTrace();
        }   
        System.out.println("INFO in Literal.testUnify(): " + c1.mguTermList(c2));
        System.out.println("INFO in Literal.testUnify(): " + c2.mguTermList(c1));
    }
    
    /** *************************************************************
     * A test method for wildcard unification
     */
    public static void testRegexUnify() {
        
        String s1 = "pobj(at-1,Mary-1).";
        String s2 = "pobj(at*,?M).";
        String s3 = "pobj(boo-3,?M).";
        System.out.println("INFO in Clause.testRegexUnify(): attempting parses ");
        Literal c1 = null;
        Literal c2 = null;
        Literal c3 = null;
        try {
            Lexer lex = new Lexer(s1);
            lex.look();
            c1 = Literal.parse(lex, 0);
            System.out.println("INFO in Clause.testRegexUnify(): parsed " + c1);
            lex.look();
            lex = new Lexer(s2);
            c2 = Literal.parse(lex, 0);
            System.out.println("INFO in Clause.testRegexUnify(): parsed " + c2);
            lex = new Lexer(s3);
            c3 = Literal.parse(lex, 0);
            System.out.println("INFO in Clause.testRegexUnify(): parsed " + c3);
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Literal.parse() " + message);
            ex.printStackTrace();
        }   
        System.out.println("INFO in Literal.testRegexUnify(): " + c1.mguTermList(c2));
        System.out.println("INFO in Literal.testRegexUnify(): " + c2.mguTermList(c1));
        System.out.println("INFO in Literal.testRegexUnify(): should fail: " + c2.mguTermList(c3));
    }
    
    /** *************************************************************
     * A test method for parsing a Literal
     */
    public static void testParse() {

        debug = true;
        try {
            String input = "+det(bank-2, The-1).";
            Lexer lex = new Lexer(input);
            lex.look();
            input = "sumo(PsychologicalAttribute,loves-3)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "valueToken(3000000,3000000-5)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "conj:and(killed-2,killed-2)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "conj:and($_200,000-2,$_60,000-5)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "year(time-1,1994)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "nummod(Palestinians-22,300-21)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
            input = "sumo(Attribute,'s-33),";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): parse: " + new Literal(input));
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Literal.parse() " + message);
            ex.printStackTrace();
        }   
    }

    /** *************************************************************
     * A test method for parsing a Literal
     */
    public static void testRemoveNumberWithComma() {

        debug = true;
        try {
            String input = "+det(bank-2, The-1).";
            Lexer lex = new Lexer(input);
            lex.look();
            input = "sumo(PsychologicalAttribute,loves-3)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "valueToken(3000000,3000000-5)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "conj:and(killed-2,killed-2)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "conj:and($_200,000-2,$_60,000-5)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "year(time-1,1994)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "nummod(Palestinians-22,300-21)";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
            input = "sumo(Attribute,'s-33),";
            System.out.println("Literal.testParse(): input: " + input);
            System.out.println("Literal.testParse(): result: " + removeNumberWithComma(input));
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Literal.parse() " + message);
            ex.printStackTrace();
        }
    }

    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {

        //testGetArg();
        testParse();
        //testRemoveNumberWithComma();
        //testUnify();
        //testRegexUnify();
    }
}
