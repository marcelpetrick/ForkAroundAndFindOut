# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Pinned lint toolbox for scripts/lint.sh: ruff (Python), yamllint (YAML), reuse (licensing
# and SPDX, REUSE 3.x) and xmllint (well-formed XML). Built locally, never published.
FROM python:3.14-slim@sha256:a2b82f3c48559aa0a8446d9af49826b6e2b2016f4cd2afabfe6013ec53729170
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends libxml2-utils git \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir ruff==0.17.0 yamllint==1.38.0 "reuse[charset-normalizer]==6.2.0" \
    && git config --system --add safe.directory '*'
WORKDIR /work
