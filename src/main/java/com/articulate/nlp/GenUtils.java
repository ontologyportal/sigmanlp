package com.articulate.nlp;


import com.articulate.sigma.nlg.NLGUtils;
import com.articulate.sigma.wordNet.WordNet;
import com.articulate.sigma.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;
import java.util.HashSet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Set;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.types.OllamaModelType;
import io.github.ollama4j.utils.OptionsBuilder;
import io.github.ollama4j.utils.Options;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;


/** Utility methods useful for synthetically generating sentences.
 */
public class GenUtils {

    boolean debug = false;
    HashSet<String> previousVariables = new HashSet<>();
    private static final int MAX_VARIABLE_LENGTH = 2;
    static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String NUMBERS = "0123456789";
    //static String OLLAMA_MODEL = "qwen3:1.7b";
    static String OLLAMA_MODEL = "llama3.2";
    static int OLLAMA_PORT;
    public static OllamaAPI ollamaAPI;
    public static Options options;

    /** ***************************************************************
     *   Creates a random unique variable name.
     *   The random variable half the time ends with a number.
     */
    public String randomVariableName() {
        Random random = new Random();
        boolean isUnique = false;
        String newVariable = "";
        while(!isUnique) {
            double biasFactor = 0.75; // higher = more bias toward shorter strings
            double r = random.nextDouble();
            int length = 1 + (int)(Math.log(1 - r) / Math.log(biasFactor)) % MAX_VARIABLE_LENGTH;
            StringBuilder sb = new StringBuilder();
            sb.append("?");
            for (int i = 0; i < length; i++) {
                int index = random.nextInt(CHARACTERS.length());
                sb.append(CHARACTERS.charAt(index));
            }
            if (random.nextBoolean()) {
                int index = random.nextInt(NUMBERS.length());
                sb.append(NUMBERS.charAt(index));
            }
            newVariable = sb.toString();
            if (debug) System.out.println("New Variable: " + newVariable);
            if (!previousVariables.contains(newVariable)) {
                isUnique = true;
                previousVariables.add(newVariable);
            }
        }
        return newVariable;
    }

    public void resetVariables() {
        previousVariables.clear();
    }

