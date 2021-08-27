package com.articulate.nlp.corpora;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Reddit {

    public static void extract(String input) {

        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines( Paths.get(input), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
            Pattern p = Pattern.compile("1qeIAgB0cPwnLhDF9XSiJM\">([^<]*)</p>");
            Matcher m = p.matcher(contentBuilder.toString());
            while (m.find()) {
                System.out.println(m.group(1));
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void findFnames(String sDir) {

        //System.out.println("findFnames(): " + sDir);
        File[] faFiles = new File(sDir).listFiles();
        for (File file: faFiles){
            if (file.getName().endsWith(".html")){
                extract(file.getAbsolutePath());
            }
            if (file.isDirectory()){
                findFnames(file.getAbsolutePath());
            }
        }
    }
    public static void main(String[] args) {

        String input = System.getenv("CORPORA") + File.separator + args[0];
        findFnames(input);
    }

}
