package io.truthcrawl.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ListPeersIT {

    private static String publicKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    @Test
    void lists_registered_peers(@TempDir Path tmp) throws Exception {
        Path pubKey = writeFile(tmp, "peer-pub.key", publicKeyBase64);
        Path peersDir = tmp.resolve("peers");

        // Register a peer first
        CliRunner.Result reg = CliRunner.run("register-peer",
                "http://localhost:9090", pubKey.toString(), peersDir.toString());
        assertEquals(0, reg.exitCode(), "register-peer failed: " + reg.stderr());

        // List peers
        CliRunner.Result r = CliRunner.run("list-peers", peersDir.toString());

        assertEquals(0, r.exitCode(), "list-peers failed: " + r.stderr());
        assertFalse(r.stdout().strip().isEmpty());
        assertEquals(64, r.stdout().strip().length()); // single 64-char hex nodeId
    }

    @Test
    void empty_peers_dir(@TempDir Path tmp) throws Exception {
        Path peersDir = tmp.resolve("peers");

        CliRunner.Result r = CliRunner.run("list-peers", peersDir.toString());

        assertEquals(0, r.exitCode(), "list-peers failed: " + r.stderr());
        assertEquals("", r.stdout().strip());
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("list-peers");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
