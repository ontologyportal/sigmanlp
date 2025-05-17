package com.articulate.nlp;

import com.articulate.sigma.*;

import java.io.PrintWriter;
import java.util.*;
import com.articulate.sigma.utils.StringUtil;
import java.util.Random;

public class GenGroundStatements {
    private boolean debug = false;

    private Set<String> skipTypes = new HashSet<>();
    private KB kb;
    private PrintWriter englishFile;
    private PrintWriter logicFile;
    private boolean skip = false;
    private final int instLimit = 200;
    public final Random rand = new Random();


    public GenGroundStatements() {
        KBmanager.getMgr().initializeOnce();
        kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
    }

    /**
     * **************************************************************
     * Generate arguments for all relations and output their English
     * paraphrase
     */
    public void generate(PrintWriter pEnglishFile, PrintWriter pLogicFile) {

        englishFile = pEnglishFile;
        logicFile = pLogicFile;
        System.out.println("GenSimpTestData.generate()");
        System.out.println("generate(): # relations: " + kb.kbCache.relations.size());
        Map<String, String> formatMap = kb.getFormatMap("EnglishLanguage");
        skipTypes.addAll(Arrays.asList("Formula"));
        System.out.println("generate(): output existing ground statements ");
        handleGroundStatements(formatMap);
        System.out.println("generate(): create ground statements ");
        genStatements(formatMap);
    }

    /** ***************************************************************
     * generate English for all ground relation statements
     */
    public void handleGroundStatements(Map<String, String> formatMap ) {

        Set<Formula> forms = new HashSet<>();
        forms.addAll(kb.formulaMap.values());
        System.out.println("handleGroundStatements(): search through " + forms.size() + " statements");
        for (Formula f : forms) {
            if (f.isGround() && formatMap.containsKey(f.relation) && !StringUtil.emptyString(f.toString())) {
                englishFile.print(GenUtils.toEnglish(f.toString(), kb));
                logicFile.println(f);
            }
        }
    }

    /** ***************************************************************
     * generate new SUMO statements for relations and output English
     * paraphrase
     */
    public void genStatements(Map<String, String> formatMap) {

        for (String rel : kb.kbCache.relations) {
            skip = false;
            if (formatMap.get(rel) != null && !kb.isFunction(rel)) {
                boolean skip = false;
                if (debug) System.out.println("genStatements()  rel: " + rel);
                List<String> sig = kb.kbCache.getSignature(rel);
                Map<String, List<String>> instMap = new HashMap<>();
                if (debug) System.out.println("sig: " + sig);
                for (String t : sig) {
                    if (skipTypes.contains(t))
                        skip = true;
                    if (StringUtil.emptyString(t) || skipTypes.contains(t))
                        continue;
                    if (debug) System.out.println("genStatements() t: " + t);
                    if (!t.endsWith("+") && !kb.isSubclass(t,"Quantity")) {
                        handleNonClass(t,instMap);
                    }
                    else if (kb.isSubclass(t,"Quantity")) {
                        if (debug) System.out.println("genStatements(): found quantity for : " + rel);
                        handleQuantity(t, instMap);
                    }
                }
                if (!skip) {
                    String form;
                    List<Formula> forms = genFormulas(rel,sig,instMap);
                    for (Formula f : forms) {
                        form = f.getFormula();
                        if (!StringUtil.emptyString(form)) {
                            logicFile.println(form);
                            String actual = GenUtils.toEnglish(form, kb);
                            englishFile.println(StringUtil.filterHtml(actual));
                        }
                    }
                }
            }
        }
    }

    /** ***************************************************************
     * handle the case where the argument type is a subclass
     */
    public String handleClass(String t, HashMap<String, ArrayList<String>> instMap) {

        String arg = "";
        String bareClass = t.substring(0, t.length() - 1);
        if (debug) System.out.println("handleClass(): bareClass: " + bareClass);
        if (bareClass.equals("Class"))
            skip = true;
        else {
            Set<String> children = kb.kbCache.getChildClasses(bareClass);
            List<String> cs = new ArrayList<>();
            cs.addAll(children);
            if (children == null || children.isEmpty())
                skip = true;
            else {
                int rint = rand.nextInt(cs.size());
                arg = cs.get(rint);
            }
        }
        return arg;
    }


