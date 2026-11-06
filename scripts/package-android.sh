#!/usr/bin/env bash
# ============================================================
#  校园日程 Android App - 源码打包（项目自包含）
#
#  用法（在 android-app 目录下执行）：
#    scripts/package-android.sh            # 默认 v2.0.1
#    scripts/package-android.sh 2.0.2      # 指定版本号
#
#  产出：
#    dist/campus-schedule-android-v<版本>.zip
#    dist/campus-schedule-android-v<版本>.zip.sha256
#
#  包内根目录即项目根（app/ scripts/ tools/ *.kts ...），解压后可直接构建。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-2.0.1}"
NAME="campus-schedule-android-v${VERSION}"
DIST="$ROOT/dist"
STAGE="$ROOT/.tmp/package-android"
PKGDIR="$STAGE/$NAME"

cleanup() {
  if [ -d "$PKGDIR" ]; then
    rm -rf "$PKGDIR"
  fi
}
trap cleanup EXIT

rm -rf "$PKGDIR"
mkdir -p "$DIST" "$PKGDIR"

# ---------- 1. 收集源码（排除可再生缓存、本机配置与历史交付包） ----------
cd "$ROOT"
tar -cf - \
  --exclude='.gradle' \
  --exclude='.kotlin' \
  --exclude='.tmp' \
  --exclude='dist' \
  --exclude='build' \
  --exclude='local.properties' \
  --exclude='*.zip' \
  --exclude='*.sha256' \
  --exclude='*.apk' \
  . | tar -xf - -C "$PKGDIR"

# ---------- 2. 生成文件清单 ----------
cd "$PKGDIR"
find . -type f ! -name 'MANIFEST.sha256' \
  | sed 's|^\./||' \
  | LC_ALL=C sort \
  | while IFS= read -r f; do
      sha256sum "$f"
    done > MANIFEST.sha256

# ---------- 3. 打包 ----------
# Git Bash 自带 tar 但没有 zip，用 Python 的 zipfile 打包（Windows 上路径最稳）
cd "$STAGE"
rm -f "$DIST/$NAME.zip"
PKG_SRC="$PKGDIR" PKG_OUT="$DIST/$NAME.zip" python - <<'PY'
import os, zipfile

src = os.environ["PKG_SRC"]
out = os.environ["PKG_OUT"]
root = os.path.dirname(src.rstrip("/\\"))
name = os.path.basename(src.rstrip("/\\"))

with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for dirpath, dirnames, filenames in os.walk(src):
        dirnames.sort()
        for fn in sorted(filenames):
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            zf.write(full, rel)
print(f"[zip] {len(zf.filelist)} entries -> {out}")
PY

# ---------- 4. 校验和 ----------
cd "$DIST"
sha256sum "$NAME.zip" > "$NAME.zip.sha256"

echo "[OK] 源码包: dist/$NAME.zip"
echo "     文件数: $(wc -l < "$PKGDIR/MANIFEST.sha256")"
echo "     校验和: $(cat "$NAME.zip.sha256")"
