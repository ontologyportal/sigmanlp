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

import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.google.common.collect.Lists;

import java.util.*;

/**
 * <b>Conjunctive Normal Form</b>
 */
public class CNF implements Comparable {

    public ArrayList<Clause> clauses = new ArrayList<Clause>();
    public static boolean debug = false;
    public static int varnum = 0; // to ensure unique variable renaming

    /** ***************************************************************
     */
    public class Subst implements Comparable {

        public HashMap<String,String> substMap = new HashMap<>();

        public String toString() {
            return substMap.toString();
        }

        /** ***************************************************************
         * Overridden equals method.
         */
        public boolean equals(Object obj) {

            //System.out.println("Subst.equals(): " + this + " : " + obj);
            if (!(obj instanceof Subst))
                return false;
            Subst sub = (Subst) obj;
            if (obj == this)
                return true;
            return this.compareTo(sub) == 0;
        }

        /** ***************************************************************
         */
        public int hashCode() {
            return toString().hashCode();
        }

        /** ***************************************************************
         * sort clauses alphabetically then compare as strings
         */
        public int lexicalOrder(Subst sub) {

            TreeSet<String> thisKeys = new TreeSet<>();
            TreeSet<String> subKeys = new TreeSet<>();
            for (String s : sub.substMap.keySet())
                subKeys.add(s);
            for (String s : this.substMap.keySet())
                thisKeys.add(s);
            int keyComp = thisKeys.toString().compareTo(subKeys.toString());
            if (keyComp != 0)
                return keyComp;

            TreeSet<String> thisValues = new TreeSet<>();
            TreeSet<String> subValues = new TreeSet<>();
            for (String s : sub.substMap.values())
                subValues.add(s);
            for (String s : this.substMap.values())
                thisValues.add(s);
            int valueComp = thisValues.toString().compareTo(subValues.toString());
                return valueComp;
        }

        /** ***************************************************************
         */
        public int compareTo(Object o) {

            //System.out.println("Subst.compareTo(): " + this + " : " + o);
            if (!(o instanceof Subst))
                return 0;
            Subst sub = (Subst) o;
            if (this.substMap.keySet().size() < sub.substMap.keySet().size())
                return -1;
            else if (this.substMap.keySet().size() > sub.substMap.keySet().size())
                return 1;
            else {
               return this.lexicalOrder(sub);
            }
        }
    }

    /** ***************************************************************
     */
    public CNF() {
    }

    /** ***************************************************************
     */
    public CNF(String s) {

        Lexer lex = new Lexer(s);
        clauses = parseSimple(lex).clauses;
    }

    /** ***************************************************************
     */
    public static CNF fromLiterals(Collection<Literal> lits) {

        CNF result = new CNF();
        for (Literal lit : lits) {
            Clause c = new Clause();
            c.disjuncts.add(lit);
            result.clauses.add(c);
        }
        return result;
    }

    /** ***************************************************************
     * Get all predicate names that aren't procedure calls or generated
     * automatically to augment the dependency parse tags
     */
    public HashSet<String> getPreds() {

        HashSet<String> result = new HashSet<>();
        for (Clause c : clauses) {
            for (Literal l : c.disjuncts) {
                if (!Procedures.isProcPred(l.pred) && !Interpreter.addedTags.contains(l.pred))
                    result.add(l.pred);
            }
        }
        //System.out.println("CNF.getPreds(): result: " + result);
        return result;
    }

