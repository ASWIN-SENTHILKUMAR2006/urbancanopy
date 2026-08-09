package com.urbancanopy.species.service;

import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded species images on disk (FR-SPEC-05).
 *
 * <p>Three rules shape this class, and each of them closes a real hole:</p>
 * <ol>
 *   <li><strong>The client's filename is never used.</strong> A stored name is generated
 *       from the species id and a random suffix. An uploaded {@code ../../application.yml}
 *       therefore cannot escape the upload directory, and two planners uploading
 *       {@code tree.jpg} cannot overwrite each other.</li>
 *   <li><strong>The extension is derived from the declared content type</strong>, not from
 *       the submitted name, so the file on disk is always named for what it claims to
 *       be.</li>
 *   <li><strong>The resolved path is verified to sit inside the upload directory</strong>
 *       before a byte is written — belt and braces behind rule 1.</li>
 * </ol>
 *
 * <p>Files live outside the JAR, at a configured location, so adding an image needs no
 * rebuild and the directory can be moved to shared storage without a code change.</p>
 */
@Service
public class SpeciesImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(SpeciesImageStorageService.class);

    /** Extension per permitted content type. Keys match {@code app.upload.allowed-content-types}. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final ApplicationProperties properties;

    public SpeciesImageStorageService(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Validates and stores an uploaded image.
     *
     * @param speciesId the species the image belongs to, used in the stored filename
     * @param file      the uploaded part
     * @return the stored filename, to be recorded against the species
     * @throws BusinessRuleViolationException when the file is empty, too large, or not an
     *                                        image of a permitted type
     */
    public String store(Long speciesId, MultipartFile file) {
        String contentType = requireAcceptable(file);

        String extension = EXTENSIONS.getOrDefault(contentType, "img");
        String storedFilename = "species-%d-%s.%s".formatted(
                speciesId, UUID.randomUUID().toString().substring(0, 8), extension);

        Path directory = uploadDirectory();
        Path target = directory.resolve(storedFilename).normalize();

        // Defence in depth: the generated name cannot escape, but a future change to the
        // naming scheme must not be able to either.
        if (!target.startsWith(directory)) {
            throw new BusinessRuleViolationException("The upload could not be stored safely.");
        }

        try {
            Files.createDirectories(directory);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store species image for species {} at {}", speciesId, target, e);
            throw new IllegalStateException("The image could not be saved. Please try again.", e);
        }

        log.info("Stored species image {} for species {}", storedFilename, speciesId);
        return storedFilename;
    }

    /**
     * Deletes a previously stored image, if it still exists.
     *
     * <p>Best-effort and never throwing: a missing or unreadable old file must not stop a
     * planner from replacing their image.</p>
     */
    public void deleteQuietly(String storedFilename) {
        if (!StringUtils.hasText(storedFilename)) {
            return;
        }
        Path directory = uploadDirectory();
        Path target = directory.resolve(storedFilename).normalize();
        if (!target.startsWith(directory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Could not delete superseded species image {}: {}", storedFilename, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------- internals

    /**
     * Validates the upload and returns its content type, case-folded.
     *
     * <p>Returns the value rather than merely asserting it, so the caller cannot re-read a
     * nullable getter it has already proved to be present.</p>
     */
    private String requireAcceptable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("Please choose an image file to upload.");
        }

        ApplicationProperties.Upload upload = properties.upload();
        String contentType = file.getContentType();
        String permitted = String.join(", ", upload.allowedContentTypes());

        if (!StringUtils.hasText(contentType)) {
            throw new BusinessRuleViolationException(
                    "The file did not declare an image type. Please upload one of: " + permitted + ".");
        }
        if (!upload.isContentTypeAllowed(contentType)) {
            throw new BusinessRuleViolationException(
                    "%s is not a supported image type. Please upload one of: %s."
                            .formatted(contentType, permitted));
        }

        if (file.getSize() > upload.maxFileSizeBytes()) {
            throw new BusinessRuleViolationException(
                    "The image is %.1f MB; the limit is %.1f MB.".formatted(
                            file.getSize() / 1_048_576d,
                            upload.maxFileSizeBytes() / 1_048_576d));
        }

        return contentType.toLowerCase(Locale.ROOT);
    }

    private Path uploadDirectory() {
        return Paths.get(properties.upload().directory()).toAbsolutePath().normalize();
    }
}
