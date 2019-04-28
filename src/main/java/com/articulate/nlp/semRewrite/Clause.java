package com.articulate.nlp.semRewrite;

/*
original version Copyright 2014-2015 IPsoft
modified 2015- Articulate Software

Author: Adam Pease apease@articulatesoftware.com

In conjunctive normal form (CNF) a formula is a conjunct of 
disjuncts.  This is the list of disjuncts.

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Clause implements Comparable {

    public ArrayList<Literal> disjuncts = new ArrayList<Literal>();
    public static boolean debug = false;
    //public static boolean bindSource = true; // whether to mark the bound flag on this (true)
       // or on the argument (false) during unification

    /** ***************************************************************
     * fewer clauses are smaller, more general sumo terms are smaller
     * and if those conditions aren't different, just choose lexical
     * order
     */
    public int compareTo(Object o) {

        if (!(o instanceof Clause))
            return 0;
        Clause c = (Clause) o;
        if (this.equals(c))
            return 0;
        if (this.disjuncts.size() < c.disjuncts.size())
            return -1;
        else if (this.disjuncts.size() > c.disjuncts.size())
            return 1;
        else {
            //System.out.println("CNF.compareTo() equal length clauses");
            return this.toString().compareTo(c.toString());
        }
    }

    /** *************************************************************
     */
    public String toString() {
        
        StringBuffer sb = new StringBuffer();
        if (disjuncts.size() > 1)
            sb.append("(");
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal c = disjuncts.get(i);
            sb.append(c.toString());
            if (disjuncts.size() > 1 && i < disjuncts.size() - 1)
                sb.append(" | ");
        }
        if (disjuncts.size() > 1)
            sb.append(")");
        return sb.toString();
    }
    
    /** *************************************************************
     */
    public Clause deepCopy() {
        
        Clause newd = new Clause();
        for (int i = 0; i < disjuncts.size(); i++) 
            newd.disjuncts.add(disjuncts.get(i).deepCopy());        
        return newd;
    }

    /** ***************************************************************
     */
    @Override
    public boolean equals(Object o) {
    
        if (!(o instanceof Clause))
            return false;
        Clause d = (Clause) o;
        if (disjuncts.size() != d.disjuncts.size())
            return false;
        for (int i = 0; i < disjuncts.size(); i++)
            if (!disjuncts.get(i).equals(d.disjuncts.get(i)))
                return false;
        return true;
    }
    
    /** ***************************************************************
     */
    public boolean empty() {
        
        if (disjuncts.size() == 0)
            return true;
        else
            return false;
    }
  
    /** *************************************************************
     */
    public void preProcessQuestionWords(List<String> qwords) {
        
        for (Literal c: disjuncts)
            c.preProcessQuestionWords(qwords);
    }
    
    /** ***************************************************************
     * Clear the bound flags on each Literal
     */
    public void clearBound() {
        
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal l = disjuncts.get(i);
            if (l.bound)
                l.bound = false;
        }
    }


    /** ***************************************************************
     * Set the bound flags on each Literal
     */
    public void bind() {

        if (disjuncts.size() > 1)
            System.out.println("Error in Clause.bind(): more than one disjunct for " + this);
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal l = disjuncts.get(i);
            l.bound = true;
        }
    }

    /** ***************************************************************
     * Clear the preserve flags on each Literal
     */
    public void clearPreserve() {
        
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal l = disjuncts.get(i);
            if (l.preserve)
                l.preserve = false;
        }
    }
    
    /** ***************************************************************
     * If literal is not marked "preserve" then remove it if bound and
     * then reset the preserve flag.  The preserve flag is set when
     * a rule literal with a '+' matches this literal.
     * Note this should only be called on inputs, not rules.
     */
    public void removeBound() {

        boolean boundFound = false;
        if (debug) System.out.println("INFO in Clause.removeBound(): before " + this);
        ArrayList<Literal> newdis = new ArrayList<Literal>();
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal l = disjuncts.get(i).deepCopy();
            if (l.bound) {
                boundFound = true;
                if (l.preserve) {
                    l.preserve = false; // reset the preserve flag
                }
                else
                    l.bound = false;
            }
            newdis.add(l);
        }
        //if (!boundFound)
        //    System.out.println("Error in Clause.removeBound(): no bound clause found for " + this);
        disjuncts = newdis;
        if (debug) System.out.println("INFO in Clause.removeBound(): after " + this);
    }
    
    /** ***************************************************************
     * Copy bound flags to this set of clauses  , merging with
     * existing bindings
     */
    public void copyBoundFlags(Clause d) {
     
        for (int i = 0; i < disjuncts.size(); i++) {
            if (d.disjuncts.get(i).bound)
                disjuncts.get(i).bound = true;
            if (d.disjuncts.get(i).preserve)
                disjuncts.get(i).preserve = true;
        }
    }
    
    /** *************************************************************
     * @return a clause that results from applying a binding list
     * to this clause.
     */
    public Clause applyBindings(Subst bindings) {
        
        Clause c = new Clause();
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal l = disjuncts.get(i);
            c.disjuncts.add(l.applySubst(bindings));
        }            
        return c;
    }

    /** *************************************************************
     * The argument to this method is the sentence and this is the rule
     * @return the set of variable bindings.  The key is the variable
     * and the value is the binding.  Note that the list of procedures
     * and their string identifiers must match those in Procedures.java .
     * If this is a procedure we try not only to run the procedure
     * but also to match sumo terms that might satisfy the procedure.  The
     * latter is done in Literal.mguTermList()
     */
    public Subst unify(Clause d) {

        if (debug) System.out.println("INFO in Clause.unify(): checking this (source/rule): " + this);
        if (debug) System.out.println("INFO in Clause.unify(): against argument: " + d);
        for (int i = 0; i < disjuncts.size(); i++) {
            Literal c1 = disjuncts.get(i);  // rule
            if (debug) System.out.println("INFO in Clause.unify(): checking " + c1);
            if (c1.pred.equals("isCELTclass") && c1.isGround())
                if (Procedures.isCELTclass(c1).equals("true"))
                    return new Subst();
            if (c1.pred.equals("isSubclass") && c1.isGround())
                if (Procedures.isSubclass(c1).equals("true"))
                    return new Subst();
            if (c1.pred.equals("isInstanceOf") && c1.isGround())
                if (Procedures.isInstanceOf(c1).equals("true"))
                    return new Subst();
            if (c1.pred.equals("isChildOf") && c1.isGround())
                if (Procedures.isChildOf(c1).equals("true"))
                    return new Subst();
            if (c1.pred.equals("isSubAttribute") && c1.isGround())
                if (Procedures.isSubAttribute(c1).equals("true")) {
                    return new Subst();
                }
            if (c1.pred.equals("different") && c1.isGround())
                if (Procedures.different(c1).equals("true")) {
                    return new Subst();
                }
            if (debug) System.out.println("INFO in Clause.unify(): done checking procedures");

            for (int j = 0; j < d.disjuncts.size(); j++) {
                Literal c2 = d.disjuncts.get(j);
                Subst bindings = c2.mguTermList(c1);
                if (debug) System.out.println("INFO in Clause.unify(): checking " + c1 + " against " + c2);
                if (bindings != null) {
                    if (c1.preserve)
                        c2.preserve = true;
                    //if (debug) System.out.println("Clause.unify(): bindSource: " + bindSource);
                    //if (bindSource) {
                        if (debug) System.out.println("Clause.unify(): binding: " + c2);
                        c2.bound = true; // mark as bound in case the rule consumes the clauses ( a ==> rule not a ?=>)
                    //}
                    //else {
                    //    c1.bound = true;
                    //    if (debug) System.out.println("Clause.unify(): binding argument: " + c1);
                    //}
                    if (debug) System.out.println("INFO in Clause.unify(): source: " + c2);
                    if (debug) System.out.println("INFO in Clause.unify(): argument: " + c1);
                    if (debug) System.out.println("INFO in Clause.unify(): bindings: " + bindings);
                    return bindings;
                }
            }
        }
        if (debug) System.out.println("INFO in Clause.unify(): this: " + this);
        if (debug) System.out.println("INFO in Clause.unify(): d: " + d);
        return null;
    }

    /** *************************************************************
     * A test method for parsing a Literal
     */
    public static void testUnify() {

        Literal l = null;
        try {
            String input = "isCELTclass(Sub,Super).";
            Lexer lex = new Lexer(input);
            lex.look();
            l = Literal.parse(lex, 0);
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Clause.testUnify() " + message);
            ex.printStackTrace();
        }
        Clause d = new Clause();
        d.disjuncts.add(l);
        Clause c = new Clause();
        System.out.println("Clause.testUnify(): " + c.unify(d));
    }

    /** *************************************************************
     * A test method for parsing a Literal
     */
    public static void testUnify2() {

        //bindSource = false;
        Literal l = null;
        Literal l2 = null;
        try {
            String input1 = "foo(Bar,Baz).";
            Lexer lex = new Lexer(input1);
            lex.look();
            l = Literal.parse(lex, 0);
            String input2 = "foo(?X,?Y).";
            Lexer lex2 = new Lexer(input2);
            lex2.look();
            l2 = Literal.parse(lex2, 0);
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in Clause.testUnify2() " + message);
            ex.printStackTrace();
        }
        Clause d = new Clause();
        d.disjuncts.add(l);
        Clause c = new Clause();
        c.disjuncts.add(l2);
        System.out.println("Clause.testParse(): " + c.unify(d));
        System.out.println("Clause.testParse(): c: " + c);
        System.out.println("Clause.testParse(): d: " + d);
    }

    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {

        testUnify();
        testUnify2();
    }
}
