package io.truthcrawl.cli;

import io.truthcrawl.core.PeerInfo;
import io.truthcrawl.core.PeerRegistry;
import io.truthcrawl.core.RequestSigner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * CLI command: register-peer.
 *
 * <p>Registers a peer in the local peer registry.
 *
 * <p>Usage: truthcrawl register-peer &lt;endpoint-url&gt; &lt;peer-pub-key&gt; &lt;peers-dir&gt;
 *
 * <p>The peer's nodeId is computed as SHA-256 of the Base64-encoded public key.
 *
 * <p>Exit codes: 0 success, 1 usage error, 2 runtime error.
 */
final class RegisterPeerCommand {

    private RegisterPeerCommand() {}

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: truthcrawl register-peer <endpoint-url> <peer-pub-key> <peers-dir>");
            return 1;
        }

        String endpointUrl = args[0];
        Path pubKeyPath = Path.of(args[1]);
        Path peersDir = Path.of(args[2]);

        try {
            String pubKeyBase64 = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();

            // Compute nodeId from public key
            io.truthcrawl.core.PublisherKey peerKey = io.truthcrawl.core.PublisherKey.fromPublicKey(pubKeyBase64);
            String nodeId = RequestSigner.computeNodeId(peerKey);

            PeerInfo peer = new PeerInfo(nodeId, endpointUrl, pubKeyBase64);
            PeerRegistry registry = new PeerRegistry(peersDir);
            registry.register(peer);

            System.out.println(peer.toCanonicalText());
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
