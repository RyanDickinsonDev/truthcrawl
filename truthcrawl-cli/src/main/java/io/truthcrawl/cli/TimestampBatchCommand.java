package io.truthcrawl.cli;

import io.truthcrawl.core.ChainLink;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.TimestampAuthority;
import io.truthcrawl.core.TimestampStore;
import io.truthcrawl.core.TimestampToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: timestamp-batch.
 *
 * <p>Timestamps a batch chain link by hashing its canonical text and issuing a token.
 *
 * <p>Usage: truthcrawl timestamp-batch &lt;chain-link-file&gt; &lt;tsa-priv-key&gt; &lt;tsa-pub-key&gt; &lt;timestamp-dir&gt;
 *
 * <p>Outputs the token canonical text. Stores the token in the timestamp directory.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class TimestampBatchCommand {

    private TimestampBatchCommand() {}

    static int run(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: truthcrawl timestamp-batch <chain-link-file> <tsa-priv-key> <tsa-pub-key> <timestamp-dir>");
            return 1;
        }

        Path chainLinkPath = Path.of(args[0]);
        Path privKeyPath = Path.of(args[1]);
        Path pubKeyPath = Path.of(args[2]);
        Path timestampDir = Path.of(args[3]);

        try {
            List<String> lines = Files.readAllLines(chainLinkPath, StandardCharsets.UTF_8);
            ChainLink chainLink = ChainLink.parse(lines);
            String dataHash = chainLink.linkHash();

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
