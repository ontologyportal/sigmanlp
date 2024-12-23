package com.articulate.nlp.corpora;

import com.google.common.collect.Lists;
import com.articulate.sigma.utils.StringUtil;
import com.articulate.sigma.utils.ProgressPrinter;
import com.articulate.nlp.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by apease on 9/4/15.
 * read data from https://github.com/shuzi/insuranceQA as per
 * http://arxiv.org/pdf/1508.01585.pdf
 */
public class ShuZiInsQA {

    Map<String,String> vocab = new HashMap<>();
    ArrayList<String> answers = new ArrayList<>();
    ArrayList<HashMap<Integer,HashSet<String>>> answerNgrams = new ArrayList<>(); // HashMap key is N
    Map<String,List<String>> training = new HashMap<>();
    ArrayList<String> resultHeader = null;

    public static boolean reduceData = true;
    // map of answer ID to answer line
    Map<Integer,Integer> idToLine = new HashMap<>();

    public class Dev {
        public String question = "";
        public HashMap<Integer,HashSet<String>> questionNgrams = new HashMap<>();
        public List<String> answersID = new ArrayList<>();

        // note that this is a partial selected set of 500, rather than the
        // full 24,000+
        public List<String> wrongAnswerIDs = new ArrayList<>();

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(question + "\n");
//            if (answersID.size() > 0)
//                sb.append(answersID.get(0) + ":" +
//                        StringUtil.getFirstNChars(answers.get(Integer.parseInt(answersID.get(0))),40));
            return sb.toString();
        }
    }

    /****************************************************************
     * @return an ordered list of text lines from a file
     */
    private List<String> readLines(String filename) {

        List<String> lines = new ArrayList<String>();
        //System.out.println("INFO in ShuZiInsQA.readLines(): Reading files");
        LineNumberReader lr = null;
        try {
            String line;
            File nounFile = new File(filename);
            if (nounFile == null) {
                System.out.println("Error in readLines(): The file '" + filename + "' does not exist ");
                return lines;
            }
            long t1 = System.currentTimeMillis();
            FileReader r = new FileReader(nounFile);
            lr = new LineNumberReader(r);
            while ((line = lr.readLine()) != null) {
                if (lr.getLineNumber() % 1000 == 0)
                    System.out.print('.');
                lines.add(line);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        finally {
            try {
                if (lr != null) {
                    lr.close();
                }
            }
            catch (Exception ex) {
            }
        }
        return lines;
    }

    /****************************************************************
     * vocabulary
     * <word index><TAB><original word>
     * 400KB file
     */
    private void readVocab() {

        List<String> lines = readLines(System.getProperty("user.home") + "/IPsoft/insuranceQA-master/vocabulary-mod");
        for (String l : lines) {
            String[] elements = l.split("\\t");
            if (elements.length == 2) {
                vocab.put(elements[0], elements[1]);
                //System.out.println(elements[0] + "," + elements[1]);
            }
        }
    }

    /****************************************************************
     */
    private String listAsTable(List<Float> l) {

        DecimalFormat myFormatter = new DecimalFormat("###.##");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i != 0)
                sb.append("\t");
            sb.append(myFormatter.format(l.get(i)));
        }
        return sb.toString();
    }

    /****************************************************************
     */
    private HashMap<Integer,HashSet<String>> addNgrams(TFIDF tfidf, String s) {

        HashMap<Integer,HashSet<String>> result = new HashMap<>();
        int nMax = 3;
        String str1 = tfidf.removePunctuation(s);
        str1 = tfidf.removeStopWords(str1);
        ArrayList<String> s1 = new ArrayList<String>();
        String[] sspl = str1.split(" ");
        s1.addAll(Arrays.asList(sspl));

        for (int n = 2; n < nMax; n++) {
            HashSet<String> ngrams = new HashSet<>();
            for (int i = 0; i < s1.size() - n; i++) {
                StringBuilder s1tok = new StringBuilder();
                for (int z = 0; z < n; z++)
                    s1tok.append(s1.get(i + z));
                ngrams.add(s1tok.toString());
            }
            result.put(n, ngrams);
        }
        return result;
    }

    /****************************************************************
     * <answer label><TAB><answer text in word index form>
     * To get the word of from its index idx_* ,  please use the file vocabulary
     * 22MB file.  We rely strictly on that the line number of an answer is
     * its ID.
     */
    private void readAnswers() {

        TFIDF cb = null;
        try {
            cb = new TFIDF(System.getenv("SIGMA_HOME") + "/KBs/WordNetMappings/stopwords.txt");
        }
        catch (IOException ioe) {
            System.out.println("Error in ShuZiInsQA.readAnswers()");
            ioe.printStackTrace();
        }
        answers.add(""); // make 0th element blank
        answerNgrams.add(new HashMap<>());
        int linenum = 1;
        List<String> lines = readLines(System.getProperty("user.home") + "/IPsoft/insuranceQA-master/answers.label.token_idx");
        for (String l : lines) {
            String[] elements = l.split("\\t");
            String answerID = elements[0];
            String sentence = elements[1];
            String[] words = sentence.split(" ");
            StringBuilder sent = new StringBuilder();
            for (String id : words) {
                String word = vocab.get(id);
                sent = sent.append(word + " ");
            }
            sent.deleteCharAt(sent.length() - 1);
            //if (answerID.equals("8362"))
            //    System.out.println("readAnswers(): line " + linenum + " " + answerID + " " + sent);
            answers.add(sent.toString());
            answerNgrams.add(addNgrams(cb, sent.toString()));
            if (!answerID.equals(Integer.toString(linenum)))
                System.out.println("Error in readAnswers(): no match for line and ID " + linenum + " " + answerID);
            idToLine.put(Integer.parseInt(answerID),linenum);
            linenum++;
            //System.out.println(answerID + ":" + sent);
        }
        //System.out.println(answers);
    }

    /****************************************************************
     * <question text in word index form><TAB><answer labels>
     * To get the word of from its index idx_* ,  please use the file vocabulary
     * 1MB file
     * Note that these are not currently used
     */
    private void readTrainingQuestions() {

        List<String> lines = readLines(System.getProperty("user.home") + "/IPsoft/insuranceQA-master/question.train.token_idx.label");
        for (String l : lines) {
            String[] elements = l.split("\\t");
            String answer = elements[1];
            String[] answerIDs = answer.split(" ");
            String sentence = elements[0];
            String[] words = sentence.split(" ");
            StringBuilder question = new StringBuilder();
            for (String id : words) {
                String word = vocab.get(id);
                question = question.append(word + " ");
            }
            question.deleteCharAt(question.length() - 1);
            ArrayList<String> answerSet = new ArrayList<>();
            for (String s : answerIDs) {
                answerSet.add(answers.get(Integer.parseInt(s)) + "\n");
            }
            training.put(question.toString(), answerSet);
            //System.out.println(question + ":" + answerSet);
        }
        //System.out.println(answers);
    }

    /****************************************************************
     * question.(dev|test1|test2).label.token_idx.pool:
     * <ground truth labels><TAB><question text in word index form><TAB><answer candidate pool>
     * To get the word of from its index idx_* ,  please use the file vocabulary
     * Notice we make an answer candidate pool with size 500 here for dev, test1 and test2.
     * If running time is not a problem for your application, you are surely encouraged to use
     * the whole answer set as the pool (label 1-24981)
     */
    private List<Dev> readDevTestQuestions() {

        List<Dev> result = new ArrayList<>();
        TFIDF cb = null;
        try {
            cb = new TFIDF(System.getenv("SIGMA_HOME") + "/KBs/WordNetMappings/stopwords.txt");
        }
        catch (IOException ioe) {
            System.out.println("Error in ShuZiInsQA.readAnswers()");
            ioe.printStackTrace();
        }
        List<String> lines = readLines(System.getProperty("user.home") + "/IPsoft/insuranceQA-master/question.dev.label.token_idx.pool");
        int count = 0;
        for (String l : lines) {
            if (StringUtil.emptyString(l)) continue;
            if (reduceData && count++ != 10) continue; // skip for debugging
            count = 0;
            String[] elements = l.split("\\t");
            String answersAr = elements[0];
            String[] rightAnswers = answersAr.split(" ");
            String sentence = elements[1];
            String[] words = sentence.split(" ");
            String wrongAnswers = elements[2];
            String[] wrongAnswerIDsAr = wrongAnswers.split(" ");

            StringBuilder question = new StringBuilder();
            for (String id : words) {
                String word = vocab.get(id);
                question = question.append(word + " ");
            }
            question.deleteCharAt(question.length() - 1);

            Dev dev = new Dev();
            dev.question = question.toString();
            dev.questionNgrams = addNgrams(cb, question.toString());
            dev.answersID = (ArrayList<String>) Arrays.stream(rightAnswers).collect(Collectors.toList());
            //(ArrayList<String>) Arrays.asList(rightAnswers);

            //System.out.println("ShuZiInsQA.readDevTestQuestions(): " + dev.answersID);
            for (String s : wrongAnswerIDsAr) {
                if (!dev.answersID.contains(s))
                    dev.wrongAnswerIDs.add(s);
            }
            result.add(dev);
        }
        return result;
    }

    /****************************************************************
     * question.(dev|test1|test2).label.token_idx.pool:
     * <ground truth labels><TAB><question text in word index form><TAB><answer candidate pool>
     * To get the word of from its index idx_* ,  please use the file vocabulary
     * Notice we make an answer candidate pool with size 500 here for dev, test1 and test2.
     * If running time is not a problem for your application, you are surely encouraged to use
     * the whole answer set as the pool (label 1-24981)
     */
    private ArrayList<Dev> readTestQuestionFile(String filename) {

        TFIDF cb = null;
        try {
            cb = new TFIDF(System.getenv("SIGMA_HOME") + "/KBs/WordNetMappings/stopwords.txt");
        }
        catch (IOException ioe) {
            System.out.println("Error in ShuZiInsQA.readAnswers()");
            ioe.printStackTrace();
        }
        System.out.println("ShuZiInsQA.readTestQuestionFile(): " + filename);
        ArrayList<Dev> test = new ArrayList<>();
        List<String> lines = readLines(filename);
        int count = 0;
        for (String l : lines) {
            if (StringUtil.emptyString(l)) continue;
            if (reduceData && count++ != 10) continue; // skip for debugging
            count = 0;
            String[] elements = l.split("\\t");
            String answersAr = elements[0];
            String[] rightAnswers = answersAr.split(" ");
            String sentence = elements[1];
            String[] words = sentence.split(" ");
            String wrongAnswers = elements[2];
            String[] wrongAnswerIDsAr = wrongAnswers.split(" ");

            StringBuilder question = new StringBuilder();
            for (String id : words) {
                String word = vocab.get(id);
                question = question.append(word + " ");
            }
            question.deleteCharAt(question.length() - 1);

            Dev dev = new Dev();
            dev.question = question.toString();
            dev.questionNgrams = addNgrams(cb, question.toString());
            dev.answersID = (ArrayList<String>) Arrays.stream(rightAnswers).collect(Collectors.toList());
            //(ArrayList<String>) Arrays.asList(rightAnswers);

            //System.out.println("ShuZiInsQA.readDevTestQuestions(): " + dev.answersID);
            for (String s : wrongAnswerIDsAr) {
                if (!dev.answersID.contains(s))
                    dev.wrongAnswerIDs.add(s);
            }
            test.add(dev);
        }
        return test;
    }

    /****************************************************************
     */
    private ArrayList<Dev> readTestQuestionOneFile() {

        ArrayList<Dev> test = readTestQuestionFile(System.getProperty("user.home") + "/IPsoft/insuranceQA-master/question.dev.label.token_idx.pool");
        return test;
    }

    /****************************************************************
     * @param ansID is the ID of the answer
     * @param candidates is an ordered map of the score and the list
     *                   of candidate matches that share that score
     */
    private double scoreCandidate(TreeMap<Float,ArrayList<Integer>> candidates, String ansID) {

        int ansInt = idToLine.get(Integer.parseInt(ansID));
        //System.out.println("ShuZiInsQA.scoreCandidates(): searching for answer id: " + ansInt);
        double result = 0.0;
        ArrayList<Float> fAr = new ArrayList<>();
        fAr.addAll(candidates.descendingKeySet());  // search from high to low match scores
        int index = 0;
        boolean found = false;
        while (index < fAr.size() && index < 10 && !found) {
            //System.out.println("ShuZiInsQA.scoreCandidates(): score: " + fAr.get(index));
            //if (fAr.get(index) > 1)
               // System.out.println("ShuZiInsQA.scoreCandidates(): " + StringUtil.getFirstNChars(candidates.get(fAr.get(index)).toString(),100));
            //    System.out.println("ShuZiInsQA.scoreCandidates(): " + candidates.get(fAr.get(index)));
            if (candidates.get(fAr.get(index)).contains(ansInt)) {
                found = true;
                //System.out.println("ShuZiInsQA.scoreCandidates(): index of sentence: " + index);
            }
            else
                index++;
        }
        if (!found)
            result = -1.0;
        else
            result = (int) (10.0 - index);
            //result = (int) (10.0 - (index / (candidates.keySet().size() / 10.0)));
        //result = (result - 4.5) / 5.5;
        return result;
    }

    /****************************************************************
     * @return a set of scores where each array element is an array of
     * doubles - answer ID and then  a score for each feature, and a binary value for whether the answer
     * is in fact a valid answer for the question in dev
     */
    private ArrayList<ArrayList<Double>> scoreOneDev(Dev dev,
                                                     TFIDF cb,
                                                     TokenOverlap to,
                                                     NGramOverlap ng,
                                                     SynsetOverlap so,
                                                     SUMOOverlap sumo) {

        ArrayList<ArrayList<Double>> result = new ArrayList<>();
        //System.out.println("ShuZiInsQA.scoreOneDev(): " + dev);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> tfidfCandidates = new TreeMap<>();
        tfidfCandidates = cb.rank(dev.question,dev.answersID,tfidfCandidates);
        tfidfCandidates = cb.rank(dev.question,dev.wrongAnswerIDs,tfidfCandidates);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> overlapCandidates = new TreeMap<>();
        overlapCandidates = to.rank(dev.question,dev.answersID,overlapCandidates);
        overlapCandidates = to.rank(dev.question,dev.wrongAnswerIDs,overlapCandidates);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> nGramCandidatesN2 = new TreeMap<>();
        nGramCandidatesN2 = ng.nGramRank(dev, dev.answersID, answerNgrams, nGramCandidatesN2, 2);
        nGramCandidatesN2 = ng.nGramRank(dev, dev.wrongAnswerIDs, answerNgrams, nGramCandidatesN2, 2);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> nGramCandidatesN3 = new TreeMap<>();
        nGramCandidatesN3 = ng.nGramRank(dev, dev.answersID, answerNgrams, nGramCandidatesN3,3);
        nGramCandidatesN3 = ng.nGramRank(dev, dev.wrongAnswerIDs, answerNgrams, nGramCandidatesN3,3);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> synOverlap = new TreeMap<>();
        synOverlap = so.rank(dev.question, dev.answersID, synOverlap);
        synOverlap = so.rank(dev.question, dev.wrongAnswerIDs, synOverlap);

        // key is the match score and value is the list of line numbers that have that score
        TreeMap<Float,ArrayList<Integer>> sumoOverlap = new TreeMap<>();
        sumoOverlap = sumo.rank(dev.question, dev.answersID, sumoOverlap);
        sumoOverlap = sumo.rank(dev.question, dev.wrongAnswerIDs, sumoOverlap);

        for (String ansID : dev.answersID) {
            double tfScore = scoreCandidate(tfidfCandidates,ansID);
            double toScore = scoreCandidate(overlapCandidates,ansID);
            double ng2Score = scoreCandidate(nGramCandidatesN2,ansID);
            double ng3Score = scoreCandidate(nGramCandidatesN3,ansID);
            double soScore = scoreCandidate(synOverlap,ansID);
            double sumoScore = scoreCandidate(sumoOverlap,ansID);

            //System.out.println("ShuZiInsQA.scoreOneDev(): score for ngram overlap: " + ngScore);
            ArrayList<Double> inputLine = new ArrayList<>();
            inputLine.add(Double.parseDouble(ansID));
            inputLine.add(tfScore);
            inputLine.add(toScore);
            inputLine.add(ng2Score);
            inputLine.add(ng3Score);
            inputLine.add(soScore);
            inputLine.add(sumoScore);
            inputLine.add(1.0); // a correct answer
            result.add(inputLine);
            //System.out.println("ShuZiInsQA.scoreOneDev():" + StringUtil.removeEnclosingCharPair(inputLine.toString(), 1, '[', ']'));
        }
        Iterator<String> it = dev.wrongAnswerIDs.iterator();
        int count = 0;
        while (it.hasNext()) {
        //while (it.hasNext() && count < 2) {
            count++;
            String ansID = it.next();
            //System.out.println("ShuZiInsQA.devsToInputs(): non-answer candidate: " + answers.get(Integer.parseInt(ansID)));
            double tfScore = scoreCandidate(tfidfCandidates,ansID);
            double toScore = scoreCandidate(overlapCandidates,ansID);
            double ng2Score = scoreCandidate(nGramCandidatesN2,ansID);
            double ng3Score = scoreCandidate(nGramCandidatesN3,ansID);
            double soScore = scoreCandidate(synOverlap,ansID);
            double sumoScore = scoreCandidate(sumoOverlap,ansID);

            ArrayList<Double> inputLine = new ArrayList<>();
            inputLine.add(Double.parseDouble(ansID));
            inputLine.add(tfScore);
            inputLine.add(toScore);
            inputLine.add(ng2Score);
            inputLine.add(ng3Score);
            inputLine.add(soScore);
            inputLine.add(sumoScore);
            inputLine.add(0.0); // an incorrect answer
            result.add(inputLine);
            //System.out.println("ShuZiInsQA.scoreOneDev():" + StringUtil.removeEnclosingCharPair(inputLine.toString(), 1, '[', ']'));
        }
        return result;
    }

    /****************************************************************
     * Create a table of scores to train from.  For each test question
     * score the correct answer(s) and negative answers with TFIDF and
     * term overlap and add to the table.  First element of each line
     * is the answer ID
     */
    private ArrayList<ArrayList<Double>> devsToInputs(List<Dev> devs,
                                                      TFIDF cb,
                                                      TokenOverlap to,
                                                      NGramOverlap ng,
                                                      SynsetOverlap so,
                                                      SUMOOverlap sumo) {

        ArrayList<ArrayList<Double>> result = new ArrayList<ArrayList<Double>>();
        ProgressPrinter pp = new ProgressPrinter(10);
        for (Dev dev : devs) {
            pp.tick();
            ArrayList<ArrayList<Double>> res = scoreOneDev(dev, cb, to, ng, so, sumo);
            for (ArrayList<Double> ar : res)
                ar.remove(0); // remove the answer number
            result.addAll(res);
        }
        System.out.println();

        return result;
    }

    /****************************************************************
     * @return an ArrayList of ArrayLists the same size as the input
     * but with Integer elements converted to String
     */
    private ArrayList<ArrayList<String>> matrixDoubleToString(ArrayList<ArrayList<Double>> input) {

        ArrayList<ArrayList<String>> result = new ArrayList<>();
        for (ArrayList<Double> row : input) {
            ArrayList<String> resultRow = new ArrayList<String>();
            for (Double i : row)
                resultRow.add(Double.toString(i));
            result.add(resultRow);
        }
        return result;
    }

    /****************************************************************
     */
    private List<NaiveBayes> createTrainingClasses(List<Dev> devs,
                                                   TFIDF cb,
                                                   TokenOverlap to,
                                                   NGramOverlap ng,
                                                   SynsetOverlap so,
                                                   SUMOOverlap sumo,
                                                   ArrayList<ArrayList<Double>> inputs,
                                                   ArrayList<String> labels,
                                                   ArrayList<String> types) {

        System.out.println("ShuZiInsQA.createTrainingClasses(): Naive Bayes ");
        System.out.println("ShuZiInsQA.createTrainingClasses(): starting timer ");
        long t1 = System.currentTimeMillis();
        List<NaiveBayes> bayesList = new ArrayList<>();
        ArrayList<ArrayList<String>> newinputs = matrixDoubleToString(inputs);
        // add types header and labels header
        NaiveBayes nb = new NaiveBayes(newinputs,labels,types);
        nb.initialize();
        bayesList.add(nb);  // full feature set

        labels.remove(5);
        types.remove(5);
        newinputs.stream()
                .forEach(a -> a.remove(5));
        NaiveBayes nb5 = new NaiveBayes(newinputs,labels,types);
        nb5.initialize();
        bayesList.add(nb5); // no SUMO overlap

        labels.remove(4);
        types.remove(4);
        newinputs.stream()
                .forEach(a -> a.remove(4));
        NaiveBayes nb4 = new NaiveBayes(newinputs,labels,types);
        nb4.initialize();
        bayesList.add(nb4); // no synset overlap

        labels.remove(3);
        types.remove(3);
        newinputs.stream()
                .forEach(a -> a.remove(3));
        NaiveBayes nb2 = new NaiveBayes(newinputs,labels,types);
        nb2.initialize();
        bayesList.add(nb2); // no trigrams

        labels.remove(2);
        types.remove(2);
        newinputs.stream()
                .forEach(a -> a.remove(2));
        NaiveBayes nb3 = new NaiveBayes(newinputs,labels,types);
        nb3.initialize();
        bayesList.add(nb3); // no bigrams
        long t2 = System.currentTimeMillis();
        System.out.println("ShuZiInsQA.createTrainingClasses(): total time: " + (t2 - t1) / 1000 + " seconds");
        return bayesList;
    }

    /****************************************************************
     */
    private List<LogisticRegression> createTrainingClassesLR(List<Dev> devs,
                                                             TFIDF cb,
                                                             TokenOverlap to,
                                                             NGramOverlap ng,
                                                             SynsetOverlap so,
                                                             SUMOOverlap sumo,
                                                             ArrayList<ArrayList<Double>> inputs,
                                                             ArrayList<String> labels,
                                                             ArrayList<String> types) {

        System.out.println("ShuZiInsQA.createTrainingClassesLR(): starting timer ");
        long t1 = System.currentTimeMillis();
        List<LogisticRegression> lrList = new ArrayList<>();
        ArrayList<ArrayList<String>> newinputs = matrixDoubleToString(inputs);
        // add types header and labels header
        LogisticRegression lr = new LogisticRegression(newinputs,labels,types);
        lr.init();
        lr.trainAdaGrad();
        lr.save();
        lrList.add(lr);  // full feature set

        labels.remove(5);
        types.remove(5);
        newinputs.stream()
                .forEach(a -> a.remove(5));
        LogisticRegression lr5 = new LogisticRegression(newinputs,labels,types);
        lr5.init();
        lr5.trainAdaGrad();
        lrList.add(lr5); // no SUMO overlap

        labels.remove(4);
        types.remove(4);
        newinputs.stream()
                .forEach(a -> a.remove(4));
        LogisticRegression lr4 = new LogisticRegression(newinputs,labels,types);
        lr4.init();
        lr4.trainAdaGrad();
        lrList.add(lr4); // no synset overlap

        labels.remove(3);
        types.remove(3);
        newinputs.stream()
                .forEach(a -> a.remove(3));
        LogisticRegression lr2 = new LogisticRegression(newinputs,labels,types);
        lr2.init();
        lr2.trainAdaGrad();
        lrList.add(lr2); // no trigrams

        labels.remove(2);
        types.remove(2);
        newinputs.stream()
                .forEach(a -> a.remove(2));
        LogisticRegression lr3 = new LogisticRegression(newinputs,labels,types);
        lr3.init();
        lr3.trainAdaGrad();
        lrList.add(lr3); // no bigrams

        long t2 = System.currentTimeMillis();
        System.out.println("ShuZiInsQA.createTrainingClassesLR(): total time: " + (t2 - t1) / 1000 + " seconds");
        return lrList;
    }

    /****************************************************************
     * get the results on the test set for TFIDF and term overlap, then
     * classify it with naive bayes, then compare that classification to
     * the actual classification.
     * @return a list of scores for each question and candidate answer.
     * we get a rank for TFIDF and term overlap followed by a binary
     * answer for naive bayes and lastly the binary classification of
     * whether the sentence really is the answer.
     */
    private ArrayList<ArrayList<Double>> classify(ArrayList<Dev> test,
                                                  List<NaiveBayes> nbList,
                                                  List<LogisticRegression> lrList,
                                                  TFIDF cb, TokenOverlap to,
                                                  NGramOverlap ng, SynsetOverlap so,
                                                  SUMOOverlap sumo) {

        System.out.print("ShuZiInsQA.classify(): starting timer");
        long t1 = System.currentTimeMillis();
        ProgressPrinter pp = new ProgressPrinter(10);
        ArrayList<ArrayList<Double>> result = new ArrayList<ArrayList<Double>>();
        for (int i = 0; i < 10; i++)
            System.out.println("ShuZiInsQA.classify(): " + test.get(i));

        for (Dev dev : test) {
            //System.out.println("ShuZiInsQA.classify(): dev: " + dev);
            pp.tick();
            ArrayList<ArrayList<Double>> oneDev = scoreOneDev(dev, cb, to, ng, so,sumo);
            ArrayList<ArrayList<String>> processedDev = matrixDoubleToString(oneDev);
            for (ArrayList<String> line : processedDev) {
                String answer = line.get(line.size()-1); // scoreOneDev returns the target answer
                line.remove(line.size()-1);
                ArrayList<Double> oneLine = new ArrayList<>();
                String ansID = line.get(0);
                line.remove(0);
                oneLine.add(Double.parseDouble(line.get(0))); // tfidf
                oneLine.add(Double.parseDouble(line.get(1))); // term overlap
                oneLine.add(Double.parseDouble(line.get(2))); // bigram overlap
                oneLine.add(Double.parseDouble(line.get(3))); // trigram overlap
                oneLine.add(Double.parseDouble(line.get(4))); // synset overlap
                oneLine.add(Double.parseDouble(line.get(5))); // sumo overlap
                ArrayList<String> shortened = new ArrayList<>(line);
                for (NaiveBayes nb : nbList) {
                    //System.out.println("ShuZiInsQA.classify(): header: " + nb.labels);
                    String clss = nb.classify(shortened);
                    shortened.remove(shortened.size() - 1);  // remove the last feature
                    oneLine.add(Double.parseDouble(clss));        // naive bayes combined
                }
                shortened = new ArrayList<>(line);
                for (LogisticRegression lr : lrList) {
                    //System.out.println("ShuZiInsQA.classify(): header: " + nb.labels);
                    //System.out.println("ShuZiInsQA.classify(): line: " + shortened);
                    //System.out.println("ShuZiInsQA.classify(): lr dim: " + lr.dim);
                    //System.out.println("ShuZiInsQA.classify(): input: " + shortened);
                    String clss = lr.classify(shortened);
                    shortened.remove(shortened.size() - 1);  // remove the last feature
                    oneLine.add(Double.parseDouble(clss));        // logistic regression combined
                }
                oneLine.add(Double.parseDouble(answer)); // 1=answer 0=not the answer
                result.add(oneLine);
                //System.out.println("ShuZiInsQA.classify(): result: " + oneLine);
            }
        }
        System.out.println();
        long t2 = System.currentTimeMillis();
        System.out.println("ShuZiInsQA.main(): total time: " + (t2-t1)/1000 + " seconds");
        return result;
    }

    /****************************************************************
     * @return the top answer candidate ID by algorithm,
     */
    private ArrayList<Map<String,Integer>> top1(ArrayList<Dev> test,
                                                List<NaiveBayes> nbList,
                                                List<LogisticRegression> lrList,
                                                TFIDF cb, TokenOverlap to,
                                                NGramOverlap ng, SynsetOverlap so,
                                                SUMOOverlap sumo) {

        System.out.println("ShuZiInsQA.top1(): starting timer");
        long t1 = System.currentTimeMillis();
        ProgressPrinter pp = new ProgressPrinter(10);
        ArrayList<Map<String,Integer>> result = new ArrayList<Map<String,Integer>>();
        for (int i = 0; i < 10; i++)
            System.out.println("ShuZiInsQA.top1(): " + test.get(i));

        for (Dev dev : test) {
            pp.tick();
            ArrayList<ArrayList<Double>> oneDev = scoreOneDev(dev, cb, to, ng, so, sumo);
            ArrayList<ArrayList<String>> processedDev = matrixDoubleToString(oneDev);
            Map<String,Integer> oneQuestion = new HashMap<>();

            String ansID = "";
            for (ArrayList<String> line : processedDev) {
                //System.out.println("ShuZiInsQA.top1(): original line: " + line);
                String answer = line.get(line.size()-1); // scoreOneDev returns the target answer
                // score one dev puts answerID first
                //System.out.println("ShuZiInsQA.top1(): line: " + line);
                Map<String,Double> maxes = new HashMap<>();
                ArrayList<String> shortened = new ArrayList<>(line);
                shortened.remove(line.size() - 1);
                shortened.remove(0);
                for (LogisticRegression lr : lrList) {
                    String lrName = "LR" + Integer.toString(shortened.size()-1);
                    double clss = lr.classifyContinuous(shortened);
                    shortened.remove(shortened.size() - 1);  // remove the last feature
                    if (!maxes.containsKey(lrName) || maxes.get(lrName) < clss) {
                        maxes.put(lrName,clss);
                        ansID = shortened.get(0);
                        oneQuestion.put(lrName,(int) Double.parseDouble(ansID));
                    }
                }
            }
            System.out.println("ShuZiInsQA.top1(): one question: " + oneQuestion);
            System.out.println("ShuZiInsQA.top1(): correct answers: " + dev.answersID);
            result.add(oneQuestion);
        }
        System.out.println();

        long t2 = System.currentTimeMillis();
        System.out.println("ShuZiInsQA.top1(): total time: " + (t2-t1)/1000 + " seconds");
        return result;
    }

    /****************************************************************
     */
    private void printMetrics(ArrayList<Float> scoresPositive,
                              ArrayList<Float> scoresNegative,
                              int numPos, int numNeg, List<String> resultHeader,
                              int classIndex) {

        if (scoresPositive.size() != scoresNegative.size())
            System.out.println("Error in ShuZiInsQA.printMetrics(): score lists don't match");
        ArrayList<Float> recall = new ArrayList<Float>();
        ArrayList<Float> precision = new ArrayList<Float>();
        ArrayList<Float> F1 = new ArrayList<Float>();
        for (int i = 0; i < scoresNegative.size(); i++) {
            recall.add(Float.valueOf(0));
            precision.add(Float.valueOf(0));
            F1.add(Float.valueOf(0));
        }
        for (int i = 0; i < scoresNegative.size(); i++) {
            //recall.set(i,scoresPositive.get(i) / (scoresPositive.get(i) + (numNeg - scoresNegative.get(i))));
            recall.set(i,scoresPositive.get(i) / numPos);
            precision.set(i,scoresPositive.get(i) / (scoresPositive.get(i) + (numPos - scoresPositive.get(i))));
            F1.set(i,2 * (precision.get(i) * recall.get(i)) / (precision.get(i) + recall.get(i)));
        }
        System.out.println("ShuZiInsQA.printMetrics():");
        System.out.println("pos/neg count: " + numPos + " " + numNeg);
        System.out.println("labels: \t" + resultHeader);

        System.out.println("pos count: \t" + listAsTable(scoresPositive));
        System.out.println("neg count: \t" + listAsTable(scoresNegative));
        for (int i = 0; i < classIndex; i++) {
            scoresPositive.set(i, scoresPositive.get(i) / numPos);
            scoresNegative.set(i, scoresNegative.get(i) / numNeg);
        }
        System.out.println("pos ratio: \t" + listAsTable(scoresPositive));
        System.out.println("neg ratio: \t" + listAsTable(scoresNegative));
        System.out.println("recall:    \t" + listAsTable(recall));
        System.out.println("precision: \t" + listAsTable(precision));
        System.out.println("F1:        \t" + listAsTable(F1));
    }

    /****************************************************************
     * score whether each method gets the right answer.  For TFIDF
     * and Term Overlap, a right answer must have a value of 10
     * @return a list of floats which are the percentage
     * correct for each of the algorithms
     */
    private void score(ArrayList<ArrayList<Double>> classifications, int classIndex) {

        System.out.println("ShuZiInsQA.score(): class index: " + classIndex);
        ArrayList<Float> scoresPositive = new ArrayList<Float>();
        ArrayList<Float> scoresNegative = new ArrayList<Float>();
        int numPos = 0;
        int numNeg = 0;
        for (int i = 0; i < classIndex; i++) {
            scoresPositive.add(Float.valueOf(0));
            scoresNegative.add(Float.valueOf(0));
        }
        for (int i = 0; i < 10; i++)
            System.out.println("ShuZiInsQA.score(): classes: " + classifications.get(i));

        for (ArrayList<Double> classes : classifications) {
            //System.out.println("ShuZiInsQA.score(): classes:         " + classes);
            //System.out.println("ShuZiInsQA.score(): class index:     " + classIndex);
            if (classes.size() == classIndex + 1) {
                if (classes.get(classIndex) > 0.9) { // a correct answer
                    numPos++;
                    if (classes.get(1) > 9.9)  // overlap
                        scoresPositive.set(0, scoresPositive.get(0) + 1);
                    if (classes.get(2) > 9.9) // tfidf
                        scoresPositive.set(1, scoresPositive.get(1) + 1);
                    if (classes.get(3) > 9.9)  // bigram
                        scoresPositive.set(2, scoresPositive.get(2) + 1);
                    if (classes.get(4) > 9.9)  // trigram
                        scoresPositive.set(3, scoresPositive.get(3) + 1);
                    if (classes.get(5) > 9.9)  // synset overlap
                        scoresPositive.set(4, scoresPositive.get(4) + 1);
                    if (classes.get(6) > 9.9)  // SUMO overlap
                        scoresPositive.set(5, scoresPositive.get(5) + 1);
                    for (int counter = 7; counter < 12; counter++)
                        if (classes.get(counter) > .9)  // NB
                            scoresPositive.set(counter-1, scoresPositive.get(counter-1) + 1);
                    for (int counter = 12; counter < classes.size()-1; counter++)
                        if (classes.get(counter) > .9)  // LR
                            scoresPositive.set(counter-1, scoresPositive.get(counter-1) + 1);
                    if (scoresPositive.toString().contains("NaN"))
                        System.out.println("ShuZiInsQA.score(): number error: " + scoresPositive);
                }
                if (classes.get(classIndex) < 0.1) { // a wrong answer
                    numNeg++;
                    if (classes.get(1) < -0.9)  // overlap
                        scoresNegative.set(0, scoresNegative.get(0) + 1);
                    if (classes.get(2) < -0.9) // tfidf
                        scoresNegative.set(1, scoresNegative.get(1) + 1);
                    if (classes.get(3) < -0.9)  // bigram
                        scoresNegative.set(2, scoresNegative.get(2) + 1);
                    if (classes.get(4) < -0.9)  // trigram
                        scoresNegative.set(3, scoresNegative.get(3) + 1);
                    if (classes.get(5) < -0.9)  // synset overlap
                        scoresNegative.set(4, scoresNegative.get(4) + 1);
                    if (classes.get(6) < -0.9)  // SUMO overlap
                        scoresNegative.set(5, scoresNegative.get(5) + 1);
                    for (int counter = 7; counter < 12; counter++)
                        if (classes.get(counter) < 0.1)  // NB
                            scoresNegative.set(counter-1, scoresNegative.get(counter-1) + 1);
                    for (int counter = 12; counter < classes.size()-1; counter++)
                        if (classes.get(counter) < 0.1)  // LR
                            scoresNegative.set(counter-1, scoresNegative.get(counter-1) + 1);
                    if (scoresNegative.toString().contains("NaN"))
                        System.out.println("ShuZiInsQA.score(): number error: " + scoresNegative);
                }
            }
            //System.out.println("ShuZiInsQA.score(): positive scores: " + scoresPositive);
            //System.out.println("ShuZiInsQA.score(): negative scores: " + scoresNegative);
        }
        //System.out.println("ShuZiInsQA.score(): num positive scores: " + numPos);
        //System.out.println("ShuZiInsQA.score(): num negative scores: " + numNeg);
        printMetrics(scoresPositive, scoresNegative, numPos, numNeg, resultHeader, classIndex);
    }

    /****************************************************************
     */
    private void scoreTop1(ArrayList<Map<String,Integer>> t1, ArrayList<Dev> test) {

        if (t1.size() != test.size())
            System.out.println("Error in ShuZiInsQA.scoreTop(): mismatched list size");

        Map<String,Integer> totals = new HashMap<String,Integer>();
        for (int i = 0; i < t1.size(); i++) {
            Dev d = test.get(i);
            Map<String,Integer> m = t1.get(i);
            for (String s : m.keySet()) {
                if (d.answersID.contains(m.get(s).toString()))
                    totals.put(s,totals.get(s) + 1);
            }
        }
        System.out.println("scoreTop(): " + totals);
    }

    /** ***************************************************************
     */
    public static void testOverlap() {

        //String s1 = "do Medicare cover my spouse";
        //String s2 = "if your spouse have work and pay Medicare tax for the entire require 40 quarter or be eligible for Medicare by virtue of be disable or some other reason , your spouse can receive his / her own medicare benefit if your spouse have not meet those qualification , if you have meet them and if your spouse be age 65 he / she can receive Medicare based on your eligibility";
        String s1 = "can you borrow against globe Life Insurance";
        String s2 = "borrowing against a life insurance policy require cash value inside that policy term life insurance do not have cash value but whole life insurance policy may so you will need have a whole life policy with global Life Insurance in order to be able borrow against it call up your company and ask if you have any cash value inside your policy and what the borrowing option and cost be";

        ShuZiInsQA sziq = null;
        ArrayList<ArrayList<String>> inputs = new ArrayList<>();
        TFIDF cb = null;
        TokenOverlap to = null;
        try {
            List<String> a = new ArrayList<>();
            sziq = new ShuZiInsQA();
            sziq.readVocab();
            sziq.readAnswers();
            a.addAll(sziq.answers);
            cb = new TFIDF(a,System.getenv("SIGMA_HOME") + "/KBs/WordNetMappings/stopwords.txt");
            to = new TokenOverlap(cb);

        }
        catch (IOException ioe) {
            System.out.println("Error in ShuZiInsQA.devsToInputs()");
            ioe.printStackTrace();
        }
        System.out.println("testOverlap(): overlap: " + to.overlap(s1, s2));
        System.out.println("testOverlap(): mapped to line: " + cb.lines.get(sziq.idToLine.get(8362)));
        System.out.println("testOverlap(): unmapped to line: " + cb.lines.get(8362));
        System.out.println("testOverlap(): index of answer: " + cb.lines.indexOf(s2));
        System.out.println("testOverlap(): last index of answer: " + cb.lines.lastIndexOf(s2));
        System.out.println("testOverlap(): overlap with mapped line: " + to.overlap(s1, cb.lines.get(8362)));
        System.out.println("testOverlap(): index of answer: " + cb.lines.indexOf(sziq.answers.get(Integer.parseInt("8362"))));
    }

    /****************************************************************
     */
    public void run() {

        readVocab();
        readAnswers();
        //readTrainingQuestions();
        List<Dev> devs = readDevTestQuestions();
        ArrayList<String> featureList = Lists.newArrayList("TFIDF", "TokenOverlap", "NGramOverlap","SynsetOverlap","SUMOOverlap");
        TFIDF cb = null;
        TokenOverlap to = null;
        NGramOverlap ng = null;
        SynsetOverlap so = null;
        SUMOOverlap sumo = null;
        try {
            List<String> a = new ArrayList<>();
            a.addAll(answers);
            cb = new TFIDF(a,System.getenv("SIGMA_HOME") + "/KBs/WordNetMappings/stopwords.txt");
            to = new TokenOverlap(cb);
            ng = new NGramOverlap(cb);
            so = new SynsetOverlap(cb);
            sumo = new SUMOOverlap(cb);
        }
        catch (IOException ioe) {
            System.out.println("Error in ShuZiInsQA.run()");
            ioe.printStackTrace();
        }

        ArrayList<ArrayList<Double>> inputs = devsToInputs(devs, cb, to, ng, so, sumo);
        // add types header and labels header

        System.out.println();
        System.out.println("------------training---------------------------");
        System.out.println();

        ArrayList<String> types = Lists.newArrayList("disc", "disc", "disc", "disc", "disc", "disc", "class");
        ArrayList<String> labels = Lists.newArrayList("tfidf", "overlap", "bigram", "trigram", "synOvlp", "SUMOvl", "answer");
        List<NaiveBayes> nblist = createTrainingClasses(devs, cb, to, ng, so, sumo, inputs, labels, types);

        types = Lists.newArrayList("disc", "disc", "disc", "disc", "disc", "disc", "class");
        labels = Lists.newArrayList("tfidf", "overlap", "bigram", "trigram", "synOvlp", "SUMOvl", "answer");
        List<LogisticRegression> lrList = createTrainingClassesLR(devs, cb, to, ng, so, sumo,inputs,labels,types);

        System.out.println();
        System.out.println("---------------testing------------------------");
        System.out.println();

        ArrayList<Dev> test = readTestQuestionOneFile();

        resultHeader = Lists.newArrayList("tfidf", "ovrlap", "bigram", "trigrm", "synOvl", "SUMOvl", "NBall", "NB!SUM", "NB!Syn",
                "NB!Tri", "NB!Bi", "LRall",  "LR!SUM",  "LR!Syn", "LR!Tri", "LR!Bi");
        ArrayList<ArrayList<Double>> intInputs = classify(test, nblist, lrList, cb, to, ng, so,sumo);
        for (int i = 0; i < 10; i++)
            System.out.println("ShuZiInsQA.run(): " + intInputs.get(i));
        score(intInputs, 16);
        ArrayList<Map<String,Integer>> t1 = top1(test, nblist, lrList, cb, to, ng, so,sumo);
        scoreTop1(t1,test);
    }

    /****************************************************************
     */
    public static void streamTest() {

    }

    /****************************************************************
     */
    public static void main(String[] args) {

        if (args[0].equals("-reduce"))
            reduceData = true;
        else
            reduceData = false;
        System.out.println("in ShuZiInsQA.main(): starting timer");
        long t1 = System.currentTimeMillis();
        ShuZiInsQA sziq = new ShuZiInsQA();
        sziq.run();
        long t2 = System.currentTimeMillis();
        System.out.println("ShuZiInsQA.main(): total time: " + (t2-t1)/1000 + " seconds");
        //testOverlap();
    }
}
