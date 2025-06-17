package com.articulate.nlp;

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


import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GenProcesses {
    // Nested class to hold the parts of speech for each sentence
    private static class SentenceData {
        public String verb;
        public String subject;
        public String directObject;
        public String indirectObject;

        @Override
        public String toString() {
            return String.join(" ",
                    subject != null ? subject : "[NO SUBJECT]",
                    verb != null ? verb : "[NO VERB]",
                    directObject != null ? directObject : "[NO DIRECT OBJ]",
                    indirectObject != null ? indirectObject : "[NO INDIRECT OBJ]"
            );
        }
    }

    // Enum outlining generation mode.
    public enum GenerationMode {
        RANDOM("Random selection"),
        WORDFREQ("Word frequency-based selection"),
        OLLAMA("Ollama API integration"),
        FRAMENETLITE_RAND("FrameNetLite random selection"),
        FRAMENETLITE_WORDFREQ("FrameNetLite word frequency-based selection"),
        FRAMENETLITE_OLLAMA("FrameNetLite Ollama API integration");

        private final String description;
        GenerationMode(String description) {this.description = description;}
        public String getDescription() {return description;}
        @Override public String toString() {return name() + ": " + description;}
        // Utility method to get enum from string input (case-insensitive)
        public static GenerationMode fromString(String input) {
            for (GenerationMode mode : values()) {
                if (mode.name().equalsIgnoreCase(input)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Invalid GenerationMode: " + input);
        }
    }

    // Queues for each pipeline stage
    private static final BlockingQueue<SentenceData> Vqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<SentenceData> SVqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<SentenceData> SVOqueue = new LinkedBlockingQueue<>();
    private static final BlockingQueue<SentenceData> SVOIOqueue = new LinkedBlockingQueue<>();

    public static KBLite kbLite;
    private static String outputFileEnglish;
    private static String outputFileLogic;
    private static int numSentences;
    private static final AtomicInteger postProcessedCount = new AtomicInteger(0);

    // Used to stop the pipeline threads gracefully. Placed in the queue after sentences have been generated.
    private static final SentenceData POISON_PILL = new SentenceData();
    private static volatile boolean monitoring = true;
    private static GenerationMode generationMode;

    private static Set<String> processesSet;
    private static Set<String> AutonomousAgentSet;
    private static Set<String> EntitySet;


    /**
     * Parses command-line arguments for output file prefix and number of sentences.
     * Returns false if arguments are invalid.
     */
    private static boolean init(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java GenProcesses <output_file_prefix> <number_of_sentences> <generation_mode>");
            System.out.println("Available generation modes: RANDOM, WORDFREQ, OLLAMA, FRAMENETLITE_RAND, FRAMENETLITE_WORDFREQ, FRAMENETLITE_OLLAMA");
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
        try {
            generationMode = GenerationMode.fromString(args[2]);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid generation mode: " + args[2]);
            System.out.println("Available generation modes: RANDOM, WORDFREQ, OLLAMA, FRAMENETLITE_RAND, FRAMENETLITE_WORDFREQ, FRAMENETLITE_OLLAMA");
            return false;
        }
        kbLite = new KBLite("SUMO");
        processesSet = kbLite.getChildClasses("Process");

        GenUtils.createFileIfDoesNotExists(outputFileEnglish);
        GenUtils.createFileIfDoesNotExists(outputFileLogic);
        return true;
    }


    /**
     * Starts all pipeline and monitoring threads.
     */
    private static Thread monitorThread;

    private static void startPipeline() {
        // Start pipeline threads (no need to keep references unless you want to join them)
        new Thread(GenProcesses::verbStage).start();
        new Thread(GenProcesses::subjStage).start();
        new Thread(GenProcesses::directObjStage).start();
        new Thread(GenProcesses::indirectObjStage).start();

        // Keep references for postProcess and monitor threads
        Thread postProcessThread = new Thread(GenProcesses::postProcessStage);
        monitorThread = new Thread(GenProcesses::monitor);
        postProcessThread.start();
        monitorThread.start();

        // Wait for postProcessStage to finish
        try {
            postProcessThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Stop the monitor thread
        monitoring = false;
        monitorThread.interrupt(); // In case it's sleeping
    }

    /**
     * Pipeline stage 1: Generates verbs and puts SentenceData objects into Vqueue.
     */
    private static void verbStage() {
        try {
            for (int i = 0; i < numSentences; i++) {
                SentenceData data = new SentenceData();
                data.verb = getVerb();
                Vqueue.put(data);
            }
            Vqueue.put(POISON_PILL);  // Use POISON_PILL instead of null
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String getVerb() {
        switch (generationMode) {
            case RANDOM:
                // TODO: Implement random verb selection
                return "eats";
            case WORDFREQ:
                // TODO: Implement word frequency-based verb selection
                return "consumes";
            case OLLAMA:
                // TODO: Implement Ollama API verb selection
                return "devours";
            case FRAMENETLITE_RAND:
                // TODO: Implement FrameNetLite random verb selection
                return "chews";
            case FRAMENETLITE_WORDFREQ:
                // TODO: Implement FrameNetLite word frequency-based verb selection
                return "nibbles";
            case FRAMENETLITE_OLLAMA:
                // TODO: Implement FrameNetLite Ollama API verb selection
                return "gobbles";
            default:
                throw new IllegalStateException("Unexpected value: " + generationMode);
        }
    }

    /**
     * Pipeline stage 2: Adds subject to SentenceData objects from Vqueue and puts them into SVqueue.
     */
    private static void subjStage() {
        try {
            while (true) {
                SentenceData data = Vqueue.take();
                if (data == POISON_PILL) {
                    SVqueue.put(POISON_PILL);
                    break;
                }
                data.subject = getSubject();
                SVqueue.put(data);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }


    private static String getSubject() {
        switch (generationMode) {
            case RANDOM:
                return "The cat";
            case WORDFREQ:
                return "The dog";
            case OLLAMA:
                return "The bird";
            case FRAMENETLITE_RAND:
                return "The mouse";
            case FRAMENETLITE_WORDFREQ:
                return "The rabbit";
            case FRAMENETLITE_OLLAMA:
                return "The fox";
            default:
                throw new IllegalStateException("Unexpected value: " + generationMode);
        }
    }

    /**
     * Pipeline stage 3: Adds direct object to SentenceData objects from SVqueue and puts them into SVOqueue.
     */
    private static void directObjStage() {
        try {
            while (true) {
                SentenceData data = SVqueue.take();
                if (data == POISON_PILL) {
                    SVOqueue.put(POISON_PILL);
                    break;
                }
                data.directObject = getDirectObject();
                SVOqueue.put(data);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }


    private static String getDirectObject() {
        switch (generationMode) {
            case RANDOM:
                return "fish";
            case WORDFREQ:
                return "bone";
            case OLLAMA:
                return "seed";
            case FRAMENETLITE_RAND:
                return "cheese";
            case FRAMENETLITE_WORDFREQ:
                return "carrot";
            case FRAMENETLITE_OLLAMA:
                return "grape";
            default:
                throw new IllegalStateException("Unexpected value: " + generationMode);
        }
    }

    /**
     * Pipeline stage 4: Adds indirect object to SentenceData objects from SVOqueue and puts them into SVOIOqueue.
     */
    private static void indirectObjStage() {
        try {
            while (true) {
                SentenceData data = SVOqueue.take();
                if (data == POISON_PILL) {
                    SVOIOqueue.put(POISON_PILL);
                    break;
                }
                data.indirectObject = getIndirectObject();
                SVOIOqueue.put(data);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String getIndirectObject() {
        switch (generationMode) {
            case RANDOM:
                return "with a fork";
            case WORDFREQ:
                return "in the yard";
            case OLLAMA:
                return "under the tree";
            case FRAMENETLITE_RAND:
                return "in the house";
            case FRAMENETLITE_WORDFREQ:
                return "on the hill";
            case FRAMENETLITE_OLLAMA:
                return "by the river";
            default:
                throw new IllegalStateException("Unexpected value: " + generationMode);
        }
    }

    /**
     * Pipeline stage 5: Processes complete SentenceData objects from SVOIOqueue and writes results to files.
     * Increments the postProcessedCount for each processed item.
     */
    private static void postProcessStage() {
        try {
            while (true) {
                SentenceData data = SVOIOqueue.take();
                if (data == POISON_PILL) break;

                String english = data.toString();
                String logic = generateLogic(data);  // Add your logic generation here

                GenUtils.writeEnglishLogicPairToFile(english, logic, outputFileEnglish, outputFileLogic);

                //System.out.println("ENGLISH: " + english);
                //System.out.println("LOGIC: " + logic);
                //System.out.println("-----");

                postProcessedCount.incrementAndGet();
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Generates a logic string for the sentence.
     * Replace this with your actual logic generation.
     */
    private static String generateLogic(SentenceData data) {
        return "[LOGIC REPRESENTATION] " + data.toString();
    }

    /**
     * Monitoring thread: Periodically prints the size of each queue and the number postprocessed.
     */
    private static void monitor() {
        try {
            while (monitoring) {
                System.out.println(
                        "Queue sizes: Vqueue=" + Vqueue.size() +
                                ", SVqueue=" + SVqueue.size() +
                                ", SVOqueue=" + SVOqueue.size() +
                                ", SVOIOqueue=" + SVOIOqueue.size() +
                                " | Postprocessed: " + postProcessedCount.get()
                );
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            // Graceful exit
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Entry point: parses arguments and starts the pipeline.
     */
    public static void main(String[] args) {
        if (!init(args)) return;
        startPipeline();
        System.out.println("Finished generating sentences.");
    }
}
