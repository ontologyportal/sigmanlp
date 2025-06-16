
/** ***************************************************************
 * This code generates language-logic pairs designed for training
 * a machine learning system.
 *
 * Specifically it generates sentences with processes in them.
 *
 * Several approaches are used to experiment.
 *  - Random. Randomly assign Subject, Direct Obj, Indirect Obj
 *  - WordPair. Using the COCA database to find the frequency
 *      that words appear in sentences.
 *  - Ollama. Use an LLM to suggest the appropriate parts of speech
 *
 *
 *  This program is pipelined, selecting in the following order:
 *
 *  :Select Process:Select Subj:Select DirObj:Select IndObj:
 *
 */



import java.util.concurrent.*;
import com.articulate.nlp.LFeatures;
import com.articulate.nlp.GenUtils;

public class GenProcesses {
    private static final BlockingQueue<LFeatures> Vqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<LFeatures> SVqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<LFeatures> SVOqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<LFeatures> SVOIOqueue = new LinkedBlockingQueue<>();
    private static String outputFileEnglish;
    private static String outputFileLogic;
    private static int numSentences;
    private static com.articulate.nlp.GenSimpTestData genSimpTestData = null;

    // Returns a random or generated verb
    static String getVerb() { return "eats"; }
    // Returns a random or generated subject
    static String getSubject() { return "The cat"; }
    // Returns a random or generated direct object
    static String getDirectObject() { return "fish"; }
    // Returns a random or generated indirect object
    static String getIndirectObject() { return "with a fork"; }

    /**
     * Parses command-line arguments for output file prefix and number of sentences.
     * Returns false if arguments are invalid.
     */
    private static boolean parseArgs(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java GenProcesses <output_file_prefix> <number_of_sentences>");
            return false;
        }
        String prefix = args[0];
        outputFileEnglish = prefix + "-eng.txt";
        outputFileLogic = prefix + "-log.txt";
        try {
            numSentences = Integer.parseInt(args[1]);
            if (numSentences <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.out.println("Please provide a positive integer for the number of sentences.");
            return false;
        }
        return true;
    }

    /**
     * Starts all pipeline and monitoring threads.
     */
    private static void startPipeline() {
        new Thread(GenProcesses::verbStage).start();
        new Thread(GenProcesses::subjStage).start();
        new Thread(GenProcesses::directObjStage).start();
        new Thread(GenProcesses::indirectObjStage).start();
        new Thread(GenProcesses::postProcessStage).start();
        new Thread(GenProcesses::monitor).start();
    }

    /**
     * Pipeline stage 1: Generates verbs and puts LFeatures objects into Vqueue.
     */
    private static void verbStage() {
        try {
            for (int i = 0; i < numSentences; i++) {
                LFeatures lfeat = new LFeatures(genSimpTestData);
                lfeat.verb = getVerb();
                Vqueue.put(lfeat);
            }
            Vqueue.put(null); // Poison pill
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Pipeline stage 2: Adds subject to LFeatures objects from Vqueue and puts them into SVqueue.
     */
    private static void subjStage() {
        try {
            while (true) {
                LFeatures lfeat = Vqueue.take();
                if (lfeat == null) {
                    SVqueue.put(null);
                    break;
                }
                lfeat.subj = getSubject();
                SVqueue.put(lfeat);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Pipeline stage 3: Adds direct object to LFeatures objects from SVqueue and puts them into SVOqueue.
     */
    private static void directObjStage() {
        try {
            while (true) {
                LFeatures lfeat = SVqueue.take();
                if (lfeat == null) {
                    SVOqueue.put(null);
                    break;
                }
                lfeat.directName = getDirectObject();
                SVOqueue.put(lfeat);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Pipeline stage 4: Adds indirect object to LFeatures objects from SVOqueue and puts them into SVOIOqueue.
     */
    private static void indirectObjStage() {
        try {
            while (true) {
                LFeatures lfeat = SVOqueue.take();
                if (lfeat == null) {
                    SVOIOqueue.put(null);
                    break;
                }
                lfeat.indirectName = getIndirectObject();
                SVOIOqueue.put(lfeat);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Pipeline stage 5: Processes complete LFeatures objects from SVOIOqueue and writes results to files.
     */
    private static void postProcessStage() {
        try {
            while (true) {
                LFeatures lfeat = SVOIOqueue.take();
                if (lfeat == null) break;
                String english = (lfeat.subj != null ? lfeat.subj : "[NO SUBJECT]") + " " +
                        (lfeat.verb != null ? lfeat.verb : "[NO VERB]") + " " +
                        (lfeat.directName != null ? lfeat.directName : "[NO DIRECT OBJ]") + " " +
                        (lfeat.indirectName != null ? lfeat.indirectName : "[NO INDIRECT OBJ]");
                String logic = (lfeat.toLogic != null) ? lfeat.toLogic() : "[NO LOGIC]";
                GenUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Monitoring thread: Periodically prints the size of each queue.
     */
    private static void monitor() {
        try {
            while (true) {
                System.out.println(
                        "Queue sizes: Vqueue=" + Vqueue.size() +
                                ", SVqueue=" + SVqueue.size() +
                                ", SVOqueue=" + SVOqueue.size() +
                                ", SVOIOqueue=" + SVOIOqueue.size()
                );
                Thread.sleep(10000); // Sleep for 10 seconds
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Entry point: parses arguments and starts the pipeline.
     */
    public static void main(String[] args) {
        if (!parseArgs(args)) return;
        startPipeline();
    }
}
