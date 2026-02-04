package io.truthcrawl.cli;

import io.truthcrawl.core.BatchManifest;
import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.ChainVerifier;
import io.truthcrawl.core.PublisherKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: verify-chain.
 *
 * <p>Verifies the integrity of a batch chain. Each batch directory must contain
 * chain-link.txt, manifest.txt, and signature.txt.
 *
 * <p>Usage: truthcrawl verify-chain &lt;pub-key&gt; &lt;batch-dir-1&gt; &lt;batch-dir-2&gt; ...
 *
 * <p>Batch directories must be in chain order (genesis first).
 *
 * <p>Exit codes: 0 = PASS, 1 = usage error, 2 = input error, 3 = verification failed.
 */
final class VerifyChainCommand {

    private VerifyChainCommand() {}

    static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: truthcrawl verify-chain <pub-key>"
                    + " <batch-dir-1> [<batch-dir-2> ...]");
            return 1;
        }

        Path pubKeyPath = Path.of(args[0]);

        try {
            String pubKeyBase64 = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey publisherKey = PublisherKey.fromPublicKey(pubKeyBase64);

            List<ChainLink> links = new ArrayList<>();
            List<BatchManifest> manifests = new ArrayList<>();
            List<String> signatures = new ArrayList<>();

            for (int i = 1; i < args.length; i++) {
                Path dir = Path.of(args[i]);
                links.add(ChainLink.parse(
                        Files.readAllLines(dir.resolve("chain-link.txt"), StandardCharsets.UTF_8)));
                manifests.add(BatchManifest.parse(
                        Files.readAllLines(dir.resolve("manifest.txt"), StandardCharsets.UTF_8)));
                signatures.add(
                        Files.readString(dir.resolve("signature.txt"), StandardCharsets.UTF_8).strip());
            }

            ChainVerifier.Result result = ChainVerifier.verify(
                    links, manifests, signatures, publisherKey);

            if (result.valid()) {
                System.out.print("PASS");
                return 0;
            } else {
                System.out.println("FAIL");
                for (String error : result.errors()) {
                    System.out.println("  " + error);
                }
                return 3;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
