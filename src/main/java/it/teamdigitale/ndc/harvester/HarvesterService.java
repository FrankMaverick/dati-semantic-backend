package it.teamdigitale.ndc.harvester;

import it.teamdigitale.ndc.harvester.exception.SinglePathProcessingException;
import it.teamdigitale.ndc.harvester.model.CvPath;
import it.teamdigitale.ndc.harvester.model.SemanticAssetPath;
import it.teamdigitale.ndc.harvester.pathprocessors.ControlledVocabularyPathProcessor;
import it.teamdigitale.ndc.harvester.pathprocessors.OntologyPathProcessor;
import it.teamdigitale.ndc.repository.TripleStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarvesterService {
    private final AgencyRepositoryService agencyRepositoryService;
    private final ControlledVocabularyPathProcessor controlledVocabularyPathProcessor;
    private final OntologyPathProcessor ontologyPathProcessor;
    private final TripleStoreRepository tripleStoreRepository;

    public void harvest(String repoUrl) throws IOException {
        log.info("Processing repo {}", repoUrl);
        try {
            Path path = cloneRepoToTempPath(repoUrl);

            cleanUpTripleStore(repoUrl);

            harvestControlledVocabularies(repoUrl, path);
            harvestOntologies(repoUrl, path);

            log.info("Repo {} processed", repoUrl);

        } catch (IOException e) {
            log.error("Exception while processing {}", repoUrl, e);
            throw e;
        }
    }

    private Path cloneRepoToTempPath(String repoUrl) throws IOException {
        Path path = agencyRepositoryService.cloneRepo(repoUrl);
        log.debug("Repo {} cloned to temp folder {}", repoUrl, path);
        return path;
    }

    private void cleanUpTripleStore(String repoUrl) {
        log.debug("Cleaning up triple store for {}", repoUrl);
        tripleStoreRepository.clearExistingNamedGraph(repoUrl);
    }

    private void harvestOntologies(String repoUrl, Path rootPath) {
        log.debug("Looking for ontology paths");

        List<SemanticAssetPath> ontologyPaths = agencyRepositoryService.getOntologyPaths(rootPath);
        log.debug("Found {} ontology path(s) for processing", ontologyPaths.size());

        for (SemanticAssetPath ontologyPath : ontologyPaths) {
            try {
                ontologyPathProcessor.process(repoUrl, ontologyPath);
            } catch (SinglePathProcessingException e) {
                log.error("Error processing ontology {} in repo {}", ontologyPath, repoUrl, e);
            }
        }
    }

    private void harvestControlledVocabularies(String repoUrl, Path rootPath) {
        log.debug("Looking for vocabulary paths");

        List<CvPath> cvPaths = agencyRepositoryService.getControlledVocabularyPaths(rootPath);
        log.debug("Found {} controlled vocabulary path(s) for processing", cvPaths.size());

        for (CvPath cvPath : cvPaths) {
            try {
                controlledVocabularyPathProcessor.process(repoUrl, cvPath);
            } catch (SinglePathProcessingException e) {
                log.error("Error processing controlled vocabulary {} in repo {}", cvPath, repoUrl, e);
            }
        }
    }
}
