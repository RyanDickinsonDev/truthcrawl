package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end dispute resolution tests.
 *
 * <p>Creates observation files and a dispute file directly,
 * then runs resolve-dispute and verifies the output.
 */
class ResolveDisputeIT {

    private static final String NODE_A = "a" + "0".repeat(63);
    private static final String NODE_B = "b" + "0".repeat(63);
    private static final String NODE_C = "c" + "0".repeat(63);
    private static final String CONTENT_HASH =
            "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
    private static final String ALT_CONTENT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void upheld_dispute_resolution(@TempDir Path tmp) throws Exception {
        // Node A observed 404 (wrong), B and C observed 200 (correct)
        Path obsA = writeObservation(tmp, "obs_a.txt", NODE_A, 404, ALT_CONTENT_HASH);
        Path obsB = writeObservation(tmp, "obs_b.txt", NODE_B, 200, CONTENT_HASH);
        Path obsC = writeObservation(tmp, "obs_c.txt", NODE_C, 200, CONTENT_HASH);

        // Compute record hash for the challenged observation (A)
        String challengedHash = computeRecordHash(404, ALT_CONTENT_HASH, NODE_A);
        String challengerHash = computeRecordHash(200, CONTENT_HASH, NODE_B);

        Path dispute = writeDispute(tmp, "dispute.txt",
                challengedHash, challengerHash, NODE_B);

        CliRunner.Result r = CliRunner.run("resolve-dispute",
                dispute.toString(), obsA.toString(), obsB.toString(), obsC.toString());

        assertEquals(0, r.exitCode(), "resolve-dispute failed: " + r.stderr());
        assertTrue(r.stdout().contains("outcome:UPHELD"));
        assertTrue(r.stdout().contains("dispute_id:2024-01-16-0001"));
    }

    @Test
    void dismissed_dispute_resolution(@TempDir Path tmp) throws Exception {
        // All nodes agree — dispute should be dismissed
        Path obsA = writeObservation(tmp, "obs_a.txt", NODE_A, 200, CONTENT_HASH);
        Path obsB = writeObservation(tmp, "obs_b.txt", NODE_B, 200, CONTENT_HASH);
        Path obsC = writeObservation(tmp, "obs_c.txt", NODE_C, 200, CONTENT_HASH);

        String challengedHash = computeRecordHash(200, CONTENT_HASH, NODE_A);
        String challengerHash = computeRecordHash(200, CONTENT_HASH, NODE_B);

        Path dispute = writeDispute(tmp, "dispute.txt",
                challengedHash, challengerHash, NODE_B);

        CliRunner.Result r = CliRunner.run("resolve-dispute",
                dispute.toString(), obsA.toString(), obsB.toString(), obsC.toString());

        assertEquals(0, r.exitCode(), "resolve-dispute failed: " + r.stderr());
        assertTrue(r.stdout().contains("outcome:DISMISSED"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("resolve-dispute");
        assertEquals(1, r.exitCode());
    }

    @Test
    void exits_2_with_too_few_observations(@TempDir Path tmp) throws Exception {
        Path obsA = writeObservation(tmp, "obs_a.txt", NODE_A, 200, CONTENT_HASH);
        Path obsB = writeObservation(tmp, "obs_b.txt", NODE_B, 200, CONTENT_HASH);

        String challengedHash = computeRecordHash(200, CONTENT_HASH, NODE_A);
        String challengerHash = computeRecordHash(200, CONTENT_HASH, NODE_B);

        Path dispute = writeDispute(tmp, "dispute.txt",
                challengedHash, challengerHash, NODE_B);

        CliRunner.Result r = CliRunner.run("resolve-dispute",
                dispute.toString(), obsA.toString(), obsB.toString());

        assertEquals(2, r.exitCode());
    }

    private Path writeObservation(Path dir, String name, String nodeId,
                                   int status, String contentHash) throws IOException {
        String text = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:https://example.com\n"
                + "final_url:https://example.com/\n"
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

    private Path writeDispute(Path dir, String name, String challengedHash,
                               String challengerHash, String challengerNodeId) throws IOException {
        String text = "dispute_id:2024-01-16-0001\n"
                + "challenged_record_hash:" + challengedHash + "\n"
                + "challenger_record_hash:" + challengerHash + "\n"
                + "url:https://example.com\n"
                + "filed_at:2024-01-16T10:00:00Z\n"
                + "challenger_node_id:" + challengerNodeId + "\n"
                + "challenger_signature:\n";
        return writeFile(dir, name, text);
    }

    /**
     * Compute the record hash for an observation with these values.
     * Must match the canonical text format exactly.
     */
    private String computeRecordHash(int status, String contentHash, String nodeId)
            throws Exception {
        String canonical = "version:0.1\n"
                + "observed_at:2024-01-15T12:00:00Z\n"
                + "url:https://example.com\n"
                + "final_url:https://example.com/\n"
                + "status_code:" + status + "\n"
                + "fetch_ms:100\n"
                + "content_hash:" + contentHash + "\n"
                + "directive:canonical:\n"
                + "directive:robots_meta:\n"
                + "directive:robots_header:\n"
                + "node_id:" + nodeId + "\n";

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
