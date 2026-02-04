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

class NodeProfileIT {

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
    void list_profiles(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // Register a node first
        CliRunner.run("register-node", "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());

        // List profiles
        CliRunner.Result r = CliRunner.run("node-profile", profilesDir.toString());
        assertEquals(0, r.exitCode(), "node-profile list failed: " + r.stderr());
        // Should output a 64-char hex node ID
        assertTrue(r.stdout().trim().length() == 64);
    }

    @Test
    void show_specific_profile(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path profilesDir = tmp.resolve("profiles");

        // Register a node
        CliRunner.Result regResult = CliRunner.run("register-node", "Alice", "ACME", "alice@acme.com",
                privKey.toString(), pubKey.toString(), profilesDir.toString());

        // Extract node_id from registration output
        String nodeId = extractField(regResult.stdout(), "node_id:");

        // Show specific profile
        CliRunner.Result r = CliRunner.run("node-profile", profilesDir.toString(), nodeId);
        assertEquals(0, r.exitCode(), "node-profile show failed: " + r.stderr());
        assertTrue(r.stdout().contains("operator_name:Alice"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("node-profile");
        assertEquals(1, r.exitCode());
    }

    private String extractField(String text, String prefix) {
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        throw new IllegalArgumentException("Field not found: " + prefix);
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
