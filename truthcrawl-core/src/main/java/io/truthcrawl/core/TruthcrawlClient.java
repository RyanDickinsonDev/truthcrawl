package io.truthcrawl.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client for communicating with truthcrawl peer nodes.
 *
 * <p>Uses JDK's built-in {@code java.net.http.HttpClient} — no external dependencies.
 *
 * <p>Authenticated requests (POST) are signed using {@link RequestSigner}.
 * Unauthenticated requests (GET) are sent without signature headers.
 */
public final class TruthcrawlClient {

    private final HttpClient httpClient;
    private final RequestSigner signer;

    /**
     * Create a client with the given signer for authenticated requests.
     *
     * @param signer the request signer (provides node identity and signing)
     */
    public TruthcrawlClient(RequestSigner signer) {
        this.signer = signer;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetch remote node info.
     *
     * @param endpoint the peer's base URL (e.g. "http://localhost:8080")
     * @return the response body (node_id and public_key lines)
     * @throws IOException if the request fails
     */
    public String getInfo(String endpoint) throws IOException {
        return doGet(endpoint + "/info");
    }

    /**
     * List remote peer nodeIds.
     *
     * @param endpoint the peer's base URL
     * @return the response body (one nodeId per line)
     * @throws IOException if the request fails
     */
    public String listPeers(String endpoint) throws IOException {
        return doGet(endpoint + "/peers");
    }

    /**
     * Register as a peer with a remote node (authenticated).
     *
     * @param endpoint the peer's base URL
     * @param peerInfo the PeerInfo to register
     * @return the response body, or null on 404
     * @throws IOException if the request fails
     */
    public String registerPeer(String endpoint, PeerInfo peerInfo) throws IOException {
        byte[] body = peerInfo.toCanonicalText().getBytes(StandardCharsets.UTF_8);
        return doPost(endpoint + "/peers", "/peers", body);
    }

    /**
     * List remote batch IDs.
     *
     * @param endpoint the peer's base URL
     * @return the response body (one batch ID per line)
     * @throws IOException if the request fails
     */
    public String listBatches(String endpoint) throws IOException {
        return doGet(endpoint + "/batches");
    }

    /**
     * Fetch a batch manifest from a remote node.
     *
     * @param endpoint the peer's base URL
     * @param batchId  the batch ID
     * @return the manifest text, or null on 404
     * @throws IOException if the request fails
     */
    public String getManifest(String endpoint, String batchId) throws IOException {
        return doGet(endpoint + "/batches/" + batchId + "/manifest");
    }

    /**
     * Fetch a batch chain-link from a remote node.
     *
     * @param endpoint the peer's base URL
     * @param batchId  the batch ID
     * @return the chain-link text, or null on 404
     * @throws IOException if the request fails
     */
    public String getChainLink(String endpoint, String batchId) throws IOException {
        return doGet(endpoint + "/batches/" + batchId + "/chain-link");
    }

    /**
     * Fetch a batch signature from a remote node.
     *
     * @param endpoint the peer's base URL
     * @param batchId  the batch ID
     * @return the signature text, or null on 404
     * @throws IOException if the request fails
     */
    public String getSignature(String endpoint, String batchId) throws IOException {
        return doGet(endpoint + "/batches/" + batchId + "/signature");
    }

    /**
     * Fetch a record by hash from a remote node.
     *
     * @param endpoint   the peer's base URL
     * @param recordHash 64-char hex record hash
     * @return the record full text, or null on 404
     * @throws IOException if the request fails
     */
    public String getRecord(String endpoint, String recordHash) throws IOException {
        return doGet(endpoint + "/records/" + recordHash);
    }

    private String doGet(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return send(request);
    }

    private String doPost(String url, String path, byte[] body) throws IOException {
        RequestSigner.SignedHeaders headers = signer.sign("POST", path, body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Node-Id", headers.nodeId())
                .header("X-Timestamp", headers.timestamp())
                .header("X-Signature", headers.signature())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return send(request);
    }

    private String send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
}
