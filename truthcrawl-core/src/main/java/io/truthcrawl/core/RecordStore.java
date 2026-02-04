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
 * Hash-addressed file storage for observation records.
 *
 * <p>Directory layout: {@code store/{first-2-hex}/{full-hash}.txt}
 *
 * <p>Rules:
 * <ul>
 *   <li>Storing an existing record is a no-op (idempotent)</li>
 *   <li>Records are never modified or deleted (append-only)</li>
 *   <li>Records are stored as full text (including signature line)</li>
 * </ul>
 */
public final class RecordStore {

    private final Path root;

    /**
     * Create a record store rooted at the given directory.
     *
     * @param root the store root directory (will be created if absent)
     */
    public RecordStore(Path root) {
        this.root = root;
    }

    /**
     * The root directory of this store.
     */
    public Path root() {
        return root;
    }

    /**
     * Store an observation record. Idempotent: if the record already exists, this is a no-op.
     *
     * @param record the record to store
     * @return the record hash
     * @throws IOException if writing fails
     */
    public String store(ObservationRecord record) throws IOException {
        String hash = record.recordHash();
        Path file = pathFor(hash);

        if (Files.exists(file)) {
            return hash;
        }

        Files.createDirectories(file.getParent());
        Files.writeString(file, record.toFullText(), StandardCharsets.UTF_8);
        return hash;
    }

    /**
     * Load a record by its hash.
     *
     * @param recordHash 64-char lowercase hex record hash
     * @return the record, or null if not found
     * @throws IOException if reading fails
     */
    public ObservationRecord load(String recordHash) throws IOException {
        Path file = pathFor(recordHash);
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return ObservationRecord.parse(lines);
    }

    /**
     * Check if a record exists in the store.
     */
    public boolean contains(String recordHash) {
        return Files.exists(pathFor(recordHash));
    }

    /**
     * List all record hashes in the store, sorted.
     *
     * @return sorted list of record hashes
     * @throws IOException if directory traversal fails
     */
    public List<String> listHashes() throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        List<String> hashes = new ArrayList<>();
        try (Stream<Path> prefixDirs = Files.list(root)) {
            prefixDirs
                    .filter(Files::isDirectory)
                    .forEach(prefixDir -> {
                        try (Stream<Path> files = Files.list(prefixDir)) {
                            files
                                    .filter(f -> f.toString().endsWith(".txt"))
                                    .forEach(f -> {
                                        String name = f.getFileName().toString();
                                        hashes.add(name.substring(0, name.length() - 4));
                                    });
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        Collections.sort(hashes);
        return Collections.unmodifiableList(hashes);
    }

    /**
     * Total number of records in the store.
     */
    public int size() throws IOException {
        return listHashes().size();
    }

    /**
     * Compute the file path for a given record hash.
     */
    Path pathFor(String recordHash) {
        String prefix = recordHash.substring(0, 2);
        return root.resolve(prefix).resolve(recordHash + ".txt");
    }
}
