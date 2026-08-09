package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import com.urbancanopy.species.entity.GrowthRate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Converts <em>trees planted</em> into <em>mature-equivalent trees</em> (FR-CALC-06).
 *
 * <p>This is the single most consequential adjustment in the system, and the reason the
 * application's figures are defensible rather than promotional. A naive tool multiplies
 * 1000 saplings by a mature tree's sequestration rate and reports a number that will not
 * be reached for two decades, if ever. This calculator applies three corrections
 * first:</p>
 *
 * <ol>
 *   <li><strong>Survival.</strong> Not every sapling planted becomes a tree. The expected
 *       survival fraction comes from {@code calc.survival.rate}.</li>
 *   <li><strong>Maturity.</strong> A tree does not sequester at its mature rate in year
 *       one. The maturity factor ramps linearly from 0 to 1 over the years the species'
 *       growth rate requires, so a fast species reaches full performance in about seven
 *       years and a slow one in about twenty.</li>
 *   <li><strong>Site condition.</strong> A multiplier for soil, irrigation and maintenance
 *       quality, from {@code calc.site.condition.factor}.</li>
 * </ol>
 *
 * <p>Every one of those figures is a database row, not a literal (BR-15).</p>
 *
 * <p><strong>Headline versus projection.</strong> The headline annual estimate uses a
 * maturity factor of 1 and is labelled "at maturity" throughout the interface — that is
 * the honest way to state a steady-state figure. The year-by-year projection uses
 * {@link #maturityFactor} for each year, which is where the ramp becomes visible.</p>
 */
@Component
public class EffectiveTreeCalculator {

    /**
     * Mature-equivalent trees at full maturity: {@code quantity × survival × site}.
     *
     * @param quantity     trees planned
     * @param coefficients active constants
     * @return effective tree count, at internal working scale
     */
    public BigDecimal atMaturity(int quantity, CoefficientSet coefficients) {
        return effective(quantity, BigDecimal.ONE, coefficients);
    }

    /**
     * Mature-equivalent trees in a given year:
     * {@code quantity × survival × site × maturityFactor(year)}.
     *
     * @param quantity     trees planned
     * @param growthRate   species growth rate, which sets how long maturity takes
     * @param year         1-based year from planting
     * @param coefficients active constants
     */
    public BigDecimal inYear(int quantity, GrowthRate growthRate, int year, CoefficientSet coefficients) {
        return effective(quantity, maturityFactor(growthRate, year, coefficients), coefficients);
    }

    /**
     * The share of mature performance a species delivers in a given year, from 0 to 1.
     *
     * <p>A linear ramp is used deliberately. Real growth is sigmoid, but a sigmoid would
     * require two further shape parameters that no administrator could sensibly justify
     * from the literature, and would make the figure harder to explain in the calculation
     * trace. A linear ramp is transparent, conservative in early years, and exact at
     * maturity — the properties that matter for a planning estimate.</p>
     *
     * @param year 1-based year from planting; years beyond maturity return exactly 1
     */
    public BigDecimal maturityFactor(GrowthRate growthRate, int year, CoefficientSet coefficients) {
        if (year <= 0) {
            return BigDecimal.ZERO;
        }
        int yearsToMaturity = coefficients.requireInt(maturityKey(growthRate));
        if (year >= yearsToMaturity) {
            return BigDecimal.ONE;
        }
        return Decimals.divideOr(
                BigDecimal.valueOf(year), BigDecimal.valueOf(yearsToMaturity), BigDecimal.ONE);
    }

    /** Years the given growth rate takes to reach mature performance. */
    public int yearsToMaturity(GrowthRate growthRate, CoefficientSet coefficients) {
        return coefficients.requireInt(maturityKey(growthRate));
    }

    private BigDecimal effective(int quantity, BigDecimal maturityFactor, CoefficientSet coefficients) {
        BigDecimal survivalRate = coefficients.require(CoefficientKeys.SURVIVAL_RATE);
        BigDecimal siteFactor = coefficients.require(CoefficientKeys.SITE_CONDITION_FACTOR);
        return Decimals.internal(
                BigDecimal.valueOf(quantity)
                        .multiply(survivalRate)
                        .multiply(siteFactor)
                        .multiply(maturityFactor));
    }

    /**
     * Resolves the coefficient key for a growth rate, defaulting to the slowest ramp when
     * the rate is unknown — the conservative choice, since over-estimating benefit is the
     * failure mode that would damage this system's credibility.
     */
    private String maturityKey(GrowthRate growthRate) {
        return growthRate == null
                ? CoefficientKeys.MATURITY_YEARS_SLOW
                : growthRate.getMaturityYearsCoefficientKey();
    }
}
