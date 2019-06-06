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
    public void appendAll(Collection<Literal> lits) {

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

        //System.out.println("CNF.compareSUMOLiterals(): this: " + this);
        //System.out.println("CNF.compareSUMOLiterals(): cnf: " + cnf);
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
     * sort clauses alphabetically then compare as strings
     */
    public TreeSet<String> toSortedLiterals() {

        TreeSet<String> thisSet = new TreeSet<>();
        for (Clause clause1 : this.clauses) {
            for (Literal d1 : clause1.disjuncts)
                thisSet.add(d1.toString());
        }
        return thisSet;
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
     */
    public String toSortedString() {

        CNF newcnf = new CNF();
        newcnf.clauses.addAll(clauses);
        Collections.sort(newcnf.clauses);
        return newcnf.toString();
    }

    /** ***************************************************************
     */
    public static String toSortedString(ArrayList<CNF> cnfs) {

        ArrayList<CNF> newcnfs = new ArrayList();
        newcnfs.addAll(cnfs);
        Collections.sort(newcnfs);
        StringBuffer sb = new StringBuffer();
        for (CNF c : newcnfs)
            sb.append(c.toSortedString());
        return sb.toString();
    }

    /** ***************************************************************
     */
    public CNF sortProceduresLast() {

        CNF result = new CNF();
        Set<Clause> procClauses = new HashSet<>();
        for (Clause c : clauses) {
            if (c.isUnitaryProcedure())
                procClauses.add(c.deepCopy());
            else
                result.clauses.add(c.deepCopy());
        }
        for (Clause c : procClauses)
            result.clauses.add(c);
        return result;
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
        CNF cnfCopy = cnf.deepCopy();
        Collections.sort(cnfCopy.clauses);
        CNF thisCopy = this.deepCopy();
        Collections.sort(thisCopy.clauses);
        for (int i = 0; i < thisCopy.clauses.size(); i++) {
            //System.out.println("INFO in CNF.equals(): checking disjunct " + clauses.get(i) +
            //        " " + cnf.clauses.get(i));
            if (!clauses.get(i).equals(cnfCopy.clauses.get(i)))
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
     * Remove the bound clauses from the statement.  Return the bound
     * clauses.  The primary action is a side effect on this instance.
     */
    public CNF removeBound() {

        if (debug) System.out.println("INFO in CNF.removeBound(): before " + this);
        CNF newCNF = new CNF();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            d.removeBound();
            if (!d.empty())
                newCNF.clauses.add(d);
        }
        if (debug) System.out.println("INFO in CNF.removeBound(): after " + newCNF);
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
    public CNF applyBindings(Subst bindings) {

        CNF cnf = new CNF();
        for (int i = 0; i < clauses.size(); i++) {
            Clause d = clauses.get(i);
            Clause dnew = new Clause();
            for (int j = 0; j < d.disjuncts.size(); j++) {
                Literal c = d.disjuncts.get(j);
                //System.out.println("INFO in CNF.applySubst(): 1 " + c);
                Literal c2 = c.applySubst(bindings);
                //System.out.println("INFO in CNF.applySubst(): 1.5 " + c2);
                dnew.disjuncts.add(c2);
                //System.out.println("INFO in CNF.applySubst(): 2 " + dnew);
            }
            //System.out.println("INFO in CNF.applySubst(): 3 " + dnew);
            cnf.clauses.add(dnew);
        }
        return cnf;
    }

    /** ***************************************************************
     * Copy bound flags to this set of clauses, merging with existing
     * bound flags
     */
    public void copyBoundFlags(CNF cnf) {

        if (cnf.clauses.size() != this.clauses.size()) {
            System.out.println("Error in copyBoundFlags() different size arguments: " +
                    this + " and " + cnf);
            return;
        }
        for (int i = 0; i < clauses.size(); i++) {
            clauses.get(i).copyBoundFlags(cnf.clauses.get(i));
        }
    }

    /** ***************************************************************
     * Copy bound flags to this set of clauses, overwriting existing
     * bound flags
     */
    public void copyBoundFlags(HashSet<Clause> clauseSet) {

        for (int i = 0; i < clauses.size(); i++) {
            clauses.get(i);
        }
    }

    /** *************************************************************
     */
    public Subst composeBindings(Subst bindold, Subst bindnew) {

        if (debug) System.out.println("INFO in CNF.composeBindings(): compose: " + bindold + " and " + bindnew);
        if (bindold == null)
            return bindnew;
        if (bindnew == null)
            return bindold;
        Subst result = new Subst();
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
    public CNF applyBindings(Subst bindings, CNF cnf) {

        CNF result = new CNF();
        if (debug) System.out.println("INFO in CNF.applySubst(): apply binding: " + bindings);
        if (debug) System.out.println("INFO in CNF.applySubst(): to: " + cnf);
        for (Clause c : cnf.clauses) {
            result.clauses.add(c.applyBindings(bindings));
        }
        if (debug) System.out.println("INFO in CNF.applySubst(): result: " + result);
        return result;
    }

    /** *************************************************************
     * add the "local" unifications to each of the global ones
     */
    public HashSet<Unification> composeUnifications(HashSet<Unification> local,
                                                    HashSet<Unification> global) {

        if (debug) System.out.println("INFO in CNF.composeUnifications(): local subs: " + local);
        if (debug) System.out.println("INFO in CNF.composeUnifications(): global subs: " + global);
        HashSet<Unification> result = new HashSet<>();
        if (global.size() == 0) {
            for (Unification lu : local) {
                Unification u = new Unification();
                u.sub.putAll(lu.sub);
                u.bound.addAll(lu.bound);
                result.add(u);
            }
            if (debug) System.out.println("INFO in CNF.composeUnifications(): result: " + result);
            return result;
        }
        for (Unification gu : global) {
            for (Unification lu : local) {
                Unification u = new Unification();
                u.sub.putAll(gu.sub);
                u.sub.putAll(lu.sub);
                u.bound.addAll(gu.bound);
                u.bound.addAll(lu.bound);
                result.add(u);
            }
        }
        if (debug) System.out.println("INFO in CNF.composeUnifications(): result: " + result);
        return result;
    }

    /** *************************************************************
     * Iterate through all possibilities of applying a given clause
     * from the "rule" to the "content", then recurse on applying the
     * remaining clauses.  Note that every clause in this must unify
     * consistently with some clause in cnf or the routine will return
     * null. This is the rule and argument is the sentence to match
     */
    public HashSet<Unification> unifyNew(CNF cnf) {

        ArrayList<Subst> result = new ArrayList<Subst>();
        Integer globalCount = 0;
        HashSet<Unification> unis = new HashSet(); // Subst> globalSubs = new HashMap<>(); // values for variables
        //HashMap<Integer,HashSet<Clause>> globalBindings = new HashMap<>(); // bound clauses from sentence
        for (Clause rc : this.clauses) {
            if (debug) System.out.println("INFO in CNF.unifyNew(): check rule clause: " + rc);
            HashSet<Unification> localUnis = new HashSet<>();
            HashSet<String> bindFound = new HashSet<>(); // was a binding found in the argument for this global substitution value
            for (Clause sc : cnf.clauses) {
                if (unis.size() == 0) {
                    if (debug) System.out.println("INFO in CNF.unifyNew(): empty substitution list so far ");
                    Clause rcCopy = rc.deepCopy();
                    Clause scCopy = sc.deepCopy();
                    if (debug) System.out.println("INFO in CNF.unifyNew(): check rule clause: " +
                            rcCopy + " against: " + scCopy);
                    Subst sub = rcCopy.unify(scCopy);
                    if (debug) System.out.println("INFO in CNF.unifyNew(): result: " + sub);
                    if (sub != null) {
                        Unification u = new Unification();
                        u.sub = sub;
                        u.bound = new HashSet<>();
                        u.bound.add(scCopy);
                        localUnis.add(u);
                        if (debug) System.out.println("INFO in CNF.unifyNew(): local unifications: " + localUnis);
                    }
                }
                for (Unification oneUni : unis) { // try unifying with each substitution candidate
                    if (debug) System.out.println("INFO in CNF.unifyNew(): checking with substitution: " + oneUni);
                    Clause rcCopy = rc.deepCopy();
                    Clause scCopy = sc.deepCopy();
                    rcCopy = rcCopy.applyBindings(oneUni.sub); // note this is actually applying substitutions (variable bindings)
                    if (debug) System.out.println("INFO in CNF.unifyNew(): check rule clause: " +
                            rcCopy + " against: " + scCopy);
                    Subst sub = rcCopy.unify(scCopy);
                    if (debug) System.out.println("INFO in CNF.unifyNew(): result: " + sub);
                    if (sub != null) {
                        Unification u = new Unification();
                        u.sub = sub;
                        u.bound = new HashSet<>();
                        u.bound.add(scCopy);
                        localUnis.add(u);
                        bindFound.add(oneUni.bound.toString());
                        if (debug) System.out.println("INFO in CNF.unifyNew(): adding bindFound: " + u.bound);
                    }
                }
            }
            HashSet<Unification> newUnis = new HashSet<>(); // values for variables and bindings
            for (Unification u : unis)
                if (bindFound.contains(u.bound.toString())) {
                    //System.out.println("INFO in CNF.unifyNew(): keeping candidate: " + u.bound +
                    //        " with bind list " + bindFound);
                    newUnis.add(u.deepCopy());
                }
                //else
                //    System.out.println("INFO in CNF.unifyNew(): no binding found for candidate: " + u.bound +
                //            " with bind list " + bindFound);
            if (localUnis.size() > 0)
                unis = composeUnifications(localUnis,newUnis);
            else
                return null; // every clause from the rule must bind to something to succeed
              // note that a ground clause can bind and not have a variable substitution
            if (debug) System.out.println("INFO in CNF.unifyNew(): global unis: " + unis);
        }
        return unis;
    }

    /** *************************************************************
     */
    public void setBoundFlags(CNF cnf, Unification u) {

        for (Clause c : u.bound) {
            for (Clause uc : cnf.clauses) {
                //System.out.println("INFO in CNF.setBoundFlags(): check: " + c + " against " + uc);
                if (c.toString().equals(uc.toString()) ||
                        (c.toString().charAt(0) == 'X' && c.toString().substring(1).equals(uc.toString()))) {
                    uc.bind();
                    //System.out.println("INFO in CNF.setBoundFlags(): equal, now bound: " + uc);
                }
            }
        }
    }

    /** *************************************************************
     * this is the rule and the argument is the sentence to match
     */
    public Subst unify(CNF cnf) {

        if (debug) System.out.println("INFO in CNF.unify(): cnf source 'rule': " + this);
        CNF sorted = sortProceduresLast();
        this.clauses = sorted.clauses;
        if (debug) System.out.println("INFO in CNF.unify(): cnf source sorted 'rule': " + this);
        if (debug) System.out.println("INFO in CNF.unify(): cnf content (argument): " + cnf);
        // HashSet<Subst> substList = this.unifyRecurse(cnf);
        HashSet<Unification> substList = this.unifyNew(cnf);
        if (debug) System.out.println("INFO in CNF.unifyNew(): substList: " + substList);
        if (substList == null)
            return null;
        if (substList.size() == 0)
            return new Subst(); // empty map
        //System.out.println("INFO in CNF.unify(): cnf source 'rule': " + this);
        //System.out.println("INFO in CNF.unify(): cnf content (argument): " + cnf);
        //System.out.println("INFO in CNF.unify(): result: " + substList);
        Unification u = substList.iterator().next();
        setBoundFlags(cnf,u);
        return u.sub;
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
        s1.put("?X","val");
        Subst s2 = new Subst();
        s2.put("?X","val");
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
    public static void testUnifyNew() {

        System.out.println("INFO in CNF.testUnifyNew(): -------------------------------------");
        String rule = "nmod:to(be_born*,?H2), sumo(Human,?H), sumo(Human,?H2), nsubjpass(be_born*,?H) ==> (parent(?H,?H2)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF cnf1 = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("nsubjpass(be_born-2,John-1), attribute(John-1,Male),   " +
                "sumo(Human,John-1), sumo(Human,Mary-5), nmod:to(be_born-2,Mary-5), case(Mary-5,to-4), root(ROOT-0,be_born-2), sumo(Birth,be_born-2).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnifyNew(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnifyNew(): cnf1 " + cnf1);
        System.out.println("INFO in CNF.testUnifyNew(): bindings: " + cnf1.unify(cnf));
        System.out.println("INFO in CNF.testUnifyNew(): cnf " + cnf);
        System.out.println("INFO in CNF.testUnifyNew(): expecting: parent(?John-1,Mary-5).");
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify1() {

        System.out.println();
        //Clause.bindSource = false; // bind the target/argument to unify
        //System.out.println("INFO in CNF.testUnify(): bindSource: " + Clause.bindSource);
        System.out.println("INFO in CNF.testUnify(1): -------------------------------------");
        String rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF rulecnf = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("sense(212345678,Foo).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + rulecnf);
        System.out.println("INFO in CNF.testUnify(): bindings: " + rulecnf.unify(cnf));
        System.out.println("INFO in CNF.testUnify(): cnf actual: " + cnf);
        System.out.println("INFO in CNF.testUnify(): expecting: Xsense(212345678,Foo).");
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify2() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(2): -------------------------------------");
        String rule = "sense(212345678,?E) ==> " +
                "(sumo(Foo,?E)).";
        Rule r = new Rule();
        r = Rule.parseString(rule);
        System.out.println(r.toString());
        CNF rulecnf = Clausifier.clausify(r.lhs);
        Lexer lex = new Lexer("sense(2123,Foo).");
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + rulecnf);
        System.out.println("INFO in CNF.testUnify(): actual bindings (should be null): " + rulecnf.unify(cnf));
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify3() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(3): -------------------------------------");
        String rule2 = "det(?X,What*), sumo(?O,?X).";
        Lexer lex = new Lexer(rule2);
        CNF rulecnf = CNF.parseSimple(lex);
        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + rulecnf);
        System.out.println("INFO in CNF.testUnify(): actual bindings (should be null): " + rulecnf.unify(cnf));
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify4() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(4): -------------------------------------");
        String rule2 = "nsubj(?X,?Y), sumo(?O,?X).";
        Lexer lex = new Lexer(rule2);
        CNF rulecnf = CNF.parseSimple(lex);
        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + rulecnf);
        System.out.println("INFO in CNF.testUnify(): actual bindings: " + rulecnf.unify(cnf));
        System.out.println("INFO in CNF.testUnify(): expecting: Xnsubj(drives-2,John-1), root(ROOT-0,drives-2), Xsumo(Transportation,drives-2), sumo(Human,John-1).");
        System.out.println("INFO in CNF.testUnify(): cnf " + cnf);
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify5() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(5): -------------------------------------");
        String rule = "nsubj(?V,?Who*).";
        Lexer lex = new Lexer(rule);
        CNF rulecnf = CNF.parseSimple(lex);
        String cnfstr = "nsubj(kicks-2,John-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex = new Lexer(cnfstr);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + rulecnf);
        System.out.println("INFO in CNF.testUnify(): actual bindings (should be null): " + rulecnf.unify(cnf));
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify6() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(6): -------------------------------------");
        String rule3 = "nsubj(?V,Who*).";
        Lexer lex2 = new Lexer(rule3);
        CNF cnf12 = CNF.parseSimple(lex2);
        String cnfstr2 = "nsubj(moves-2,Who-1), root(ROOT-0,kicks-2), det(cart-4,the-3), dobj(kicks-2,cart-4), sumo(Kicking,kicks-2), sumo(Human,John-1), sumo(Wagon,cart-4).";
        lex2 = new Lexer(cnfstr2);
        CNF cnf2 = CNF.parseSimple(lex2);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf2);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + cnf12);
        System.out.println("INFO in CNF.testUnify(): actual bindings (should be ?V=moves-2): " + cnf12.unify(cnf2));
        System.out.println("INFO in CNF.testUnify(): expected bindings: ?V=moves-2");
    }

    /** *************************************************************
     * A test method
     */
    public static void testUnify7() {

        System.out.println();
        System.out.println("INFO in CNF.testUnify(7): -------------------------------------");
        String rule4 = "StartTime(?V,?T), day(?T,?D), month(?T,?M), year(?T,?Y).";
        Lexer lex4 = new Lexer(rule4);
        CNF cnf4 = CNF.parseSimple(lex4);
        //String cnfstr5 = "root(ROOT-0,be-3), det(celebration-2,the-1), nsubj(be-3,celebration-2), prep_from(be-3,July-5), num(July-5,5-6), num(July-5,1980-8), prep_to(be-3,August-10), num(August-10,4-11), num(August-10,1980-13), sumo(SocialParty,celebration-2), number(SINGULAR,celebration-2), tense(PAST,be-3), number(SINGULAR,July-5), number(SINGULAR,August-10), day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        String cnfstr5 = "day(time-2,4), StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), month(time-2,August), EndTime(was-3,time-2), day(time-1,5), year(time-2,1980)";
        //String cnfstr5 = "StartTime(was-3,time-1), month(time-1,July), year(time-1,1980), day(time-1,5)";
        Lexer lex5 = new Lexer(cnfstr5);
        CNF cnf5 = CNF.parseSimple(lex5);
        System.out.println("INFO in CNF.testUnify(): cnf source: " + cnf4);
        System.out.println("INFO in CNF.testUnify(): cnf argument: " + cnf5);
        System.out.println("INFO in CNF.testUnify(): actual bindings: " + cnf4.unify(cnf5));
    }

    /** ***************************************************************
     */
    public static void testSort() {

        String input = "dobj(ate-3, chicken-4), sumo(Eating,ate-3), sumo(Man,George-1), nmod:in(ate-3, December-6), " +
                "compound(Washington-2, George-1), root(ROOT-0, ate-3), sumo(ChickenMeat,chicken-4).";
        CNF cnf4 = new CNF(input);
        String sorted = "compound(Washington-2, George-1), dobj(ate-3, chicken-4), " +
                "nmod:in(ate-3, December-6), root(ROOT-0, ate-3), sumo(Eating,ate-3), sumo(Man,George-1), " +
                " sumo(ChickenMeat,chicken-4).";
        System.out.println("result: " + cnf4.toSortedString());
    }

    /** ***************************************************************
     */
    public static void testParse() {

        String clauses = "nsubj(drives-2,John-1), root(ROOT-0,drives-2), sumo(Transportation,drives-2), sumo(Human,John-1).";
        Lexer lex = new Lexer(clauses);
        CNF cnf = CNF.parseSimple(lex);
        System.out.println(cnf.getPreds());
    }

    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {

        //Clause.bindSource = false;
        //testEquality();
        //testContains();
        //testMerge();
        testUnify4();
        //testUnify2();
        //testParseSimple();
        //CNF cnf = new CNF();
        //cnf.testSubst();
        //testSort();
    }
}
