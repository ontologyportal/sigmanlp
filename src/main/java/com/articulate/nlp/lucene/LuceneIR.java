package com.articulate.nlp.lucene;
// based on
// https://examples.javacodegeeks.com/core-java/apache/lucene/apache-lucene-hello-world-example/

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.FSDirectory;

import com.articulate.sigma.StringUtil;

public class LuceneIR {

    public static HashMap<SCOREKEY,Integer> docScore = new HashMap<>();
    public static HashMap<SCOREKEY,Integer> sentScore = new HashMap<>();
    public enum SCOREKEY {  TRUEPOS,   // real answer found
                            FALSEPOS,  // non answer reported as answer
                            TRUENEG,   // non answer not found
                            FALSENEG}; // answer not reported as an answer

    private static int answerLimit = 5; // number of answers returned to a given question
    public static boolean bm25 = true;
    public static boolean displaySentence = true;

    public static final String DOC_CONTENT_FIELD = "content";
    public static final String SENTENCE_CONTENT_FIELD = "sentence";

    /** **************************************************************
     */
    public static void initScores(HashMap<SCOREKEY,Integer> score) {

        score.put(SCOREKEY.TRUEPOS,0);
        score.put(SCOREKEY.FALSEPOS,0);
        score.put(SCOREKEY.TRUENEG,0);
        score.put(SCOREKEY.FALSENEG,0);
    }

    /** **************************************************************
     */
    private static void incrementScore(HashMap<SCOREKEY,Integer> score,
                                       SCOREKEY key) {

        int s = score.get(key);
        s++;
        score.put(key,s);
    }

