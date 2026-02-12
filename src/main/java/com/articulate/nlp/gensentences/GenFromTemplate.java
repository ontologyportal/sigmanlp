package com.articulate.nlp.gensentences;

import java.io.IOException;

/***************************************************************
 * Performs synthetic sentence generation from JSON templates.
 ***************************************************************/
public class GenFromTemplate {

    /***************************************************************
     * Entry point for loading and printing template metadata.
     ***************************************************************/
    public static void main(String[] args) {

        String home = System.getProperty("user.home");
        String templatePath = home + "/workspace/sigmanlp/src/main/java/com/articulate/nlp/gensentences/templates.json";
        if (args != null && args.length > 0) {
            if ("-h".equals(args[0]) || "--help".equals(args[0])) {
                System.out.println("Usage: GenFromTemplate <templates-json-file-path>");
                return;
            }
            templatePath = args[0];
        }
        try {
            Templates templates = Templates.read(templatePath);
            System.out.println("Loaded templates.json with " + templates.size() + " template(s).");
            Templates.Template template;
            int i = 0;
            while ((template = templates.getNext()) != null) {
                System.out.println("template[" + i + "]: " + template.getName()
                        + " (slots=" + template.getSlots().length + ")");
                i++;
            }
        }
        catch (IOException ex) {
            System.err.println("Unable to read templates.json: " + ex.getMessage());
        }
    }

}
