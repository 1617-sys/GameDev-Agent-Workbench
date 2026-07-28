package com.example.gameworkbench.config;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class PlaytestTelemetryMigrationTest {
 @Test void migrationContainsRequiredIdempotencyAndIsolationConstraints() throws Exception {
  String sql=Files.readString(Path.of("src/main/resources/db/migration/V31__add_playtest_telemetry.sql"));
  assertThat(sql).contains("uk_playtest_batch","uk_playtest_event_uuid","uk_playtest_event_sequence","prototype_version_uuid","prototype_playtest_aggregate","balance_suggestion_request");
  assertThat(sql.toLowerCase()).doesNotContain("prompt", "token", "user_agent", "ip_address");
 }
}
