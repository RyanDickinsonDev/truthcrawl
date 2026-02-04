package io.truthcrawl.cli;

import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.TimestampStore;
import io.truthcrawl.core.TimestampToken;
import io.truthcrawl.core.TimestampVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: verify-timestamp.
 *
 * <p>Verifies a stored timestamp token against a TSA public key.
 *
 * <p>Usage: truthcrawl verify-timestamp &lt;data-hash&gt; &lt;tsa-pub-key&gt; &lt;timestamp-dir&gt;
 *
 * <p>Exit codes: 0 valid, 1 usage error, 2 not found, 3 verification failed.
 */
final class VerifyTimestampCommand {

    private VerifyTimestampCommand() {}

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: truthcrawl verify-timestamp <data-hash> <tsa-pub-key> <timestamp-dir>");
            return 1;
        }

        String dataHash = args[0];
        Path pubKeyPath = Path.of(args[1]);
        Path timestampDir = Path.of(args[2]);

        try {
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromPublicKey(pubKey);

            TimestampStore store = new TimestampStore(timestampDir);
            TimestampToken token = store.load(dataHash);
            if (token == null) {
                System.err.println("No timestamp found for: " + dataHash);
                return 2;
            }

            TimestampVerifier.Result result = TimestampVerifier.verify(token, key);
            System.out.print(token.toCanonicalText());

            if (result.valid()) {
                System.out.println("VALID");
                return 0;
            } else {
                for (String error : result.errors()) {
                    System.err.println("Verification error: " + error);
                }
                return 3;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
