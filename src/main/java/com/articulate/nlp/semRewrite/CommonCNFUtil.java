package com.articulate.nlp.semRewrite;

/*
Copyright 2014-2015 IPsoft,
          2015 - 2017 Articulate Software
          2017 - Infosys

Author: Peigen You
        Adam Pease, apease@articulatesoftware.com

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
import com.articulate.sigma.StringUtil;

import java.io.*;
import java.util.*;

/***********************************************************
 * A set of static functions that deal with finding common CNF content
 * between sentences
 *
 * findPath     will find all paths between two literals in the CNF string
 * findOneCommonCNF    will try to find the intersection CNF among a list of CNFs
 * getCommonCNF    will try to find common CNF pair between sentences
 */
public class CommonCNFUtil {

    private int varCounter = 0;
    private static List<String> ignorePreds = Arrays.asList(new String[]{"number", "tense"/**, "root", "names"**/});
    public KB kb;

    /***********************************************************
     */
    private class Substitution {

        public HashMap<String,String> substMap = new HashMap<>();
        public HashSet<Literal> targetLiterals = new HashSet<>();
        public HashSet<Literal> sourceLiterals = new HashSet<>();

        public String toString() {
            return substMap.toString() + "\n" + sourceLiterals.toString() + "\n" + targetLiterals.toString();
        }
    }

    /***********************************************************
     * load sentences from file, one line one sentence
     */
    public static String[] loadSentencesFromTxt(String path) {

        ArrayList<String> res = new ArrayList<String>();
        try (Scanner in = new Scanner(new FileReader(path))) {
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (StringUtil.emptyString(line))
                    continue;
                res.add(line);
            }
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return res.toArray(new String[res.size()]);
    }

