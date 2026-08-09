package com.urbancanopy.report.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.plan.dto.EstimateResponse;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes the full environmental impact report for one plan (FR-RPT-01 … FR-RPT-04).
 *
 * <p>The section order follows FR-RPT-02 exactly: cover, summary, composition, per-species
 * contribution, aggregate impact, canopy and feasibility, projection, equivalences, methodology
 * appendix, disclaimer.</p>
 *
 * <p>This class composes a document from a result that has already been computed. It performs
 * no calculation of its own — a report that did its own arithmetic could disagree with the
 * screen the reader was just looking at.</p>
 */
public class PlanImpactReportWriter {

    private final EstimateResponse estimate;
    private final String authorName;
    private final String reportId;
    private final String generatedAt;
    private final ReportFormatter fmt;

    public PlanImpactReportWriter(EstimateResponse estimate, String authorName,
                                  String reportId, String generatedAt, ReportFormatter formatter) {
        this.estimate = estimate;
        this.authorName = authorName;
        this.reportId = reportId;
        this.generatedAt = generatedAt;
        this.fmt = formatter;
    }

    /** Renders the document and returns its bytes. */
    public byte[] write() {
        Document document = new Document(PageSize.A4,
                PageDecorator.margin(), PageDecorator.margin(),
                PageDecorator.margin() + 8, PageDecorator.margin() + 8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new PageDecorator(estimate.planTitle(), reportId, generatedAt));

        document.addTitle(estimate.planTitle() + " — environmental impact report");
        document.addAuthor(authorName);
        document.addCreator("UrbanCanopy");
        document.addSubject("Estimated environmental impact of an urban plantation plan");

        document.open();

        cover(document);
        document.newPage();

        CalculationResult result = estimate.result();

        planSummary(document);
        if (!result.sufficientData()) {
            // An empty plan is an ordinary draft state, not an error — but there is nothing
            // to report, and printing pages of zeroes would look like a finding.
            document.add(PdfBlocks.callout(
                    "This plan contains no species, so no environmental estimate can be produced. "
                            + "Add at least one species and generate the report again.",
                    PdfTheme.WARNING, new java.awt.Color(0xFB, 0xEF, 0xE0)));
            document.close();
            return out.toByteArray();
        }

        aggregateImpact(document, result);
        speciesComposition(document, result);
        speciesContribution(document, result);
        pollutants(document, result);
        canopyAndFeasibility(document, result);
        projection(document, result);
        equivalences(document, result);
        methodology(document, result);

        document.close();
        return out.toByteArray();
    }

    // --------------------------------------------------------------------------- cover

    private void cover(Document document) {
        document.add(PdfBlocks.spacer(90));

        Paragraph brand = new Paragraph("URBANCANOPY", PdfTheme.tableHeader());
        brand.setSpacingAfter(6);
        document.add(brand);

        Paragraph title = new Paragraph(estimate.planTitle(), PdfTheme.display());
        title.setSpacingAfter(6);
        document.add(title);

        Paragraph subtitle = new Paragraph("Estimated environmental impact", PdfTheme.h2());
        subtitle.setSpacingAfter(24);
        document.add(subtitle);

        List<String> facts = new ArrayList<>();
        if (estimate.siteName() != null) {
            facts.add("Site");
            facts.add(estimate.siteName());
        }
        facts.add("Purpose");
        facts.add(estimate.purpose().label());
        facts.add("Available land");
        facts.add(fmt.area(estimate.landAreaSquareMetres()));
        facts.add("Planting from");
        facts.add(String.valueOf(estimate.targetStartYear()));
        facts.add("Projection horizon");
        facts.add(estimate.projectionYears() + " years");
        facts.add("Status");
        facts.add(estimate.status().label());
        facts.add("Prepared by");
        facts.add(authorName);
        facts.add("Generated");
        facts.add(generatedAt);
        facts.add("Report reference");
        facts.add(reportId);

        document.add(PdfBlocks.definitions(facts.toArray(String[]::new)));

        // Which coefficient set produced these figures is a cover-page fact, not a footnote:
        // it is what tells a reader whether this document will still reconcile next year.
        document.add(PdfBlocks.callout(
                estimate.fromSnapshot()
                        ? "The figures in this report were fixed when the plan was completed on "
                          + fmt.dateTime(estimate.snapshotComputedAt()) + ", using calculation engine "
                          + estimate.engineVersion() + ". Later revisions to the coefficient registry "
                          + "do not change them, so this document will continue to reconcile."
                        : "This plan is not yet completed, so its figures were computed against the "
                          + "coefficient registry as it stands today, using calculation engine "
                          + estimate.engineVersion() + ". Completing the plan fixes them.",
                PdfTheme.INFO, new java.awt.Color(0xE4, 0xEF, 0xF8)));

        document.add(PdfBlocks.callout(estimate.result().disclaimer(),
                PdfTheme.INK_MUTED, PdfTheme.SURFACE_ALT));
    }

