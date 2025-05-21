package com.articulate.nlp;

import com.articulate.sigma.Formula;
import java.io.File;
import java.util.ArrayList;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

public class KBLite {

    // Constant for the KB directory path, always ends with a separator
    private static final String KB_FILEPATH = System.getenv("SIGMA_HOME") + File.separator + "KBs";
    public String kbDir = KB_FILEPATH; // kbDir used for compatibility purposes.
    private List<String> kifFiles = new ArrayList<>();

    // Fast lookup map: key = second argument, value = argument list
    // private final Map<String, List<List<String>>> termArguments = new HashMap<>();
    public final Set<String> terms = new TreeSet<>();
    public Set<String> relations = new HashSet<>();
    public Set<String> functions = new HashSet<>();

    private final List<Implication> implications = new ArrayList<>();
    private final Map<String, String> documentation = new HashMap<>();
    private final Map<String, List<String>> termFormats = new HashMap<>();
    private final Map<String, List<String>> formats = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Map<String, List<String>> parents = new HashMap<>();
    private final Map<String, List<List<String>>> domains = new HashMap<>();
    private final Map<String, List<String>> ranges = new HashMap<>();
    private final Set<String> subclasses = new HashSet<>();
    private final Set<String> instances = new HashSet<>();
    private final Set<String> subAttributes = new HashSet<>();
    private final Set<String> subrelations = new HashSet<>();

    Random rand = new Random();
    // This is the formula, then the arg list. example: ["(subclass Cat Animal)", "subclass", "cat", "animal"]
    // rawFormulasWithArgs does not include implication formulas, or formulas that begin with IGNORED_FORMULA_STARTS.
    private final List<List<String>> rawFormulasWithArgs = new ArrayList<>();

    // Set of relevant first arguments
    private static final Set<String> TERM_CREATION_ARGUMENTS = new HashSet<>(Arrays.asList(
            "subAttribute", "instance", "subrelation", "subclass"
    ));

    private static final Set<String> IGNORED_FORMULA_STARTS = new HashSet<>(Arrays.asList(
            "and", "or", "forall", "exists"
    ));

    // Nested class to hold implications
    public static class Implication {
        public List<String> antecedent;
        public List<String> consequent;
        public Implication(List<String> antecedent, List<String> consequent) {
            this.antecedent = antecedent;
            this.consequent = consequent;
        }
    }

    /**
     * Constructor that takes the KB name and extracts the kif files for that KB.
     */
    public KBLite(String kbName) {
        System.out.println("\n********************************************************\nWARNING: KBLite does not perform syntax, type check, or \nany other check to ensure the knowledge base is accurate. \nOnly use after you are otherwise confident in the \naccuracy of the Knowledge Base. EnglishLanguage only.\n********************************************************\n");
        getKifFilesFromConfig(kbName);
        System.out.println("Loading kif files into cache.");
        loadKifs();
        buildRelationsCache();
        buildFunctionsCache();
        // Sort domains
        for (Map.Entry<String, List<List<String>>> entry : domains.entrySet()) {
            List<List<String>> listOfLists = entry.getValue();
            listOfLists.sort(Comparator.comparingInt(
                    sublist -> Integer.parseInt(sublist.get(2))
            ));
        }
        System.out.println("Files loaded.");
    }

