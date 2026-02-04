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
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDisputeIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;
    private static String nodeId;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(Base64.getDecoder().decode(publicKeyBase64));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        nodeId = sb.toString();
    }

    @Test
    void file_dispute_creates_signed_record(@TempDir Path tmp) throws Exception {
        Path challenged = writeRecord(tmp, "challenged.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
        Path challenger = writeRecord(tmp, "challenger.txt", 404,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path output = tmp.resolve("dispute.txt");

        CliRunner.Result r = CliRunner.run("file-dispute", "2024-01-16-0001",
                challenged.toString(), challenger.toString(),
                privKey.toString(), pubKey.toString(), output.toString());

        assertEquals(0, r.exitCode(), "file-dispute failed: " + r.stderr());
        assertTrue(Files.exists(output));

        String content = Files.readString(output);
        assertTrue(content.contains("dispute_id:2024-01-16-0001"));
        assertTrue(content.contains("challenger_node_id:" + nodeId));
        assertTrue(content.contains("challenger_signature:"));

        // stdout is the dispute hash (64-char hex)
        assertEquals(64, r.stdout().length());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("file-dispute");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_for_mismatched_urls(@TempDir Path tmp) throws Exception {
        Path challenged = writeRecord(tmp, "challenged.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                "https://example.com");
        Path challenger = writeRecordWithUrl(tmp, "challenger.txt", 200,
                "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                "https://other.com");
        Path privKey = writeFile(tmp, "priv.key", privateKeyBase64);
        Path pubKey = writeFile(tmp, "pub.key", publicKeyBase64);
        Path output = tmp.resolve("dispute.txt");

        CliRunner.Result r = CliRunner.run("file-dispute", "2024-01-16-0001",
                challenged.toString(), challenger.toString(),
                privKey.toString(), pubKey.toString(), output.toString());

        assertEquals(2, r.exitCode());
    }

    private Path writeRecord(Path dir, String name, int status, String contentHash)
            throws IOException {
        return writeRecord(dir, name, status, contentHash, "https://example.com");
    }

    private Path writeRecord(Path dir, String name, int status, String contentHash, String url)
            throws IOException {
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:" + url + "\n"
                + "final_url:" + url + "/\n"
                + "status_code:" + status + "\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + nodeId + "\n"
                + "node_signature:\n";
        return writeFile(dir, name, text);
    }

    private Path writeRecordWithUrl(Path dir, String name, int status, String contentHash,
                                     String url) throws IOException {
        // Different node_id to avoid duplicate in potential sets
        String otherNodeId = "f" + nodeId.substring(1);
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:" + url + "\n"
                + "final_url:" + url + "/\n"
                + "status_code:" + status + "\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + otherNodeId + "\n"
                + "node_signature:\n";
        return writeFile(dir, name, text);
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
