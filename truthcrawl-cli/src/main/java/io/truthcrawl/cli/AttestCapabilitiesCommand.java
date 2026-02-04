package io.truthcrawl.cli;

import io.truthcrawl.core.CrawlAttestation;
import io.truthcrawl.core.NodeProfile;
import io.truthcrawl.core.NodeProfileStore;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RequestSigner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * CLI command: attest-capabilities.
 *
 * <p>Creates a signed crawl attestation declaring which domains a node crawls.
 * Updates the existing node profile with the attestation.
 *
 * <p>Usage: truthcrawl attest-capabilities &lt;priv-key&gt; &lt;pub-key&gt; &lt;profiles-dir&gt; &lt;domain1&gt; [domain2...]
 *
 * <p>Outputs the attestation canonical text. Updates the profile in the profiles directory.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class AttestCapabilitiesCommand {

    private AttestCapabilitiesCommand() {}

    static int run(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: truthcrawl attest-capabilities <priv-key> <pub-key> <profiles-dir> <domain1> [domain2...]");
            return 1;
        }

        Path privKeyPath = Path.of(args[0]);
        Path pubKeyPath = Path.of(args[1]);
        Path profilesDir = Path.of(args[2]);
        List<String> domains = Arrays.asList(Arrays.copyOfRange(args, 3, args.length));

        try {
            String privKey = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);

            String nodeId = RequestSigner.computeNodeId(key);

            // Load existing profile
            NodeProfileStore store = new NodeProfileStore(profilesDir);
            NodeProfile existing = store.load(nodeId);
            if (existing == null) {
                System.err.println("Error: no profile found for node " + nodeId.substring(0, 16) + "...");
                System.err.println("Run 'register-node' first.");
                return 2;
            }

            CrawlAttestation attestation = CrawlAttestation.create(key, domains, Instant.now());

            // Update profile with attestation
            NodeProfile updated = new NodeProfile(existing.registration(), attestation);
            store.store(updated);

            System.out.print(attestation.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