    /** ***************************************************************
     *   Creates a file if one doesn't exist already.
     */
    public static void createFileIfDoesNotExists(String fileName) {

        Path filePath = Paths.get(fileName);
        if (Files.exists(filePath)) {
            return;
        } else {
            try {
                Files.createFile(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /** **************************************************************************
     * Gets the best SUMO mapping for a word. Chooses a random equivalent mapping,
     * if no equivalent mapping exists, return null.
     */
    public static String getBestSUMOMapping(Set<String> synsetOfTerm) {

        ArrayList<String> equivalentTerms = new ArrayList();
        for (String synset:synsetOfTerm) {
            String sumoMapping = WordNet.wn.getSUMOMapping(synset);
            if (sumoMapping != null) {
                sumoMapping = sumoMapping.substring(2);
                if (sumoMapping.charAt(sumoMapping.length() - 1) == '=') {
                    equivalentTerms.add(sumoMapping.substring(0, sumoMapping.length() - 1));
                }
            }
        }
        if (!equivalentTerms.isEmpty()) {
            // TODO: Do wordsense disambiguation
            Random rand = new Random();
            return equivalentTerms.get(rand.nextInt(equivalentTerms.size()));
        }
        return null;
    }

    public static String capitalizeFirstLetter(String sentenceToCapitalize) {
            if (sentenceToCapitalize == null || sentenceToCapitalize.isEmpty()) {
                return sentenceToCapitalize;
            }
            return sentenceToCapitalize.substring(0, 1).toUpperCase() + sentenceToCapitalize.substring(1);
    }

    /** ***************************************************************
     *   Writes an english sentence and logic sentence to their
     *   respective files.
     */
    public static void writeEnglishLogicPairToFile(String english, String logic, String outputFileEnglish, String outputFileLogic) {
        FileChannel fileChannel1 = null;
        FileChannel fileChannel2 = null;
        FileLock lock1 = null;
        FileLock lock2 = null;
        english = english + "\n";
        logic = logic + "\n";

        try {
            fileChannel1 = FileChannel.open(Paths.get(outputFileEnglish), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            fileChannel2 = FileChannel.open(Paths.get(outputFileLogic), StandardOpenOption.WRITE, StandardOpenOption.APPEND);

            lock1 = fileChannel1.lock();
            lock2 = fileChannel2.lock();

            ByteBuffer buffer1 = ByteBuffer.wrap(english.getBytes());
            ByteBuffer buffer2 = ByteBuffer.wrap(logic.getBytes());

            fileChannel1.write(buffer1);
            fileChannel2.write(buffer2);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (lock1 != null) lock1.release();
                if (fileChannel1 != null) fileChannel1.close();
                if (lock2 != null) lock2.release();
                if (fileChannel2 != null) fileChannel2.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /** ***************************************************************
     * generate new SUMO statements for relations and output English
     * paraphrase
     */
    public static String toEnglish(String form, KB kb) {

        return NLGUtils.htmlParaphrase("", form, kb.getFormatMap("EnglishLanguage"),
                kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage");
    }

    /** ***************************************************************
     *   Runs a bash command
     */
    public static String runBashCommand(String command) {

        StringBuilder output = new StringBuilder();
        try {
            // Run the command using bash -c
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);  // combine stderr with stdout
            Process process = builder.start();
            // Read the output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command exited with code ").append(exitCode).append("\n");
            }

        } catch (IOException | InterruptedException e) {
            output.append("Error: ").append(e.getMessage()).append("\n");
        }
        return output.toString().trim();
    }

    /**
     * Checks if the Ollama server is listening on a specified port.
     * @param port the port to check
     * @return true if the server is running, false otherwise
     */
    public static boolean isOllamaServerRunning(int port) {
        try (Socket s = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Starts the Ollama server on the specified port if it is not already running.
     * @param port the port to check and use for the server
     * @throws IOException if there is an error starting the server process
     * @throws InterruptedException if the process is interrupted
     */
    public static void startOllamaServer(int port) {
        System.out.println("GenUtils: Starting Ollama Server on port: " + port);
        try {
            if (!isOllamaServerRunning(port)) {
                ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
                pb.environment().put("OLLAMA_HOST", "0.0.0.0:" + port);
                Process process = pb.start();
                Thread.sleep(10000);
                if (!isOllamaServerRunning(port)) {
                    System.err.println("Failed to start Ollama server on port " + port);
                    System.exit(1);
                }
                OLLAMA_PORT = port;
                System.out.println("GenUtils: Ollama server started on port: " + port);
            } else { System.out.println("GenUtils: Ollama server already running on port: " + port);}

            System.out.println("GenUtils: Creating connection to Ollama server using model: " + OLLAMA_MODEL);
            ollamaAPI = new OllamaAPI("http://localhost:" + port + "/");
            ollamaAPI.setVerbose(false);
            options = new OptionsBuilder().setTemperature(0.0f).build();
            System.out.println("GenUtils: Connected to Ollama server");
        } catch (Exception e) {
            System.err.println("Failed to start Ollama server on port " + port);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void changeOllamaModel(String new_model) {
        OLLAMA_MODEL = new_model;
    }

    public static String askOllama(String prompt) {
        try {
            if (ollamaAPI == null) {
                startOllamaServer(11436);
            }
            ollamaAPI.setRequestTimeoutSeconds(60);
            OllamaResult result =
                    ollamaAPI.generate(OLLAMA_MODEL, prompt, false, options);
            return result.getResponse().toString();
        } catch (Exception e) {
            System.out.println("Error in GenUtils.askOllama(): " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}
