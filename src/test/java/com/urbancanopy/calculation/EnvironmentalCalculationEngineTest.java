package com.urbancanopy.calculation;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.calculation.model.FeasibilityVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.urbancanopy.calculation.CalculationTestFixtures.DISCLAIMER;
import static com.urbancanopy.calculation.CalculationTestFixtures.ashoka;
import static com.urbancanopy.calculation.CalculationTestFixtures.banyan;
import static com.urbancanopy.calculation.CalculationTestFixtures.defaultCoefficients;
import static com.urbancanopy.calculation.CalculationTestFixtures.engine;
import static com.urbancanopy.calculation.CalculationTestFixtures.neem;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the arithmetic at the heart of the application.
 *
 * <p>Every expected value below is derived by hand in its own comment, so a reviewer can
 * check the test as easily as the code. A test that asserts a magic number tells nobody
 * whether the code or the test is wrong.</p>
 */
@DisplayName("Environmental calculation engine")
class EnvironmentalCalculationEngineTest {

    private final EnvironmentalCalculationEngine engine = engine();
    private final CoefficientSet coefficients = defaultCoefficients();

    private CalculationResult evaluate(BigDecimal landAreaSqm, int years,
                                       CalculationInput.SpeciesInput... species) {
        return engine.evaluate(
                new CalculationInput(landAreaSqm, years, List.of(species)),
                coefficients, 2026, DISCLAIMER);
    }

    @Nested
    @DisplayName("carbon sequestration")
    class Carbon {

        @Test
        @DisplayName("applies the survival rate rather than reporting raw planted counts")
        void appliesSurvivalRate() {
            // 1000 neem x 0.85 survival x 1.00 site x 1.00 maturity = 850 effective trees
            // 850 x 21.800 kg CO2/tree/yr                            = 18,530.000 kg/yr
            CalculationResult result = evaluate(new BigDecimal("50000"), 10, neem(1000));

            assertThat(result.effectiveTrees()).isEqualByComparingTo("850.00");
            assertThat(result.carbon().annualKg()).isEqualByComparingTo("18530.000");

            // The naive figure a tool without a survival adjustment would report:
            assertThat(result.carbon().annualKg()).isNotEqualByComparingTo("21800.000");
        }

        @Test
        @DisplayName("reports tonnes consistently with kilograms")
        void tonnesMatchKilograms() {
            CalculationResult result = evaluate(new BigDecimal("50000"), 10, neem(1000));

            // 18,530 kg = 18.530 t
            assertThat(result.carbon().annualTonnes()).isEqualByComparingTo("18.530");
        }

        @Test
        @DisplayName("sums contributions across a mixed planting")
        void sumsAcrossSpecies() {
            // neem   : 100 x 0.85 =  85 effective x 21.800 = 1,853.000 kg/yr
            // banyan :  50 x 0.85 = 42.5 effective x 31.500 = 1,338.750 kg/yr
            //                                        total  = 3,191.750 kg/yr
            CalculationResult result = evaluate(new BigDecimal("20000"), 10, neem(100), banyan(50));

            assertThat(result.carbon().annualKg()).isEqualByComparingTo("3191.750");
            assertThat(result.totalTrees()).isEqualTo(150);
        }

