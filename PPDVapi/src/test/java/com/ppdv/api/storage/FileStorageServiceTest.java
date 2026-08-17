package com.ppdv.api.storage;

import com.ppdv.api.config.FileStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidatedImageInsideConfiguredDirectory() throws Exception {
        FileStorageService service = service();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "ignored.html", "image/png", png);

        String url = service.store(file, "../../PPDV-01", 7L);

        assertTrue(url.matches("/uploads/\\d{4}-\\d{2}-\\d{2}/[a-f0-9]{8}_[a-zA-Z0-9_-]+_7\\.png"));
        Path stored = tempDir.resolve(url.substring("/uploads/".length()));
        assertTrue(stored.normalize().startsWith(tempDir));
        assertEquals(png.length, Files.size(stored));
    }

    @Test
    void rejectsSpoofedImageContent() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file", "attack.png", "image/png", "<script>alert(1)</script>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.store(file, "PPDV-01", 7L));
    }

    private FileStorageService service() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBaseDir(tempDir.toString());
        return new FileStorageService(properties);
    }
}
