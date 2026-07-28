/*
 * Copyright © ${year} the original author or authors (piergiorgio@apache.org)
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
package org.opencrawling.camunda;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.opencrawling.core.document.RepositoryDocument;

import reactor.test.StepVerifier;

class CamundaRepositoryConnectorTest {

    private CamundaRepositoryConnector connector;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockHttpResponse;

    @BeforeEach
    void setUp() {
        connector = new CamundaRepositoryConnector(
                "http://localhost:8080/engine-rest",
                "demo",
                "demo",
                10,
                "",
                true,
                "all"
        );
        mockHttpClient = mock(HttpClient.class);
        mockHttpResponse = mock(HttpResponse.class);
        connector.setHttpClient(mockHttpClient);
    }

    @Test
    void testGetName() {
        assertEquals("CamundaConnector", connector.getName());
    }

    @Test
    void testConnectSuccess() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn("[{\"id\":\"proc-1\"}]");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        connector.connect();
    }

    @Test
    void testConnectFailure() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(401);
        when(mockHttpResponse.body()).thenReturn("Unauthorized");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockHttpResponse);

        assertThrows(IOException.class, () -> connector.connect());
    }

    @Test
    void testScanProcessInstances() throws Exception {
        String processInstancesJson = """
                [
                  {
                    "id": "proc-100",
                    "processDefinitionId": "order-process:1:10",
                    "processDefinitionKey": "order-process",
                    "businessKey": "ORD-99",
                    "startUserId": "user1",
                    "startTime": "2026-07-24T10:00:00.000+0000",
                    "endTime": "2026-07-24T10:05:00.000+0000",
                    "durationInMillis": 300000
                  }
                ]
                """;

        String variablesJson = """
                [
                  {
                    "name": "amount",
                    "value": 1500
                  }
                ]
                """;

        HttpResponse<String> instancesResponse = mock(HttpResponse.class);
        when(instancesResponse.statusCode()).thenReturn(200);
        when(instancesResponse.body()).thenReturn(processInstancesJson, "[]");

        HttpResponse<String> variablesResponse = mock(HttpResponse.class);
        when(variablesResponse.statusCode()).thenReturn(200);
        when(variablesResponse.body()).thenReturn(variablesJson);

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(instancesResponse)
                .thenReturn(variablesResponse)
                .thenReturn(instancesResponse);

        StepVerifier.create(connector.scan("/"))
                .assertNext(doc -> {
                    assertNotNull(doc);
                    assertEquals("proc-100", doc.id());
                    assertEquals("camunda://process-instances/proc-100", doc.uri());
                    assertEquals(List.of("order-process"), doc.metadata().get("processDefinitionKey"));
                    assertEquals(List.of("1500"), doc.metadata().get("camunda_var_amount"));
                })
                .verifyComplete();
    }
}
