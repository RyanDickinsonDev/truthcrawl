package io.truthcrawl.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TruthcrawlServerTest {

    private TruthcrawlServer server;
    private String baseUrl;
    private HttpClient httpClient;
    private PublisherKey nodeKey;
    private Path storeDir;
    private Path batchesDir;
    private Path peersDir;

    @BeforeEach
    void setup(@TempDir Path tmp) throws IOException {
        nodeKey = PublisherKey.generate();
        storeDir = tmp.resolve("store");
        batchesDir = tmp.resolve("batches");
        peersDir = tmp.resolve("peers");
        Files.createDirectories(batchesDir);

        server = new TruthcrawlServer(
                new InetSocketAddress("127.0.0.1", 0),
                new RecordStore(storeDir),
                batchesDir,
                new PeerRegistry(peersDir),
                nodeKey);
        server.start();

        int port = server.address().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void teardown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void get_info() throws Exception {
        HttpResponse<String> resp = get("/info");

        assertEquals(200, resp.statusCode());
        String nodeId = RequestSigner.computeNodeId(nodeKey);
        assertTrue(resp.body().contains("node_id:" + nodeId));
        assertTrue(resp.body().contains("public_key:" + nodeKey.publicKeyBase64()));
    }

    @Test
    void get_peers_empty() throws Exception {
        HttpResponse<String> resp = get("/peers");

        assertEquals(200, resp.statusCode());
        assertEquals("", resp.body().strip());
    }

    @Test
    void post_peers_requires_auth() throws Exception {
        HttpResponse<String> resp = post("/peers", "node_id:a\n");

        assertEquals(401, resp.statusCode());
    }

    @Test
    void post_peers_authenticated() throws Exception {
        // First register the peer so the server knows its key
        PublisherKey peerKey = PublisherKey.generate();
        RequestSigner peerSigner = new RequestSigner(peerKey);

        PeerInfo peerInfo = new PeerInfo(
                peerSigner.nodeId(), "http://peer:9090", peerKey.publicKeyBase64());

        // Pre-register the peer so the server can verify the signature
        PeerRegistry registry = new PeerRegistry(peersDir);
        registry.register(peerInfo);

        byte[] body = peerInfo.toCanonicalText().getBytes(StandardCharsets.UTF_8);
        RequestSigner.SignedHeaders headers = peerSigner.sign("POST", "/peers", body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/peers"))
                .header("X-Node-Id", headers.nodeId())
                .header("X-Timestamp", headers.timestamp())
                .header("X-Signature", headers.signature())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("registered"));
    }

    @Test
    void get_batches_empty() throws Exception {
        HttpResponse<String> resp = get("/batches");

        assertEquals(200, resp.statusCode());
        assertEquals("", resp.body().strip());
    }

    @Test
    void get_batches_lists_batch_dirs() throws Exception {
        Files.createDirectories(batchesDir.resolve("batch-2024-01-15"));
        Files.createDirectories(batchesDir.resolve("batch-2024-01-16"));

        HttpResponse<String> resp = get("/batches");

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("2024-01-15"));
        assertTrue(resp.body().contains("2024-01-16"));
    }

    @Test
    void get_manifest() throws Exception {
        Path batchDir = batchesDir.resolve("batch-2024-01-15");
        Files.createDirectories(batchDir);
        String manifest = "a".repeat(64) + "\n" + "b".repeat(64) + "\n";
        Files.writeString(batchDir.resolve("manifest.txt"), manifest, StandardCharsets.UTF_8);

        HttpResponse<String> resp = get("/batches/2024-01-15/manifest");

        assertEquals(200, resp.statusCode());
        assertEquals(manifest, resp.body());
    }

    @Test
    void get_manifest_not_found() throws Exception {
        HttpResponse<String> resp = get("/batches/nonexistent/manifest");

        assertEquals(404, resp.statusCode());
    }

    @Test
    void get_record() throws Exception {
        // Write a record file directly into the store
        RecordStore store = new RecordStore(storeDir);
        String hash = "c".repeat(64);
        Path recordFile = store.pathFor(hash);
        Files.createDirectories(recordFile.getParent());
        String recordText = "version:0.1\nurl:https://example.com\n";
        Files.writeString(recordFile, recordText, StandardCharsets.UTF_8);

        HttpResponse<String> resp = get("/records/" + hash);

        assertEquals(200, resp.statusCode());
        assertEquals(recordText, resp.body());
    }

    @Test
    void get_record_not_found() throws Exception {
        HttpResponse<String> resp = get("/records/" + "d".repeat(64));

        assertEquals(404, resp.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
