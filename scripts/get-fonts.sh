#!/usr/bin/env bash
# Скачивает русские шрифты (Google Fonts, лицензия OFL) в assets приложения.
set -u
OUT=app/src/main/assets/fonts
mkdir -p "$OUT"
ok=0
while IFS='|' read -r file name path; do
  [ -z "${file:-}" ] && continue
  if curl -fsSL --retry 3 "https://raw.githubusercontent.com/google/fonts/main/$path" -o "$OUT/$file" < /dev/null; then
    ok=$((ok+1))
  else
    echo "FAILED: $file ($path)"
    rm -f "$OUT/$file"
  fi
done < "$OUT/fonts.txt"
echo "fonts downloaded: $ok"
[ "$ok" -ge 30 ] || { echo "Нужно минимум 30 шрифтов"; exit 1; }
