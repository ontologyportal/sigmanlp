package com.articulate.nlp.corpora;

import com.articulate.sigma.utils.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reddit {

    /** ***************************************************************
     */
    public static void extract(String input) {

        StringBuilder contentBuilder = new StringBuilder();
        Collection<String> files = FileUtil.walk(input);
        //System.out.println("Reddit.extract(): # files: " + files.size());
        for (String s : files)
            contentBuilder.append(FileUtil.readLines(s,false).toString()).append("\n");
        Pattern p = Pattern.compile("1qeIAgB0cPwnLhDF9XSiJM\">([^<]*)</p>");
        Matcher m = p.matcher(contentBuilder.toString());
        while (m.find()) {
            String text = m.group(1);
            text = text.replace("&#x27;","'");
            text = text.replace("[removed]","");
            text = text.replace("(nsfw)","'");
            System.out.println(text);
        }

        p = Pattern.compile("\"e\":\"text\",\"t\":\"([^\"]*)\"");
        m = p.matcher(contentBuilder.toString());
        while (m.find()) {
            String text = m.group(1);
            text = text.replace("&#x27;","'");
            System.out.println(m.group(1));
        }
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        String input = System.getenv("CORPORA") + File.separator + args[0];
        extract(input);
    }

}
