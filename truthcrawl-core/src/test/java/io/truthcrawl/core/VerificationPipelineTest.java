package io.truthcrawl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificationPipelineTest {

    @TempDir
    Path tempDir;

    private ObservationRecord makeRecord(String url, String nodeId,
                                          String contentHash, int statusCode) {
        return ObservationRecord.builder()
                .version("0.1")
                .observedAt(Instant.parse("2024-01-15T12:00:00Z"))
                .url(url)
                .finalUrl(url)
                .statusCode(statusCode)
                .fetchMs(100)
                .contentHash(contentHash)
                .nodeId(nodeId)
                .build();
    }

    @Test
    void all_matched() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        // Batch records
        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        ObservationRecord r2 = makeRecord("https://b.com", "node1", "b".repeat(64), 200);
        store.store(r1);
        store.store(r2);

        // Independent observations (same content, different nodes)
        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "a".repeat(64), 200);
        ObservationRecord ind2 = makeRecord("https://b.com", "node2", "b".repeat(64), 200);
        store.store(ind1);
        store.store(ind2);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash(), r2.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        assertEquals(2, result.report().recordsMatched());
        assertEquals(0, result.report().recordsMismatched());
        assertTrue(result.mismatchHashes().isEmpty());
    }

    @Test
    void detects_mismatch() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        // Independent observation with different content_hash
        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "c".repeat(64), 200);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        assertEquals(0, result.report().recordsMatched());
        assertEquals(1, result.report().recordsMismatched());
        assertEquals(1, result.mismatchHashes().size());
        assertEquals(r1.recordHash(), result.mismatchHashes().get(0));
    }

    @Test
    void unverifiable_when_no_independent_observations() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        // No independent observations, so unverifiable
        assertEquals(0, result.report().recordsMatched());
        assertEquals(0, result.report().recordsMismatched());
        assertEquals(1, result.details().size());
        assertEquals(VerificationPipeline.RecordStatus.UNVERIFIABLE,
                result.details().get(0).status());
    }

    @Test
    void skips_same_node_observations() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        // Another observation from the SAME node — should not count
        ObservationRecord sameNode = makeRecord("https://a.com", "node1", "d".repeat(64), 200);
        store.store(sameNode);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        assertEquals(VerificationPipeline.RecordStatus.UNVERIFIABLE,
                result.details().get(0).status());
    }

    @Test
    void respects_min_observations_threshold() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        // Only 1 independent observation
        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "a".repeat(64), 200);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        // Require 2 independent observations
        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed",
                        store, 10, 2);

        assertEquals(VerificationPipeline.RecordStatus.UNVERIFIABLE,
                result.details().get(0).status());
    }

    @Test
    void mixed_matched_and_unverifiable() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        ObservationRecord r2 = makeRecord("https://b.com", "node1", "b".repeat(64), 200);
        store.store(r1);
        store.store(r2);

        // Independent observation only for URL a
        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "a".repeat(64), 200);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash(), r2.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        assertEquals(1, result.report().recordsMatched());
        assertEquals(0, result.report().recordsMismatched());
        // One detail should be UNVERIFIABLE
        long unverifiable = result.details().stream()
                .filter(d -> d.status() == VerificationPipeline.RecordStatus.UNVERIFIABLE)
                .count();
        assertEquals(1, unverifiable);
    }

    @Test
    void mismatch_includes_discrepancies() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        // Independent observation with different status code
        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "a".repeat(64), 404);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        VerificationPipeline.RecordDetail detail = result.details().get(0);
        assertEquals(VerificationPipeline.RecordStatus.MISMATCHED, detail.status());
        assertFalse(detail.discrepancies().isEmpty());
        assertTrue(detail.discrepancies().stream()
                .anyMatch(d -> d.field().equals("status_code")));
    }

    @Test
    void disputes_filed_is_zero_in_report() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "c".repeat(64), 200);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        // Pipeline doesn't auto-file disputes
        assertEquals(0, result.report().disputesFiled());
    }

    @Test
    void deterministic_results() throws IOException {
        RecordStore store = new RecordStore(tempDir.resolve("store"));

        ObservationRecord r1 = makeRecord("https://a.com", "node1", "a".repeat(64), 200);
        store.store(r1);

        ObservationRecord ind1 = makeRecord("https://a.com", "node2", "a".repeat(64), 200);
        store.store(ind1);

        BatchManifest manifest = BatchManifest.of(List.of(r1.recordHash()));
        String merkleRoot = manifest.merkleRoot();

        VerificationPipeline.PipelineResult r1Result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);
        VerificationPipeline.PipelineResult r2Result =
                VerificationPipeline.run("2024-01-15", manifest, merkleRoot, "seed", store);

        assertEquals(r1Result.report().recordsMatched(), r2Result.report().recordsMatched());
        assertEquals(r1Result.report().recordsMismatched(), r2Result.report().recordsMismatched());
        assertEquals(r1Result.details().size(), r2Result.details().size());
    }
}
