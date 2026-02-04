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

class RegisterPeerIT {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        privateKeyBase64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
    }

    @Test
    void registers_peer(@TempDir Path tmp) throws Exception {
        Path pubKey = writeFile(tmp, "peer-pub.key", publicKeyBase64);
        Path peersDir = tmp.resolve("peers");

        CliRunner.Result r = CliRunner.run("register-peer",
                "http://localhost:9090", pubKey.toString(), peersDir.toString());

        assertEquals(0, r.exitCode(), "register-peer failed: " + r.stderr());
        assertTrue(r.stdout().contains("node_id:"));
        assertTrue(r.stdout().contains("endpoint:http://localhost:9090"));
        assertTrue(r.stdout().contains("public_key:" + publicKeyBase64));
        assertTrue(Files.exists(peersDir));
    }

    @Test
    void exits_1_with_no_arguments() throws Exception {
        CliRunner.Result r = CliRunner.run("register-peer");
        assertEquals(1, r.exitCode());
    }

    private Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
