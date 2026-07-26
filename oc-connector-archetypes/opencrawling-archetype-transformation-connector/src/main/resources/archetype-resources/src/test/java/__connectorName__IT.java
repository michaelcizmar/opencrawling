/*
 * Copyright © 2026 the original author or authors (piergiorgio@apache.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ${package};

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencrawling.core.document.RepositoryDocument;
import reactor.test.StepVerifier;

/**
 * Integration Test for {@link ${connectorName}}.
 * Runs during the Maven failsafe integration-test phase (mvn verify)
 * against the OpenCrawling Docker composition.
 */
public class ${connectorName}IT {

    private ${connectorName}Configuration configuration;
    private ${connectorName}Transformer transformer;
    private ${connectorName} connector;

    @BeforeEach
    public void setUp() {
        configuration = new ${connectorName}Configuration();
        transformer = new ${connectorName}Transformer(configuration);
        connector = new ${connectorName}(configuration, transformer);
    }

    @Test
    public void testIntegrationTransformationFlow() throws Exception {
        connector.connect();
        assertTrue(connector.isConnected());

        RepositoryDocument doc = new RepositoryDocument(
            "doc-it-1",
            "http://example.com/doc-it-1",
            new ByteArrayInputStream("Integration content".getBytes(StandardCharsets.UTF_8)),
            new HashMap<>(),
            "public",
            Instant.now()
        );

        StepVerifier.create(connector.transform(doc))
            .assertNext(transformed -> {
                assertNotNull(transformed);
                assertTrue(transformed.metadata().containsKey("transformedBy"));
            })
            .verifyComplete();

        connector.disconnect();
        assertFalse(connector.isConnected());
    }
}
