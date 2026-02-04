package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WarcReaderTest {

    @Test
    void reads_single_record(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        byte[] payload = "test content".getBytes(StandardCharsets.UTF_8);
        String expectedHash = writer.write(payload, "text/plain");

        List<WarcRecord> records = WarcReader.readAll(warcFile);

        assertEquals(1, records.size());
        assertEquals(expectedHash, records.get(0).blockDigest());
        assertArrayEquals(payload, records.get(0).payload());
        assertEquals("text/plain", records.get(0).contentType());
    }

    @Test
    void reads_multiple_records(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        byte[] p1 = "first".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "second".getBytes(StandardCharsets.UTF_8);
        String h1 = writer.write(p1, "text/plain");
        String h2 = writer.write(p2, "text/html");

        List<WarcRecord> records = WarcReader.readAll(warcFile);

        assertEquals(2, records.size());
        assertEquals(h1, records.get(0).blockDigest());
        assertArrayEquals(p1, records.get(0).payload());
        assertEquals(h2, records.get(1).blockDigest());
        assertArrayEquals(p2, records.get(1).payload());
    }

    @Test
    void preserves_record_metadata(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        Instant fixed = Instant.parse("2024-03-15T08:00:00Z");
        writer.write("data".getBytes(StandardCharsets.UTF_8), "application/json", fixed);

        List<WarcRecord> records = WarcReader.readAll(warcFile);

        assertEquals(1, records.size());
        assertEquals(fixed, records.get(0).warcDate());
        assertEquals("application/json", records.get(0).contentType());
        assertTrue(records.get(0).recordId().startsWith("urn:uuid:"));
    }

    @Test
    void detects_corrupted_digest(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        writer.write("good data".getBytes(StandardCharsets.UTF_8), "text/plain");

        // Corrupt the file by replacing some payload bytes
        String content = Files.readString(warcFile, StandardCharsets.UTF_8);
        String corrupted = content.replace("good data", "bad! data");
        Files.writeString(warcFile, corrupted, StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () ->
                WarcReader.readAll(warcFile));
    }

    @Test
    void reads_empty_payload(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        String hash = writer.write(new byte[0], "application/octet-stream");

        List<WarcRecord> records = WarcReader.readAll(warcFile);

        assertEquals(1, records.size());
        assertEquals(hash, records.get(0).blockDigest());
        assertEquals(0, records.get(0).payload().length);
    }

    @Test
    void roundtrip_binary_payload(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) binary[i] = (byte) i;

        writer.write(binary, "application/octet-stream");

        List<WarcRecord> records = WarcReader.readAll(warcFile);
        assertEquals(1, records.size());
        assertArrayEquals(binary, records.get(0).payload());
    }
}
