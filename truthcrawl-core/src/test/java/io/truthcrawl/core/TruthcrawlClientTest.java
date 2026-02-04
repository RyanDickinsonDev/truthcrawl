package io.truthcrawl.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TruthcrawlClientTest {

    private TruthcrawlServer server;
    private String baseUrl;
    private PublisherKey serverKey;
    private PublisherKey clientKey;
    private TruthcrawlClient client;
    private Path peersDir;

    @BeforeEach
    void setup(@TempDir Path tmp) throws IOException {
        serverKey = PublisherKey.generate();
        clientKey = PublisherKey.generate();

        Path storeDir = tmp.resolve("store");
        Path batchesDir = tmp.resolve("batches");
        peersDir = tmp.resolve("peers");
        Files.createDirectories(batchesDir);

        // Pre-register client as peer on server so authenticated requests work
        PeerRegistry serverRegistry = new PeerRegistry(peersDir);
        RequestSigner clientSigner = new RequestSigner(clientKey);
        PeerInfo clientPeer = new PeerInfo(
                clientSigner.nodeId(), "http://client:0", clientKey.publicKeyBase64());
        serverRegistry.register(clientPeer);

        server = new TruthcrawlServer(
                new InetSocketAddress("127.0.0.1", 0),
                new RecordStore(storeDir),
                batchesDir,
                serverRegistry,
                serverKey);
        server.start();

        int port = server.address().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        client = new TruthcrawlClient(new RequestSigner(clientKey));
    }

    @AfterEach
    void teardown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void get_info() throws IOException {
        String info = client.getInfo(baseUrl);

        assertNotNull(info);
        String serverNodeId = RequestSigner.computeNodeId(serverKey);
        assertTrue(info.contains("node_id:" + serverNodeId));
        assertTrue(info.contains("public_key:" + serverKey.publicKeyBase64()));
    }

    @Test
    void list_peers() throws IOException {
        String peers = client.listPeers(baseUrl);
        assertNotNull(peers);
        // The server has our client registered as a peer
        RequestSigner clientSigner = new RequestSigner(clientKey);
        assertTrue(peers.contains(clientSigner.nodeId()));
    }

    @Test
    void register_peer() throws IOException {
        RequestSigner clientSigner = new RequestSigner(clientKey);
        PeerInfo peerInfo = new PeerInfo(
                clientSigner.nodeId(), "http://updated:1234", clientKey.publicKeyBase64());

        String result = client.registerPeer(baseUrl, peerInfo);

        assertNotNull(result);
        assertTrue(result.contains("registered"));
    }

    @Test
    void list_batches_empty() throws IOException {
        String batches = client.listBatches(baseUrl);
        assertNotNull(batches);
        assertEquals("", batches.strip());
    }

    @Test
    void get_manifest_not_found() throws IOException {
        String manifest = client.getManifest(baseUrl, "nonexistent");
        assertNull(manifest);
    }

    @Test
    void get_record_not_found() throws IOException {
        String record = client.getRecord(baseUrl, "e".repeat(64));
        assertNull(record);
    }
}
