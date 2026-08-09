package com.urbancanopy.plan.repository;

import com.urbancanopy.config.JpaAuditingConfig;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.support.EntityFixtures;
import com.urbancanopy.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice tests for {@link PlantationPlanRepository}.
 *
 * <p>Their purpose is the hand-written JPQL. Every one of these queries is a string that the
 * compiler cannot check: a mistyped property, a join that silently produces a cartesian
 * product, or a soft-delete predicate left off a listing are all invisible until something
 * runs them. These tests run them.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
// A slice does not load the application's own @Configuration classes, so without this the
// auditing listener never runs and every insert fails on a null created_at. Importing the real
// config also means these tests exercise the actual auditing wiring rather than a stand-in.
@Import(JpaAuditingConfig.class)
@DisplayName("PlantationPlanRepository")
class PlantationPlanRepositoryTest {

    @Autowired
    private PlantationPlanRepository planRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherOwner;
    private TreeSpecies neem;

    @BeforeEach
    void seed() {
        owner = entityManager.merge(EntityFixtures.planner("owner@test.local"));
        otherOwner = entityManager.merge(EntityFixtures.planner("other@test.local"));
        neem = entityManager.merge(EntityFixtures.species("Neem", "Azadirachta indica", "21.800"));
        entityManager.flush();
    }

