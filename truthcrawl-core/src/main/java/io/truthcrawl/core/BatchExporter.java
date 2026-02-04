package io.truthcrawl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports a published batch into a self-contained directory.
 *
 * <p>Export layout:
 * <pre>
 * batch-{batchId}/
 *   metadata.txt        (BatchMetadata canonical text)
 *   manifest.txt        (BatchManifest canonical text)
 *   chain-link.txt      (ChainLink canonical text)
 *   signature.txt       (Base64 publisher signature, single line)
 *   records/
 *     {hash}.txt        (ObservationRecord full text, one per manifest entry)
 * </pre>
 *
 * <p>The exported directory is self-verifiable: given the publisher's public key,
 * the entire batch can be verified from the export alone.
 *
 * <p>Exporting is deterministic: same inputs always produce byte-identical files.
 */
public final class BatchExporter {

    private BatchExporter() {}

    /**
     * Export a batch to a directory.
     *
     * @param outputParent parent directory where batch-{batchId}/ will be created
     * @param chainLink    the chain link for this batch
     * @param manifest     the batch manifest
     * @param signature    the publisher's Base64 signature
     * @param store        the record store containing the records
     * @return the path to the created export directory
     * @throws IOException              if writing fails
     * @throws IllegalArgumentException if any manifest entry is missing from the store
     */
    public static Path export(Path outputParent, ChainLink chainLink,
                              BatchManifest manifest, String signature,
                              RecordStore store) throws IOException {

        String batchId = chainLink.batchId();
        Path exportDir = outputParent.resolve("batch-" + batchId);
        Path recordsDir = exportDir.resolve("records");
        Files.createDirectories(recordsDir);

        // Write metadata (derived from chain link)
        BatchMetadata metadata = new BatchMetadata(
                chainLink.batchId(),
                chainLink.merkleRoot(),
                chainLink.manifestHash(),
                chainLink.recordCount()
        );
        Files.writeString(
                exportDir.resolve("metadata.txt"),
                metadata.toCanonicalText(),
                StandardCharsets.UTF_8);

        // Write manifest
        Files.writeString(
                exportDir.resolve("manifest.txt"),
                manifest.toCanonicalText(),
                StandardCharsets.UTF_8);

        // Write chain link
        Files.writeString(
                exportDir.resolve("chain-link.txt"),
                chainLink.toCanonicalText(),
                StandardCharsets.UTF_8);

        // Write signature (single line, no trailing newline for clean reading)
        Files.writeString(
                exportDir.resolve("signature.txt"),
                signature + "\n",
                StandardCharsets.UTF_8);

        // Write all records
        for (String hash : manifest.entries()) {
            ObservationRecord record = store.load(hash);
            if (record == null) {
                throw new IllegalArgumentException(
                        "Record not found in store: " + hash);
            }
            Files.writeString(
                    recordsDir.resolve(hash + ".txt"),
                    record.toFullText(),
                    StandardCharsets.UTF_8);
        }

        return exportDir;
    }
}
