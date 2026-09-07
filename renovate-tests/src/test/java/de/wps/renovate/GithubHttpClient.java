package de.wps.renovate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

class GithubHttpClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    HttpResponse<String> getGeoserverReleases() {
        try {
            var request = HttpRequest.newBuilder(
                    URI.create("https://api.github.com/repos/geoserver/geoserver/releases?per_page=30")).GET().build();
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request interrupted", e);
        }
    }
}
