package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveContentIT {

    @Test
    void archives_file_content(@TempDir Path tmp) throws Exception {
        Path inputFile = writeFile(tmp, "page.html", "<html>Hello</html>");
        Path archiveDir = tmp.resolve("archive");

        CliRunner.Result r = CliRunner.run("archive-content",
                inputFile.toString(), "text/html", archiveDir.toString());

        assertEquals(0, r.exitCode(), "archive-content failed: " + r.stderr());
        String hash = r.stdout().strip();
        assertEquals(64, hash.length(), "Expected 64-char hex hash, got: " + hash);
        assertTrue(Files.exists(archiveDir.resolve("content.warc")));
    }

    @Test
    void idempotent_archive(@TempDir Path tmp) throws Exception {
        Path inputFile = writeFile(tmp, "page.html", "<html>Same</html>");
        Path archiveDir = tmp.resolve("archive");

        CliRunner.Result r1 = CliRunner.run("archive-content",
                inputFile.toString(), "text/html", archiveDir.toString());
        CliRunner.Result r2 = CliRunner.run("archive-content",
                inputFile.toString(), "text/html", archiveDir.toString());

        assertEquals(0, r1.exitCode());
        assertEquals(0, r2.exitCode());
        assertEquals(r1.stdout().strip(), r2.stdout().strip());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("archive-content");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
