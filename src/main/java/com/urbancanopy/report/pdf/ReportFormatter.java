package com.urbancanopy.report.pdf;

import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns figures into the text a report prints.
 *
 * <p>The printed counterpart of the browser's `format.js`, and it exists for the same reason:
 * one place decides how a number becomes text, so the same figure reads identically on screen
 * and on paper. A report that rounded differently from the screen it was generated from would
 * undermine both.</p>
 *
 * <p>Formatting is fixed to a neutral locale rather than the server's default. A report is a
 * document that gets emailed: it must not depend on the grouping separator of whichever machine
 * happened to produce it, or "1,234.5" becomes "1.234,5" for half its readers.</p>
 */
@Component
public class ReportFormatter {

    private static final Locale LOCALE = Locale.UK;

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LOCALE).withZone(ZoneId.systemDefault());

    private static final BigDecimal HECTARE_THRESHOLD = BigDecimal.valueOf(100_000);

    /** A decimal figure with thousands separators, or an em dash when absent. */
    public String number(BigDecimal value, int decimals) {
        if (value == null) {
            return "—";
        }
        return String.format(LOCALE, "%,." + decimals + "f", value.setScale(decimals, RoundingMode.HALF_UP));
    }

    /** A whole number. */
    public String count(long value) {
        return String.format(LOCALE, "%,d", value);
    }

    /** A figure exactly as stored, trailing zeros removed — for echoing what a planner typed. */
    public String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    public String percent(BigDecimal value) {
        return value == null ? "—" : number(value, 1) + " %";
    }

    /** Kilograms rendered as tonnes, which is the unit the report quotes carbon in. */
    public String tonnesFromKg(BigDecimal kilograms) {
        return kilograms == null ? "—" : number(Decimals.kilogramsToTonnes(kilograms), 2);
    }

    /** Area, switching to hectares once square metres stop being readable. */
    public String area(BigDecimal squareMetres) {
        if (squareMetres == null) {
            return "—";
        }
        return squareMetres.abs().compareTo(HECTARE_THRESHOLD) >= 0
                ? number(squareMetres.divide(BigDecimal.valueOf(10_000), 4, RoundingMode.HALF_UP), 2) + " ha"
                : number(squareMetres, 0) + " m²";
    }

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }
}
