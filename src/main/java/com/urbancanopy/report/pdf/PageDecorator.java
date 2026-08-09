package com.urbancanopy.report.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Draws the running header and footer on every page, including "Page X of Y" (FR-RPT-04).
 *
 * <h2>How "of Y" is possible</h2>
 * <p>The total page count is not known while the document is being written — it is only known
 * when the document ends. The usual workaround is to render the whole document twice. This
 * class avoids that with the standard PDF technique: a small {@link PdfTemplate} is reserved on
 * each page as a placeholder, and the real total is painted into every one of them in
 * {@link #onCloseDocument}, once it is finally known. One pass, exact numbering.</p>
 *
 * <p>The cover page is left undecorated — a header above a title page reads as a mistake — by
 * skipping page 1.</p>
 */
class PageDecorator extends PdfPageEventHelper {

    private static final float MARGIN = 42f;

    private final String documentTitle;
    private final String reportId;
    private final String generatedAt;

    /** Placeholder into which the final page total is painted when the document closes. */
    private PdfTemplate totalPages;

    PageDecorator(String documentTitle, String reportId, String generatedAt) {
        this.documentTitle = documentTitle;
        this.reportId = reportId;
        this.generatedAt = generatedAt;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        // 40×12 points is enough for "of 12" at 8pt.
        totalPages = writer.getDirectContent().createTemplate(40, 12);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        int page = writer.getPageNumber();
        if (page == 1) {
            return; // the cover carries its own identity
        }

        PdfContentByte canvas = writer.getDirectContent();
        Rectangle size = document.getPageSize();

        // ---------------------------------------------------------------------- header
        float headerY = size.getHeight() - MARGIN + 16;

        canvas.beginText();
        canvas.setFontAndSize(PdfTheme.small().getBaseFont(), 8);
        canvas.setColorFill(PdfTheme.INK_MUTED);
        canvas.setTextMatrix(MARGIN, headerY);
        canvas.showText(documentTitle);
        canvas.endText();

        canvas.beginText();
        canvas.setFontAndSize(PdfTheme.small().getBaseFont(), 8);
        canvas.setColorFill(PdfTheme.INK_MUTED);
        String right = "UrbanCanopy · " + reportId;
        float rightWidth = PdfTheme.small().getBaseFont().getWidthPoint(right, 8);
        canvas.setTextMatrix(size.getWidth() - MARGIN - rightWidth, headerY);
        canvas.showText(right);
        canvas.endText();

        canvas.setColorStroke(PdfTheme.BORDER);
        canvas.setLineWidth(0.5f);
        canvas.moveTo(MARGIN, headerY - 5);
        canvas.lineTo(size.getWidth() - MARGIN, headerY - 5);
        canvas.stroke();

        // ---------------------------------------------------------------------- footer
        float footerY = MARGIN - 18;

        canvas.setColorStroke(PdfTheme.BORDER);
        canvas.moveTo(MARGIN, footerY + 12);
        canvas.lineTo(size.getWidth() - MARGIN, footerY + 12);
        canvas.stroke();

        canvas.beginText();
        canvas.setFontAndSize(PdfTheme.smallMuted().getBaseFont(), 8);
        canvas.setColorFill(PdfTheme.INK_MUTED);
        canvas.setTextMatrix(MARGIN, footerY);
        canvas.showText("Generated " + generatedAt + " · estimates, not certified measurements");
        canvas.endText();

        // "Page X of " is written now; the template holding "Y" is placed beside it and
        // filled in at close.
        String pageLabel = "Page " + page + " of ";
        float labelWidth = PdfTheme.smallMuted().getBaseFont().getWidthPoint(pageLabel, 8);
        float totalWidth = 22f;
        float startX = size.getWidth() - MARGIN - labelWidth - totalWidth;

        canvas.beginText();
        canvas.setFontAndSize(PdfTheme.smallMuted().getBaseFont(), 8);
        canvas.setColorFill(PdfTheme.INK_MUTED);
        canvas.setTextMatrix(startX, footerY);
        canvas.showText(pageLabel);
        canvas.endText();

        canvas.addTemplate(totalPages, startX + labelWidth, footerY);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        // getPageNumber() reports the page the writer would write NEXT, not the last page it
        // wrote — so at close it is one past the total. Without the correction every report
        // ends "Page 5 of 6", which is plausible enough to survive a careless review.
        String total = String.valueOf(writer.getPageNumber() - 1);

        totalPages.beginText();
        totalPages.setFontAndSize(PdfTheme.smallMuted().getBaseFont(), 8);
        totalPages.setColorFill(PdfTheme.INK_MUTED);
        totalPages.setTextMatrix(0, 0);
        totalPages.showText(total);
        totalPages.endText();
    }

    /** The margin every page uses, exposed so the document is created with matching margins. */
    static float margin() {
        return MARGIN;
    }

    /** Alignment constant kept local so callers need not import iText's Element. */
    static int alignRight() {
        return Element.ALIGN_RIGHT;
    }

    /** Convenience for a blank paragraph phrase. */
    static Phrase blank() {
        return new Phrase(" ");
    }
}
