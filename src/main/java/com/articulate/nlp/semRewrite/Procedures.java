package com.articulate.nlp.semRewrite;

/*
Author: Sofia Athenikos
Author: Adam Pease apease@articulatesoftware.com

original version Copyright 2014-2015 IPsoft
modified 2015- Articulate Software

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

Note that the list of String identifiers for the procedures must
match those listed in Clause.unify()
*/

import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.Formula;

import java.util.Arrays;
import java.util.List;

public class Procedures {

    public static boolean debug = false;

    public static final List<String> procNames = Arrays.asList("isCELTclass",
            "isSubclass", "isInstanceOf", "isSubAttribute", "isChildOf", "different");

    public static final List<String> procClasses = Arrays.asList("Super", "Person", "Time");

    /** ***************************************************************
     */
    public static boolean isProcPred(String s) {

        if (procNames.contains(s))
            return true;
        return false;
    }

    /** ***************************************************************
     * CELT classes which are not SUMO classes, like "person"
     */
    public static String different(Literal c) {

        if (c.arg1.equals(c.arg2))
            return "false";
        else
            return "true";
    }

    /** ***************************************************************
     * CELT classes which are not SUMO classes, like "person"
     */
    public static String isCELTclass(Literal c) {

        if (!c.pred.equals("isCELTclass")) {
            System.out.println("Error in Procedures.isCELTclass(): bad predicate: " + c.pred);
            return "false";
        }
        // debugging terms
        if (c.arg1.equals("Sub") && c.arg2.equals("Super"))
            return "true";

        if (!procClasses.contains(c.arg2))
            System.out.println("Warning in Procedures.isCELTclass(): unreserved CELT class: " + c.arg2);

        KB kb = KBmanager.getMgr().getKB("SUMO");
        if (debug) System.out.println("INFO in Procedures.isCELTclass(): " + c);
        if (kb == null) {
            //if (c.arg1.equals("River") && c.arg2.equals("Object"))
            //    return "true";
            return "false";
        }
        if (debug) System.out.println("INFO in Procedures.isCELTclass(): kb.kbCache.subclassOf(\"TimeMeasure\"): " +
                kb.kbCache.subclassOf(c.arg1, "TimeMeasure"));
        if (debug) System.out.println("INFO in Procedures.isCELTclass(): kb.kbCache.isInstanceOf(\"TimeMeasure\"): " +
                kb.kbCache.isInstanceOf(c.arg1, "TimeMeasure"));
        if (debug) System.out.println("INFO in Procedures.isCELTclass(): kb.kbCache.transInstOf(\"TimeMeasure\"): " +
                kb.kbCache.transInstOf(c.arg1, "TimeMeasure"));

        if (c.arg2.equals("Person"))
            if (kb.kbCache.subclassOf(c.arg1, "Human") || kb.kbCache.isInstanceOf(c.arg1, "Human") ||
                    kb.kbCache.subclassOf(c.arg1, "SocialRole") || kb.kbCache.isInstanceOf(c.arg1, "SocialRole"))
                return "true";
            else
                return "false";
        else if (c.arg2.equals("Time"))
            if (kb.kbCache.subclassOf(c.arg1, "TimeMeasure") || kb.kbCache.isInstanceOf(c.arg1, "TimeMeasure") |
                    kb.kbCache.subclassOf(c.arg1, "Process") || kb.kbCache.isInstanceOf(c.arg1, "Process"))
                return "true";
            else
                return "false";
        else if (kb.kbCache.subclassOf(c.arg1, c.arg2) || kb.kbCache.isInstanceOf(c.arg1,c.arg2))
            return "true";
        else {
            if (debug) System.out.println("INFO in Procedures.isCELTclass(): not a valid CELT class " +
                    "or no super/sub relationship: returning false");
            return "false";
        }
    }

