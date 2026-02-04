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
 * Persistent storage for {@link TimestampToken}s.
 *
 * <p>Directory layout: {@code timestamps/{data-hash}.txt}
 *
 * <p>Rules:
 * <ul>
 *   <li>One token per data hash (first-write-wins)</li>
 *   <li>Tokens are never modified or deleted (append-only)</li>
 *   <li>Re-storing the same data hash is a no-op</li>
 * </ul>
 */
public final class TimestampStore {

    private final Path root;

    /**
     * Create a timestamp store rooted at the given directory.
     *
     * @param root the store root directory (will be created if absent)
     */
    public TimestampStore(Path root) {
        this.root = root;
    }

    /**
     * The root directory of this store.
     */
    public Path root() {
        return root;
    }

    /**
     * Store a timestamp token. First-write-wins: if a token for this data hash
     * already exists, this is a no-op.
     *
     * @param token the token to store
     * @throws IOException if writing fails
     */
    public void store(TimestampToken token) throws IOException {
        Path file = pathFor(token.dataHash());
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(root);
        Files.writeString(file, token.toCanonicalText(), StandardCharsets.UTF_8);
    }

    /**
     * Load a timestamp token by data hash.
     *
     * @param dataHash 64-char lowercase hex data hash
     * @return the token, or null if not found
     * @throws IOException if reading fails
     */
    public TimestampToken load(String dataHash) throws IOException {
        Path file = pathFor(dataHash);
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return TimestampToken.parse(lines);
    }

    /**
     * Check if a timestamp exists for the given data hash.
     */
    public boolean contains(String dataHash) {
        return Files.exists(pathFor(dataHash));
    }

    /**
     * List all timestamped data hashes, sorted.
     *
     * @return sorted list of data hashes
     * @throws IOException if directory traversal fails
     */
    public List<String> listHashes() throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        List<String> hashes = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            files
                    .filter(f -> f.toString().endsWith(".txt"))
                    .forEach(f -> {
                        String name = f.getFileName().toString();
                        hashes.add(name.substring(0, name.length() - 4));
                    });
        }

        Collections.sort(hashes);
        return Collections.unmodifiableList(hashes);
    }

    /**
     * Total number of tokens in the store.
     */
    public int size() throws IOException {
        return listHashes().size();
    }

    private Path pathFor(String dataHash) {
        return root.resolve(dataHash + ".txt");
    }
}
