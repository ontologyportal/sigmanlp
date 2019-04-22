package com.articulate.nlp.semRewrite;

import java.util.HashSet;

/** *************************************************************
 */
public class Unification {

    Subst sub = new Subst();
    HashSet<Clause> bound = new HashSet<>();

    public String toString() { return sub.toString() + " : " + bound.toString(); }

    public Unification deepCopy() {
        Unification result = new Unification();
        result.sub = sub.deepCopy();
        result.bound = new HashSet<>();
        for (Clause c : bound)
            result.bound.add(c.deepCopy());
        return result;
    }
}
