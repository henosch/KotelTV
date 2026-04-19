#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="KotelTV"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_BASENAME="${PROJECT_NAME}-backup-${TIMESTAMP}"
DESKTOP_DIR="${HOME}/Desktop"
BACKUP_DIR="${DESKTOP_DIR}/${BACKUP_BASENAME}"
ARCHIVE_PATH="${DESKTOP_DIR}/${BACKUP_BASENAME}.tar.gz"

EXCLUDES=(
  "--exclude=.git/"
  "--exclude=.gradle/"
  "--exclude=.gradle-bootstrap/"
  "--exclude=.gradle-tmp/"
  "--exclude=.gradle-local/"
  "--exclude=.kotlin/"
  "--exclude=.idea/"
  "--exclude=.omx/"
  "--exclude=app/build/"
  "--exclude=build/"
  "--exclude=*/build/"
  "--exclude=.DS_Store"
  "--exclude=*/.DS_Store"
  "--exclude=*.tmp"
  "--exclude=*.temp"
  "--exclude=*.log"
  "--exclude=*.iml"
  "--exclude=*.apk"
)

mkdir -p "${BACKUP_DIR}"

rsync -a "${EXCLUDES[@]}" "${SCRIPT_DIR}/" "${BACKUP_DIR}/"

# Verknüpften Keystore aus keystore.properties mitsichern, falls er außerhalb des Projekts liegt.
if [ -f "${SCRIPT_DIR}/keystore.properties" ]; then
  KEYSTORE_PATH="$(grep -E '^storeFile=' "${SCRIPT_DIR}/keystore.properties" | cut -d'=' -f2-)"
  if [ -n "${KEYSTORE_PATH}" ] && [ -f "${KEYSTORE_PATH}" ]; then
    mkdir -p "${BACKUP_DIR}/_external"
    cp "${KEYSTORE_PATH}" "${BACKUP_DIR}/_external/$(basename "${KEYSTORE_PATH}")"
    printf 'Keystore mitgesichert: %s\n' "${KEYSTORE_PATH}"
  else
    printf 'WARNUNG: storeFile aus keystore.properties nicht gefunden (%s)\n' "${KEYSTORE_PATH}" >&2
  fi
fi

tar -czf "${ARCHIVE_PATH}" -C "${DESKTOP_DIR}" "${BACKUP_BASENAME}"

printf 'Backup erstellt: %s\nArchiv erstellt: %s\n' "${BACKUP_DIR}" "${ARCHIVE_PATH}"
rm -rf "${BACKUP_DIR}/"
printf 'Ordner gelöscht: %s\n' "${BACKUP_DIR}"
