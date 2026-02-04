package io.truthcrawl.cli;

import io.truthcrawl.core.MerkleTree;
import io.truthcrawl.core.ProofStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: verify-proof.
 *
 * <p>Verifies a Merkle inclusion proof.
 *
 * <p>Usage: truthcrawl verify-proof &lt;leaf-hex&gt; &lt;proof-file&gt; &lt;expected-root-hex&gt;
 *
 * <p>Proof file format (one step per line):
 * <pre>
 *   left:&lt;hex&gt;
 *   right:&lt;hex&gt;
 * </pre>
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — proof valid</li>
 *   <li>1 — usage error</li>
 *   <li>2 — input error (bad file or bad data)</li>
 *   <li>3 — proof invalid</li>
 * </ul>
 */
final class VerifyProofCommand {

    private VerifyProofCommand() {}

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: truthcrawl verify-proof <leaf-hex> <proof-file> <expected-root-hex>");
            return 1;
        }

        String leafHex = args[0];
        Path proofPath = Path.of(args[1]);
        String expectedRoot = args[2];

        if (!Files.isRegularFile(proofPath)) {
            System.err.println("File not found: " + proofPath);
            return 2;
        }

        try {
            List<String> lines = Files.readAllLines(proofPath, StandardCharsets.UTF_8)
                    .stream()
                    .filter(line -> !line.isBlank())
                    .toList();

            List<ProofStep> proof = parseProof(lines);

            boolean valid = MerkleTree.verifyProof(leafHex, proof, expectedRoot);
            if (valid) {
                System.out.println("PASS");
                return 0;
            } else {
                System.out.println("FAIL");
                return 3;
            }

        } catch (IOException e) {
            System.err.println("Error reading proof file: " + e.getMessage());
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid proof data: " + e.getMessage());
            return 2;
        }
    }

    private static List<ProofStep> parseProof(List<String> lines) {
        List<ProofStep> steps = new ArrayList<>(lines.size());
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon == -1) {
                throw new IllegalArgumentException("Invalid proof line (missing ':'): " + line);
            }
            String posStr = line.substring(0, colon);
            String hex = line.substring(colon + 1);

            ProofStep.Position pos = switch (posStr) {
                case "left" -> ProofStep.Position.LEFT;
                case "right" -> ProofStep.Position.RIGHT;
                default -> throw new IllegalArgumentException("Invalid position '" + posStr + "', expected 'left' or 'right'");
            };

            steps.add(new ProofStep(MerkleTree.decodeHex(hex), pos));
        }
        return steps;
    }
}
