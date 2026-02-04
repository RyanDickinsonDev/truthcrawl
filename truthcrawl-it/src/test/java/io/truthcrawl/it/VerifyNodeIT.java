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

class VerifyNodeIT {

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
    void verifies_valid_profile(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // Register node
        CliRunner.Result regResult = CliRunner.run("register-node",
                "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());
        assertEquals(0, regResult.exitCode());

        // Find the profile file
        Path profileFile = Files.list(profilesDir).findFirst().orElseThrow();

        // Verify it
        CliRunner.Result r = CliRunner.run("verify-node", profileFile.toString());
        assertEquals(0, r.exitCode(), "verify-node failed: " + r.stderr());
        assertTrue(r.stdout().contains("VALID"));
        assertTrue(r.stdout().contains("operator:Alice"));
    }

    @Test
    void verifies_profile_with_attestation(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // Register node
        CliRunner.run("register-node", "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());

        // Attest capabilities
        CliRunner.run("attest-capabilities",
                privKey.toString(), pubKey.toString(), profilesDir.toString(),
                "example.com", "test.org");

        // Find the profile file
        Path profileFile = Files.list(profilesDir).findFirst().orElseThrow();

        // Verify it
        CliRunner.Result r = CliRunner.run("verify-node", profileFile.toString());
        assertEquals(0, r.exitCode(), "verify-node failed: " + r.stderr());
        assertTrue(r.stdout().contains("VALID"));
        assertTrue(r.stdout().contains("domains:2"));
    }

    @Test
    void detects_tampered_profile(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // Register node
        CliRunner.run("register-node", "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());

        // Find and tamper the profile file
        Path profileFile = Files.list(profilesDir).findFirst().orElseThrow();
        String content = Files.readString(profileFile);
        String tampered = content.replace("operator_name:Alice", "operator_name:Mallory");
        Path tamperedFile = tmp.resolve("tampered.txt");
        Files.writeString(tamperedFile, tampered);

        // Verify tampered profile
        CliRunner.Result r = CliRunner.run("verify-node", tamperedFile.toString());
        assertEquals(2, r.exitCode());
        assertTrue(r.stdout().contains("INVALID"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-node");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
