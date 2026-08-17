package com.ppdv.api.storage;

import com.ppdv.api.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 巡检图片文件存储：按日期分目录，随机文件名防冲突。
 * 存储目录：{baseDir}/{yyyy-MM-dd}/{uuid}_{原始文件名}
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    private final Path baseDir;

    public FileStorageService(FileStorageProperties props) {
        this.baseDir = Paths.get(props.getBaseDir()).toAbsolutePath().normalize();
        log.info("图片存储目录: {}", this.baseDir);
    }

    /**
     * 保存上传文件，返回可访问的相对路径（如 /uploads/2026-07-27/a1b2c3d4_image.jpg）。
     */
    public String store(MultipartFile file, String deviceId, Long areaId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String extension = validatedImageExtension(file);
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String safeDeviceId = deviceId == null || deviceId.isBlank()
                ? "unknown"
                : deviceId.replaceAll("[^a-zA-Z0-9_-]", "_");
        String newName = UUID.randomUUID().toString().substring(0, 8)
                + "_" + safeDeviceId
                + "_" + (areaId != null ? areaId : "")
                + extension;

        Path targetDir = baseDir.resolve(dateDir).normalize();
        Path targetFile = targetDir.resolve(newName).normalize();
        if (!targetFile.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        try {
            Files.createDirectories(targetDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, targetFile);
            }
            String relativePath = "/uploads/" + dateDir + "/" + newName;
            log.info("图片已存储: {} ({} bytes)", relativePath, file.getSize());
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("图片存储失败: " + e.getMessage(), e);
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    private String validatedImageExtension(MultipartFile file) {
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_IMAGE_TYPES.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("仅支持 JPEG、PNG、GIF、WebP 图片");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!matchesSignature(contentType, header)) {
                throw new IllegalArgumentException("文件内容与图片类型不匹配");
            }
            return extension;
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取上传文件", e);
        }
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> header.length >= 12
                    && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }
}
