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

/**
 * End-to-end: publish a chain of batches, then verify-chain.
 */
class VerifyChainIT {

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
    void valid_chain_passes(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path manifest = writeManifest(tmp);

        // Publish genesis batch
        Path batch1 = tmp.resolve("batch1");
        CliRunner.Result r1 = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch1.toString());
        assertEquals(0, r1.exitCode(), "publish batch1 failed: " + r1.stderr());

        String merkleRoot = extractField(r1.stdout(), "merkle_root");

        // Publish second batch
        Path batch2 = tmp.resolve("batch2");
        CliRunner.Result r2 = CliRunner.run("publish-chain-batch",
                "2024-01-16", manifest.toString(), privKey.toString(),
                pubKey.toString(), merkleRoot, batch2.toString());
        assertEquals(0, r2.exitCode(), "publish batch2 failed: " + r2.stderr());

        // Verify chain
        CliRunner.Result v = CliRunner.run("verify-chain",
                pubKey.toString(), batch1.toString(), batch2.toString());
        assertEquals(0, v.exitCode(), "verify-chain failed: " + v.stderr());
        assertEquals("PASS", v.stdout());
    }

    @Test
    void tampered_chain_fails(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path manifest = writeManifest(tmp);

        // Publish genesis batch
        Path batch1 = tmp.resolve("batch1");
        CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch1.toString());

        // Tamper with chain-link.txt (modify previous_root)
        Path chainLinkFile = batch1.resolve("chain-link.txt");
        String content = Files.readString(chainLinkFile);
        content = content.replace("previous_root:" + "0".repeat(64),
                "previous_root:" + "f".repeat(64));
        Files.writeString(chainLinkFile, content);

        // Verify should fail
        CliRunner.Result v = CliRunner.run("verify-chain",
                pubKey.toString(), batch1.toString());
        assertEquals(3, v.exitCode());
        assertTrue(v.stdout().contains("FAIL"));
    }

    @Test
    void wrong_key_fails(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path manifest = writeManifest(tmp);

        // Publish batch
        Path batch1 = tmp.resolve("batch1");
        CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch1.toString());

        // Verify with different key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        Path otherPubKey = writeFile(tmp, "other.key",
                Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));

        CliRunner.Result v = CliRunner.run("verify-chain",
                otherPubKey.toString(), batch1.toString());
        assertEquals(3, v.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-chain");
        assertEquals(1, r.exitCode());
    }

    private String extractField(String output, String key) {
        for (String line : output.split("\n")) {
            if (line.startsWith(key + ":")) {
                return line.substring(key.length() + 1);
            }
        }
        throw new IllegalArgumentException("Field not found: " + key);
    }

    private Path writeManifest(Path dir) throws IOException {
        String content = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb\n"
                + "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d\n"
                + "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6\n";
        return writeFile(dir, "manifest.txt", content);
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
