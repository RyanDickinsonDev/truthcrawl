package io.truthcrawl.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates end-to-end verification of an imported batch.
 *
 * <p>Pipeline steps:
 * <ol>
 *   <li>Sample records from the batch manifest (using {@link VerificationSampler})</li>
 *   <li>For each sampled record, look up independent observations of the same URL
 *       from the local record store (different node_id required)</li>
 *   <li>Compare sampled records against independent observations (using {@link RecordComparator})</li>
 *   <li>Generate an {@link AuditReport} summarizing matches/mismatches</li>
 * </ol>
 *
 * <p>Records with no independent observations are marked as UNVERIFIABLE.
 * Only records that were actually compared count toward matched/mismatched in the report.
 */
public final class VerificationPipeline {

    /** Default minimum independent observations required per URL. */
    public static final int DEFAULT_MIN_OBSERVATIONS = 1;

    private VerificationPipeline() {}

    /**
     * Status of a single record's verification.
     */
    public enum RecordStatus {
        MATCHED,
        MISMATCHED,
        UNVERIFIABLE
    }

    /**
     * Detail for a single record's verification.
     *
     * @param recordHash   the record hash from the manifest
     * @param status       verification outcome
     * @param discrepancies field-level discrepancies (empty if matched or unverifiable)
     */
    public record RecordDetail(
            String recordHash,
            RecordStatus status,
            List<RecordComparator.Discrepancy> discrepancies
    ) {}

    /**
     * Full pipeline result.
     *
     * @param report         the audit report (counts only verified records)
     * @param details        per-record verification details (one per sampled record)
     * @param mismatchHashes record hashes that had discrepancies (for dispute filing)
     */
    public record PipelineResult(
            AuditReport report,
            List<RecordDetail> details,
            List<String> mismatchHashes
    ) {}

    /**
     * Run the verification pipeline with default settings.
     *
     * @param batchId    the batch identifier (YYYY-MM-DD)
     * @param manifest   the batch manifest
     * @param merkleRoot the batch's Merkle root
     * @param userSeed   seed for deterministic sampling
     * @param store      the local record store
     * @return pipeline result
     * @throws IOException if reading from the store fails
     */
    public static PipelineResult run(String batchId, BatchManifest manifest,
                                      String merkleRoot, String userSeed,
                                      RecordStore store) throws IOException {
        return run(batchId, manifest, merkleRoot, userSeed, store,
                VerificationSampler.DEFAULT_SAMPLE_SIZE, DEFAULT_MIN_OBSERVATIONS);
    }

    /**
     * Run the verification pipeline.
     *
     * @param batchId           the batch identifier (YYYY-MM-DD)
     * @param manifest          the batch manifest
     * @param merkleRoot        the batch's Merkle root
     * @param userSeed          seed for deterministic sampling
     * @param store             the local record store
     * @param maxSampleSize     maximum records to sample
     * @param minObservations   minimum independent observations required per URL
     * @return pipeline result
     * @throws IOException if reading from the store fails
     */
    public static PipelineResult run(String batchId, BatchManifest manifest,
                                      String merkleRoot, String userSeed,
                                      RecordStore store, int maxSampleSize,
                                      int minObservations) throws IOException {

        // Step 1: Sample records from manifest
        List<String> sampledHashes = VerificationSampler.sample(
                manifest, merkleRoot, userSeed, maxSampleSize);

        // Build index for independent observation lookup
        IndexBuilder.Index index = IndexBuilder.build(store);

        List<RecordDetail> details = new ArrayList<>();
        List<String> mismatchHashes = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int unverifiable = 0;

        for (String hash : sampledHashes) {
            // Load the sampled record
            ObservationRecord sampledRecord = store.load(hash);
            if (sampledRecord == null) {
                // Record not in store — shouldn't happen post-import but handle gracefully
                details.add(new RecordDetail(hash, RecordStatus.UNVERIFIABLE, List.of()));
                unverifiable++;
                continue;
            }

            // Step 2: Find independent observations for the same URL
            String url = sampledRecord.url();
            String sampledNodeId = sampledRecord.nodeId();
            List<String> urlHashes = index.byUrl(url);

            // Filter to independent observations (different node_id)
            List<ObservationRecord> independentObs = new ArrayList<>();
            for (String urlHash : urlHashes) {
                if (urlHash.equals(hash)) continue; // skip self
                ObservationRecord obs = store.load(urlHash);
                if (obs != null && !obs.nodeId().equals(sampledNodeId)) {
                    independentObs.add(obs);
                }
            }

            // Check minimum observation threshold
            if (independentObs.size() < minObservations) {
                details.add(new RecordDetail(hash, RecordStatus.UNVERIFIABLE, List.of()));
                unverifiable++;
                continue;
            }

            // Step 3: Compare against first independent observation
            RecordComparator.Result result = RecordComparator.compare(
                    sampledRecord, independentObs.get(0));

            if (result.match()) {
                details.add(new RecordDetail(hash, RecordStatus.MATCHED, List.of()));
                matched++;
            } else {
                details.add(new RecordDetail(hash, RecordStatus.MISMATCHED,
                        result.discrepancies()));
                mismatchHashes.add(hash);
                mismatched++;
            }
        }

        // Step 4: Generate audit report (only counts verified records)
        int sampled = matched + mismatched;
        AuditReport report = new AuditReport(
                batchId,
                manifest.size(),
                sampled,
                matched,
                mismatched,
                0  // disputes not auto-filed by pipeline; caller decides
        );

        return new PipelineResult(
                report,
                Collections.unmodifiableList(details),
                Collections.unmodifiableList(mismatchHashes)
        );
    }
}
