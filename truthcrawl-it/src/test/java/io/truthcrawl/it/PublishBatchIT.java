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

class PublishBatchIT {

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
    void publishes_valid_batch(@TempDir Path tmp) throws Exception {
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("out");

        CliRunner.Result r = CliRunner.run("publish-batch",
                "2024-01-15", manifest.toString(), privKey.toString(), pubKey.toString(), outDir.toString());

        assertEquals(0, r.exitCode(), "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("batch_id:2024-01-15"));
        assertTrue(r.stdout().contains("record_count:3"));
        assertTrue(Files.exists(outDir.resolve("manifest.txt")));
        assertTrue(Files.exists(outDir.resolve("metadata.txt")));
        assertTrue(Files.exists(outDir.resolve("signature.txt")));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("publish-batch");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_for_missing_manifest(@TempDir Path tmp) throws Exception {
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);

        CliRunner.Result r = CliRunner.run("publish-batch",
                "2024-01-15", "/nonexistent/manifest.txt", privKey.toString(), pubKey.toString(), tmp.resolve("out").toString());

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_2_for_invalid_batch_id(@TempDir Path tmp) throws Exception {
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);

        CliRunner.Result r = CliRunner.run("publish-batch",
                "not-a-date", manifest.toString(), privKey.toString(), pubKey.toString(), tmp.resolve("out").toString());

        assertEquals(2, r.exitCode());
    }

    private Path writeManifest(Path dir) throws IOException {
        return writeFile(dir, "manifest.txt", String.join("\n",
                "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "2e7d2c03a9507ae265ecf5b5356885a53393a2029d241394997265a1a25aefc6") + "\n");
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