    /** **************************************************************
     * Index sentences in a document
     * @param standardAnalyzer is the analyzer that must be used later.
     * @param doc is the document to be indexed.
     * @param dir is the in memory directory that holds the document
     *
     */
    public static void indexDocInMemory(StandardAnalyzer standardAnalyzer,
                                        Document doc, Directory dir) {

        try {
            IndexWriterConfig config = new IndexWriterConfig(standardAnalyzer);
            if (bm25)
                // config.setSimilarity(new BM25Similarity((float) 1.2, (float) 0.75)); // SA uses (1,1)
                config.setSimilarity(new BM25Similarity(1,1)); // SA uses (1,1)
            IndexWriter writer = new IndexWriter(dir, config);

            Properties properties = new Properties();
            properties.setProperty("annotators", "tokenize, ssplit");
            StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
            String noXML = StringUtil.removeHTML(doc.get(LuceneIR.DOC_CONTENT_FIELD));

            noXML = noXML.replaceAll("\\n\\n", System.getProperty("line.separator"));
            noXML = noXML.replace("\n", " ");
            System.out.println("indexDocInMemory(): noXML: " +
                    StringUtil.getFirstNChars(noXML,100));
            List<CoreMap> sentences = pipeline.process(noXML)
                    .get(CoreAnnotations.SentencesAnnotation.class);
            for (CoreMap sentence : sentences) { // write sentences to RAMDirectory
                //System.out.println(sentence.toString());
                Document document = new Document();
                document.add(new TextField(LuceneIR.SENTENCE_CONTENT_FIELD, sentence.toString(), Field.Store.YES)); // "document" is a sentence
                writer.addDocument(document);
            }
            writer.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** **************************************************************
     */
    public ArrayList<String> getSentenceAnswers(Document doc, SearchResult sr, int num) {

        answerLimit = num;
        return getSentenceAnswers(doc,sr);
    }

    /** **************************************************************
     * provided a document and a query, return the most likely set
     * of sentences matching the query from the document.
     */
    public ArrayList<String> getSentenceAnswers(Document doc, SearchResult sr) {

        if (doc == null) {
            System.out.println("error in getSentenceAnswers(): null document");
            return null;
        }
        String q = sr.query;
        ArrayList<String> results = new ArrayList<>();
        try {
            //System.out.println("getSentenceAnswers(): Doc: " +
            //        StringUtil.getFirstNChars(doc.get(LuceneIR.DOC_CONTENT_FIELD),100));
            StandardAnalyzer standardAnalyzer = new StandardAnalyzer(); // SA uses EnglishAnalyzer
            Directory directory = new RAMDirectory();
            indexDocInMemory(standardAnalyzer,doc,directory);
            // now search over the sentences
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            if (bm25)
                // searcher.setSimilarity(new BM25Similarity((float) 1.2,(float) 0.75));
                searcher.setSimilarity(new BM25Similarity(1,1));
            QueryParser parser = new QueryParser(LuceneIR.SENTENCE_CONTENT_FIELD, standardAnalyzer);
            //  SA uses       Query query = parser.createMinShouldMatchQuery("sentence", q, 0.3f);
            Query query = parser.parse(q);
            TopDocs sresults = searcher.search(query, answerLimit);
            System.out.println("getSentenceAnswers(): Hits for " + sr.query + " -->" + sresults.totalHits);
            ScoreDoc[] sdocs = sresults.scoreDocs;
            HashSet<String> foundAnswers = new HashSet<>();
            HashSet<String> falseAnswers = new HashSet<>();
            boolean sentenceFound = false;
            for (ScoreDoc sd : sdocs) {
                int docnum = sd.doc;
                Document document = searcher.doc(docnum); // "document" is a sentence
                if (displaySentence) System.out.println("candidate sentence: " + document.get(SENTENCE_CONTENT_FIELD));
                for (String ans : sr.answers.values()) {
                    if (document.get(SENTENCE_CONTENT_FIELD).contains(ans)) {
                        System.out.println("Answer sentence found! " + document.get(SENTENCE_CONTENT_FIELD));
                        sentenceFound = true;
                        foundAnswers.add(ans);
                    }
                }
                if (!sentenceFound)
                    falseAnswers.add(document.get(SENTENCE_CONTENT_FIELD));
                results.add(document.get(SENTENCE_CONTENT_FIELD));
            }
            sentScore.put(SCOREKEY.TRUEPOS,
                    sentScore.get(SCOREKEY.TRUEPOS) + foundAnswers.size());
            sentScore.put(SCOREKEY.FALSEPOS,
                    sentScore.get(SCOREKEY.FALSEPOS) + falseAnswers.size());
            sentScore.put(SCOREKEY.FALSENEG,
                    sentScore.get(SCOREKEY.FALSENEG) + sr.answers.values().size());
            return results;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    /** **************************************************************
     * @param query
     */
    public void runOneQuery(String query, Directory dir,
                            StandardAnalyzer sa) {

        initScores(docScore);
        initScores(sentScore);
        try {
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("contents", sa);
            Query q = parser.parse(query);
            if (bm25)
                searcher.setSimilarity(new BM25Similarity((float) 1.2,(float) 0.75));
            TopDocs results = searcher.search(q, answerLimit);
            ScoreDoc[] hits = results.scoreDocs;
            System.out.println();
            System.out.println("-------------------");
            System.out.println("Found " + hits.length + " hits for query: ");
            System.out.println("query: " + query);
            int limit = hits.length;
            if (limit > answerLimit)
                limit = answerLimit;
            for (int i = 0; i < limit; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                SearchResult sr = new SearchResult();
                sr.query = query;
                ArrayList<String> answerSentences = getSentenceAnswers(d,sr);
                System.out.println((i + 1) + ". " + d.get("id") + " : " + hits[i].score);
            }
            System.out.println();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** **************************************************************
     * Run tests and print out whether the answer is among each set of
     * results.  Tally the number of correct answers present.
     *
     * @param tests is a list of tests with answers
     */
    public void runTests(HashMap<String,SearchResult> tests, Directory dir,
                         StandardAnalyzer sa) {

        try {
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("contents", sa);
            for (SearchResult sr : tests.values()) {
                boolean found = false;
                Query query = parser.parse(sr.query);
                if (bm25)
                    searcher.setSimilarity(new BM25Similarity((float) 1.2,(float) 0.75));
                TopDocs results = searcher.search(query, answerLimit);
                ScoreDoc[] hits = results.scoreDocs;
                System.out.println();
                System.out.println("-------------------");
                System.out.println("Found " + hits.length + " hits for query: ");
                System.out.println("query: " + sr.query);
                System.out.println("correct answer: " + sr.answers);
                int limit = hits.length;
                if (limit > answerLimit)
                    limit = answerLimit;
                for (int i = 0; i < limit; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);
                    ArrayList<String> answerSentences = getSentenceAnswers(d,sr);
                    System.out.println((i + 1) + ". " + d.get("id") + " : " + hits[i].score);
                    if (sr.answers.keySet().contains(d.get("id")))
                        found = true;
                }
                if (found) {
                    incrementScore(docScore,SCOREKEY.TRUEPOS);
                }
                else {
                    incrementScore(docScore,SCOREKEY.FALSENEG);
                    //System.out.println("Answer not found :" + scores.get("docNotFoundCount"));
                }
                docScore.put(SCOREKEY.FALSEPOS,
                        docScore.get(SCOREKEY.FALSEPOS) + limit);
                System.out.println();
            }
            //System.out.println("runTests(): Found: " + scores.get("docFoundCount") +
                    //" not found: " + scores.get("docNotFoundCount"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** **************************************************************
     * Parse the test set from TREC8
     *
     * @param filename is the full path
     */
    public HashMap<String,SearchResult> parseTREC8questions(String filename) {

        HashMap<String,SearchResult> result = new HashMap<>();
        LineNumberReader lr = null;
        try {
            String line;
            File f = new File(filename);
            if (f == null) {
                System.out.println("Error in parseTREC8questions(): The file does not exist in " + filename);
                return result;
            }
            FileReader r = new FileReader(filename);
            lr = new LineNumberReader(r);
            while ((line = lr.readLine()) != null) {
                if (line.startsWith("<num> Number: ")) {
                    SearchResult sr = new SearchResult();
                    String num = line.substring(line.indexOf(":") + 1).trim();
                    sr.id = num;
                    line = lr.readLine();
                    line = lr.readLine();
                    if (!line.startsWith("<desc> Description:"))
                        System.out.println("Error in parseTREC8questions(): bad line " + line);
                    line = lr.readLine();
                    sr.query = line.replace("\"","\\\""); //lucene appears confused by quotes in a query
                    sr.query = sr.query.replace("?",".");
                    result.put(sr.id,sr);
                }
            }
        }
        catch (Exception ex) {
            System.out.println("Error in parseTREC8(): " + ex.getMessage());
            ex.printStackTrace();
        }
        System.out.println(result);
        return result;
    }

    /** **************************************************************
     * Parse the test set from TREC8
     *
     * @param filename is the full path
     */
    public void parseTREC8answers(String filename, HashMap<String,SearchResult> questions) {

        LineNumberReader lr = null;
        try {
            String line;
            File f = new File(filename);
            if (f == null) {
                System.out.println("Error in parseTREC8answers(): The file does not exist in " + filename);
                return;
            }
            FileReader r = new FileReader(filename);
            lr = new LineNumberReader(r);
            while ((line = lr.readLine()) != null) {
                String num = line.substring(0,line.indexOf(" ")).trim();
                SearchResult sr = questions.get(num);
                if (sr != null)
                    sr.answers.put("unknown",line.substring(line.indexOf(" ")).trim());
            }
        }
        catch (Exception ex) {
            System.out.println("Error in parseTREC8answers(): " + ex.getMessage());
            ex.printStackTrace();
        }
        System.out.println(questions);
    }

    /** **************************************************************
     * Parse the "development" test set from TREC8
     *
     * @param filename is the full path
     */
    public ArrayList<SearchResult> parseTREC8dev(String filename) {

        ArrayList<SearchResult> result = new ArrayList<>();
        LineNumberReader lr = null;
        try {
            String line;
            File f = new File(filename);
            if (f == null) {
                System.out.println("Error in parseTREC8(): The file does not exist in " + filename);
                return result;
            }
            FileReader r = new FileReader(filename);
            lr = new LineNumberReader(r);
            while ((line = lr.readLine()) != null) {
                if (line.startsWith("Number: ")) {
                    SearchResult sr = new SearchResult();
                    sr.id = line.substring(line.indexOf(":") + 2);
                    line = lr.readLine();
                    sr.query = line.replace("\"","\\\""); //lucene appears confused by quotes in a query
                    line = lr.readLine();
                    ArrayList<String> fileList = new ArrayList<>();
                    while (!StringUtil.emptyString(line)) {
                        if (line.matches("(FR|CR|FT|FBIS|LA).+")) {
                            fileList.add(line);
                        }
                        else {
                            for (String s : fileList)
                                sr.answers.put(s,line);
                            fileList = new ArrayList<>();
                        }
                        line = lr.readLine();
                    }
                    result.add(sr);
                }
                else
                    System.out.println("Error in LuceneHellowWorld.parseTREC8(): unexpected line: " + line);
            }
        }
        catch (Exception ex) {
                System.out.println("Error in parseTREC8(): " + ex.getMessage());
                ex.printStackTrace();
        }
        System.out.println(result);
        return result;
    }

    /** **************************************************************
     * Parse a single file of <DOC>...</DOC> tags and convert them into
     * separate documents stored in the returned map. Or if there are no
     * such tags, just add the whole document.
     *
     * @return a HashMap of document ids that are the keys and values
     * which are the contents of each document
     */
    public static HashMap<String,String> parseDoc(String filename) {

        HashMap<String,String> result = new HashMap<>();
        try {
            File f = new File(filename);
            if (f == null) {
                System.out.println("Error in parseTREC8(): The file does not exist in " + filename);
                return result;
            }
            FileReader r = new FileReader(filename);
            LineNumberReader lr = new LineNumberReader(r);
            String line = "";
            StringBuffer doc = new StringBuffer();
            String id = "";
            while ((line = lr.readLine()) != null) {
                doc.append(line + "\n");
                if (line.startsWith("</DOC>")) {
                    result.put(id,doc.toString());
                    doc = new StringBuffer();
                }
                if (line.startsWith("<DOCNO>")) {
                    id = line.substring(7,line.indexOf("<",8)).trim();
                }
            }
            if (result.keySet().size() == 0 && doc.length() != 0) {
                result.put(filename,doc.toString());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /** **************************************************************
     * Recursively add all documents in the directory to the IndexWriter.  Note that
     * parseDoc() is called because each file actually contains multiple
     * documents.
     * @param docDir is the directory path to where documents are found
     */
    public static void indexDocuments(IndexWriter writer, String docDir) {

        try {
            System.out.println("indexDocument(): indexing: " + docDir);
            File dir = new File(docDir);
            File[] files = dir.listFiles();
            if (files == null || files.length <= 1)
                System.out.println("Error in indexDocument(): no files in " + docDir);
            for (File f : files) {
                if (f.isDirectory())
                    indexDocuments(writer,f.getAbsolutePath());
                else {
                    HashMap<String,String> componentDocs = parseDoc(f.getAbsolutePath());
                    System.out.println("Add file: " + f + " with " + componentDocs.keySet().size() + " documents inside.");
                    for (String id : componentDocs.keySet()) {
                        Document document = new Document();
                        String path = f.getCanonicalPath();
                        document.add(new StringField("path", path, Field.Store.YES));
                        //Reader reader = new FileReader(f);
                        document.add(new TextField("id", id, Field.Store.YES));
                        document.add(new TextField("contents", componentDocs.get(id), Field.Store.YES));
                        writer.addDocument(document);
                    }
                }
            }
            //writer.optimize();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** **************************************************************
     */
    public static void precisionRecall(HashMap<SCOREKEY,Integer> scores) {

        System.out.println("scores: " + scores);
        int truepos = scores.get(SCOREKEY.TRUEPOS);
        int falsepos = scores.get(SCOREKEY.FALSEPOS);
        int falseneg = scores.get(SCOREKEY.FALSENEG);
        float precision = (float) truepos / (truepos + falsepos);
        float recall = (float) truepos / (truepos + falseneg);
        float f1 = (2 * precision * recall) / (precision + recall);
        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
        System.out.println("F1 : " + f1);
    }

    /** **************************************************************
     * Read and index the TREC files, parse a test and answer key,
     * then run and score the tests.
     */
    public static void runTest(Directory directory, StandardAnalyzer sa)
            throws IOException, ParseException {

        //String testFile = "/home/apease/corpora/TREC/TREC8/development.qa.txt";
        LuceneIR lhd = new LuceneIR();
        //ArrayList<SearchResult> tests =
        //        lhd.parseTREC8dev(testFile);
        String testFile = "/home/apease/corpora/TREC/TREC8/trec8.topics.qa_questions.txt";
        HashMap<String,SearchResult> tests =
                lhd.parseTREC8questions(testFile);
        initScores(docScore);
        lhd.parseTREC8answers("/home/apease/corpora/TREC/TREC8/trec8.orig.qanswers.only.txt",tests);

        initScores(sentScore);
        lhd.runTests(tests,directory,sa);
        System.out.println("--------" + tests.size() + " Tests in " + testFile + "--------");
        System.out.println();
        System.out.println(answerLimit + " answers");
        System.out.println();
        System.out.println("Document scores: ");
        precisionRecall(docScore);
        System.out.println();
        System.out.println("Sentence scores: ");
        precisionRecall(sentScore);
        System.out.println();
    }

    /** **************************************************************
     * @return the directory into which Lucene writes its index
     */
    public static Directory init(StandardAnalyzer sa) {

        Directory directory = null;
        try {
            // directory into which Lucene writes its index
            String indexDir = "/home/apease/Programs/lucene-6.5.1/testoutput";
            String defaultIndex = System.getenv("LUCENE_DEFAULT_INDEX");
            if (!StringUtil.emptyString(defaultIndex))
                indexDir = defaultIndex;
            directory = FSDirectory.open(Paths.get(indexDir));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return directory;
    }

    /** **************************************************************
     * Write Lucene's index into the environment path variable LUCENE_DEFAULT_INDEX
     * @param filesDir is the directory in which there are files to index
     */
    public static void reindex(String filesDir, StandardAnalyzer sa) {

        try {
            IndexWriterConfig config = new IndexWriterConfig(sa);
            if (bm25)
                config.setSimilarity(new BM25Similarity((float) 1.2, (float) 0.75));
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            String indexDir = System.getenv("LUCENE_DEFAULT_INDEX");
            Directory indexDirectory = FSDirectory.open(Paths.get(indexDir));
            IndexWriter writer = new IndexWriter(indexDirectory, config);
            indexDocuments(writer,filesDir);
            //indexDocuments(writer, "/home/apease/corpora/Trec5");  // LA, FBIS
            //indexDocuments(writer, "/home/apease/corpora/TREC4");   // CR, FT, FR
            writer.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** **************************************************************
     * run a grep command and return a list of the results
     */
    private static ArrayList<String> grep(String command) {

        ArrayList<String> result = new ArrayList<>();
        try {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(command);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    pr.getInputStream()));
            String line = null;
            while ((line = input.readLine()) != null) {
                result.add(line);
            }
        }
        catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
        return result;
    }

    /** **************************************************************
     * run a grep command and return a list of the results
     */
    private static HashMap<String,String> grepFiles(String str, String path) {

        HashMap<String,String> result = new HashMap<String,String>();
        String cmdString = "grep -r \"" + str + "\" " + path;
        System.out.println(cmdString);
        ArrayList<String> lines = grep(cmdString);
        for (String s : lines) {
            if (s.indexOf(":") != -1) {
                String fullfile = s.substring(0,s.indexOf(":"));
                String fname = fullfile.substring(fullfile.lastIndexOf(':') + 1);
                result.put(fname,str);
                System.out.println("Adding answer: " + fname + ":" + str);
            }
        }
        return result;
    }

    /** **************************************************************
     * Test to ensure that all the TREC8 questions have answers available
     * in the documents provided.
     */
    public static void findAnswerDocs() {

        LuceneIR lhd = new LuceneIR();
        HashMap<String,SearchResult> tests =
                lhd.parseTREC8questions("/home/apease/corpora/TREC/TREC8/trec8.topics.qa_questions.txt");
        lhd.parseTREC8answers("/home/apease/corpora/TREC/TREC8/trec8.orig.qanswers.only.txt",tests);
        try {
            for (SearchResult sr : tests.values()) {
                Collection<String> answers = sr.answers.values();
                HashMap<String,String> newanswers = new HashMap<>();
                for (String ans : answers) {
                    newanswers.putAll(grepFiles(ans,"/home/apease/corpora/TREC4"));
                    newanswers.putAll(grepFiles(ans,"/home/apease/corpora/Trec5"));
                }
                if (newanswers.keySet().size() == 0)
                    System.out.println("No answers for question: " + sr.id);
                sr.answers = newanswers;
                System.out.println(sr.query);
                for (String s : sr.answers.keySet()) {
                    System.out.println(s);
                    System.out.println(sr.answers.get(s));
                }
                System.out.println();
            }
        }
        catch (Exception e) {
            System.out.println(e.toString());
            e.printStackTrace();
        }
    }

    /** **************************************************************
     */
    public static void interactive(Directory dir, StandardAnalyzer sa) {

        LuceneIR lhd = new LuceneIR();
        String input = "";
        Scanner scanner = new Scanner(System.in);
        do {
            System.out.print("Enter query: ");
            input = scanner.nextLine().trim();
            System.out.print("test query: " + input);
            lhd.runOneQuery(input,dir,sa);
        } while (!input.equals("exit") && !input.equals("quit"));
    }

    /** **************************************************************
     * @param args
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws IOException, ParseException {

        //need to try StandardFilter, LowerCaseFilter, StopFilter, SnowBallFilter(English),
        //  EnglishMinimalStemFilter, CodePointCountFilter

        StandardAnalyzer sa = new StandardAnalyzer();
        Directory indexDir = init(sa); //
        if (args == null || args.length < 1 || args[0].equals("-h")) {
            System.out.println("Information Retrieval");
            System.out.println("  options:");
            System.out.println("  -h              - show this help screen");
            System.out.println("  -q \"<question>\" - runs one test question on a loaded corpus");
            System.out.println("  -i              - runs a loop of one question at a time,");
            System.out.println("       'quit' to quit");
            System.out.println("  -<1-0>          - specifies answer set size of one to ten (where 0 indicates 10)");
            System.out.println("  -d              - test the default test corpus without reindexing");
            System.out.println("  -t <file>       - indexes and runs the specified test corpus");
            System.out.println("  -r              - reindex");
            System.out.println("  -f              - validate presence of answers for all TREC8 questions");
        }
        if (args != null && args.length > 0 && args[0].startsWith("-") && args[0].matches(".*\\d.*")) {
            for (char c : args[0].toCharArray()) {
                if (Character.isDigit(c)) {
                    answerLimit = Character.digit(c,10);
                    if (answerLimit == 0)
                        answerLimit = 10;
                }
                System.out.println("answerLimit changed to " + answerLimit);
            }
        }
        if (args != null && args.length > 0 && args[0].startsWith("-") && args[0].contains("r")) {
            String filesDir = System.getenv("LUCENE_DEFAULT_INPUT");
            reindex(filesDir,sa);
        }
        if (args != null && args.length > 0 && args[0].startsWith("-") && args[0].contains("d")) {
            runTest(indexDir,sa);
        }
        if (args != null && args.length > 0 && args[0].startsWith("-") && args[0].contains("f")) {
            findAnswerDocs();
        }
        if (args != null && args.length > 0 && args[0].startsWith("-") && args[0].contains("i")) {
            displaySentence = true;
            answerLimit = 1;
            interactive(indexDir,sa);
        }
        if (args != null && args.length > 1 && args[0].startsWith("-") && args[0].contains("q")) {
            LuceneIR lhd = new LuceneIR();
            lhd.runOneQuery(StringUtil.removeEnclosingQuotes(args[1]),indexDir,sa);
        }
    }
}
