package com.urbancanopy.report.pdf;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;

import java.awt.Color;

/**
 * The report's visual language, in one place.
 *
 * <p>The colours here are the same design tokens the interface uses (design system §2), so a
 * figure printed in a report is the colour the planner saw on screen. That consistency is the
 * whole point of fixing one colour per metric: a reader who scanned the dashboard can scan the
 * PDF the same way.</p>
 *
 * <p>Light-mode values only — paper has one theme.</p>
 */
final class PdfTheme {

    // ------------------------------------------------------------------------- colour

    static final Color INK = new Color(0x13, 0x21, 0x19);
    static final Color INK_SECONDARY = new Color(0x4F, 0x61, 0x57);
    static final Color INK_MUTED = new Color(0x78, 0x8A, 0x7F);

    static final Color PRIMARY = new Color(0x1B, 0x6B, 0x47);
    static final Color PRIMARY_SUBTLE = new Color(0xE4, 0xF1, 0xE9);

    static final Color BORDER = new Color(0xDC, 0xE4, 0xDD);
    static final Color SURFACE_ALT = new Color(0xF8, 0xFB, 0xF8);

    static final Color CARBON = new Color(0x1B, 0x6B, 0x47);
    static final Color OXYGEN = new Color(0x2A, 0x7D, 0xA8);
    static final Color POLLUTANT = new Color(0xB4, 0x69, 0x1F);
    static final Color CANOPY = new Color(0x5C, 0x8A, 0x3A);

    static final Color SUCCESS = new Color(0x2E, 0x7D, 0x32);
    static final Color WARNING = new Color(0xA6, 0x5F, 0x00);
    static final Color DANGER = new Color(0xB3, 0x26, 0x1E);
    static final Color INFO = new Color(0x15, 0x65, 0xA0);

    // --------------------------------------------------------------------------- type

    /**
     * Helvetica throughout.
     *
     * <p>One of the fourteen fonts every PDF reader is required to have, so the document
     * renders identically everywhere without embedding a font file. Inter — the interface's
     * face — would have to be embedded, and a missing glyph in a scientific report is worse
     * than a slightly different sans.</p>
     */
    private static final String FAMILY = FontFactory.HELVETICA;
    private static final String FAMILY_BOLD = FontFactory.HELVETICA_BOLD;
    private static final String FAMILY_OBLIQUE = FontFactory.HELVETICA_OBLIQUE;

    static Font display() {
        return FontFactory.getFont(FAMILY_BOLD, 24, INK);
    }

    static Font h1() {
        return FontFactory.getFont(FAMILY_BOLD, 15, PRIMARY);
    }

    static Font h2() {
        return FontFactory.getFont(FAMILY_BOLD, 11, INK);
    }

    static Font body() {
        return FontFactory.getFont(FAMILY, 9.5f, INK);
    }

    static Font bodySecondary() {
        return FontFactory.getFont(FAMILY, 9.5f, INK_SECONDARY);
    }

    static Font small() {
        return FontFactory.getFont(FAMILY, 8, INK_SECONDARY);
    }

    static Font smallMuted() {
        return FontFactory.getFont(FAMILY, 8, INK_MUTED);
    }

    static Font italic() {
        return FontFactory.getFont(FAMILY_OBLIQUE, 9, INK_SECONDARY);
    }

    static Font tableHeader() {
        return FontFactory.getFont(FAMILY_BOLD, 8, INK_SECONDARY);
    }

    static Font tableBody() {
        return FontFactory.getFont(FAMILY, 8.5f, INK);
    }

    static Font tableBodyBold() {
        return FontFactory.getFont(FAMILY_BOLD, 8.5f, INK);
    }

    static Font metric(Color colour) {
        return FontFactory.getFont(FAMILY_BOLD, 18, colour);
    }

    // -------------------------------------------------------------------------- cells

    /** A column heading: small, uppercase-feeling, on a tinted band. */
    static PdfPCell headerCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableHeader()));
        cell.setBackgroundColor(SURFACE_ALT);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(5);
        return cell;
    }

    static PdfPCell cell(String text) {
        return cell(text, Element.ALIGN_LEFT, tableBody());
    }

    static PdfPCell numericCell(String text) {
        return cell(text, Element.ALIGN_RIGHT, tableBody());
    }

    static PdfPCell totalCell(String text, int alignment) {
        PdfPCell cell = cell(text, alignment, tableBodyBold());
        cell.setBackgroundColor(SURFACE_ALT);
        return cell;
    }

    static PdfPCell cell(String text, int alignment, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "—" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(5);
        return cell;
    }

    /** A cell with no rule at all — used to lay out blocks rather than tabulate data. */
    static PdfPCell layoutCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfTheme() {
        throw new AssertionError("Utility class");
    }
}
