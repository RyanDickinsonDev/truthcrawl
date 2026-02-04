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

class PublishChainBatchIT {

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
    void publish_genesis_batch(@TempDir Path tmp) throws Exception {
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("batch1");

        CliRunner.Result r = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", outDir.toString());

        assertEquals(0, r.exitCode(), "publish failed: " + r.stderr());
        assertTrue(Files.exists(outDir.resolve("chain-link.txt")));
        assertTrue(Files.exists(outDir.resolve("manifest.txt")));
        assertTrue(Files.exists(outDir.resolve("signature.txt")));
        assertTrue(r.stdout().contains("previous_root:" + "0".repeat(64)));
    }

    @Test
    void publish_chained_batch(@TempDir Path tmp) throws Exception {
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);

        // Publish genesis
        Path batch1 = tmp.resolve("batch1");
        CliRunner.Result r1 = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batch1.toString());
        assertEquals(0, r1.exitCode());

        // Extract merkle_root from output
        String merkleRoot = null;
        for (String line : r1.stdout().split("\n")) {
            if (line.startsWith("merkle_root:")) {
                merkleRoot = line.substring("merkle_root:".length());
            }
        }

        // Publish second batch chained to first
        Path batch2 = tmp.resolve("batch2");
        CliRunner.Result r2 = CliRunner.run("publish-chain-batch",
                "2024-01-16", manifest.toString(), privKey.toString(),
                pubKey.toString(), merkleRoot, batch2.toString());
        assertEquals(0, r2.exitCode());
        assertTrue(r2.stdout().contains("previous_root:" + merkleRoot));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("publish-chain-batch");
        assertEquals(1, r.exitCode());
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
