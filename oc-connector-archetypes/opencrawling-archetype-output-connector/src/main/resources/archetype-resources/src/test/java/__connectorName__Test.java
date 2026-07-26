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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencrawling.core.document.RepositoryDocument;
import reactor.test.StepVerifier;

public class ${connectorName}Test {

    private ${connectorName}Configuration configuration;
    private ${connectorName}Exporter exporter;
    private ${connectorName} connector;

    @BeforeEach
    public void setUp() {
        configuration = new ${connectorName}Configuration();
        exporter = new ${connectorName}Exporter(configuration);
        connector = new ${connectorName}(configuration, exporter);
    }

    @Test
    public void testConnectorLifecycle() throws Exception {
        assertEquals("${connectorName}", connector.getName());
        assertFalse(connector.isConnected());

        connector.connect();
        assertTrue(connector.isConnected());

        connector.disconnect();
        assertFalse(connector.isConnected());
    }

    @Test
    public void testSendWhenConnected() throws Exception {
        connector.connect();

        RepositoryDocument doc = new RepositoryDocument(
            "doc-1",
            "http://example.com/doc-1",
            new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)),
            Map.of(),
            "public",
            Instant.now()
        );

        StepVerifier.create(connector.send(doc))
            .verifyComplete();
    }

    @Test
    public void testSendWhenNotConnected() {
        RepositoryDocument doc = new RepositoryDocument(
            "doc-1",
            "http://example.com/doc-1",
            new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)),
            Map.of(),
            "public",
            Instant.now()
        );

        StepVerifier.create(connector.send(doc))
            .expectError(IllegalStateException.class)
            .verify();
    }
}
