package com.urbancanopy.species.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.species.dto.TreeSpeciesResponse;
import com.urbancanopy.species.dto.TreeSpeciesSummary;
import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.WaterRequirement;
import com.urbancanopy.species.service.TreeSpeciesService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read access to the species catalogue (FR-SPEC-08 … FR-SPEC-13).
 *
 * <p>Public by design: the catalogue is reference material and browsing it before signing
 * up is exactly the behaviour the requirements describe. Only the mutating endpoints are
 * restricted, and they live in {@link AdminTreeSpeciesController} — a separate class under
 * {@code /admin}, so the privileged surface is visible in the URL and in the package
 * rather than hidden in a per-method annotation.</p>
 *
 * <p>Every filter is optional and arrives as a typed enum: an unrecognised value is
 * rejected by the framework as a 400 before any service code runs.</p>
 */
@RestController
@RequestMapping("/api/v1/tree-species")
public class TreeSpeciesController {

    private final TreeSpeciesService speciesService;

    public TreeSpeciesController(TreeSpeciesService speciesService) {
        this.speciesService = speciesService;
    }

    /**
     * Paginated, searchable, filterable catalogue (FR-SPEC-09, FR-SPEC-10).
     *
     * <p>{@code activeOnly} defaults to {@code true}: the public catalogue must not offer a
     * species that has been withdrawn. The admin screen passes {@code false} explicitly.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TreeSpeciesSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GrowthRate growthRate,
            @RequestParam(required = false) WaterRequirement waterRequirement,
            @RequestParam(required = false) ClimateZone climateZone,
            @RequestParam(required = false) SoilType soilType,
            @RequestParam(defaultValue = "false") boolean nativeOnly,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @PageableDefault(sort = "commonName", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(speciesService.search(
                search, growthRate, waterRequirement, climateZone, soilType,
                nativeOnly, activeOnly, pageable)));
    }

    /** Full species detail, including how many live plans depend on it (FR-SPEC-08). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TreeSpeciesResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(speciesService.findById(id)));
    }

    /** Lightweight list for the plan editor's species picker (FR-SPEC-12). */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<TreeSpeciesSummary>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(speciesService.findSelectable()));
    }

    /**
     * Every value each catalogue filter can take.
     *
     * <p>Served from the enums rather than duplicated in JavaScript, so adding a climate
     * zone updates the filter automatically instead of leaving the interface showing a
     * constant name until a second file is remembered.</p>
     */
    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<Map<String, List<EnumOption>>>> filters() {
        return ResponseEntity.ok(ApiResponse.ok(speciesService.filterOptions()));
    }
}
