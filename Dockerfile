ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

FROM alpine:3.23@sha256:fd791d74b68913cbb027c6546007b3f0d3bc45125f797758156952bc2d6daf40 AS fetch

ARG GEOSERVER_VERSION=2.28.3

RUN apk add --no-cache curl unzip \
 && curl -fSL -o /tmp/gs.zip \
      "https://sourceforge.net/projects/geoserver/files/GeoServer/${GEOSERVER_VERSION}/geoserver-${GEOSERVER_VERSION}-bin.zip/download" \
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

COPY --from=fetch --chown=geoserver:geoserver /opt/geoserver /opt/geoserver

RUN mkdir -p /opt/geoserver_data \
 && cp -r /opt/geoserver/data_dir/. /opt/geoserver_data/ \
 && rm -rf /opt/geoserver/data_dir \
 && chown -R geoserver:geoserver /opt/geoserver_data

EXPOSE 8080
WORKDIR /opt/geoserver
ENTRYPOINT ["/sbin/tini", "--"]
USER geoserver
CMD ["/opt/geoserver/bin/startup.sh"]
