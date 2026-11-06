'use strict';

/**
 * 图标表 → Android VectorDrawable 生成器（安卓项目自包含）。
 *
 * 输入，按优先级：
 *   1. 环境变量 ICONS_SRC 指定的绝对路径
 *   2. tools/icons-source.js —— 项目内快照（随源码分发，默认）
 *   3. ../wechat-miniprogram/miniprogram/utils/icons.js —— 同级小程序项目（开发期同步用）
 * 输出：app/src/main/res/drawable/glyph_*.xml + ui/icons/AppIcons.kt
 *
 * 小程序图标改版后同步快照：
 *   cp ../wechat-miniprogram/miniprogram/utils/icons.js tools/icons-source.js
 *   node scripts/gen_android_icons.js
 *
 * 转换要点：
 *   1. base64 解出 SVG 后取 viewBox（24×24）与内部图形；
 *   2. <circle> / <rect> 转成等价的 <path> pathData（Android VectorDrawable 只支持 path）；
 *   3. 线宽 1.8、圆头圆角与小程序保持一致；
 *   4. 描边色统一写黑，运行时由 Compose 的 Icon(tint=...) 着色，
 *      这样同一图形可以复用多种颜色（brand / muted / gold / white）。
 *
 * 用法（在 android-app 目录下）：node scripts/gen_android_icons.js
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SNAPSHOT = path.join(ROOT, 'tools', 'icons-source.js');
const SIBLING = path.join(ROOT, '..', 'wechat-miniprogram', 'miniprogram', 'utils', 'icons.js');
const SRC = process.env.ICONS_SRC || (fs.existsSync(SNAPSHOT) ? SNAPSHOT : SIBLING);
const OUT_DIR = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable');

const STROKE_WIDTH = 1.8;

/**
 * camelCase → snake_case。
 *
 * 用 `glyph_` 前缀而不是 `ic_`：应用图标本身也叫 `ic_launcher_*`，
 * 生成器清理旧文件时不能误删它们，前缀分开最安全。
 */
function toResName(key) {
  const snake = key
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
    .toLowerCase();
  return `glyph_${snake}`;
}

function num(value) {
  const n = Number(value);
  return Number.isInteger(n) ? String(n) : String(n);
}

/** <circle cx cy r/> → 两段 180° 弧拼成整圆。 */
function circleToPath(cx, cy, r) {
  const top = num(Number(cy) - Number(r));
  const bottom = num(Number(cy) + Number(r));
  const rr = num(r);
  return `M${num(cx)},${top}A${rr},${rr} 0 1 0 ${num(cx)},${bottom}A${rr},${rr} 0 1 0 ${num(cx)},${top}Z`;
}

/** <rect x y w h rx/> → 圆角矩形 path（rx 缺省或为 0 时用直角）。 */
function rectToPath(x, y, w, h, rxRaw) {
  const nx = Number(x) || 0;
  const ny = Number(y) || 0;
  const nw = Number(w) || 0;
  const nh = Number(h) || 0;
  const rx = Math.min(Number(rxRaw || 0), nw / 2, nh / 2);

  const right = nx + nw;
  const bottom = ny + nh;

  if (rx <= 0) {
    return `M${num(nx)},${num(ny)}H${num(right)}V${num(bottom)}H${num(nx)}Z`;
  }

  const r = num(rx);
  const xs = num(nx + rx);
  const ys = num(ny + rx);
  const xe = num(right - rx);
  const ye = num(bottom - rx);

  return (
    `M${xs},${num(ny)}` +
    `H${xe}A${r},${r} 0 0 1 ${num(right)},${ys}` +
    `V${ye}A${r},${r} 0 0 1 ${xe},${num(bottom)}` +
    `H${xs}A${r},${r} 0 0 1 ${num(nx)},${ye}` +
    `V${ys}A${r},${r} 0 0 1 ${xs},${num(ny)}Z`
  );
}

