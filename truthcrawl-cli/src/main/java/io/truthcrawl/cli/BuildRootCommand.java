package io.truthcrawl.cli;

import io.truthcrawl.core.MerkleTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: build-root.
 *
 * <p>Reads a manifest file (one SHA-256 hex hash per line), computes the Merkle root,
 * and prints it to stdout.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — usage error</li>
 *   <li>2 — input error (bad file or bad data)</li>
 * </ul>
 */
final class BuildRootCommand {

    private BuildRootCommand() {}

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: truthcrawl build-root <manifest-file>");
            return 1;
        }

        Path manifestPath = Path.of(args[0]);
        if (!Files.isRegularFile(manifestPath)) {
            System.err.println("File not found: " + manifestPath);
            return 2;
        }

        try {
            List<String> leaves = Files.readAllLines(manifestPath, StandardCharsets.UTF_8)
                    .stream()
                    .filter(line -> !line.isBlank())
                    .toList();

            if (leaves.isEmpty()) {
                System.err.println("Manifest is empty");
                return 2;
            }

            String root = MerkleTree.computeRoot(leaves);
            System.out.println(root);
            return 0;

        } catch (IOException e) {
            System.err.println("Error reading manifest: " + e.getMessage());
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid manifest data: " + e.getMessage());
            return 2;
        }
    }
}
