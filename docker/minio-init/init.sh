set -eu

ENDPOINT="${MINIO_INTERNAL_ENDPOINT:-http://minio:9000}"
BUCKET="${MINIO_BUCKET:-cv-avatars}"

echo "[minio-init] MinIO is ready from ${ENDPOINT} ..."
i=0
until mc alias set local "${ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 30 ]; then
    echo "[minio-init] Error: Cannot connect to MinIO after 30 tries" >&2
    exit 1
  fi
  sleep 2
done

echo "[minio-init] Create bucket '${BUCKET}' (ignore if existed)"
mc mb --ignore-existing "local/${BUCKET}"

echo "[minio-init] Set PRIVATE policy for '${BUCKET}'"
mc anonymous set none "local/${BUCKET}"

echo "[minio-init] Completed."
mc ls local
