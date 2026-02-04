package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RetrieveContentIT {

    @Test
    void retrieves_archived_content(@TempDir Path tmp) throws Exception {
        String content = "<html>Retrievable</html>";
        Path inputFile = writeFile(tmp, "page.html", content);
        Path archiveDir = tmp.resolve("archive");

        // Archive first
        CliRunner.Result archiveResult = CliRunner.run("archive-content",
                inputFile.toString(), "text/html", archiveDir.toString());
        assertEquals(0, archiveResult.exitCode(), "archive failed: " + archiveResult.stderr());
        String hash = archiveResult.stdout().strip();

        // Retrieve
        CliRunner.Result r = CliRunner.run("retrieve-content",
                hash, archiveDir.toString());

        assertEquals(0, r.exitCode(), "retrieve failed: " + r.stderr());
        assertEquals(content, r.stdout());
    }

    @Test
    void exits_2_for_nonexistent_hash(@TempDir Path tmp) throws Exception {
        Path archiveDir = tmp.resolve("archive");
        Files.createDirectories(archiveDir);

        CliRunner.Result r = CliRunner.run("retrieve-content",
                "a".repeat(64), archiveDir.toString());

        assertEquals(2, r.exitCode());
        assertTrue(r.stderr().contains("Not found"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("retrieve-content");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
