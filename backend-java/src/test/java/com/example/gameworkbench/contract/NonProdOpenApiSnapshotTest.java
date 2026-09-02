package com.example.gameworkbench.contract;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class NonProdOpenApiSnapshotTest extends OpenApiSnapshotSupport {
    @Test
    void springDocContractMatchesCheckedInSnapshot() throws Exception {
        verifySnapshot("non-prod");
    }
}
