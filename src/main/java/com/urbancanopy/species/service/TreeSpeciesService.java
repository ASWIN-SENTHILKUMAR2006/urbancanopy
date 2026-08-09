package com.urbancanopy.species.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.DuplicateResourceException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.plan.repository.PlanItemRepository;
import com.urbancanopy.species.dto.TreeSpeciesRequest;
import com.urbancanopy.species.dto.TreeSpeciesResponse;
import com.urbancanopy.species.dto.TreeSpeciesSummary;
import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.entity.WaterRequirement;
import com.urbancanopy.species.mapper.TreeSpeciesMapper;
import com.urbancanopy.species.repository.TreeSpeciesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curates the scientific reference catalogue (FR-SPEC-01 … FR-SPEC-13).
 *
 * <p>The rule that shapes this service is BR-18: <strong>a species that any plan depends
 * on is deactivated, never deleted.</strong> Deleting it would either break every plan
 * that used it or, worse, silently change their figures. Deactivation removes it from
 * pickers for new plans while leaving existing plans exactly as they were.</p>
 */
@Service
public class TreeSpeciesService {

    private static final Logger log = LoggerFactory.getLogger(TreeSpeciesService.class);

    private static final String RESOURCE = "Tree species";

    private final TreeSpeciesRepository speciesRepository;
    private final PlanItemRepository planItemRepository;
    private final TreeSpeciesMapper mapper;
    private final AuditService auditService;

