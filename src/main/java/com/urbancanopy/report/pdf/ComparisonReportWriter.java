package com.urbancanopy.report.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.urbancanopy.plan.dto.PlanComparisonResponse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Composes the side-by-side comparison report for two to four plans (FR-RPT-05, FR-CMP-04).
 *
 * <p>Landscape, because the document is a table read across its rows and a portrait page would
 * squeeze four plan columns into unreadable widths.</p>
 *
 * <p>The normalised block is reproduced as prominently as the totals. On paper even more than
 * on screen, a reader skims the first table and stops: if that table were totals alone, the
 * report would consistently recommend whichever plan is largest while appearing to compare
 * their designs.</p>
 */
public class ComparisonReportWriter {

    private final PlanComparisonResponse comparison;
    private final String authorName;
    private final String reportId;
    private final String generatedAt;
    private final ReportFormatter fmt;

    public ComparisonReportWriter(PlanComparisonResponse comparison, String authorName,
                                  String reportId, String generatedAt, ReportFormatter formatter) {
        this.comparison = comparison;
        this.authorName = authorName;
        this.reportId = reportId;
        this.generatedAt = generatedAt;
        this.fmt = formatter;
    }

    public byte[] write() {
        Document document = new Document(PageSize.A4.rotate(),
                PageDecorator.margin(), PageDecorator.margin(),
                PageDecorator.margin() + 8, PageDecorator.margin() + 8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new PageDecorator("Plan comparison", reportId, generatedAt));

        document.addTitle("Plantation plan comparison");
        document.addAuthor(authorName);
        document.addCreator("UrbanCanopy");

        document.open();

        cover(document);
        for (PlanComparisonResponse.MetricGroup group : comparison.groups()) {
            group(document, group);
        }
        closing(document);

        document.close();
        return out.toByteArray();
    }

    private void cover(Document document) {
        Paragraph brand = new Paragraph("URBANCANOPY", PdfTheme.tableHeader());
        brand.setSpacingAfter(4);
        document.add(brand);

        Paragraph title = new Paragraph("Plantation plan comparison", PdfTheme.display());
        title.setSpacingAfter(10);
        document.add(title);

        PdfPTable table = PdfBlocks.table(
                new float[]{26f, 18f, 14f, 14f, 14f, 14f},
                "Plan", "Purpose", "Status", "Trees", "Land", "Horizon");

        for (PlanComparisonResponse.ComparedPlan plan : comparison.plans()) {
            PdfPCell name = new PdfPCell();
            name.setBorderColor(PdfTheme.BORDER);
            name.setBorderWidth(0.5f);
            name.setPadding(5);
            name.addElement(new Paragraph(plan.title(), PdfTheme.tableBodyBold()));
            if (plan.siteName() != null) {
                name.addElement(new Paragraph(plan.siteName(), PdfTheme.smallMuted()));
            }
            table.addCell(name);

            table.addCell(PdfTheme.cell(plan.purpose().label()));
            table.addCell(PdfTheme.cell(plan.status().label()));
            table.addCell(PdfTheme.numericCell(fmt.count(plan.totalTrees())));
            table.addCell(PdfTheme.numericCell(fmt.area(plan.landAreaSquareMetres())));
            table.addCell(PdfTheme.numericCell(plan.projectionYears() + " yr"));
        }
        document.add(table);

        document.add(PdfBlocks.definitions(
                "Prepared by", authorName,
                "Generated", generatedAt,
                "Report reference", reportId,
                "Basis", "All plans evaluated against the current coefficient registry, so the "
                        + "columns are directly comparable."));

        document.add(PdfBlocks.callout(comparison.disclaimer(), PdfTheme.INK_MUTED, PdfTheme.SURFACE_ALT));
    }

    private void group(Document document, PlanComparisonResponse.MetricGroup group) {
        document.add(PdfBlocks.sectionHeading(group.label()));
        document.add(PdfBlocks.note(group.description()));

        int planCount = comparison.plans().size();
        float[] widths = new float[planCount + 1];
        widths[0] = 26f;
        for (int i = 1; i <= planCount; i++) {
            widths[i] = 74f / planCount;
        }

        String[] headers = new String[planCount + 1];
        headers[0] = "Metric";
        for (int i = 0; i < planCount; i++) {
            headers[i + 1] = comparison.plans().get(i).title();
        }

        PdfPTable table = PdfBlocks.table(widths, headers);

        for (PlanComparisonResponse.Metric metric : group.metrics()) {
            String label = metric.unit() == null || metric.unit().isBlank()
                    ? metric.label()
                    : metric.label() + " (" + metric.unit() + ")";
            table.addCell(PdfTheme.cell(label));

            List<BigDecimal> values = metric.values();
            for (int i = 0; i < planCount; i++) {
                BigDecimal value = i < values.size() ? values.get(i) : null;
                boolean best = metric.bestPlanId() != null
                        && comparison.plans().get(i).id().equals(metric.bestPlanId());

                // The star is the only marker: colour alone would not survive a monochrome
                // print, which is exactly how a report like this tends to be read.
                String text = value == null ? "—" : fmt.number(value, 2);
                PdfPCell cell = PdfTheme.cell(
                        best ? "★ " + text : text,
                        Element.ALIGN_RIGHT,
                        best ? PdfTheme.tableBodyBold() : PdfTheme.tableBody());
                if (best) {
                    cell.setBackgroundColor(PdfTheme.PRIMARY_SUBTLE);
                }
                table.addCell(cell);
            }
        }

        document.add(table);
    }

    private void closing(Document document) {
        document.add(PdfBlocks.note("★ marks the best value on that row. On the totals, "
                + "\"best\" means the largest figure — which the largest plan will usually win. "
                + "The normalised rows are what compare the designs."));

        Paragraph closing = new Paragraph(
                "Report " + reportId + " · generated " + generatedAt, PdfTheme.smallMuted());
        closing.setAlignment(Element.ALIGN_CENTER);
        closing.setSpacingBefore(14);
        document.add(closing);
    }
}
