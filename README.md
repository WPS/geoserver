# WPS GeoServer Base Image

Minimales GeoServer-Base-Image auf Alpine-Basis mit gebündeltem Jetty und Java 21.

Publiziert auf: `ghcr.io/wps/geoserver`

## Verwendung

```bash
docker pull ghcr.io/wps/geoserver:3.0.0
docker run --rm -p 8080:8080 ghcr.io/wps/geoserver:3.0.0
```

## Tags

| Tag | Bedeutung |
|---|---|
| `:{version}` | Aktuellster Build dieser GeoServer-Version, z.B. `:3.0.0` |
| `:{version}-{YYYYMMDD}-{n}` | Datumsgenaue Version, z.B. `:3.0.0-20260702-1` |

Gepflegte GeoServer-Versionen: `2.28.3`, `3.0.0` (Matrix in [`.github/workflows/build.yml`](.github/workflows/build.yml)).

## Lokales Bauen und Testen

```bash
# Image bauen (Beispiel GeoServer 3.0.0)
docker build --build-arg GEOSERVER_VERSION=3.0.0 -t geoserver-local:3.0.0 .

# Mit eigenem Base-Image (z.B. gehärtetes Alpine)
docker build \
  --build-arg GEOSERVER_VERSION=3.0.0 \
  --build-arg BASE_IMAGE=ghcr.io/mein-org/my-alpine-java:21 \
  -t geoserver-local:3.0.0 .

# Container starten (Admin-UI: http://localhost:8080/geoserver/web/)
docker run --rm -p 8080:8080 geoserver-local:3.0.0

# Integrationstests ausführen
./integration-tests/mvnw -f integration-tests/pom.xml verify \
  -Dgeoserver.image=geoserver-local:3.0.0 \
  -Dgeoserver.version=3.0.0
```

## Build-ARGs

| ARG | Default | Bedeutung |
|---|---|---|
| `GEOSERVER_VERSION` | `2.28.3` | GeoServer-Version (aus `-bin.zip` Distribution) |
| `BASE_IMAGE` | `eclipse-temurin:21-jre-alpine@sha256:…` | Runtime-Base-Image; muss Alpine/musl-kompatibel sein |

Das `BASE_IMAGE`-ARG ermöglicht den Einsatz eines anderen Base-Images (z.B. ein gehärtetes
org-internes Alpine+Java-Image), ohne das Dockerfile zu ändern.

## Image erweitern (Downstream-Dockerfile)

Das Image läuft als `USER geoserver` (UID 1000). Um beim Image-Extend Pakete zu installieren oder
Dateien zu schreiben, auf `USER root` wechseln und danach zurücksetzen:

```dockerfile
FROM ghcr.io/wps/geoserver:3.0.0

USER root
RUN apk add --no-cache wget \
 && rm -rf /opt/geoserver_data/workspaces

COPY --chown=geoserver:geoserver workspaces/ /opt/geoserver_data/workspaces/

USER geoserver
```

Die GeoServer-Version steckt im Tag (`3.0.0`). Um auf eine neue Version zu wechseln, den Tag im
`FROM` aktualisieren. Renovate erkennt `ghcr.io`-Tags ohne zusätzliche Konfiguration.

## GeoServer-Version aktualisieren

Die `geoserver_version`-Matrix in `.github/workflows/build.yml` ergänzen oder anpassen. Bei einem Major-Versionswechsel
(z.B. 2.x → 3.x) die `JAVA_OPTS` im `Dockerfile` gegen die neue GeoServer-Version prüfen
(jakarta vs. javax Module-Flags).
