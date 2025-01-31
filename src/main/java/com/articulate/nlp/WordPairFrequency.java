package com.articulate.nlp;

import com.articulate.sigma.utils.AVPair;
import java.util.ArrayList;
import java.sql.*;
import java.util.HashSet;
import java.util.Random;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.wordNet.WordNetUtilities;
import java.io.File;
import com.articulate.sigma.KB;


/** ***************************************************************
 * This code chooses words based on their occurence frequency with
 * other words. Word co-occurence frequencies are calculated from
 * COCA and stored in a database called word_pairs.db.
 */

public class WordPairFrequency {
    public static boolean debug = true;

    private static String db_location = System.getenv("CORPORA") +"/COCA/word_pairs.db";

    public enum WordType{noun, verb};

    /** ***************************************************************
     * Gets word pair frequencies from the database.
     */
    public static ArrayList<AVPair> getWordPairFrequencies(String word, WordType word_type_1, WordType word_type_2) {

        ArrayList<AVPair> frequencyList = new ArrayList();

        String query = "WITH VerbId AS (SELECT Id FROM Word WHERE Root='"+word+"' AND pos='"+word_type_1.name()+"') " +
                "SELECT word, count " +
                "FROM (" +
                "        SELECT w.root as word, count" +
                "        FROM WordPair wp, Word w, Word w1" +
                "        WHERE wp.Word1_id = (SELECT Id FROM VerbId)" +
                " AND wp.Word2_id = w.Id" +
                " AND w.pos = '" + word_type_2.name() + "'" +
                " AND wp.Word1_id = w1.id " +
                " UNION ALL" +
                " SELECT w.root as word_id , count " +
                " FROM WordPair wp, Word w, Word w1 " +
                " WHERE wp.Word2_id = (SELECT Id FROM VerbId) " +
                " AND wp.Word1_id = w.Id " +
                " AND w.pos = '" + word_type_2.name() + "'" +
                " AND wp.Word2_id = w1.id " +
                "     ) AS union_sums " +
                " ORDER BY count DESC " +
                " LIMIT 100;";

        Connection c = null;
        Statement stmt = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:"+WordPairFrequency.db_location);
            stmt = c.createStatement();

            ResultSet rs = stmt.executeQuery(query);

            if (debug) System.out.println("Word ID | Count");
            if (debug) System.out.println("------------------");
            while ( rs.next() ) {
                String wordPair = rs.getString("word");
                String frequency = rs.getString("count");
                frequencyList.add(new AVPair(wordPair, frequency));
                if (debug) System.out.println(wordPair + " | " + frequency);
            }
            rs.close();
            stmt.close();
            c.close();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        return frequencyList;
    }

    private static boolean dbExists(){
        File file = new File(WordPairFrequency.db_location);
        if (file.exists()) {
            return true;
        }
        else {
            System.out.println("Error in WordPairFrequency.dbExists(): Database " + file + " cannot be found.");
            return false;
        }
    }

    /** **************************************************************************
     * Gets the best SUMO mapping for a word. Chooses a random equivalent mapping first,
     * if no equivalent mapping exists, chooses a random subsuming mapping.
     */
    private static String getBestSUMOMapping(HashSet<String> synsetOfTerm) {

        ArrayList<String> equivalentTerms = new ArrayList();
        ArrayList<String> subsumingTerms = new ArrayList();
        for (String synset:synsetOfTerm) {
            String sumoMapping = WordNet.wn.getSUMOMapping(synset);
            if (sumoMapping != null) {
                sumoMapping = sumoMapping.substring(2);
                if (sumoMapping.charAt(sumoMapping.length() - 1) == '=') {
                    equivalentTerms.add(sumoMapping.substring(0, sumoMapping.length() - 1));
                }
                else {
                    subsumingTerms.add(sumoMapping.substring(0, sumoMapping.length() -1));
                }
            }
        }
        Random rand = new Random();
        if (equivalentTerms.size() > 0) {
            return equivalentTerms.get(rand.nextInt(equivalentTerms.size()));
        }
        else if (subsumingTerms.size() > 0) {
            return subsumingTerms.get(rand.nextInt(subsumingTerms.size()));
        }
        return null;
    }

