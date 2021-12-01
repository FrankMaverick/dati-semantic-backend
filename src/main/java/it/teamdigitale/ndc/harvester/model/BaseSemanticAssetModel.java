package it.teamdigitale.ndc.harvester.model;

import it.teamdigitale.ndc.harvester.SemanticAssetType;
import it.teamdigitale.ndc.harvester.model.exception.InvalidModelException;
import static it.teamdigitale.ndc.harvester.model.extractors.LiteralExtractor.extract;
import static it.teamdigitale.ndc.harvester.model.extractors.LiteralExtractor.extractAll;
import static it.teamdigitale.ndc.harvester.model.extractors.LiteralExtractor.extractOptional;
import static it.teamdigitale.ndc.harvester.model.extractors.NodeExtractor.extractMaybeNode;
import static it.teamdigitale.ndc.harvester.model.extractors.NodeExtractor.extractMaybeNodes;
import static it.teamdigitale.ndc.harvester.model.extractors.NodeExtractor.extractNode;
import static it.teamdigitale.ndc.harvester.model.extractors.NodeExtractor.extractNodes;
import it.teamdigitale.ndc.harvester.model.extractors.NodeSummaryExtractor;
import static it.teamdigitale.ndc.harvester.model.extractors.NodeSummaryExtractor.maybeNodeSummaries;
import it.teamdigitale.ndc.harvester.model.index.NodeSummary;
import it.teamdigitale.ndc.harvester.model.index.SemanticAssetMetadata;
import static java.lang.String.format;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import org.apache.jena.sparql.vocabulary.FOAF;
import static org.apache.jena.vocabulary.DCAT.contactPoint;
import static org.apache.jena.vocabulary.DCAT.keyword;
import static org.apache.jena.vocabulary.DCAT.theme;
import static org.apache.jena.vocabulary.DCTerms.accrualPeriodicity;
import static org.apache.jena.vocabulary.DCTerms.conformsTo;
import static org.apache.jena.vocabulary.DCTerms.creator;
import static org.apache.jena.vocabulary.DCTerms.description;
import static org.apache.jena.vocabulary.DCTerms.issued;
import static org.apache.jena.vocabulary.DCTerms.language;
import static org.apache.jena.vocabulary.DCTerms.modified;
import static org.apache.jena.vocabulary.DCTerms.publisher;
import static org.apache.jena.vocabulary.DCTerms.rightsHolder;
import static org.apache.jena.vocabulary.DCTerms.subject;
import static org.apache.jena.vocabulary.DCTerms.temporal;
import static org.apache.jena.vocabulary.DCTerms.title;
import static org.apache.jena.vocabulary.OWL.versionInfo;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.VCARD4;

public abstract class BaseSemanticAssetModel implements SemanticAssetModel {
    protected final Model rdfModel;
    protected final String source;
    private Resource mainResource;
    private String repoUrl;

    public BaseSemanticAssetModel(Model rdfModel, String source, String repoUrl) {
        this.rdfModel = rdfModel;
        this.source = source;
        this.repoUrl = repoUrl;
    }

    @Override
    public Model getRdfModel() {
        return rdfModel;
    }

    @Override
    public Resource getMainResource() {
        if (mainResource == null) {
            mainResource = getUniqueResourceByType(getMainResourceTypeIri());
        }

        return mainResource;
    }

    protected abstract String getMainResourceTypeIri();

    private Resource getUniqueResourceByType(String resourceTypeIri) {
        List<Resource> resources = rdfModel
            .listResourcesWithProperty(RDF.type, createResource(resourceTypeIri))
            .toList();

        checkFileDeclaresSingleResource(resources, resourceTypeIri);
        return resources.get(0);
    }

    private void checkFileDeclaresSingleResource(List<Resource> resources, String typeIri) {
        if (resources.size() == 1) {
            return;
        }

        if (resources.isEmpty()) {
            throw new InvalidModelException(
                format("No statement for a node whose type is '%s' in '%s'", typeIri, source));
        }
        throw new InvalidModelException(
            format(
                "Found %d statements for nodes whose type is '%s' in '%s', expecting only 1",
                resources.size(), typeIri, source));
    }

    public SemanticAssetMetadata extractMetadata() {
        Resource mainResource = getMainResource();
        return SemanticAssetMetadata.builder()
            .iri(mainResource.getURI())
            .repoUrl(repoUrl)
            .rightsHolder(
                NodeSummaryExtractor.mustExtractNodeSummary(mainResource, rightsHolder, FOAF.name))
            .type(getType())
            .title(extract(mainResource, title))
            .description(extract(mainResource, description))
            .modifiedOn(parseDate(extract(mainResource, modified)))
            .themes(asIriList(extractNodes(mainResource, theme)))
            .accrualPeriodicity(extractNode(mainResource, accrualPeriodicity).getURI())
            .subjects(asIriList(extractMaybeNodes(mainResource, subject)))
            .contactPoint(getContactPoint(mainResource))
            .publishers(maybeNodeSummaries(mainResource, publisher, FOAF.name))
            .creators(maybeNodeSummaries(mainResource, creator, FOAF.name))
            .versionInfo(extractOptional(mainResource, versionInfo))
            .issuedOn(parseDate(extractOptional(mainResource, issued)))
            .languages(asIriList(extractMaybeNodes(mainResource, language)))
            .keywords(extractAll(mainResource, keyword))
            .temporal(extractOptional(mainResource, temporal))
            .conformsTo(maybeNodeSummaries(mainResource, conformsTo, FOAF.name))
            .build();
    }

    private NodeSummary getContactPoint(Resource mainResource) {
        Resource contactPointNode = extractMaybeNode(mainResource, contactPoint);
        if (Objects.nonNull(contactPointNode)) {
            Resource email = extractMaybeNode(contactPointNode, VCARD4.hasEmail);
            if (Objects.nonNull(email)) {
                return NodeSummary.builder()
                    .iri(contactPointNode.getURI())
                    .summary(email.getURI())
                    .build();
            }
        }
        return null;
    }

    private List<String> asIriList(List<Resource> resources) {
        return resources.stream().map(Resource::getURI)
            .collect(Collectors.toList());
    }

    private LocalDate parseDate(String date) {
        if (Objects.isNull(date)) {
            return null;
        }
        return DatatypeConverter.parseDate(date).toInstant().atZone(ZoneId.systemDefault())
            .toLocalDate();
    }

    private SemanticAssetType getType() {
        return SemanticAssetType.getByIri(getMainResourceTypeIri());
    }
}
