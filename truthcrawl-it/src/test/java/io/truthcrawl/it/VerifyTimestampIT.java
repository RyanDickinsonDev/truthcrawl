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

class VerifyTimestampIT {

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
    void verifies_valid_timestamp(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "tsa-priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "tsa-pub.key", publicKeyBase64);
        Path tsDir = tmp.resolve("timestamps");

        // Create timestamp
        CliRunner.Result ts = CliRunner.run("timestamp",
                "a".repeat(64), privKey.toString(), pubKey.toString(), tsDir.toString());
        assertEquals(0, ts.exitCode());

        // Verify
        CliRunner.Result ver = CliRunner.run("verify-timestamp",
                "a".repeat(64), pubKey.toString(), tsDir.toString());

        assertEquals(0, ver.exitCode(), "verify failed: " + ver.stderr());
        assertTrue(ver.stdout().contains("VALID"));
    }

    @Test
    void rejects_wrong_key(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "tsa-priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "tsa-pub.key", publicKeyBase64);
        Path tsDir = tmp.resolve("timestamps");

        // Create timestamp
        CliRunner.run("timestamp",
                "a".repeat(64), privKey.toString(), pubKey.toString(), tsDir.toString());

        // Verify with wrong key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair wrongKp = kpg.generateKeyPair();
        String wrongPub = Base64.getEncoder().encodeToString(wrongKp.getPublic().getEncoded());
        Path wrongKey = writeFile(tmp, "wrong.key", wrongPub);

        CliRunner.Result ver = CliRunner.run("verify-timestamp",
                "a".repeat(64), wrongKey.toString(), tsDir.toString());

        assertEquals(3, ver.exitCode());
    }

    @Test
    void exits_2_for_missing_timestamp(@TempDir Path tmp) throws Exception {
        Path pubKey = writeFile(tmp, "tsa-pub.key", publicKeyBase64);
        Path tsDir = tmp.resolve("timestamps");
        Files.createDirectories(tsDir);

        CliRunner.Result r = CliRunner.run("verify-timestamp",
                "f".repeat(64), pubKey.toString(), tsDir.toString());

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-timestamp");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
