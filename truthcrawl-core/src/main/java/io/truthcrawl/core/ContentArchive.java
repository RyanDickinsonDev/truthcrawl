package io.truthcrawl.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content-addressable archive backed by WARC files.
 *
 * <p>Content is indexed by SHA-256 hash of the payload. The archive maintains
 * an in-memory index mapping content hash to the WARC record. The index is
 * rebuilt by scanning WARC files on startup.
 *
 * <p>Rules:
 * <ul>
 *   <li>Content is stored in: {@code archive/content.warc}</li>
 *   <li>Storing the same content hash twice is a no-op (first-write-wins)</li>
 *   <li>Retrieval by hash returns the payload bytes or null if not found</li>
 *   <li>The archive is append-only (except for policy pruning)</li>
 *   <li>Listing all content hashes returns a sorted list</li>
 * </ul>
 */
public final class ContentArchive {

    private final Path archiveDir;
    private final Map<String, WarcRecord> index;

    /**
     * Create a content archive rooted at the given directory.
     * Rebuilds the in-memory index by scanning existing WARC files.
     *
     * @param archiveDir the archive root directory (will be created if absent)
     * @throws IOException if scanning existing WARC files fails
     */
    public ContentArchive(Path archiveDir) throws IOException {
        this.archiveDir = archiveDir;
        this.index = new LinkedHashMap<>();
        rebuildIndex();
    }

    /**
     * The archive root directory.
     */
    public Path archiveDir() {
        return archiveDir;
    }

    /**
     * The path to the WARC file backing this archive.
     */
    public Path warcFile() {
        return archiveDir.resolve("content.warc");
    }

    /**
     * Archive content. First-write-wins: if this content hash already exists,
     * this is a no-op and returns the existing hash.
     *
     * @param payload     the raw content bytes
     * @param contentType the media type
     * @return the content hash (SHA-256 of payload, lowercase hex)
     * @throws IOException if writing fails
     */
    public String store(byte[] payload, String contentType) throws IOException {
        String hash = WarcWriter.computeDigest(payload);
        if (index.containsKey(hash)) {
            return hash;
        }

        WarcWriter writer = new WarcWriter(warcFile());
        Instant now = Instant.now();
        writer.write(payload, contentType, now);

        // Re-read the file to get the exact record that was written
        // (with its assigned record ID and date)
        rebuildIndex();

        return hash;
    }

    /**
     * Retrieve archived content by hash.
     *
     * @param contentHash 64-char lowercase hex SHA-256
     * @return the payload bytes, or null if not found
     */
    public byte[] retrieve(String contentHash) {
        WarcRecord record = index.get(contentHash);
        if (record == null) return null;
        return record.payload();
    }

    /**
     * Check if content with the given hash exists in the archive.
     */
    public boolean contains(String contentHash) {
        return index.containsKey(contentHash);
    }

    /**
     * List all content hashes in the archive, sorted.
     *
     * @return sorted list of content hashes
     */
    public List<String> listHashes() {
        List<String> hashes = new ArrayList<>(index.keySet());
        Collections.sort(hashes);
        return Collections.unmodifiableList(hashes);
    }

    /**
     * Total number of records in the archive.
     */
    public int count() {
        return index.size();
    }

    /**
     * Total payload size in bytes across all records.
     */
    public long totalSize() {
        return index.values().stream()
                .mapToLong(WarcRecord::contentLength)
                .sum();
    }

    /**
     * The oldest WARC-Date in the archive, or null if empty.
     */
    public Instant oldest() {
        return index.values().stream()
                .map(WarcRecord::warcDate)
                .min(Instant::compareTo)
                .orElse(null);
    }

    /**
     * The newest WARC-Date in the archive, or null if empty.
     */
    public Instant newest() {
        return index.values().stream()
                .map(WarcRecord::warcDate)
                .max(Instant::compareTo)
                .orElse(null);
    }

    /**
     * Get the in-memory index (content hash -> WarcRecord). Unmodifiable.
     */
    Map<String, WarcRecord> index() {
        return Collections.unmodifiableMap(index);
    }

    /**
     * Rebuild the in-memory index by scanning the WARC file.
     */
    void rebuildIndex() throws IOException {
        index.clear();
        Path warc = warcFile();
        if (!Files.exists(warc)) return;

        List<WarcRecord> records = WarcReader.readAll(warc);
        for (WarcRecord record : records) {
            // First-write-wins: only index the first occurrence of each hash
            index.putIfAbsent(record.blockDigest(), record);
        }
    }
}
