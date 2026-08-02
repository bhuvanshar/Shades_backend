package com.sunglassstore.service;

import com.sunglassstore.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalImageStorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/gif", ".gif");
    private final Path uploadRoot;
    private final String publicBaseUrl;

    public LocalImageStorageService(@Value("${app.upload.dir:uploads/products}") String uploadDir,
                                    @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
    }

    public String store(Long productId, Long variantId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("An image file is required");
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) throw new BadRequestException("Only JPEG, PNG and GIF images are supported");
        try (InputStream validationStream = file.getInputStream()) {
            if (ImageIO.read(validationStream) == null) throw new BadRequestException("The uploaded file is not a valid image");
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded image could not be read");
        }
        String owner = variantId == null ? "product" : "variants/" + variantId;
        Path directory = uploadRoot.resolve(String.valueOf(productId)).resolve(owner).normalize();
        if (!directory.startsWith(uploadRoot)) throw new BadRequestException("Invalid upload path");
        String filename = UUID.randomUUID() + extension;
        Path destination = directory.resolve(filename).normalize();
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BadRequestException("The image could not be stored");
        }
        return publicBaseUrl + "/uploads/products/" + productId + "/" + owner.replace('\\', '/') + "/" + filename;
    }

    public void delete(String imageUrl) {
        String marker = "/uploads/products/";
        int index = imageUrl == null ? -1 : imageUrl.indexOf(marker);
        if (index < 0) return;
        Path target = uploadRoot.resolve(imageUrl.substring(index + marker.length())).normalize();
        if (!target.startsWith(uploadRoot)) return;
        try { Files.deleteIfExists(target); } catch (IOException ignored) { /* DB record is authoritative. */ }
    }

    public Path getUploadRoot() { return uploadRoot; }
}
