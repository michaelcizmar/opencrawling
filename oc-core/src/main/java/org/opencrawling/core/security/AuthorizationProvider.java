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
package org.opencrawling.core.security;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface (SPI) for OpenCrawling Fine-Grained Access Control (Authorization).
 * Supports both static metadata payload matching and external ReBAC engines (e.g. OpenFGA).
 */
public interface AuthorizationProvider {

    /**
     * Check whether a given user principal with assigned roles/groups is authorized to access a document.
     *
     * @param documentId Document identifier
     * @param documentMetadata Document metadata payload map (contains static ACLs or references)
     * @param userPrincipal User ID / Principal email
     * @param userRoles Roles / groups assigned to user
     * @return true if authorized, false otherwise
     */
    boolean isAccessible(String documentId, Map<String, Object> documentMetadata, String userPrincipal, List<String> userRoles);

    /**
     * Synchronize document security rules into the authorization store (e.g. OpenFGA Relationship Tuples).
     *
     * @param documentId Document identifier
     * @param securityConfig Document security rules
     * @throws Exception if writing relationship tuples fails
     */
    default void writeRelationshipTuples(String documentId, SecurityConfig securityConfig) throws Exception {
        // Default no-op for static metadata authorization
    }
}
