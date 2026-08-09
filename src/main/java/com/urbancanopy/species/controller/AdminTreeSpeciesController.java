package com.urbancanopy.species.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.species.dto.SpeciesStatusRequest;
import com.urbancanopy.species.dto.TreeSpeciesRequest;
import com.urbancanopy.species.dto.TreeSpeciesResponse;
import com.urbancanopy.species.dto.TreeSpeciesSummary;
import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.WaterRequirement;
import com.urbancanopy.species.service.SpeciesImageService;
import com.urbancanopy.species.service.TreeSpeciesService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Curation of the species catalogue — administrators only (FR-SPEC-01 … FR-SPEC-07).
 *
 * <p>Separated from the public {@link TreeSpeciesController} rather than mixed into it. The
 * privileged surface is then identifiable from the URL alone, and the authorisation check
 * appears once per method against a single seam instead of being a property of a shared
 * class that someone later adds an unguarded method to.</p>
 *
 * <p>Until Phase 12, {@code assertAdmin()} resolves through
 * {@link CurrentUserProvider} and is satisfied by the development admin-override header, so
 * these endpoints are exercisable before any login page exists. In Phase 12 the same call
 * consults the authenticated principal — <strong>this class does not change</strong>.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/tree-species")
public class AdminTreeSpeciesController {

    private final TreeSpeciesService speciesService;
    private final SpeciesImageService imageService;
    private final CurrentUserProvider currentUserProvider;

    public AdminTreeSpeciesController(TreeSpeciesService speciesService,
                                      SpeciesImageService imageService,
                                      CurrentUserProvider currentUserProvider) {
        this.speciesService = speciesService;
        this.imageService = imageService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * The catalogue as an administrator sees it — deactivated species included.
     *
     * <p>A curation screen that hid withdrawn species would make them unreachable, and so
     * unrecoverable, which is the opposite of what deactivation is for (FR-SPEC-11).</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TreeSpeciesSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GrowthRate growthRate,
            @RequestParam(required = false) WaterRequirement waterRequirement,
            @RequestParam(required = false) ClimateZone climateZone,
            @RequestParam(required = false) SoilType soilType,
            @RequestParam(defaultValue = "false") boolean nativeOnly,
            @PageableDefault(sort = "commonName", direction = Sort.Direction.ASC) Pageable pageable) {

        currentUserProvider.assertAdmin();
        return ResponseEntity.ok(ApiResponse.ok(speciesService.search(
                search, growthRate, waterRequirement, climateZone, soilType,
                nativeOnly, false, pageable)));
    }

    /** Creates a species (FR-SPEC-01). */
    @PostMapping
    public ResponseEntity<ApiResponse<TreeSpeciesResponse>> create(
            @Valid @RequestBody TreeSpeciesRequest request) {

        currentUserProvider.assertAdmin();
        TreeSpeciesResponse created = speciesService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                created, "'%s' has been added to the catalogue.".formatted(created.commonName())));
    }

    /** Replaces a species' reference values (FR-SPEC-01). */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TreeSpeciesResponse>> update(
            @PathVariable Long id, @Valid @RequestBody TreeSpeciesRequest request) {

        currentUserProvider.assertAdmin();
        TreeSpeciesResponse updated = speciesService.update(id, request);

        // The plan count is part of the message because changing a reference value changes
        // every estimate derived from it — the administrator is told the blast radius.
        String message = updated.usedByPlanCount() == null || updated.usedByPlanCount() == 0
                ? "'%s' has been updated.".formatted(updated.commonName())
                : "'%s' has been updated; %d plan(s) using it will now report revised figures."
                        .formatted(updated.commonName(), updated.usedByPlanCount());

        return ResponseEntity.ok(ApiResponse.ok(updated, message));
    }

    /** Activates or deactivates a species (FR-SPEC-11, FR-SPEC-12). */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TreeSpeciesResponse>> setStatus(
            @PathVariable Long id, @Valid @RequestBody SpeciesStatusRequest request) {

        currentUserProvider.assertAdmin();
        TreeSpeciesResponse updated = speciesService.setActive(id, request.isActive());

        String message = request.isActive()
                ? "'%s' is active and will appear in the catalogue.".formatted(updated.commonName())
                : "'%s' is deactivated. Existing plans keep working; new plans will not offer it."
                        .formatted(updated.commonName());

        return ResponseEntity.ok(ApiResponse.ok(updated, message));
    }

    /**
     * Deletes a species, but only when no plan depends on it (BR-18).
     *
     * <p>A species that is in use produces 422 with an explanation and the deactivation
     * alternative — the rule lives in the service, not here.</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        currentUserProvider.assertAdmin();
        speciesService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("The species has been deleted."));
    }

    /** Uploads or replaces a species photograph (FR-SPEC-05). */
    @PostMapping("/{id}/image")
    public ResponseEntity<ApiResponse<TreeSpeciesResponse>> uploadImage(
            @PathVariable Long id, @RequestPart("file") MultipartFile file) {

        currentUserProvider.assertAdmin();
        TreeSpeciesResponse updated = imageService.replaceImage(id, file);
        return ResponseEntity.ok(ApiResponse.ok(updated, "The image has been uploaded."));
    }
}
