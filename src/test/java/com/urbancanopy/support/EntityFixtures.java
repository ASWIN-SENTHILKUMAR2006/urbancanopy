package com.urbancanopy.support;

import com.urbancanopy.plan.entity.LandAreaUnit;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.entity.WaterRequirement;
import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

/**
 * Builds persistable entities for tests.
 *
 * <p>{@link TreeSpecies} is instantiated reflectively for the same reason the production
 * service does it: its no-arg constructor is {@code protected} so that application code cannot
 * casually create a species with every environmental value left null. The test suite respects
 * that constraint rather than weakening the entity to accommodate itself.</p>
 */
public final class EntityFixtures {

    private EntityFixtures() {
        throw new AssertionError("Utility class");
    }

    public static User planner(String email) {
        return new User("Test Planner", email, "$2a$10$notarealhashbutlongenough000000000000000000000000000", Role.ROLE_USER);
    }

    public static User administrator(String email) {
        return new User("Test Administrator", email, "$2a$10$notarealhashbutlongenough000000000000000000000000000", Role.ROLE_ADMIN);
    }

    /** A species with plausible, non-zero values in every field the engine reads. */
    public static TreeSpecies species(String commonName, String scientificName, String co2PerYear) {
        TreeSpecies species = newSpecies();

        species.setCommonName(commonName);
        species.setScientificName(scientificName);
        species.setDescription("Fixture species for tests.");

        species.setCo2SequestrationKgPerYear(new BigDecimal(co2PerYear));
        species.setOxygenProductionKgPerYear(new BigDecimal("16.000"));
        species.setPm25RemovalGramsPerYear(new BigDecimal("54.000"));
        species.setPm10RemovalGramsPerYear(new BigDecimal("120.000"));
        species.setNo2RemovalGramsPerYear(new BigDecimal("72.000"));
        species.setSo2RemovalGramsPerYear(new BigDecimal("46.000"));
        species.setCoRemovalGramsPerYear(new BigDecimal("31.000"));

        species.setGrowthRate(GrowthRate.MODERATE);
        species.setWaterRequirement(WaterRequirement.LOW);
        species.setClimateZone(ClimateZone.TROPICAL);
        species.setSoilType(SoilType.LOAMY);

        species.setCanopyRadiusMetres(new BigDecimal("4.50"));
        species.setMatureHeightMetres(new BigDecimal("12.00"));
        species.setLifeSpanYears(150);
        species.setSpacingMetres(new BigDecimal("6.00"));
        species.setNativeSpecies(true);
        species.setSourceCitation("Fixture citation — not a real source.");
        species.setActive(true);

        return species;
    }

    /** A draft plan of 12,000 m² over a ten-year horizon. */
    public static PlantationPlan plan(User owner, String title) {
        return new PlantationPlan(
                owner, title, new BigDecimal("12000"), LandAreaUnit.SQUARE_METRE,
                PlantationPurpose.AIR_QUALITY_IMPROVEMENT, 2026, 10);
    }

    private static TreeSpecies newSpecies() {
        try {
            var constructor = TreeSpecies.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("TreeSpecies could not be instantiated for a test", e);
        }
    }
}
