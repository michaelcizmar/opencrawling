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
package org.opencrawling.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opencrawling.vespa.VespaFields;
import org.opencrawling.vespa.config.VespaOutputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Vespa-native counterpart to {@link McpVectorServer}, backed directly by Vespa's document/v1 search
 * API instead of the Spring AI VectorStore abstraction. Only active when this instance runs with Vespa
 * as its output store (mirrors {@link org.opencrawling.vespa.config.VespaClientConfig}'s condition).
 * Defaults to Vespa's "hybrid" rank profile (BM25 + vector fused in a single query) and excludes
 * explicit access denials inside the query itself, on top of the same authoritative per-hit ACL
 * re-check {@link McpVectorServer} already performs for the pgvector-backed tools.
 */
@Component
@ConditionalOnProperty(name = "opencrawling.mcp.server.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'${spring.opencrawling.output.type:pgvector}' == 'vespa'")
public class VespaMcpVectorServer {

    private static final Logger log = LoggerFactory.getLogger(VespaMcpVectorServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VespaOutputProperties properties;
    private final EmbeddingModel embeddingModel;

    public VespaMcpVectorServer(VespaOutputProperties properties,
                                 @Autowired(required = false) @Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        log.info("Initialized Vespa-backed Secure MCP tool surface (endpoint: {}).", properties.endpoint());
    }

    public record VespaSearchHit(String chunkId, String uri, String content, String acl, double relevance) {}

    public record VespaSecureSearchResult(List<VespaSearchHit> hits, int totalCount, boolean degraded, String message) {}

    public record VespaDocumentDetails(String chunkId, String uri, String content, String acl, List<String> allowedRead) {}

    @McpTool(description = "Perform a secure search on vectorized documents stored in Vespa. Defaults to Vespa's 'hybrid' rank profile, fusing BM25 keyword scoring with vector similarity in a single query. Results are filtered on the server side using the caller's identity/groups against each document's security rules before anything is returned to the LLM.")
    public VespaSecureSearchResult vespaSecureVectorSearch(
            @McpToolParam(description = "The natural language query or keywords to search for", required = true) String query,
            @McpToolParam(description = "The user principal identity or email of the caller to enforce ACL check", required = true) String userPrincipal,
            @McpToolParam(description = "Comma-separated list of groups/roles the caller belongs to (e.g. 'finance,engineering')", required = false) String userRoles,
            @McpToolParam(description = "Maximum number of results to return (default 5)", required = false) Integer maxResults,
            @McpToolParam(description = "Vespa rank profile: 'hybrid' (default, BM25+vector fused), 'semantic' (vector only), or 'default' (BM25 only)", required = false) String rankProfile,
            @McpToolParam(description = "Dimensions of the target document type to query (384, 768, 1024)", required = false) Integer dimensions
    ) {
        int limit = (maxResults != null && maxResults > 0) ? maxResults : 5;
        List<String> roles = splitRoles(userRoles);
        String documentType = resolveDocumentType(dimensions);
        String effectiveRankProfile = (rankProfile == null || rankProfile.isBlank()) ? "hybrid" : rankProfile.toLowerCase();

        log.info("Vespa MCP secure search. Query: '{}', Principal: '{}', RankProfile: '{}', DocumentType: '{}'",
                query, userPrincipal, effectiveRankProfile, documentType);

        try {
            RawSearchResult raw = executeSearch(documentType, query, effectiveRankProfile, limit, userPrincipal, roles);
            List<VespaSearchHit> accessible = raw.hits().stream()
                    .filter(hit -> isAccessible(hit, userPrincipal, roles))
                    .limit(limit)
                    .map(hit -> new VespaSearchHit(hit.chunkId(), hit.uri(), hit.content(), hit.acl(), hit.relevance()))
                    .toList();
            return new VespaSecureSearchResult(accessible, raw.totalCount(), raw.degraded(), raw.message());
        } catch (Exception e) {
            log.error("Vespa MCP secure search failed: {}", e.getMessage(), e);
            throw new RuntimeException("Error executing secure Vespa search", e);
        }
    }

    @McpTool(description = "Retrieve the full text content of a specific document chunk stored in Vespa by its URI, enforcing security checks to ensure the caller has read access.")
    public VespaDocumentDetails vespaGetDocumentContent(
            @McpToolParam(description = "The URI of the document to retrieve (e.g. file:///path/to/doc.txt)", required = true) String documentUri,
            @McpToolParam(description = "The user principal identity of the caller", required = true) String userPrincipal,
            @McpToolParam(description = "Comma-separated list of groups/roles the caller belongs to", required = false) String userRoles,
            @McpToolParam(description = "Dimensions of the target document type to query (384, 768, 1024)", required = false) Integer dimensions
    ) {
        List<String> roles = splitRoles(userRoles);
        String documentType = resolveDocumentType(dimensions);

        log.info("Vespa MCP get document content. URI: '{}', Principal: '{}', DocumentType: '{}'", documentUri, userPrincipal, documentType);

        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("yql", "select * from " + documentType + " where (" + buildDenyExclusion(userPrincipal, roles) + ") and "
                    + VespaFields.FIELD_URI + " contains \"" + escapeYql(documentUri) + "\"");
            body.put("hits", 20);
            List<RawHit> hits = parseHits(postSearch(body)).hits();

            return hits.stream()
                    .filter(hit -> isAccessible(hit, userPrincipal, roles))
                    .findFirst()
                    .map(hit -> new VespaDocumentDetails(hit.chunkId(), hit.uri(), hit.content(), hit.acl(), hit.allowedRead()))
                    .orElseThrow(() -> new NoSuchElementException("Document not found or access denied."));
        } catch (NoSuchElementException e) {
            throw e;
        } catch (Exception e) {
            log.error("Vespa MCP get document content failed: {}", e.getMessage(), e);
            throw new RuntimeException("Error retrieving document details from Vespa", e);
        }
    }

    @McpTool(description = "List documents currently indexed in Vespa that are accessible to the caller, showing their URIs and access rules.")
    public List<Map<String, Object>> vespaListAccessibleSources(
            @McpToolParam(description = "The user principal identity of the caller", required = true) String userPrincipal,
            @McpToolParam(description = "Comma-separated list of groups/roles the caller belongs to", required = false) String userRoles,
            @McpToolParam(description = "Dimensions of the target document type to query (384, 768, 1024)", required = false) Integer dimensions
    ) {
        List<String> roles = splitRoles(userRoles);
        String documentType = resolveDocumentType(dimensions);

        log.info("Vespa MCP list accessible sources. Principal: '{}', DocumentType: '{}'", userPrincipal, documentType);

        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("yql", "select * from " + documentType + " where " + buildDenyExclusion(userPrincipal, roles));
            body.put("hits", 200);
            List<RawHit> hits = parseHits(postSearch(body)).hits();

            return hits.stream()
                    .filter(hit -> isAccessible(hit, userPrincipal, roles))
                    .map(hit -> {
                        Map<String, Object> summary = new HashMap<>();
                        summary.put("id", hit.chunkId());
                        summary.put("uri", hit.uri());
                        summary.put("acl", hit.acl());
                        summary.put("allowedRead", hit.allowedRead());
                        return summary;
                    })
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("Vespa MCP list accessible sources failed: {}", e.getMessage(), e);
            throw new RuntimeException("Error listing accessible sources from Vespa", e);
        }
    }

    // --- search execution -------------------------------------------------------------------

    record RawHit(String chunkId, String uri, String content, String acl, List<String> allowedRead, List<String> deniedRead, double relevance) {}

    record RawSearchResult(List<RawHit> hits, int totalCount, boolean degraded, String message) {}

    private RawSearchResult executeSearch(String documentType, String query, String rankProfile, int limit,
                                           String userPrincipal, List<String> roles) throws Exception {
        int resultLimit = Math.max(limit * 3, 30);
        String denyExclusion = buildDenyExclusion(userPrincipal, roles);

        if ("default".equals(rankProfile)) {
            return bm25Search(documentType, query, denyExclusion, resultLimit, false, null);
        }

        if (embeddingModel == null) {
            return bm25Search(documentType, query, denyExclusion, resultLimit, true,
                    "No embedding model is configured; showing BM25-only results instead of " + rankProfile + ".");
        }

        float[] vector;
        try {
            vector = embeddingModel.embed(query);
        } catch (Exception e) {
            return bm25Search(documentType, query, denyExclusion, resultLimit, true,
                    "Failed to compute a query embedding (" + e.getMessage() + "); showing BM25-only results instead.");
        }
        return vectorSearch(documentType, query, rankProfile, denyExclusion, resultLimit, vector);
    }

    private RawSearchResult bm25Search(String documentType, String query, String denyExclusion, int resultLimit,
                                        boolean degraded, String degradedMessage) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("yql", "select * from " + documentType + " where (" + denyExclusion + ") and userQuery()");
        body.put("query", query);
        body.put("ranking", "default");
        body.put("hits", resultLimit);
        RawSearchResult parsed = parseHits(postSearch(body));
        return new RawSearchResult(parsed.hits(), parsed.totalCount(), degraded, degradedMessage);
    }

    private RawSearchResult vectorSearch(String documentType, String query, String rankProfile, String denyExclusion,
                                          int resultLimit, float[] vector) throws Exception {
        String queryInputName = resolveQueryInputName(documentType);
        ObjectNode body = MAPPER.createObjectNode();
        String yql;
        if ("hybrid".equals(rankProfile)) {
            yql = "select * from " + documentType + " where (" + denyExclusion + ") and (({targetHits:" + resultLimit
                    + "}nearestNeighbor(" + VespaFields.FIELD_EMBEDDING + ", " + queryInputName + ")) or userQuery())";
            body.put("query", query);
        } else {
            yql = "select * from " + documentType + " where (" + denyExclusion + ") and ({targetHits:" + resultLimit
                    + "}nearestNeighbor(" + VespaFields.FIELD_EMBEDDING + ", " + queryInputName + "))";
        }
        body.put("yql", yql);
        ArrayNode vectorValues = body.putArray("input.query(" + queryInputName + ")");
        for (float value : vector) {
            vectorValues.add(value);
        }
        body.put("ranking", rankProfile);
        body.put("hits", resultLimit);
        return parseHits(postSearch(body));
    }

    private RawSearchResult parseHits(JsonNode response) {
        JsonNode root = response.path("root");
        int totalCount = root.path("fields").path("totalCount").asInt(0);
        List<RawHit> hits = new ArrayList<>();
        for (JsonNode child : root.path("children")) {
            JsonNode fields = child.path("fields");
            hits.add(new RawHit(
                    fields.path(VespaFields.FIELD_CHUNK_ID).asText(""),
                    fields.path(VespaFields.FIELD_URI).asText(""),
                    fields.path(VespaFields.FIELD_TEXT).asText(""),
                    fields.path(VespaFields.FIELD_ACL).asText(""),
                    toStringList(fields.path(VespaFields.FIELD_SECURITY_ALLOWED_READ)),
                    toStringList(fields.path(VespaFields.FIELD_SECURITY_DENIED_READ)),
                    child.path("relevance").asDouble(0)
            ));
        }
        return new RawSearchResult(hits, totalCount, false, null);
    }

    private JsonNode postSearch(ObjectNode body) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds())).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.endpoint() + "/search/"))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = MAPPER.readTree(response.body());
        JsonNode errors = json.path("root").path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            throw new IllegalStateException("Vespa query failed: " + errors.get(0).path("message").asText(errors.toString()));
        }
        return json;
    }

    // --- ACL enforcement ---------------------------------------------------------------------

    static boolean isAccessible(RawHit hit, String userPrincipal, List<String> userGroups) {
        if (matchesAny(hit.deniedRead(), userPrincipal, userGroups)) {
            return false;
        }
        if (hit.allowedRead() != null && !hit.allowedRead().isEmpty()) {
            return matchesAny(hit.allowedRead(), userPrincipal, userGroups);
        }
        String acl = (hit.acl() == null || hit.acl().isBlank()) ? "public" : hit.acl();
        return matchesAny(List.of(acl), userPrincipal, userGroups);
    }

    private static boolean matchesAny(List<String> identities, String userPrincipal, List<String> userGroups) {
        if (identities == null || identities.isEmpty()) return false;
        for (String identity : identities) {
            if ("public".equalsIgnoreCase(identity)) return true;
            if (userPrincipal != null && userPrincipal.equalsIgnoreCase(identity)) return true;
            if (userGroups != null && userGroups.stream().anyMatch(g -> g.equalsIgnoreCase(identity))) return true;
        }
        return false;
    }

    /**
     * Excludes explicit denies inside the Vespa query itself - true defense in depth, since
     * {@link #isAccessible} still re-checks every hit afterward and remains the sole authority;
     * this only narrows candidates before ranking.
     */
    static String buildDenyExclusion(String userPrincipal, List<String> userGroups) {
        List<String> candidates = new ArrayList<>();
        if (userPrincipal != null && !userPrincipal.isBlank()) {
            candidates.add(userPrincipal.trim());
        }
        if (userGroups != null) {
            candidates.addAll(userGroups);
        }
        if (candidates.isEmpty()) {
            return "true";
        }
        String denyClause = candidates.stream()
                .map(candidate -> VespaFields.FIELD_SECURITY_DENIED_READ + " contains \"" + escapeYql(candidate) + "\"")
                .collect(Collectors.joining(" or "));
        // Vespa's YQL grammar rejects the "not (...)" keyword form here; negation must be "!(...)".
        return "!(" + denyClause + ")";
    }

    private static String escapeYql(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> toStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static List<String> splitRoles(String userRoles) {
        if (userRoles == null || userRoles.trim().isBlank()) {
            return List.of();
        }
        return Arrays.stream(userRoles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    String resolveDocumentType(Integer dimensions) {
        if (dimensions == null) return properties.documentType();
        return switch (dimensions) {
            case 384 -> VespaFields.DOCUMENT_TYPE_384;
            case 768 -> VespaFields.DOCUMENT_TYPE_768;
            case 1024 -> VespaFields.DOCUMENT_TYPE_1024;
            default -> properties.documentType();
        };
    }

    private static String resolveQueryInputName(String documentType) {
        if (VespaFields.DOCUMENT_TYPE_384.equals(documentType)) return VespaFields.QUERY_INPUT_384;
        if (VespaFields.DOCUMENT_TYPE_768.equals(documentType)) return VespaFields.QUERY_INPUT_768;
        if (VespaFields.DOCUMENT_TYPE_1024.equals(documentType)) return VespaFields.QUERY_INPUT_1024;
        return VespaFields.QUERY_INPUT_DEFAULT;
    }
}