/** 解析 SVG 内部图形，返回 pathData 数组。 */
function svgInnerToPaths(inner) {
  const paths = [];
  const re =/<(circle|rect|path)\b([^>]*)\/?>/g;
  let m;
  while ((m = re.exec(inner))) {
    const tag = m[1];
    const attrs = m[2] || '';
    const attr = (name) => {
      const hit = new RegExp(`${name}\\s*=\\s*"([^"]*)"`).exec(attrs);
      return hit ? hit[1] : null;
    };

    if (tag === 'path') {
      const d = attr('d');
      if (d) paths.push(d.trim());
    } else if (tag === 'circle') {
      paths.push(circleToPath(attr('cx') || 0, attr('cy') || 0, attr('r') || 0));
    } else if (tag === 'rect') {
      paths.push(
        rectToPath(attr('x') || 0, attr('y') || 0, attr('width') || 0, attr('height') || 0, attr('rx'))
      );
    }
  }
  return paths;
}

function buildVectorXml(pathData) {
  const body = pathData
    .map(
      (d) =>
        `    <path\n` +
        `        android:pathData="${d}"\n` +
        `        android:strokeColor="#FF000000"\n` +
        `        android:strokeWidth="${STROKE_WIDTH}"\n` +
        `        android:strokeLineCap="round"\n` +
        `        android:strokeLineJoin="round" />`
    )
    .join('\n');

  return (
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
    `    android:width="24dp"\n` +
    `    android:height="24dp"\n` +
    `    android:viewportWidth="24"\n` +
    `    android:viewportHeight="24">\n` +
    `${body}\n` +
    `</vector>\n`
  );
}

function main() {
  if (!fs.existsSync(SRC)) {
    console.error(`找不到图标源：${SRC}`);
    console.error('请放置 tools/icons-source.js，或用 ICONS_SRC 指向小程序 utils/icons.js。');
    process.exit(1);
  }

  const icons = require(SRC);
  const keys = Object.keys(icons);
  fs.mkdirSync(OUT_DIR, { recursive: true });

  // 清掉上一次生成的图标，避免改名后残留（只清 glyph_ 前缀，不碰应用图标）
  for (const file of fs.readdirSync(OUT_DIR)) {
    if (/^glyph_.*\.xml$/.test(file)) fs.unlinkSync(path.join(OUT_DIR, file));
  }

  const manifest = [];
  for (const key of keys) {
    const dataUrl = icons[key];
    const comma = dataUrl.indexOf(',');
    if (comma < 0) throw new Error(`图标 ${key} 不是合法的 data URL`);
    const svg = Buffer.from(dataUrl.slice(comma + 1), 'base64').toString('utf8');

    const inner = svg.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
    const paths = svgInnerToPaths(inner);
    if (!paths.length) throw new Error(`图标 ${key} 没有解析到图形`);

    const resName = toResName(key);
    fs.writeFileSync(path.join(OUT_DIR, `${resName}.xml`), buildVectorXml(paths), 'utf8');
    manifest.push({ key, res: resName, paths: paths.length });
  }

  // 生成 Kotlin 侧的资源映射表
  // 注意：R.drawable.* 不是编译期常量，不能用 const val。
  const ktLines = manifest
    .map((m) => `    val ${m.key} = R.drawable.${m.res}`)
    .join('\n');

  const kt = `package com.campus.schedule.ui.icons

import com.campus.schedule.R

/**
 * 全站线性图标（24×24 / 线宽 1.8 / 圆头圆角），源自 tools/icons-source.js 图标表快照。
 *
 * 由 \`scripts/gen_android_icons.js\` 自动生成，请勿手改：
 * 改图形请更新快照后重跑脚本。
 *
 * 描边在 XML 里统一为黑色，运行时通过 Compose 的 \`Icon(tint = ...)\` 着色，
 * 同一图形因此可以复用品牌蓝 / 次级灰 / 金色 / 反白四种色调。
 */
object AppIcons {
${ktLines}
}
`;
  const ktPath = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'campus', 'schedule', 'ui', 'icons', 'AppIcons.kt');
  fs.mkdirSync(path.dirname(ktPath), { recursive: true });
  fs.writeFileSync(ktPath, kt, 'utf8');

  console.log(`图标源：${SRC}`);
  console.log(`已生成 ${manifest.length} 个 VectorDrawable → ${OUT_DIR}`);
  console.log(`Kotlin 映射表 → ${ktPath}`);
}

main();
