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

import org.opencrawling.core.connector.TransformationConnector;
import org.opencrawling.core.document.RepositoryDocument;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ${connectorName} implements TransformationConnector {

    private final ${connectorName}Configuration configuration;
    private final ${connectorName}Transformer transformer;
    private boolean connected = false;

    public ${connectorName}(${connectorName}Configuration configuration, ${connectorName}Transformer transformer) {
        this.configuration = configuration;
        this.transformer = transformer;
    }

    @Override
    public String getName() {
        return "${connectorName}";
    }

    @Override
    public void connect() throws Exception {
        // Initialize transformation resources/models
        this.connected = true;
    }

    @Override
    public void disconnect() throws Exception {
        // Clean up resources
        this.connected = false;
    }

    @Override
    public Flux<RepositoryDocument> transform(RepositoryDocument document) throws Exception {
        if (!connected) {
            return Flux.error(new IllegalStateException("Connector is not connected. Call connect() first."));
        }
        return transformer.transformDocument(document);
    }

    public boolean isConnected() {
        return connected;
    }
}
