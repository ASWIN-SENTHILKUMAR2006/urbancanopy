package com.urbancanopy.report.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.time.Instant;

/**
 * One generated-report record, as the administrator's log screen shows it (FR-RPT-06).
 *
 * <p>The plan's title is copied out rather than nested, because the plan reference is nullable:
 * a comparison report belongs to several plans and to none in particular, and a plan can be
 * soft-deleted after its report was generated. The log outlives its subject.</p>
 *
 * @param planIds for a comparison, the plans it covered — the only record of what was compared
 *                once the comparison itself is gone
 */
public record ReportLogResponse(
        Long id,
        EnumOption reportType,
        Long userId,
        String userName,
        Long planId,
        String planTitle,
        String planIds,
        String fileName,
        Long fileSizeBytes,
        Instant generatedAt
) {
}
