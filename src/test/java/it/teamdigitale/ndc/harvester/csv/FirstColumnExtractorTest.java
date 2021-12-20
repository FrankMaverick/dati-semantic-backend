package it.teamdigitale.ndc.harvester.csv;

import it.teamdigitale.ndc.harvester.csv.FirstColumnExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirstColumnExtractorTest {

    private FirstColumnExtractor extractor = new FirstColumnExtractor();

    @Test
    void shouldExtractFirstColumn() {
        assertThat(extractor.extract(List.of("first", "second", "third"))).isEqualTo("first");
    }
}