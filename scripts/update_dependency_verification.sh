#!/usr/bin/env bash

set -euo pipefail

./gradlew \
  -Porg.jetbrains.intellij.platform.verifyPluginDefaultRecommendedIdes=false \
  --refresh-dependencies \
  --write-verification-metadata sha256 \
  buildPlugin check
