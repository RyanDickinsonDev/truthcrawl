package io.truthcrawl.cli;

import io.truthcrawl.core.PublisherKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: gen-key.
 *
 * <p>Generates an Ed25519 key pair and writes the public and private keys to files.
 *
 * <p>Usage: truthcrawl gen-key &lt;output-dir&gt;
 *
 * <p>Creates two files:
 * <ul>
 *   <li>{@code output-dir/pub.key} — Base64-encoded public key</li>
 *   <li>{@code output-dir/priv.key} — Base64-encoded private key</li>
 * </ul>
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class GenKeyCommand {

    private GenKeyCommand() {}

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: truthcrawl gen-key <output-dir>");
            return 1;
        }

        Path outputDir = Path.of(args[0]);

        try {
            Files.createDirectories(outputDir);
            PublisherKey key = PublisherKey.generate();

            Path pubFile = outputDir.resolve("pub.key");
            Path privFile = outputDir.resolve("priv.key");

            Files.writeString(pubFile, key.publicKeyBase64() + "\n", StandardCharsets.UTF_8);
            Files.writeString(privFile, key.privateKeyBase64() + "\n", StandardCharsets.UTF_8);

            System.out.println("public_key:" + pubFile);
            System.out.println("private_key:" + privFile);
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
