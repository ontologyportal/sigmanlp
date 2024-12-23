package com.articulate.nlp.semRewrite;

/*
Copyright 2014-2015 IPsoft

Author: Adam Pease adam.pease@ipsoft.com

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

import com.articulate.sigma.Formula;
import com.articulate.sigma.FormulaUtil;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;

public class RHS {

    public Formula form = null; // a SUO-KIF formula - can be empty
    public CNF cnf = null;
    boolean stop;
    public static boolean debug = false;
    
    /** ***************************************************************
     */
    public String toString() {
        
        StringBuilder sb = new StringBuilder();
        if (stop)
            sb.append("stop");
        else if (cnf != null)
            sb.append("(" + cnf + ")");
        else if (form == null)
            sb.append("!");
        else 
            sb.append("{" + form.toString() + "}");
        return sb.toString();
    }
    
    /** ***************************************************************
     */
    public RHS deepCopy() {
        
        RHS rhs = new RHS();
        if (form != null)
            rhs.form = form.deepCopy();
        if (cnf != null)
            rhs.cnf = cnf.deepCopy();
        rhs.stop = stop;
        return rhs;
    }

    /** ***************************************************************
     * If there's more than one literal, return null and print error
     */
    public Literal toLiteral() {

        if (cnf == null) {
            System.out.println("Error in RHS.toLiteral(): null cnf: " + this);
            return null;
        }
        if (cnf.clauses == null) {
            System.out.println("Error in RHS.toLiteral(): null clauses: " + this);
            return null;
        }
        if (cnf.clauses.size() > 1) {
            System.out.println("Error in RHS.toLiteral(): more than one clause: " + this);
            return null;
        }
        Clause c =  cnf.clauses.get(0);
        if (c.disjuncts == null) {
            System.out.println("Error in RHS.toLiteral(): null disjuncts: " + this);
            return null;
        }
        if (c.disjuncts.size() > 1) {
            System.out.println("Error in RHS.toLiteral(): more than one disjunct: " + this);
            return null;
        }
        return c.disjuncts.get(0);
    }

    /** ***************************************************************
     * The predicate must already have been read
     */
    public static RHS parse(Lexer lex, int startLine) {

        KB kb = KBmanager.getMgr().getKB("SUMO");
        String errStart = "Parsing error in " + RuleSet.filename;
        String errStr;
        RHS rhs = new RHS();
        try {
            if (lex.testTok(Lexer.Stop)) {
                rhs.stop = true;
                lex.next();
                if (debug) System.out.println("Info in RHS.parse(): 1 " + lex.look());
                if (!lex.testTok(Lexer.Stop)) {
                    errStr = (errStart + ": Invalid end token '" + lex.next() + "' near line " + startLine);
                    throw new ParseException(errStr, startLine);
                }
            }
            else if (lex.testTok(Lexer.Zero)) {
                lex.next();
            }
            else if (lex.testTok(Lexer.OpenBracket)) {
                StringBuilder sb = new StringBuilder();
                String st = lex.nextUnfiltered();
                while (!st.equals("}")) {
                    st = lex.nextUnfiltered();
                    if (!st.equals("}"))
                        sb.append(st);
                }
                rhs.form = new Formula(sb.toString());
                if (debug) System.out.println("Info in RHS.parse(): form: " + rhs.form);
                if (debug) System.out.println("Info in RHS.parse(): is simple: " + rhs.form.isSimpleClause(kb));
                if (debug) System.out.println("Info in RHS.parse(): is binary: " + rhs.form.isBinary());
                if (rhs.form.isSimpleClause(kb) && rhs.form.isBinary())
                    rhs.cnf = new CNF(FormulaUtil.toProlog(rhs.form));
                if (debug) System.out.println("Info in RHS.parse(): SUMO: " + sb.toString());
            }
            else if (lex.testTok(Lexer.OpenPar)) {
                lex.next();
                rhs.cnf = CNF.parseSimple(lex);
                if (debug) System.out.println("Info in RHS.parse(): cnf " + rhs.cnf);
                //lex.next();
            }
            //System.out.println("Info in RHS.parse(): 2 " + lex.look());
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in RHS.parse() " + message);
            ex.printStackTrace();
        }
        return rhs;
    }
 
    /** ***************************************************************
     * Apply variable substitutions to this set of clauses
     */
    public RHS applyBindings(Subst bindings) {

        if (bindings == null) {
            System.out.println("Error in RHS.applyBindings(): null bindings");
            return this;
        }
        RHS rhs = new RHS();
        if (cnf != null) {
            rhs.cnf = cnf.applyBindings(bindings);
            rhs.form = form;
            if (debug) System.out.println("INFO in RHS.applyBindings(): cnf: " + cnf);
        }

        Iterator<String> it = bindings.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            String value = bindings.get(key);
            if (debug) System.out.println("INFO in RHS.applyBindings(): key,bindings: " + key + ", " + bindings);
            if (form != null && form.getFormula() != null) {
                form = form.replaceVar(key, value);
                if (debug) System.out.println("INFO in RHS.applyBindings(): formula: " + form.getFormula());
            }
        }
        if (form != null) {
            if (debug) System.out.println("INFO in RHS.applySubst(): formula (2): " + form.getFormula());
            rhs.form = form;
            if (debug) System.out.println("INFO in RHS.applySubst(): formula (3): " + form.getFormula());
        }
        return rhs;
    }
    
    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {
    }
}