    /** ***************************************************************
     * Gets an Object from a Subject (and verb). The intersection of the
     * Subject list and Object list are calculated, and a random term is selected.
     */
    public static String getNounFromNounAndVerb(LFeatures lfeat) {

        if (debug) System.out.println("WordPairFrequency.getNounFromNoun()" + lfeat.subj + " " + lfeat.verb);
        if (!dbExists()) { return lfeat.objects.getNext(); }
        ArrayList<AVPair> subjSet = WordPairFrequency.getWordPairFrequencies(lfeat.verb, WordType.verb, WordType.noun);
        ArrayList<AVPair> objSet = WordPairFrequency.getWordPairFrequencies(lfeat.subj.toLowerCase(), WordType.noun, WordType.noun);
        ArrayList<AVPair> mergedList = new ArrayList();
        for (AVPair subj:subjSet) {
            for (AVPair obj:objSet) {
                if (subj.attribute.equals(obj.attribute)) {
                    mergedList.add(new AVPair(obj.attribute, obj.value));
                    break;
                }
            }
        }
        if (mergedList.size() != 0) {
            RandSet mergedSet = RandSet.create(mergedList);
            for (int i = 0; i < 5; i++) {
                String term = mergedSet.getNext();
                HashSet<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(term);
                for (int j = 0; j < 5; j++) {
                    String noun = getBestSUMOMapping(synsetOfTerm);
                    if (debug) System.out.println("Choosing: " + term + " which maps to " + noun + " in SUMO.");
                    if (noun != null && !noun.equals("Human")) {
                        return noun;
                    }
                }
            }

            return lfeat.objects.getNext();
        }
        return lfeat.objects.getNext();
    }

    /** ***************************************************************
     * Gets a noun from a particular class given a verb.
     * classname could be "BodyPart" or "SocialRole" for example.
     */
    public static String getNounInClassFromVerb(LFeatures lfeat, KB kb, String className) {

        if (debug) System.out.println("WordPairFrequency.getNounInClassFromVerb() - className: " + className);
        if (!dbExists()) { return lfeat.objects.getNext(); }
        ArrayList<AVPair> subjList = getWordPairFrequencies(lfeat.verb, WordType.verb, WordType.noun);
        ArrayList<AVPair> instanceList = new ArrayList();
        for (AVPair subj:subjList) {
            HashSet<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(subj.attribute);
            String noun = getBestSUMOMapping(synsetOfTerm);
            System.out.println("THOMPSON: " + subj.attribute + " maps to " + noun);
            //String wordCapitalized = subj.attribute.substring(0, 1).toUpperCase() + subj.attribute.substring(1);
            if (kb.isSubclass(noun, className)) {
                //subj.attribute = noun;
                instanceList.add(subj);
            }
        }
        if (instanceList.size() > 0) {
            if (debug) System.out.println("WordPairFrequency.getNounInClassFromVerb Picking from list ------------------------------");
            RandSet instanceSet = RandSet.create(instanceList);
            return instanceSet.getNext();
        }
        else {
            if (debug) System.out.println("WordPairFrequency.getNounInClassFromVerb - Empty List");
            return null;
        }
    }

    /** ***************************************************************
     * Given a verb, gets an associated random Noun from that Verb.
     */
    public static String getNounFromVerb(LFeatures lfeat) {

        if (debug) System.out.println("THOMPSON: WordPairFrequency.getNounFromVerb() ");
        if (!dbExists()) { return lfeat.objects.getNext(); }
        RandSet subjSet = RandSet.create(getWordPairFrequencies(lfeat.verb, WordType.verb, WordType.noun));
        String term = subjSet.getNext();
        HashSet<String> synsetOfTerm = WordNet.wn.getSynsetsFromWord(term);
        String noun = getBestSUMOMapping(synsetOfTerm);
        return (noun != null) ? noun : lfeat.objects.getNext();
    }

    /** ***************************************************************
    * main for testing purposes
    *
    * cmd line example:
    * java -Xmx40g -classpath $ONTOLOGYPORTAL_GIT/sigmanlp/build/sigmanlp.jar:$ONTOLOGYPORTAL_GIT/sigmanlp/build/lib/* \
    *        com.articulate.nlp.WordPairFrequency love noun verb
    *
    * Should return (if the print statement is uncommented):
    *    Word ID | Count
    *    ------------------
    *    be | 83519
    *    fall | 9520
    *    say | 9338
    *    make | 8574
    *    know | 5803
    *    go | 4461
    *    get | 4227
    *    think | 3715
    *    see | 3474
    *    come | 3365
    */
    public static void main(String args[]) {

        WordPairFrequency.debug = true;
        System.out.println("Testing WordPairFrequency");
        String[][] testSet = {{"love", "noun", "verb"}};
        if (args.length > 2) {
            testSet[0][0] = args[0];
            testSet[0][1] = args[1];
            testSet[0][2] = args[2];
        }
        for (String[] testWord : testSet) {
            System.out.println("Testing with word: " + testWord[0]);
            ArrayList<AVPair> pairFrequencies = getWordPairFrequencies(testWord[0], WordType.valueOf(testWord[1]), WordType.valueOf(testWord[2]));
        }
    }


}