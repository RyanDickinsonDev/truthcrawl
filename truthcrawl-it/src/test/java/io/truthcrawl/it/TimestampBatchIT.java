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

class TimestampBatchIT {

    private static String pubKeyBase64;
    private static String privKeyBase64;
    private static String tsaPubBase64;
    private static String tsaPrivBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");

        KeyPair publisherKp = kpg.generateKeyPair();
        pubKeyBase64 = Base64.getEncoder().encodeToString(publisherKp.getPublic().getEncoded());
        privKeyBase64 = Base64.getEncoder().encodeToString(publisherKp.getPrivate().getEncoded());

        KeyPair tsaKp = kpg.generateKeyPair();
        tsaPubBase64 = Base64.getEncoder().encodeToString(tsaKp.getPublic().getEncoded());
        tsaPrivBase64 = Base64.getEncoder().encodeToString(tsaKp.getPrivate().getEncoded());
    }

    @Test
    void timestamps_chain_link(@TempDir Path tmp) throws Exception {
        // Publish a chain batch first
        Path manifest = writeFile(tmp, "manifest.txt",
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb\n"
                        + "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d\n");
        Path privKey = writeFile(tmp, "priv.key", privKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", pubKeyBase64);
        Path batchDir = tmp.resolve("batch");

        CliRunner.Result pub = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batchDir.toString());
        assertEquals(0, pub.exitCode(), "publish failed: " + pub.stderr());

        // Timestamp the chain link
        Path tsaPriv = writeFile(tmp, "tsa-priv.key", tsaPrivBase64);
        Path tsaPub = writeFile(tmp, "tsa-pub.key", tsaPubBase64);
        Path tsDir = tmp.resolve("timestamps");

        CliRunner.Result ts = CliRunner.run("timestamp-batch",
                batchDir.resolve("chain-link.txt").toString(),
                tsaPriv.toString(), tsaPub.toString(), tsDir.toString());

        assertEquals(0, ts.exitCode(), "timestamp-batch failed: " + ts.stderr());
        assertTrue(ts.stdout().contains("data_hash:"));
        assertTrue(ts.stdout().contains("tsa_signature:"));
    }

    @Test
    void timestamped_batch_verifiable(@TempDir Path tmp) throws Exception {
        Path manifest = writeFile(tmp, "manifest.txt",
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb\n");
        Path privKey = writeFile(tmp, "priv.key", privKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", pubKeyBase64);
        Path batchDir = tmp.resolve("batch");

        CliRunner.run("publish-chain-batch",
                "2024-01-15", manifest.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batchDir.toString());

        Path tsaPriv = writeFile(tmp, "tsa-priv.key", tsaPrivBase64);
        Path tsaPub = writeFile(tmp, "tsa-pub.key", tsaPubBase64);
        Path tsDir = tmp.resolve("timestamps");

        CliRunner.Result ts = CliRunner.run("timestamp-batch",
                batchDir.resolve("chain-link.txt").toString(),
                tsaPriv.toString(), tsaPub.toString(), tsDir.toString());
        assertEquals(0, ts.exitCode());

        // Extract data hash from output
        String dataHash = extractField(ts.stdout(), "data_hash");

        // Verify the timestamp
        CliRunner.Result ver = CliRunner.run("verify-timestamp",
                dataHash, tsaPub.toString(), tsDir.toString());

        assertEquals(0, ver.exitCode(), "verify failed: " + ver.stderr());
        assertTrue(ver.stdout().contains("VALID"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("timestamp-batch");
        assertEquals(1, r.exitCode());
    }

    private String extractField(String output, String key) {
        for (String line : output.split("\n")) {
            if (line.startsWith(key + ":")) {
                return line.substring(key.length() + 1);
            }
        }
        throw new RuntimeException("Field not found: " + key);
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
