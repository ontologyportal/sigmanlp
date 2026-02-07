package com.articulate.nlp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.articulate.nlp.GenOrientationImages.ImageRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.util.*;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import com.openai.azure.credential.AzureApiKeyCredential;

public class GenOrientationImages {
    private static String content; 
    public static class ImageRecord {

        private int id;
        private List<String> image_list;
        private String language_description;
        private String logical_description;
        

        // Required no-arg constructor
        public ImageRecord() {}

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public List<String> getImage_list() {
            return image_list;
        }

        public void setImage_list(List<String> image_list) {
            this.image_list = image_list;
        }

        public String getLanguage_description() {
            return language_description;
        }

        public void setLanguage_description(String language_description) {
            this.language_description = language_description;
        }

        public String getLogical_description() {
            return logical_description;
        }

        public void setLogical_description(String logical_description) {
            this.logical_description = logical_description;
        }

        @Override
        public String toString() {
            return "ImageRecord{" +
                    "id=" + id +
                    ", image_list=" + image_list +
                    ", language_description='" + language_description + '\'' +
                    ", logical_description='" + logical_description + '\'' +
                    '}';
        }
    }

    public static String readStringFromFile() throws IOException {
        return Files.readString(Path.of("image_api_key.txt")).trim();
        //"image_api_key.txt"
    }



    public static void generateImage(String prompt, String content) {

        String endpoint = "https://llm-agents-east-resource.openai.azure.com/openai/v1/";
        String deploymentName = "gpt-image-1-mini";
        String apiKey = content;
        System.out.println("Entered Gen image method");
        //System.out.println(apiKey);
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(endpoint)
                .credential(AzureApiKeyCredential.create(apiKey))
                .build();

        ImageGenerateParams imageGenerateParams = ImageGenerateParams.builder()
                .prompt(prompt)
                .model(deploymentName)
                .n(1)
                .build();

        client.images().generate(imageGenerateParams).data().orElseThrow().forEach(image -> {
                try {   
                        String base64String = image.b64Json().orElseThrow();
                        byte[] imageData = Base64.getDecoder().decode(base64String);
                        Files.write(Paths.get("images/output.png"), imageData);
                } catch (IOException e) {
                        e.printStackTrace();
                }
        });
    }
    
    public static void main(String[] args) {
        
try {
            ObjectMapper mapper = new ObjectMapper();
            File jsonFile = new File("data.json");

            List<ImageRecord> records = mapper.readValue(
                    jsonFile,
                    new TypeReference<List<ImageRecord>>() {}
            );

            for (ImageRecord record : records) {
                System.out.println("ID: " + record.getId());
                System.out.println("Images: " + record.getImage_list());
                System.out.println("Language: " + record.getLanguage_description());
                System.out.println("Logical: " + record.getLogical_description());
                System.out.println("----");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
         content = readStringFromFile();
            //System.out.println(content);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        generateImage("The coat is on the four-poster bed.", content);
    }
}