    /** ***************************************************************
     * Get all the constants.  If it's a word variable save just the
     * content and remove the asterisk suffix.  If it's a token, remove
     * the token number suffix. For use in pruning application rules, this
     * routine assumes that procedures and sumo clauses are not considered.
     */
    public HashSet<String> getTerms() {

        HashSet<String> result = new HashSet<>();
        for (Clause c : clauses) {
            for (Literal l : c.disjuncts) {
                if (!Procedures.isProcPred(l.pred) && !Interpreter.addedTags.contains(l.pred)) {
                    if (!Literal.isVariable(l.arg1)) {
                        if (Literal.isToken(l.arg1))
                            result.add(Literal.tokenOnly(l.arg1));
                        else
                            result.add(l.arg1);
                    }
                    if (Literal.isWordVariable(l.arg1))
                        result.add(Literal.tokenOnly(l.arg1));

                    if (!Literal.isVariable(l.arg2)) {
                        if (Literal.isToken(l.arg2))
                            result.add(Literal.tokenOnly(l.arg2));
                        else
                            result.add(l.arg2);
                    }
                    if (Literal.isWordVariable(l.arg2))
                        result.add(Literal.tokenOnly(l.arg2));
                }
            }
        }
        //System.out.println("CNF.getPreds(): result: " + result);
        return result;
    }

    /** ***************************************************************
     * append a literal to this CNF
     */
    public void prepend(Literal lit) {

        Clause c = new Clause();
        c.disjuncts.add(lit);
        clauses.add(0,c);
    }

    /** ***************************************************************
     * append a literal to this CNF
     */
    public void append(Literal lit) {

        Clause c = new Clause();
        c.disjuncts.add(lit);
        clauses.add(c);
    }

    /** ***************************************************************
     * append all literal to this CNF
     */
    public void appendAll(HashSet<Literal> lits) {

        for (Literal lit : lits)
            append(lit);
    }

    /** ***************************************************************
     * append all literal to this CNF
     */
    public void appendAll(CNF cnfnew) {

        for (Clause c : cnfnew.clauses) {
            for (Literal lit : c.disjuncts)
                this.append(lit);
        }
    }

    /** ***************************************************************
     * rename all variables in the CNF
     */
    public CNF renameVariables() {

        HashMap<String,String> varmap = new HashMap<>();
        CNF result = new CNF();
        for (Clause c : clauses) {
            Clause newC = new Clause();
            for (Literal l : c.disjuncts) {
                Literal newL = new Literal();
                newL.pred = l.pred;
                if (Literal.isVariable(l.arg1)) {
                    if (varmap.containsKey(l.arg1))
                        newL.arg1 = varmap.get(l.arg1);
                    else {
                        newL.arg1 = "?VAR" + Integer.toString(varnum++);
                        varmap.put(l.arg1,newL.arg1);
                    }
                }
                else
                    newL.arg1 = l.arg1;

                if (Literal.isVariable(l.arg2)) {
                    if (varmap.containsKey(l.arg2))
                        newL.arg2 = varmap.get(l.arg2);
                    else {
                        newL.arg2 = "?VAR" + Integer.toString(varnum++);
                        varmap.put(l.arg2,newL.arg2);
                    }
                }
                else
                    newL.arg2 = l.arg2;

                newC.disjuncts.add(newL);
            }
            result.clauses.add(newC);
        }
        return result;
    }

    /** ***************************************************************
     */
    private HashSet<Literal> getSUMOLiterals(CNF cnf) {

        HashSet<Literal> result = new HashSet<>();
        for (Clause clause1 : cnf.clauses) {
            for (Literal d1 : clause1.disjuncts) {
                if (d1.pred.equals("sumo"))
                    result.add(d1);
            }
        }
        return result;
    }

