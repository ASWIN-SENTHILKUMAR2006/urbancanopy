package com.urbancanopy.species.repository;

import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.entity.WaterRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence access for {@link TreeSpecies}. */
public interface TreeSpeciesRepository extends JpaRepository<TreeSpecies, Long> {

    Optional<TreeSpecies> findByIdAndActiveTrue(Long id);

    List<TreeSpecies> findByActiveTrueOrderByCommonNameAsc();

    List<TreeSpecies> findByIdIn(Collection<Long> ids);

    long countByActiveTrue();

    // ------------------------------------------------------------- uniqueness (BR-05)

    boolean existsByCommonNameIgnoreCase(String commonName);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    boolean existsByCommonNameIgnoreCaseAndIdNot(String commonName, Long id);

    boolean existsByScientificNameIgnoreCaseAndIdNot(String scientificName, Long id);

    // ------------------------------------------------------------------- catalogue

    /**
     * Catalogue search with every filter optional (FR-SPEC-09, FR-SPEC-10).
     *
     * <p>A null parameter disables its own predicate, which keeps one query serving all
     * filter combinations rather than requiring a Specification or a combinatorial
     * explosion of derived method names. Sorting is supplied through {@code Pageable}, so
     * any environmental attribute can be sorted on without a new query.</p>
     *
     * @param activeOnly when true, inactive species are excluded — the public catalogue
     *                   sets this, the admin screen does not (FR-SPEC-12)
     */
    @Query("""
            SELECT s FROM TreeSpecies s
            WHERE (:activeOnly = FALSE OR s.active = TRUE)
              AND (:search IS NULL
                   OR LOWER(s.commonName)     LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.scientificName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(s.localName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:growthRate       IS NULL OR s.growthRate = :growthRate)
              AND (:waterRequirement IS NULL OR s.waterRequirement = :waterRequirement)
              AND (:climateZone      IS NULL OR s.climateZone = :climateZone)
              AND (:soilType         IS NULL OR s.soilType = :soilType)
              AND (:nativeOnly = FALSE OR s.nativeSpecies = TRUE)
            """)
    Page<TreeSpecies> search(@Param("search") String search,
                             @Param("growthRate") GrowthRate growthRate,
                             @Param("waterRequirement") WaterRequirement waterRequirement,
                             @Param("climateZone") ClimateZone climateZone,
                             @Param("soilType") SoilType soilType,
                             @Param("nativeOnly") boolean nativeOnly,
                             @Param("activeOnly") boolean activeOnly,
                             Pageable pageable);
}
