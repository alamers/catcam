package nl.aardbeitje.catcam.train;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

public class Images {

    private static final int MAX_SIZE = 10;
    private static final int EVALUATE_MOD = 5;
    private final Path basePath = Paths.get("/Users/arjanl/camera/");

    private Map<Path, Set<Concept>> allFileConcepts = new LinkedHashMap<>();
    private Map<Concept, Set<Path>> allConceptFiles = new LinkedHashMap<>();
    private final Random rnd = new Random();

    private Map<Path, Set<Concept>> trainMap = new LinkedHashMap<>();
    private Map<Path, Set<Concept>> verifyMap = new LinkedHashMap<>();

    public void prepare() throws IOException {
        addConcept(Concept.CAT);
        addConcept(Concept.EMPTY);
        // todo verify that empty cannot be mixed with cat or fox
        split();
        
        System.out.println("  training set: " + trainMap.size() + " images");
        System.out.println("  full set: " + allFileConcepts.size() + " images");
        allConceptFiles.forEach( (c,s) -> System.out.println("    concept " + c + ": " + s.size() + " images"));
    }
    
    public Stream<Path> newFiles() throws IOException {
        Path newFilesDir = basePath.resolve("new");
        return Files.list(newFilesDir);
    }

    public Path pathForNewUnknown() {
        return basePath.resolve("new-unknown");
    }
    public Path pathForNewCat() {
        return basePath.resolve("new-cat");
    }

    public Path pathForNewEmpty() {
        return basePath.resolve("new-empty");
    }

    public Map<Path, Set<Concept>> getTrainMap() {
        return trainMap;
    }

    public Map<Path, Set<Concept>> getVerifyMap() {
        return verifyMap;
    }

    private void split() {
        // randomly assign 2/3 to train set and 1/3 to verify set

        allConceptFiles.keySet().forEach(concept -> {
            allConceptFiles.get(concept).forEach(p -> {
                if (p.toString().hashCode() % EVALUATE_MOD == 0) {
                    verifyMap.put(p, allFileConcepts.get(p));
                } else {
                    trainMap.put(p, allFileConcepts.get(p));
                }
            });
        });
    }

    private void addConcept(Concept concept) throws IOException {
        Path dir = basePath.resolve(concept.name().toLowerCase());
        Files.list(dir).forEach(p -> addFile(p, concept));
    }

    private void addFile(Path p, Concept concept) {
//        if (allConceptFiles.get(concept)!=null && allConceptFiles.get(concept).size() >= MAX_SIZE) {
//            return;
//        }
        
        addConceptToFile(p, concept);
        addFileToConcept(p, concept);
    }

    private void addConceptToFile(Path p, Concept concept) {
        Set<Path> paths = allConceptFiles.get(concept);
        if (paths == null) {
            paths = new LinkedHashSet<Path>();
            allConceptFiles.put(concept, paths);
        }
        paths.add(p);
    }

    private void addFileToConcept(Path p, Concept concept) {
        Set<Concept> concepts = allFileConcepts.get(p);
        if (concepts == null) {
            concepts = new LinkedHashSet<Concept>();
            allFileConcepts.put(p, concepts);
        }
        concepts.add(concept);
    }

}
