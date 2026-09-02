package com.example.gameworkbench.contract;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdOpenApiSnapshotTest extends OpenApiSnapshotSupport {
    @Test
    void springDocContractMatchesCheckedInSnapshot() throws Exception {
        verifySnapshot("prod");
    }
}
