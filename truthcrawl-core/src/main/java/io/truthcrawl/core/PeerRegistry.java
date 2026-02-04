package io.truthcrawl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persistent file-based storage for {@link PeerInfo} records.
 *
 * <p>Directory layout: {@code peers/{node-id}.txt}
 *
 * <p>Rules:
 * <ul>
 *   <li>Lookup by nodeId returns PeerInfo or null</li>
 *   <li>Re-registering the same nodeId overwrites (endpoint may change)</li>
 *   <li>Listing peers returns a sorted list of nodeIds</li>
 * </ul>
 */
public final class PeerRegistry {

    private final Path root;

    /**
     * Create a peer registry rooted at the given directory.
     *
     * @param root the registry root directory (will be created if absent)
     */
    public PeerRegistry(Path root) {
        this.root = root;
    }

    /**
     * The root directory of this registry.
     */
    public Path root() {
        return root;
    }

    /**
     * Register a peer. Overwrites if the nodeId already exists.
     *
     * @param peer the peer info to register
     * @throws IOException if writing fails
     */
    public void register(PeerInfo peer) throws IOException {
        Files.createDirectories(root);
        Files.writeString(pathFor(peer.nodeId()), peer.toCanonicalText(), StandardCharsets.UTF_8);
    }

    /**
     * Load a peer by nodeId.
     *
     * @param nodeId 64-char lowercase hex node ID
     * @return the peer info, or null if not found
     * @throws IOException if reading fails
     */
    public PeerInfo load(String nodeId) throws IOException {
        Path file = pathFor(nodeId);
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return PeerInfo.parse(lines);
    }

    /**
     * Check if a peer is registered.
     */
    public boolean contains(String nodeId) {
        return Files.exists(pathFor(nodeId));
    }

    /**
     * List all registered peer nodeIds, sorted.
     *
     * @return sorted list of nodeIds
     * @throws IOException if directory traversal fails
     */
    public List<String> listNodeIds() throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            files
                    .filter(f -> f.toString().endsWith(".txt"))
                    .forEach(f -> {
                        String name = f.getFileName().toString();
                        ids.add(name.substring(0, name.length() - 4));
                    });
        }

        Collections.sort(ids);
        return Collections.unmodifiableList(ids);
    }

    /**
     * Total number of registered peers.
     */
    public int size() throws IOException {
        return listNodeIds().size();
    }

    private Path pathFor(String nodeId) {
        return root.resolve(nodeId + ".txt");
    }
}
