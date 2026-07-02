package de.wps.geoserver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class GeoserverBaseImageIntegrationTest {

    private static final String DEFAULT_IMAGE = "ghcr.io/wps/geoserver:latest";

    @Container
    static GenericContainer<?> geoserver = new GenericContainer<>(
            DockerImageName.parse(System.getProperty("geoserver.image", DEFAULT_IMAGE)))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/geoserver/ows?service=WMS&version=1.3.0&request=GetCapabilities")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final String BASIC_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("admin:geoserver".getBytes());

    private static String baseUrl;

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
        baseUrl = "http://" + geoserver.getHost() + ":" + geoserver.getMappedPort(8080);
        var authCheck = get("/geoserver/rest/about/version.json");
        assertThat(authCheck.statusCode())
                .as("GeoServer-Authentifizierung fehlgeschlagen (HTTP %d) — "
                        + "GeoServer 3.x erzwingt beim ersten Start ggf. eine Passwortänderung; "
                        + "default credentials admin:geoserver gelten dann nicht mehr".formatted(authCheck.statusCode()))
                .isEqualTo(200);
    }

    @Test
    void webUi_isReachable() throws IOException, InterruptedException {
        var response = get("/geoserver/web/");

        assertThat(response.statusCode()).as(response.body()).isBetween(200, 399);
    }

    @Test
    void restVersionEndpoint_returnsExpectedVersion() throws IOException, InterruptedException {
        String expectedVersion = System.getProperty("geoserver.version");
        assertThat(expectedVersion)
                .as("System-Property geoserver.version muss gesetzt sein (z.B. -Dgeoserver.version=2.28.3)")
                .isNotNull().isNotEmpty();

        var response = get("/geoserver/rest/about/version.json");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(expectedVersion);
    }

    @Test
    void wmsGetCapabilities_respondsSuccessfully() throws IOException, InterruptedException {
        var response = get("/geoserver/ows?service=WMS&version=1.3.0&request=GetCapabilities");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).containsAnyOf("WMS_Capabilities", "WMT_MS_Capabilities");
    }

    @Test
    void wfsGetCapabilities_respondsSuccessfully() throws IOException, InterruptedException {
        var response = get("/geoserver/ows?service=WFS&version=2.0.0&request=GetCapabilities");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void wmsGetMap_rendersPngForFirstAvailableLayer() throws IOException, InterruptedException {
        var capabilitiesResponse = get("/geoserver/ows?service=WMS&version=1.3.0&request=GetCapabilities");
        assertThat(capabilitiesResponse.statusCode()).isEqualTo(200);
        String firstLayerName = extractFirstLayerName(capabilitiesResponse.body());
        assertThat(firstLayerName).as("GetCapabilities muss mindestens einen Layer enthalten").isNotNull();

        var response = getBytes("/geoserver/ows"
                + "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap"
                + "&LAYERS=" + firstLayerName
                + "&STYLES="
                + "&FORMAT=image/png"
                + "&WIDTH=256&HEIGHT=256"
                + "&CRS=EPSG:4326"
                + "&BBOX=-90,-180,90,180");

        assertThat(response.statusCode()).as("GetMap für Layer '%s' schlug fehl".formatted(firstLayerName)).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(ct -> assertThat(ct).contains("image/png"));
        assertThat(response.body()).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    void container_runsAsNonRootUser1000() throws IOException, InterruptedException {
        var result = geoserver.execInContainer("id", "-u");

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout().trim()).isEqualTo("1000");
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(request(path), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<byte[]> getBytes(String path) throws IOException, InterruptedException {
        return HTTP.send(request(path), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", BASIC_AUTH)
                .GET()
                .build();
    }

    private static String extractFirstLayerName(String capabilities) {
        Matcher m = Pattern.compile("<Layer[^>]*>.*?<Name>([^<>]+)</Name>", Pattern.DOTALL).matcher(capabilities);
        return m.find() ? m.group(1).trim() : null;
    }
}
