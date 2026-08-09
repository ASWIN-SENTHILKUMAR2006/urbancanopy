package com.urbancanopy.coefficient.service;

import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.coefficient.entity.EnvironmentalCoefficient;
import com.urbancanopy.coefficient.repository.EnvironmentalCoefficientRepository;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the environmental coefficient registry: resolves it, caches it, validates changes
 * to it, and verifies at startup that it is complete.
 *
 * <h2>Caching</h2>
 * <p>The registry is around twenty rows, read on every single calculation and written
 * perhaps a handful of times a year. It is held in a single immutable
 * {@link CoefficientSet} behind an {@link AtomicReference} and replaced wholesale on any
 * change, so readers never see a half-updated set and never take a lock. This is why
 * coefficient edits take effect without a restart (FR-ADM-05) while calculations stay
 * fast (NFR-01).</p>
 *
 * <h2>Startup verification</h2>
 * <p>On application ready, every key in {@link CoefficientKeys#REQUIRED_KEYS} is checked
 * to exist and the scoring weights are checked to sum to 1.0. If either fails the
 * application <strong>refuses to start</strong>. The alternative — starting anyway and
 * substituting a default — would produce plausible-looking figures that are quietly wrong,
 * which for an estimation tool is the worst possible failure mode. A system that will not
 * start is a problem; a system that reports confident, incorrect carbon figures is a
 * liability.</p>
 */
@Service
public class CoefficientService {

    private static final Logger log = LoggerFactory.getLogger(CoefficientService.class);

    /** Tolerance for the scoring weights summing to 1.0 (BR-20). */
    private static final BigDecimal WEIGHT_SUM_TOLERANCE = new BigDecimal("0.001");

    private final EnvironmentalCoefficientRepository repository;

    /** Immutable snapshot, swapped atomically. Readers never block. */
    private final AtomicReference<CoefficientSet> cache = new AtomicReference<>();

    public CoefficientService(EnvironmentalCoefficientRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------- resolution

    /**
     * The coefficient set currently in force.
     *
     * <p>Loaded from the database on first use, then served from memory until an edit
     * invalidates it.</p>
     */
    @Transactional(readOnly = true)
    public CoefficientSet activeSet() {
        CoefficientSet cached = cache.get();
        if (cached != null) {
            return cached;
        }
        CoefficientSet loaded = loadFromDatabase();
        cache.set(loaded);
        return loaded;
    }

    /** Discards the cache so the next read reloads from the database. */
    public void invalidate() {
        cache.set(null);
        log.debug("Coefficient cache invalidated");
    }

    private CoefficientSet loadFromDatabase() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        repository.findAll().forEach(c -> values.put(c.getCoefficientKey(), c.getValue()));
        log.debug("Loaded {} environmental coefficients", values.size());
        return CoefficientSet.of(values);
    }

    // ------------------------------------------------------------------- read access

    @Transactional(readOnly = true)
    public List<EnvironmentalCoefficient> findAll() {
        return repository.findAllByOrderByCategoryAscCoefficientKeyAsc();
    }

    @Transactional(readOnly = true)
    public EnvironmentalCoefficient findByKey(String coefficientKey) {
        return repository.findByCoefficientKey(coefficientKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Environmental coefficient '" + coefficientKey + "' was not found."));
    }

    @Transactional(readOnly = true)
    public EnvironmentalCoefficient findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Environmental coefficient", id));
    }

    // ------------------------------------------------------------------ modification

    /**
     * Updates one coefficient's value (FR-ADM-04).
     *
     * <p>Rejects a value outside the coefficient's own declared range, and rejects a change
     * to a scoring weight that would break the "weights sum to 1.0" invariant (BR-20). The
     * cache is invalidated on success so the next calculation uses the new value
     * immediately.</p>
     *
     * @throws BusinessRuleViolationException if the value is out of range or breaks BR-20
     */
    @Transactional
    public EnvironmentalCoefficient updateValue(Long id, BigDecimal newValue) {
        EnvironmentalCoefficient coefficient = findById(id);

        if (!coefficient.isWithinRange(newValue)) {
            throw new BusinessRuleViolationException(
                    "FR-ADM-05",
                    "'%s' must be within %s. The submitted value %s is outside that range."
                            .formatted(coefficient.getLabel(), coefficient.describeRange(),
                                    newValue == null ? "(none)" : newValue.toPlainString()));
        }

        BigDecimal previousValue = coefficient.getValue();
        coefficient.setValue(newValue);

        if (isScoringWeight(coefficient.getCoefficientKey())) {
            assertScoringWeightsSumToOne(coefficient.getCoefficientKey(), newValue);
        }

        repository.saveAndFlush(coefficient);
        invalidate();

        log.info("Coefficient '{}' changed from {} to {}",
                coefficient.getCoefficientKey(), previousValue, newValue);
        return coefficient;
    }

    /** Restores one coefficient to its seeded default (FR-ADM-06). */
    @Transactional
    public EnvironmentalCoefficient restoreDefault(Long id) {
        EnvironmentalCoefficient coefficient = findById(id);
        BigDecimal previousValue = coefficient.getValue();
        coefficient.restoreDefault();
        repository.saveAndFlush(coefficient);
        invalidate();

        log.info("Coefficient '{}' restored from {} to its default {}",
                coefficient.getCoefficientKey(), previousValue, coefficient.getDefaultValue());
        return coefficient;
    }

    // -------------------------------------------------------------------- validation

    private boolean isScoringWeight(String coefficientKey) {
        return Arrays.asList(CoefficientKeys.SCORING_WEIGHTS).contains(coefficientKey);
    }

    /**
     * Verifies the four scoring weights still sum to 1.0 (BR-20).
     *
     * <p>{@code pendingKey}/{@code pendingValue} describe the change being made, so the
     * check is performed against the state that <em>would</em> result rather than the state
     * currently in the database.</p>
     */
    private void assertScoringWeightsSumToOne(String pendingKey, BigDecimal pendingValue) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String weightKey : CoefficientKeys.SCORING_WEIGHTS) {
            BigDecimal value = weightKey.equals(pendingKey)
                    ? pendingValue
                    : repository.findByCoefficientKey(weightKey)
                    .map(EnvironmentalCoefficient::getValue)
                    .orElse(BigDecimal.ZERO);
            sum = sum.add(value);
        }

        if (sum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
            throw new BusinessRuleViolationException(
                    "BR-20",
                    ("Impact score weights must sum to 1.00. This change would make them total %s. "
                            + "Adjust the other weights before saving.")
                            .formatted(sum.setScale(3, RoundingMode.HALF_UP).toPlainString()));
        }
    }

    // ------------------------------------------------------------- startup integrity

    /**
     * Verifies the registry is complete and internally consistent before the application
     * serves any traffic.
     *
     * @throws IllegalStateException if a required coefficient is missing or the scoring
     *                               weights do not sum to 1.0
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void verifyRegistryOnStartup() {
        CoefficientSet active = activeSet();

        List<String> missing = new ArrayList<>();
        for (String requiredKey : CoefficientKeys.REQUIRED_KEYS) {
            if (!active.contains(requiredKey)) {
                missing.add(requiredKey);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("""
                    The environmental coefficient registry is incomplete. \
                    The calculation engine cannot produce correct figures without these keys: %s. \
                    Check that migration V2__seed_reference_data.sql ran successfully."""
                    .formatted(String.join(", ", missing)));
        }

        BigDecimal weightSum = Arrays.stream(CoefficientKeys.SCORING_WEIGHTS)
                .map(active::require)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (weightSum.subtract(BigDecimal.ONE).abs().compareTo(WEIGHT_SUM_TOLERANCE) > 0) {
            throw new IllegalStateException(
                    "Impact score weights must sum to 1.00 but total %s. Correct them in the "
                            .formatted(weightSum.toPlainString())
                            + "environmental_coefficients table before starting.");
        }

        log.info("Environmental coefficient registry verified — {} coefficients loaded, "
                + "all {} required keys present, scoring weights sum to {}",
                active.size(), CoefficientKeys.REQUIRED_KEYS.length, weightSum.toPlainString());
    }
}