    private void getKifFilesFromConfig(String kbName) {
        kifFiles.clear();
        try {
            File file = new File(KB_FILEPATH + File.separator + "config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(file); // <-- fully qualified
            doc.getDocumentElement().normalize();

            org.w3c.dom.NodeList kbList = doc.getElementsByTagName("kb");
            for (int i = 0; i < kbList.getLength(); i++) {
                org.w3c.dom.Element kbElement = (org.w3c.dom.Element) kbList.item(i);
                String nameAttr = kbElement.getAttribute("name");
                if (kbName.equals(nameAttr)) {
                    org.w3c.dom.NodeList constituentList = kbElement.getElementsByTagName("constituent");
                    for (int j = 0; j < constituentList.getLength(); j++) {
                        org.w3c.dom.Element consElem = (org.w3c.dom.Element) constituentList.item(j);
                        String filename = consElem.getAttribute("filename");
                        if (filename != null && !filename.isEmpty()) {
                            kifFiles.add(filename);
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadKifs() {
        for (String kifFile : kifFiles) {
            File file = new File(KB_FILEPATH + File.separator + kifFile);
            if (!file.exists()) {
                System.err.println("Error: File not found: " + file.getAbsolutePath());
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                StringBuilder formula = new StringBuilder();
                int parenBalance = 0;
                boolean inFormula = false;
                boolean inQuote = false;
                while ((line = reader.readLine()) != null) {
                    // Remove comments (everything after ';')
                    line = stripComments(line, inQuote).trim();
                    if (line.isEmpty()) {
                        continue; // Skip blank/whitespace-only lines
                    }

                    // If not currently in a formula, look for the start
                    if (!inFormula) {
                        if (line.startsWith("(")) {
                            inFormula = true;
                            formula.setLength(0); // reset the formula buffer
                            parenBalance = 0;
                        } else {
                            System.out.println("ERROR in KBLite.loadKifs. Unexpected characters outside of comments or formulas for line: " + line);
                            continue; // skip lines that don't start a formula
                        }
                    }

                    // Accumulate the line
                    formula.append(line).append(" ");

                    for (int i = 0; i < line.length(); i++) {
                        char c = line.charAt(i);
                        // Handle quote toggling (ignore escaped quotes)
                        if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                            inQuote = !inQuote;
                        }
                        if (!inQuote) {
                            if (c == '(') parenBalance++;
                            else if (c == ')') parenBalance--;
                        }
                    }

                    // If balanced, process the formula
                    if (inFormula && parenBalance == 0) {
                        String formulaStr = formula.toString().replace("\n", "").trim();
                        processFormula(formulaStr);
                        inFormula = false;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    private String stripComments(String line, boolean p_inQuote) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            // Toggle inQuote state on unescaped "
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                p_inQuote = !p_inQuote;
            }
            // If semicolon and not in quote, treat as comment start
            if (c == ';' && !p_inQuote) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void processFormula(String formulaStr) {

        List<String> arguments = splitFormulaArguments(formulaStr);
        if (arguments == null || arguments.isEmpty()
                || IGNORED_FORMULA_STARTS.contains(arguments.get(0)))
            return; // In this lite version we don't care about formulas that start with exists, and, or.

        // Handle implication entries, and save the rest of the formulas to rawFormulasWithArgs
        if (arguments.size() > 2 && "=>".equals(arguments.get(0))) {
            List<String> antecedent = splitFormulaArguments(arguments.get(1));
            List<String> consequent = splitFormulaArguments(arguments.get(2));
            implications.add(new Implication(antecedent, consequent));
        } else { // Handle non-implications separately.
            List<String> thisRawFormWithArg = new ArrayList<>();
            thisRawFormWithArg.add(formulaStr);               // insert at front
            thisRawFormWithArg.addAll(arguments);      // append the rest
            rawFormulasWithArgs.add(thisRawFormWithArg);
        }

        // Handle documentation entries
        if (arguments.size() > 3 &&
                "documentation".equals(arguments.get(0)) &&
                "EnglishLanguage".equals(arguments.get(2))) {
            documentation.put(arguments.get(1), arguments.get(3));
        }

        // Handle format entries
        if (arguments.size() > 3 &&
                "format".equals(arguments.get(0)) &&
                "EnglishLanguage".equals(arguments.get(1))) {
            String key = arguments.get(2);
            String value = arguments.get(3).replaceAll("^\"|\"$", ""); // Strip off opening and closing quotation marks
            List<String> valueList = formats.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
                formats.put(key, valueList);
            }
            valueList.add(value);
        }

        // Handle termFormat entries
        if (arguments.size() > 3 &&
                "termFormat".equals(arguments.get(0)) &&
                "EnglishLanguage".equals(arguments.get(1))) {
            String key = arguments.get(2);
            String value = arguments.get(3).replaceAll("^\"|\"$", ""); // Strip off opening and closing quotation marks
            List<String> valueList = termFormats.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
                termFormats.put(key, valueList);
            }
            valueList.add(value);
        }

        // Handle domain and domainSubclass entries
        if (arguments.size() > 3 &&
                ("domain".equals(arguments.get(0)) || "domainSubclass".equals(arguments.get(0)))) {
            String key = arguments.get(1); // the second argument

            List<List<String>> argLists = domains.get(key);
            if (argLists == null) {
                argLists = new ArrayList<>();
                domains.put(key, argLists);
            }
            argLists.add(arguments);
        }

        if (arguments.size() > 2 &&
                ("range".equals(arguments.get(0)) || "rangeSubclass".equals(arguments.get(0)))) {
            String key = arguments.get(1); // the second argument
            ranges.put(key, arguments);    // store the entire argument list as the value
        }

        // Save to terms map if first argument is a term creation argument,
        // there is a second argument, and it does NOT start with '?'
        if (arguments.size() > 1 &&
                TERM_CREATION_ARGUMENTS.contains(arguments.get(0)) &&
                !arguments.get(1).startsWith("?")) {

            terms.add(arguments.get(1));
            terms.add(arguments.get(2)); // Otherwise things like Entity will never get added, or if doing a domain ontology, the root level classes.
            switch (arguments.get(0)) {
                case "subAttribute":
                    subAttributes.add(arguments.get(1));
                    break;
                case "instance":
                    instances.add(arguments.get(1));
                    break;
                case "subrelation":
                    subrelations.add(arguments.get(1));
                    break;
                case "subclass":
                    subclasses.add(arguments.get(1));
                    break;
                default:
                    System.out.println("Error in KBLite, should not get here with line: " + arguments);
                    break;
            }

            String childKey = arguments.get(2);
            String childValue = arguments.get(1);
            List<String> childList = children.get(childKey);
            if (childList == null) {
                childList = new ArrayList<>();
                children.put(childKey, childList);
            }
            childList.add(childValue);

            String parentKey = arguments.get(1);
            String parentValue = arguments.get(2);
            List<String> parentList = parents.get(parentKey);
            if (parentList == null) {
                parentList = new ArrayList<>();
                parents.put(parentKey, parentList);
            }
            parentList.add(parentValue);
        }
    }


    /**
     * Splits a KIF formula into top-level arguments, preserving subformulas.
     * e.g., "(subclass bat (subformula function))" -> ["subclass", "bat", "(subformula function)"]
     */
    private List<String> splitFormulaArguments(String formula) {
        if (formula.startsWith("(") && formula.endsWith(")")) {
            formula = formula.substring(1, formula.length() - 1).trim();
        }
        else {
            return null; // Not a valid formula.
        }
        List<String> result = new ArrayList<>();
        int len = formula.length();
        int i = 0;
        while (i < len) {
            // Skip whitespace
            while (i < len && Character.isWhitespace(formula.charAt(i))) i++;
            if (i >= len) break;

            char c = formula.charAt(i);
            if (c == '(') {
                // Parse a balanced subformula
                int start = i;
                int balance = 0;
                do {
                    if (formula.charAt(i) == '(') balance++;
                    else if (formula.charAt(i) == ')') balance--;
                    i++;
                } while (i < len && balance > 0);
                result.add(formula.substring(start, i).trim());
            } else if (c == '"') {
                // Parse a quoted string
                int start = i;
                i++; // skip initial quote
                boolean escaped = false;
                while (i < len) {
                    char ch = formula.charAt(i);
                    if (ch == '\\' && !escaped) {
                        escaped = true;
                    } else if (ch == '"' && !escaped) {
                        i++; // include closing quote
                        break;
                    } else {
                        escaped = false;
                    }
                    i++;
                }
                result.add(formula.substring(start, i).trim());
            } else {
                // Parse a token until whitespace, '(' or '"'
                int start = i;
                while (i < len && !Character.isWhitespace(formula.charAt(i)) && formula.charAt(i) != '(' && formula.charAt(i) != '"') i++;
                result.add(formula.substring(start, i).trim());
            }
        }
        return result;
    }

    private void buildRelationsCache() {
        relations.addAll(getAllInstances("Relation"));
    }

    private void buildFunctionsCache() {
        functions.addAll(getAllInstances("Function"));
    }


    /**********************************************************************
        All the methods above are used to load the cache.
        All the methods below are used after the cache has been loaded.
     ********************************************************************/


    /** ***************************************************************
     *  Returns the format map in English only. Parameter lang is
     *  ignored. Only used for compatibility.
     */
    public Map<String, List<String>> getFormatMap() {
        return formats;
    }
    public Map<String, List<String>> getFormatMap(String lang) {
        return formats;
    }
    public Map<String, List<String>> getFormatMapAll(String lang) {
        return formats;
    }

    /** *****************************************************************
     *
     *  Returns true if the term is a subclass.
     */
    public boolean isSubclass (String term) {
        return subclasses.contains(term);
    }

    /***************************************************************
     * Returns
     * true if the subclass cache supports the conclusion that c1 is a subclass
     * of c2, else returns false.  Note that classes are also subclasses of
     * themselves. Note: will return false if childClass is an instance, even if
     * it is an instance of the parentClass.
     *
     * @param c1 A String, the name of a Class.
     * @param parent A String, the name of a Class.
     * @return boolean
     */
    public boolean subclassOf(String childClass, String parentClass) {return isSubclass(childClass, parentClass);}
    public boolean isSubclass (String childClass, String parentClass) {
        if (childClass == null || parentClass == null || childClass.isEmpty() || parentClass.isEmpty())
            return false;
        if (childClass.equals(parentClass))
           return true;
        Set<String> subclassesOfParent = getChildClasses(parentClass);
        return subclassesOfParent.contains(childClass);
    }

    public boolean isInstance (String term) {
        return instances.contains(term) || subrelations.contains(term) || subAttributes.contains(term);
    }

    /***************************************************************
     * Returns
     * true if i is an instance of c, else returns false.
     *
     * @param i A String denoting an instance.
     * @param c A String denoting a Class.
     * @return true or false.
     */
    public boolean isInstanceOf(String i, String c) {

        Set<String> childInstances = getAllInstances(c);
        return childInstances.contains(i);
    }


    public Set<String> getChildClasses(String cl) {

        Set<String> childClasses = new HashSet<>();
        Queue<String> childrenToProcess = new LinkedList<>();
        List<String> childrenOfCl = children.get(cl);
        if (childrenOfCl != null)
            childrenToProcess.addAll(childrenOfCl);
        while (!childrenToProcess.isEmpty()) {
            String child = childrenToProcess.poll();
            if (isSubclass(child)) {
                childClasses.add(child);
                List<String> childrenOfChild = children.get(child);
                if (childrenOfChild != null)
                    childrenToProcess.addAll(childrenOfChild);
            }
        }
        return childClasses;
    }

    public Set<String> getInstancesForType(String className) {
        return getAllInstances(className);
    }

    public Set<String> getAllInstances(String className) {
        if (className == null || className.isEmpty()) {
            return new TreeSet<>();
        }
        Set<String> instancesOfClassName = new TreeSet<>();
        Set<String> allSubclassesofClassName = getChildClasses(className);
        allSubclassesofClassName.add(className);
        Iterator<String> iterator = allSubclassesofClassName.iterator();
        while (iterator.hasNext()) {
            List<String> childrenOfClass = children.get(iterator.next());
            if (childrenOfClass != null) {
                for (String child : childrenOfClass) {
                    if (isInstance(child)) {
                        instancesOfClassName.add(child);
                    }
                }
            }
        }
        return instancesOfClassName;
    }

    public Map<String, List<String>> getTermFormatMap() {
        return termFormats;
    }

    /** ************************************************
     *
     *  Returns random term format.
     *  Currently Only supports English.
     */
    public String getTermFormat(String lang, String term) {
        return getTermFormat(term);
    }

    public String getTermFormat(String term) {
        List<String> termFormatsForTerm = termFormats.get(term);
        if (termFormatsForTerm == null) {
            System.out.println("No term format for: " + term);
            return null;
        }
        return termFormatsForTerm.get(rand.nextInt(termFormatsForTerm.size()));
    }

    /***************************************************************
     * Returns an ArrayList containing the Formulas that match the request.
     *
     * @param kind   May be one of "ant", "cons", "stmt", or "arg" ONLY SUPPORTS "arg"
     * @param term   The term that appears in the statements being requested.
     * @param argnum The argument position of the term being asked for. The first
     *               argument after the predicate is "1". This parameter is ignored
     *               if the kind is "ant", "cons" or "stmt".
     * @return An ArrayList of Formula(s), which will be empty if no match
     * found.
     * see KIF.createKey()
     */
    public List<Formula> ask(String kind, int argnum, String term) {
        // Only support for kind == "arg"
        // Format of rawFormulasWithArgs is the complete formula, then the args. example ["(subclass Cat Animal)", "subclass", "Cat", "Animal"]
        // The actual args in rawFormulasWithArgs starts at 1, so we have to add 1 to argnum when querying rawFormulasWithArgs.
        if (kind.equals("stmt")) { System.out.println("ERROR IN KBLite.ask(). Unsupported kind for kind: '" + kind + "' argnum: " + argnum + " term: " + term); return null;}
        argnum++;
        List<Formula> result = new ArrayList<>();
        for (List<String> formula: rawFormulasWithArgs) {
            if (argnum < formula.size()
                    && formula.get(argnum).equals(term)) {
                result.add(new Formula(formula.get(0)));
            }
        }
        return result;
    }

    /***************************************************************
     * @return an ArrayList of Formulas in which the two terms provided appear
     * in the indicated argument positions. If there are no Formula(s)
     * matching the given terms and respective argument positions,
     * return an empty ArrayList. Iterate through the smallest list of
     * results.
     */
    public List<Formula> askWithRestriction(int argnum1, String term1, int argnum2, String term2) {
        // See not on KBLite.ask() for why we increments the argsnums.
        argnum1++; argnum2++;
        List<Formula> result = new ArrayList<>();
        for (List<String> formula: rawFormulasWithArgs) {
            if (argnum1 < formula.size() && argnum2 < formula.size()
                    && formula.get(argnum1).equals(term1) && formula.get(argnum2).equals(term2)) {
                result.add(new Formula(formula.get(0)));
            }
        }
        return result;
    }

    /***************************************************************
     * Returns an
     * ArrayList of Formulas in which the two terms provided appear in the
     * indicated argument positions. If there are no Formula(s) matching the
     * given terms and respective argument positions, return an empty ArrayList.
     *
     * @return ArrayList
     */
    public List<Formula> askWithTwoRestrictions(int argnum1, String term1,
                                                int argnum2, String term2,
                                                int argnum3, String term3) {
        //see note on KBLite.ask() for why we increment the argnums.
        argnum1++; argnum2++; argnum3++;
        List<Formula> result = new ArrayList<>();
        for (List<String> formula: rawFormulasWithArgs) {
            if (argnum1 < formula.size() && argnum2 < formula.size() && argnum3 < formula.size()
                    && formula.get(argnum1).equals(term1) && formula.get(argnum2).equals(term2) && formula.get(argnum3).equals(term3)) {
                result.add(new Formula(formula.get(0)));
            }
        }
        return result;
    }

    /*************************************************************
     *  TODO: Does very basic syntax and type checking. This is good
     *  enough for basic sentence generation.
     */
/*    public boolean isValidFormula (String formula) {
        // Check balanced parentheses
        int count = 0;
        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (c == '(') {
                count++;
            } else if (c == ')') {
                count--;
                if (count < 0) return false;
            }
        }
        if (count != 0) return false;
        List<String> formArgs = splitFormulaArguments(formula);
        if (relations.contains(formArgs.get(0))) {
            if (relationTypesGood(formArgs)) {
                return true;
            }
        }
        return true;
    }
*/
    /***********************************************************
     *
     * @param formArgs - array of arguments. i.e. ['mother', 'mom', 'child']
     * @return true if the types of the input match the types of the domain.
     */
/*    private boolean relationTypesGood(List<String> formArgs) {
        List<List<String>> relDomains = domains.get(formArgs.get(0));
        if (relDomains.size() != formArgs.size()-1) {
            System.out.println("Wrong arity for formula: " + formArgs);
            return false;
        }
        for (int i=0; i<relDomains.size(); i++) {
            dClass = relDomains.get(i).get(3);
            fArg = formArgs.get(i);
            if (d.get(0).equals("domainSubclass")) {
                Set<String> dSubClasses =  getChildClasses(dClass);
                return false;
            } else {
                Set<String> dInstances = getAllInstances(dClass);

                return false;
            }
        }
        return true;
    }
*/
    public static void main(String[] args) {
        KBLite kbLite = new KBLite("SUMO");
        for (String f : kbLite.kifFiles) {
            System.out.println(f);
        }
        Set<String> birdClasses = kbLite.getChildClasses("Bird");
        for (String bird : birdClasses) {
            System.out.println(bird);
        }

    }
}
