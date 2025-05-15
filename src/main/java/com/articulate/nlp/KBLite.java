package com.articulate.nlp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class KBLite {

    // Constant for the KB directory path, always ends with a separator
    private static final String KB_FILEPATH = System.getenv("SIGMA_HOME") + File.separator + "KBs" + File.separator;

    private List<String> kifFiles = new ArrayList<>();

    /**
     * Constructor that takes the KB name and extracts the kif files for that KB.
     */
    public KBLite(String kbName) {
        extractKifFiles(kbName);
    }

    // Private, non-static method to populate kifFiles
    private void extractKifFiles(String kbName) {
        kifFiles.clear(); // Clear previous results
        try {
            File file = new File(KB_FILEPATH + "config.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList kbList = doc.getElementsByTagName("kb");
            for (int i = 0; i < kbList.getLength(); i++) {
                Element kbElement = (Element) kbList.item(i);
                String nameAttr = kbElement.getAttribute("name");
                if (kbName.equals(nameAttr)) {
                    NodeList constituentList = kbElement.getElementsByTagName("constituent");
                    for (int j = 0; j < constituentList.getLength(); j++) {
                        Element consElem = (Element) constituentList.item(j);
                        String filename = consElem.getAttribute("filename");
                        if (filename != null && !filename.isEmpty()) {
                            kifFiles.add(filename);
                        }
                    }
                    break; // Only process the first matching <kb>
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Public getter for kifFiles
    public List<String> getKifFiles() {
        return kifFiles;
    }

    // Example usage
    public static void main(String[] args) {
        KBLite kbLite = new KBLite("SUMO");
        for (String f : kbLite.getKifFiles()) {
            System.out.println(f);
        }
    }
}
