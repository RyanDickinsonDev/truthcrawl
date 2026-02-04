package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentArchiveTest {

    @Test
    void store_and_retrieve(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);

        String hash = archive.store(payload, "text/plain");

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertArrayEquals(payload, archive.retrieve(hash));
    }

    @Test
    void first_write_wins(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        byte[] payload = "same content".getBytes(StandardCharsets.UTF_8);

        String hash1 = archive.store(payload, "text/plain");
        String hash2 = archive.store(payload, "text/plain");

        assertEquals(hash1, hash2);
        assertEquals(1, archive.count());
    }

    @Test
    void retrieve_nonexistent_returns_null(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));

        assertNull(archive.retrieve("a".repeat(64)));
    }

    @Test
    void contains_check(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        byte[] payload = "data".getBytes(StandardCharsets.UTF_8);

        String hash = archive.store(payload, "text/plain");

        assertTrue(archive.contains(hash));
        assertFalse(archive.contains("b".repeat(64)));
    }

    @Test
    void list_hashes_sorted(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));

        String h1 = archive.store("aaa".getBytes(StandardCharsets.UTF_8), "text/plain");
        String h2 = archive.store("bbb".getBytes(StandardCharsets.UTF_8), "text/plain");
        String h3 = archive.store("ccc".getBytes(StandardCharsets.UTF_8), "text/plain");

        List<String> hashes = archive.listHashes();
        assertEquals(3, hashes.size());
        // Verify sorted
        for (int i = 1; i < hashes.size(); i++) {
            assertTrue(hashes.get(i - 1).compareTo(hashes.get(i)) <= 0);
        }
        assertTrue(hashes.contains(h1));
        assertTrue(hashes.contains(h2));
        assertTrue(hashes.contains(h3));
    }

    @Test
    void count_and_total_size(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        byte[] p1 = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "world!".getBytes(StandardCharsets.UTF_8);

        archive.store(p1, "text/plain");
        archive.store(p2, "text/plain");

        assertEquals(2, archive.count());
        assertEquals(p1.length + p2.length, archive.totalSize());
    }

    @Test
    void oldest_and_newest(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));

        archive.store("first".getBytes(StandardCharsets.UTF_8), "text/plain");
        archive.store("second".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertNotNull(archive.oldest());
        assertNotNull(archive.newest());
        assertTrue(archive.oldest().compareTo(archive.newest()) <= 0);
    }

    @Test
    void empty_archive_stats(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));

        assertEquals(0, archive.count());
        assertEquals(0, archive.totalSize());
        assertNull(archive.oldest());
        assertNull(archive.newest());
        assertTrue(archive.listHashes().isEmpty());
    }

    @Test
    void index_rebuilt_on_new_instance(@TempDir Path tmp) throws IOException {
        Path archiveDir = tmp.resolve("archive");
        ContentArchive archive1 = new ContentArchive(archiveDir);
        byte[] payload = "persistent".getBytes(StandardCharsets.UTF_8);
        String hash = archive1.store(payload, "text/plain");

        // Create a new instance pointing to the same directory
        ContentArchive archive2 = new ContentArchive(archiveDir);

        assertTrue(archive2.contains(hash));
        assertArrayEquals(payload, archive2.retrieve(hash));
        assertEquals(1, archive2.count());
    }

    @Test
    void content_hash_matches_payload(@TempDir Path tmp) throws IOException {
        ContentArchive archive = new ContentArchive(tmp.resolve("archive"));
        byte[] payload = "verify this".getBytes(StandardCharsets.UTF_8);

        String hash = archive.store(payload, "text/plain");

        // Independently compute hash and verify it matches
        String expected = WarcWriter.computeDigest(payload);
        assertEquals(expected, hash);
        assertArrayEquals(payload, archive.retrieve(hash));
    }
}
