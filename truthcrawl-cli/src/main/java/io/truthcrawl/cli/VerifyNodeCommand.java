package io.truthcrawl.cli;

import io.truthcrawl.core.NodeProfile;
import io.truthcrawl.core.NodeProfileVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI command: verify-node.
 *
 * <p>Verifies a node profile file. The public key is embedded in the registration,
 * so no external key is needed.
 *
 * <p>Usage: truthcrawl verify-node &lt;profile-file&gt;
 *
 * <p>Exit codes: 0 verification passed, 1 usage error, 2 verification failed.
 */
final class VerifyNodeCommand {

    private VerifyNodeCommand() {}

    static int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: truthcrawl verify-node <profile-file>");
            return 1;
        }

        Path profilePath = Path.of(args[0]);

        try {
            List<String> lines = Files.readAllLines(profilePath, StandardCharsets.UTF_8);
            NodeProfile profile = NodeProfile.parse(lines);

            NodeProfileVerifier.Result result = NodeProfileVerifier.verify(profile);

            if (result.valid()) {
                System.out.println("VALID");
                System.out.println("node_id:" + profile.nodeId());
                System.out.println("operator:" + profile.registration().operatorName());
                System.out.println("organization:" + profile.registration().organization());
                if (profile.attestation() != null) {
                    System.out.println("domains:" + profile.attestation().domains().size());
                    for (String domain : profile.attestation().domains()) {
                        System.out.println("  " + domain);
                    }
                }
                return 0;
            } else {
                System.out.println("INVALID");
                for (String error : result.errors()) {
                    System.out.println("  " + error);
                }
                return 2;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }
}
