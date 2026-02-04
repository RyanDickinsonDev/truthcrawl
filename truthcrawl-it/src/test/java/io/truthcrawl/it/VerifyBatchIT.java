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

class VerifyBatchIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    }

    /**
     * Full round-trip: publish a batch, then verify it independently.
     */
    @Test
    void publish_then_verify_round_trip(@TempDir Path tmp) throws Exception {
        // Publish
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("out");

        CliRunner.Result pub = CliRunner.run("publish-batch",
                "2024-01-15", manifest.toString(), privKey.toString(), pubKey.toString(), outDir.toString());
        assertEquals(0, pub.exitCode(), "publish failed: " + pub.stderr());

        // Verify using the published artifacts
        CliRunner.Result ver = CliRunner.run("verify-batch",
                outDir.resolve("metadata.txt").toString(),
                outDir.resolve("manifest.txt").toString(),
                outDir.resolve("signature.txt").toString(),
                pubKey.toString());

        assertEquals(0, ver.exitCode(), "verify stderr: " + ver.stderr());
        assertEquals("PASS", ver.stdout());
    }

    @Test
    void verification_fails_with_wrong_key(@TempDir Path tmp) throws Exception {
        // Publish with one key
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("out");

        CliRunner.Result pub = CliRunner.run("publish-batch",
                "2024-01-15", manifest.toString(), privKey.toString(), pubKey.toString(), outDir.toString());
        assertEquals(0, pub.exitCode());

        // Verify with a different key
        KeyPair otherKp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path otherPubKey = writeFile(tmp, "other.key",
                Base64.getEncoder().encodeToString(otherKp.getPublic().getEncoded()));

        CliRunner.Result ver = CliRunner.run("verify-batch",
                outDir.resolve("metadata.txt").toString(),
                outDir.resolve("manifest.txt").toString(),
                outDir.resolve("signature.txt").toString(),
                otherPubKey.toString());

        assertEquals(3, ver.exitCode());
        assertEquals("FAIL", ver.stdout());
    }

    @Test
    void verification_fails_with_tampered_manifest(@TempDir Path tmp) throws Exception {
        // Publish
        Path manifest = writeManifest(tmp);
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("out");

        CliRunner.Result pub = CliRunner.run("publish-batch",
                "2024-01-15", manifest.toString(), privKey.toString(), pubKey.toString(), outDir.toString());
        assertEquals(0, pub.exitCode());

        // Tamper with manifest
        Path tamperedManifest = writeFile(tmp, "tampered.txt",
                "0000000000000000000000000000000000000000000000000000000000000000\n");

        CliRunner.Result ver = CliRunner.run("verify-batch",
                outDir.resolve("metadata.txt").toString(),
                tamperedManifest.toString(),
                outDir.resolve("signature.txt").toString(),
                pubKey.toString());

        assertEquals(3, ver.exitCode());
        assertEquals("FAIL", ver.stdout());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-batch");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_for_missing_file() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-batch",
                "/nonexistent/metadata.txt", "/nonexistent/manifest.txt",
                "/nonexistent/signature.txt", "/nonexistent/pub.key");
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
