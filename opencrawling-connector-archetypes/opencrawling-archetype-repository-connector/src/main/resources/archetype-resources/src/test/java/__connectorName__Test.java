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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class ${connectorName}Test {

    private ${connectorName}Configuration configuration;
    private ${connectorName}Fetcher fetcher;
    private ${connectorName} connector;

    @BeforeEach
    public void setUp() {
        configuration = new ${connectorName}Configuration();
        fetcher = new ${connectorName}Fetcher(configuration);
        connector = new ${connectorName}(configuration, fetcher);
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
    public void testScanWhenConnected() throws Exception {
        connector.connect();

        StepVerifier.create(connector.scan("/test/path"))
            .assertNext(doc -> {
                assertNotNull(doc);
                assertEquals("doc-1", doc.id());
            })
            .verifyComplete();
    }

    @Test
    public void testScanWhenNotConnected() {
        StepVerifier.create(connector.scan("/test/path"))
            .expectError(IllegalStateException.class)
            .verify();
    }
}
