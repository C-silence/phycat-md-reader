#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 项目根 的阅读页与主题资源同步进 app assets，供 APK 使用。

用法（在 android/ 目录执行）：
    python tools/sync_assets.py

同步内容：
  - theme-preview/index.html          （css 路径改写：去掉 "../"，与 css 同目录）
  - theme-preview/vendor/…            （markdown-it，本地化）
  - 根目录 phycat-*.css 9 套配色
  - 根目录 phycat/ 基础样式 + 字体
"""
import os
import shutil

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))   # 项目根
DEST = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "app")

THEMES = ["caramel", "cherry", "dark", "forest", "mauve", "mint", "prussian", "sakura", "sky"]
PHYCAT_FILES = [
    "phycat.light.css", "phycat.dark.css",
    "Cascadia-Code-Regular.ttf",
    "HarmonyOS_Sans_SC_Bold.woff", "HarmonyOS_Sans_SC_Regular.woff",
    "SourceHanSerifCN-Bold.ttf", "SourceHanSerifCN-Medium.ttf",
]


def main():
    if os.path.isdir(DEST):
        shutil.rmtree(DEST)
    os.makedirs(os.path.join(DEST, "vendor"), exist_ok=True)
    os.makedirs(os.path.join(DEST, "phycat"), exist_ok=True)

    for t in THEMES:
        shutil.copy(os.path.join(ROOT, f"phycat-{t}.css"), os.path.join(DEST, f"phycat-{t}.css"))
    for f in PHYCAT_FILES:
        shutil.copy(os.path.join(ROOT, "phycat", f), os.path.join(DEST, "phycat", f))
    shutil.copy(os.path.join(ROOT, "theme-preview", "vendor", "markdown-it.min.js"),
                os.path.join(DEST, "vendor", "markdown-it.min.js"))

    idx_path = os.path.join(ROOT, "theme-preview", "index.html")
    with open(idx_path, encoding="utf-8") as fh:
        idx = fh.read().replace("../phycat-", "phycat-")
    with open(os.path.join(DEST, "index.html"), "w", encoding="utf-8", newline="") as fh:
        fh.write(idx)

    total = sum(os.path.getsize(os.path.join(dp, fn))
                for dp, _, fs in os.walk(DEST) for fn in fs)
    print(f"同步完成 → {DEST}")
    print(f"资源总量 ≈ {round(total / 1e6, 1)} MB")


if __name__ == "__main__":
    main()
