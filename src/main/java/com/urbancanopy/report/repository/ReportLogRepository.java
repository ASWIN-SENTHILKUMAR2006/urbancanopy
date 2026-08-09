package com.urbancanopy.report.repository;

import com.urbancanopy.report.entity.ReportLog;
import com.urbancanopy.report.entity.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for {@link ReportLog} (FR-RPT-06). */
public interface ReportLogRepository extends JpaRepository<ReportLog, Long> {

    /**
     * Administrator listing of generated reports.
     *
     * <p>Both associations are fetch-joined because the listing renders the user's name and
     * the plan's title on every row (NFR-02).</p>
     */
    @Query("""
            SELECT r FROM ReportLog r
            LEFT JOIN FETCH r.user
            LEFT JOIN FETCH r.plan
            WHERE (:reportType IS NULL OR r.reportType = :reportType)
              AND (:userId     IS NULL OR r.user.id = :userId)
            """)
    Page<ReportLog> search(@Param("reportType") ReportType reportType,
                           @Param("userId") Long userId,
                           Pageable pageable);

    long countByUserId(Long userId);
}
