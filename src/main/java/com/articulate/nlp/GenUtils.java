package com.articulate.nlp;


import com.articulate.sigma.nlg.NLGUtils;
import com.articulate.sigma.*;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.HashSet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;



/** Utility methods useful for synthetically generating sentences.
 */
public class GenUtils {

    boolean debug = false;
    HashSet<String> previousVariables = new HashSet<>();
    private static final int MAX_VARIABLE_LENGTH = 2;
    static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String NUMBERS = "0123456789";

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
    public void createFileIfDoesNotExists(String fileName) {

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


}
