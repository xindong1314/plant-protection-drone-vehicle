package com.ppdv.api.storage;

import com.ppdv.api.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 巡检图片文件存储：按日期分目录，随机文件名防冲突。
 * 存储目录：{baseDir}/{yyyy-MM-dd}/{uuid}_{原始文件名}
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path baseDir;

    public FileStorageService(FileStorageProperties props) {
        this.baseDir = Paths.get(props.getBaseDir()).toAbsolutePath().normalize();
        log.info("图片存储目录: {}", this.baseDir);
    }

    /**
     * 保存上传文件，返回可访问的相对路径（如 /uploads/2026-07-27/a1b2c3d4_image.jpg）。
     */
    public String store(MultipartFile file, String deviceId, Long areaId) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String newName = UUID.randomUUID().toString().substring(0, 8)
                + "_" + (deviceId != null ? deviceId : "unknown")
                + "_" + (areaId != null ? areaId : "")
                + ext;

        Path targetDir = baseDir.resolve(dateDir);
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(newName);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
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
}
