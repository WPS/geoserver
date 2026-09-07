package de.wps.renovate;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RenovateConfigTest {

    private static final Path RENOVATE_CONFIG = Path.of("..", "renovate.json5");
    private static final Path VERSIONS_JSON = Path.of("..", "versions.json");

    private static final JsonMapper JSON5 = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private static GithubHttpClient client;

    @BeforeAll
    static void setUp() {
        client = new GithubHttpClient();
    }

    @Test
    void customManagerFilePatternsResolveToVersionsJson() throws IOException {
        // arrange
        List<String> filePatterns = customManagers().stream()
                .map(manager -> manager.get("managerFilePatterns").get(0).asText())
                .toList();
        assertThat(filePatterns).as("expected 2 customManagers in renovate.json5").hasSize(2);

        // act / assert
        for (String filePattern : filePatterns) {
            Pattern compiledFilePattern = Pattern.compile(filePattern.substring(1, filePattern.length() - 1));
            assertThat(compiledFilePattern.matcher("versions.json").matches())
                    .as("managerFilePatterns %s doesn't resolve to versions.json", filePattern)
                    .isTrue();
        }
    }

    @Test
    void customManagerRegexesEachMatchExactlyOnceInVersionsJson() throws IOException {
        // arrange
        List<String> matchStrings = customManagers().stream()
                .map(manager -> manager.get("matchStrings").get(0).asText())
                .toList();
        assertThat(matchStrings).as("expected 2 customManagers in renovate.json5").hasSize(2);
        String versionsJson = Files.readString(VERSIONS_JSON);

        // act / assert
        for (String matchString : matchStrings) {
            Matcher m = Pattern.compile(matchString).matcher(versionsJson);
            assertThat(m.find()).as("no match for %s in versions.json", matchString).isTrue();
            assertThat(m.find()).as("more than one match for %s in versions.json", matchString).isFalse();
        }
    }

    @Test
    void geoserverGithubReleases_shouldUsePlainVersionTags() throws IOException {
        // arrange
        List<String> extractVersionTemplates = customManagers().stream()
                .map(manager -> manager.get("extractVersionTemplate").asText())
                .toList();

        // act
        var response = client.getGeoserverReleases();
        List<String> tags = Pattern.compile("\"tag_name\":\\s*\"([^\"]+)\"").matcher(response.body())
                .results().map(m -> m.group(1)).toList();

        // assert
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        for (String template : extractVersionTemplates) {
            Pattern versionPattern = Pattern.compile(template);
            assertThat(tags)
                    .as("no release tag satisfies %s among: %s", template, tags)
                    .anySatisfy(tag -> assertThat(versionPattern.matcher(tag).matches()).isTrue());
        }
    }

    private static List<JsonNode> customManagers() throws IOException {
        JsonNode config = JSON5.readTree(Files.readString(RENOVATE_CONFIG));
        return StreamSupport.stream(config.get("customManagers").spliterator(), false).toList();
    }
}
