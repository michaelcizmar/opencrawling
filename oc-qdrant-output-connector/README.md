# OpenCrawling - Qdrant Output Connector

This module provides the `OutputConnector` implementation for **[Qdrant](https://qdrant.tech/)** (self-hosted or Qdrant Cloud), Rust-based vector search engine. Crawled document chunks and embeddings are indexed into a Qdrant collection with the same ACL pre-filtering model used by the pgvector and Milvus connectors.

## Feature Overview

1. **Collection auto-provisioning**: on startup, `QdrantCollectionInitializer` creates the configured collection (if missing) with the requested vector size/distance metric, plus keyword payload indexes on `security_allowed_read` / `security_denied_read` for ACL pre-filtering.
2. **ACL-aware indexing**: each point's payload carries `security_allowed_read`, `security_denied_read`, and `security_inheritance`, derived from the document's `SecurityConfig`/`PermissionRule` list — the same OIS security model used across every OpenCrawling output connector.
3. **Two write paths, one mapper**: `QdrantOutputConnector.send()` (direct `OutputConnector` SPI path) and `QdrantStoreWriterConsumer` (the Kafka `opencrawling-embedded` writer used by the decoupled pipeline) both delegate to `QdrantPointMapper`, so the ACL/payload-mapping logic exists exactly once.
4. **Deterministic point IDs**: since Qdrant point IDs must be a UUID or `u64` (not an arbitrary string), each chunk's Qdrant ID is `UUID.nameUUIDFromBytes(chunkId)` — deterministic, so re-ingesting a document upserts in place instead of duplicating points. The original chunk id is preserved in the `chunk_id` payload field.
5. **Optional quantization**: Scalar (`SQ8`) or Binary quantization can be enabled per collection at creation time to reduce memory footprint on large collections.

## Configuration Parameters

All properties are bound via `QdrantOutputProperties` under the `spring.opencrawling.output.qdrant` prefix.

| Parameter | Spring Property Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Host** | `spring.opencrawling.output.qdrant.host` | `localhost` | Qdrant server hostname |
| **Port** | `spring.opencrawling.output.qdrant.port` | `6334` | gRPC port (REST API defaults to `6333`) |
| **API Key** | `spring.opencrawling.output.qdrant.api-key` | `""` | Optional API key for Qdrant Cloud / secured instances |
| **Collection Name** | `spring.opencrawling.output.qdrant.collection-name` | `enterprise_kb` | Target collection, auto-created if it doesn't exist |
| **Dimensions** | `spring.opencrawling.output.qdrant.dimensions` | `1024` | Vector size (must match the configured embedding model) |
| **Distance** | `spring.opencrawling.output.qdrant.distance` | `COSINE` | `COSINE`, `DOT`, or `EUCLID` |
| **Quantization** | `spring.opencrawling.output.qdrant.quantization` | `NONE` | `NONE`, `SCALAR_INT8`, or `BINARY` |
| **Use TLS** | `spring.opencrawling.output.qdrant.use-tls` | `false` | Enable TLS on the gRPC channel |
| **Batch Size** | `spring.opencrawling.output.qdrant.batch-size` | `500` | Max points per `upsert` call when indexing a single document's chunks |

To select this connector, set `spring.opencrawling.output.type=qdrant`.

## Payload Mapping

Every indexed point carries these payload fields (see `QdrantFields`), plus any dynamic document metadata:

```json
{
  "chunk_id": "doc-123_a1b2c3",
  "text": "...chunk text...",
  "uri": "file:///data/doc-123.pdf",
  "acl": "acl-123",
  "lastModified": "2026-07-23T08:00:00Z",
  "security_inheritance": true,
  "security_allowed_read": ["user1", "hr-group"],
  "security_denied_read": ["external-user"]
}
```

## Testing & Execution

### 1. Run Unit Tests
```bash
mvn test -pl oc-qdrant-output-connector
```

### 2. Run Integration Tests
Requires a local Docker daemon (uses `org.testcontainers.qdrant.QdrantContainer`):
```bash
mvn verify -pl oc-qdrant-output-connector
```

### 3. Standalone Qdrant via Docker Compose
```bash
docker compose -f oc-qdrant-output-connector/docker/docker-compose.yml up -d
```

### 4. Full Decoupled Pipeline with Qdrant
```bash
docker compose -f oc-qdrant-output-connector/docker/docker-compose-decoupled-with-qdrant.yml up -d --build
```
See the root [README.md](../README.md) ("Decoupled Qdrant-Based Deployment") for details.
