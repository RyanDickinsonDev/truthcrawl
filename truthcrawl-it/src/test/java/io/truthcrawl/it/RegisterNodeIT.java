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

class RegisterNodeIT {

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
    void registers_node(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        CliRunner.Result r = CliRunner.run("register-node",
                "Alice", "ACME Corp", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());

        assertEquals(0, r.exitCode(), "register-node failed: " + r.stderr());
        assertTrue(r.stdout().contains("operator_name:Alice"));
        assertTrue(r.stdout().contains("organization:ACME Corp"));
        assertTrue(r.stdout().contains("contact_email:alice@acme.com"));
        assertTrue(r.stdout().contains("node_id:"));
        assertTrue(r.stdout().contains("public_key:"));
        assertTrue(r.stdout().contains("registered_at:"));
        assertTrue(r.stdout().contains("registration_signature:"));

        // Profile file should exist
        long profileCount = Files.list(profilesDir).count();
        assertEquals(1, profileCount);
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("register-node");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