    /** ***************************************************************
     * generate new SUMO statements for relations using the set of
     * available instances for each argument type and output English
     * paraphrase
     */
    public List<Formula> genFormulas(String rel, List<String> sig,
                                            Map<String, List<String>> instMap) {

        List<StringBuilder> forms = new ArrayList<>();
        String currT;
        for (int i = 1; i < sig.size(); i++) {
            currT = sig.get(i);
            if (currT.endsWith("+"))
                return new ArrayList<>(); // bail out if there is a subclass argument
        }

        StringBuilder form = new StringBuilder();
        form.append("(").append(rel).append(" ");
        forms.add(form);

        List<StringBuilder> newforms;
        String arg;
        StringBuilder f;
        for (int i = 1; i < sig.size(); i++) {
            newforms = new ArrayList<>();
            currT = sig.get(i);
            if (debug) System.out.println("genFormula() currT: " + currT);
            if (instMap.get(currT) == null || instMap.get(currT).size() < 1)
                return new ArrayList<>();
            int max = instMap.get(currT).size();
            if (max > instLimit) {
                max = instLimit;
                if (sig.size() > 2)  // avoid combinatorial explosion in higher arities
                    max = 100;
                if (sig.size() > 3)
                    max = 31;
                if (sig.size() > 4)
                    max = 15;
            }

            for (int j = 0; j < max; j++) {
                arg = instMap.get(currT).get(j);
                for (StringBuilder sb : forms) {
                    f = new StringBuilder(sb);
                    f.append(arg).append(" ");
                    newforms.add(f);
                }
            }
            forms = newforms;
            if (forms.size() % 1000 == 0)
                System.out.println("genFormulas(): size so far: " + forms.size());
        }

        List<Formula> formsList = new ArrayList<>();
        Formula formula;
        for (StringBuilder sb : forms) {
            sb.deleteCharAt(sb.length() - 1);
            sb.append(")");
            formula = new Formula(sb.toString());
            formsList.add(formula);
        }
        return formsList;
    }

    /** ***************************************************************
     * handle the case where the argument type is not a subclass
     */
    public void handleNonClass(String t, Map<String, List<String>> instMap) {

        if (debug) System.out.println("handleNonClass(): t: " + t);
        Set<String> hinsts = kb.kbCache.getInstancesForType(t);
        if (hinsts.contains("statementPeriod"))
            if (debug) System.out.println("handleNonClass(): hinsts: " + hinsts);
        List<String> insts = new ArrayList<>();
        insts.addAll(hinsts);
        if (debug) System.out.println("handleNonClass(): insts: " + insts);
        if (!insts.isEmpty()) {
            if (instMap.containsKey(t)) {
                List<String> oldinsts = instMap.get(t);
                oldinsts.addAll(insts);
            }
            else
                instMap.put(t,insts);
        }
        else {
            String term = t + "1";
            if (debug) System.out.println("handleNonClass(2): t: " + t);
            String lang = "EnglishLanguage";
            insts.add(term);
            if (debug) System.out.println("handleNonClass(): insts(2): " + insts);
            //System.out.println("handleNonClass(): term format size: " + kb.getTermFormatMap(lang).keySet().size());
            //System.out.println("handleNonClass(): containsKey: " + kb.getTermFormatMap(lang).containsKey(t));
            //System.out.println("handleNonClass(): termFormat: " + kb.getTermFormatMap(lang).get(t));
            String fString = "a " + kb.getTermFormatMap(lang).get(t); // kb.getTermFormat(lang,t);
            String form = "(termFormat EnglishLanguage " + term + " \"" + fString + "\")";
            Map<String, String> langTermFormatMap = kb.getTermFormatMap(lang);
            langTermFormatMap.put(term, fString);
            //System.out.println(form);
            kb.tell(form);
            instMap.put(t, insts);
        }
        if (debug) System.out.println("handleNonClass(): instMap: " + instMap);
    }


    /** ***************************************************************
     * handle quantities
     */
    public void handleQuantity(String t, Map<String, List<String>> instMap) {

        Set<String> instances = kb.getAllInstances(t);
        if (instances.size() < 1) {
            if (debug) System.out.println("handleQuantity(): no instances for " + t);
            return;
        }
        List<String> arInsts = new ArrayList<>();
        arInsts.addAll(instances);
        int rint = rand.nextInt(instances.size());
        String inst = arInsts.get(rint); // get an instance of a quantity
        float num = rand.nextFloat() * 100;
        String f = "(MeasureFn " + num + " " + inst + ")";
        if (instMap.containsKey(t)) {
            List<String> insts = instMap.get(t);
            insts.add(f);
        }
        else {
            List<String> insts = new ArrayList<>();
            insts.add(f);
            instMap.put(t,insts);
        }
    }
}

