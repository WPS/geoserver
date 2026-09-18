ARG BASE_IMAGE=eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8
ARG BUILD_IMAGE=alpine:3.24@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6

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
