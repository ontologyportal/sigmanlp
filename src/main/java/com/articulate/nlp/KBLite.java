package com.articulate.nlp;

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


public class KBLite {

    // Constant for the KB directory path, always ends with a separator
    private static final String KB_FILEPATH = System.getenv("SIGMA_HOME") + File.separator + "KBs" + File.separator;
    public String kbDir = KB_FILEPATH; // kbDir used for compatibility purposes.
    private List<String> kifFiles = new ArrayList<>();

    // Fast lookup map: key = second argument, value = argument list
    private final Map<String, List<List<String>>> termArguments = new HashMap<>();
    public final Set<String> terms = new TreeSet<>();
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


    // Set of relevant first arguments
    private static final Set<String> TERM_CREATION_ARGUMENTS = new HashSet<>(Arrays.asList(
            "subAttribute", "instance", "subrelation", "subclass"
    ));

    /**
     * Constructor that takes the KB name and extracts the kif files for that KB.
     */
    public KBLite(String kbName) {
        System.out.println("\n*****************************************************\nWARNING: KBLite does not perform syntax, type check, \nor any other check to ensure the knowledge base is \naccurate. Only use after you are otherwise confident \nin the accuracy of the Knowledge Base.\n*****************************************************\n");
        getKifFilesFromConfig(kbName);
        System.out.println("Loading kif files into cache.");
        loadKifs();
        System.out.println("Files loaded.");
    }

    private void getKifFilesFromConfig(String kbName) {
        kifFiles.clear();
        try {
            File file = new File(KB_FILEPATH + "config.xml");
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
            File file = new File(KB_FILEPATH + kifFile);
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

        if (formulaStr.startsWith("(") && formulaStr.endsWith(")")) {
            formulaStr = formulaStr.substring(1, formulaStr.length() - 1).trim();
        }
        List<String> arguments = splitFormulaArguments(formulaStr);

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
            String value = arguments.get(3);
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
            String value = arguments.get(3);
            List<String> valueList = termFormats.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
                termFormats.put(key, valueList);
            }
            valueList.add(value);
        }

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

            List<List<String>> argLists = termArguments.get(arguments.get(1));
            if (argLists == null) {
                argLists = new ArrayList<>();
                termArguments.put(arguments.get(1), argLists);
            }
            argLists.add(arguments);

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
     * E.g., "subclass bat (subformula function)" -> ["subclass", "bat", "(subformula function)"]
     */
    private List<String> splitFormulaArguments(String formula) {
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

    public boolean isSubclass (String term) {
        return subclasses.contains(term);
    }

    public boolean isInstance (String term) {
        return instances.contains(term) || subrelations.contains(term) || subAttributes.contains(term);
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


    public static void main(String[] args) {
        KBLite kbLite = new KBLite("SUMO");
        for (String f : kbLite.kifFiles) {
            System.out.println(f);
        }
        Set<String> AnimalInstances = kbLite.getAllInstances("Relation");
        for (String item:AnimalInstances) {
            System.out.println("Relation instances: " + item);
        }
        System.out.println("Instance count: " + kbLite.instances.size());
    }
}
