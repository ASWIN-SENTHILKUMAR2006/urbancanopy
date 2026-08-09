package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates tonnes and kilograms into things people can picture (FR-CALC-09).
 *
 * <p>"71.4 tonnes of CO₂ per year" means very little to a councillor deciding whether to
 * fund a plantation. "590,000 car-kilometres" means something. These conversions are what
 * carry the estimate from a technical report into a decision.</p>
 *
 * <p>Every equivalence is a division by a coefficient row. Adding a new one — flights,
 * air-conditioner hours, tonnes of coal — is an {@code INSERT} into
 * {@code environmental_coefficients} and a single entry here; the ambition is that the
 * data change alone is enough, and the code below is deliberately shaped as a list of
 * declarations rather than a sequence of statements to keep that step trivial.</p>
 */
@Component
public class EquivalenceConverter {

    /**
     * Builds the full set of equivalences for a plan.
     *
     * @param annualCo2Kg  annual sequestration in kilograms
     * @param annualO2Kg   annual oxygen generation in kilograms
     * @param coefficients supplies each conversion factor
     */
    public List<CalculationResult.Equivalence> convert(
            BigDecimal annualCo2Kg, BigDecimal annualO2Kg, CoefficientSet coefficients) {

        List<CalculationResult.Equivalence> equivalences = new ArrayList<>();

        addIfPresent(equivalences, coefficients, CoefficientKeys.EQUIV_CAR_CO2_PER_KM,
                annualCo2Kg, "carKilometres", "Car travel offset", "km",
                "Kilometres of average passenger-car travel whose emissions this plantation offsets each year.");

        addIfPresent(equivalences, coefficients, CoefficientKeys.EQUIV_HOUSEHOLD_CO2_PER_YEAR,
                annualCo2Kg, "households", "Household electricity offset", "households",
                "Households whose annual electricity emissions this plantation offsets.");

        addIfPresent(equivalences, coefficients, CoefficientKeys.EQUIV_PETROL_CO2_PER_LITRE,
                annualCo2Kg, "petrolLitres", "Petrol offset", "litres",
                "Litres of petrol whose combustion emissions this plantation offsets each year.");

        addIfPresent(equivalences, coefficients, CoefficientKeys.EQUIV_PERSON_O2_PER_YEAR,
                annualO2Kg, "peopleSupported", "Annual oxygen need met", "people",
                "People whose annual oxygen requirement this plantation generates.");

        return List.copyOf(equivalences);
    }

    /**
     * Adds one equivalence: {@code quantity ÷ factor}, rounded to whole units.
     *
     * <p>Skipped silently when the coefficient is absent, so an administrator removing a
     * conversion factor removes the equivalence from every screen and report rather than
     * breaking them.</p>
     */
    private void addIfPresent(
            List<CalculationResult.Equivalence> target,
            CoefficientSet coefficients,
            String coefficientKey,
            BigDecimal quantity,
            String key,
            String label,
            String unit,
            String description) {

        coefficients.find(coefficientKey)
                .filter(factor -> factor.signum() > 0)
                .flatMap(factor -> Decimals.divide(Decimals.orZero(quantity), factor))
                .map(value -> value.setScale(0, RoundingMode.HALF_UP))
                .filter(value -> value.signum() > 0)
                .ifPresent(value -> target.add(
                        new CalculationResult.Equivalence(key, label, value, unit, description)));
    }
}
