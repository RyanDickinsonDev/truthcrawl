package io.truthcrawl.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Appends {@link WarcRecord}s to a {@code .warc} file.
 *
 * <p>The writer assigns WARC-Record-ID (UUID v4) and WARC-Date (current UTC time),
 * computes WARC-Block-Digest from the payload bytes, and writes the complete record
 * atomically (header + payload + terminator in one operation).
 *
 * <p>Multiple records can be appended to the same file.
 */
public final class WarcWriter {

    private final Path warcFile;

    /**
     * Create a writer that appends to the given WARC file.
     *
     * @param warcFile the path to the .warc file (created if absent)
     */
    public WarcWriter(Path warcFile) {
        this.warcFile = warcFile;
    }

    /**
     * The path to the WARC file.
     */
    public Path warcFile() {
        return warcFile;
    }

    /**
     * Write a payload to the WARC file.
     *
     * @param payload     the raw content bytes
     * @param contentType the media type of the content
     * @return the content hash (SHA-256 of payload, lowercase hex)
     * @throws IOException if writing fails
     */
    public String write(byte[] payload, String contentType) throws IOException {
        return write(payload, contentType, Instant.now());
    }

    /**
     * Write a payload to the WARC file with an explicit timestamp.
     *
     * @param payload     the raw content bytes
     * @param contentType the media type of the content
     * @param warcDate    the WARC-Date to use
     * @return the content hash (SHA-256 of payload, lowercase hex)
     * @throws IOException if writing fails
     */
    public String write(byte[] payload, String contentType, Instant warcDate) throws IOException {
        String digest = computeDigest(payload);
        String recordId = "urn:uuid:" + UUID.randomUUID();

        WarcRecord record = new WarcRecord(recordId, warcDate, contentType, digest, payload);
        byte[] recordBytes = record.toBytes();

        Files.createDirectories(warcFile.getParent());
        try (OutputStream out = Files.newOutputStream(warcFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write(recordBytes);
        }

        return digest;
    }

    /**
     * Compute SHA-256 digest of the given bytes, returned as lowercase hex.
     */
    static String computeDigest(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return MerkleTree.encodeHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
