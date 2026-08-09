package com.urbancanopy.report.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.urbancanopy.calculation.CalculationTestFixtures;
import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.plan.dto.EstimateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the generated PDFs by reading them back.
 *
 * <p>Asserting that a byte array is non-empty proves almost nothing about a document — a
 * report can be a valid PDF and still be missing its figures, its citations or its
 * disclaimer. These tests parse the produced file and assert on the text a reader would
 * actually see, which is the only claim worth making about a report.</p>
 */
@DisplayName("PDF report writers")
class ReportWriterTest {

    private static final ReportFormatter FORMATTER = new ReportFormatter();

    // ------------------------------------------------------------------------ fixtures

    private static CalculationResult result(CalculationInput input) {
        return CalculationTestFixtures.engine().evaluate(
                input,
                CalculationTestFixtures.defaultCoefficients(),
                2026,
                CalculationTestFixtures.DISCLAIMER);
    }

    private static EstimateResponse estimate(CalculationResult result) {
        return new EstimateResponse(
                42L,
                "Anna Nagar Avenue Drive",
                "Anna Nagar 2nd Avenue",
                new EnumOption("AIR_QUALITY_IMPROVEMENT", "Air quality improvement", null),
                new EnumOption("COMPLETED", "Completed", null),
                new BigDecimal("1.2"),
                new EnumOption("HECTARE", "Hectares", "ha"),
                new BigDecimal("12000.00"),
                2026,
                10,
                true,
                Instant.parse("2026-03-01T10:15:00Z"),
                "1.0.0",
                Instant.parse("2026-03-01T10:15:00Z"),
                result);
    }

    private static String textOf(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private static int pageCount(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    private byte[] planReport() {
        CalculationResult result = result(new CalculationInput(
                new BigDecimal("12000"), 10,
                List.of(CalculationTestFixtures.neem(800), CalculationTestFixtures.banyan(400))));

        return new PlanImpactReportWriter(
                estimate(result), "Demo Planner", "UC-PLAN-20260301-ABC123",
                "1 Mar 2026, 10:15", FORMATTER).write();
    }

    // ------------------------------------------------------------------- plan report

    @Nested
    @DisplayName("plan impact report")
    class PlanImpactReport {

        @Test
        @DisplayName("is a readable multi-page PDF")
        void isMultiPage() throws IOException {
            byte[] pdf = planReport();

            assertThat(pdf).isNotEmpty();
            // Cover, body sections and the appendix cannot fit on one page.
            assertThat(pageCount(pdf)).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("carries every section FR-RPT-02 requires")
        void carriesEverySection() throws IOException {
            String text = textOf(planReport());

            assertThat(text)
                    .contains("Anna Nagar Avenue Drive")
                    .contains("1. Plan summary")
                    .contains("2. Aggregate impact")
                    .contains("3. Species composition")
                    .contains("4. Environmental contribution by species")
                    .contains("5. Air pollutant removal")
                    .contains("6. Canopy and spatial feasibility")
                    .contains("7. Multi-year projection")
                    .contains("Appendix A");
        }

        @Test
        @DisplayName("states who prepared it and when, with a quotable reference")
        void carriesProvenance() throws IOException {
            String text = textOf(planReport());

            assertThat(text)
                    .contains("Demo Planner")
                    .contains("UC-PLAN-20260301-ABC123")
                    .contains("1 Mar 2026, 10:15");
        }

        @Test
        @DisplayName("never presents figures without the estimation disclaimer (FR-CALC-08)")
        void carriesDisclaimer() throws IOException {
            assertThat(textOf(planReport())).contains(CalculationTestFixtures.DISCLAIMER);
        }

        @Test
        @DisplayName("cites a published source for every species (BR-19)")
        void citesEverySpecies() throws IOException {
            String text = textOf(planReport());

            assertThat(text).contains("Species reference sources");
            // The fixtures' citation text, which must survive into the appendix.
            assertThat(text).contains("Nowak");
        }

        @Test
        @DisplayName("numbers every page as 'Page X of Y' with a resolved total (FR-RPT-04)")
        void numbersPages() throws IOException {
            byte[] pdf = planReport();
            String text = textOf(pdf);
            int pages = pageCount(pdf);

            // The total is painted into a deferred template at close; if that failed the
            // label would read "Page 2 of " with nothing after it.
            assertThat(text).contains("Page 2 of " + pages);
        }

        @Test
        @DisplayName("says an empty plan has nothing to report rather than printing zeroes")
        void handlesEmptyPlan() throws IOException {
            CalculationResult empty = result(new CalculationInput(new BigDecimal("12000"), 10, List.of()));

            byte[] pdf = new PlanImpactReportWriter(
                    estimate(empty), "Demo Planner", "UC-PLAN-EMPTY",
                    "1 Mar 2026, 10:15", FORMATTER).write();

            String text = textOf(pdf);
            assertThat(text).contains("contains no species");
            assertThat(text).doesNotContain("4. Environmental contribution by species");
        }
    }

    // ----------------------------------------------------------------- report naming

    @Nested
    @DisplayName("formatter")
    class Formatting {

        @Test
        @DisplayName("renders an absent figure as an em dash, never as zero")
        void absentIsNotZero() {
            assertThat(FORMATTER.number(null, 2)).isEqualTo("—");
            assertThat(FORMATTER.percent(null)).isEqualTo("—");
            assertThat(FORMATTER.area(null)).isEqualTo("—");
        }

        @Test
        @DisplayName("switches to hectares once square metres stop being readable")
        void scalesArea() {
            assertThat(FORMATTER.area(new BigDecimal("12000"))).isEqualTo("12,000 m²");
            assertThat(FORMATTER.area(new BigDecimal("250000"))).isEqualTo("25.00 ha");
        }

        @Test
        @DisplayName("groups thousands the same way regardless of the server's locale")
        void isLocaleStable() {
            assertThat(FORMATTER.number(new BigDecimal("1234567.891"), 2)).isEqualTo("1,234,567.89");
            assertThat(FORMATTER.count(1234567)).isEqualTo("1,234,567");
        }
    }
}
