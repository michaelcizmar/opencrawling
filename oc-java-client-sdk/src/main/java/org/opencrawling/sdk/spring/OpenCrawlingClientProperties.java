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
package org.opencrawling.sdk.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Spring Boot Configuration Properties for OpenCrawling Java Client SDK.
 */
@ConfigurationProperties(prefix = "opencrawling.client")
public class OpenCrawlingClientProperties {

    /**
     * OpenCrawling REST API base URL endpoint (e.g. http://localhost:8080).
     */
    private String baseUrl = "http://localhost:8080";

    /**
     * Optional API Key header (X-API-Key).
     */
    private String apiKey;

    /**
     * Optional OAuth2 / JWT Bearer token header.
     */
    private String bearerToken;

    /**
     * HTTP connection establishment timeout duration.
     */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * HTTP request socket read timeout duration.
     */
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
