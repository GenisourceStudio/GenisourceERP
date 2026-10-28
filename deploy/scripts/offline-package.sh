#!/usr/bin/env bash
# Builds an offline installation package: the images, the database bootstrap and the
# scripts, in one archive that can be carried to a factory with no internet.
#
# A factory in a plant room often has no route to a registry, and an ERP that cannot
# be installed is not an ERP. This script produces everything needed on a machine
# with a Docker daemon and nothing else.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

OUT="${1:-$ROOT/dist/offline}"
PLATFORM="${PLATFORM:-linux/amd64}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found in PATH." >&2
  exit 1
fi

mkdir -p "$OUT/images" "$OUT/scripts"

echo "==> Building the images"
# The proxy in the Docker config is a host-level proxy; the build container cannot
# reach it, so the proxy variables are cleared for the build.
DOCKER_BUILDKIT=${DOCKER_BUILDKIT:-0} docker build \
  --build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= \
  --build-arg http_proxy= --build-arg https_proxy= \
  -f deploy/docker/backend.Dockerfile -t "genisource/erp-backend:${VERSION:-latest}" .

DOCKER_BUILDKIT=${DOCKER_BUILDKIT:-0} docker build \
  --build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= \
  --build-arg http_proxy= --build-arg https_proxy= \
  -f deploy/docker/frontend.Dockerfile -t "genisource/erp-frontend:${VERSION:-latest}" .

echo "==> Saving the images"
for image in genisource/erp-backend genisource/erp-frontend; do
  archive="$OUT/images/${image##*/}-${VERSION:-latest}.tar"
  docker save "${image}:${VERSION:-latest}" | gzip > "$archive"
  echo "    $(basename "$archive")  $(du -h "$archive" | cut -f1)"
done

echo "==> Copying the scripts and the compose file"
# The package ships pre-built images, so its compose file must use `image:` rather
# than `build:` — a machine with no source tree cannot compile anything.
awk -v ver="${VERSION:-latest}" '
  /^    build:$/ { pending = 1; next }
  # Remember which image the service wanted before skipping the block.
  pending && /^      dockerfile: / { last_docker = $2; next }
  pending && /^      / { next }
  pending {
    if (last_docker ~ /backend/) {
      print "    image: genisource/erp-backend:" ver
    } else {
      print "    image: genisource/erp-frontend:" ver
    }
    pending = 0
  }
  { print }
' compose.yaml > "$OUT/compose.yaml"

cp deploy/scripts/install.sh deploy/scripts/backup.sh deploy/scripts/restore.sh \
   deploy/scripts/upgrade.sh "$OUT/scripts/"
cp -R deploy/nginx "$OUT/" 2>/dev/null || true
chmod +x "$OUT/scripts/"*.sh

cat > "$OUT/INSTALL" <<'INSTRUCTIONS'
Genisource ERP — offline package
================================

On the target machine:

  1. docker load -i images/erp-backend-*.tar
     docker load -i images/erp-frontend-*.tar

  2. cd into this directory and run:

       ./scripts/install.sh

     The installer writes a .env with random secrets, starts the stack and waits for
     the API to turn healthy.

If the target machine has no internet, set in .env before running:

     MAVEN_MIRROR=central
     NPM_REGISTRY=https://registry.npmjs.org

The images are already built, so no network access is needed.
INSTRUCTIONS

echo
echo "Offline package written to $OUT"
du -sh "$OUT"
