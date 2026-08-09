package com.urbancanopy.plan.entity;

import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.support.EntityFixtures;
import com.urbancanopy.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour the aggregate root enforces for every caller (BR-07, BR-08, BR-10).
 *
 * <p>These rules live on the entity rather than in a service so that they hold however the plan
 * is reached. Testing them here, with no Spring and no database, is what that placement buys.</p>
 */
@DisplayName("PlantationPlan")
class PlantationPlanTest {

    private User owner;
    private TreeSpecies neem;
    private TreeSpecies peepal;

    @BeforeEach
    void setUp() {
        owner = EntityFixtures.planner("owner@test.local");
        ReflectionTestUtils.setField(owner, "id", 1L);

        neem = EntityFixtures.species("Neem", "Azadirachta indica", "21.800");
        ReflectionTestUtils.setField(neem, "id", 10L);

        peepal = EntityFixtures.species("Peepal", "Ficus religiosa", "26.400");
        ReflectionTestUtils.setField(peepal, "id", 11L);
    }

    private PlantationPlan plan() {
        return EntityFixtures.plan(owner, "Avenue Drive");
    }

    @Test
    @DisplayName("normalises land area to square metres on construction (BR-07)")
    void normalisesLandArea() {
        PlantationPlan plan = new PlantationPlan(
                owner, "Hectares", new BigDecimal("1.2"), LandAreaUnit.HECTARE,
                PlantationPurpose.CARBON_OFFSET, 2026, 10);

        assertThat(plan.getLandAreaSquareMetres()).isEqualByComparingTo("12000.00");
        // The figure the planner typed survives, so the form redisplays what they entered.
        assertThat(plan.getLandAreaInput()).isEqualByComparingTo("1.2");
        assertThat(plan.getLandAreaUnit()).isEqualTo(LandAreaUnit.HECTARE);
    }

    @Test
    @DisplayName("converts acres using the exact definitional factor")
    void convertsAcres() {
        PlantationPlan plan = plan();
        plan.setLandArea(new BigDecimal("1"), LandAreaUnit.ACRE);

        assertThat(plan.getLandAreaSquareMetres()).isEqualByComparingTo("4046.86");
    }

    @Test
    @DisplayName("adding a species already present increments it rather than duplicating (BR-08)")
    void addingTwiceIncrements() {
        PlantationPlan plan = plan();

        plan.addSpecies(neem, 100);
        plan.addSpecies(neem, 50);

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getQuantity()).isEqualTo(150);
        assertThat(plan.getTotalTrees()).isEqualTo(150);
    }

    @Test
    @DisplayName("different species occupy separate items")
    void differentSpeciesAreSeparate() {
        PlantationPlan plan = plan();

        plan.addSpecies(neem, 100);
        plan.addSpecies(peepal, 200);

        assertThat(plan.getItems()).hasSize(2);
        assertThat(plan.getTotalTrees()).isEqualTo(300);
    }

    @Test
    @DisplayName("exposes its items as an unmodifiable view")
    void itemsAreUnmodifiable() {
        PlantationPlan plan = plan();
        plan.addSpecies(neem, 100);

        // Mutating the returned list must not be a way around addSpecies and its BR-08 rule.
        assertThat(plan.getItems()).isUnmodifiable();
    }

    @Test
    @DisplayName("is calculable only with at least one species and a positive land area (BR-10)")
    void calculability() {
        PlantationPlan plan = plan();
        assertThat(plan.isCalculable()).as("no species yet").isFalse();

        plan.addSpecies(neem, 100);
        assertThat(plan.isCalculable()).as("species and land area").isTrue();

        plan.setLandArea(BigDecimal.ZERO, LandAreaUnit.SQUARE_METRE);
        assertThat(plan.isCalculable()).as("no land area").isFalse();
    }

    @Test
    @DisplayName("removing an item detaches it so orphan removal can delete the row")
    void removingDetaches() {
        PlantationPlan plan = plan();
        plan.addSpecies(neem, 100);
        PlanItem item = plan.getItems().get(0);

        plan.removeItem(item);

        assertThat(plan.getItems()).isEmpty();
        assertThat(item.getPlan()).isNull();
    }

    @Test
    @DisplayName("recognises its owner by id")
    void recognisesOwner() {
        PlantationPlan plan = plan();

        assertThat(plan.isOwnedBy(1L)).isTrue();
        assertThat(plan.isOwnedBy(2L)).isFalse();
        assertThat(plan.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("a soft-deleted plan is never editable, whatever its status")
    void deletedIsNeverEditable() {
        PlantationPlan plan = plan();
        plan.setStatus(PlanStatus.DRAFT);

        plan.markDeleted();

        assertThat(plan.isDeleted()).isTrue();
        assertThat(plan.isEditable()).isFalse();
    }
}