    /***********************************************************
     * load sentences from file, one line one sentence
     * return map
     */
    public static Map<Integer, String> loadSentencesMap(String path) {

        Map<Integer, String> res = new HashMap<Integer, String>();
        try (Scanner in = new Scanner(new FileReader(path))) {
            int index = 0;
            while (in.hasNextLine()) {
                String line = in.nextLine();
                res.put(index++, line);
            }
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return res;
    }

    /***********************************************************
     */
    public static Map<Integer, CNF> generateCNFForStringSet(Map<Integer, String> sentences) {

        Map<Integer, CNF> res = new HashMap<Integer, CNF>();
        Interpreter inter = new Interpreter();
        KBmanager.getMgr().initializeOnce();
        try {
            inter.initialize();
            for (Integer index : sentences.keySet()) {
                String q = sentences.get(index);
                CNF cnf = inter.interpretGenCNF(q);
                System.out.println("generateCNFForStringSet(): before preprocess: " + cnf);
                //cnf = preProcessCNF(cnf);
                System.out.println("generateCNFForStringSet(): " + cnf);
                res.put(index, cnf);
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return res;
    }

    /***********************************************************
     */
    public CNF findOneCommonCNF(Collection<CNF> input) {

        CNF res = new CNF();
        boolean isFirst = true;
        System.out.println("findOneCommonCNF(): Among the following CNF: \n");
        for (CNF c : input) {
            System.out.println(c);
            if (!isFirst) {
                //res = unification(res, c);
                //res = mostSpecificUnifier(res, c);
                res = mostSpecificUnifier(res, c);
            }
            else {
                res = c;
                isFirst = false;
            }
        }
        System.out.println("\n The common CNF is " + res);
        return res;
    }

    /***********************************************************
     * find common ancestor of two sumo terms in the sumo term family tree
     * see KBcache.getCommonParent()
     */
    private String findCommonAncestor(String s1, String s2) {

        System.out.println("findCommonAncestor(): " + s1 + " : " + s2);
        if ((s1.contains("\"") || s1.contains("-")) || (s2.contains("-") || s2.contains("\"")) || (s1.startsWith("?") || (s2.startsWith("?")))) {
            System.out.println("findCommonAncestor(): result: ?X");
            return "?X";
        }
        else {
            if (kb == null) {
                System.out.println("error in findCommonAncestor(): null kb");
                return null;
            }
            if (kb.containsTerm(s1) && kb.containsTerm(s2)) {
                String parent = kb.kbCache.getCommonParent(s1,s2);
                System.out.println("findCommonAncestor(): result: " + parent);
                return parent;
            }
            else {
                if (!kb.containsTerm(s1))
                    System.out.println("findCommonAncestor(): KB missing term: " + s1);
                if (!kb.containsTerm(s2))
                    System.out.println("findCommonAncestor(): KB missing term: " + s2);
                return null;
            }
        }
    }

    /***********************************************************
     * function to take a string of sentences, with one per line,
     * and generate one common CNF for all sentences
     */
    public CNF findCommonCNF(String sents) {

        String[] sentAr = sents.split("\n");
        Map<Integer,String> strs = new HashMap<>();
        int index = 0;
        for (String s : sentAr)
            strs.put(index++,s);
        Map<Integer, CNF> cnfMap = CommonCNFUtil.generateCNFForStringSet(strs);
        System.out.println("\nfindCommonCNF(): Sentences are:\n");
        for (Integer i : strs.keySet()) {
            System.out.println(strs.get(i));
            System.out.println(cnfMap.get(i));
        }
        Collection<CNF> cnfList = new ArrayList<>();
        for (int i : cnfMap.keySet()) {
            CNF cnf = cnfMap.get(i);
            tokensToVars(cnf);
        }
        CNF cnf = findOneCommonCNF(cnfMap.values());
        return cnf;
    }

    /***********************************************************
     * function to load text file and generate one common CNF for all sentences,
     * one sentence one line.
     */
    public CNF loadFileAndFindCommonCNF(String path) throws IOException {

        Map<Integer, String> strs = loadSentencesMap(path);
        Map<Integer, CNF> cnfMap = CommonCNFUtil.generateCNFForStringSet(strs);
        System.out.println("\nloadFileAndFindCommonCNF(): Sentences are:\n");
        for (Integer i : strs.keySet()) {
            System.out.println(strs.get(i));
            System.out.println(cnfMap.get(i));
        }
        CNF cnf = findOneCommonCNF(cnfMap.values());
        return cnf;
    }

    /***********************************************************
     */
    public HashMap<String,String> unify(String l1, String l2, String pred) {

        HashMap<String,String> theta = new HashMap<>();
        //System.out.println("unify(Str,Str): " + l1 + "\n" + l2);
        //System.out.println("unify(Str,Str): theta: " + theta);
        if (Literal.isVariable(l1)) {
            theta.put(l1, l2);
        }
        else if (Literal.isVariable(l2)) {
            theta.put(l2, l1);
        }
        else if (!l1.equals(l2)) {
            if (!pred.equals("sumo")) {// only check to see if SUMO terms have a common ancestor if they're not equal
                //System.out.println("unify(Str,Str): returning null ");
                return null;
            }
            String ca = findCommonAncestor(l1,l2);
            //System.out.println("unify(Str,Str): ancestor: " + ca);
            if (!StringUtil.emptyString(ca) && !ca.equals("Entity")) {
                theta.put(l1,ca);
                theta.put(l2,ca);
            }
            else
                return null;
        }
        else
            return null;
        System.out.println("unify(Str,Str): theta: " + theta);
        return theta;
    }

    /***********************************************************
     */
    public HashMap<String,String> unify(Literal l1, Literal l2) {

        HashMap<String,String> theta = new HashMap<>();
        //System.out.println("unify(Lit,Lit): " + l1 + "\n" + l2);
        //System.out.println("unify(Lit,Lit): theta: " + theta);
        if (!l1.pred.equals(l2.pred))
            return null;
        theta = unify(l1.arg1,l2.arg1,l1.pred);
        if (theta == null)
            return null;
        HashMap<String,String> theta2 = unify(l1.arg2,l2.arg2,l1.pred);
        if (theta2 == null)
            return null;
        theta.putAll(theta2);
        l1.bound = true;
        l2.bound = true;
        //System.out.println("unify(Lit,Lit): return theta: " + theta);
        return theta;
    }

    /***********************************************************
     */
    public CNF applyBindings(CNF c1, HashMap<String,String> subst) {

        CNF result = new CNF();
        for (Clause c : c1.clauses) {
            Clause newc = new Clause();
            for (Literal d : c.disjuncts) {
                Literal newd = new Literal();
                newd.pred = d.pred;
                if (subst.containsKey(d.arg1))
                    newd.arg1 = subst.get(d.arg1);
                else
                    newd.arg1 = d.arg1;
                if (subst.containsKey(d.arg2))
                    newd.arg2 = subst.get(d.arg2);
                else
                    newd.arg2 = d.arg2;
                newc.disjuncts.add(newd);
            }
            result.clauses.add(newc);
        }
        return result;
    }

    /***********************************************************
     * Find the "bigger" CNF using CNF.compareTo()
     */
    public CNF mostSpecificForm(Collection<CNF> cnfs) {

        //System.out.println("mostSpecificForm(): ");
        if (cnfs == null || cnfs.size() == 0)
            return null;
        Iterator<CNF> it = cnfs.iterator();
        CNF first = it.next();
        //System.out.println("mostSpecificForm(): first: " + first);
        CNF best = first;
        while (it.hasNext()) {
            CNF next = it.next();
            //System.out.println("mostSpecificForm(): next: " + next);
            int nextBigger = next.compareTo(best);
            //System.out.println("mostSpecificForm(): nextBigger: " + nextBigger);
            if (nextBigger > 0) {
                //System.out.println("mostSpecificForm(): replacing best");
                best = next;
                //System.out.println("mostSpecificForm(): best: " + best);
            }
        }
        return best;
    }

    /***********************************************************
     * Apply a set of bindings to a collection of literals
     */
    public Collection<Literal> substitute(Substitution subst, Collection<Literal> lits) {

        Collection<Literal> result = new HashSet<>();
        for (Literal d1 : lits) {
            Literal newLit = new Literal();
            newLit.pred = d1.pred;
            if (subst.substMap.containsKey(d1.arg1))
                newLit.arg1 = subst.substMap.get(d1.arg1);
            else
                newLit.arg1 = d1.arg1;
            if (subst.substMap.containsKey(d1.arg2))
                newLit.arg2 = subst.substMap.get(d1.arg2);
            else
                newLit.arg2 = d1.arg2;
            result.add(newLit);
        }
        return result;
    }

    /***********************************************************
     * Apply a set of bindings to a CNF
     */
    public CNF substitute(Substitution subst, CNF cnf) {

        CNF result = new CNF();
        for (Clause clause1 : cnf.clauses) {
            Clause newClause = new Clause();
            for (Literal d1 : clause1.disjuncts) {
                Literal newLit = new Literal();
                newLit.pred = d1.pred;
                if (subst.substMap.containsKey(d1.arg1))
                    newLit.arg1 = subst.substMap.get(d1.arg1);
                else
                    newLit.arg1 = d1.arg1;
                if (subst.substMap.containsKey(d1.arg2))
                    newLit.arg2 = subst.substMap.get(d1.arg2);
                else
                    newLit.arg2 = d1.arg2;
                newClause.disjuncts.add(newLit);
            }
            result.clauses.add(newClause);
        }
        return result;
    }

    /***********************************************************
     * Apply one set of bindings to another.  Reject the binding
     * if it binds a targetLiteral that was previously bound.
     */
    public Substitution composeBindings(Substitution newSubst,
                                        Substitution oldSubst) {

        //System.out.println("composeBindings(): new: " + newSubst);
        //System.out.println("composeBindings(): old: " + oldSubst);
        if (newSubst.targetLiterals.size() > 1)
            System.out.println("Error: composeBindings(): target literals size unexpectedly greater than one: " + newSubst);
        if (oldSubst.targetLiterals.contains(newSubst.targetLiterals.iterator().next())) {
            System.out.println("Error: composeBindings(): target literal already bound: " + newSubst.targetLiterals);
            return null;
        }
        Substitution result = new Substitution();
        for (String oldKey : oldSubst.substMap.keySet()) {
            String oldVal = oldSubst.substMap.get(oldKey);
            if (newSubst.substMap.containsKey(oldVal)) {
                result.substMap.put(oldKey, newSubst.substMap.get(oldVal));
            }
            else {
                result.substMap.put(oldKey, oldVal);
            }
        }
        for (String newKey : newSubst.substMap.keySet()) {
            if (!result.substMap.containsKey(newKey)) {
                result.substMap.put(newKey, newSubst.substMap.get(newKey));
            }
        }
        result.targetLiterals.addAll(newSubst.targetLiterals);
        result.sourceLiterals.addAll(newSubst.sourceLiterals);
        result.targetLiterals.addAll(oldSubst.targetLiterals);
        result.sourceLiterals.addAll(oldSubst.sourceLiterals);
        //System.out.println("composeBindings(): combined: " + result);
        return result;
    }

    /***********************************************************
     */
    public CNF mostSpecificUnifier(CNF c1, CNF c2) {

        c1 = c1.renameVariables();
        c2 = c2.renameVariables();
        System.out.println("mostSpecificUnifier(): find unifer for " + c1 + " and " + c2);
          // all options for unifying the two forms
        List<Substitution> substList = new ArrayList<>();
        Substitution substInitial = new Substitution(); // initially create an empty substitution to add to
        substList.add(substInitial);
        for (Clause clause1 : c1.clauses) {
            for (Literal d1 : clause1.disjuncts) { // try to match each literal in c1 to one in c2
                List<Substitution> newSubstList = new ArrayList<>();
                for (Clause clause2 : c2.clauses) {
                    for (Literal d2 : clause2.disjuncts) {
                        HashMap<String,String> substTemp = new HashMap<>();
                        substTemp = unify(d1,d2);
                        if (substTemp != null && substTemp.size() > 0) {// if two literals unify, add their substitutions
                            Substitution sub = new Substitution();
                            sub.substMap = substTemp;
                            sub.sourceLiterals.add(d1);
                            sub.targetLiterals.add(d2);
                            newSubstList.add(sub);
                        }
                    }
                }
                //System.out.println("mostSpecificUnifier(): subst for Literal: " + d1 + " is " + newSubstList);
                //System.out.println("mostSpecificUnifier(): old subst: " + substList);
                if (substList.size() == 0) // if no unification was found previously, just add the new one
                    substList.addAll(newSubstList);
                else { // if there was a previous unification for these forms, compose the cross product of new and existing substitutions
                    List<Substitution> tempSubstList = new ArrayList<>();
                    if (newSubstList.size() == 0)
                        tempSubstList.addAll(substList);
                    for (Substitution subst : newSubstList) {
                        for (Substitution oldSubst : substList) {
                            Substitution newSubst = composeBindings(subst, oldSubst);
                            if (newSubst != null)
                                tempSubstList.add(newSubst);
                            else
                                tempSubstList.add(oldSubst); // if the new binding is for a literal already bound, ignore it
                        }
                    }
                    substList = tempSubstList;
                }
                //System.out.println("mostSpecificUnifier(): new subst: " + substList);
            }
        }
        System.out.println("mostSpecificUnifier(): final: " + substList);
        Set<CNF> results = new HashSet<>();
        for (Substitution sub : substList) {
            results.add(CNF.fromLiterals(substitute(sub,sub.sourceLiterals)));
            System.out.println("mostSpecificUnifier(): literals: " + substitute(sub,sub.sourceLiterals));
        }

        return mostSpecificForm(results);
    }

    /***********************************************************
     * Replace all instances of a given token with the given variable
     * Modify the CNF parameter directly
     */
    public static void renameTokens(String var, String token, CNF cnf) {

        for (Clause c : cnf.clauses) {
            for (Literal d : c.disjuncts) {
                Literal newLit = new Literal();
                newLit.pred = d.pred;
                if (d.arg1.equals(token))
                    d.arg1 = var;
                if (d.arg2.equals(token))
                    d.arg2 = var;
            }
        }
    }

    /***********************************************************
     */
    public static void tokensToVars(CNF cnf) {

        for (Clause c : cnf.clauses) {
            for (Literal d : c.disjuncts) {
                if (Literal.isToken(d.arg1))
                    d.arg1 = "?" + d.arg1;
                if (Literal.isToken(d.arg2))
                    d.arg2 = "?" + d.arg2;
            }
        }
    }

    /***********************************************************
     * Find the most specific unifier for two strings formatted as
     * dependency parses
     */
    public static void testCommonForms(String s1, String s2) {

        KBmanager.getMgr().initializeOnce();
        Collection<CNF> cnfs = new ArrayList<>();
        CNF c1 = CNF.valueOf(s1);
        tokensToVars(c1);
        CNF c2 = CNF.valueOf(s2);
        tokensToVars(c2);
        cnfs.add(c1);
        cnfs.add(c2);

        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.testCommonForms(): s1: " + s1);
        System.out.println("CommonCNFUtil.testCommonForms(): s2: " + s2);
        System.out.println("CommonCNFUtil.testCommonForms(): result: " +
                ccu.findOneCommonCNF(cnfs));
    }

    /***********************************************************
     * print the most specific of two dependency parses
     */
    public static void testMostSpecificForms(String s1, String s2) {

        KBmanager.getMgr().initializeOnce();
        Collection<CNF> cnfs = new ArrayList<>();
        CNF c1 = CNF.valueOf(s1);
        tokensToVars(c1);
        CNF c2 = CNF.valueOf(s2);
        tokensToVars(c2);
        cnfs.add(c1);
        cnfs.add(c2);

        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.testMostSpecificForms(): s1: " + s1);
        System.out.println("CommonCNFUtil.testMostSpecificForms(): s2: " + s2);
        System.out.println("CommonCNFUtil.testMostSpecificForms(): result: " +
                ccu.mostSpecificForm(cnfs));
    }

    /***********************************************************
     * Test the most specific unifier for two strings'
     * dependency parses
     */
    public static void testSentenceInput() {

        KBmanager.getMgr().initializeOnce();
        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.test(): " +
                ccu.findCommonCNF("John kicks the cart.\nSusan pushes the wagon."));
    }

    /***********************************************************
     * Test the most specific unifier for two strings'
     * dependency parses
     */
    public static void testSentenceInput2() {

        KBmanager.getMgr().initializeOnce();
        CommonCNFUtil ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        CNF result = ccu.findCommonCNF("John gets in the car.\nSusan rides in the tank");
        CNF expected = new CNF("sumo(Physical,?VAR7), case(?VAR9,get_in), " +
                "root(?VAR6,get_in), det(?VAR9,?VAR11), nmod:in(get_in,?VAR9)");
        System.out.println("CommonCNFUtil.test(): result: " + result);
        System.out.println("CommonCNFUtil.test(): expected: " + expected);
        if (result.equals(expected))
            System.out.println("CommonCNFUtil.test(): pass");
        else
            System.out.println("CommonCNFUtil.test(): fail");
    }

    /***********************************************************
     */
    public static void test() {

        String s1 = null;
        String s2 = null;
        s1 = "names(John-1,\"John\")";
        s2 = "names(Susan-1,\"Susan\")";
        System.out.println("CommonCNFUtil.test(): " );
        testCommonForms(s1,s2);
/*
        s1 = "sumo(Wagon,cart-4), sumo(Kicking,kicks-2), nsubj(kicks-2,John-1), " +
                "dobj(kicks-2,cart-4)";
        s2 = "sumo(Pushing,pushes-2), " +
                "sumo(Trailer,wagon-4), dobj(pushes-2,wagon-4), nsubj(pushes-2,Susan-1)";
        System.out.println("CommonCNFUtil.test(): " );
        testCommonForms(s1,s2);

        System.out.println("---------------------------------\n");

        s1 = "root(ROOT-0,kicks-2), det(cart-4,the-3), names(John-1,\"John\"), " +
                "sumo(Wagon,cart-4), sumo(Kicking,kicks-2), nsubj(kicks-2,John-1), " +
                "dobj(kicks-2,cart-4), attribute(John-1,Male), sumo(Human,John-1), " +
                "number(SINGULAR,John-1), lemma(John,John-1), tense(PRESENT,kicks-2), " +
                "lemma(kick,kicks-2), number(SINGULAR,cart-4), lemma(cart,cart-4)";
        s2 = "root(ROOT-0,pushes-2), det(wagon-4,the-3), names(Susan-1,\"Susan\"), " +
                "attribute(Susan-1,Female), sumo(Pushing,pushes-2), sumo(Human,Susan-1), " +
                "sumo(Trailer,wagon-4), dobj(pushes-2,wagon-4), nsubj(pushes-2,Susan-1), " +
                "number(SINGULAR,Susan-1), lemma(Susan,Susan-1), tense(PRESENT,pushes-2), " +
                "lemma(push,pushes-2), number(SINGULAR,wagon-4), lemma(wagon,wagon-4)";
        System.out.println("CommonCNFUtil.test(): " );
        testCommonForms(s1,s2);

        System.out.println("---------------------------------\n");

        ccu = new CommonCNFUtil();
        ccu.kb = KBmanager.getMgr().getKB("SUMO");
        System.out.println("CommonCNFUtil.test(): " +
            ccu.findCommonCNF("John kicks the cart.\nSusan pushes the wagon."));
*/
    }


    /***********************************************************
     */
    public static void main(String[] args) {

        //test();
        //testMostSpecificForm1();
        testSentenceInput2();
    }
}