    public TreeSpeciesService(TreeSpeciesRepository speciesRepository,
                              PlanItemRepository planItemRepository,
                              TreeSpeciesMapper mapper,
                              AuditService auditService) {
        this.speciesRepository = speciesRepository;
        this.planItemRepository = planItemRepository;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------------- queries

    /**
     * Paginated, filtered catalogue search (FR-SPEC-09, FR-SPEC-10).
     *
     * @param activeOnly the public catalogue passes {@code true}; the admin screen passes
     *                   {@code false} so deactivated species remain manageable
     */
    @Transactional(readOnly = true)
    public PageResponse<TreeSpeciesSummary> search(
            String search, GrowthRate growthRate, WaterRequirement waterRequirement,
            ClimateZone climateZone, SoilType soilType, boolean nativeOnly,
            boolean activeOnly, Pageable pageable) {

        Page<TreeSpecies> page = speciesRepository.search(
                StringUtils.hasText(search) ? search.trim() : null,
                growthRate, waterRequirement, climateZone, soilType,
                nativeOnly, activeOnly, pageable);

        return PageResponse.from(page, mapper::toSummary);
    }

    /** Full detail, including how many live plans depend on this species. */
    @Transactional(readOnly = true)
    public TreeSpeciesResponse findById(Long id) {
        TreeSpecies species = requireById(id);
        return mapper.toResponse(species, planItemRepository.countLivePlansUsingSpecies(id));
    }

    /** Lightweight list for the plan editor's picker — active species only (FR-SPEC-12). */
    @Transactional(readOnly = true)
    public List<TreeSpeciesSummary> findSelectable() {
        return speciesRepository.findByActiveTrueOrderByCommonNameAsc().stream()
                .map(mapper::toSummary)
                .toList();
    }

    /**
     * Every value a catalogue filter can take.
     *
     * <p>Served from the enums rather than duplicated in JavaScript, so adding a climate
     * zone updates the filter automatically.</p>
     */
    public Map<String, List<EnumOption>> filterOptions() {
        Map<String, List<EnumOption>> options = new LinkedHashMap<>();
        options.put("growthRate", EnumOption.from(GrowthRate.class, GrowthRate::getLabel));
        options.put("waterRequirement", EnumOption.from(
                WaterRequirement.class, WaterRequirement::getLabel, WaterRequirement::getGuidance));
        options.put("climateZone", EnumOption.from(ClimateZone.class, ClimateZone::getLabel));
        options.put("soilType", EnumOption.from(SoilType.class, SoilType::getLabel));
        return options;
    }

    // ------------------------------------------------------------------ modification

    /** Creates a species (FR-SPEC-01). */
    @Transactional
    public TreeSpeciesResponse create(TreeSpeciesRequest request) {
        assertNamesAvailable(request, null);

        TreeSpecies species = newSpecies();
        mapper.apply(request, species);
        species.setActive(true);

        TreeSpecies saved = speciesRepository.save(species);
        auditService.record(AuditEventType.SPECIES_CREATED, RESOURCE, saved.getId(),
                "Created species '%s' (%s)".formatted(saved.getCommonName(), saved.getScientificName()));

        log.info("Species created: {} (id={})", saved.getCommonName(), saved.getId());
        return mapper.toResponse(saved, 0L);
    }

    /**
     * Updates a species (FR-SPEC-01).
     *
     * <p>Changes take effect on every plan that references it — which is the intended
     * behaviour, since correcting a sequestration rate should correct every estimate
     * derived from it. Plans that must not move are pinned by their result snapshot
     * instead (FR-CALC-14).</p>
     */
    @Transactional
    public TreeSpeciesResponse update(Long id, TreeSpeciesRequest request) {
        TreeSpecies species = requireById(id);
        assertNamesAvailable(request, id);

        mapper.apply(request, species);
        TreeSpecies saved = speciesRepository.save(species);

        long affectedPlans = planItemRepository.countLivePlansUsingSpecies(id);
        auditService.record(AuditEventType.SPECIES_UPDATED, RESOURCE, id,
                "Updated species '%s'; %d live plan(s) affected"
                        .formatted(saved.getCommonName(), affectedPlans));

        log.info("Species updated: {} (id={}), affecting {} plan(s)",
                saved.getCommonName(), id, affectedPlans);
        return mapper.toResponse(saved, affectedPlans);
    }

    /** Activates or deactivates a species (FR-SPEC-11, FR-SPEC-12). */
    @Transactional
    public TreeSpeciesResponse setActive(Long id, boolean active) {
        TreeSpecies species = requireById(id);
        species.setActive(active);
        TreeSpecies saved = speciesRepository.save(species);

        long affectedPlans = planItemRepository.countLivePlansUsingSpecies(id);
        auditService.record(
                active ? AuditEventType.SPECIES_ACTIVATED : AuditEventType.SPECIES_DEACTIVATED,
                RESOURCE, id,
                "%s species '%s'".formatted(active ? "Activated" : "Deactivated", saved.getCommonName()));

        return mapper.toResponse(saved, affectedPlans);
    }

    /**
     * Deletes a species, but only if nothing depends on it (BR-18, FR-SPEC-11).
     *
     * @throws BusinessRuleViolationException when plans reference it — the caller is told
     *                                        exactly how many, and offered deactivation
     */
    @Transactional
    public void delete(Long id) {
        TreeSpecies species = requireById(id);

        if (planItemRepository.existsBySpeciesId(id)) {
            long planCount = planItemRepository.countLivePlansUsingSpecies(id);
            throw new BusinessRuleViolationException("BR-18",
                    ("'%s' is used by %d plantation plan(s) and cannot be deleted — doing so would "
                            + "change or break their saved estimates. Deactivate it instead: it will "
                            + "disappear from new plans while existing plans keep working.")
                            .formatted(species.getCommonName(), planCount));
        }

        speciesRepository.delete(species);
        auditService.record(AuditEventType.SPECIES_DELETED, RESOURCE, id,
                "Deleted unused species '%s'".formatted(species.getCommonName()));
        log.info("Species deleted: {} (id={})", species.getCommonName(), id);
    }

    /** Records an uploaded image filename against a species (FR-SPEC-05). */
    @Transactional
    public TreeSpeciesResponse attachImage(Long id, String storedFilename) {
        TreeSpecies species = requireById(id);
        species.setImagePath(storedFilename);
        TreeSpecies saved = speciesRepository.save(species);
        return mapper.toResponse(saved, planItemRepository.countLivePlansUsingSpecies(id));
    }

    // ---------------------------------------------------------------------- internals

    /** Entity lookup for other services in this package. */
    @Transactional(readOnly = true)
    public TreeSpecies requireById(Long id) {
        return speciesRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    /**
     * Enforces name uniqueness (BR-05).
     *
     * @param excludeId the record being edited, so it does not collide with itself
     */
    private void assertNamesAvailable(TreeSpeciesRequest request, Long excludeId) {
        String commonName = request.commonName().trim();
        String scientificName = request.scientificName().trim();

        boolean commonTaken = excludeId == null
                ? speciesRepository.existsByCommonNameIgnoreCase(commonName)
                : speciesRepository.existsByCommonNameIgnoreCaseAndIdNot(commonName, excludeId);
        if (commonTaken) {
            throw DuplicateResourceException.of("commonName", "Common name", commonName);
        }

        boolean scientificTaken = excludeId == null
                ? speciesRepository.existsByScientificNameIgnoreCase(scientificName)
                : speciesRepository.existsByScientificNameIgnoreCaseAndIdNot(scientificName, excludeId);
        if (scientificTaken) {
            throw DuplicateResourceException.of("scientificName", "Scientific name", scientificName);
        }
    }

    /**
     * Instantiates a {@link TreeSpecies}.
     *
     * <p>The entity's no-arg constructor is {@code protected} so that application code
     * cannot casually create a species with every environmental value left null — JPA
     * needs it, callers do not. This one factory is the sanctioned route, and the mapper
     * populates every field immediately afterwards.</p>
     */
    private TreeSpecies newSpecies() {
        try {
            var constructor = TreeSpecies.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("TreeSpecies could not be instantiated", e);
        }
    }
}
