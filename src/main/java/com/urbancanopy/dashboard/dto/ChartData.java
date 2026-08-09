package com.urbancanopy.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One chart's data, in the shape the chart is actually drawn from.
 *
 * <h2>Why every chart here is a single series</h2>
 * <p>All four dashboard charts answer the same kind of question — "compare these magnitudes"
 * — and for that job the reader gets identity from the category label, not from a colour. A
 * palette that gave each pollutant its own hue would be repeating what the axis already says,
 * and it would do so with five warm hues that cannot be told apart reliably: measured on the
 * project's own metric colours, PM2.5 against PM10 scores ΔE 9.4 for normal vision against a
 * floor of 15, and three of the five read as grey. So these charts carry one hue and let
 * length do the work. Colour returns as a carrier of identity only on the comparison screen,
 * where the series genuinely are the subject.</p>
 *
 * @param key         stable identifier, matching the path segment that produced it
 * @param unit        the unit every value is expressed in — the client never guesses
 * @param colourToken the single design-system token the marks are drawn in
 * @param footnote    optional note, e.g. that a tail of small categories was folded into
 *                    "Other" — a truncation the reader is told about rather than left to
 *                    infer from a total that does not add up
 */
public record ChartData(
        String key,
        String title,
        String subtitle,
        String unit,
        String colourToken,
        List<Point> points,
        String footnote
) {

    /**
     * One labelled magnitude.
     *
     * @param secondaryLabel optional context shown beside the label — a scientific name, a
     *                       calendar year — kept separate so the chart can render it at a
     *                       different weight rather than concatenating it into the label
     */
    public record Point(String label, String secondaryLabel, BigDecimal value) {

        public static Point of(String label, BigDecimal value) {
            return new Point(label, null, value);
        }
    }
}
