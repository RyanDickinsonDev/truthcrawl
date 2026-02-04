package io.truthcrawl.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@link WarcRecord}s from a {@code .warc} file sequentially.
 *
 * <p>The reader validates required header fields and verifies WARC-Block-Digest
 * matches the actual payload hash. A digest mismatch is a parse error.
 *
 * <p>Records can be iterated without loading the entire file into memory.
 */
public final class WarcReader {

    private WarcReader() {}

    /**
     * Read all WARC records from the given file.
     *
     * @param warcFile path to the .warc file
     * @return list of parsed WarcRecords in file order
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if a record is malformed or digest mismatches
     */
    public static List<WarcRecord> readAll(Path warcFile) throws IOException {
        List<WarcRecord> records = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(warcFile);
             BufferedInputStream in = new BufferedInputStream(raw)) {
            while (true) {
                WarcRecord record = readNext(in);
                if (record == null) break;
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Read the next WARC record from the stream, or null if at end.
     */
    private static WarcRecord readNext(InputStream in) throws IOException {
        // Read header lines until blank line (CRLF on its own)
        List<String> headerLines = new ArrayList<>();
        String line = readLine(in);
        if (line == null) return null; // end of stream

        // Skip leading empty lines between records
        while (line != null && line.isEmpty()) {
            line = readLine(in);
        }
        if (line == null) return null;

        headerLines.add(line);
        while (true) {
            line = readLine(in);
            if (line == null || line.isEmpty()) break;
            headerLines.add(line);
        }

        if (headerLines.isEmpty()) return null;

        // Extract Content-Length from headers
        int contentLength = -1;
        for (String h : headerLines) {
            if (h.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(h.substring("Content-Length:".length()).strip());
                break;
            }
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Missing Content-Length header");
        }

        // Read payload bytes
        byte[] payload = in.readNBytes(contentLength);
        if (payload.length != contentLength) {
            throw new IllegalArgumentException(
                    "Expected " + contentLength + " payload bytes, got " + payload.length);
        }

        // Consume record terminator (two CRLFs = \r\n\r\n)
        consumeTerminator(in);

        // Parse and validate the record
        WarcRecord record = WarcRecord.parse(headerLines, payload);

        // Verify digest
        String actualDigest = WarcWriter.computeDigest(payload);
        if (!actualDigest.equals(record.blockDigest())) {
            throw new IllegalArgumentException(
                    "WARC-Block-Digest mismatch: header says " + record.blockDigest()
                            + " but payload hashes to " + actualDigest);
        }

        return record;
    }

    /**
     * Read a single line (terminated by CRLF or LF). Returns null at end of stream.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n') {
                    in.reset();
                }
                return buf.toString(StandardCharsets.UTF_8);
            }
            if (b == '\n') {
                return buf.toString(StandardCharsets.UTF_8);
            }
            buf.write(b);
        }
        if (buf.size() > 0) {
            return buf.toString(StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Consume the record terminator (two CRLFs after payload).
     * Tolerant of LF-only line endings.
     */
    private static void consumeTerminator(InputStream in) throws IOException {
        // The terminator is \r\n\r\n. We need to consume up to 4 bytes.
        // Be tolerant: consume any combination of \r and \n.
        in.mark(4);
        for (int i = 0; i < 4; i++) {
            int b = in.read();
            if (b == -1) break;
            if (b != '\r' && b != '\n') {
                // Not part of terminator; the stream will be re-read by next readLine
                // We can't unread, but this shouldn't happen with well-formed WARC
                break;
            }
        }
    }
}
