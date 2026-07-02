# WPS GeoServer Base Image

Minimal GeoServer base image on Alpine with bundled Jetty and Java 21.

Published at: `ghcr.io/wps/geoserver`

## Usage

```bash
docker pull ghcr.io/wps/geoserver:3.0.0
docker run --rm -p 8080:8080 ghcr.io/wps/geoserver:3.0.0
```

## Tags

| Tag | Meaning |
|---|---|
| `:{version}` | Latest build of that GeoServer version, e.g. `:3.0.0` |
| `:{version}-{YYYYMMDD}-{n}` | Date-stamped build, e.g. `:3.0.0-20260702-1` |
| `:latest` | Latest build of the most recent supported GeoServer version |

Maintained GeoServer versions: `2.28.3`, `3.0.0` (matrix in [`.github/workflows/build.yml`](.github/workflows/build.yml)).

## Local build and test

```bash
# Build image (example: GeoServer 3.0.0)
docker build --build-arg GEOSERVER_VERSION=3.0.0 -t geoserver-local:3.0.0 .

# Build with a custom base image (e.g. a hardened internal Alpine+Java image)
docker build \
  --build-arg GEOSERVER_VERSION=3.0.0 \
  --build-arg BASE_IMAGE=registry.example.com/hardened-java21:alpine \
  -t geoserver-local:3.0.0 .

# Start container (Admin UI: http://localhost:8080/geoserver/web/)
docker run --rm -p 8080:8080 geoserver-local:3.0.0

# Run integration tests
./integration-tests/mvnw -f integration-tests/pom.xml verify \
  -Dgeoserver.image=geoserver-local:3.0.0 \
  -Dgeoserver.version=3.0.0
```

## Build ARGs

| ARG | Default | Description |
|---|---|---|
| `GEOSERVER_VERSION` | `2.28.3` | GeoServer version (downloaded from the `-bin.zip` distribution) |
| `BASE_IMAGE` | `eclipse-temurin:21-jre-alpine@sha256:…` | Runtime base image; must be Alpine/musl-compatible |

The `BASE_IMAGE` ARG allows substituting a different base image (e.g. a hardened
org-internal Alpine+Java image) without modifying the Dockerfile.

## Extending the image (downstream Dockerfile)

The image runs as `USER geoserver` (UID 1000). To install packages or write files
when extending the image, switch to `USER root` and reset it afterwards:

```dockerfile
FROM ghcr.io/wps/geoserver:3.0.0

USER root
RUN apk add --no-cache wget \
 && rm -rf /opt/geoserver_data/workspaces

COPY --chown=geoserver:geoserver workspaces/ /opt/geoserver_data/workspaces/

USER geoserver
```

The GeoServer version is embedded in the tag (`3.0.0`). To upgrade, update the tag in
the `FROM` line. Renovate and Dependabot detect `ghcr.io` tags without additional configuration.

## Security

Each build is scanned with [Trivy](https://github.com/aquasecurity/trivy). Results are uploaded
to the [GitHub Security tab](https://github.com/WPS/geoserver/security/code-scanning) as SARIF and
attached as build artifacts (CycloneDX SBOM).

GeoServer-specific JAR CVEs are version-bound rather than base-image-bound — switching the base
image (Alpine vs. Ubuntu) does not resolve them; only GeoServer version upgrades do.

To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Adding a new GeoServer version to the CI matrix

Add or update the `geoserver_version` matrix in `.github/workflows/build.yml`. On a major version
change (e.g. 2.x → 3.x), review the `JAVA_OPTS` in the `Dockerfile` against the new GeoServer
version (jakarta vs. javax module flags).
