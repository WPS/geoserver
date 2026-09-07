ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
ARG BUILD_IMAGE=alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b

FROM ${BUILD_IMAGE} AS build

ARG GEOSERVER_VERSION

RUN apk add --no-cache curl unzip \
 && DOWNLOAD_URL="https://sourceforge.net/projects/geoserver/files/GeoServer/${GEOSERVER_VERSION}/geoserver-${GEOSERVER_VERSION}-bin.zip/download" \
 && curl -fSL -o /tmp/gs.zip "${DOWNLOAD_URL}" \
 && EXPECTED_MD5=$(curl -fsSL "https://sourceforge.net/projects/geoserver/rss?path=/GeoServer/${GEOSERVER_VERSION}" \
      | grep -F "url=\"${DOWNLOAD_URL}\"" \
      | grep -oE 'algo="md5">[0-9a-f]{32}' \
      | grep -oE '[0-9a-f]{32}') \
 && ACTUAL_MD5=$(md5sum /tmp/gs.zip | cut -d' ' -f1) \
 && if [ -z "${EXPECTED_MD5}" ] || [ "${EXPECTED_MD5}" != "${ACTUAL_MD5}" ]; then \
      echo "GeoServer checksum verification failed: expected=${EXPECTED_MD5:-<none>} actual=${ACTUAL_MD5}"; \
      exit 1; \
    fi \
 && unzip -q /tmp/gs.zip -d /opt/geoserver \
 && rm /tmp/gs.zip

FROM ${BASE_IMAGE}

LABEL org.opencontainers.image.title="WPS GeoServer Base Image" \
      org.opencontainers.image.description="Minimal GeoServer base image on Alpine with bundled Jetty and Java 21 (musl)" \
      org.opencontainers.image.vendor="WPS" \
      org.opencontainers.image.source="https://github.com/WPS/geoserver"

RUN apk add --no-cache ttf-dejavu fontconfig tini tzdata ca-certificates gcompat

ENV TZ=Europe/Berlin \
    GEOSERVER_HOME=/opt/geoserver \
    GEOSERVER_DATA_DIR=/opt/geoserver_data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 \
      -Dsun.java2d.renderer=sun.java2d.marlin.DMarlinRenderingEngine \
      -Dsun.java2d.renderer.useRef=soft \
      --add-exports=java.desktop/com.sun.imageio.plugins.jpeg=ALL-UNNAMED \
      --add-exports=java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED \
      --add-opens=java.desktop/java.awt.image=ALL-UNNAMED \
      --add-opens=java.desktop/javax.imageio=ALL-UNNAMED \
      --add-opens=java.desktop/javax.imageio.stream=ALL-UNNAMED"

RUN addgroup -S -g 1000 geoserver && adduser -S -u 1000 -G geoserver geoserver

COPY --from=build --chown=geoserver:geoserver /opt/geoserver /opt/geoserver

RUN mkdir -p /opt/geoserver_data \
 && cp -r /opt/geoserver/data_dir/. /opt/geoserver_data/ \
 && rm -rf /opt/geoserver/data_dir \
 && chown -R geoserver:geoserver /opt/geoserver_data

EXPOSE 8080
WORKDIR /opt/geoserver
ENTRYPOINT ["/sbin/tini", "--"]
USER geoserver
CMD ["/opt/geoserver/bin/startup.sh"]
