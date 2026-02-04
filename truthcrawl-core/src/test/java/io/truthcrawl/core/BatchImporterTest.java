package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchImporterTest {

    @TempDir
    Path tempDir;

    private ObservationRecord makeRecord(String url, String nodeId, String contentHash) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url(url)
                .finalUrl(url)
                .statusCode(200)
                .fetchMs(100)
                .contentHash(contentHash)
                .nodeId(nodeId)
                .build();
    }

    private Path createValidExport(PublisherKey key) throws IOException {
        RecordStore sourceStore = new RecordStore(tempDir.resolve("source-store"));
        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64));
        ObservationRecord r2 = makeRecord("https://b.com", "node2", "b".repeat(64));
        sourceStore.store(r1);
        sourceStore.store(r2);

        BatchManifest manifest = BatchManifest.of(
                List.of(r1.recordHash(), r2.recordHash()));
        ChainLink chainLink = ChainLink.fromManifest("2024-01-15", manifest, ChainLink.GENESIS_ROOT);
        String signature = key.sign(chainLink.signingInput());

        Path outputDir = tempDir.resolve("exports");
        Files.createDirectories(outputDir);
        return BatchExporter.export(outputDir, chainLink, manifest, signature, sourceStore);
    }

    @Test
    void imports_valid_batch() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);
        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));

        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        assertTrue(receipt.valid());
        assertEquals("2024-01-15", receipt.batchId());
        assertEquals(2, receipt.recordsImported());
        assertEquals(0, receipt.recordsAlreadyPresent());
        assertTrue(receipt.errors().isEmpty());
    }

    @Test
    void idempotent_reimport() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);
        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));

        BatchImporter.importBatch(exportDir, importStore, key);
        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        assertTrue(receipt.valid());
        assertEquals(0, receipt.recordsImported());
        assertEquals(2, receipt.recordsAlreadyPresent());
    }

    @Test
    void rejects_wrong_publisher_key() throws IOException {
        PublisherKey signingKey = PublisherKey.generate();
        PublisherKey wrongKey = PublisherKey.generate();
        Path exportDir = createValidExport(signingKey);
        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));

        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, wrongKey);

        assertFalse(receipt.valid());
        assertTrue(receipt.errors().stream().anyMatch(e -> e.contains("signature")));
    }

    @Test
    void rejects_tampered_manifest() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);

        // Tamper with the manifest file
        Path manifestFile = exportDir.resolve("manifest.txt");
        Files.writeString(manifestFile, "f".repeat(64) + "\n", StandardCharsets.UTF_8);

        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));
        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        assertFalse(receipt.valid());
    }

    @Test
    void rejects_missing_record_file() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);

        // Delete one record file
        Path recordsDir = exportDir.resolve("records");
        Files.list(recordsDir).findFirst().ifPresent(f -> {
            try { Files.delete(f); } catch (IOException e) { throw new RuntimeException(e); }
        });

        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));
        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        assertFalse(receipt.valid());
        assertTrue(receipt.errors().stream().anyMatch(e -> e.contains("Missing record")));
    }

    @Test
    void receipt_has_canonical_text() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);
        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));

        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        String text = receipt.toCanonicalText();
        assertTrue(text.contains("batch_id:2024-01-15"));
        assertTrue(text.contains("records_imported:2"));
        assertTrue(text.contains("records_already_present:0"));
        assertTrue(text.contains("valid:true"));
    }

    @Test
    void does_not_store_records_when_validation_fails() throws IOException {
        PublisherKey signingKey = PublisherKey.generate();
        PublisherKey wrongKey = PublisherKey.generate();
        Path exportDir = createValidExport(signingKey);
        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));

        BatchImporter.importBatch(exportDir, importStore, wrongKey);

        assertEquals(0, importStore.size());
    }

    @Test
    void rejects_record_with_wrong_hash() throws IOException {
        PublisherKey key = PublisherKey.generate();
        Path exportDir = createValidExport(key);

        // Overwrite a record file with different content but keep the filename
        Path recordsDir = exportDir.resolve("records");
        Path firstRecord = Files.list(recordsDir).sorted().findFirst().orElseThrow();
        ObservationRecord fake = makeRecord("https://fake.com", "fakenode", "f".repeat(64));
        Files.writeString(firstRecord, fake.toFullText(), StandardCharsets.UTF_8);

        RecordStore importStore = new RecordStore(tempDir.resolve("import-store"));
        BatchImporter.ImportReceipt receipt =
                BatchImporter.importBatch(exportDir, importStore, key);

        assertFalse(receipt.valid());
        assertTrue(receipt.errors().stream().anyMatch(e -> e.contains("hash mismatch")));
    }
}
