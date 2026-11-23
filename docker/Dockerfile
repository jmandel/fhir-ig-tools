# Reference build/test image: the FHIR autobuilder's localdev image (Jekyll 4.4.1 / Liquid 4.0.4 /
# kramdown 2.6.1 / kramdown-parser-gfm 1.1.0 / Rouge 4.7.0 / Ruby 3.4.13, JDK 21), pinned by digest (docs/TRACKER.md
# D9), plus python3. The digest is ghcr.io/fhir/ig-publisher-localdev:latest as of 2026-11-19 (= hl7fhir/ig-publisher-base
# :latest; the ig-build CI image from the same Dockerfile has the same gems). Until 2026-11-19 it was sha256:39509f41…
# (kramdown 2.5.2 / Ruby 3.2.3). Tracking: jekyll/INTEGRATION.md "Compatibility contract".
# Tag it `ig-publisher-localdev-ca` (the scripts use that name).
#
# Behind a TLS-inspecting proxy? Put the proxy's root CA certificate(s) as *.crt or *.pem files in
# docker/extra-ca/ before building; they are trusted by the OS, Java and Node. The directory may be empty.
#
#   docker build -t ig-publisher-localdev-ca docker/
#
# Drift checks only: --build-arg BASE=ghcr.io/fhir/ig-publisher-localdev:latest
ARG BASE=ghcr.io/fhir/ig-publisher-localdev@sha256:daa6a3f40ffb80e71f1b3a026498cf60f0a324e188a5cb33676e2a9f871df898
FROM ${BASE}
USER root
# python3: some templates' ant scripts shell out to it (WHO smart-ig-template); the CI image has it too.
RUN apt-get update && apt-get -y --no-install-recommends install python3 && rm -rf /var/lib/apt/lists/*
COPY extra-ca/ /tmp/extra-ca/
RUN set -e; n=0; for f in /tmp/extra-ca/*.crt /tmp/extra-ca/*.pem; do \
      [ -f "$f" ] || continue; n=$((n+1)); \
      cp "$f" "/usr/local/share/ca-certificates/extra-ca-$n.crt"; \
      keytool -importcert -noprompt -trustcacerts -alias "extra-ca-$n" -file "$f" -cacerts -storepass changeit; \
      cat "$f" >> /usr/local/share/ca-certificates/extra-ca-bundle.pem; \
    done; update-ca-certificates; touch /usr/local/share/ca-certificates/extra-ca-bundle.pem; rm -rf /tmp/extra-ca
ENV NODE_EXTRA_CA_CERTS=/usr/local/share/ca-certificates/extra-ca-bundle.pem \
    SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
