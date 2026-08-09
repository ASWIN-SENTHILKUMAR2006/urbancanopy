package com.urbancanopy.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed, validated binding of every tunable application value declared under the
 * {@code app.*} key in {@code application.yml}.
 *
 * <p>This type is the single reason no operational constant needs to appear as a literal
 * in Java code. Values that are <em>scientific</em> rather than operational — survival
 * rates, equivalence factors, scoring weights — deliberately do not live here: they live
 * in the {@code environmental_coefficients} table so that an administrator can change
 * them at runtime without a redeploy (FR-ADM-04, BR-15).</p>
 *
 * <p>Validation is applied at startup: a malformed configuration fails fast rather than
 * producing wrong estimates later.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(

        @NotBlank String name,
        @NotBlank String version,

        /** Stamped onto each result snapshot so a historical report records its engine. */
        @NotBlank String engineVersion,

        /** Mandatory estimation disclaimer shown wherever computed values appear (FR-CALC-08). */
        @NotBlank String disclaimer,

        @NotNull @Valid Pagination pagination,
        @NotNull @Valid Upload upload,
        @NotNull @Valid PasswordPolicy passwordPolicy,
        @NotNull @Valid Plan plan,
        @NotNull @Valid Security security
) {

    /** Default and maximum page sizes applied to every paginated endpoint (NFR-02). */
    public record Pagination(
            @Min(1) @Max(200) int defaultPageSize,
            @Min(1) @Max(500) int maxPageSize
    ) {
        /** Clamps a requested page size into the permitted range. */
        public int clamp(Integer requested) {
            if (requested == null || requested < 1) {
                return defaultPageSize;
            }
            return Math.min(requested, maxPageSize);
        }
    }

    /** Species image upload constraints (FR-SPEC-05). */
    public record Upload(
            @NotBlank String directory,
            @NotBlank String publicPath,
            @NotEmpty List<String> allowedContentTypes,
            @Min(1024) long maxFileSizeBytes
    ) {
        public boolean isContentTypeAllowed(String contentType) {
            return contentType != null
                    && allowedContentTypes.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(contentType));
        }
    }

    /** Password rules enforced at registration and password change (FR-AUTH-03). */
    public record PasswordPolicy(
            @Min(6) @Max(64) int minLength,
            @Min(8) @Max(200) int maxLength,
            boolean requireLetter,
            boolean requireDigit
    ) {}

    /** Structural bounds on a plantation plan (BR-09, BR-14). */
    public record Plan(
            @Min(1) int minProjectionYears,
            @Min(1) @Max(200) int maxProjectionYears,
            @Min(1) int maxQuantityPerItem,
            @Min(2) int minPlansPerComparison,
            @Min(2) @Max(10) int maxPlansPerComparison,
            @Min(1) int maxItemsPerPlan
    ) {}

    /**
     * Authentication settings (FR-AUTH-08, FR-AUTH-11).
     *
     * <p>{@code seedPassword} exists for one narrow purpose: the accounts created by the
     * seed migration carry a placeholder where their password hash belongs, because a
     * migration cannot hash a password and shipping a known hash in version control would be
     * a backdoor. On startup, any account still holding that placeholder is given this
     * password — so a fresh installation is usable — and the fact is logged loudly.</p>
     *
     * <p>It is blank in production by design. Left blank, the seeded accounts simply cannot
     * authenticate, which is the correct default: an installation with no usable
     * administrator is recoverable, one with a publicly known administrator password is not.</p>
     */
    public record Security(
            @NotBlank String bootstrapAdminEmail,
            String seedPassword,

            /** Failed attempts before an account is temporarily locked (FR-AUTH-11). */
            @Min(1) @Max(20) int maxFailedAttempts,

            /** How long that lock lasts. */
            @Min(1) @Max(1440) int lockMinutes
    ) {

        /** {@code true} when a seed password was supplied and placeholder accounts can be opened. */
        public boolean hasSeedPassword() {
            return seedPassword != null && !seedPassword.isBlank();
        }
    }
}
