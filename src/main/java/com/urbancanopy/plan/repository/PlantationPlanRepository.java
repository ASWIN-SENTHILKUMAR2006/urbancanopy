package com.urbancanopy.plan.repository;

import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.entity.PlantationPurpose;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence access for {@link PlantationPlan}. */
public interface PlantationPlanRepository extends JpaRepository<PlantationPlan, Long> {

    /**
     * Loads a plan with its items and their species in a single query.
     *
     * <p>Calculating a plan touches every item and every item's species. Without this
     * fetch join, evaluating a 20-species plan issues 41 queries instead of 1 (NFR-02).</p>
     */
    @Query("""
            SELECT DISTINCT p FROM PlantationPlan p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.species
            JOIN FETCH p.owner
            WHERE p.id = :id AND p.deleted = FALSE
            """)
    Optional<PlantationPlan> findDetailedById(@Param("id") Long id);

    /** Same as {@link #findDetailedById} for several plans — used by comparison (UC-08). */
    @Query("""
            SELECT DISTINCT p FROM PlantationPlan p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.species
            JOIN FETCH p.owner
            WHERE p.id IN :ids AND p.deleted = FALSE
            """)
    List<PlantationPlan> findDetailedByIds(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = "owner")
    Optional<PlantationPlan> findByIdAndDeletedFalse(Long id);

    /** Listing query — deliberately does not fetch items; the list view does not show them. */
    @Query("""
            SELECT p FROM PlantationPlan p
            WHERE p.owner.id = :ownerId
              AND p.deleted = FALSE
              AND (:search  IS NULL
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(p.siteName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status  IS NULL OR p.status = :status)
              AND (:purpose IS NULL OR p.purpose = :purpose)
            """)
    Page<PlantationPlan> searchForOwner(@Param("ownerId") Long ownerId,
                                        @Param("search") String search,
                                        @Param("status") PlanStatus status,
                                        @Param("purpose") PlantationPurpose purpose,
                                        Pageable pageable);

    /** Administrator view across all owners (FR-PLAN-11). */
    @Query("""
            SELECT p FROM PlantationPlan p
            JOIN FETCH p.owner
            WHERE p.deleted = FALSE
              AND (:search  IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status  IS NULL OR p.status = :status)
            """)
    Page<PlantationPlan> searchAll(@Param("search") String search,
                                   @Param("status") PlanStatus status,
                                   Pageable pageable);

    List<PlantationPlan> findByOwnerIdAndDeletedFalse(Long ownerId);

    /**
     * Every live plan of one owner, with items and species fetched (FR-DASH-01 … FR-DASH-05).
     *
     * <p>The dashboard evaluates each plan through the calculation engine, which touches every
     * item and every item's species. Without this fetch join a planner with 10 plans of 5
     * species each would issue 61 queries to render one page.</p>
     */
    @Query("""
            SELECT DISTINCT p FROM PlantationPlan p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.species
            WHERE p.owner.id = :ownerId AND p.deleted = FALSE
            """)
    List<PlantationPlan> findDetailedForOwner(@Param("ownerId") Long ownerId);

    /** Recent activity for the dashboard (FR-DASH-06). */
    @Query("""
            SELECT p FROM PlantationPlan p
            WHERE p.owner.id = :ownerId AND p.deleted = FALSE
            ORDER BY p.updatedAt DESC
            """)
    List<PlantationPlan> findRecentForOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    long countByOwnerIdAndDeletedFalse(Long ownerId);

    /**
     * Live plan counts for several owners at once, for the administrator's user list (FR-ADM-01).
     *
     * <p>One grouped query instead of a per-row count — the same N+1 avoidance the plan listing
     * applies to its own aggregates.</p>
     *
     * @return rows of {@code [ownerId, planCount]}; owners with no plans are absent
     */
    @Query("""
            SELECT p.owner.id, COUNT(p) FROM PlantationPlan p
            WHERE p.owner.id IN :ownerIds AND p.deleted = FALSE
            GROUP BY p.owner.id
            """)
    List<Object[]> countByOwnerIds(@Param("ownerIds") Collection<Long> ownerIds);

    long countByOwnerIdAndStatusAndDeletedFalse(Long ownerId, PlanStatus status);

    long countByDeletedFalse();

    boolean existsByOwnerIdAndTitleIgnoreCaseAndDeletedFalse(Long ownerId, String title);

    boolean existsByOwnerIdAndTitleIgnoreCaseAndIdNotAndDeletedFalse(
            Long ownerId, String title, Long id);

    /** Plans grouped by purpose, for the dashboard distribution chart (FR-DASH-05). */
    @Query("""
            SELECT p.purpose, COUNT(p) FROM PlantationPlan p
            WHERE p.owner.id = :ownerId AND p.deleted = FALSE
            GROUP BY p.purpose
            """)
    List<Object[]> countByPurposeForOwner(@Param("ownerId") Long ownerId);
}