        @Test
        @DisplayName("attributes each species its share of the total")
        void computesSpeciesShares() {
            CalculationResult result = evaluate(new BigDecimal("20000"), 10, neem(100), banyan(50));

            // 1,853.000 / 3,191.750 = 58.06 %
            CalculationResult.SpeciesContribution neemShare = result.contributions().get(0);
            assertThat(neemShare.co2SharePercent()).isEqualByComparingTo("58.06");

            BigDecimal totalShare = result.contributions().stream()
                    .map(CalculationResult.SpeciesContribution::co2SharePercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalShare).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("canopy and feasibility")
    class CanopyAndFeasibility {

        @Test
        @DisplayName("computes canopy area from planted count, not survival-adjusted count")
        void canopyUsesPlantedCount() {
            // 100 neem x pi x 4.5^2 = 100 x 63.617 = 6,361.73 m2
            // Survival is NOT applied: canopy is a physical footprint question.
            CalculationResult result = evaluate(new BigDecimal("20000"), 10, neem(100));

            assertThat(result.canopy().totalAreaSquareMetres()).isEqualByComparingTo("6361.73");
        }

        @Test
        @DisplayName("caps displayed coverage at 100% but retains the uncapped ratio")
        void capsCoverageButRetainsTruth() {
            // 100 neem = 6,361.73 m2 canopy on 2,000 m2 of land = 318.09 %
            CalculationResult result = evaluate(new BigDecimal("2000"), 10, neem(100));

            assertThat(result.canopy().coveragePercent()).isEqualByComparingTo("100.00");
            assertThat(result.canopy().uncappedCoveragePercent()).isEqualByComparingTo("318.09");
        }

        @Test
        @DisplayName("reports OPTIMAL when spacing demand sits in the target band")
        void optimalVerdict() {
            // 100 neem x 6^2 spacing = 3,600 m2 required, on 4,000 m2 = 90.00 % utilisation
            // 40 % < 90 % <= 90 %  ->  OPTIMAL
            CalculationResult result = evaluate(new BigDecimal("4000"), 10, neem(100));

            assertThat(result.feasibility().utilisationPercent()).isEqualByComparingTo("90.00");
            assertThat(result.feasibility().verdict()).isEqualTo(FeasibilityVerdict.OPTIMAL);
        }

        @Test
        @DisplayName("reports OVERCROWDED and states the maximum plantable count")
        void overcrowdedVerdict() {
            // 200 neem x 36 = 7,200 m2 required on 2,000 m2 = 360 % -> OVERCROWDED
            // mean footprint 36 m2/tree; 2,000 / 36 = 55 trees fit
            CalculationResult result = evaluate(new BigDecimal("2000"), 10, neem(200));

            assertThat(result.feasibility().verdict()).isEqualTo(FeasibilityVerdict.OVERCROWDED);
            assertThat(result.feasibility().maxPlantableTrees()).isEqualTo(55);
            assertThat(result.feasibility().surplusAreaSquareMetres()).isEqualByComparingTo("-5200.00");
        }

        @Test
        @DisplayName("reports UNDER_UTILISED when the site has substantial spare capacity")
        void underUtilisedVerdict() {
            // 10 neem x 36 = 360 m2 on 10,000 m2 = 3.6 % -> UNDER_UTILISED
            CalculationResult result = evaluate(new BigDecimal("10000"), 10, neem(10));

            assertThat(result.feasibility().verdict()).isEqualTo(FeasibilityVerdict.UNDER_UTILISED);
            assertThat(result.warnings()).anyMatch(w -> w.contains("available land is used"));
        }

        @Test
        @DisplayName("reports NOT_APPLICABLE rather than dividing by zero when land area is absent")
        void handlesMissingLandArea() {
            CalculationResult result = evaluate(BigDecimal.ZERO, 10, neem(100));

            assertThat(result.feasibility().verdict()).isEqualTo(FeasibilityVerdict.NOT_APPLICABLE);
            assertThat(result.canopy().coveragePercent()).isNull();
            assertThat(result.feasibility().utilisationPercent()).isNull();

            // Everything not dependent on land area still computes normally.
            assertThat(result.carbon().annualKg()).isEqualByComparingTo("1853.000");
        }
    }

    @Nested
    @DisplayName("species diversity")
    class Diversity {

        @Test
        @DisplayName("scores a single-species planting as zero diversity and flags the risk")
        void singleSpeciesIsMonoculture() {
            CalculationResult result = evaluate(new BigDecimal("20000"), 10, neem(100));

            assertThat(result.diversity().shannonIndex()).isEqualByComparingTo("0.0000");
            assertThat(result.diversity().speciesCount()).isEqualTo(1);
            assertThat(result.diversity().monocultureRisk()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("vulnerable to a single pest"));
        }

        @Test
        @DisplayName("computes Shannon H' for an even three-species mix")
        void evenMixMaximisesIndex() {
            // Three species at 100 each: p = 1/3 for all
            // H' = -3 x (1/3 x ln(1/3)) = ln(3) = 1.0986
            CalculationResult result = evaluate(
                    new BigDecimal("50000"), 10, neem(100), banyan(100), ashoka(100));

            assertThat(result.diversity().shannonIndex()).isEqualByComparingTo("1.0986");
            assertThat(result.diversity().monocultureRisk()).isFalse();
        }

        @Test
        @DisplayName("flags a dominant species above the configured threshold")
        void flagsDominance() {
            // neem 700 of 800 total = 87.5 % > 60 % threshold
            CalculationResult result = evaluate(
                    new BigDecimal("50000"), 10, neem(700), ashoka(100));

            assertThat(result.diversity().dominantSpeciesName()).isEqualTo("Neem");
            assertThat(result.diversity().dominantSharePercent()).isEqualByComparingTo("87.50");
            assertThat(result.diversity().monocultureRisk()).isTrue();
        }
    }

    @Nested
    @DisplayName("multi-year projection")
    class Projection {

        @Test
        @DisplayName("ramps a fast-growing species to full performance over seven years")
        void fastSpeciesRamp() {
            CalculationResult result = evaluate(new BigDecimal("50000"), 10, neem(1000));

            List<CalculationResult.ProjectionPoint> points = result.projection();
            assertThat(points).hasSize(10);

            // Year 1: maturity 1/7; 1000 x 0.85 x 1/7 = 121.428571 effective
            //         121.428571 x 21.800 = 2,647.14 kg
            assertThat(points.get(0).annualCo2Kg()).isEqualByComparingTo("2647.143");
            assertThat(points.get(0).calendarYear()).isEqualTo(2026);

            // Year 7 onward: fully mature, 18,530 kg/yr
            assertThat(points.get(6).annualCo2Kg()).isEqualByComparingTo("18530.000");
            assertThat(points.get(9).annualCo2Kg()).isEqualByComparingTo("18530.000");
            assertThat(points.get(9).averageMaturity()).isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("accumulates cumulative carbon monotonically")
        void cumulativeIsMonotonic() {
            CalculationResult result = evaluate(new BigDecimal("50000"), 10, neem(500), banyan(200));

            List<CalculationResult.ProjectionPoint> points = result.projection();
            for (int i = 1; i < points.size(); i++) {
                assertThat(points.get(i).cumulativeCo2Kg())
                        .isGreaterThanOrEqualTo(points.get(i - 1).cumulativeCo2Kg());
            }
            assertThat(result.carbon().horizonKg())
                    .isEqualByComparingTo(points.get(points.size() - 1).cumulativeCo2Kg());
        }

        @Test
        @DisplayName("shows a slow species trailing a fast one over a short horizon")
        void growthRateChangesTheHorizonTotal() {
            // Both plans have similar mature annual rates, but banyan takes 20 years to
            // get there while neem takes 7. Over 10 years that gap is the whole point of
            // showing a projection at all.
            CalculationResult fast = evaluate(new BigDecimal("50000"), 10, neem(1000));
            CalculationResult slow = evaluate(new BigDecimal("50000"), 10, banyan(690));

            assertThat(fast.carbon().annualKg())
                    .isCloseTo(slow.carbon().annualKg(), org.assertj.core.data.Offset.offset(
                            new BigDecimal("200")));
            assertThat(fast.carbon().horizonKg()).isGreaterThan(slow.carbon().horizonKg());
        }
    }

    @Nested
    @DisplayName("impact score")
    class Score {

        @Test
        @DisplayName("weights four sub-scores that each stay within 0-100")
        void subScoresAreBounded() {
            CalculationResult result = evaluate(
                    new BigDecimal("20000"), 10, neem(100), banyan(50), ashoka(80));

            CalculationResult.ImpactScore score = result.impactScore();
            assertThat(score.overall()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
            assertThat(score.carbonSubScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
            assertThat(score.airQualitySubScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
            assertThat(score.canopySubScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
            assertThat(score.diversitySubScore()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));

            // The weighted sum must reconcile with the reported total.
            BigDecimal recomputed = score.carbonSubScore().multiply(score.carbonWeight())
                    .add(score.airQualitySubScore().multiply(score.airQualityWeight()))
                    .add(score.canopySubScore().multiply(score.canopyWeight()))
                    .add(score.diversitySubScore().multiply(score.diversityWeight()));
            assertThat(score.overall()).isCloseTo(recomputed,
                    org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
        }

        @Test
        @DisplayName("rewards species choice, not merely planting more trees")
        void scoreIsIntensityBased() {
            // Same species, ten times the quantity: the per-tree intensity is identical,
            // so the carbon sub-score must not move.
            CalculationResult small = evaluate(new BigDecimal("100000"), 10, neem(100));
            CalculationResult large = evaluate(new BigDecimal("100000"), 10, neem(1000));

            assertThat(small.impactScore().carbonSubScore())
                    .isEqualByComparingTo(large.impactScore().carbonSubScore());
        }
    }

    @Nested
    @DisplayName("equivalences and disclosure")
    class Disclosure {

        @Test
        @DisplayName("converts carbon and oxygen into relatable comparisons")
        void producesEquivalences() {
            // 18,530 kg CO2 / 0.121 kg per km = 153,140 car-km
            CalculationResult result = evaluate(new BigDecimal("50000"), 10, neem(1000));

            assertThat(result.equivalences()).extracting(CalculationResult.Equivalence::key)
                    .contains("carKilometres", "households", "petrolLitres", "peopleSupported");

            CalculationResult.Equivalence carKm = result.equivalences().stream()
                    .filter(e -> e.key().equals("carKilometres")).findFirst().orElseThrow();
            assertThat(carKm.value()).isEqualByComparingTo("153140");
        }

        @Test
        @DisplayName("always carries the estimation disclaimer")
        void alwaysCarriesDisclaimer() {
            assertThat(evaluate(new BigDecimal("5000"), 10, neem(50)).disclaimer())
                    .isEqualTo(DISCLAIMER);
            assertThat(evaluate(new BigDecimal("5000"), 10).disclaimer())
                    .isEqualTo(DISCLAIMER);
        }

        @Test
        @DisplayName("explains every headline figure in the calculation trace")
        void producesExplainableTrace() {
            CalculationResult result = evaluate(new BigDecimal("5000"), 10, neem(100));

            assertThat(result.trace()).isNotEmpty();
            assertThat(result.trace()).anyMatch(entry -> entry.label().equals("CO₂ · Neem"));
            assertThat(result.trace()).anyMatch(entry -> entry.expression().contains("0.85"));
            assertThat(result.trace()).anyMatch(entry -> entry.label().startsWith("Source ·"));
        }
    }

    @Nested
    @DisplayName("degenerate input")
    class DegenerateInput {

        @Test
        @DisplayName("returns a zeroed result for an empty plan instead of throwing")
        void emptyPlanIsNotAnError() {
            CalculationResult result = evaluate(new BigDecimal("5000"), 10);

            assertThat(result.sufficientData()).isFalse();
            assertThat(result.totalTrees()).isZero();
            assertThat(result.carbon().annualKg()).isEqualByComparingTo("0");
            assertThat(result.warnings()).isNotEmpty();
        }

        @Test
        @DisplayName("produces identical output for identical input")
        void isDeterministic() {
            CalculationResult first = evaluate(new BigDecimal("12000"), 15, neem(800), banyan(120));
            CalculationResult second = evaluate(new BigDecimal("12000"), 15, neem(800), banyan(120));

            assertThat(first.carbon()).isEqualTo(second.carbon());
            assertThat(first.pollutants()).isEqualTo(second.pollutants());
            assertThat(first.impactScore()).isEqualTo(second.impactScore());
            assertThat(first.projection()).isEqualTo(second.projection());
        }
    }
}
