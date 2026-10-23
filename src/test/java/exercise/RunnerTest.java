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
    void runsTheSampleJsonAndRendersPatientsAndRejects() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/sample-patients.json"),
                StandardCharsets.UTF_8);

        JsonNode out = Runner.MAPPER.readTree(Runner.run(lines));

        assertThat(out.get("patients")).hasSize(1);
        assertThat(out.at("/patients/0/lastName").asText()).isEqualTo("Doe");
        assertThat(out.at("/patients/0/dateOfBirth").asText()).isEqualTo("1980-05-01");
        assertThat(out.at("/patients/0/phone").asText()).isEqualTo("6045550101");
        assertThat(out.at("/patients/0/email").asText()).isEqualTo("jane.doe@example.com");
        assertThat(out.at("/patients/0/sources")).hasSize(2);
        assertThat(out.get("rejected")).hasSize(1);
        assertThat(out.at("/rejected/0/index").asInt()).isEqualTo(2);
    }

    @Test
    void blankInputMeansNoRecords() throws Exception {
        JsonNode out = Runner.MAPPER.readTree(Runner.run(List.of("", "  ")));

        assertThat(out.get("patients")).isEmpty();
        assertThat(out.get("rejected")).isEmpty();
    }
}
