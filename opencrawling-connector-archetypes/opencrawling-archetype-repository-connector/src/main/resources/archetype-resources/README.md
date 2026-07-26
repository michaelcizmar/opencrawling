# ${artifactId}

An OpenCrawling Repository Connector plugin: `${connectorName}`.

## 📌 Overview
This module was generated using the **OpenCrawling Repository Connector Archetype**. It provides the implementation of `RepositoryConnector` for scanning, extracting, and streaming documents into the OpenCrawling ingestion pipeline.

## 🚀 Testing & Execution

### 1. Run Unit Tests
Run fast unit tests (`*Test.java`):

```bash
mvn clean test
```

### 2. Run Integration Tests
Run integration tests (`*IT.java`) with Maven Failsafe:

```bash
mvn verify
```

### 3. Docker Compose Overlay & E2E Testing
To test this custom connector inside the official OpenCrawling Docker environment:

```bash
# Step 1: Package the connector JAR
mvn clean package

# Step 2: Start OpenCrawling distribution with the custom connector overlay
docker compose -f docker/docker-compose.dist.yml -f docker/docker-compose.override.yml up -d

# Step 3: Test interactively via OpenCrawling Admin UI
# Open http://localhost:3000 in your browser to configure, run, and monitor jobs using your custom connector!

# Step 4: Verify container logs & status
docker compose -f docker/docker-compose.dist.yml -f docker/docker-compose.override.yml logs -f opencrawling-backend

# Step 5: Stop services when finished
docker compose -f docker/docker-compose.dist.yml -f docker/docker-compose.override.yml down
```

## ⚙️ Configuration & Architecture
- Implements `RepositoryConnector` with reactive streaming (`Flux<RepositoryDocument>`).
- Includes Docker Compose distribution (`docker/docker-compose.dist.yml`) and overlay mount configuration (`docker/docker-compose.override.yml`).
- Pre-configured with SPI service registration in `META-INF/services/org.opencrawling.core.connector.RepositoryConnector`.
