package com.articulate.nlp.corpora;

import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBmanager;
import com.articulate.sigma.utils.StringUtil;

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

    // The term for which a start date has been generated.
    // This assumes car models are presented in year order.
    private static HashSet<String> startDateProduced = new HashSet<>();

    // in order to prevent duplicates
    private static HashSet<String> statements = new HashSet<>();

    /** ***************************************************************
     */
    public static void main(String[] args) throws IOException {

        HashSet<String> companies = new HashSet<>();
        //System.out.println("INFO in Cars.main()");
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
                    String make = m1.group(2);
                    make = make.replaceAll(" ","");
                    make = StringUtil.stringToKIFid(make);
                    String model = m1.group(3);
                    model = model.replaceAll(" ","");
                    model = StringUtil.stringToKIFid(model);
                    String name = make + model;
                    if (!startDateProduced.contains(name)) {
                        System.out.println("(firstInstanceCreated " + name + " (BeginFn (YearFn " + m1.group(1) + ")))");
                        startDateProduced.add(name);
                        statements.add("(subclass " + name + " " + make + "Automobile)");
                        statements.add("(termFormat EnglishLanguage " + name + " \"" + m1.group(2) + " " + m1.group(3) + "\")");
                        statements.add("(termFormat EnglishLanguage " + name + " \"" + m1.group(3) + "\")");
                        statements.add("(documentation " + name + " EnglishLanguage \"The " + m1.group(3) +
                                " model of cars made by " + m1.group(2) + " beginning in " + m1.group(1) + "\")");
                    }
                    companies.add("(subclass " + make + "Automobile Automobile)");
                    companies.add("(termFormat EnglishLanguage " + make + "Automobile \"" + make + "\")");
                }
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        for (String s : companies)
            System.out.println(s);
        for (String s : statements)
            System.out.println(s);
    }
}
