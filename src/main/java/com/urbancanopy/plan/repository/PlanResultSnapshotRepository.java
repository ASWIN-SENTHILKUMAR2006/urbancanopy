package com.urbancanopy.plan.repository;

import com.urbancanopy.plan.entity.PlanResultSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence access for {@link PlanResultSnapshot} (FR-CALC-14). */
public interface PlanResultSnapshotRepository extends JpaRepository<PlanResultSnapshot, Long> {

    Optional<PlanResultSnapshot> findByPlanId(Long planId);

    /**
     * Headline figures from the snapshots of the given plans, in one query.
     *
     * <p>Feeds the two computed columns of the plan listing. A projection rather than the
     * entities, because a list row needs two numbers out of the snapshot's twenty.</p>
     *
     * @return rows of {@code [planId, annualCo2Kg, impactScore]}
     */
    @Query("""
            SELECT s.plan.id, s.annualCo2Kg, s.impactScore
            FROM PlanResultSnapshot s
            WHERE s.plan.id IN :planIds
            """)
    List<Object[]> summariseByPlanIds(@Param("planIds") Collection<Long> planIds);

    void deleteByPlanId(Long planId);

    boolean existsByPlanId(Long planId);
}
