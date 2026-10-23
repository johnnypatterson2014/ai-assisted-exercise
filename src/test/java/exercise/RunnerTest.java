package exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class RunnerTest {

    @Test
    void runsTheSampleJsonAndRendersAlertsAndRejects() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/sample-readings.json"),
                StandardCharsets.UTF_8);

        JsonNode out = Runner.MAPPER.readTree(Runner.run(lines));

        assertThat(out.get("alerts")).hasSize(2);
        assertThat(out.at("/alerts/0/severity").asText()).isEqualTo("WARNING");
        assertThat(out.at("/alerts/1/severity").asText()).isEqualTo("CRITICAL");
        assertThat(out.at("/alerts/1/at").asText()).isEqualTo("2026-10-23T10:00:00Z");
        assertThat(out.get("rejected")).hasSize(2);
        assertThat(out.at("/rejected/0/index").asInt()).isEqualTo(4);
        assertThat(out.at("/rejected/1/index").asInt()).isEqualTo(5);
    }

    @Test
    void blankInputMeansNoReadings() throws Exception {
        JsonNode out = Runner.MAPPER.readTree(Runner.run(List.of("", "  ")));

        assertThat(out.get("alerts")).isEmpty();
        assertThat(out.get("rejected")).isEmpty();
    }
}
