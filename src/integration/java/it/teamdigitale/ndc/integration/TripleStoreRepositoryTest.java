package it.teamdigitale.ndc.integration;

import it.teamdigitale.ndc.repository.TripleStoreRepository;
import it.teamdigitale.ndc.repository.TripleStoreRepositoryProperties;
import org.apache.jena.arq.querybuilder.SelectBuilder;
import org.apache.jena.arq.querybuilder.UpdateBuilder;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.testcontainers.utility.DockerImageName.parse;

@Testcontainers
public class TripleStoreRepositoryTest {

    private static final int VIRTUOSO_PORT = 8890;
    String sparqlUrl = "http://localhost:" + virtuoso.getMappedPort(VIRTUOSO_PORT) + "/sparql";
    final String graphName = this.getClass().getSimpleName();
    TripleStoreRepository repository;

    private static GenericContainer virtuoso = new GenericContainer(parse("tenforce/virtuoso"))
            .withReuse(true)
            .withExposedPorts(VIRTUOSO_PORT)
            .withEnv("DBA_PASSWORD", "dba")
            .withEnv("SPARQL_UPDATE", "true");

    @BeforeAll
    public static void beforeAll() {
        virtuoso.start();
    }

    @BeforeEach
    public void beforeEach() {
        TripleStoreRepositoryProperties properties = new TripleStoreRepositoryProperties(sparqlUrl, "dba", "dba");
        repository = new TripleStoreRepository(properties);
        deleteAllTriplesFromGraph(graphName, sparqlUrl);
        deleteAllTriplesFromGraph("http://agid", sparqlUrl);
        deleteAllTriplesFromGraph("http://istat", sparqlUrl);
    }

    @Test
    void shouldSaveGivenModelInVirtuosoTestcontainer() {
        // given
        Model model = RDFDataMgr.loadModel("src/test/resources/testdata/onto.ttl");

        // when
        repository.save(graphName, model);

        // then
        Query findPeriodicity = new SelectBuilder()
                .addVar("o")
                .addWhere(
                        "<https://w3id.org/italia/onto/CulturalHeritage>",
                        "<http://purl.org/dc/terms/accrualPeriodicity>",
                        "?o"
                )
                .from(graphName)
                .build();
        ResultSet resultSet =
                QueryExecutionFactory.sparqlService(sparqlUrl, findPeriodicity).execSelect();

        assertThat(resultSet.hasNext()).isTrue();
        QuerySolution querySolution = resultSet.next();
        assertThat(querySolution).as("Check a valid result row").isNotNull();
        assertThat(querySolution.get("o").toString()).as("Check variable bound value")
                .isEqualTo("http://publications.europa.eu/resource/authority/frequency/IRREG");
    }

    @Test
    void shouldDeleteOnlyTheSpecifiedNamedGraph() {
        // given
        SelectBuilder findTitle = new SelectBuilder()
                .addVar("b")
                .addVar("t")
                .addWhere(
                        "?b",
                        "<http://example/title>",
                        "?t"
                );
        UpdateRequest updateRequest = new UpdateBuilder()
                .addInsert("<http://agid>", "<http://example/egbook>", "<http://example/title>",
                        "This is an example title")
                .buildRequest();
        UpdateExecutionFactory.createRemote(updateRequest, sparqlUrl).execute();

        //when
        ResultSet resultSet = repository.select(findTitle);
        assertThat(resultSet.hasNext()).isTrue();
        assertThat(resultSet.next().get("b").asResource().getURI()).isEqualTo("http://example/egbook");
        assertThat(resultSet.hasNext()).isFalse();

        // when
        repository.clearExistingNamedGraph("http://agid");
        resultSet = repository.select(findTitle);

        // then
        assertThat(resultSet.hasNext()).isFalse();
    }

    private void deleteAllTriplesFromGraph(String graphName, String sparqlUrl) {
        UpdateRequest updateRequest = UpdateFactory.create();
        updateRequest.add("CLEAR GRAPH <" + graphName + ">");
        UpdateExecutionFactory.createRemote(updateRequest, sparqlUrl).execute();
    }
}
