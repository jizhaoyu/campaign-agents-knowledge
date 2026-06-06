package com.enterprise.agentplatform.knowledge.service;

import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path baseDir;

    public LocalFileStorageService(@Value("${app.storage.base-dir:storage}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        try {
            Files.createDirectories(baseDir);
            String fileName = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
            Path destination = baseDir.resolve(fileName).normalize();
            if (!isInStorageBaseDir(destination)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件路径非法");
            }
            file.transferTo(destination);
            return destination.toString();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    public Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件路径非法");
        }
        Path keyPath = Paths.get(objectKey);
        Path target = keyPath.isAbsolute()
                ? keyPath.toAbsolutePath().normalize()
                : baseDir.resolve(keyPath).normalize();
        if (!isInStorageBaseDir(target)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件路径非法");
        }
        return target;
    }

    public void deleteIfExists(String objectKey) {
        Path target;
        try {
            target = resolve(objectKey);
        } catch (BusinessException ex) {
            log.warn("Skip deleting file outside storage base dir: objectKey={}, baseDir={}", objectKey, baseDir);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Failed to delete stored document file: target={}", target, ex);
        }
    }

    private String sanitize(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "upload.bin" : fileName;
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean isInStorageBaseDir(Path target) {
        return target.startsWith(baseDir);
    }
}
