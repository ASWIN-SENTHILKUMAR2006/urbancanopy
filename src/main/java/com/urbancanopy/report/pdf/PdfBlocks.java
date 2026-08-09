package com.urbancanopy.report.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;

/**
 * The repeated pieces a report is assembled from: section headings, KPI strips, bars, notes.
 *
 * <h2>Charts are drawn, not embedded</h2>
 * <p>FR-RPT-03 asks for the dashboard's charts in the report. They are redrawn here as PDF
 * vector graphics rather than screenshotted from the browser's canvas, for three reasons: a
 * vector bar stays sharp at any zoom and in print, where a 96-dpi PNG does not; the server can
 * produce the document on its own without a browser round-trip to collect images; and the
 * report of a plan is then reproducible from the plan alone, which is what makes it evidence.</p>
 *
 * <p>The bars use the same one-hue-per-chart rule the screen does, and every bar carries its
 * figure beside it — the bar is never the only place the number appears.</p>
 */
final class PdfBlocks {

    /** Usable width between the page margins on A4. */
    static final float CONTENT_WIDTH = 595f - (2 * 42f);

    // ------------------------------------------------------------------------ headings

    static Paragraph sectionHeading(String text) {
        Paragraph heading = new Paragraph(text, PdfTheme.h1());
        heading.setSpacingBefore(18);
        heading.setSpacingAfter(8);
        return heading;
    }

    static Paragraph subHeading(String text) {
        Paragraph heading = new Paragraph(text, PdfTheme.h2());
        heading.setSpacingBefore(12);
        heading.setSpacingAfter(6);
        return heading;
    }

    static Paragraph body(String text) {
        Paragraph paragraph = new Paragraph(text, PdfTheme.body());
        paragraph.setSpacingAfter(6);
        return paragraph;
    }