    private PlantationPlan persistPlan(User planOwner, String title, PlanStatus status, boolean deleted) {
        PlantationPlan plan = EntityFixtures.plan(planOwner, title);
        plan.setStatus(status);
        if (deleted) {
            plan.markDeleted();
        }
        plan.addSpecies(neem, 100);
        PlantationPlan saved = planRepository.save(plan);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    @Nested
    @DisplayName("owner scoping")
    class OwnerScoping {

        @Test
        @DisplayName("returns only the requested owner's plans")
        void scopesToOwner() {
            persistPlan(owner, "Mine", PlanStatus.DRAFT, false);
            persistPlan(otherOwner, "Theirs", PlanStatus.DRAFT, false);

            Page<PlantationPlan> page = planRepository.searchForOwner(
                    owner.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(PlantationPlan::getTitle)
                    .containsExactly("Mine");
        }

        @Test
        @DisplayName("excludes soft-deleted plans (BR-21)")
        void excludesDeleted() {
            persistPlan(owner, "Live", PlanStatus.DRAFT, false);
            persistPlan(owner, "Deleted", PlanStatus.DRAFT, true);

            Page<PlantationPlan> page = planRepository.searchForOwner(
                    owner.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(PlantationPlan::getTitle)
                    .containsExactly("Live");
        }

        @Test
        @DisplayName("filters by search term, status and purpose together")
        void appliesEveryFilter() {
            persistPlan(owner, "Avenue Drive", PlanStatus.COMPLETED, false);
            persistPlan(owner, "Avenue Verge", PlanStatus.DRAFT, false);
            persistPlan(owner, "Park Belt", PlanStatus.COMPLETED, false);

            Page<PlantationPlan> page = planRepository.searchForOwner(
                    owner.getId(), "avenue", PlanStatus.COMPLETED,
                    PlantationPurpose.AIR_QUALITY_IMPROVEMENT, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(PlantationPlan::getTitle)
                    .containsExactly("Avenue Drive");
        }

        @Test
        @DisplayName("matches the search term case-insensitively against site name too")
        void searchesSiteName() {
            PlantationPlan plan = EntityFixtures.plan(owner, "Untitled");
            plan.setSiteName("Anna Nagar 2nd Avenue");
            planRepository.save(plan);
            entityManager.flush();
            entityManager.clear();

            Page<PlantationPlan> page = planRepository.searchForOwner(
                    owner.getId(), "ANNA NAGAR", null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("detailed fetch")
    class DetailedFetch {

        @Test
        @DisplayName("loads items and species without a second query (NFR-02)")
        void fetchJoinsTheGraph() {
            PlantationPlan saved = persistPlan(owner, "Fetch me", PlanStatus.DRAFT, false);

            PlantationPlan loaded = planRepository.findDetailedById(saved.getId()).orElseThrow();
            entityManager.detach(loaded);

            // Detached: touching a lazy association now would throw, so reading the species'
            // name proves the fetch join actually populated the graph.
            assertThat(loaded.getItems()).hasSize(1);
            assertThat(loaded.getItems().get(0).getSpecies().getCommonName()).isEqualTo("Neem");
            assertThat(loaded.getOwner().getEmail()).isEqualTo("owner@test.local");
        }

        @Test
        @DisplayName("refuses to load a soft-deleted plan")
        void hidesDeleted() {
            PlantationPlan deleted = persistPlan(owner, "Gone", PlanStatus.DRAFT, true);

            assertThat(planRepository.findDetailedById(deleted.getId())).isEmpty();
            assertThat(planRepository.findByIdAndDeletedFalse(deleted.getId())).isEmpty();
        }

        @Test
        @DisplayName("loads several plans for comparison in one query")
        void fetchesManyById() {
            PlantationPlan first = persistPlan(owner, "First", PlanStatus.DRAFT, false);
            PlantationPlan second = persistPlan(owner, "Second", PlanStatus.DRAFT, false);

            List<PlantationPlan> plans = planRepository.findDetailedByIds(
                    List.of(first.getId(), second.getId()));

            // DISTINCT matters here: without it the join against items multiplies the rows.
            assertThat(plans).hasSize(2);
        }
    }

    @Nested
    @DisplayName("aggregates")
    class Aggregates {

        @Test
        @DisplayName("counts live plans per owner in one grouped query")
        void countsByOwner() {
            persistPlan(owner, "One", PlanStatus.DRAFT, false);
            persistPlan(owner, "Two", PlanStatus.DRAFT, false);
            persistPlan(owner, "Deleted", PlanStatus.DRAFT, true);
            persistPlan(otherOwner, "Theirs", PlanStatus.DRAFT, false);

            List<Object[]> rows = planRepository.countByOwnerIds(
                    List.of(owner.getId(), otherOwner.getId()));

            assertThat(rows).hasSize(2);
            assertThat(rows.stream()
                    .filter(row -> owner.getId().equals(row[0]))
                    .map(row -> ((Number) row[1]).longValue())
                    .findFirst())
                    .contains(2L);
        }

        @Test
        @DisplayName("enforces per-owner title uniqueness among live plans only")
        void titleUniquenessIgnoresDeleted() {
            persistPlan(owner, "Phase 1", PlanStatus.DRAFT, true);

            // The deleted plan must not block reusing its title — the reason this rule lives
            // in the service rather than in a database unique key.
            assertThat(planRepository
                    .existsByOwnerIdAndTitleIgnoreCaseAndDeletedFalse(owner.getId(), "Phase 1"))
                    .isFalse();

            persistPlan(owner, "Phase 1", PlanStatus.DRAFT, false);

            assertThat(planRepository
                    .existsByOwnerIdAndTitleIgnoreCaseAndDeletedFalse(owner.getId(), "phase 1"))
                    .isTrue();
        }

        @Test
        @DisplayName("orders recent activity by last update, newest first (FR-DASH-06)")
        void ordersRecentPlans() throws InterruptedException {
            persistPlan(owner, "Older", PlanStatus.DRAFT, false);
            Thread.sleep(20); // auditing timestamps are millisecond-resolution
            persistPlan(owner, "Newer", PlanStatus.DRAFT, false);

            List<PlantationPlan> recent = planRepository.findRecentForOwner(
                    owner.getId(), PageRequest.of(0, 5));

            assertThat(recent).extracting(PlantationPlan::getTitle)
                    .containsExactly("Newer", "Older");
        }
    }
}
