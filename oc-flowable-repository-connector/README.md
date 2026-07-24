# OpenCrawling - Flowable Repository Connector

This module provides the repository connector for **Flowable BPMN Process Engine**. It connects to the Flowable REST API, crawls historic process instances, downloads process metadata and BPMN variables, and constructs standard `RepositoryDocument` instances for OpenCrawling's RAG/vectorization ingestion pipeline.

## Feature Overview

1. **Historic Process Ingestion**: Crawls `/history/historic-process-instances`.
2. **Dynamic BPMN Variables Extraction**: Fetches `/history/historic-variable-instances` per process instance and attaches variables to metadata (mapped as `flowable_var_<varName>`) and document JSON body.
3. **Structured Concurrency & Virtual Threads**: Leverages Java 25 Virtual Threads (`Thread.ofVirtual()`) and `StructuredTaskScope` for concurrent processing.

## Configuration Parameters

| Parameter | Spring Property Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **URL** | `spring.opencrawling.connector.flowable.url` | `http://localhost:8080/flowable-rest/service` | Base URL of Flowable REST service |
| **Username** | `spring.opencrawling.connector.flowable.username` | `admin` | Basic auth username |
| **Password** | `spring.opencrawling.connector.flowable.password` | `test` | Basic auth password |
| **Batch Size** | `spring.opencrawling.connector.flowable.batch-size` | `100` | Process instances page size |
| **Process Definition Key** | `spring.opencrawling.connector.flowable.process-definition-key` | `""` | Optional filter by process definition key |
| **Include Variables** | `spring.opencrawling.connector.flowable.include-variables` | `true` | Include historical BPMN variables |
| **Scope** | `spring.opencrawling.connector.flowable.scope` | `all` | Scope of instances (`all`, `completed`, `active`) |

## Metadata Mapping Example

```json
{
  "id": "proc-1234",
  "uri": "flowable://process-instances/proc-1234",
  "metadata": {
    "mimeType": ["application/json"],
    "processDefinitionId": ["invoice-process:2:98765"],
    "processDefinitionKey": ["invoice-process"],
    "businessKey": ["INV-2026-001"],
    "startUserId": ["finance_agent_1"],
    "startTime": ["2026-07-23T08:00:00Z"],
    "endTime": ["2026-07-23T08:15:00Z"],
    "durationInMillis": ["900000"],
    "flowable_var_totalAmount": ["250.50"],
    "flowable_var_customerName": ["ACME Corp"]
  }
}
```
