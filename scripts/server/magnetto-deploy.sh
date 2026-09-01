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
    --exclude="$remote_dir/.m2" \
    --exclude="$remote_dir/target" \
    -C /opt torrentbot
fi

tar -xzf "$archive_realpath" -C "$remote_dir"
cd "$remote_dir"

jar_path="$remote_dir/target/torrentbot-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$jar_path" ]]; then
  echo "Application jar was not found in deploy archive: $jar_path" >&2
  exit 2
fi

docker build -f Dockerfile.runtime -t torrentbot-bot-app .
docker compose up -d --no-deps --force-recreate bot-app

ready=0
for attempt in $(seq 1 120); do
  state="$(docker inspect -f '{{.State.Status}}' torrentbot-bot-app-1 2>/dev/null || true)"
  if [[ "$state" != "running" ]]; then
    echo "bot-app container is not running: $state" >&2
    docker logs --since=5m torrentbot-bot-app-1 2>&1 | tail -200 >&2 || true
    exit 1
  fi

  if docker logs --since=5m torrentbot-bot-app-1 2>&1 | grep -q 'Started TorrentBotApplication'; then
    ready=1
    break
  fi

  sleep 2
done

if [[ "$ready" != "1" ]]; then
  echo "bot-app did not report successful startup in time." >&2
  docker logs --since=5m torrentbot-bot-app-1 2>&1 | tail -200 >&2 || true
  exit 1
fi

docker logs --since=2m torrentbot-bot-app-1 2>&1 | tail -200
