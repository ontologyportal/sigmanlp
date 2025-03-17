package com.articulate.nlp.semRewrite;/*
Copyright 2014-2015 IPsoft

Author: Peigen You Peigen.You@ipsoft.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program ; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston,
MA  02111-1307 USA
*/

import com.articulate.sigma.Formula;
import com.articulate.sigma.KBmanager;
import com.google.common.collect.Lists;
import com.articulate.nlp.Document;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/************************************************************
 * A class for testing the performance of sigma-based system
 * It will generate CNF and KIF for given sentences and sent them to inference
 * and can also save the output to json file.
 *
 * To use:
 * run class's main under stanford parser directory with argument -h to see detailed guidence
 *
 *
 */
public class QAOutputGenerator {

    /************************************************************
     */
    public static List<String> getAllFilenamesInDir(String dir) throws IOException {

        List<String> res = new ArrayList<String>();
        Files.walk(Paths.get(dir)).forEach(filePath -> {
            if (Files.isRegularFile(filePath)) {
                System.out.println(filePath);
                res.add(filePath.getFileName().toString());
            }
        });
        return res;
    }

    /************************************************************
     */
    public static List<List<String>> extractFile(Path p) {

        List<String> querys = new ArrayList<>();
        List<String> anses = new ArrayList<>();
        List<List<String>> res = new ArrayList<>();

        try (Scanner in = new Scanner(p)) {
            String input;
            while (in.hasNextLine()) {
                input = in.nextLine();
                input = input.replaceAll("[\"]", "\\\"");
                //passage
                if (input.startsWith("&&")) {
                    try (PrintWriter out = new PrintWriter(p.toString() + ".passage")) {
                        input = in.nextLine();
                        while (input != null && !input.startsWith("&&")) {
                            out.append(input);
                            input = in.nextLine();
                        }
                        out.flush();
                    }
                    while (in.hasNext()) {
                        input = in.nextLine();
                        input = input.replaceAll("\"", "\\\"");
                        if (input.startsWith("@@")) {
                            input = input.substring(2);
                            querys.add(input);
                        }
                        if (input.startsWith("!!")) {
                            input = input.substring(2);
                            anses.add(input);
                        }
                        if (input.startsWith("??")) {
                            input = "not correct";
                            anses.add(input);
                        }
                    }
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        res.add(querys);
        res.add(anses);
        return res;
    }

    /************************************************************
     * Generate output from an array of strings
     */
    public static List<Record> getRecordFromStringSet(String[] strs, Interpreter inter) {

        List<Record> res = new ArrayList<>();
        Field questionfield;
        try {
            questionfield = inter.getClass().getDeclaredField("question");
            questionfield.setAccessible(true);
            Field userInputsfield = inter.getClass().getDeclaredField("userInputs");
            userInputsfield.setAccessible(true);
            Document userInputs = (Document) userInputsfield.get(inter);
            int i = 0;
            Record r;
            List<String> kifClauses;
            List<CNF> inputs;
            String s1, s2, s3, result;
            for (String s : strs) {
                r = new Record(i++, s);
                s = s.trim();
                if (s.endsWith("?"))
                    questionfield.setBoolean(inter, true);
                else
                    questionfield.setBoolean(inter, false);

                if (!questionfield.getBoolean(inter)) {
                    Interpreter.tfidf.addInput(s);
                }
                try {
                    inputs = Lists.newArrayList(inter.interpretGenCNF(s));
                    r.CNF = inputs.get(0);
                    kifClauses = inter.interpretCNF(inputs);
                }
                catch (Exception e) {
                    System.err.println("Paring error in sentence :" + s);
                    r.CNF = new CNF();
                    r.result = "Parsing error";
                    res.add(r);
                    continue;
                }
                s1 = inter.toFOL(kifClauses);
                s2 = Interpreter.postProcess(s1);
                s3 = inter.addQuantification(s2);
                r.KIF = new Formula(s3).toString();

                result = inter.fromKIFClauses(kifClauses);
                System.out.println("INFO in Interpreter.interpretSingle(): Theorem proving result: '" + result + "'");

                if (questionfield.getBoolean(inter)) {
                    if (("I don't know.".equals(result) && inter.autoir) || Interpreter.ir) {
                        if (inter.autoir) {
                            System.out.println("Interpreter had no response so trying TFIDF");
                        }
                        result = Interpreter.tfidf.matchInput(s);
                    }
                }
                else {
                    // Store processed sentence
                    userInputs.add(s);
                }

                //System.out.println("INFO in Interpreter.interpretSingle(): combined result: " + result);
                r.result = result;
                res.add(r);
            }
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (NoSuchFieldException e) {
            System.out.println("IO error");
            return null;
        }

        return res;
    }

    /************************************************************
     * save the record to a json file
     */
    public static void saveRecord(List<Record> list, String path) throws FileNotFoundException {

        if (list == null) {
            System.out.println("INFO in RewriteRuleUtil.saveRecord():  list is null");
            return;
        }
        try (PrintWriter pw = new PrintWriter(path)) {
            JSONArray arr = new JSONArray();
            JSONObject obj;
            for (Record k : list) {
                obj = new JSONObject();
                obj.put("index", k.index + "");
                obj.put("sentence", k.sen);
                obj.put("CNF", k.CNF.toString());
                obj.put("KIF", k.KIF);
                obj.put("result", k.result);
                arr.add(obj);
            }
            pw.println(arr.toJSONString());
            pw.flush();
        }
    }

    /************************************************************
     * class to store output
     */
    public static class Record {

        public int index;
        public String sen;
        public CNF CNF;
        public String KIF;
        public String result;

        public Record(int index, String sen) {

            this.index = index;
            this.sen = sen;
        }

        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"index\":\"").append(index).append("\",\n");
            sb.append("  \"sentence\":\"").append(sen).append("\",\n");
            sb.append("  \"CNF\":\"").append(CNF).append("\",\n");
            sb.append("  \"KIF\":\"").append(KIF).append("\",\n");
            sb.append("  \"result\":\"").append(result).append("\"\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    /************************************************************
     * generate output with filename and output to jasonfilename
     * both should with path
     */
    public static void generate(String inputPath, String outputPath, Interpreter inter) throws IOException {

        KBmanager.getMgr().initializeOnce();
        if (inter == null) {
            inter = new Interpreter();
            inter.initialize();
        }
        String[] strs = CommonCNFUtil.loadSentencesFromTxt(inputPath);
        System.out.println("Strins are: " + Arrays.toString(strs));
        List<Record> res = getRecordFromStringSet(strs, inter);
        System.out.println(res);
        try {
            saveRecord(res, outputPath);
        }
        catch (FileNotFoundException e) {
            System.err.println("Can't save file.");
            e.printStackTrace();
        }
    }

    /************************************************************
     * generate output with filename and output to jasonfilename
     * both should with path
     */
    public static void generateForDir(String dir, Interpreter inter) throws IOException {

        if (!dir.endsWith("/"))
            dir = dir + "/";
        KBmanager.getMgr().initializeOnce();
        if (inter == null) {
            inter = new Interpreter();
            inter.initialize();
        }
        List<String> files;
        try {
            files = getAllFilenamesInDir(dir);
        }
        catch (IOException e) {
            System.err.println("The directory input is not correct.");
            return;
        }
        for (String file : files) {
            if (file.startsWith(".") || !file.endsWith("txt"))
                continue;
            generateForFile(dir + file, inter);
        }

    }

    /************************************************************
     * generate output with filename and output to jasonfilename
     * both should with path
     */
    public static void generateForFile(String file, Interpreter inter) throws IOException {

        KBmanager.getMgr().initializeOnce();
        if (inter == null) {
            inter = new Interpreter();
            inter.initialize();
        }
        String inputPath = file;
        String outputPath = file.substring(0, file.lastIndexOf('/') + 1) + "Output-" + file.substring(file.lastIndexOf('/') + 1, file.lastIndexOf('.') + 1) + "json";
        System.out.println("Input file is :" + inputPath);
        System.out.println("Output file is :" + outputPath);
        generate(inputPath, outputPath, inter);
    }

    /************************************************************
     */
//    public static void testOnOnePassage() {
//
//        String path = "/Users/peigenyou/workspace/input.txt";
//        String outputPath = "/Users/peigenyou/workspace/output.json";
//        generate(path, outputPath);
//    }
//
//    /************************************************************
//     */
//    public static void testExtractFile() {
//
//        try {
//            ArrayList<Path> paths = getAllFilenamesInDir();
//            StringBuilder sb = new StringBuilder();
//            for (Path p : paths) {
//                ArrayList<ArrayList<String>> qa = extractFile(p);
//                for (int i = 0; i < qa.get(0).size(); ++i) {
//                    sb.append("{\n");
//                    sb.append("  \"file\":\"" + p.getFileName() + "\",\n");
//                    sb.append("  \"query\":\"" + qa.get(0).get(i) + "\",\n");
//                    sb.append("  \"answer\":\"" + qa.get(1).get(i) + "\"\n");
//                    sb.append("},\n");
//                }
//            }
//            try (PrintWriter out = new PrintWriter("/Users/peigenyou/Downloads/qa.json")) {
//                out.print(sb.toString());
//            }
//
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    public static void main(String[] args) throws IOException {

        System.out.println("INFO in QAOutputGenerator.main()");
        KBmanager.getMgr().initializeOnce();
        Interpreter inter = new Interpreter();
        inter.initialize();
        if (args != null && args.length > 1 && (args[0].equals("-f"))) {
            generateForFile(args[1], inter);
        }
        if (args != null && args.length > 1 && args[0].equals("-d")) {
            generateForDir(args[1], inter);
        }
        else if (args != null && args.length > 0 && args[0].equals("-h")) {
            System.out.println("Batch test tool of sigma.");
            System.out.println("  options:");
            System.out.println("  -h            - show this help screen");
            System.out.println("  -d directory  - runs on all the .txt file under directory");
            System.out.println("  -f filepath   - runs on one file");
            System.out.println("       All input file should be in a format of one sentence one line.");
        }
        else {
            try (Scanner in = new Scanner(System.in)) {
                String input = "";
                while (!input.equals("quit")) {
                    System.out.println("Please enter the file you want to test on:  'quit' to exit");
                    input = in.nextLine();
                    try {
                        generateForFile(input, inter);
                    }
                    catch (IOException e) {
                        System.err.println("The file input is invalid, please enter the fullpath with filename: eg. /Users/Obama/workspace/test.txt");
                        continue;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
