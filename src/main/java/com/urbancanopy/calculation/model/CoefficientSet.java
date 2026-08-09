package com.urbancanopy.calculation.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable snapshot of the coefficient registry, passed <em>into</em> the engine.
 *
 * <p>The engine does not look coefficients up; it is handed them. That inversion is what
 * keeps the engine pure, and it buys two things that a directly-injected service could
 * not:</p>
 *
 * <ul>
 *   <li><strong>Reproducibility.</strong> Recomputing a historical result means passing
 *       the coefficient set stored in its snapshot. Same input, same constants, same
 *       answer — years later.</li>
 *   <li><strong>Testability.</strong> A unit test constructs the exact constants it wants
 *       to reason about, with no Spring context and no database.</li>
 * </ul>
 *
 * <p>A missing key is a programming error, not a runtime condition: the application
 * verifies at startup that every key in
 * {@link com.urbancanopy.coefficient.CoefficientKeys#REQUIRED_KEYS} exists, and refuses to
 * start otherwise. {@link #require(String)} therefore fails loudly rather than silently
 * substituting a default that would make every subsequent figure quietly wrong.</p>
 */
public record CoefficientSet(Map<String, BigDecimal> values) {

    public CoefficientSet {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static CoefficientSet of(Map<String, BigDecimal> values) {
        return new CoefficientSet(values);
    }

    /**
     * The value for {@code key}.
     *
     * @throws IllegalStateException if the key is absent — see the class comment on why
     *                               this is preferable to a silent fallback
     */
    public BigDecimal require(String key) {
        BigDecimal value = values.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    "Environmental coefficient '" + key + "' is missing from the registry. "
                            + "The application should have refused to start; check the startup "
                            + "verification in CoefficientService.");
        }
        return value;
    }

    /** The value for {@code key}, if present. */
    public Optional<BigDecimal> find(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** The value for {@code key} as an {@code int}, for counts such as years. */
    public int requireInt(String key) {
        return require(key).intValue();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public int size() {
        return values.size();
    }

    /** A stable, ordered copy suitable for serialising into a result snapshot. */
    public Map<String, BigDecimal> asOrderedMap() {
        return new LinkedHashMap<>(new java.util.TreeMap<>(values));
    }
}
