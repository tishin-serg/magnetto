#!/usr/bin/env bash
set -euo pipefail

archive_path="${1:-}"
remote_dir="/opt/torrentbot"
backup_dir="/opt/torrentbot-backups"
lock_file="/var/lock/magnetto-deploy.lock"

if [[ -z "$archive_path" ]]; then
  echo "Usage: magnetto-deploy /tmp/magnetto-source.tar.gz" >&2
  exit 2
fi

archive_realpath="$(realpath "$archive_path")"
case "$archive_realpath" in
  /tmp/magnetto-source-*.tar.gz|/tmp/magnetto-source.tar.gz) ;;
  *)
    echo "Refusing archive outside the expected /tmp/magnetto-source*.tar.gz path: $archive_realpath" >&2
    exit 2
    ;;
esac

if [[ ! -f "$archive_realpath" ]]; then
  echo "Archive was not found: $archive_realpath" >&2
  exit 2
fi

exec 9>"$lock_file"
flock -n 9 || {
  echo "Another magnetto deploy is already running." >&2
  exit 75
}

ts="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir" "$remote_dir"

if [[ -d "$remote_dir" ]]; then
  tar -czf "$backup_dir/torrentbot-before-ci-$ts.tar.gz" \
    --exclude="$remote_dir/.env" \
    -C /opt torrentbot
fi

tar -xzf "$archive_realpath" -C "$remote_dir"
cd "$remote_dir"

docker run --rm \
  -v "$remote_dir:/workspace" \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -Dmaven.test.skip=true package

docker build -f Dockerfile.runtime -t torrentbot-bot-app .
docker compose up -d --no-deps bot-app
docker exec torrentbot-bot-app-1 sh -c 'wget -qO- http://127.0.0.1:8080/actuator/health || curl -s http://127.0.0.1:8080/actuator/health'
docker logs --since=2m torrentbot-bot-app-1 2>&1 | tail -200
