package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by apease on 6/25/18.
 * data courtesy of https://raw.githubusercontent.com/n8barr/automotive-model-year-data/master/data.sql
 */
public class Cars {

    private static KB kb = null;

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        HashSet<String> companies = new HashSet<>();
        System.out.println("INFO in Cars.main()");
        String filename = System.getenv("CORPORA") + File.separator + "cardata.sql";
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line;
            while ((line = lr.readLine()) != null) {
                //(1954, 'Cadillac', 'Fleetwood')
                Pattern p1 = Pattern.compile("^\\((\\d+), '([^']+)', '([^']+)'\\),");
                Matcher m1 = p1.matcher(line);
                if (m1.matches()) {
                    String make =  m1.group(2);
                    make = make.replaceAll(" ","");
                    String model = m1.group(3);
                    model = model.replaceAll(" ","");
                    String name = make + model;
                    System.out.println("(firstInstanceCreated " + name + " (BeginFn (YearFn " + m1.group(1) + ")))");
                    System.out.println("(subclass " + name + " " + make + "Automobile)");
                    companies.add("(subclass " + make + "Automobile Automobile)");
                    System.out.println("(termFormat EnglishLanguage " + name + " \"" + m1.group(2) + " " + m1.group(3) + "\")");
                    System.out.println("(termFormat EnglishLanguage " + name + " \"" + m1.group(3) + "\")");
                    System.out.println("(documentation " + name + " EnglishLanguage \"The " + m1.group(3) +
                            " model of cars made by " + m1.group(2) + " beginning in " + m1.group(1) + "\")");
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        for (String s : companies)
            System.out.println(s);
    }
}
