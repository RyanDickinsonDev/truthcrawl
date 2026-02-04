package io.truthcrawl.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A single WARC/1.0 record consisting of a header block and a payload block.
 *
 * <p>Minimal WARC/1.0 header fields:
 * <pre>
 * WARC/1.0
 * WARC-Type: resource
 * WARC-Record-ID: &lt;urn:uuid:{uuid}&gt;
 * WARC-Date: {ISO-8601 UTC}
 * Content-Length: {payload byte length}
 * Content-Type: {media type}
 * WARC-Block-Digest: sha256:{64-char lowercase hex}
 * </pre>
 *
 * <p>The payload is the raw content bytes (no transformation).
 * WARC-Block-Digest is the SHA-256 hash of the payload.
 * Records are terminated by two CRLFs after the payload.
 * Records are immutable once written.
 *
 * @param recordId    WARC-Record-ID value (e.g. "urn:uuid:...")
 * @param warcDate    WARC-Date (UTC instant)
 * @param contentType Content-Type media type
 * @param blockDigest WARC-Block-Digest SHA-256 of payload (64-char lowercase hex)
 * @param payload     raw content bytes
 */
public record WarcRecord(
        String recordId,
        Instant warcDate,
        String contentType,
        String blockDigest,
        byte[] payload
) {
    private static final String WARC_VERSION = "WARC/1.0";
    private static final String WARC_TYPE = "resource";
    private static final String CRLF = "\r\n";
    private static final String RECORD_TERMINATOR = CRLF + CRLF;

    public WarcRecord {
        if (recordId == null || recordId.isEmpty()) {
            throw new IllegalArgumentException("recordId must not be empty");
        }
        if (warcDate == null) {
            throw new IllegalArgumentException("warcDate must not be null");
        }
        if (contentType == null || contentType.isEmpty()) {
            throw new IllegalArgumentException("contentType must not be empty");
        }
        if (blockDigest == null || blockDigest.length() != 64) {
            throw new IllegalArgumentException("blockDigest must be 64-char hex");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    /**
     * Content-Length: the byte length of the payload.
     */
    public int contentLength() {
        return payload.length;
    }

    /**
     * Serialize the WARC header block (without payload or terminator).
     */
    public String headerText() {
        return WARC_VERSION + CRLF
                + "WARC-Type: " + WARC_TYPE + CRLF
                + "WARC-Record-ID: <" + recordId + ">" + CRLF
                + "WARC-Date: " + DateTimeFormatter.ISO_INSTANT.format(warcDate) + CRLF
                + "Content-Length: " + contentLength() + CRLF
                + "Content-Type: " + contentType + CRLF
                + "WARC-Block-Digest: sha256:" + blockDigest + CRLF;
    }

    /**
     * Serialize the complete WARC record as bytes (header + blank line + payload + terminator).
     */
    public byte[] toBytes() {
        byte[] header = (headerText() + CRLF).getBytes(StandardCharsets.UTF_8);
        byte[] terminator = RECORD_TERMINATOR.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[header.length + payload.length + terminator.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(payload, 0, result, header.length, payload.length);
        System.arraycopy(terminator, 0, result, header.length + payload.length, terminator.length);
        return result;
    }

    /**
     * Parse a WARC record from header lines and payload bytes.
     *
     * @param headerLines the header lines (WARC/1.0, WARC-Type, etc.)
     * @param payload     the raw payload bytes
     * @return parsed WarcRecord
     * @throws IllegalArgumentException if header is invalid
     */
    public static WarcRecord parse(List<String> headerLines, byte[] payload) {
        if (headerLines.isEmpty() || !headerLines.get(0).equals(WARC_VERSION)) {
            throw new IllegalArgumentException(
                    "Expected first line to be " + WARC_VERSION);
        }

        String recordId = null;
        Instant warcDate = null;
        String contentType = null;
        String blockDigest = null;

        for (int i = 1; i < headerLines.size(); i++) {
            String line = headerLines.get(i);
            int colon = line.indexOf(':');
            if (colon == -1) continue;
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();

            switch (key) {
                case "WARC-Record-ID" -> recordId = stripAngleBrackets(value);
                case "WARC-Date" -> warcDate = Instant.parse(value);
                case "Content-Type" -> contentType = value;
                case "WARC-Block-Digest" -> {
                    if (!value.startsWith("sha256:")) {
                        throw new IllegalArgumentException(
                                "WARC-Block-Digest must start with sha256:");
                    }
                    blockDigest = value.substring("sha256:".length());
                }
                default -> { /* ignore unknown headers like WARC-Type, Content-Length */ }
            }
        }

        if (recordId == null) {
            throw new IllegalArgumentException("Missing WARC-Record-ID header");
        }
        if (warcDate == null) {
            throw new IllegalArgumentException("Missing WARC-Date header");
        }
        if (contentType == null) {
            throw new IllegalArgumentException("Missing Content-Type header");
        }
        if (blockDigest == null) {
            throw new IllegalArgumentException("Missing WARC-Block-Digest header");
        }

        return new WarcRecord(recordId, warcDate, contentType, blockDigest, payload);
    }

    private static String stripAngleBrackets(String value) {
        if (value.startsWith("<") && value.endsWith(">")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
