package com.urbancanopy.common.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The application's single numeric policy.
 *
 * <p>Every figure this system reports is the product of a chain of multiplications and
 * divisions. Performed in {@code double}, that chain drifts: a report that reads
 * {@code 12.999999999998} instead of {@code 13.00} destroys the credibility of the whole
 * document. All arithmetic therefore uses {@link BigDecimal}, and the scale and rounding
 * rules live here rather than being repeated — and eventually contradicted — at each call
 * site.</p>
 *
 * <p><strong>Rounding discipline:</strong> intermediate results keep
 * {@link #INTERNAL_SCALE} digits; rounding to {@link #PRESENTATION_SCALE} happens once,
 * at the presentation boundary. Rounding between intermediate steps compounds error and
 * is never done.</p>
 */
public final class Decimals {

    /** Digits retained on intermediate results. */
    public static final int INTERNAL_SCALE = 6;

    /** Digits shown to a user or written into a report. */
    public static final int PRESENTATION_SCALE = 2;

    /** Digits used for indices such as Shannon H′, where 2 dp would lose meaning. */
    public static final int INDEX_SCALE = 4;

    /** The one rounding mode used throughout the application. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Precision for division, which would otherwise be non-terminating. */
    public static final MathContext DIVISION_CONTEXT = new MathContext(20, ROUNDING);

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Decimals() {
        throw new AssertionError("Utility class");
    }

    /** Returns {@code value}, or {@link BigDecimal#ZERO} when it is {@code null}. */
    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Rounds to the internal working scale. Use between calculation stages. */
    public static BigDecimal internal(BigDecimal value) {
        return orZero(value).setScale(INTERNAL_SCALE, ROUNDING);
    }

    /** Rounds to the presentation scale. Use only when producing a DTO or report cell. */
    public static BigDecimal present(BigDecimal value) {
        return orZero(value).setScale(PRESENTATION_SCALE, ROUNDING);
    }

    /** Rounds to the index scale, for dimensionless indices such as Shannon H′. */
    public static BigDecimal index(BigDecimal value) {
        return orZero(value).setScale(INDEX_SCALE, ROUNDING);
    }

    /** Rounds to an explicit scale. */
    public static BigDecimal scaled(BigDecimal value, int scale) {
        return orZero(value).setScale(scale, ROUNDING);
    }

    /**
     * Divides safely. Returns {@link Optional#empty()} when the divisor is zero or
     * {@code null} — the caller then renders "not applicable" rather than crashing or
     * producing {@code NaN}. This is what keeps a plan with no land area from throwing
     * (UC-06 alternate flow A2).
     */
    public static Optional<BigDecimal> divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(orZero(dividend).divide(divisor, DIVISION_CONTEXT));
    }

    /** Divides, substituting {@code fallback} when the divisor is zero. */
    public static BigDecimal divideOr(BigDecimal dividend, BigDecimal divisor, BigDecimal fallback) {
        return divide(dividend, divisor).orElse(fallback);
    }

    /**
     * Expresses {@code part} as a percentage of {@code whole}.
     * Empty when {@code whole} is zero — a percentage of nothing is undefined, not zero.
     */
    public static Optional<BigDecimal> percentage(BigDecimal part, BigDecimal whole) {
        return divide(part, whole).map(ratio -> ratio.multiply(HUNDRED));
    }

    /** Multiplies a chain of factors, treating {@code null} as {@link BigDecimal#ONE}. */
    public static BigDecimal product(BigDecimal... factors) {
        BigDecimal result = BigDecimal.ONE;
        for (BigDecimal factor : factors) {
            result = result.multiply(factor == null ? BigDecimal.ONE : factor);
        }
        return result;
    }

    /** Sums a chain of terms, treating {@code null} as {@link BigDecimal#ZERO}. */
    public static BigDecimal sum(BigDecimal... terms) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal term : terms) {
            result = result.add(orZero(term));
        }
        return result;
    }

    /** Constrains a value to {@code [min, max]}. */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal v = orZero(value);
        if (min != null && v.compareTo(min) < 0) {
            return min;
        }
        if (max != null && v.compareTo(max) > 0) {
            return max;
        }
        return v;
    }

    /** {@code true} when the value is non-null and strictly greater than zero. */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** Converts grams to kilograms. */
    public static BigDecimal gramsToKilograms(BigDecimal grams) {
        return orZero(grams).divide(BigDecimal.valueOf(1_000), DIVISION_CONTEXT);
    }

    /** Converts kilograms to metric tonnes. */
    public static BigDecimal kilogramsToTonnes(BigDecimal kilograms) {
        return orZero(kilograms).divide(BigDecimal.valueOf(1_000), DIVISION_CONTEXT);
    }
}