    /** ***************************************************************
     * More general sumo terms are "smaller", fewer literals are "smaller".
     * If # of literals is equal, no SUMO terms or identical, or terms at
     * same level, return 0
     */
    private int compareSUMOLiterals(CNF cnf) {

        System.out.println("CNF.compareSUMOLiterals(): this: " + this);
        System.out.println("CNF.compareSUMOLiterals(): cnf: " + cnf);
        KB kb = KBmanager.getMgr().getKB("SUMO");
        HashSet<Literal> thisSUMO = getSUMOLiterals(this);
        HashSet<Literal> cnfSUMO = getSUMOLiterals(cnf);
        if (thisSUMO.size() == 0 && cnfSUMO.size() == 0)
            return 0;
        if (thisSUMO.size() > cnfSUMO.size())
            return 1;
        if (thisSUMO.size() < cnfSUMO.size())
            return -1;
        int thisScore = 0;
        int cnfScore = 0;
        for (Literal l : thisSUMO) {
            int depth = kb.termDepth(l.arg1);
            //System.out.println("CNF.compareSUMOLiterals(): depth of: " + l.arg1 + "=" + depth);
            thisScore = thisScore + depth;
        }
        System.out.println("CNF.compareSUMOLiterals(): this score: " + thisScore);

        for (Literal l : cnfSUMO) {
            int depth = kb.termDepth(l.arg1);
            //System.out.println("CNF.compareSUMOLiterals(): depth of: " + l.arg1 + "=" + depth);
            cnfScore = cnfScore + depth;
        }
        System.out.println("CNF.compareSUMOLiterals(): cnf score: " + cnfScore);

        if (thisScore > cnfScore) {
            //System.out.println("CNF.compareSUMOLiterals(): this is 'bigger'");
            return 1;
        }
        if (thisScore < cnfScore) {
            //System.out.println("CNF.compareSUMOLiterals(): cnf is 'bigger'");
            return -1;
        }
        return 0;
    }

    /** ***************************************************************
     * sort clauses alphabetically then compare as strings
     */
    public int lexicalOrder(CNF cnf) {

        TreeSet<String> thisSet = new TreeSet<>();
        TreeSet<String> cnfSet = new TreeSet<>();
        for (Clause clause1 : cnf.clauses) {
            for (Literal d1 : clause1.disjuncts)
                cnfSet.add(d1.toString());
        }
        for (Clause clause1 : this.clauses) {
            for (Literal d1 : clause1.disjuncts)
                thisSet.add(d1.toString());
        }
        return thisSet.toString().compareTo(cnfSet.toString());
    }

    /** ***************************************************************
     * fewer clauses are smaller, more general sumo terms are smaller
     * and if those conditions aren't different, just choose lexical
     * order
     */
    public int compareTo(Object o) {

        if (!(o instanceof CNF))
            return 0;
        CNF cnf = (CNF) o;
        if (this.equals(cnf))
            return 0;
        if (this.clauses.size() < cnf.clauses.size())
            return -1;
        else if (this.clauses.size() > cnf.clauses.size())
            return 1;
        else {
            //System.out.println("CNF.compareTo() equal length clauses");
            int SUMOlitVal = this.compareSUMOLiterals(cnf);
            if (SUMOlitVal == 0)
                return this.lexicalOrder(cnf);
            else
                return SUMOlitVal;
        }
    }