    static Paragraph note(String text) {
        Paragraph paragraph = new Paragraph(text, PdfTheme.small());
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    static Paragraph spacer(float height) {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setSpacingAfter(height);
        return paragraph;
    }

    // ---------------------------------------------------------------------- KPI strip

    /**
     * The headline figures, as a row of tinted tiles.
     *
     * <p>Each tile carries a coloured rule on its left in the metric's own colour — the same
     * colour that metric wears on screen.</p>
     */
    static PdfPTable kpiStrip(Kpi... kpis) {
        PdfPTable table = new PdfPTable(kpis.length);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(10);

        for (Kpi kpi : kpis) {
            PdfPTable tile = new PdfPTable(1);
            tile.setWidthPercentage(100);

            PdfPCell label = new PdfPCell(new Phrase(kpi.label().toUpperCase(), PdfTheme.tableHeader()));
            label.setBorder(Rectangle.NO_BORDER);
            label.setPaddingBottom(2);
            tile.addCell(label);

            Phrase value = new Phrase();
            value.add(new Chunk(kpi.value(), PdfTheme.metric(kpi.colour())));
            if (kpi.unit() != null) {
                value.add(new Chunk(" " + kpi.unit(), PdfTheme.bodySecondary()));
            }
            PdfPCell valueCell = new PdfPCell(value);
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setPaddingBottom(2);
            tile.addCell(valueCell);

            PdfPCell note = new PdfPCell(new Phrase(kpi.note() == null ? " " : kpi.note(), PdfTheme.smallMuted()));
            note.setBorder(Rectangle.NO_BORDER);
            tile.addCell(note);

            PdfPCell holder = new PdfPCell(tile);
            holder.setBorder(Rectangle.LEFT);
            holder.setBorderColorLeft(kpi.colour());
            holder.setBorderWidthLeft(3);
            holder.setPadding(8);
            holder.setBackgroundColor(PdfTheme.SURFACE_ALT);
            table.addCell(holder);
        }

        return table;
    }

    /** One headline figure. */
    record Kpi(String label, String value, String unit, String note, Color colour) {}

    // ---------------------------------------------------------------------- bar charts

    /**
     * A labelled horizontal bar chart.
     *
     * <p>Three columns: the category, the bar, and the figure. The bar itself is a nested
     * two-cell table whose column widths are the filled and unfilled proportions — which is
     * how a rectangle of an exact proportional width is drawn in a PDF without any coordinate
     * arithmetic.</p>
     *
     * @param rows label, value and display text per bar
     * @param colour the single hue for every bar in this chart
     */
    static PdfPTable barChart(java.util.List<BarRow> rows, Color colour) {
        double max = rows.stream()
                .mapToDouble(row -> row.value() == null ? 0 : row.value().doubleValue())
                .max()
                .orElse(0);

        PdfPTable table = new PdfPTable(new float[]{28f, 52f, 20f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);

        for (BarRow row : rows) {
            PdfPCell label = new PdfPCell(new Phrase(row.label(), PdfTheme.tableBody()));
            label.setBorder(Rectangle.NO_BORDER);
            label.setVerticalAlignment(Element.ALIGN_MIDDLE);
            label.setPaddingBottom(4);
            table.addCell(label);

            PdfPCell bar = new PdfPCell(bar(fraction(row.value(), max), colour));
            bar.setBorder(Rectangle.NO_BORDER);
            bar.setVerticalAlignment(Element.ALIGN_MIDDLE);
            bar.setPaddingBottom(4);
            bar.setPaddingLeft(4);
            bar.setPaddingRight(4);
            table.addCell(bar);

            PdfPCell value = new PdfPCell(new Phrase(row.display(), PdfTheme.tableBody()));
            value.setBorder(Rectangle.NO_BORDER);
            value.setHorizontalAlignment(Element.ALIGN_RIGHT);
            value.setVerticalAlignment(Element.ALIGN_MIDDLE);
            value.setPaddingBottom(4);
            table.addCell(value);
        }

        return table;
    }

    /** One bar. */
    record BarRow(String label, BigDecimal value, String display) {}

    /**
     * A single proportional bar.
     *
     * <p>The filled and empty halves are separate columns, so the boundary between them is
     * exact. Both are clamped away from zero because a PDF table column of zero width is
     * rejected outright.</p>
     */
    private static PdfPTable bar(double fraction, Color colour) {
        float filled = (float) Math.max(0.008, Math.min(0.992, fraction));
        float empty = 1f - filled;

        PdfPTable bar = new PdfPTable(new float[]{filled, empty});
        bar.setWidthPercentage(100);

        PdfPCell fill = new PdfPCell(new Phrase(" ", PdfTheme.smallMuted()));
        fill.setBackgroundColor(colour);
        fill.setBorder(Rectangle.NO_BORDER);
        fill.setFixedHeight(9);
        bar.addCell(fill);

        PdfPCell rest = new PdfPCell(new Phrase(" ", PdfTheme.smallMuted()));
        rest.setBackgroundColor(PdfTheme.SURFACE_ALT);
        rest.setBorder(Rectangle.NO_BORDER);
        rest.setFixedHeight(9);
        bar.addCell(rest);

        return bar;
    }

    private static double fraction(BigDecimal value, double max) {
        if (value == null || max <= 0) {
            return 0;
        }
        return value.doubleValue() / max;
    }

    // -------------------------------------------------------------------------- tables

    /** Builds a table with the given relative column widths and header labels. */
    static PdfPTable table(float[] widths, String... headers) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);
        // Repeat the header when a long table breaks across pages, so page 2 is still readable.
        table.setHeaderRows(1);

        for (String header : headers) {
            table.addCell(PdfTheme.headerCell(header));
        }
        return table;
    }

    /** A two-column definition list, for summary blocks. */
    static PdfPTable definitions(String... labelsAndValues) {
        PdfPTable table = new PdfPTable(new float[]{38f, 62f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(8);

        for (int i = 0; i < labelsAndValues.length; i += 2) {
            PdfPCell label = new PdfPCell(new Phrase(labelsAndValues[i], PdfTheme.small()));
            label.setBorder(Rectangle.BOTTOM);
            label.setBorderColor(PdfTheme.BORDER);
            label.setBorderWidth(0.5f);
            label.setPadding(5);
            table.addCell(label);

            PdfPCell value = new PdfPCell(new Phrase(labelsAndValues[i + 1], PdfTheme.tableBody()));
            value.setBorder(Rectangle.BOTTOM);
            value.setBorderColor(PdfTheme.BORDER);
            value.setBorderWidth(0.5f);
            value.setPadding(5);
            table.addCell(value);
        }
        return table;
    }

    /**
     * A tinted callout — the disclaimer, a warning, a verdict.
     *
     * <p>The disclaimer appears in one of these on every report, because FR-CALC-08 requires
     * computed figures never to travel without it.</p>
     */
    static PdfPTable callout(String text, Color accent, Color background) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(8);

        PdfPCell cell = new PdfPCell(new Phrase(text, PdfTheme.small()));
        cell.setBackgroundColor(background);
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColorLeft(accent);
        cell.setBorderWidthLeft(3);
        cell.setPadding(8);
        table.addCell(cell);

        return table;
    }

    private PdfBlocks() {
        throw new AssertionError("Utility class");
    }
}
