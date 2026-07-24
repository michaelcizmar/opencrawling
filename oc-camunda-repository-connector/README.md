# OpenCrawling - Camunda Repository Connector

The `oc-camunda-repository-connector` module allows OpenCrawling to crawl historic process instances and historical process variables from a **Camunda Platform 7 REST API** (`/engine-rest/history/process-instance`).

## Features
- Crawls historic process instances and associated historical BPMN variables (`/history/variable-instance`).
- Extracts standard BPMN process metadata (`processDefinitionId`, `processDefinitionKey`, `businessKey`, `startUserId`, `startTime`, `endTime`, `durationInMillis`).
- Dynamically maps process variables into metadata attributes prefixed with `camunda_var_<varName>`.
- Parallelized batch execution using **Java 25 Virtual Threads** and **Structured Task Scope**.

## Configuration Properties

| Property key | Environment Variable | Default | Description |
|---|---|---|---|
| `url` | `CAMUNDA_URL` | `http://localhost:8080/engine-rest` | Base URL of the Camunda REST API |
| `username` | `CAMUNDA_USERNAME` | `demo` | Camunda Basic Auth username |
| `password` | `CAMUNDA_PASSWORD` | `demo` | Camunda Basic Auth password |
| `batch-size` | `CAMUNDA_BATCH_SIZE` | `100` | Process instance page batch size |
| `process-definition-key` | `CAMUNDA_PROCESS_DEFINITION_KEY` | `""` | Optional process definition key filter |
| `include-variables` | `CAMUNDA_INCLUDE_VARIABLES` | `true` | Whether to fetch historic process variables |
| `scope` | `CAMUNDA_SCOPE` | `all` | State filter: `all`, `completed`, or `active` |
