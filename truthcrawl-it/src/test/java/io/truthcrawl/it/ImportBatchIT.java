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

class ImportBatchIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    }

    private Path createExportDir(Path tmp) throws Exception {
        Path storeDir = tmp.resolve("source-store");
        String h1 = storeRecord(storeDir, tmp, "https://a.com", "node1", "a".repeat(64), "r1.txt");
        String h2 = storeRecord(storeDir, tmp, "https://b.com", "node2", "b".repeat(64), "r2.txt");

        Path manifestFile = tmp.resolve("manifest.txt");
        Files.writeString(manifestFile, h1 + "\n" + h2 + "\n");

        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path batchDir = tmp.resolve("batch");

        CliRunner.Result pub = CliRunner.run("publish-chain-batch",
                "2024-01-15", manifestFile.toString(), privKey.toString(),
                pubKey.toString(), "genesis", batchDir.toString());
        assertEquals(0, pub.exitCode(), "publish failed: " + pub.stderr());

        Path outputDir = tmp.resolve("export-out");
        Files.createDirectories(outputDir);
        CliRunner.Result exp = CliRunner.run("export-batch",
                batchDir.resolve("manifest.txt").toString(),
                batchDir.resolve("chain-link.txt").toString(),
                batchDir.resolve("signature.txt").toString(),
                storeDir.toString(), outputDir.toString());
        assertEquals(0, exp.exitCode(), "export failed: " + exp.stderr());

        return Path.of(exp.stdout().strip());
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
    void imports_valid_batch(@TempDir Path tmp) throws Exception {
        Path exportDir = createExportDir(tmp);
        Path importStore = tmp.resolve("import-store");
        Path pubKey = writeFile(tmp, "pub2.key", publicKeyBase64);

        CliRunner.Result r = CliRunner.run("import-batch",
                exportDir.toString(), importStore.toString(), pubKey.toString());

        assertEquals(0, r.exitCode(), "import failed: " + r.stderr());
        assertTrue(r.stdout().contains("batch_id:2024-01-15"));
        assertTrue(r.stdout().contains("valid:true"));
        assertTrue(r.stdout().contains("records_imported:2"));
    }

    @Test
    void rejects_wrong_key(@TempDir Path tmp) throws Exception {
        Path exportDir = createExportDir(tmp);
        Path importStore = tmp.resolve("import-store");

        // Generate different key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair wrongKp = kpg.generateKeyPair();
        String wrongPub = Base64.getEncoder().encodeToString(wrongKp.getPublic().getEncoded());
        Path wrongKey = writeFile(tmp, "wrong.key", wrongPub);

        CliRunner.Result r = CliRunner.run("import-batch",
                exportDir.toString(), importStore.toString(), wrongKey.toString());

        assertEquals(2, r.exitCode());
        assertTrue(r.stdout().contains("valid:false"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("import-batch");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
