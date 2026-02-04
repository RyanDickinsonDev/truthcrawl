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

class AttestCapabilitiesIT {

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
    void attests_capabilities(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // First register the node
        CliRunner.Result regResult = CliRunner.run("register-node",
                "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());
        assertEquals(0, regResult.exitCode(), "register-node failed: " + regResult.stderr());

        // Then attest capabilities
        CliRunner.Result attResult = CliRunner.run("attest-capabilities",
                privKey.toString(), pubKey.toString(), profilesDir.toString(),
                "example.com", "test.org");

        assertEquals(0, attResult.exitCode(), "attest-capabilities failed: " + attResult.stderr());
        assertTrue(attResult.stdout().contains("node_id:"));
        assertTrue(attResult.stdout().contains("attested_at:"));
        assertTrue(attResult.stdout().contains("domain:example.com"));
        assertTrue(attResult.stdout().contains("domain:test.org"));
        assertTrue(attResult.stdout().contains("attestation_signature:"));
    }

    @Test
    void fails_without_registration(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        CliRunner.Result r = CliRunner.run("attest-capabilities",
                privKey.toString(), pubKey.toString(), profilesDir.toString(),
                "example.com");

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("attest-capabilities");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
