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

class BatchExporterTest {

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

    private ExportFixture createExportFixture() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64));
        ObservationRecord r2 = makeRecord("https://b.com", "node2", "b".repeat(64));
        String h1 = store.store(r1);
        String h2 = store.store(r2);

        BatchManifest manifest = BatchManifest.of(List.of(h1, h2));
        ChainLink chainLink = ChainLink.fromManifest("2024-01-15", manifest, ChainLink.GENESIS_ROOT);

        PublisherKey key = PublisherKey.generate();
        String signature = key.sign(chainLink.signingInput());

        return new ExportFixture(store, manifest, chainLink, signature, key);
    }

    record ExportFixture(RecordStore store, BatchManifest manifest,
                         ChainLink chainLink, String signature, PublisherKey key) {}

    @Test
    void exports_complete_directory() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        assertTrue(Files.exists(exportDir.resolve("metadata.txt")));
        assertTrue(Files.exists(exportDir.resolve("manifest.txt")));
        assertTrue(Files.exists(exportDir.resolve("chain-link.txt")));
        assertTrue(Files.exists(exportDir.resolve("signature.txt")));
        assertTrue(Files.isDirectory(exportDir.resolve("records")));
    }

    @Test
    void export_directory_name_contains_batch_id() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        assertEquals("batch-2024-01-15", exportDir.getFileName().toString());
    }

    @Test
    void metadata_is_parseable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        List<String> lines = Files.readAllLines(exportDir.resolve("metadata.txt"), StandardCharsets.UTF_8);
        BatchMetadata parsed = BatchMetadata.parse(lines);
        assertEquals("2024-01-15", parsed.batchId());
        assertEquals(f.chainLink.merkleRoot(), parsed.merkleRoot());
        assertEquals(f.chainLink.manifestHash(), parsed.manifestHash());
        assertEquals(f.chainLink.recordCount(), parsed.recordCount());
    }

    @Test
    void manifest_is_parseable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        List<String> lines = Files.readAllLines(exportDir.resolve("manifest.txt"), StandardCharsets.UTF_8);
        BatchManifest parsed = BatchManifest.parse(lines);
        assertEquals(f.manifest.entries(), parsed.entries());
    }

    @Test
    void chain_link_is_parseable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        List<String> lines = Files.readAllLines(exportDir.resolve("chain-link.txt"), StandardCharsets.UTF_8);
        ChainLink parsed = ChainLink.parse(lines);
        assertEquals(f.chainLink, parsed);
    }

    @Test
    void signature_is_readable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        String sig = Files.readString(exportDir.resolve("signature.txt"), StandardCharsets.UTF_8).strip();
        assertEquals(f.signature, sig);
    }

    @Test
    void all_records_are_exported() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        for (String hash : f.manifest.entries()) {
            assertTrue(Files.exists(exportDir.resolve("records").resolve(hash + ".txt")));
        }
    }

    @Test
    void exported_records_are_parseable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        for (String hash : f.manifest.entries()) {
            List<String> lines = Files.readAllLines(
                    exportDir.resolve("records").resolve(hash + ".txt"), StandardCharsets.UTF_8);
            ObservationRecord record = ObservationRecord.parse(lines);
            assertEquals(hash, record.recordHash());
        }
    }

    @Test
    void export_is_self_verifiable() throws IOException {
        ExportFixture f = createExportFixture();
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path exportDir = BatchExporter.export(outputDir, f.chainLink, f.manifest, f.signature, f.store);

        // Verify signature from export alone
        String sig = Files.readString(exportDir.resolve("signature.txt"), StandardCharsets.UTF_8).strip();
        ChainLink link = ChainLink.parse(
                Files.readAllLines(exportDir.resolve("chain-link.txt"), StandardCharsets.UTF_8));
        assertTrue(f.key.verify(link.signingInput(), sig));
    }

    @Test
    void throws_when_record_missing_from_store() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64));
        store.store(r1);

        // Create manifest with a hash not in the store
        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash(), "f".repeat(64)));
        ChainLink chainLink = ChainLink.fromManifest("2024-01-15", manifest, ChainLink.GENESIS_ROOT);

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        assertThrows(IllegalArgumentException.class, () ->
                BatchExporter.export(outputDir, chainLink, manifest, "sig", store));
    }
}
