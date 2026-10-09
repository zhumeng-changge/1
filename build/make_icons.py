# -*- coding: utf-8 -*-
"""为 APK 准备：启动图标（各密度 mipmap）+ 提示音 + 组合 assets/index.html"""
import os, shutil, re
from PIL import Image

APK = r"C:\Users\Lenovo\Documents\deepseek-harness\default-workspace\schedule\apk"
PWA = r"C:\Users\Lenovo\Documents\deepseek-harness\default-workspace\schedule\reminder-app"

# ---------- 1. 启动图标 ----------
master = Image.open(os.path.join(PWA, "icon-512.png")).convert("RGBA")
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, size in DENS.items():
    d = os.path.join(APK, "res", "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    master.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"), "PNG", optimize=True)
    print("icon", name, size)

# ---------- 2. 提示音 ----------
raw = os.path.join(APK, "res", "raw")
os.makedirs(raw, exist_ok=True)
shutil.copyfile(os.path.join(PWA, "alarm.wav"), os.path.join(raw, "alarm.wav"))
print("alarm.wav ->", os.path.getsize(os.path.join(raw, "alarm.wav")), "bytes")

# ---------- 3. 组合 assets/index.html（网页应用 + APK 桥接） ----------
os.makedirs(os.path.join(APK, "assets"), exist_ok=True)
html = open(os.path.join(PWA, "index.html"), encoding="utf-8").read()

if 'id="androidBridge"' not in html:
    html = html.replace("</body>", '<script src="bridge.js" id="androidBridge"></script>\n</body>')
    print("已注入 bridge.js")

# APK 内不需要 service worker / manifest 引用
html = html.replace('<link rel="manifest" href="manifest.webmanifest">', '')
html = html.replace("navigator.serviceWorker.register('sw.js')", "Promise.reject(new Error('apk: no sw'))")

open(os.path.join(APK, "assets", "index.html"), "w", encoding="utf-8").write(html)
print("assets/index.html", os.path.getsize(os.path.join(APK, "assets", "index.html")), "bytes")
