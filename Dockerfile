# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Distribution image: serves the Android APK, its SHA-256, the license and an install
# page. The phone camera is never used here; monitoring runs only on the phone.
# Build context: the directory staged by scripts/docker-dist.sh.
FROM nginx:1.30.6-alpine@sha256:ff8328b4279ec385af48cde9ef486e7165ad74b7065cb3cd86c377384e2e2dd5
ARG VERSION=dev
LABEL org.opencontainers.image.title="Fork Around & Find Out" \
      org.opencontainers.image.description="Download server for the offline Android elbow-on-table monitor APK" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.licenses="GPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/marcelpetrick/ForkAroundAndFindOut"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY site/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/healthz"]
