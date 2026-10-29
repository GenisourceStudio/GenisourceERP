#!/usr/bin/env bash
# Assemble rendered frames + music into the final mp4.
# Requires ffmpeg (this machine has it at /opt/homebrew/bin, which is not always on PATH).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
FRAMES="${FRAME_DIR:-${TMPDIR:-/tmp}/genisource-erp-frames}"
WAV="$HERE/music.wav"
OUT="$HERE/../genisource-erp-promo.mp4"

[ -d "$FRAMES" ] || { echo "frames not found: $FRAMES (run: node tools/render.mjs frames)"; exit 1; }
if [ ! -f "$WAV" ]; then
  echo "music.wav missing — synthesising (node tools/music.mjs)"
  node "$HERE/music.mjs"
fi

/opt/homebrew/bin/ffmpeg -y -v error -framerate 30 -i "$FRAMES/f_%05d.png" -i "$WAV" \
  -c:v libx264 -preset slow -crf 17 -pix_fmt yuv420p -profile:v high -level 4.1 \
  -movflags +faststart -c:a aac -b:a 192k -ar 48000 -shortest -t 104.4 "$OUT"

/opt/homebrew/bin/ffprobe -v error -show_entries format=duration,size -of default=noprint_wrappers=1 "$OUT"
