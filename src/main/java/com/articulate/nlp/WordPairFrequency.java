package com.articulate.nlp;

/** ***************************************************************
 * This code deals with word pair frequency. Statistics were
 * generated from COCA.
 *
 * Given a verb, the getWordPairFrequencyList function returns
 * a list of nouns that co-occur with that verb in sentences,
 * along with the frequency with which those words occur.
 *
 *  To generate the
 *
 */

import java.util.HashMap;
import java.util.Map;
import java.sql.*;

public class WordPairFrequency {

    private static String db_location = System.getenv("CORPORA") +"/COCA/word_pairs.db";

    public static Map<String, String> getWordPairFrequencies(String word, String word_type_1, String word_type_2) {
        Map<String, String> frequencyMap = new HashMap<>();

        String query = "WITH VerbId AS (SELECT Id FROM Word WHERE Root='"+word+"' AND pos='"+word_type_1+"') " +
                "SELECT word, count " +
                "FROM (" +
                "        SELECT w.root as word, count" +
                "        FROM WordPair wp, Word w, Word w1" +
                "        WHERE wp.Word1_id = (SELECT Id FROM VerbId)" +
                " AND wp.Word2_id = w.Id" +
                " AND w.pos = '" + word_type_2 + "'" +
                " AND wp.Word1_id = w1.id " +
                " UNION ALL" +
                " SELECT w.root as word_id , count " +
                " FROM WordPair wp, Word w, Word w1 " +
                " WHERE wp.Word2_id = (SELECT Id FROM VerbId) " +
                " AND wp.Word1_id = w.Id " +
                " AND w.pos = '" + word_type_2 + "'" +
                " AND wp.Word2_id = w1.id " +
                "     ) AS union_sums " +
                " ORDER BY count DESC " +
                " LIMIT 10;";

        Connection c = null;
        Statement stmt = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:"+WordPairFrequency.db_location);
            stmt = c.createStatement();

            ResultSet rs = stmt.executeQuery(query);

            System.out.println("Word ID | Count");
            System.out.println("------------------");
            while ( rs.next() ) {
                String wordPair = rs.getString("word");
                String frequency = rs.getString("count");
                frequencyMap.put(wordPair, frequency);
                System.out.println(wordPair + " | " + frequency);
            }
            rs.close();
            stmt.close();
            c.close();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        return frequencyMap;
    }

    /*
     main for testing purposes

     cmd line example:
     java -Xmx40g -classpath $ONTOLOGYPORTAL_GIT/sigmanlp/build/sigmanlp.jar:$ONTOLOGYPORTAL_GIT/sigmanlp/build/lib/* \
            com.articulate.nlp.WordPairFrequency love noun verb

     Should return:
        Word ID | Count
        ------------------
        be | 83519
        fall | 9520
        say | 9338
        make | 8574
        know | 5803
        go | 4461
        get | 4227
        think | 3715
        see | 3474
        come | 3365
    */
    public static void main(String args[]) {
        System.out.println("Testing WordPairFrequency");
        String[][] testSet = {{"love", "noun", "verb"}};
        if (args.length > 2) {
            testSet[0][0] = args[0];
            testSet[0][1] = args[1];
            testSet[0][2] = args[2];
        }
        for (String[] testWord : testSet) {
            System.out.println("Testing with word: " + testWord[0]);
            Map<String, String> pairFrequencies = getWordPairFrequencies(testWord[0], testWord[1], testWord[2]);
        }
    }


}