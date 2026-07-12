package com.example.gameworkbench.evaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SchemaEvaluatorTest {
 @Test void rejectsMissingStructureBeforeNormalization() { var result = new SchemaEvaluator(new ObjectMapper()).evaluate("{}", "game-config", "1.0"); assertThat(result.status()).isEqualTo("FAILED"); assertThat(result.violations()).contains("VERSION_REQUIRED", "ITEMS_ARRAY_REQUIRED"); }
 @Test void skipsUnknownSchemaInsteadOfPretendingPass() { assertThat(new SchemaEvaluator(new ObjectMapper()).evaluate("{}", "text", "1.0").status()).isEqualTo("SKIPPED"); }
}
