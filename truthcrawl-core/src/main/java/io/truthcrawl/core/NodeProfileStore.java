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
 * Persistent storage for {@link NodeProfile}s.
 *
 * <p>Directory layout: {@code profiles/{node-id}.txt}
 *
 * <p>Rules:
 * <ul>
 *   <li>One profile per node ID</li>
 *   <li>Profiles can be updated (e.g. adding an attestation)</li>
 *   <li>Profiles can be looked up by node ID (exact match)</li>
 *   <li>Listing profiles returns a sorted list of node IDs</li>
 * </ul>
 */
public final class NodeProfileStore {

    private final Path root;

    /**
     * Create a profile store rooted at the given directory.
     *
     * @param root the store root directory (will be created if absent)
     */
    public NodeProfileStore(Path root) {
        this.root = root;
    }

    /**
     * The root directory of this store.
     */
    public Path root() {
        return root;
    }

    /**
     * Store a node profile. Overwrites any existing profile for this node ID.
     *
     * @param profile the profile to store
     * @throws IOException if writing fails
     */
    public void store(NodeProfile profile) throws IOException {
        Path file = pathFor(profile.nodeId());
        Files.createDirectories(root);
        Files.writeString(file, profile.toCanonicalText(), StandardCharsets.UTF_8);
    }

    /**
     * Load a node profile by node ID.
     *
     * @param nodeId 64-char lowercase hex node ID
     * @return the profile, or null if not found
     * @throws IOException if reading fails
     */
    public NodeProfile load(String nodeId) throws IOException {
        Path file = pathFor(nodeId);
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return NodeProfile.parse(lines);
    }

    /**
     * Check if a profile exists for the given node ID.
     */
    public boolean contains(String nodeId) {
        return Files.exists(pathFor(nodeId));
    }

    /**
     * List all node IDs with stored profiles, sorted.
     *
     * @return sorted list of node IDs
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
     * Total number of profiles in the store.
     */
    public int size() throws IOException {
        return listNodeIds().size();
    }

    private Path pathFor(String nodeId) {
        return root.resolve(nodeId + ".txt");
    }
}