    // ------------------------------------------------------------------------- summary

    private void planSummary(Document document) {
        document.add(PdfBlocks.sectionHeading("1. Plan summary"));

        CalculationResult result = estimate.result();
        document.add(PdfBlocks.definitions(
                "Plan title", estimate.planTitle(),
                "Site", estimate.siteName() == null ? "—" : estimate.siteName(),
                "Purpose", estimate.purpose().label(),
                "Available land", fmt.area(estimate.landAreaSquareMetres())
                        + " (entered as " + fmt.plain(estimate.landAreaInput()) + " "
                        + estimate.landAreaUnit().description() + ")",
                "Trees planned", fmt.count(result.totalTrees()),
                "Effective trees", fmt.number(result.effectiveTrees(), 0)
                        + " after survival and maturity adjustment",
                "Species", fmt.count(result.diversity().speciesCount()),
                "Projection", estimate.projectionYears() + " years from " + estimate.targetStartYear()));
    }

    private void aggregateImpact(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("2. Aggregate impact"));

        document.add(PdfBlocks.kpiStrip(
                new PdfBlocks.Kpi("CO₂ / year", fmt.number(result.carbon().annualTonnes(), 2), "t",
                        fmt.number(result.carbon().horizonTonnes(), 1) + " t over the horizon", PdfTheme.CARBON),
                new PdfBlocks.Kpi("O₂ / year", fmt.number(result.oxygen().annualKg(), 0), "kg",
                        null, PdfTheme.OXYGEN),
                new PdfBlocks.Kpi("Pollutants / year", fmt.number(result.pollutants().totalKg(), 1), "kg",
                        "PM2.5, PM10, NO₂, SO₂, CO", PdfTheme.POLLUTANT),
                new PdfBlocks.Kpi("Impact score", fmt.number(result.impactScore().overall(), 0), "/100",
                        "diversity H′ " + fmt.number(result.diversity().shannonIndex(), 2), PdfTheme.PRIMARY)));

        document.add(PdfBlocks.subHeading("Score composition"));
        document.add(PdfBlocks.note(
                "The overall score is a weighted composite. The sub-scores show which part of the "
                        + "plan drives it, so the figure can be explained rather than merely asserted."));

        CalculationResult.ImpactScore score = result.impactScore();
        PdfPTable table = PdfBlocks.table(new float[]{40f, 20f, 20f, 20f},
                "Component", "Sub-score", "Weight", "Contribution");

        addScoreRow(table, "Carbon", score.carbonSubScore(), score.carbonWeight());
        addScoreRow(table, "Air quality", score.airQualitySubScore(), score.airQualityWeight());
        addScoreRow(table, "Canopy", score.canopySubScore(), score.canopyWeight());
        addScoreRow(table, "Diversity", score.diversitySubScore(), score.diversityWeight());

