package io.truthcrawl.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * HTTP API server for batch discovery, record exchange, and peer management.
 *
 * <p>Uses JDK's built-in {@code com.sun.net.httpserver.HttpServer} — no external
 * dependencies.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /info} — node info (nodeId + public key)</li>
 *   <li>{@code GET /peers} — list registered peer nodeIds</li>
 *   <li>{@code POST /peers} — register a peer (authenticated)</li>
 *   <li>{@code GET /batches} — list available batch IDs</li>
 *   <li>{@code GET /batches/{batch-id}/manifest} — batch manifest text</li>
 *   <li>{@code GET /records/{record-hash}} — record full text</li>
 * </ul>
 */
public final class TruthcrawlServer {

    private final HttpServer server;
    private final RecordStore recordStore;
    private final Path batchesDir;
    private final PeerRegistry peerRegistry;
    private final RequestVerifier verifier;
    private final String nodeId;
    private final String publicKeyBase64;

    /**
     * Create a server bound to the given address.
     *
     * @param address        host and port to bind to
     * @param recordStore    the record store for serving records
     * @param batchesDir     the directory containing batch-{id}/ subdirectories
     * @param peerRegistry   the peer registry
     * @param nodeKey        the server node's key pair
     * @throws IOException if the server socket cannot be created
     */
    public TruthcrawlServer(InetSocketAddress address,
                            RecordStore recordStore,
                            Path batchesDir,
                            PeerRegistry peerRegistry,
                            PublisherKey nodeKey) throws IOException {
        this.recordStore = recordStore;
        this.batchesDir = batchesDir;
        this.peerRegistry = peerRegistry;
        this.verifier = new RequestVerifier(peerRegistry);
        this.nodeId = RequestSigner.computeNodeId(nodeKey);
        this.publicKeyBase64 = nodeKey.publicKeyBase64();

        this.server = HttpServer.create(address, 0);
        server.createContext("/", this::handleDashboard);
        server.createContext("/info", this::handleInfo);
        server.createContext("/peers", this::handlePeers);
        server.createContext("/batches", this::handleBatches);
        server.createContext("/records", this::handleRecords);
    }

    /**
     * Start the server.
     */
    public void start() {
        server.start();
    }

    /**
     * Stop the server.
     *
     * @param delay seconds to wait for ongoing exchanges to complete
     */
    public void stop(int delay) {
        server.stop(delay);
    }

    /**
     * The address the server is bound to.
     */
    public InetSocketAddress address() {
        return server.getAddress();
    }

    // --- Handlers ---

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            sendError(exchange, 404, "Not found");
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/dashboard.html")) {
            if (in == null) {
                sendError(exchange, 500, "Dashboard not found in classpath");
                return;
            }
            byte[] html = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private void handleInfo(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 400, "Only GET is supported for /info");
            return;
        }
        String body = "node_id:" + nodeId + "\n"
                + "public_key:" + publicKeyBase64 + "\n";
        sendOk(exchange, body);
    }

    private void handlePeers(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equals(method)) {
            List<String> ids = peerRegistry.listNodeIds();
            StringBuilder sb = new StringBuilder();
            for (String id : ids) {
                sb.append(id).append("\n");
            }
            sendOk(exchange, sb.toString());
            return;
        }

        if ("POST".equals(method)) {
            // Authenticate
            byte[] body = exchange.getRequestBody().readAllBytes();
            RequestVerifier.Result auth = verifier.verify(
                    "POST",
                    "/peers",
                    getHeader(exchange, "X-Node-Id"),
                    getHeader(exchange, "X-Timestamp"),
                    getHeader(exchange, "X-Signature"),
                    body,
                    Instant.now());

            if (!auth.valid()) {
                sendError(exchange, 401, String.join("; ", auth.errors()));
                return;
            }

            // Parse and register peer
            try {
                String bodyStr = new String(body, StandardCharsets.UTF_8);
                List<String> lines = List.of(bodyStr.split("\n"));
                PeerInfo peer = PeerInfo.parse(lines);
                peerRegistry.register(peer);
                sendOk(exchange, "registered\n");
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid peer info: " + e.getMessage());
            }
            return;
        }

        sendError(exchange, 400, "Only GET and POST are supported for /peers");
    }

    private void handleBatches(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 400, "Only GET is supported for /batches");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        // GET /batches/{batch-id}/manifest|chain-link|signature
        for (String fileType : List.of("manifest", "chain-link", "signature")) {
            if (path.endsWith("/" + fileType)) {
                String batchId = extractBatchIdWithSuffix(path, fileType);
                if (batchId == null) {
                    sendError(exchange, 400, "Invalid batch path");
                    return;
                }

                Path file = batchesDir.resolve("batch-" + batchId).resolve(fileType + ".txt");
                if (!Files.exists(file)) {
                    sendError(exchange, 404, "Batch not found: " + batchId);
                    return;
                }

                String content = Files.readString(file, StandardCharsets.UTF_8);
                sendOk(exchange, content);
                return;
            }
        }

        // GET /batches
        if ("/batches".equals(path)) {
            List<String> batchIds = listBatchIds();
            StringBuilder sb = new StringBuilder();
            for (String id : batchIds) {
                sb.append(id).append("\n");
            }
            sendOk(exchange, sb.toString());
            return;
        }

        sendError(exchange, 404, "Not found: " + path);
    }

    private void handleRecords(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 400, "Only GET is supported for /records");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        // path = /records/{record-hash}
        if ("/records".equals(path)) {
            sendError(exchange, 400, "Usage: GET /records/{record-hash}");
            return;
        }

        String recordHash = path.substring("/records/".length());
        if (recordHash.isEmpty() || recordHash.contains("/")) {
            sendError(exchange, 400, "Invalid record hash");
            return;
        }

        // Read the raw file rather than parse-and-reserialize for fidelity
        Path recordFile = recordStore.pathFor(recordHash);
        if (!Files.exists(recordFile)) {
            sendError(exchange, 404, "Record not found: " + recordHash);
            return;
        }

        String recordText = Files.readString(recordFile, StandardCharsets.UTF_8);
        sendOk(exchange, recordText);
    }

    // --- Helpers ---

    private List<String> listBatchIds() throws IOException {
        if (!Files.exists(batchesDir)) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(batchesDir)) {
            dirs
                    .filter(Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("batch-"))
                    .forEach(d -> {
                        String name = d.getFileName().toString();
                        ids.add(name.substring("batch-".length()));
                    });
        }

        Collections.sort(ids);
        return ids;
    }

    /**
     * Extract batch ID from path like /batches/{batch-id}/{suffix}.
     */
    private String extractBatchIdWithSuffix(String path, String suffix) {
        String prefix = "/batches/";
        String end = "/" + suffix;
        if (!path.startsWith(prefix) || !path.endsWith(end)) {
            return null;
        }
        String batchId = path.substring(prefix.length(), path.length() - end.length());
        if (batchId.isEmpty()) {
            return null;
        }
        return batchId;
    }

    private String getHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private void sendOk(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
