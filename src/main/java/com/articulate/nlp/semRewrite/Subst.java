package com.articulate.nlp.semRewrite;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

/** ***************************************************************
 */
public class Subst implements Comparable {

    private HashMap<String,String> substMap = new HashMap<>();

    /** ***************************************************************
     */
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

    /** ***************************************************************
     */
    public String get(String s) {
        return substMap.get(s);
    }

    /** ***************************************************************
     */
    public void put(String key, String value) {
        substMap.put(key,value);
    }

    /** ***************************************************************
     */
    public void putAll(Subst sub) {
        substMap.putAll(sub.substMap);
    }

    /** ***************************************************************
     */
    public boolean containsKey(String key) {
        return substMap.containsKey(key);
    }

    /** ***************************************************************
     */
    public Set<String> keySet() {
        return substMap.keySet();
    }

    /** ***************************************************************
     */
    public Subst deepCopy() {
        Subst s = new Subst();
        s.substMap.putAll(substMap);
        return s;
    }

    /** ***************************************************************
     */
    public Collection<String> values() {
        return substMap.values();
    }
}
