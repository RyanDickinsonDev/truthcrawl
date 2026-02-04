package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ArchivePolicyTest {

    @Test
    void unlimited_policy_prunes_nothing(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        archive.store("keep me".getBytes(StandardCharsets.UTF_8), "text/plain");

        int pruned = ArchivePolicy.UNLIMITED.prune(archive, Instant.now());

        assertEquals(0, pruned);
        assertEquals(1, archive.count());
    }

    @Test
    void age_based_pruning(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");

        // Write a record with an old date directly via WarcWriter
        WarcWriter writer = new WarcWriter(archiveDir.resolve("content.warc"));
        Instant oldDate = Instant.parse("2020-01-01T00:00:00Z");
        writer.write("old content".getBytes(StandardCharsets.UTF_8), "text/plain", oldDate);

        Instant recentDate = Instant.parse("2024-06-01T00:00:00Z");
        writer.write("new content".getBytes(StandardCharsets.UTF_8), "text/plain", recentDate);

        ContentArchive archive = new ContentArchive(archiveDir);
        assertEquals(2, archive.count());

        // Prune records older than 365 days from the "recent" date
        Instant now = recentDate.plus(Duration.ofDays(1));
        ArchivePolicy policy = new ArchivePolicy(365, -1);
        int pruned = policy.prune(archive, now);

        assertEquals(1, pruned);
        assertEquals(1, archive.count());
    }

    @Test
    void size_based_pruning(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");

        WarcWriter writer = new WarcWriter(archiveDir.resolve("content.warc"));
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-02-01T00:00:00Z");
        Instant t3 = Instant.parse("2024-03-01T00:00:00Z");

        byte[] p1 = "aaaa".getBytes(StandardCharsets.UTF_8); // 4 bytes
        byte[] p2 = "bbbb".getBytes(StandardCharsets.UTF_8); // 4 bytes
        byte[] p3 = "cccc".getBytes(StandardCharsets.UTF_8); // 4 bytes

        writer.write(p1, "text/plain", t1);
        writer.write(p2, "text/plain", t2);
        writer.write(p3, "text/plain", t3);

        ContentArchive archive = new ContentArchive(archiveDir);
        assertEquals(3, archive.count());
        assertEquals(12, archive.totalSize());

        // Limit to 8 bytes: should prune oldest record (4 bytes)
        ArchivePolicy policy = new ArchivePolicy(-1, 8);
        int pruned = policy.prune(archive, Instant.now());

        assertEquals(1, pruned);
        assertEquals(2, archive.count());
        assertTrue(archive.totalSize() <= 8);
    }

    @Test
    void no_pruning_when_within_limits(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");

        WarcWriter writer = new WarcWriter(archiveDir.resolve("content.warc"));
        writer.write("small".getBytes(StandardCharsets.UTF_8), "text/plain",
                Instant.now());

        ContentArchive archive = new ContentArchive(archiveDir);

        ArchivePolicy policy = new ArchivePolicy(365, 1000);
        int pruned = policy.prune(archive, Instant.now());

        assertEquals(0, pruned);
        assertEquals(1, archive.count());
    }

    @Test
    void prune_empty_archive(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));

        ArchivePolicy policy = new ArchivePolicy(1, 100);
        int pruned = policy.prune(archive, Instant.now());

        assertEquals(0, pruned);
    }

    @Test
    void combined_age_and_size_pruning(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");

        WarcWriter writer = new WarcWriter(archiveDir.resolve("content.warc"));
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Instant recent = Instant.parse("2024-06-01T00:00:00Z");

        // Old record (will be pruned by age)
        writer.write("old".getBytes(StandardCharsets.UTF_8), "text/plain", old);
        // Two recent records
        writer.write("new1".getBytes(StandardCharsets.UTF_8), "text/plain", recent);
        writer.write("new22".getBytes(StandardCharsets.UTF_8), "text/plain",
                recent.plus(Duration.ofHours(1)));

        ContentArchive archive = new ContentArchive(archiveDir);
        assertEquals(3, archive.count());

        // Age prunes "old", then size limit prunes oldest remaining if needed
        Instant now = recent.plus(Duration.ofDays(1));
        ArchivePolicy policy = new ArchivePolicy(365, 5); // max 5 bytes
        int pruned = policy.prune(archive, now);

        assertTrue(pruned >= 1);
        assertTrue(archive.totalSize() <= 5);
    }

    @Test
    void pruning_preserves_newer_records(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");

        WarcWriter writer = new WarcWriter(archiveDir.resolve("content.warc"));
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Instant recent = Instant.parse("2024-06-01T00:00:00Z");

        byte[] oldPayload = "old data".getBytes(StandardCharsets.UTF_8);
        byte[] newPayload = "new data".getBytes(StandardCharsets.UTF_8);

        writer.write(oldPayload, "text/plain", old);
        writer.write(newPayload, "text/plain", recent);

        ContentArchive archive = new ContentArchive(archiveDir);

        Instant now = recent.plus(Duration.ofDays(1));
        ArchivePolicy policy = new ArchivePolicy(365, -1);
        policy.prune(archive, now);

        // Old record pruned, new record preserved
        String newHash = WarcWriter.computeDigest(newPayload);
        assertTrue(archive.contains(newHash));
        assertArrayEquals(newPayload, archive.retrieve(newHash));

        String oldHash = WarcWriter.computeDigest(oldPayload);
        assertFalse(archive.contains(oldHash));
    }
}
