package io.truthcrawl.cli;

/**
 * CLI entry point. Dispatches to subcommands.
 *
 * <p>Usage:
 * <pre>
 *   truthcrawl build-root &lt;manifest-file&gt;
 *   truthcrawl verify-proof &lt;leaf-hex&gt; &lt;proof-file&gt; &lt;expected-root-hex&gt;
 *   truthcrawl publish-batch &lt;batch-id&gt; &lt;manifest&gt; &lt;priv-key&gt; &lt;pub-key&gt; &lt;out-dir&gt;
 *   truthcrawl verify-batch &lt;metadata&gt; &lt;manifest&gt; &lt;signature&gt; &lt;pub-key&gt;
 *   truthcrawl observe &lt;url&gt; &lt;priv-key&gt; &lt;pub-key&gt; &lt;output-file&gt;
 *   truthcrawl verify-record &lt;record&gt; &lt;node-pub-key&gt; &lt;manifest&gt; &lt;metadata&gt;
 *   truthcrawl compare-records &lt;original&gt; &lt;actual&gt;
 * </pre>
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: truthcrawl <command> [args...]");
            printCommands();
            System.exit(1);
        }

        String command = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);

        int exitCode = switch (command) {
            case "build-root" -> BuildRootCommand.run(rest);
            case "verify-proof" -> VerifyProofCommand.run(rest);
            case "publish-batch" -> PublishBatchCommand.run(rest);
            case "verify-batch" -> VerifyBatchCommand.run(rest);
            case "observe" -> ObserveCommand.run(rest);
            case "verify-record" -> VerifyRecordCommand.run(rest);
            case "compare-records" -> CompareRecordsCommand.run(rest);
            case "file-dispute" -> FileDisputeCommand.run(rest);
            case "resolve-dispute" -> ResolveDisputeCommand.run(rest);
            case "node-reputation" -> NodeReputationCommand.run(rest);
            case "publish-chain-batch" -> PublishChainBatchCommand.run(rest);
            case "verify-chain" -> VerifyChainCommand.run(rest);
            case "sample-observations" -> SampleObservationsCommand.run(rest);
            case "audit-report" -> AuditReportCommand.run(rest);
            case "store-record" -> StoreRecordCommand.run(rest);
            case "query-url" -> QueryUrlCommand.run(rest);
            case "query-node" -> QueryNodeCommand.run(rest);
            case "chain-stats" -> ChainStatsCommand.run(rest);
            case "export-batch" -> ExportBatchCommand.run(rest);
            case "import-batch" -> ImportBatchCommand.run(rest);
            case "verify-pipeline" -> VerifyPipelineCommand.run(rest);
            case "verification-status" -> VerificationStatusCommand.run(rest);
            default -> {
                System.err.println("Unknown command: " + command);
                printCommands();
                yield 1;
            }
        };

        System.exit(exitCode);
    }

    private static void printCommands() {
        System.err.println("Commands: build-root, verify-proof, publish-batch, verify-batch, observe, verify-record, compare-records, file-dispute, resolve-dispute, node-reputation, publish-chain-batch, verify-chain, sample-observations, audit-report, store-record, query-url, query-node, chain-stats, export-batch, import-batch, verify-pipeline, verification-status");
    }
}