        table.addCell(PdfTheme.totalCell("Overall", Element.ALIGN_LEFT));
        table.addCell(PdfTheme.totalCell("", Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell("", Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(fmt.number(score.overall(), 2), Element.ALIGN_RIGHT));

        document.add(table);

        if (!result.warnings().isEmpty()) {
            for (String warning : result.warnings()) {
                document.add(PdfBlocks.callout(warning, PdfTheme.WARNING,
                        new java.awt.Color(0xFB, 0xEF, 0xE0)));
            }
        }
    }

    private void addScoreRow(PdfPTable table, String label, BigDecimal subScore, BigDecimal weight) {
        table.addCell(PdfTheme.cell(label));
        table.addCell(PdfTheme.numericCell(fmt.number(subScore, 2)));
        table.addCell(PdfTheme.numericCell(fmt.number(weight, 2)));
        table.addCell(PdfTheme.numericCell(
                fmt.number(subScore == null || weight == null ? null : subScore.multiply(weight), 2)));
    }

    // --------------------------------------------------------------------- composition

    private void speciesComposition(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("3. Species composition"));

        PdfPTable table = PdfBlocks.table(new float[]{30f, 30f, 13f, 13f, 14f},
                "Common name", "Scientific name", "Quantity", "Share", "Effective");

        for (CalculationResult.SpeciesContribution row : result.contributions()) {
            table.addCell(PdfTheme.cell(row.commonName()));
            table.addCell(PdfTheme.cell(row.scientificName(), Element.ALIGN_LEFT, PdfTheme.italic()));
            table.addCell(PdfTheme.numericCell(fmt.count(row.quantity())));
            table.addCell(PdfTheme.numericCell(fmt.percent(row.quantitySharePercent())));
            table.addCell(PdfTheme.numericCell(fmt.number(row.effectiveTrees(), 0)));
        }

        table.addCell(PdfTheme.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfTheme.totalCell("", Element.ALIGN_LEFT));
        table.addCell(PdfTheme.totalCell(fmt.count(result.totalTrees()), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell("100 %", Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.effectiveTrees(), 0), Element.ALIGN_RIGHT));

        document.add(table);
        document.add(PdfBlocks.note(
                "Effective trees = planted × expected survival rate × maturity factor. Every rate in "
                        + "the next section is derived from the effective count, not the planted count."));
    }

    private void speciesContribution(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("4. Environmental contribution by species"));

        PdfPTable table = PdfBlocks.table(new float[]{26f, 15f, 15f, 16f, 14f, 14f},
                "Species", "CO₂ t/yr", "O₂ kg/yr", "Pollutants g/yr", "Canopy m²", "CO₂ share");

        for (CalculationResult.SpeciesContribution row : result.contributions()) {
            table.addCell(PdfTheme.cell(row.commonName()));
            table.addCell(PdfTheme.numericCell(fmt.tonnesFromKg(row.annualCo2Kg())));
            table.addCell(PdfTheme.numericCell(fmt.number(row.annualO2Kg(), 0)));
            table.addCell(PdfTheme.numericCell(fmt.number(row.annualTotalPollutantGrams(), 0)));
            table.addCell(PdfTheme.numericCell(fmt.number(row.canopyAreaSquareMetres(), 0)));
            table.addCell(PdfTheme.numericCell(fmt.percent(row.co2SharePercent())));
        }

