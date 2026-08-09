package com.urbancanopy.report.service;

/**
 * A rendered document, ready to be streamed.
 *
 * <p>Bytes rather than a stream or a temporary file. A plan report is a few hundred kilobytes
 * at most, and holding it whole means its size is known before the response begins — so
 * {@code Content-Length} is accurate, the browser can show real download progress, and a
 * failure during rendering produces a clean error instead of a truncated PDF that has already
 * been half-committed to the wire with a 200 status.</p>
 *
 * @param fileName the download filename, already safe for a header and a filesystem
 */
public record GeneratedReport(String fileName, byte[] content) {

    public int sizeBytes() {
        return content.length;
    }
}
