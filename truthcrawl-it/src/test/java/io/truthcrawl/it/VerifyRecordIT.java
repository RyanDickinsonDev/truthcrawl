package io.truthcrawl.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end: build a signed record, publish it in a batch, verify record inclusion.
 *
 * <p>Does not use the observe command (which requires network). Instead, constructs
 * a record file directly and tests the verify-record and compare-records pipelines.
 */
class VerifyRecordIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;
    private static String nodeId;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        // node_id = SHA-256 of public key bytes
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(Base64.getDecoder().decode(publicKeyBase64));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        nodeId = sb.toString();
    }

    /**
     * Full pipeline: create record → sign → publish batch → verify-record.
     */
    @Test
    void signed_record_verifies_against_batch(@TempDir Path tmp) throws Exception {
        // 1. Create and sign a record using observe-like flow
        //    (We build the record text and use the CLI to sign via publish-batch)
        String unsignedCanonical = buildRecordCanonical();

        // Sign the canonical text with Ed25519
        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(java.security.KeyFactory.getInstance("Ed25519")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(privateKeyBase64))));
        sig.update(unsignedCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        String fullRecord = unsignedCanonical + "node_signature:" + signature + "\n";
        Path recordFile = writeFile(tmp, "record.txt", fullRecord);

        // 2. Compute record hash (SHA-256 of canonical text)
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] recordHashBytes = md.digest(
                unsignedCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hashSb = new StringBuilder();
        for (byte b : recordHashBytes) {
            hashSb.append(Character.forDigit((b >> 4) & 0xf, 16));
            hashSb.append(Character.forDigit(b & 0xf, 16));
        }
        String recordHash = hashSb.toString();

        // 3. Build manifest containing this record hash + two others
        Path manifestFile = writeFile(tmp, "manifest.txt",
                recordHash + "\n"
                        + "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb\n"
                        + "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d\n");

        // 4. Publish batch
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path outDir = tmp.resolve("batch");

        CliRunner.Result pubResult = CliRunner.run("publish-batch",
                "2024-01-15", manifestFile.toString(), privKey.toString(),
                pubKey.toString(), outDir.toString());
        assertEquals(0, pubResult.exitCode(), "publish failed: " + pubResult.stderr());

        // 5. Verify record against batch
        CliRunner.Result verResult = CliRunner.run("verify-record",
                recordFile.toString(), pubKey.toString(),
                outDir.resolve("manifest.txt").toString(),
                outDir.resolve("metadata.txt").toString());
        assertEquals(0, verResult.exitCode(), "verify-record failed: " + verResult.stderr());
        assertEquals("PASS", verResult.stdout());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-record");
        assertEquals(1, r.exitCode());
    }

    private String buildRecordCanonical() {
        return "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:https://example.com\n"
                + "final_url:https://example.com/\n"
                + "status_code:200\n"
                + "fetch_ms:142\n"
                + "content_hash:b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9\n"
                + "header:content-type:text/html\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + nodeId + "\n";
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