        table.addCell(PdfTheme.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.carbon().annualTonnes(), 2), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.oxygen().annualKg(), 0), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.pollutants().totalGrams(), 0), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(
                fmt.number(result.canopy().totalAreaSquareMetres(), 0), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell("100 %", Element.ALIGN_RIGHT));

        document.add(table);

        document.add(PdfBlocks.subHeading("Where the carbon comes from"));
        List<PdfBlocks.BarRow> bars = result.contributions().stream()
                .map(row -> new PdfBlocks.BarRow(
                        row.commonName(),
                        row.annualCo2Kg(),
                        fmt.tonnesFromKg(row.annualCo2Kg()) + " t"))
                .toList();
        document.add(PdfBlocks.barChart(bars, PdfTheme.CARBON));
    }

    private void pollutants(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("5. Air pollutant removal"));
        document.add(PdfBlocks.note("Annual removal at maturity, across the whole planting."));

        List<PdfBlocks.BarRow> bars = result.pollutants().breakdown().stream()
                .map(row -> new PdfBlocks.BarRow(
                        row.label(), row.grams(), fmt.number(row.kilograms(), 1) + " kg"))
                .toList();
        document.add(PdfBlocks.barChart(bars, PdfTheme.POLLUTANT));

        PdfPTable table = PdfBlocks.table(new float[]{40f, 30f, 30f}, "Pollutant", "g / year", "kg / year");
        for (CalculationResult.PollutantBreakdown row : result.pollutants().breakdown()) {
            table.addCell(PdfTheme.cell(row.label()));
            table.addCell(PdfTheme.numericCell(fmt.number(row.grams(), 1)));
            table.addCell(PdfTheme.numericCell(fmt.number(row.kilograms(), 2)));
        }
        table.addCell(PdfTheme.totalCell("Total", Element.ALIGN_LEFT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.pollutants().totalGrams(), 1), Element.ALIGN_RIGHT));
        table.addCell(PdfTheme.totalCell(fmt.number(result.pollutants().totalKg(), 2), Element.ALIGN_RIGHT));
        document.add(table);
    }

    // ------------------------------------------------------------ canopy & feasibility

    private void canopyAndFeasibility(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("6. Canopy and spatial feasibility"));

        CalculationResult.Canopy canopy = result.canopy();
        CalculationResult.Feasibility feasibility = result.feasibility();

        document.add(PdfBlocks.definitions(
                "Land available", fmt.area(canopy.landAreaSquareMetres()),
                "Canopy at maturity", fmt.area(canopy.totalAreaSquareMetres()),
                "Canopy coverage", canopy.coveragePercent() == null
                        ? "Not applicable — no land area given"
                        : fmt.percent(canopy.coveragePercent())
                          + (canopy.uncappedCoveragePercent() != null
                             && canopy.uncappedCoveragePercent().compareTo(BigDecimal.valueOf(100)) > 0
                                ? " (" + fmt.percent(canopy.uncappedCoveragePercent())
                                  + " before overlap is capped)" : ""),
                "Area required at spacing", fmt.area(feasibility.requiredAreaSquareMetres()),
                "Space utilisation", feasibility.utilisationPercent() == null
                        ? "Not applicable" : fmt.percent(feasibility.utilisationPercent()),
                "Surplus / deficit", feasibility.surplusAreaSquareMetres() == null
                        ? "—" : fmt.area(feasibility.surplusAreaSquareMetres()),
                "Maximum plantable at this mix", feasibility.maxPlantableTrees() == null
                        ? "—" : fmt.count(feasibility.maxPlantableTrees()),
                "Verdict", verdictLabel(feasibility.verdict())));

        document.add(PdfBlocks.callout(
                verdictNarrative(feasibility),
                verdictColour(feasibility.verdict()),
                PdfTheme.SURFACE_ALT));
    }

    private String verdictLabel(com.urbancanopy.calculation.model.FeasibilityVerdict verdict) {
        return verdict == null ? "—" : verdict.getLabel();
    }

    private java.awt.Color verdictColour(com.urbancanopy.calculation.model.FeasibilityVerdict verdict) {
        if (verdict == null) {
            return PdfTheme.INK_MUTED;
        }
        return switch (verdict) {
            case OPTIMAL -> PdfTheme.SUCCESS;
            case TIGHT -> PdfTheme.WARNING;
            case OVERCROWDED -> PdfTheme.DANGER;
            case UNDER_UTILISED -> PdfTheme.INFO;
            case NOT_APPLICABLE -> PdfTheme.INK_MUTED;
        };
    }

    /** The assessment is advisory (BR-13); the wording says so rather than reading as a refusal. */
    private String verdictNarrative(CalculationResult.Feasibility feasibility) {
        if (feasibility.verdict() == null
                || feasibility.verdict() == com.urbancanopy.calculation.model.FeasibilityVerdict.NOT_APPLICABLE) {
            return "No land area was supplied, so spatial feasibility could not be assessed.";
        }
        return feasibility.verdict().getGuidance()
                + " This assessment is advisory: a denser planting may still be the right decision, "
                + "but it will not reach the canopy area this estimate assumes.";
    }

    // ---------------------------------------------------------------------- projection

    private void projection(Document document, CalculationResult result) {
        document.add(PdfBlocks.sectionHeading("7. Multi-year projection"));
        document.add(PdfBlocks.note(
                "Sequestration rises as the planting matures. The annual rate reaches its full value "
                        + "only once the species have reached maturity for their growth rate."));

        List<PdfBlocks.BarRow> bars = result.projection().stream()
                .map(point -> new PdfBlocks.BarRow(
                        String.valueOf(point.calendarYear()),
                        point.cumulativeCo2Kg(),
                        fmt.tonnesFromKg(point.cumulativeCo2Kg()) + " t"))
                .toList();
        document.add(PdfBlocks.barChart(bars, PdfTheme.CARBON));

        PdfPTable table = PdfBlocks.table(new float[]{16f, 20f, 22f, 22f, 20f},
                "Year", "Calendar", "Annual CO₂ t", "Cumulative CO₂ t", "Maturity");

        for (CalculationResult.ProjectionPoint point : result.projection()) {
            table.addCell(PdfTheme.numericCell(String.valueOf(point.year())));
            table.addCell(PdfTheme.numericCell(String.valueOf(point.calendarYear())));
            table.addCell(PdfTheme.numericCell(fmt.tonnesFromKg(point.annualCo2Kg())));
            table.addCell(PdfTheme.numericCell(fmt.tonnesFromKg(point.cumulativeCo2Kg())));
            table.addCell(PdfTheme.numericCell(fmt.percent(
                    point.averageMaturity() == null ? null
                            : point.averageMaturity().multiply(BigDecimal.valueOf(100)))));
        }
        document.add(table);
    }

    private void equivalences(Document document, CalculationResult result) {
        if (result.equivalences().isEmpty()) {
            return;
        }

        document.add(PdfBlocks.sectionHeading("8. What these figures are equivalent to"));
        document.add(PdfBlocks.note(
                "Relatable comparisons for the plan's annual carbon and oxygen figures. Each is "
                        + "derived from an editable coefficient, listed in the appendix."));

        PdfPTable table = PdfBlocks.table(new float[]{32f, 20f, 48f}, "Equivalence", "Value", "Basis");
        for (CalculationResult.Equivalence equivalence : result.equivalences()) {
            table.addCell(PdfTheme.cell(equivalence.label()));
            table.addCell(PdfTheme.numericCell(
                    fmt.number(equivalence.value(), 0) + " " + equivalence.unit()));
            table.addCell(PdfTheme.cell(equivalence.description(), Element.ALIGN_LEFT, PdfTheme.small()));
        }
        document.add(table);
    }

    // --------------------------------------------------------------------- methodology

    /**
     * The appendix that makes the rest of the document checkable (FR-RPT-02, BR-19).
     *
     * <p>Per-species citations and the calculation trace. Without these the report is a set of
     * assertions; with them a reader can follow any figure back to the arithmetic and the
     * literature it came from.</p>
     */
    private void methodology(Document document, CalculationResult result) {
        document.newPage();
        document.add(PdfBlocks.sectionHeading("Appendix A — Methodology and assumptions"));

        document.add(PdfBlocks.body(
                "Every figure in this report is computed from published per-species average values, "
                        + "adjusted for expected survival and for the maturity a species reaches within "
                        + "the projection horizon. No measurement was taken at the site."));

        document.add(PdfBlocks.subHeading("A.1 Calculation trace"));
        document.add(PdfBlocks.note("The arithmetic behind the headline figures, with the actual "
                + "numbers substituted."));

        PdfPTable trace = PdfBlocks.table(new float[]{24f, 52f, 24f}, "Figure", "Arithmetic", "Result");
        for (CalculationResult.TraceEntry entry : result.trace()) {
            trace.addCell(PdfTheme.cell(entry.label()));
            trace.addCell(PdfTheme.cell(entry.expression(), Element.ALIGN_LEFT, PdfTheme.small()));
            trace.addCell(PdfTheme.cell(entry.result(), Element.ALIGN_RIGHT, PdfTheme.tableBody()));
        }
        document.add(trace);

        document.add(PdfBlocks.subHeading("A.2 Species reference sources"));
        document.add(PdfBlocks.note(
                "Each species' per-tree values are taken from the source below. A value without a "
                        + "source is not admitted to the catalogue."));

        PdfPTable sources = PdfBlocks.table(new float[]{26f, 74f}, "Species", "Source");
        for (CalculationResult.SpeciesContribution row : result.contributions()) {
            PdfPCell name = new PdfPCell();
            name.setBorderColor(PdfTheme.BORDER);
            name.setBorderWidth(0.5f);
            name.setPadding(5);
            name.addElement(new Paragraph(row.commonName(), PdfTheme.tableBody()));
            name.addElement(new Paragraph(row.scientificName(), PdfTheme.italic()));
            sources.addCell(name);

            sources.addCell(PdfTheme.cell(
                    row.sourceCitation() == null ? "—" : row.sourceCitation(),
                    Element.ALIGN_LEFT, PdfTheme.small()));
        }
        document.add(sources);

        document.add(PdfBlocks.subHeading("A.3 Disclaimer"));
        document.add(PdfBlocks.callout(result.disclaimer(), PdfTheme.INK_MUTED, PdfTheme.SURFACE_ALT));

        Paragraph closing = new Paragraph(
                "Report " + reportId + " · generated " + generatedAt + " · calculation engine "
                        + estimate.engineVersion(), PdfTheme.smallMuted());
        closing.setAlignment(Element.ALIGN_CENTER);
        closing.setSpacingBefore(14);
        document.add(closing);
    }
}
