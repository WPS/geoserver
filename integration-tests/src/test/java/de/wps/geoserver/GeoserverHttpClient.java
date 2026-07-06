package de.wps.geoserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

class GeoserverHttpClient {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "geoserver";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final String baseUrl;
    private final String basicAuth;

    GeoserverHttpClient(String baseUrl) {
        this(baseUrl, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    GeoserverHttpClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    HttpResponse<String> get(String path) {
        return send(request(path), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> getBytes(String path) {
        return send(request(path), HttpResponse.BodyHandlers.ofByteArray());
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            return HTTP.send(req, handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request interrupted", e);
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", basicAuth)
                .GET()
                .build();
    }
}
