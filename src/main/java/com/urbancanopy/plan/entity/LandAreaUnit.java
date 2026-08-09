package com.urbancanopy.plan.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Units in which a planner may express available land, with the exact factor that
 * normalises each to square metres (BR-07).
 *
 * <p>These factors are held in code rather than in the coefficient registry, and the
 * distinction matters: the registry exists for values that are <em>scientific estimates</em>
 * and may legitimately be revised — survival rates, sequestration benchmarks. One acre is
 * exactly 4046.8564224 m² by definition. It is not an estimate, it will never be revised,
 * and making it editable would create a way to silently produce wrong answers with no
 * corresponding benefit.</p>
 */
public enum LandAreaUnit {

    SQUARE_METRE("m²", "Square metres", new BigDecimal("1")),
    HECTARE("ha", "Hectares", new BigDecimal("10000")),
    ACRE("ac", "Acres", new BigDecimal("4046.8564224"));

    private final String symbol;
    private final String label;
    private final BigDecimal squareMetresPerUnit;

    LandAreaUnit(String symbol, String label, BigDecimal squareMetresPerUnit) {
        this.symbol = symbol;
        this.label = label;
        this.squareMetresPerUnit = squareMetresPerUnit;
    }

    /** Converts a value in this unit to square metres. */
    public BigDecimal toSquareMetres(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.multiply(squareMetresPerUnit).setScale(2, RoundingMode.HALF_UP);
    }

    /** Converts square metres back to this unit, for redisplay. */
    public BigDecimal fromSquareMetres(BigDecimal squareMetres) {
        if (squareMetres == null) {
            return BigDecimal.ZERO;
        }
        return squareMetres.divide(squareMetresPerUnit, 4, RoundingMode.HALF_UP);
    }

    public String getSymbol() {
        return symbol;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getSquareMetresPerUnit() {
        return squareMetresPerUnit;
    }
}