    /** ***************************************************************
     * Take a ground procedure literal l2 and see if ground literal l1 can satisfy it.
     * @param l1 is the "sentence" content that must be a sumo literal.
     * @param l2 is the "rule" that must be a isCELTclass literal
     */
    public static Subst procUnify(Literal l1, Literal l2) {

        if (debug) System.out.println("INFO in Procedures.procUnify(): l1 (content): " + l1);
        if (debug) System.out.println("INFO in Procedures.procUnify(): l2 (rule): " + l2);
        Subst result = new Subst();
        if (!l1.isGround() || !l2.isGround())
            return null;
        if (!l1.pred.equals("sumo") && !l1.pred.equals("sumoInstance"))
            return null;
        if (!l2.pred.equals("isCELTclass"))
            return null;
        if (!l1.arg2.equals(l2.arg1))
            return null;
        KB kb = KBmanager.getMgr().getKB("SUMO");
        if (l2.arg2.equals("Person"))
            if (kb.isSubclass(l1.arg1, "Human") || kb.isInstanceOf(l1.arg1, "Human") ||
                    kb.isSubclass(l1.arg1, "SocialRole") || kb.isInstanceOf(l1.arg1, "SocialRole"))
                return result;
            else
                return null;
        else if (l2.arg2.equals("Time"))
            if (kb.isSubclass(l1.arg1, "TimeMeasure") || kb.isSubclass(l1.arg1, "Process") || kb.isInstanceOf(l1.arg1, "Process"))
                return result;
            else
                return null;
        return null;
    }

    /** ***************************************************************
     * if subclass or equal return true
     * kb.isSubclass(child,parent)
     */
    public static String isSubclass(Literal c) {

        // debugging terms
        if (c.arg1.equals("Sub") && c.arg2.equals("Super"))
            return "true";
        KB kb = KBmanager.getMgr().getKB("SUMO");
        if (debug) System.out.println("INFO in Procedures.isSubclass():term set size: " + kb.terms.size());
        if (debug) System.out.println("INFO in Procedures.isSubclass(): " + c);
        if (debug) System.out.println(": " + c);
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kb contains (first arg): " + c.arg1 + " : " + kb.terms.contains(c.arg1));
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kb contains (second arg): " + c.arg2 + " : " + kb.terms.contains(c.arg2));
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kb.isSubclass(" + c.arg1 + "," + c.arg2 + "): " +
                kb.isSubclass(c.arg1, c.arg2));
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kb.childOf(" + c.arg1 + "," + c.arg2 + "): " +
                kb.childOf(c.arg1, c.arg2));
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kb.isChildOf(" + c.arg1 + "," + c.arg2 + "): " +
                kb.isChildOf(c.arg1, c.arg2));
        if (debug) System.out.println("INFO in Procedures.isSubclass(): kbCache.childOfP(" + c.arg1 + "," + c.arg2 + "): " +
                kb.kbCache.childOfP("subclass",c.arg1, c.arg2));
        if (c.arg1.equals(c.arg2))
            return "true";
        if (kb.isSubclass(c.arg1, c.arg2))
            return "true";
        else
            return "false";
    }

    /** ***************************************************************
     */
    public static String isInstanceOf(Literal c) {

        // debugging terms
        if (c.arg1.equals("Sub") && c.arg2.equals("Super"))
            return "true";
        KB kb = KBmanager.getMgr().getKB("SUMO");
        List<Formula> forms = kb.ask("arg",1,c.arg1);
        if (debug) System.out.println("INFO in Procedures.isInstanceOf(): " + forms);
        Formula f = null;
        //if (forms != null && forms.size() > 0) forms.get(0);
        //if (debug = true) System.out.println("INFO in Procedures.isInstanceOf(): " + f.getArgument(2));
        //if (debug = true) System.out.println("INFO in Procedures.isInstanceOf(): " + kb.isSubclass(f.getArgument(2),c.arg2));
        if (debug) System.out.println("INFO in Procedures.isInstanceOf(): " + c);
        if (debug) System.out.println("INFO in Procedures.isInstanceOf(): " + kb.isInstanceOf(c.arg1, c.arg2));
        if (kb.isInstanceOf(c.arg1, c.arg2))
            return "true";
        else
            return "false";
    }

    /** ***************************************************************
     * Check both instances and classes with isInstanceOf() and isSubclass()
     */
    public static String isChildOf(Literal c) {

        if (isInstanceOf(c).equals("true") || isSubclass(c).equals("true"))
            return "true";
        else
            return "false";
    }

    /** ***************************************************************
     */
    public static String isSubAttribute(Literal c) {

        // debugging terms
        if (c.arg1.equals("Sub") && c.arg2.equals("Super"))
            return "true";
        KB kb = KBmanager.getMgr().getKB("SUMO");
        if (kb.isSubAttribute(c.arg1, c.arg2)) {
            return "true";
        }
        else {
            return "false";
        }
    }

}
