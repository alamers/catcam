package nl.aardbeitje.catcam.train;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.request.model.Action;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.ConceptModel;
import clarifai2.dto.model.Model;
import clarifai2.dto.model.ModelTrainingStatus;
import clarifai2.dto.model.ModelVersion;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;

public class Main {

    private static final float REQUIRED_CONFIDENCE = 0.8f;

    public static final String API_KEY_ALL_ACCESS = "bf879da72f5b4ec9b4c6665f84b02a8f";

    private static int correct = 0;
    private static int falsePositive = 0;
    private static int falseNegative = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        // explore();
//        testVerify();
        // fullCycle();
        assessIncoming();
    }

    private static void assessIncoming() throws IOException {

        ClarifaiClient client = new ClarifaiBuilder(API_KEY_ALL_ACCESS).buildSync();

        ConceptModel conceptModel = client.getModelByID("CatCamModel").executeSync().get().asConceptModel();

        Images images = new Images();
        images.newFiles().forEach(p -> {
            List<ClarifaiOutput<Concept>> concepts = conceptModel.predict().withInputs(ClarifaiInput.forImage(p.toFile())).executeSync().get();
            System.out.println("File: " + p);
            System.out.print("Predicted: ");

            boolean moved = false;
            for (Concept concept : concepts.iterator().next().data()) {
                try {
                    System.out.print(" " + concept.name() + "(" + concept.value() * 100.0f + "%)");
                    if (concept.value() > 0.2f && "CAT".equals(concept.name())) {
                        Files.copy(p, images.pathForNewCat().resolve(p.getFileName()));
                        moved = true;
                    }
                    if (concept.value() > 0.2f && "EMPTY".equals(concept.name())) {
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

        });

    }

    private static void explore() {
        ClarifaiClient client = new ClarifaiBuilder(API_KEY_ALL_ACCESS).buildSync();

        ConceptModel conceptModel = client.getDefaultModels().generalModel().asConceptModel();
        List<Concept> concepts = conceptModel.outputInfo().concepts();
        for (Concept c : concepts) {
            System.out.println(c.name());
        }
    }

    private static void testVerify() throws IOException, InterruptedException {
        ClarifaiClient client = new ClarifaiBuilder(API_KEY_ALL_ACCESS).buildSync();

        System.out.println("Preparing image set");
        Images images = new Images();
        images.prepare();

        Collection<Concept> ownConcepts = new ArrayList<>();
        ownConcepts.add(Concept.forID(nl.aardbeitje.catcam.train.Concept.CAT.name()));
        verify(client.getModelByID("CatCamModel").executeSync().get().asConceptModel(), images, ownConcepts);
        int ownCorrect = correct;
        int ownFalsePositive = falsePositive;
        int ownFalseNegative = falseNegative;

        // Collection<Concept> generalConcepts = new ArrayList<>();
        // generalConcepts.add(Concept.forID("animal"));
        // verify(client.getDefaultModels().generalModel().asConceptModel(),
        // images, generalConcepts);
        // int generalCorrectness = correct;
        // int generalFalsePositive = falsePositive;
        // int generalFalseNegative = falseNegative;

        System.out.println("own score: " + (100.0f * ownCorrect / images.getVerifyMap().size()) + "%");
        System.out.println("  false positives: " + (100.0f * ownFalsePositive / images.getVerifyMap().size()) + "% (" + ownFalsePositive + ")");
        System.out.println("  false negatives: " + (100.0f * ownFalseNegative / images.getVerifyMap().size()) + "% (" + ownFalseNegative + ")");

        // System.out.println( "general model score: " +
        // (100.0f*generalCorrectness/images.getVerifyMap().size()) + "%");
        // System.out.println( " false positives: " +
        // (100.0f*generalFalsePositive/images.getVerifyMap().size()) + "% (" +
        // generalFalsePositive + ")");
        // System.out.println( " false negatives: " +
        // (100.0f*generalFalseNegative/images.getVerifyMap().size()) + "% (" +
        // generalFalseNegative + ")");

    }

    private static void verify(final ConceptModel conceptModel, Images images, Collection<Concept> verifyConcepts) {
        System.out.println("Verifying model");
        correct = 0;
        falsePositive = 0;
        falseNegative = 0;
        images.getVerifyMap().forEach((p, s) -> {
            try {
                List<ClarifaiOutput<Concept>> concepts = conceptModel.predict().selectConcepts(verifyConcepts).withInputs(ClarifaiInput.forImage(p.toFile())).executeSync().get();
                System.out.println("File: " + p);
                System.out.print("Predicted: ");
                concepts.iterator().next().data().forEach(c -> {
                    System.out.print(" " + c.asConcept().name() + "(" + c.asConcept().value() * 100.0f + "%)");
                });
                System.out.println(".");

                System.out.print("Actually: ");
                s.forEach(c -> System.out.print(c));

                // we do one image at a time, so pick the first recognized
                // concepts
                if (evaluate(concepts.iterator().next().data(), s)) {
                    System.out.print(" = correct!");
                } else {
                    System.out.print(" = INCORRECT!");
                }
                System.out.println(".");
            } catch (Exception e) {
                System.err.println("error verifying file " + p);
                e.printStackTrace();
            }
        });
        System.out.println("score: " + (100.0f * correct / images.getVerifyMap().size()) + "%");
    }

    private static boolean evaluate(List<Concept> concepts, Set<nl.aardbeitje.catcam.train.Concept> s) {
        boolean detectedCat = false;

        for (Concept c : concepts) {
            if (c.asConcept().name().equalsIgnoreCase("cat")) {
                if (c.asConcept().value() > REQUIRED_CONFIDENCE) {
                    detectedCat = true;
                    break;
                }
            }
        }

        boolean result = s.contains(nl.aardbeitje.catcam.train.Concept.CAT) == detectedCat;
        if (result) {
            correct++;
        } else {
            if (detectedCat) {
                falsePositive++;
            } else {
                falseNegative++;
            }
        }
        return result;
    }

    private static void fullCycle() throws IOException, InterruptedException {
        ClarifaiClient client = new ClarifaiBuilder(API_KEY_ALL_ACCESS).buildSync();

        System.out.println("Deleting previous work");
        client.deleteAllInputs().executeSync();
        client.deleteAllModels().executeSync();

        System.out.println("Preparing image set");
        Images images = new Images();
        images.prepare();

        System.out.println("Uploading concepts");
        client.addConcepts().plus(Concept.forID(nl.aardbeitje.catcam.train.Concept.CAT.name())).executeSync();
        client.addConcepts().plus(Concept.forID(nl.aardbeitje.catcam.train.Concept.EMPTY.name())).executeSync();

        System.out.println("Uploading train set");
        images.getTrainMap().forEach((p, s) -> {
            List<Concept> concepts = s.stream().map(c -> Concept.forID(c.name())).collect(Collectors.toList());
            System.out.println("  uploading " + p + " with concepts " + concepts);
            client.addInputs().plus(ClarifaiInput.forImage(p.toFile()).withConcepts(concepts)).executeSync();
        });

        System.out.println("Creating model");
        ConceptModel conceptModel = client.createModel("CatCamModel").executeSync().get();

        client.modifyModel(conceptModel.id()).withConcepts(Action.MERGE, Concept.forID(nl.aardbeitje.catcam.train.Concept.CAT.name())).executeSync();
        client.modifyModel(conceptModel.id()).withConcepts(Action.MERGE, Concept.forID(nl.aardbeitje.catcam.train.Concept.EMPTY.name())).executeSync();

        System.out.println("Training model");
        System.out.println(client.trainModel(conceptModel.id()).executeSync().get());

        System.out.print("Waiting for model to be ready:");
        ModelVersion version;
        do {
            version = conceptModel.getVersions().getPage(1).executeSync().get().iterator().next();
            System.out.print(" " + version.status());
            Thread.sleep(1000);
        } while (version.status() != ModelTrainingStatus.TRAINED);
        System.out.println(". Ready :)");

        Collection<Concept> ownConcepts = new ArrayList<>();
        ownConcepts.add(Concept.forID(nl.aardbeitje.catcam.train.Concept.CAT.name()));

        verify(conceptModel, images, ownConcepts);
    }

}
