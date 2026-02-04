package io.truthcrawl.cli;

import io.truthcrawl.core.PeerRegistry;
import io.truthcrawl.core.PublisherKey;
import io.truthcrawl.core.RecordStore;
import io.truthcrawl.core.TruthcrawlServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI command: start-server.
 *
 * <p>Starts the HTTP API server.
 *
 * <p>Usage: truthcrawl start-server &lt;port&gt; &lt;store-dir&gt; &lt;batches-dir&gt;
 *     &lt;peers-dir&gt; &lt;priv-key&gt; &lt;pub-key&gt;
 *
 * <p>The server runs until the process is terminated.
 *
 * <p>Exit codes: 0 success (on shutdown), 1 usage error, 2 runtime error.
 */
final class StartServerCommand {

    private StartServerCommand() {}

    static int run(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: truthcrawl start-server <port> <store-dir> <batches-dir> <peers-dir> <priv-key> <pub-key>");
            return 1;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[0]);
            return 1;
        }

        Path storeDir = Path.of(args[1]);
        Path batchesDir = Path.of(args[2]);
        Path peersDir = Path.of(args[3]);
        Path privKeyPath = Path.of(args[4]);
        Path pubKeyPath = Path.of(args[5]);

        try {
            String privKey = Files.readString(privKeyPath, StandardCharsets.UTF_8).strip();
            String pubKey = Files.readString(pubKeyPath, StandardCharsets.UTF_8).strip();
            PublisherKey key = PublisherKey.fromKeyPair(pubKey, privKey);

            RecordStore recordStore = new RecordStore(storeDir);
            PeerRegistry peerRegistry = new PeerRegistry(peersDir);

            InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
            TruthcrawlServer server = new TruthcrawlServer(
                    address, recordStore, batchesDir, peerRegistry, key);

            server.start();
            System.out.println("Server started on port " + port);

            // Block until interrupted
            Thread.currentThread().join();
            return 0;

        } catch (InterruptedException e) {
            System.out.println("Server stopped");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
