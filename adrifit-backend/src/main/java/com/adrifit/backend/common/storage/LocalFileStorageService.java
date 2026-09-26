package com.adrifit.backend.common.storage;

import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!s3")
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path baseDir;

    public LocalFileStorageService(@Value("${adrifit.storage.local.base-dir:uploads}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory: " + this.baseDir, e);
        }
    }

    @Override
    public void store(String key, InputStream stream, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store file with key={}", key, e);
            throw new BusinessException("Failed to store file");
        }
    }

    @Override
    public InputStream load(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("File not found: " + key);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("Failed to read file with key={}", key, e);
            throw new BusinessException("Failed to read file");
        }
    }

    @Override
    public Resource loadResource(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("File not found: " + key);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete file with key={}", key, e);
        }
    }

    @Override
    public String providerName() {
        return "local";
    }

    private Path resolve(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BusinessException("Invalid storage key (path traversal detected)");
        }
        return resolved;
    }
}
