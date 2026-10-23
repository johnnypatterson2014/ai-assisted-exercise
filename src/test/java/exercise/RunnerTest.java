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
    void runsTheSampleJsonAndRendersConflictsAndRejects() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/sample-appointments.json"),
                StandardCharsets.UTF_8);

        JsonNode out = Runner.MAPPER.readTree(Runner.run(lines));

        assertThat(out.get("conflicts")).hasSize(2);
        assertThat(out.at("/conflicts/0/kind").asText()).isEqualTo("PROVIDER");
        assertThat(out.at("/conflicts/0/resourceId").asText()).isEqualTo("dr-1");
        assertThat(out.at("/conflicts/1/kind").asText()).isEqualTo("PATIENT");
        assertThat(out.get("rejected")).hasSize(1);
        assertThat(out.at("/rejected/0/index").asInt()).isEqualTo(3);
        assertThat(out.at("/rejected/0/id").asText()).isEqualTo("a4");
    }

    @Test
    void blankInputMeansNoAppointments() throws Exception {
        JsonNode out = Runner.MAPPER.readTree(Runner.run(List.of("", "  ")));

        assertThat(out.get("conflicts")).isEmpty();
        assertThat(out.get("rejected")).isEmpty();
    }
}
