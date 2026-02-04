package io.truthcrawl.cli;

import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.TimestampAuthority;
import io.truthcrawl.core.TimestampStore;
import io.truthcrawl.core.TimestampToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: timestamp.
 *
 * <p>Issues a timestamp token for a data hash using a local TSA key.
 *
 * <p>Usage: truthcrawl timestamp &lt;data-hash&gt; &lt;tsa-priv-key&gt; &lt;tsa-pub-key&gt; &lt;timestamp-dir&gt;
 *
 * <p>Outputs the token canonical text. Stores the token in the timestamp directory.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class TimestampCommand {

    private TimestampCommand() {}

    static int run(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: truthcrawl timestamp <data-hash> <tsa-priv-key> <tsa-pub-key> <timestamp-dir>");
            return 1;
        }

        String dataHash = args[0];
        Path privKeyPath = Path.of(args[1]);
        Path pubKeyPath = Path.of(args[2]);
        Path timestampDir = Path.of(args[3]);

        try {
            String privKey = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);

            TimestampAuthority tsa = new TimestampAuthority(key);
            TimestampToken token = tsa.issue(dataHash);

            TimestampStore store = new TimestampStore(timestampDir);
            store.store(token);

            System.out.print(token.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
