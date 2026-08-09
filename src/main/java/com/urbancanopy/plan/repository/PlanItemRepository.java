package com.urbancanopy.plan.repository;

import com.urbancanopy.plan.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Persistence access for {@link PlanItem}.
 *
 * <p>Items are normally reached through their {@link com.urbancanopy.plan.entity.PlantationPlan}
 * aggregate. This repository exists for the two questions that cross aggregates: whether a
 * species is in use, and how species usage aggregates across a user's plans.</p>
 */
public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

    /**
     * Whether any plan references this species. Answers the question that decides between
     * deactivation and deletion (BR-18, FR-SPEC-11).
     */
    boolean existsBySpeciesId(Long speciesId);

    /** How many live plans reference this species — shown to the admin before deactivating. */
    @Query("""
            SELECT COUNT(DISTINCT i.plan.id) FROM PlanItem i
            WHERE i.species.id = :speciesId AND i.plan.deleted = FALSE
            """)
    long countLivePlansUsingSpecies(@Param("speciesId") Long speciesId);

    /**
     * Total trees per species across a user's live plans.
     * Feeds the "CO₂ contribution by species" dashboard chart (FR-DASH-02).
     *
     * @return rows of {@code [speciesId, commonName, totalQuantity]}
     */
    @Query("""
            SELECT i.species.id, i.species.commonName, SUM(i.quantity)
            FROM PlanItem i
            WHERE i.plan.owner.id = :ownerId AND i.plan.deleted = FALSE
            GROUP BY i.species.id, i.species.commonName
            ORDER BY SUM(i.quantity) DESC
            """)
    List<Object[]> sumQuantityBySpeciesForOwner(@Param("ownerId") Long ownerId);

    /**
     * The most-used species across the whole system, for the admin dashboard (FR-DASH-07).
     *
     * @return rows of {@code [speciesId, commonName, planCount, totalQuantity]}
     */
    @Query("""
            SELECT i.species.id, i.species.commonName, COUNT(DISTINCT i.plan.id), SUM(i.quantity)
            FROM PlanItem i
            WHERE i.plan.deleted = FALSE
            GROUP BY i.species.id, i.species.commonName
            ORDER BY COUNT(DISTINCT i.plan.id) DESC, SUM(i.quantity) DESC
            """)
    List<Object[]> findMostUsedSpecies(org.springframework.data.domain.Pageable pageable);

    /**
     * Species count and tree total for each of the given plans, in one query.
     *
     * <p>The plan listing shows "12 species · 1,240 trees" per row. Reading
     * {@code plan.getItems().size()} to produce that would issue one query per row — the
     * exact N+1 the architecture forbids on list-with-children views. This aggregates the
     * whole page in a single statement instead.</p>
     *
     * @return rows of {@code [planId, speciesCount, totalQuantity]}; plans with no items are
     *         absent from the result rather than present with zeroes
     */
    @Query("""
            SELECT i.plan.id, COUNT(i.id), COALESCE(SUM(i.quantity), 0)
            FROM PlanItem i
            WHERE i.plan.id IN :planIds
            GROUP BY i.plan.id
            """)
    List<Object[]> summariseByPlanIds(@Param("planIds") java.util.Collection<Long> planIds);

    /** Total trees across a user's live plans (FR-DASH-01). */
    @Query("""
            SELECT COALESCE(SUM(i.quantity), 0) FROM PlanItem i
            WHERE i.plan.owner.id = :ownerId AND i.plan.deleted = FALSE
            """)
    long sumQuantityForOwner(@Param("ownerId") Long ownerId);
}
