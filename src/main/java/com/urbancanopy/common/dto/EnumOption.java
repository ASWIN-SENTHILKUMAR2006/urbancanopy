package com.urbancanopy.common.dto;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * A selectable value and its human-readable label.
 *
 * <p>Enum labels are defined once, on the enum, and served to the browser through this
 * type. The alternative — hardcoding "Air quality improvement" in a JavaScript lookup
 * table — guarantees that the day someone adds a plantation purpose, the interface shows
 * {@code AIR_QUALITY_IMPROVEMENT} to a user until a second, unrelated file is remembered.</p>
 *
 * @param value       the enum constant name, sent back on submission
 * @param label       display text
 * @param description optional explanatory text for a tooltip or helper line
 */
public record EnumOption(String value, String label, String description) {

    public static EnumOption of(String value, String label) {
        return new EnumOption(value, label, null);
    }

    /** Maps every constant of an enum into options. */
    public static <E extends Enum<E>> List<EnumOption> from(
            Class<E> enumType, Function<E, String> labeller) {
        return from(enumType, labeller, constant -> null);
    }

    /** Maps every constant of an enum into options, with descriptions. */
    public static <E extends Enum<E>> List<EnumOption> from(
            Class<E> enumType,
            Function<E, String> labeller,
            Function<E, String> describer) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> new EnumOption(
                        constant.name(), labeller.apply(constant), describer.apply(constant)))
                .toList();
    }
}
