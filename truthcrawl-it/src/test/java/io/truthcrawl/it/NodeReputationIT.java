package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for node-reputation CLI command.
 */
class NodeReputationIT {

    private static final String NODE_A = "a" + "0".repeat(63);
    private static final String NODE_B = "b" + "0".repeat(63);
    private static final String NODE_C = "c" + "0".repeat(63);

    @Test
    void computes_reputation_from_resolution_file(@TempDir Path tmp) throws Exception {
        // Write a resolution file where NODE_B and NODE_C won, NODE_A lost
        Path resolution = writeResolution(tmp, "resolution.txt",
                "UPHELD", NODE_A, NODE_B, NODE_C);

        CliRunner.Result r = CliRunner.run("node-reputation", resolution.toString());

        assertEquals(0, r.exitCode(), "node-reputation failed: " + r.stderr());
        assertTrue(r.stdout().contains("node:" + NODE_A));
        assertTrue(r.stdout().contains("node:" + NODE_B));
        assertTrue(r.stdout().contains("disputes_won:"));
        assertTrue(r.stdout().contains("disputes_lost:"));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("node-reputation");
        assertEquals(1, r.exitCode());
    }

    @Test
    void multiple_resolution_files(@TempDir Path tmp) throws Exception {
        Path r1 = writeResolution(tmp, "r1.txt", "UPHELD", NODE_A, NODE_B, NODE_C);
        Path r2 = writeResolution(tmp, "r2.txt", "UPHELD", NODE_A, NODE_B, NODE_C);

        CliRunner.Result r = CliRunner.run("node-reputation",
                r1.toString(), r2.toString());

        assertEquals(0, r.exitCode(), "node-reputation failed: " + r.stderr());
        // NODE_A should have 2 losses
        assertTrue(r.stdout().contains("disputes_lost:2"));
        // NODE_B should have 2 wins
        assertTrue(r.stdout().contains("disputes_won:2"));
    }

    /**
     * Write a resolution in canonical text format.
     * minorityNode is in the minority; majorityNode1 and majorityNode2 are majority.
     */
    private Path writeResolution(Path dir, String name, String outcome,
                                  String minorityNode,
                                  String majorityNode1, String majorityNode2)
            throws IOException {
        // Build a minimal valid resolution canonical text
        StringBuilder sb = new StringBuilder();
        sb.append("dispute_id:2024-01-16-0001\n");
        sb.append("outcome:").append(outcome).append('\n');
        sb.append("resolved_at:2024-01-16T10:00:00Z\n");
        sb.append("observations_count:3\n");

        // Include all consensus fields
        for (String field : new String[]{
                "status_code", "content_hash", "final_url",
                "directive:canonical", "directive:robots_meta", "directive:robots_header"}) {
            sb.append("field:").append(field).append('\n');
            sb.append("majority:200\n");  // arbitrary majority value
            sb.append("count:2/3\n");
            sb.append("challenged:404\n");  // arbitrary challenged value
        }

        sb.append("majority_nodes:").append(majorityNode1).append(',').append(majorityNode2).append('\n');
        sb.append("minority_nodes:").append(minorityNode).append('\n');

        return writeFile(dir, name, sb.toString());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
