package com.urbancanopy.species.service;

import com.urbancanopy.species.dto.TreeSpeciesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * The "replace a species photograph" use case (FR-SPEC-05).
 *
 * <p>A separate service, small as it is, for a specific reason: it is the only place a
 * {@link MultipartFile} — a web-tier type — meets the catalogue. {@link TreeSpeciesService}
 * therefore stays free of upload concerns and keeps its promise that the service layer
 * knows nothing about HTTP, while the controller keeps its promise that it orchestrates
 * nothing.</p>
 *
 * <p>The order of operations matters. The new file is written <em>before</em> the old one is
 * deleted, so a failed upload leaves the existing image intact rather than leaving the
 * species with no picture at all.</p>
 */
@Service
public class SpeciesImageService {

    private static final Logger log = LoggerFactory.getLogger(SpeciesImageService.class);

    private final TreeSpeciesService speciesService;
    private final SpeciesImageStorageService storageService;

    public SpeciesImageService(TreeSpeciesService speciesService,
                               SpeciesImageStorageService storageService) {
        this.speciesService = speciesService;
        this.storageService = storageService;
    }

    /**
     * Stores the uploaded image against a species, superseding any previous one.
     *
     * @return the species as it now stands, with the resolved public image URL
     */
    public TreeSpeciesResponse replaceImage(Long id, MultipartFile file) {
        String previousFilename = speciesService.requireById(id).getImagePath();

        String storedFilename = storageService.store(id, file);
        TreeSpeciesResponse updated = speciesService.attachImage(id, storedFilename);

        // Only once the record points at the new file is the old one expendable. Deletion
        // is best-effort: an orphaned file is untidy, a missing current image is a defect.
        if (previousFilename != null && !previousFilename.equals(storedFilename)) {
            storageService.deleteQuietly(previousFilename);
            log.debug("Superseded species image {} for species {}", previousFilename, id);
        }

        return updated;
    }
}
