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

class SampleObservationsIT {

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
    void samples_deterministically(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path manifest = writeManifest(tmp);

        // Publish chain batch to get chain-link.txt
        Path batch = tmp.resolve("batch");
        CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch.toString());

        // Sample twice with same seed
        CliRunner.Result r1 = CliRunner.run("sample-observations",
                batch.resolve("manifest.txt").toString(),
                batch.resolve("chain-link.txt").toString(),
                "audit-seed", "2");

        CliRunner.Result r2 = CliRunner.run("sample-observations",
                batch.resolve("manifest.txt").toString(),
                batch.resolve("chain-link.txt").toString(),
                "audit-seed", "2");

        assertEquals(0, r1.exitCode(), "sample failed: " + r1.stderr());
        assertEquals(0, r2.exitCode());
        assertEquals(r1.stdout(), r2.stdout());

        // Should have 2 lines (2 hashes)
        String[] hashes = r1.stdout().split("\n");
        assertEquals(2, hashes.length);
        for (String hash : hashes) {
            assertEquals(64, hash.length());
        }
    }

    @Test
    void different_seeds_different_results(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path manifest = writeManifest(tmp);

        Path batch = tmp.resolve("batch");
        CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch.toString());

        CliRunner.Result r1 = CliRunner.run("sample-observations",
                batch.resolve("manifest.txt").toString(),
                batch.resolve("chain-link.txt").toString(),
                "seed-a", "2");

        CliRunner.Result r2 = CliRunner.run("sample-observations",
                batch.resolve("manifest.txt").toString(),
                batch.resolve("chain-link.txt").toString(),
                "seed-b", "2");

        assertEquals(0, r1.exitCode());
        assertEquals(0, r2.exitCode());
        // Different seeds should produce different samples (with high probability)
        assertTrue(!r1.stdout().equals(r2.stdout()),
                "Expected different samples for different seeds");
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("sample-observations");
        assertEquals(1, r.exitCode());
    }

    private Path writeManifest(Path dir) throws IOException {
        String content = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb\n"
                + "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d\n"
                + "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6\n"
                + "18ac3e7343f016890c510e93f935261169d9e3f565436429830faf0934f4f8e4\n"
                + "3f79bb7b435b05321651daefd374cdc681dc06faa65e374e38337b88ca046dea\n";
        return writeFile(dir, "manifest.txt", content);
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
