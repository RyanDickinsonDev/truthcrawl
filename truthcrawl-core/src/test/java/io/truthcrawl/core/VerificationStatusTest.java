package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificationStatusTest {

    @TempDir
    Path tempDir;

    @Test
    void canonical_text_format() {
        VerificationStatus status = new VerificationStatus(
                "2024-01-15",
                VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                100, 10, 10, 0, 0,
                Instant.parse("2024-01-16T10:00:00Z"));

        String text = status.toCanonicalText();
        assertTrue(text.contains("batch_id:2024-01-15"));
        assertTrue(text.contains("batch_status:VERIFIED_CLEAN"));
        assertTrue(text.contains("records_total:100"));
        assertTrue(text.contains("records_checked:10"));
        assertTrue(text.contains("records_matched:10"));
        assertTrue(text.contains("records_mismatched:0"));
        assertTrue(text.contains("records_unverifiable:0"));
        assertTrue(text.contains("checked_at:2024-01-16T10:00:00Z"));
    }

    @Test
    void parse_roundtrip() {
        VerificationStatus original = new VerificationStatus(
                "2024-01-15",
                VerificationStatus.BatchStatus.VERIFIED_WITH_DISPUTES,
                100, 10, 8, 2, 5,
                Instant.parse("2024-01-16T10:00:00Z"));

        List<String> lines = List.of(original.toCanonicalText().split("\n"));
        VerificationStatus parsed = VerificationStatus.parse(lines);

        assertEquals(original.batchId(), parsed.batchId());
        assertEquals(original.batchStatus(), parsed.batchStatus());
        assertEquals(original.recordsTotal(), parsed.recordsTotal());
        assertEquals(original.recordsChecked(), parsed.recordsChecked());
        assertEquals(original.recordsMatched(), parsed.recordsMatched());
        assertEquals(original.recordsMismatched(), parsed.recordsMismatched());
        assertEquals(original.recordsUnverifiable(), parsed.recordsUnverifiable());
        assertEquals(original.checkedAt(), parsed.checkedAt());
    }

    @Test
    void hash_determinism() {
        VerificationStatus s1 = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                100, 10, 10, 0, 0, Instant.parse("2024-01-16T10:00:00Z"));
        VerificationStatus s2 = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                100, 10, 10, 0, 0, Instant.parse("2024-01-16T10:00:00Z"));

        assertEquals(s1.statusHash(), s2.statusHash());
        assertEquals(64, s1.statusHash().length());
    }

    @Test
    void different_status_different_hash() {
        VerificationStatus s1 = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                100, 10, 10, 0, 0, Instant.parse("2024-01-16T10:00:00Z"));
        VerificationStatus s2 = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_WITH_DISPUTES,
                100, 10, 8, 2, 0, Instant.parse("2024-01-16T10:00:00Z"));

        assertNotEquals(s1.statusHash(), s2.statusHash());
    }

    @Test
    void save_and_load() throws IOException {
        Path verDir = tempDir.resolve("verification");
        VerificationStatus status = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                50, 10, 10, 0, 0, Instant.parse("2024-01-16T10:00:00Z"));

        status.save(verDir);

        VerificationStatus loaded = VerificationStatus.load(verDir, "2024-01-15");
        assertNotNull(loaded);
        assertEquals(status.batchId(), loaded.batchId());
        assertEquals(status.batchStatus(), loaded.batchStatus());
    }

    @Test
    void load_returns_null_when_not_found() throws IOException {
        Path verDir = tempDir.resolve("verification");
        Files.createDirectories(verDir);

        assertNull(VerificationStatus.load(verDir, "2024-99-99"));
    }

    @Test
    void from_pipeline_result_clean() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://a.com").finalUrl("https://a.com")
                .statusCode(200).fetchMs(100)
                .contentHash("a".repeat(64)).nodeId("node1").build();
        ObservationRecord ind = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://a.com").finalUrl("https://a.com")
                .statusCode(200).fetchMs(100)
                .contentHash("a".repeat(64)).nodeId("node2").build();
        store.store(r1);
        store.store(ind);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, manifest.merkleRoot(), "seed", store);

        VerificationStatus status = VerificationStatus.fromPipelineResult(
                result, Instant.parse("2024-01-16T10:00:00Z"));

        assertEquals(VerificationStatus.BatchStatus.VERIFIED_CLEAN, status.batchStatus());
        assertEquals(1, status.recordsMatched());
        assertEquals(0, status.recordsMismatched());
    }

    @Test
    void from_pipeline_result_with_disputes() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://a.com").finalUrl("https://a.com")
                .statusCode(200).fetchMs(100)
                .contentHash("a".repeat(64)).nodeId("node1").build();
        ObservationRecord ind = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://a.com").finalUrl("https://a.com")
                .statusCode(200).fetchMs(100)
                .contentHash("c".repeat(64)).nodeId("node2").build();
        store.store(r1);
        store.store(ind);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, manifest.merkleRoot(), "seed", store);

        VerificationStatus status = VerificationStatus.fromPipelineResult(
                result, Instant.parse("2024-01-16T10:00:00Z"));

        assertEquals(VerificationStatus.BatchStatus.VERIFIED_WITH_DISPUTES, status.batchStatus());
    }

    @Test
    void from_pipeline_result_unverifiable() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));
        ObservationRecord r1 = ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url("https://a.com").finalUrl("https://a.com")
                .statusCode(200).fetchMs(100)
                .contentHash("a".repeat(64)).nodeId("node1").build();
        store.store(r1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, manifest.merkleRoot(), "seed", store);

        VerificationStatus status = VerificationStatus.fromPipelineResult(
                result, Instant.parse("2024-01-16T10:00:00Z"));

        assertEquals(VerificationStatus.BatchStatus.UNVERIFIABLE, status.batchStatus());
    }

    @Test
    void rejects_mismatched_counts() {
        assertThrows(IllegalArgumentException.class, () ->
                new VerificationStatus("2024-01-15",
                        VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                        100, 10, 5, 3, 0,
                        Instant.parse("2024-01-16T10:00:00Z")));
    }

    @Test
    void format_report() {
        VerificationStatus status = new VerificationStatus(
                "2024-01-15", VerificationStatus.BatchStatus.VERIFIED_CLEAN,
                100, 10, 10, 0, 2, Instant.parse("2024-01-16T10:00:00Z"));

        String report = status.formatReport();
        assertTrue(report.contains("VERIFIED_CLEAN"));
        assertTrue(report.contains("Matched:       10"));
        assertTrue(report.contains("Unverifiable:  2"));
    }
}
