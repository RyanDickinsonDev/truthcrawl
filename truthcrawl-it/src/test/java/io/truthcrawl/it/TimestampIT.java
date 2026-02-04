package io.truthcrawl.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    }

    @Test
    void timestamps_data_hash(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "tsa-priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "tsa-pub.key", publicKeyBase64);
        Path tsDir = tmp.resolve("timestamps");

        CliRunner.Result r = CliRunner.run("timestamp",
                "a".repeat(64), privKey.toString(), pubKey.toString(), tsDir.toString());

        assertEquals(0, r.exitCode(), "timestamp failed: " + r.stderr());
        assertTrue(r.stdout().contains("data_hash:" + "a".repeat(64)));
        assertTrue(r.stdout().contains("issued_at:"));
        assertTrue(r.stdout().contains("tsa_key_id:"));
        assertTrue(r.stdout().contains("tsa_signature:"));
        assertTrue(Files.exists(tsDir.resolve("a".repeat(64) + ".txt")));
    }

    @Test
    void idempotent_timestamp(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "tsa-priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "tsa-pub.key", publicKeyBase64);
        Path tsDir = tmp.resolve("timestamps");

        CliRunner.Result r1 = CliRunner.run("timestamp",
                "a".repeat(64), privKey.toString(), pubKey.toString(), tsDir.toString());
        CliRunner.Result r2 = CliRunner.run("timestamp",
                "a".repeat(64), privKey.toString(), pubKey.toString(), tsDir.toString());

        assertEquals(0, r1.exitCode());
        assertEquals(0, r2.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("timestamp");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
