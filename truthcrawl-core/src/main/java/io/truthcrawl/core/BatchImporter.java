package io.truthcrawl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports a batch export directory into a local record store.
 *
 * <p>Validates integrity on import:
 * <ul>
 *   <li>Signature is valid against the publisher's public key</li>
 *   <li>manifest_hash matches computed hash of manifest</li>
 *   <li>merkle_root matches computed root of manifest entries</li>
 *   <li>record_count matches manifest size</li>
 *   <li>All manifest entries have corresponding record files</li>
 *   <li>Record hashes match their manifest entries</li>
 * </ul>
 *
 * <p>Records are stored in the local RecordStore (idempotent).
 * Import never modifies existing records.
 *
 * @see BatchExporter
 */
public final class BatchImporter {

    private BatchImporter() {}

    /**
     * Receipt produced after a batch import.
     *
     * @param batchId              the batch identifier
     * @param recordsImported      number of new records stored
     * @param recordsAlreadyPresent number of records that were already in the store
     * @param valid                whether all validation checks passed
     * @param errors               validation error messages (empty if valid)
     */
    public record ImportReceipt(
            String batchId,
            int recordsImported,
            int recordsAlreadyPresent,
            boolean valid,
            List<String> errors
    ) {
        /**
         * Canonical text representation.
         */
        public String toCanonicalText() {
            return "batch_id:" + batchId + "\n"
                    + "records_imported:" + recordsImported + "\n"
                    + "records_already_present:" + recordsAlreadyPresent + "\n"
                    + "valid:" + valid + "\n";
        }
    }

    /**
     * Import a batch from an export directory.
     *
     * @param exportDir  the batch export directory (e.g., batch-2024-01-15/)
     * @param store      the local record store to import into
     * @param publisherKey the publisher's public key for signature verification
     * @return an import receipt summarizing the result
     * @throws IOException if reading files fails
     */
    public static ImportReceipt importBatch(Path exportDir, RecordStore store,
                                             PublisherKey publisherKey) throws IOException {
        List<String> errors = new ArrayList<>();

        // Read files
        String metadataText = Files.readString(exportDir.resolve("metadata.txt"), StandardCharsets.UTF_8);
        String manifestText = Files.readString(exportDir.resolve("manifest.txt"), StandardCharsets.UTF_8);
        String chainLinkText = Files.readString(exportDir.resolve("chain-link.txt"), StandardCharsets.UTF_8);
        String signature = Files.readString(exportDir.resolve("signature.txt"), StandardCharsets.UTF_8).strip();

        // Parse
        BatchMetadata metadata;
        BatchManifest manifest;
        ChainLink chainLink;
        try {
            metadata = BatchMetadata.parse(List.of(metadataText.split("\n")));
        } catch (Exception e) {
            errors.add("Failed to parse metadata: " + e.getMessage());
            return new ImportReceipt(extractBatchId(exportDir), 0, 0, false, List.copyOf(errors));
        }
        try {
            manifest = BatchManifest.parse(List.of(manifestText.split("\n")));
        } catch (Exception e) {
            errors.add("Failed to parse manifest: " + e.getMessage());
            return new ImportReceipt(metadata.batchId(), 0, 0, false, List.copyOf(errors));
        }
        try {
            chainLink = ChainLink.parse(List.of(chainLinkText.split("\n")));
        } catch (Exception e) {
            errors.add("Failed to parse chain link: " + e.getMessage());
            return new ImportReceipt(metadata.batchId(), 0, 0, false, List.copyOf(errors));
        }

        // Validate signature
        boolean sigValid = publisherKey.verify(chainLink.signingInput(), signature);
        if (!sigValid) {
            errors.add("Invalid publisher signature");
        }

        // Validate manifest_hash
        if (!metadata.manifestHash().equals(manifest.manifestHash())) {
            errors.add("manifest_hash mismatch: metadata says " + metadata.manifestHash()
                    + " but manifest computes " + manifest.manifestHash());
        }

        // Validate merkle_root
        if (!metadata.merkleRoot().equals(manifest.merkleRoot())) {
            errors.add("merkle_root mismatch: metadata says " + metadata.merkleRoot()
                    + " but manifest computes " + manifest.merkleRoot());
        }

        // Validate record_count
        if (metadata.recordCount() != manifest.size()) {
            errors.add("record_count mismatch: metadata says " + metadata.recordCount()
                    + " but manifest has " + manifest.size());
        }

        // Validate and import records
        Path recordsDir = exportDir.resolve("records");
        int imported = 0;
        int alreadyPresent = 0;

        for (String hash : manifest.entries()) {
            Path recordFile = recordsDir.resolve(hash + ".txt");
            if (!Files.exists(recordFile)) {
                errors.add("Missing record file: " + hash);
                continue;
            }

            try {
                List<String> lines = Files.readAllLines(recordFile, StandardCharsets.UTF_8);
                ObservationRecord record = ObservationRecord.parse(lines);

                // Verify record hash matches manifest entry
                String computedHash = record.recordHash();
                if (!computedHash.equals(hash)) {
                    errors.add("Record hash mismatch for " + hash
                            + ": file computes " + computedHash);
                    continue;
                }

                // Store if validation passed so far
                if (errors.isEmpty()) {
                    if (store.contains(computedHash)) {
                        alreadyPresent++;
                    } else {
                        store.store(record);
                        imported++;
                    }
                }
            } catch (Exception e) {
                errors.add("Failed to parse record " + hash + ": " + e.getMessage());
            }
        }

        boolean valid = errors.isEmpty();
        return new ImportReceipt(metadata.batchId(), imported, alreadyPresent,
                valid, List.copyOf(errors));
    }

    private static String extractBatchId(Path exportDir) {
        String dirName = exportDir.getFileName().toString();
        if (dirName.startsWith("batch-")) {
            return dirName.substring(6);
        }
        return dirName;
    }
}
