package io.truthcrawl.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VerifyProofIT {

    private static final String LEAF_0 =
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";
    private static final String ROOT =
            "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe";

    @Test
    void prints_pass_for_valid_proof(@TempDir Path tmp) throws Exception {
        Path proof = writeValidProof(tmp);

        CliRunner.Result r = CliRunner.run("verify-proof", LEAF_0, proof.toString(), ROOT);

        assertEquals(0, r.exitCode());
        assertEquals("PASS", r.stdout());
    }

    @Test
    void exits_3_for_wrong_root(@TempDir Path tmp) throws Exception {
        Path proof = writeValidProof(tmp);
        String wrongRoot = "0000000000000000000000000000000000000000000000000000000000000000";

        CliRunner.Result r = CliRunner.run("verify-proof", LEAF_0, proof.toString(), wrongRoot);

        assertEquals(3, r.exitCode());
        assertEquals("FAIL", r.stdout());
    }

    @Test
    void exits_2_for_missing_proof_file() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-proof", LEAF_0, "/nonexistent/proof.txt", ROOT);

        assertEquals(2, r.exitCode());
        assertFalse(r.stderr().isEmpty());
    }

    @Test
    void exits_2_for_malformed_proof(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad-proof.txt");
        Files.writeString(bad, "garbage-line\n");

        CliRunner.Result r = CliRunner.run("verify-proof", LEAF_0, bad.toString(), ROOT);

        assertEquals(2, r.exitCode());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("verify-proof");

        assertEquals(1, r.exitCode());
    }

    /**
     * Valid proof for leaf 0 of the 3-leaf manifest (independently computed).
     */
    private Path writeValidProof(Path dir) throws IOException {
        Path proof = dir.resolve("proof.txt");
        Files.writeString(proof, String.join("\n",
                "right:3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                "right:a3e333fbee455b9a054cf05077f0f9d45b91bd13db4cd4a3681ec47455af085c") + "\n");
        return proof;
    }
}
