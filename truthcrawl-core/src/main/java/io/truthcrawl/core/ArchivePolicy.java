package io.truthcrawl.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Defines retention constraints on a {@link ContentArchive} and supports
 * explicit pruning.
 *
 * <p>Constraints:
 * <ul>
 *   <li>{@code maxAgeDays}: records older than this are eligible for pruning
 *       (-1 means no age limit)</li>
 *   <li>{@code maxTotalBytes}: total payload size limit; oldest records pruned
 *       first (-1 means no size limit)</li>
 * </ul>
 *
 * <p>Pruning is explicit (invoked by the operator, never automatic) and
 * rewrites the WARC file excluding pruned records, then rebuilds the index.
 *
 * @param maxAgeDays   max record age in days, or -1 for no limit
 * @param maxTotalBytes max total payload bytes, or -1 for no limit
 */
public record ArchivePolicy(long maxAgeDays, long maxTotalBytes) {

    /**
     * A policy with no limits (never prunes anything).
     */
    public static final ArchivePolicy UNLIMITED = new ArchivePolicy(-1, -1);

    /**
     * Prune the given archive according to this policy.
     *
     * <p>Age-based pruning is evaluated against {@code now}. Size-based pruning
     * removes the oldest records first until total size is within the limit.
     *
     * @param archive the archive to prune
     * @param now     the current time (for age evaluation)
     * @return the number of records pruned
     * @throws IOException if rewriting the WARC file fails
     */
    public int prune(ContentArchive archive, Instant now) throws IOException {
        if (maxAgeDays < 0 && maxTotalBytes < 0) {
            return 0; // no limits, nothing to prune
        }

        // Read all current records
        Path warcFile = archive.warcFile();
        if (!Files.exists(warcFile)) return 0;

        List<WarcRecord> allRecords = WarcReader.readAll(warcFile);
        if (allRecords.isEmpty()) return 0;

        // Phase 1: age-based pruning
        List<WarcRecord> kept = new ArrayList<>();
        for (WarcRecord record : allRecords) {
            if (maxAgeDays >= 0) {
                Duration age = Duration.between(record.warcDate(), now);
                if (age.toDays() > maxAgeDays) {
                    continue; // pruned
                }
            }
            kept.add(record);
        }

        // Phase 2: size-based pruning (oldest first)
        if (maxTotalBytes >= 0) {
            // Sort by date ascending so we remove oldest first
            kept.sort(Comparator.comparing(WarcRecord::warcDate));
            long totalSize = kept.stream().mapToLong(WarcRecord::contentLength).sum();

            while (totalSize > maxTotalBytes && !kept.isEmpty()) {
                WarcRecord oldest = kept.remove(0);
                totalSize -= oldest.contentLength();
            }
        }

        int pruned = allRecords.size() - kept.size();
        if (pruned == 0) return 0;

        // Rewrite the WARC file with only kept records
        rewriteWarcFile(warcFile, kept);

        // Rebuild the archive index
        archive.rebuildIndex();

        return pruned;
    }

    /**
     * Rewrite a WARC file with only the given records, preserving order.
     */
    private void rewriteWarcFile(Path warcFile, List<WarcRecord> records) throws IOException {
        Path tmpFile = warcFile.resolveSibling(warcFile.getFileName() + ".tmp");
        try {
            if (records.isEmpty()) {
                Files.deleteIfExists(warcFile);
                return;
            }

            try (OutputStream out = Files.newOutputStream(tmpFile)) {
                for (WarcRecord record : records) {
                    out.write(record.toBytes());
                }
            }

            Files.move(tmpFile, warcFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }
}
