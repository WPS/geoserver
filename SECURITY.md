# Security Policy

## Supported versions

| Tag | Supported |
|---|---|
| `:3.0.0` / `:latest` | ✅ Active |
| `:2.28.3` | ✅ Active |

Older tags are not actively maintained. Please upgrade to a supported version before reporting
a vulnerability.

## Reporting a vulnerability

**Preferred:** Use [GitHub Private Vulnerability Reporting](https://github.com/WPS/geoserver/security/advisories/new)
to report confidentially. We aim to acknowledge reports within 5 business days.

**Fallback:** Send an email to `info@wps.de` with subject `[SECURITY] WPS/geoserver – <short description>`.

Please do not open a public GitHub issue for security vulnerabilities.

## What is and is not in scope

**In scope:** vulnerabilities in the Alpine base packages (`apk` packages) or the Java runtime
(`eclipse-temurin`) included in this image.

**Out of scope:** GeoServer application CVEs (JAR-level). These are version-bound — they are
fixed by upgrading GeoServer, not by changing the base image. Report those upstream to the
[GeoServer project](https://geoserver.org/comm/issues.html).

## Scanning and SBOM

Every build is scanned with [Trivy](https://github.com/aquasecurity/trivy). Results are published
to the [Security tab](https://github.com/WPS/geoserver/security/code-scanning) and attached as
CycloneDX SBOM artifacts on each CI run.
