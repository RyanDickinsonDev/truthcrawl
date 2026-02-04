package io.truthcrawl.cli;

import io.truthcrawl.core.NodeProfile;
import io.truthcrawl.core.NodeProfileStore;
import io.truthcrawl.core.NodeRegistration;
import io.truthcrawl.core.PublisherKey;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * CLI command: register-node.
 *
 * <p>Creates a self-signed node registration binding an operator identity
 * to a node's Ed25519 key pair.
 *
 * <p>Usage: truthcrawl register-node &lt;operator-name&gt; &lt;organization&gt; &lt;contact-email&gt; &lt;priv-key&gt; &lt;pub-key&gt; &lt;profiles-dir&gt;
 *
 * <p>Outputs the registration canonical text. Stores the profile in the profiles directory.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 input error.
 */
final class RegisterNodeCommand {

    private RegisterNodeCommand() {}

    static int run(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: truthcrawl register-node <operator-name> <organization> <contact-email> <priv-key> <pub-key> <profiles-dir>");
            return 1;
        }

        String operatorName = args[0];
        String organization = args[1];
        String contactEmail = args[2];
        Path privKeyPath = Path.of(args[3]);
        Path pubKeyPath = Path.of(args[4]);
        Path profilesDir = Path.of(args[5]);

        try {
            String privKey = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);

            NodeRegistration registration = NodeRegistration.create(
                    operatorName, organization, contactEmail, key, Instant.now());

            NodeProfileStore store = new NodeProfileStore(profilesDir);
            NodeProfile profile = new NodeProfile(registration, null);
            store.store(profile);

            System.out.print(registration.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