    /** ***************************************************************
     */
    public String toString() {

        StringBuffer sb = new StringBuffer();
        //sb.append("[");
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            sb.append(d.toString());
            if (clauses.size() > 1 && i < clauses.size() - 1)
                sb.append(", ");
        }
        //sb.append("]");
        return sb.toString();
    }

    /** ***************************************************************
     * When a CNF is a simple list of literals, return them as such.
     * Otherwise return null;
     */
    public List<Literal> toLiterals() {

        List<Literal> result = new ArrayList<>();
        for (Clause clause : clauses)   {
            if (clause.disjuncts.size() > 1) {
                System.out.println("Error in CNF.toLiterals(): disjunctive CNF");
                return null;
            }
            for (Literal l : clause.disjuncts)
                result.add(l);
        }
        return result;
    }

    /** ***************************************************************
     */
    public static CNF fromListString(List<String> liststring) {

        System.out.println("CNF.fromListString(): " + liststring);
        CNF cnf = new CNF();
        for (String s : liststring)   {
            Literal l = new Literal(s);
            cnf.append(l);
        }
        return cnf;
    }

    /** ***************************************************************
     */
    public List<String> toListString() {

        List<String> retString = Lists.newArrayList();
        for (Clause clause : clauses)   {
            retString.add(clause.toString());
        }

        return retString;
    }

    /** ***************************************************************
     */
    @Override
    public boolean equals(Object o) {

        if (!(o instanceof CNF))
            return false;
        CNF cnf = (CNF) o;
        //System.out.println("INFO in CNF.equals(): Checking " + cnf + " against " + this);
        if (clauses.size() != cnf.clauses.size())
            return false;
        for (int i = 0; i < clauses.size(); i++) {
            //System.out.println("INFO in CNF.equals(): checking disjunct " + clauses.get(i) +
            //        " " + cnf.clauses.get(i));
            if (!clauses.get(i).equals(cnf.clauses.get(i)))
                return false;
        }
        //System.out.println("INFO in CNF.equals(): true!");
        return true;
    }

    /** ***************************************************************
     */
    public static CNF valueOf(String s) {

        Lexer lex = new Lexer(s);
        return CNF.parseSimple(lex);
    }

    /** ***************************************************************
     * @return a CNF object formed from a single (Literal) disjunct
     */
    public static CNF valueOf(Literal d) {

        CNF result = new CNF();
        Clause c = new Clause();
        c.disjuncts.add(d);
        result.clauses.add(c);
        return result;
    }

    /** ***************************************************************
     * @return a copy of CNF object minus the given disjunct
     */
    public CNF rest(Literal d) {

        CNF result = new CNF();
        for (Clause c : clauses) {
            Clause newClause = new Clause();
            for (Literal d2 : c.disjuncts) {
                if (!d2.equals(d))
                    newClause.disjuncts.add(d2);
            }
            if (newClause.disjuncts.size() > 0)
                result.clauses.add(newClause);
        }
        return result;
    }

    /** ***************************************************************
     */
    public CNF deepCopy() {

        CNF cnfnew = new CNF();
        for (int i = 0; i < clauses.size(); i++) {
            cnfnew.clauses.add(clauses.get(i).deepCopy());
        }
        return cnfnew;
    }

    /** *************************************************************
     */
    public boolean empty() {

        return clauses.size() == 0;
    }

    /** *************************************************************
     */
    public void preProcessQuestionWords(List<String> qwords) {

        for (Clause d: clauses)
            d.preProcessQuestionWords(qwords);
    }

    /** ***************************************************************
     */
    public void clearBound() {

        //System.out.println("INFO in CNF.clearBound(): before " + this);
        ArrayList<Clause> newclauses = new ArrayList<Clause>();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            d.clearBound();
            if (!d.empty())
                newclauses.add(d);
        }
        //System.out.println("INFO in CNF.clearBound(): after " + this);
    }

    /** ***************************************************************
     */
    public void clearPreserve() {

        //System.out.println("INFO in CNF.clearBound(): before " + this);
        ArrayList<Clause> newclauses = new ArrayList<Clause>();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            d.clearPreserve();
            if (!d.empty())
                newclauses.add(d);
        }
        //System.out.println("INFO in CNF.clearBound(): after " + this);
    }

    /** ***************************************************************
     */
    public void merge(CNF cnf) {

        //System.out.println("INFO in CNF.merge(): before " + this + "\narg: " + cnf);
        for (int i = 0; i < cnf.clauses.size(); i++)
            if (!clauses.contains(cnf.clauses.get(i)))
                clauses.add(cnf.clauses.get(i));
        //System.out.println("INFO in CNF.merge(): after " + this);
    }

    /** ***************************************************************
     */
    public CNF removeBound() {

        //System.out.println("INFO in CNF.removeBound(): before " + this);
        CNF newCNF = new CNF();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            d.removeBound();
            if (!d.empty())
                newCNF.clauses.add(d);
        }
        //System.out.println("INFO in CNF.removeBound(): after " + newCNF);
        return newCNF;
    }

    /** ***************************************************************
     * Only positive clauses, no disjuncts, which is the output format
     * of the Stanford Dependency Parser
     */
    public static CNF parseSimple(Lexer lex) {

        CNF cnf = new CNF();
        String token = "";
        try {
            ArrayList<String> tokens = new ArrayList<String>();
            tokens.add(Lexer.EOFToken);
            tokens.add(Lexer.ClosePar);
            tokens.add(Lexer.FullStop);
            while (!lex.testTok(tokens)) {
                Clause d = new Clause();
                Literal c = Literal.parse(lex, 0);
                //if (debug) System.out.println("INFO in CNF.parseSimple(): " + c);
                d.disjuncts.add(c);
                cnf.clauses.add(d);
                if (lex.testTok(Lexer.Comma))
                    lex.next();
                else if (lex.testTok(Lexer.ClosePar)) {
                    lex.next();
                    //if (debug) System.out.println("INFO in CNF.parseSimple(): final token: " + lex.look());
                    if (!lex.testTok(Lexer.FullStop) && !lex.look().equals("*EOF*"))  // allow EOF as well as period
                        System.out.println("Error in CNF.parseSimple(): Bad token: " + lex.look());
                }
                else
                    if (!lex.testTok(Lexer.FullStop) && !lex.look().equals("*EOF*")) // allow EOF as well as period
                        System.out.println("Error in CNF.parseSimple(): Bad token: " + lex.look());
            }
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in CNF.parse(): " + message);
            ex.printStackTrace();
        }
        //if (debug) System.out.println("INFO in CNF.parseSimple(): returning: " + cnf);
        return cnf;
    }

    /** ***************************************************************
     * Apply variable substitutions to this set of clauses
     */
    public CNF applyBindings(HashMap<String,String> bindings) {

        CNF cnf = new CNF();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            Clause dnew = new Clause();
            for (int j = 0; j < d.disjuncts.size(); j++) {
                Literal c = d.disjuncts.get(j);
                //System.out.println("INFO in CNF.applyBindings(): 1 " + c);
                Literal c2 = c.applyBindings(bindings);
                //System.out.println("INFO in CNF.applyBindings(): 1.5 " + c2);
                dnew.disjuncts.add(c2);
                //System.out.println("INFO in CNF.applyBindings(): 2 " + dnew);
            }
            //System.out.println("INFO in CNF.applyBindings(): 3 " + dnew);
            cnf.clauses.add(dnew);
        }
        return cnf;
    }

    /** ***************************************************************
     * Copy bound flags to this set of clauses
     */
    public void copyBoundFlags(CNF cnf) {

        for (int i = 0; i < clauses.size(); i++)
            clauses.get(i).copyBoundFlags(cnf.clauses.get(i));
    }

    /** ***************************************************************
     * Test a disjunct from a rule against a sentence.  It must succeed
     * for the rule to be bound.  If a binding is found, it can exit
     * without trying all the options.
     */
    private HashMap<String,String> unifyDisjunct(Clause d1, CNF cnf2, CNF cnf1, HashMap<String,String> bindings) {

        if (debug) System.out.println("INFO in CNF.unifyDisjunct(): checking " + d1 + " against " + cnf2);
        HashMap<String,String> result = new HashMap<String,String>();
        for (Clause d2 : cnf2.clauses) {  // sentence
            if (debug) System.out.println("INFO in CNF.unifyDisjunct(): checking " + d1 + " against " + d2);
            HashMap<String,String> bindings2 = d2.unify(d1);
            if (debug) System.out.println("INFO in CNF.unifyDisjunct(): d1 " + d1 + " d2 " + d2);
            if (debug) System.out.println("INFO in CNF.unifyDisjunct(): checked " + d1 + " against " + d2);
            if (debug) System.out.println("INFO in CNF.unifyDisjunct(): bindings " + bindings2);
            if (bindings2 != null) {
                return bindings2;
            }
        }
        return null;
    }

    /** *************************************************************
     */
    public HashMap<String,String> composeBindings(HashMap<String,String> bindold,
                                                  HashMap<String,String> bindnew) {

        if (debug) System.out.println("INFO in CNF.composeBindings(): compose: " + bindold + " and " + bindnew);
        if (bindold == null)
            return bindnew;
        if (bindnew == null)
            return bindold;
        HashMap<String,String> result = new HashMap<String,String>();
        for (String oldKey : bindold.keySet()) {
            String oldVal = bindold.get(oldKey);
            if (bindnew.containsKey(oldVal)) {
                result.put(oldKey, bindnew.get(oldVal));
            }
            else {
                result.put(oldKey, oldVal);
            }
        }
        for (String newKey : bindnew.keySet()) {
            if (!result.containsKey(newKey)) {
                result.put(newKey, bindnew.get(newKey));
            }
        }
        if (debug) System.out.println("INFO in CNF.composeBindings(): return: " + result);
        return result;
    }

    /** *************************************************************
     */
    public CNF applyBindings(HashMap<String,String> bindings, CNF cnf) {

        CNF result = new CNF();
        if (debug) System.out.println("INFO in CNF.applyBindings(): apply binding: " + bindings);
        if (debug) System.out.println("INFO in CNF.applyBindings(): to: " + cnf);
        for (Clause c : cnf.clauses) {
            result.clauses.add(c.applyBindings(bindings));
        }
        if (debug) System.out.println("INFO in CNF.applyBindings(): result: " + result);
        return result;
    }

    /** *************************************************************
     * Look for a clause in cnf to unify with c.  Return all possibilities.
     * If no unifications are found, return null.
     */
    public HashSet<Subst> unifyRecurse(Clause c, CNF cnf) {

        CNF cnfCopy = cnf.deepCopy();
        if (debug) System.out.println("");
        if (debug) System.out.println("INFO in CNF.unifyRecurse(c): clause: " + c);
        if (debug) System.out.println("INFO in CNF.unifyRecurse(c): cnf 'content': " + cnf);
        HashSet<Subst> result = new HashSet<Subst>();
        boolean modelFound = false;
        for (Clause c2 : cnfCopy.clauses) {
            Clause cCopy = c.deepCopy();
            Clause c2Copy = c2.deepCopy();
            HashMap<String,String> bind = c2Copy.unify(cCopy);
            // HashMap<String,String> bind = cCopy.unify(c2);
            if (bind != null) {
                modelFound = true;
                if (debug) System.out.println("INFO in CNF.unifyRecurse(c): found binding: " + bind);
                Subst s = new Subst();
                s.substMap = bind;
                result.add(s);
            }
        }
        if (debug) System.out.println("INFO in CNF.unifyRecurse(): result (clause): " + result);
        if (!modelFound)
            return null;
        return result;
    }

    /** *************************************************************
     * Iterate through all possibilities of applying a given clause
     * from the "rule" to the "content", then recurse on applying the
     * remaining clauses.  Note that every clause in this must unify
     * consistently with some clause in cnf or the routine will return
     * null.
     */
    public HashSet<Subst> unifyRecurse(CNF cnf) {

        if (debug) System.out.println("");
        if (debug) System.out.println("INFO in CNF.unifyRecurse(): cnf 'rule': " + this);
        if (debug) System.out.println("INFO in CNF.unifyRecurse(): cnf 'content': " + cnf);
        HashSet<Subst> result = new HashSet<Subst>();
        if (this.clauses.size() == 0)
            return result;
        boolean modelFound = false;
        for (Clause c : this.clauses) {
            Clause cCopy = c.deepCopy();
            CNF cnfCopy = cnf.deepCopy();
            HashSet<Subst> bindList = unifyRecurse(cCopy,cnfCopy);
            if (debug) System.out.println("INFO in CNF.unifyRecurse(): returning from clause recurse with: " + bindList);
            if (debug) System.out.println("INFO in CNF.unifyRecurse(): current result: " + result);
            if (debug && bindList != null) System.out.println("INFO in CNF.unifyRecurse(): " + bindList.size());
            CNF thiscopy = this.deepCopy();
            thiscopy.clauses.remove(c);
            if (bindList != null) {
                if (thiscopy.clauses.size() == 0) {
                    result.addAll(bindList);
                    modelFound = true;
                }
                else {
                    for (Subst s : bindList) {
                        CNF another = thiscopy.deepCopy();
                        another = applyBindings(s.substMap, another);
                        cnfCopy = cnf.deepCopy();
                        HashSet<Subst> subst2 = another.unifyRecurse(cnfCopy);
                        if (debug)
                            System.out.println("INFO in CNF.unifyRecurse(): returning from recurse with: " + subst2);
                        if (subst2 != null) {
                            modelFound = true;
                            if (subst2.size() == 0)
                                result.add(s);
                            else {
                                for (Subst s2 : subst2) {
                                    Subst substnew = new Subst();
                                    substnew.substMap = composeBindings(s.substMap, s2.substMap);
                                    result.add(substnew);
                                }
                            }
                        }
                    }
                }
            }
            if (debug) System.out.println("INFO in CNF.unifyRecurse(): current result: " + result);
        }
        if (!modelFound) {
            if (debug) System.out.println("INFO in CNF.unifyRecurse(): no models found to complete: " + result);
            return null;
        }
        else
            if (debug) System.out.println("INFO in CNF.unifyRecurse(): result: " + result);
        return result;
    }

    /** *************************************************************
     */
    public HashMap<String,String> unify(CNF cnf) {

        if (debug) System.out.println("INFO in CNF.unify(): cnf 'rule': " + this);
        if (debug) System.out.println("INFO in CNF.unify(): cnf content: " + cnf);
        HashSet<Subst> substList = this.unifyRecurse(cnf);
        if (debug) System.out.println("INFO in CNF.unifyNew(): substList: " + substList);
        if (substList == null)
            return null;
        if (substList.size() == 0)
            return (new Subst()).substMap; // empty map
        ArrayList<Subst> result = new ArrayList<>();
        result.addAll(substList);
        if (debug) System.out.println("INFO in CNF.unify(): result: " + result);
        return result.get(0).substMap;
    }

    /** *************************************************************
     * A test method
     */
    public static void testMerge() {
        
        Lexer lex = new Lexer("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        CNF cnf1 = CNF.parseSimple(lex);
        Lexer lex2 = new Lexer("foo(BodyMotion,Bob-2), bar(Human,John-1).");
        CNF cnf2 = CNF.parseSimple(lex2);
        cnf1.merge(cnf2);
        System.out.println("INFO in CNF.testEquality(): should have four clauses: " + cnf1);
    }

    /** *************************************************************
     * A test method
     */
    public static void testParseSimple() {
        
        Lexer lex = new Lexer("num(?O,?N), +sumo(?C,?O).");
        CNF cnf1 = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testParseSimple(): " + cnf1);
        lex = new Lexer("sumo(Vehicle,?car-6), nmod:in(?put-2,?car-6)");
        cnf1 = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testParseSimple(): " + cnf1);
    }

    /** *************************************************************
     * A test method
     */
    public static void testEquality() {
        
        Lexer lex = new Lexer("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        CNF cnf1 = CNF.parseSimple(lex);
        Lexer lex2 = new Lexer("sumo(BodyMotion,Bob-2), sumo(Human,John-1).");
        CNF cnf2 = CNF.parseSimple(lex2);
        System.out.println("INFO in CNF.testEquality(): should be true: " + cnf1.equals(cnf2));
    }

    /** *************************************************************
     * A test method
     */
    public void testSubst() {

        Subst s1 = new Subst();
        s1.substMap.put("?X","val");
        Subst s2 = new Subst();
        s2.substMap.put("?X","val");
        HashSet<Subst> set = new HashSet<>();
        set.add(s1);
        set.add(s2);
        System.out.println(set);
    }

    /** *************************************************************
     * A test method
     */
    public static void testContains() {
        
        Lexer lex = new Lexer("sumo(BodyMotion,Bob-2).");
        CNF cnf1 = CNF.parseSimple(lex);
        Lexer lex2 = new Lexer("sumo(BodyMotion,Bob-2).");
        CNF cnf2 = CNF.parseSimple(lex2);
        ArrayList<CNF> al = new ArrayList<CNF>();
        al.add(cnf1);
        if (!al.contains(cnf2))
            al.add(cnf2);
        System.out.println("INFO in CNF.testEquality(): should be 1: " + al.size());
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify2() {

        System.out.println("INFO in CNF.testUnify2(): -------------------------------------");
        String rule = "nmod:to(be_born*,?H2), sumo(Human,?H), sumo(Human,?H2), nsubjpass(be_born*,?H) ==> (parent(?H,?H2)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("nsubjpass(be_born-2,John-1), attribute(John-1,Male),   " +
                "sumo(Human,John-1), sumo(Human,Mary-5), nmod:to(be_born-2,Mary-5), case(Mary-5,to-4), root(ROOT-0,be_born-2), sumo(Birth,be_born-2).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings: " + cnf1.unify(cnf));
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): expecting: parent(?John-1,Mary-5).");
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify() {

        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        String rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("sense(212345678,Foo).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings: " + cnf1.unify(cnf));
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): expecting: Xsense(212345678,Foo).");
        
        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        cnf1 = Clausifier.clausify(r.lhs);
        lex = new Lexer("sense(2123,Foo).");
        cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings  (should be null): " + cnf1.unify(cnf));
        
        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        String rule2 = "det(?X,What*), sumo(?O,?X).";
        lex = new Lexer(rule2);
        cnf1 = CNF.parseSimple(lex);
        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings (should be null): " + cnf1.unify(cnf));

        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        rule2 = "nsubj(?X,?Y), sumo(?O,?X).";
        lex = new Lexer(rule2);
        cnf1 = CNF.parseSimple(lex);
        clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings: " + cnf1.unify(cnf));
        System.out.println("INFO in CNF.testUnify(): expecting: Xnsubj(drives-2,John-1), root(ROOT-0,drives-2), Xsumo(Transportation,drives-2), sumo(Human,John-1).");
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        
        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        rule = "nsubj(?V,?Who*).";
        lex = new Lexer(rule);
        cnf1 = CNF.parseSimple(lex);
        String cnfstr = "nsubj(kicks-2,John-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex = new Lexer(cnfstr);
        cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnify(): bindings (should be null): " + cnf1.unify(cnf));
        
        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        String rule3 = "nsubj(?V,Who*).";
        Lexer lex2 = new Lexer(rule3);
        CNF cnf12 = CNF.parseSimple(lex2);
        String cnfstr2 = "nsubj(moves-2,Who-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex2 = new Lexer(cnfstr2);
        CNF cnf2 = CNF.parseSimple(lex2);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf2);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf12);
        System.out.println("INFO in CNF.testUnify(): bindings (should be ?V=moves-2): " + cnf12.unify(cnf2));

        System.out.println("INFO in CNF.testUnify(): -------------------------------------");
        String rule4 = "StartTime(?V,?T), day(?T,?D), month(?T,?M), year(?T,?Y).";
        Lexer lex4 = new Lexer(rule4);
        CNF cnf4 = CNF.parseSimple(lex4);
        //String cnfstr5 = "root(ROOT-0,be-3), det(celebration-2,the-1), nsubj(be-3,celebration-2), prep_from(be-3,July-5), num(July-5,5-6), num(July-5,1980-8), prep_to(be-3,August-10), num(August-10,4-11), num(August-10,1980-13), sumo(SocialParty,celebration-2), number(SINGULAR,celebration-2), tense(PAST,be-3), number(SINGULAR,July-5), number(SINGULAR,August-10), day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        String cnfstr5 = "day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        //String cnfstr5 = "StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), day(time-1,5)";
        Lexer lex5 = new Lexer(cnfstr5);
        CNF cnf5 = CNF.parseSimple(lex5);
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf4);
        System.out.println("INFO in CNF.testUnify(): cnf1 " + cnf5);
        System.out.println("INFO in CNF.testUnify(): bindings: " + cnf4.unify(cnf5));
    }
    
    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {
        
        //testEquality();
        //testContains();
        //testMerge();
        //testUnify2();
        //testParseSimple();
        //CNF cnf = new CNF();
        //cnf.testSubst();

        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        Lexer lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println(cnf.getPreds());
    }
}
