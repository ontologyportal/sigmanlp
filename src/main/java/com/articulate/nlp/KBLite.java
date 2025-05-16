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

    private List<String> kifFiles = new ArrayList<>();

    // Fast lookup map: key = second argument, value = argument list
    private final Map<String, List<List<String>>> terms = new HashMap<>();
    private final Map<String, String> documentation = new HashMap<>();
    private final Map<String, List<String>> termFormats = new HashMap<>();
    private final Map<String, List<String>> formats = new HashMap<>();
    private final Map<String, List<String>> children = new HashMap<>();
    private final Map<String, List<String>> parents = new HashMap<>();
    private final Map<String, List<List<String>>> domains = new HashMap<>();
    private final Map<String, List<String>> ranges = new HashMap<>();


    // Set of relevant first arguments
    private static final Set<String> TERM_CREATION_ARGUMENTS = new HashSet<>(Arrays.asList(
            "subAttribute", "instance", "subrelation", "subclass"
    ));

    /**
     * Constructor that takes the KB name and extracts the kif files for that KB.
     */
    public KBLite(String kbName) {
        System.out.println("WARNING: KBLite does not perform syntax, type check, or any other check to ensure the knowledge base is accurate. Only use after you are otherwise confident in the accuracy of the Knowledge Base");
        getKifFilesFromConfig(kbName);
        loadKifs();
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

                while ((line = reader.readLine()) != null) {
                    // Remove comments (everything after ';')
                    int commentIndex = line.indexOf(';');
                    if (commentIndex != -1) {
                        line = line.substring(0, commentIndex);
                    }
                    line = line.trim();
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
                            continue; // skip lines that don't start a formula
                        }
                    }

                    // Accumulate the line
                    formula.append(line).append(" ");

                    // Update parenthesis balance
                    for (char c : line.toCharArray()) {
                        if (c == '(') parenBalance++;
                        else if (c == ')') parenBalance--;
                    }

                    // If balanced, process the formula
                    if (inFormula && parenBalance == 0) {
                        processFormula(formula);
                        inFormula = false;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading file: " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }


    private void processFormula(String formula) {
        String formulaStr = formula.toString().replace("\n", "").trim();
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

        // Save to terms map if first argument is a term creation argument,
        // there is a second argument, and it does NOT start with '?'
        if (arguments.size() > 1 &&
                TERM_CREATION_ARGUMENTS.contains(arguments.get(0)) &&
                !arguments.get(1).startsWith("?")) {

            List<List<String>> argLists = terms.get(arguments.get(1));
            if (argLists == null) {
                argLists = new ArrayList<>();
                terms.put(arguments.get(1), argLists);
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


    public Set<String> getChildClasses(String cl) {

        Set<String> childClasses = new HashSet<>();
        Queue<String> childrenToProcess = new LinkedList<>();
        childrenToProcess.addAll(children(cl));
        while (!childrenToProcess.isEmpty()) {
            child = childrenToProcess.poll();
            childArgLists = terms.get(child);
            if (child.get(0).equals("subclass")) {
                childrenToProcess.addAll(children())
            }
        }
        return childClasses
    }


    public static void main(String[] args) {
        KBLite kbLite = new KBLite("SUMO");
        for (String f : kbLite.kifFiles) {
            System.out.println(f);
        }
        kbLite.children.forEach((key, valueList) -> {
            if (valueList.size() > 0) {
                System.out.println(key + " : " + valueList);
            }
        });
    }
}
