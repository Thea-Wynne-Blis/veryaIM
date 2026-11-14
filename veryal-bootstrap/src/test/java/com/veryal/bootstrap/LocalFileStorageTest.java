package com.veryal.bootstrap;

import com.veryal.common.exception.BusinessException;
import com.veryal.infrastructure.storage.LocalFileStorage;
import com.veryal.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void putGetDeleteAndRejectPathTraversal() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setRootPath(root.toString());
        LocalFileStorage storage = new LocalFileStorage(properties);

        byte[] content = "hello local storage".getBytes(StandardCharsets.UTF_8);
        storage.put("tenant/1/2026/01/01/a.txt", content, "text/plain");

        assertThat(storage.type().name()).isEqualTo("LOCAL");
        assertThat(storage.get("tenant/1/2026/01/01/a.txt")).isEqualTo(content);
        assertThat(storage.presignedUrl("tenant/1/2026/01/01/a.txt", java.time.Duration.ofMinutes(1)))
                .isNull();

        storage.delete("tenant/1/2026/01/01/a.txt");
        assertThatThrownBy(() -> storage.get("tenant/1/2026/01/01/a.txt"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.put("../escape.txt", content, "text/plain"))
                .isInstanceOf(BusinessException.class);
    }
}
