package nl.aardbeitje.catcam.train;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class MainCustomVision {

    public static final String PREDICTION_KEY = "c311b560d0a44104bad812b99987b7c9";
    public static final String PREDICTION_URL = "https://southcentralus.api.cognitive.microsoft.com/customvision/v1.1/Prediction/c700c7d7-24c0-4cb6-9fd1-08708e3f1911/image?";

    public static class Concept {
        public Concept(String name, float value) {
            this.name = name;
            this.value = value;
        }

        String name;
        float value;
    }

    public static void main(String[] args) throws IOException {

        Images images = new Images();
        images.newFiles().forEach(p -> {
            try {
            assessFile(images, p);
            } catch(Exception e ) {
                System.err.println("Could not assess file " + p + ", continuing with next one");
            }
        });

    }

    private static void assessFile(Images images, Path p) {
        List<Concept> concepts = predict(p.toFile());

        System.out.println("File: " + p);
        System.out.print("Predicted: ");

        boolean moved = false;
        for (Concept concept : concepts) {
            try {
                System.out.print(" " + concept.name + "(" + concept.value * 100.0f + "%)");
                if (concept.value > 0.2f && "CAT".equalsIgnoreCase(concept.name)) {
                    Files.copy(p, images.pathForNewCat().resolve(p.getFileName()));
                    moved = true;
                }
                if (concept.value > 0.2f && "EMPTY".equalsIgnoreCase(concept.name)) {
                    Files.copy(p, images.pathForNewEmpty().resolve(p.getFileName()));
                    moved = true;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            if (moved) {
                Files.delete(p);
            } else {
                Files.move(p, images.pathForNewUnknown().resolve(p.getFileName()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(".");
    }

    public static List<Concept> predict(File file) {
        try {
            HttpClient httpclient = HttpClientBuilder.create().build();

            URIBuilder builder = new URIBuilder(PREDICTION_URL);

            URI uri = builder.build();
            HttpPost request = new HttpPost(uri);

            // Request headers.
            request.setHeader("Prediction-Key", PREDICTION_KEY);
            request.setHeader("Content-Type", "application/octet-stream");

            request.setEntity(null);
            //
            // MultipartEntity mpEntity = new MultipartEntity();
            // ContentBody cbFile = new FileBody(file, "image/jpeg");
            // mpEntity.addPart("userfile", cbFile);

            request.setEntity(new FileEntity(file, "image/jpeg"));
            HttpResponse response = httpclient.execute(request);
            HttpEntity resEntity = response.getEntity();

            List<Concept> concepts = new ArrayList<>();
            System.out.println(response.getStatusLine());
            if (resEntity != null) {
                String jsonString = EntityUtils.toString(resEntity);
                JSONObject json = new JSONObject(jsonString);
                for (int i = 0; i < json.getJSONArray("Predictions").length(); i++) {
                    JSONObject o = json.getJSONArray("Predictions").getJSONObject(i);
                    float prob = o.getNumber("Probability").floatValue();
                    String name = o.getString("Tag");
                    concepts.add(new Concept(name, prob));
                }
            }
            if (resEntity != null) {
                resEntity.consumeContent();
            }

            httpclient.getConnectionManager().shutdown();

            return concepts;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
