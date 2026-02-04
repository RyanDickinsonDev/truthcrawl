package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveStatsIT {

    @Test
    void shows_stats_for_populated_archive(@TempDir Path tmp) throws Exception {
        Path archiveDir = tmp.resolve("archive");
        Path f1 = writeFile(tmp, "a.txt", "hello");
        Path f2 = writeFile(tmp, "b.txt", "world!");

        CliRunner.run("archive-content", f1.toString(), "text/plain", archiveDir.toString());
        CliRunner.run("archive-content", f2.toString(), "text/plain", archiveDir.toString());

        CliRunner.Result r = CliRunner.run("archive-stats", archiveDir.toString());

        assertEquals(0, r.exitCode(), "archive-stats failed: " + r.stderr());
        assertTrue(r.stdout().contains("count:2"));
        assertTrue(r.stdout().contains("total_bytes:11")); // 5 + 6
        assertTrue(r.stdout().contains("oldest:"));
        assertTrue(r.stdout().contains("newest:"));
    }

    @Test
    void shows_stats_for_empty_archive(@TempDir Path tmp) throws Exception {
        Path archiveDir = tmp.resolve("archive");

        CliRunner.Result r = CliRunner.run("archive-stats", archiveDir.toString());

        assertEquals(0, r.exitCode(), "archive-stats failed: " + r.stderr());
        assertTrue(r.stdout().contains("count:0"));
        assertTrue(r.stdout().contains("total_bytes:0"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("archive-stats");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
