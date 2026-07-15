package com.example.gameworkbench.config;
import static org.assertj.core.api.Assertions.assertThat;import java.nio.file.*;import org.junit.jupiter.api.Test;
class PrototypeExportMigrationTest {@Test void migrationFreezesInputsAndFileUnderDatabaseIdempotency()throws Exception{String sql=Files.readString(Path.of("src/main/resources/db/migration/V32__add_prototype_export_job.sql"));assertThat(sql).contains("frozen_input_json","package_bytes","uk_prototype_export_idempotency","request_fingerprint","attempt_count");}}
