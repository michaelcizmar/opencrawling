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
package org.opencrawling.runtime.api;

import java.io.IOException;
import java.util.List;

import org.opencrawling.runtime.service.VespaInsightsService;
import org.opencrawling.runtime.service.VespaInsightsService.DocumentTypeCount;
import org.opencrawling.runtime.service.VespaInsightsService.VespaDeployResult;
import org.opencrawling.runtime.service.VespaInsightsService.VespaHealthResult;
import org.opencrawling.runtime.service.VespaInsightsService.VespaQueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vespa")
public class VespaInsightsController {

    private final VespaInsightsService insightsService;

    @Autowired
    public VespaInsightsController(VespaInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/health")
    public VespaHealthResult getHealth(@RequestParam String endpoint) {
        return insightsService.checkHealth(endpoint);
    }

    @GetMapping("/document-counts")
    public List<DocumentTypeCount> getDocumentCounts(@RequestParam String endpoint) {
        return insightsService.getDocumentCounts(endpoint);
    }

    @PostMapping("/query")
    public VespaQueryResult runQuery(@RequestBody VespaQueryRequest request) {
        return insightsService.runQuery(request.endpoint(), request.documentType(), request.queryText(), request.rankProfile());
    }

    @PostMapping("/deploy/bundled")
    public VespaDeployResult deployBundledSchema(@RequestBody DeployBundledRequest request) {
        return insightsService.deployBundledSchema(request.configEndpoint());
    }

    @PostMapping(value = "/deploy/custom", consumes = "multipart/form-data")
    public VespaDeployResult deployCustomSchema(@RequestParam("configEndpoint") String configEndpoint,
                                                 @RequestParam("file") MultipartFile file) throws IOException {
        return insightsService.deployCustomSchema(configEndpoint, file.getBytes(), file.getContentType());
    }

    public record VespaQueryRequest(String endpoint, String documentType, String queryText, String rankProfile) {}

    public record DeployBundledRequest(String configEndpoint) {}
}
