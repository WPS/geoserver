package de.wps.geoserver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    private static GeoserverHttpClient client;

    @BeforeAll
    static void setUp() {
        client = new GeoserverHttpClient("http://" + geoserver.getHost() + ":" + geoserver.getMappedPort(8080));
        var authCheck = client.get("/geoserver/rest/about/version.json");
        assertThat(authCheck.statusCode())
                .as("GeoServer-Authentifizierung fehlgeschlagen (HTTP %d) — "
                        + "GeoServer 3.x erzwingt beim ersten Start ggf. eine Passwortänderung; "
                        + "default credentials admin:geoserver gelten dann nicht mehr".formatted(authCheck.statusCode()))
                .isEqualTo(200);
    }

    @Test
    void webUi_isReachable() {
        var response = client.get("/geoserver/web/");

        assertThat(response.statusCode()).as(response.body()).isBetween(200, 399);
    }

    @Test
    void restVersionEndpoint_returnsExpectedVersion() {
        // arrange
        String expectedVersion = System.getProperty("geoserver.version");
        assertThat(expectedVersion)
                .as("System-Property geoserver.version muss gesetzt sein (z.B. -Dgeoserver.version=2.28.3)")
                .isNotNull().isNotEmpty();

        // act
        var response = client.get("/geoserver/rest/about/version.json");

        // assert
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(expectedVersion);
    }

    @Test
    void wmsGetCapabilities_respondsSuccessfully() {
        var response = client.get("/geoserver/ows?service=WMS&version=1.3.0&request=GetCapabilities");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).containsAnyOf("WMS_Capabilities", "WMT_MS_Capabilities");
    }

    @Test
    void wfsGetCapabilities_respondsSuccessfully() {
        var response = client.get("/geoserver/ows?service=WFS&version=2.0.0&request=GetCapabilities");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void wmsGetMap_rendersPngForFirstAvailableLayer() {
        // arrange
        var capabilitiesResponse = client.get("/geoserver/ows?service=WMS&version=1.3.0&request=GetCapabilities");
        assertThat(capabilitiesResponse.statusCode()).isEqualTo(200);
        String firstLayerName = extractFirstLayerName(capabilitiesResponse.body());
        assertThat(firstLayerName).as("GetCapabilities muss mindestens einen Layer enthalten").isNotNull();

        // act
        var response = client.getBytes("/geoserver/ows"
                + "?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap"
                + "&LAYERS=" + firstLayerName
                + "&STYLES="
                + "&FORMAT=image/png"
                + "&WIDTH=256&HEIGHT=256"
                + "&CRS=EPSG:4326"
                + "&BBOX=-90,-180,90,180");

        // assert
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

    private static String extractFirstLayerName(String capabilities) {
        Matcher m = Pattern.compile("<Layer[^>]*>.*?<Name>([^<>]+)</Name>", Pattern.DOTALL).matcher(capabilities);
        return m.find() ? m.group(1).trim() : null;
    }
}
