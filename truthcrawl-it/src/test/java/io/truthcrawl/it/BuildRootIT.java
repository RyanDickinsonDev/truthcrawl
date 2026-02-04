package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildRootIT {

    private static final String EXPECTED_ROOT =
            "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe";

    @Test
    void prints_root_for_valid_manifest(@TempDir Path tmp) throws Exception {
        Path manifest = writeManifest(tmp);

        CliRunner.Result r = CliRunner.run("build-root", manifest.toString());

        assertEquals(0, r.exitCode());
        assertEquals(EXPECTED_ROOT, r.stdout());
    }

    @Test
    void exits_2_for_missing_file() throws Exception {
        CliRunner.Result r = CliRunner.run("build-root", "/nonexistent/manifest.txt");

        assertEquals(2, r.exitCode());
        assertFalse(r.stderr().isEmpty());
    }

    @Test
    void exits_2_for_empty_manifest(@TempDir Path tmp) throws Exception {
        Path empty = tmp.resolve("empty.txt");
        Files.writeString(empty, "\n");

        CliRunner.Result r = CliRunner.run("build-root", empty.toString());

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_2_for_invalid_hex(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad.txt");
        Files.writeString(bad, "not-a-valid-hex-string\n");

        CliRunner.Result r = CliRunner.run("build-root", bad.toString());

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("build-root");

        assertEquals(1, r.exitCode());
    }

    private Path writeManifest(Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.txt");
        Files.writeString(manifest, String.join("\n",
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6") + "\n");
        return manifest;
    }
}
