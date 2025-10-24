#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="${IMAGE_NAME:-redis-benchmark-runner}"
NETWORK_NAME="${NETWORK_NAME:-redis-benchmark-net}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis-benchmark-redis}"
RUNNER_CONTAINER="${RUNNER_CONTAINER:-redis-benchmark}"
REDIS_URI="redis://${REDIS_CONTAINER}:6379"
NETWORK_CREATED=0
REDIS_STARTED=0
IMAGE_BUILT=0

cleanup() {
  set +e
  docker rm -f "${RUNNER_CONTAINER}" >/dev/null 2>&1 || true
  docker rm -f "${REDIS_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
  if [[ "${IMAGE_BUILT}" -eq 1 ]]; then
    docker rmi "${IMAGE_NAME}" >/dev/null 2>&1 || true
  fi
  set -e
}

ensure_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker es requerido para ejecutar este benchmark."
    exit 1
  fi
}

cleanup_existing_resources() {
  docker rm -f "${RUNNER_CONTAINER}" >/dev/null 2>&1 || true
  docker rm -f "${REDIS_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
  docker rmi "${IMAGE_NAME}" >/dev/null 2>&1 || true
}

build_image() {
  echo "Construyendo imagen ${IMAGE_NAME}..."
  docker build --rm --force-rm -t "${IMAGE_NAME}" "${ROOT_DIR}"
  IMAGE_BUILT=1
}

ensure_network() {
  if ! docker network ls --format '{{.Name}}' | grep -qx "${NETWORK_NAME}"; then
    docker network create "${NETWORK_NAME}" >/dev/null
    NETWORK_CREATED=1
  fi
}

start_redis() {
  echo "Arrancando Redis (${REDIS_IMAGE})..."
  docker run --rm -d --name "${REDIS_CONTAINER}" --network "${NETWORK_NAME}" "${REDIS_IMAGE}" >/dev/null
  REDIS_STARTED=1
}

wait_for_redis() {
  echo "Esperando a Redis..."
  for _ in {1..30}; do
    if docker exec "${REDIS_CONTAINER}" redis-cli ping >/dev/null 2>&1; then
      docker exec "${REDIS_CONTAINER}" redis-cli FLUSHALL >/dev/null
      echo "Redis listo e inicializado en blanco."
      return
    fi
    sleep 1
  done
  echo "Redis no respondió a tiempo" >&2
  exit 1
}

run_benchmarks() {
  mkdir -p "${ROOT_DIR}/benchmark-results"
  docker run --rm \
    --name "${RUNNER_CONTAINER}" \
    --network "${NETWORK_NAME}" \
    -e REDIS_URI="${REDIS_URI}" \
    -v "${ROOT_DIR}/benchmark-results:/app/benchmark-results" \
    "${IMAGE_NAME}"
}

ensure_docker
trap cleanup EXIT INT TERM

cleanup_existing_resources

build_image
ensure_network
start_redis
wait_for_redis
run_benchmarks

echo "Benchmarks completados. Resultados en benchmark-results/latest.txt"
