package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WarcWriterTest {

    @Test
    void writes_warc_file(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        byte[] payload = "test content".getBytes(StandardCharsets.UTF_8);
        String hash = writer.write(payload, "text/plain");

        assertTrue(Files.exists(warcFile));
        assertEquals(64, hash.length());
        assertEquals(WarcWriter.computeDigest(payload), hash);
    }

    @Test
    void creates_parent_directories(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("sub/dir/test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        writer.write("data".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertTrue(Files.exists(warcFile));
    }

    @Test
    void appends_multiple_records(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        writer.write("first".getBytes(StandardCharsets.UTF_8), "text/plain");
        long sizeAfterFirst = Files.size(warcFile);

        writer.write("second".getBytes(StandardCharsets.UTF_8), "text/plain");
        long sizeAfterSecond = Files.size(warcFile);

        assertTrue(sizeAfterSecond > sizeAfterFirst);
    }

    @Test
    void returns_correct_digest(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        String hash = writer.write(payload, "text/plain");

        assertEquals(WarcWriter.computeDigest(payload), hash);
    }

    @Test
    void write_with_explicit_timestamp(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");

        writer.write("data".getBytes(StandardCharsets.UTF_8), "text/plain", fixed);

        String content = Files.readString(warcFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("WARC-Date: 2024-01-01T00:00:00Z"));
    }

    @Test
    void warc_file_accessor(@TempDir Path tmp) {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);
        assertEquals(warcFile, writer.warcFile());
    }

    @Test
    void compute_digest_deterministic() {
        byte[] data = "deterministic".getBytes(StandardCharsets.UTF_8);
        assertEquals(WarcWriter.computeDigest(data), WarcWriter.computeDigest(data));
        assertEquals(64, WarcWriter.computeDigest(data).length());
    }

    @Test
    void empty_payload(@TempDir Path tmp) throws IOException {
        Path warcFile = tmp.resolve("test.warc");
        WarcWriter writer = new WarcWriter(warcFile);

        String hash = writer.write(new byte[0], "application/octet-stream");

        assertTrue(Files.exists(warcFile));
        assertEquals(64, hash.length());
    }
}
