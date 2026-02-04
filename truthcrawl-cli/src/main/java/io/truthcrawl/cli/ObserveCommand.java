package io.truthcrawl.cli;

import io.truthcrawl.core.NodeSigner;
import io.truthcrawl.core.ObservationRecord;
import io.truthcrawl.core.PublisherKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: observe.
 *
 * <p>Fetches a URL and outputs a signed ObservationRecord.
 *
 * <p>Usage: truthcrawl observe &lt;url&gt; &lt;private-key-file&gt; &lt;public-key-file&gt; &lt;output-file&gt;
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input/fetch error.
 */
final class ObserveCommand {

    private ObserveCommand() {}

    static int run(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: truthcrawl observe <url> <private-key-file> <public-key-file> <output-file>");
            return 1;
        }

        String url = args[0];
        Path privateKeyPath = Path.of(args[1]);
        Path publicKeyPath = Path.of(args[2]);
        Path outputPath = Path.of(args[3]);

        try {
            String privKeyBase64 = Files.readString(privateKeyPath, StandardCharsets.UTF_8).strip();
            String pubKeyBase64 = Files.readString(publicKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKeyBase64, privKeyBase64);
            NodeSigner signer = NodeSigner.fromKeyPair(key);

            ObservationRecord unsigned = HttpObserver.observe(url, signer.nodeId());
            ObservationRecord signed = signer.sign(unsigned);

            Files.writeString(outputPath, signed.toFullText(), StandardCharsets.UTF_8);

            System.out.println("url:" + signed.url());
            System.out.println("record_hash:" + signed.recordHash());
            System.out.println("node_id:" + signed.nodeId());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
