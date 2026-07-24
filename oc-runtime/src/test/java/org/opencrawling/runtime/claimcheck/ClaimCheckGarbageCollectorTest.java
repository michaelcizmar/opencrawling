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
package org.opencrawling.runtime.claimcheck;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.opencrawling.core.claimcheck.ClaimCheckProperties;
import org.opencrawling.core.claimcheck.LocalFileClaimCheckStore;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimCheckGarbageCollectorTest {

    @Test
    void testGarbageCollectorPurgesExpiredFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("gc-test-claims");
        LocalFileClaimCheckStore store = new LocalFileClaimCheckStore(tempDir);

        ClaimCheckProperties props = new ClaimCheckProperties();
        props.getLifecycle().setEnableBackgroundGc(true);
        props.getLifecycle().setTtlHours(1);

        ClaimCheckGarbageCollector gc = new ClaimCheckGarbageCollector(store, props);

        // Put fresh content
        String text = "Fresh claim check data";
        URI freshUri = store.put("fresh-doc.txt", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), text.length());

        // Put old content and manually backdate its last modified time to 2 hours ago
        String oldText = "Expired claim check data";
        URI oldUri = store.put("expired-doc.txt", new ByteArrayInputStream(oldText.getBytes(StandardCharsets.UTF_8)), oldText.length());
        Path oldPath = Path.of(oldUri);
        Files.setLastModifiedTime(oldPath, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(Duration.ofHours(2))));

        assertThat(Files.exists(Path.of(freshUri))).isTrue();
        assertThat(Files.exists(oldPath)).isTrue();

        // Execute GC sweep
        gc.runGarbageCollectionSweep();

        // Fresh file should remain, expired file should be purged
        assertThat(Files.exists(Path.of(freshUri))).isTrue();
        assertThat(Files.exists(oldPath)).isFalse();

        // Clean up
        store.delete(freshUri);
        Files.deleteIfExists(tempDir);
    }
}
