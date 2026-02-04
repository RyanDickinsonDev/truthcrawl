package io.truthcrawl.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationStatusIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    }

    private String storeRecord(Path storeDir, Path tmp, String url, String nodeId,
                               String contentHash, String filename) throws Exception {
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:" + url + "\n"
                + "final_url:" + url + "\n"
                + "status_code:200\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + nodeId + "\n"
                + "node_signature:\n";
        Path file = tmp.resolve(filename);
        Files.writeString(file, text, StandardCharsets.UTF_8);

        CliRunner.Result r = CliRunner.run("store-record", file.toString(), storeDir.toString());
        assertEquals(0, r.exitCode(), "store-record failed: " + r.stderr());
        return r.stdout().strip();
    }

    @Test
    void retrieves_saved_status(@TempDir Path tmp) throws Exception {
        Path storeDir = tmp.resolve("store");
        String h1 = storeRecord(storeDir, tmp, "https://a.com", "node1", "a".repeat(64), "r1.txt");
        storeRecord(storeDir, tmp, "https://a.com", "node2", "a".repeat(64), "ind1.txt");

        Path manifestFile = tmp.resolve("manifest.txt");
        Files.writeString(manifestFile, h1 + "\n");

        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path batchDir = tmp.resolve("batch");

        CliRunner.Result pub = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifestFile.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batchDir.toString());
        assertEquals(0, pub.exitCode());

        String merkleRoot = extractField(pub.stdout(), "merkle_root");
        Path verDir = tmp.resolve("verification");

        // Run pipeline to save status
        CliRunner.Result pipe = CliRunner.run("verify-pipeline",
                "2024-01-15",
                batchDir.resolve("manifest.txt").toString(),
                merkleRoot, "testseed", storeDir.toString(),
                verDir.toString());
        assertEquals(0, pipe.exitCode());

        // Retrieve status
        CliRunner.Result status = CliRunner.run("verification-status",
                "2024-01-15", verDir.toString());

        assertEquals(0, status.exitCode(), "status failed: " + status.stderr());
        assertTrue(status.stdout().contains("batch_id:2024-01-15"));
        assertTrue(status.stdout().contains("batch_status:VERIFIED_CLEAN"));
    }

    @Test
    void exits_2_for_missing_status(@TempDir Path tmp) throws Exception {
        Path verDir = tmp.resolve("verification");
        Files.createDirectories(verDir);

        CliRunner.Result r = CliRunner.run("verification-status",
                "2024-99-99", verDir.toString());

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verification-status");
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
