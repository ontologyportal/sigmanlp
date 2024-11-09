package com.articulate.nlp.semRewrite;

/*
Copyright 2014-2015 IPsoft

Author: Adam Pease adam.pease@ipsoft.com

This class represents the left hand side of a rule.

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

import java.lang.reflect.Method;
import java.text.ParseException;

/** *************************************************************
LHS ::= ClausePattern        Match & delete atomic clause
        +ClausePattern       Match & preserve atomic clause
        LHS, LHS             Boolean conjunction 
        (LHS | LHS)          Boolean disjunction
        —LHS                 Boolean negation        
        {ProcedureCall}      Procedural attachment
 */
public class LHS {

    public enum LHSop {AND,OR,NOT,PRESERVE,DELETE,PROC}
    public Literal clause;
    public LHS lhs1;
    public LHS lhs2;
    public LHSop operator;
    public Method method;

    /** ***************************************************************
     */
    public String toString() {

        StringBuilder sb = new StringBuilder();
        if (operator == LHSop.AND)
            sb.append("[" + lhs1.toString() + ", " + lhs2.toString() + "]");
        else if (operator == LHSop.OR)
            sb.append("(" + lhs1.toString() + " | " + lhs2.toString() + ")");
        else if (operator == LHSop.DELETE)
            sb.append(clause.toString());
        else if (operator == LHSop.PRESERVE)
            sb.append("+" + clause.toString());
        else if (operator == LHSop.NOT)
            sb.append("-" + lhs1.toString());
        else if (operator == LHSop.PROC)
            sb.append("{" + method.toString() + "}");
        return sb.toString();
    }

    /** ***************************************************************
     */
    public LHS deepCopy() {

        LHS newlhs = new LHS();
        newlhs.clause = clause;
        newlhs.lhs1 = lhs1;
        newlhs.lhs2 = lhs2;
        newlhs.operator = operator;
        newlhs.method = method;
        return newlhs;
    }

    /** ***************************************************************
     * @param lex is a Lexer which has been initialized with the
     * textual version of the LHS
     * @param startLine is the line in the text file at which the
     * LHS appears.  If it is in a large rule the start line
     * could be different than the actual line of text for the LHS.
     * If the LHS is just from a string rather than directly from
     * a text file then the startLine will be 0.
     * @return a LHS corresponding to the input text passed to the
     * Lexer.
     */
    public static LHS parse(Lexer lex, int startLine) {

        String errStart = "Parsing error in " + RuleSet.filename;
        String errStr;
        LHS lhs = new LHS();
        try {
            //System.out.println("INFO in LHS.parse(): " + lex.look());
            if (lex.testTok(Lexer.Plus)) {
               // Clause c = Clause.parse(lex,startLine);
               // lhs.operator = LHSop.DELETE;
               // lhs.clause = c;
               // return lhs;
            }
            else if (lex.testTok(Lexer.OpenPar)) {
                lex.next();
                lhs.operator = LHSop.OR;
                lhs.lhs1 = LHS.parse(lex,startLine);
                if (!lex.testTok(Lexer.Or)) {
                    errStr = (errStart + ": Invalid end token '" + lex.look() + "' near line " + startLine);
                    throw new ParseException(errStr, startLine);
                }
                lex.next();
                lhs.lhs2 = LHS.parse(lex,startLine);
                if (!lex.testTok(Lexer.ClosePar)) {
                    errStr = (errStart + ": Invalid end token '" + lex.look() + "' near line " + startLine);
                    throw new ParseException(errStr, startLine);
                }
                lex.next();
                return lhs;
            }
            else if (lex.testTok(Lexer.Negation)) {
                lhs.operator = LHSop.NOT;
                lex.next();
                lhs.lhs1 = LHS.parse(lex,startLine);
                return lhs;
            }
            else if (lex.testTok(Lexer.OpenBracket)) {
                lex.next();
                lhs.operator = LHSop.PROC;
                Literal c = Literal.parse(lex,startLine);
                lhs.clause = c;
                //lex.next();
                if (!lex.testTok(Lexer.CloseBracket)) {
                    errStr = (errStart + ": Expected end bracket instead of '" + lex.look() + "' near line " + startLine);
                    throw new ParseException(errStr, startLine);
                }
                lex.next();
                return lhs;
            }
            // Now it's either just a clause or a left hand side
            Literal c = Literal.parse(lex,startLine);
            //System.out.println("INFO in LHS.parse(): found a clause: " + c);
            //System.out.println("INFO in LHS.parse(): " + lex.look());
            if (lex.testTok(Lexer.Comma)) {
                LHS left = new LHS();
                left.operator = LHSop.DELETE;
                left.clause = c;
                lhs.operator = LHSop.AND;
                lhs.lhs1 = left;
                lex.next();
                lhs.lhs2 = LHS.parse(lex, startLine);
            }
            else {
                lhs.clause = c;
                lhs.operator = LHSop.DELETE;
            }
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            System.out.println("Error in LHS.parse(): " + message);
            ex.printStackTrace();
        }
        //System.out.println("INFO in LHS.parse(): returning: " + lhs);
        return lhs;
    }

    /** *************************************************************
     * A test method
     */
    public static void testParse() {

        String s = "{isCELTclass(?X,Object)} ==> (isCELTclass(?X,Time)).";
        Lexer lex = new Lexer(s);
        LHS lhs = new LHS();
        lhs.parse(lex, 0);
    }

    /** *************************************************************
     * A test method
     */
    public static void testParseProc() {

        String s = "{isCELTclass(?X,Object)} ==> (isCELTclass(?X,Time)).";
        Lexer lex = new Lexer(s);
        LHS lhs = new LHS();
        lhs.parse(lex, 0);
    }

    /** *************************************************************
     * A test method
     */
    public static void testParseProc2() {

        String s = "+nsubj(?C2,?X), +amod(?C2,?C), cop(?C2,be*), det(?C2,?D), sumo(?Y,?C), sumo(Human,?X), isInstanceOf(?Y,Nation) ==> (citizen(?X,?Y)).";
        Lexer lex = new Lexer(s);
        LHS lhs = new LHS();
        System.out.println("INFO in LHS.testParseProc2(): " + lhs.parse(lex, 0));
    }
    
    /** *************************************************************
     * A test method
     */
    public static void main (String args[]) {
        
        testParseProc2();
    }
}
