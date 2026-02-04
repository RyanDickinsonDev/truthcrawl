package io.truthcrawl.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WarcRecordTest {

    private static final String RECORD_ID = "urn:uuid:12345678-1234-1234-1234-123456789abc";
    private static final Instant DATE = Instant.parse("2024-06-15T10:30:00Z");
    private static final String CONTENT_TYPE = "text/html";
    private static final byte[] PAYLOAD = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = WarcWriter.computeDigest(PAYLOAD);

    @Test
    void basic_construction() {
        WarcRecord record = new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, DIGEST, PAYLOAD);
        assertEquals(RECORD_ID, record.recordId());
        assertEquals(DATE, record.warcDate());
        assertEquals(CONTENT_TYPE, record.contentType());
        assertEquals(DIGEST, record.blockDigest());
        assertArrayEquals(PAYLOAD, record.payload());
    }

    @Test
    void content_length_matches_payload() {
        WarcRecord record = new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, DIGEST, PAYLOAD);
        assertEquals(PAYLOAD.length, record.contentLength());
    }

    @Test
    void empty_payload_allowed() {
        String emptyDigest = WarcWriter.computeDigest(new byte[0]);
        WarcRecord record = new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, emptyDigest, new byte[0]);
        assertEquals(0, record.contentLength());
    }

    @Test
    void header_text_contains_required_fields() {
        WarcRecord record = new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, DIGEST, PAYLOAD);
        String header = record.headerText();
        assertTrue(header.startsWith("WARC/1.0\r\n"));
        assertTrue(header.contains("WARC-Type: resource\r\n"));
        assertTrue(header.contains("WARC-Record-ID: <" + RECORD_ID + ">\r\n"));
        assertTrue(header.contains("WARC-Date: 2024-06-15T10:30:00Z\r\n"));
        assertTrue(header.contains("Content-Length: " + PAYLOAD.length + "\r\n"));
        assertTrue(header.contains("Content-Type: text/html\r\n"));
        assertTrue(header.contains("WARC-Block-Digest: sha256:" + DIGEST + "\r\n"));
    }

    @Test
    void to_bytes_contains_header_payload_terminator() {
        WarcRecord record = new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, DIGEST, PAYLOAD);
        byte[] bytes = record.toBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.contains("WARC/1.0\r\n"));
        assertTrue(text.contains("Hello, World!"));
        assertTrue(text.endsWith("\r\n\r\n"));
    }

    @Test
    void parse_from_header_lines() {
        List<String> headers = List.of(
                "WARC/1.0",
                "WARC-Type: resource",
                "WARC-Record-ID: <" + RECORD_ID + ">",
                "WARC-Date: 2024-06-15T10:30:00Z",
                "Content-Length: " + PAYLOAD.length,
                "Content-Type: text/html",
                "WARC-Block-Digest: sha256:" + DIGEST
        );
        WarcRecord record = WarcRecord.parse(headers, PAYLOAD);
        assertEquals(RECORD_ID, record.recordId());
        assertEquals(DATE, record.warcDate());
        assertEquals(CONTENT_TYPE, record.contentType());
        assertEquals(DIGEST, record.blockDigest());
        assertArrayEquals(PAYLOAD, record.payload());
    }

    @Test
    void parse_rejects_missing_version() {
        List<String> headers = List.of(
                "WARC-Type: resource",
                "WARC-Record-ID: <" + RECORD_ID + ">"
        );
        assertThrows(IllegalArgumentException.class, () ->
                WarcRecord.parse(headers, PAYLOAD));
    }

    @Test
    void parse_rejects_missing_record_id() {
        List<String> headers = List.of(
                "WARC/1.0",
                "WARC-Type: resource",
                "WARC-Date: 2024-06-15T10:30:00Z",
                "Content-Length: " + PAYLOAD.length,
                "Content-Type: text/html",
                "WARC-Block-Digest: sha256:" + DIGEST
        );
        assertThrows(IllegalArgumentException.class, () ->
                WarcRecord.parse(headers, PAYLOAD));
    }

    @Test
    void parse_rejects_missing_digest() {
        List<String> headers = List.of(
                "WARC/1.0",
                "WARC-Type: resource",
                "WARC-Record-ID: <" + RECORD_ID + ">",
                "WARC-Date: 2024-06-15T10:30:00Z",
                "Content-Length: " + PAYLOAD.length,
                "Content-Type: text/html"
        );
        assertThrows(IllegalArgumentException.class, () ->
                WarcRecord.parse(headers, PAYLOAD));
    }

    @Test
    void rejects_null_record_id() {
        assertThrows(IllegalArgumentException.class, () ->
                new WarcRecord(null, DATE, CONTENT_TYPE, DIGEST, PAYLOAD));
    }

    @Test
    void rejects_null_date() {
        assertThrows(IllegalArgumentException.class, () ->
                new WarcRecord(RECORD_ID, null, CONTENT_TYPE, DIGEST, PAYLOAD));
    }

    @Test
    void rejects_invalid_digest_length() {
        assertThrows(IllegalArgumentException.class, () ->
                new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, "short", PAYLOAD));
    }

    @Test
    void rejects_null_payload() {
        assertThrows(IllegalArgumentException.class, () ->
                new WarcRecord(RECORD_ID, DATE, CONTENT_TYPE, DIGEST, null));
    }
}
