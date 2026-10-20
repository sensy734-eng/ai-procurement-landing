package com.zufe.procurement.common.storage;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件存储服务，功能点 M1-2-5 供应商资质附件。
 * 文件存服务端上传目录，数据库只记相对路径，不含主机信息。
 * 路径做规范化校验，防止相对路径穿越写到目录之外。
 */
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /**
     * 存储文件。subDir 为业务子目录（如 supplier-attachments/{供应商id}）。
     * 落盘文件名用 UUID，不使用客户端原始名，避免路径注入与中文名兼容问题。
     */
    public StoredFile store(MultipartFile file, String subDir) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = resolve(subDir + "/" + storedName);
        Files.createDirectories(target.getParent());
        file.transferTo(target);
        StoredFile stored = new StoredFile();
        stored.setRelativePath(root.relativize(target).toString().replace("\\", "/"));
        stored.setSize(file.getSize());
        return stored;
    }

    public boolean exists(String relativePath) {
        try {
            Path path = resolve(relativePath);
            return Files.isRegularFile(path);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Resource load(String relativePath) throws IOException {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("文件不存在：" + relativePath);
        }
        return new UrlResource(path.toUri());
    }

    /** 下载响应头用的文件名编码，兼容中文 */
    public static String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase();
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        return path;
    }

    @Data
    public static class StoredFile {
        private String relativePath;
        private long size;
    }
}
