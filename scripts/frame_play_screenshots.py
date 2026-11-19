#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
# SPDX-License-Identifier: GPL-3.0-or-later
"""Frame raw phone screenshots into 1080x1920 (9:16) Google Play images.

Raw phone captures are 1080x2400 (ratio 2.22), above Play's 2:1 limit, so
each one is scaled onto a brand-colour canvas with a caption.

Usage: python3 scripts/frame_play_screenshots.py es
Reads docs/store/raw-screenshots/<lang>/NN-name.png and writes
fastlane/metadata/android/<locale>/images/phoneScreenshots/NN-name.png. Requires Pillow and the
Adwaita Sans variable font (or pass another .ttf with --font).
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

LOCALES = {"es": "es-ES", "en": "en-US", "ca": "ca"}

CAPTIONS = {
    "es": {
        "01-notificacion": ("Te avisa al bajar del coche", "Con lo que no te puedes olvidar"),
        "02-lista": ("Recordatorios por coche", "Al entrar, al salir, cada día o una vez"),
        "03-avisos": ("Marca lo pendiente", "Completa cada cosa de un toque"),
        "04-editor": ("Crea un aviso en segundos", "Escríbelo o díctalo"),
        "05-estado": ("Todo bajo control", "Permisos, Bluetooth y última alerta"),
        "06-ajustes": ("Varios coches, una app", "Sin cuentas, sin anuncios"),
    },
    "en": {
        "01-notificacion": ("Alerts as you leave the car", "With everything you can't forget"),
        "02-lista": ("Reminders for each car", "When entering, leaving, daily or once"),
        "03-avisos": ("Tick off what's pending", "Complete each item with one tap"),
        "04-editor": ("Add a reminder in seconds", "Type it or dictate it"),
        "05-estado": ("Everything under control", "Permissions, Bluetooth and latest alert"),
        "06-ajustes": ("Several cars, one app", "No accounts, no ads"),
    },
    "ca": {
        "01-notificacion": ("T’avisa en baixar del cotxe", "Amb el que no pots oblidar"),
        "02-lista": ("Recordatoris per cotxe", "En entrar, en sortir, cada dia o una vegada"),
        "03-avisos": ("Marca el que tens pendent", "Completa cada cosa amb un toc"),
        "04-editor": ("Crea un avís en segons", "Escriu-lo o dicta’l"),
        "05-estado": ("Tot sota control", "Permisos, Bluetooth i última alerta"),
        "06-ajustes": ("Diversos cotxes, una app", "Sense comptes, sense anuncis"),
    },
}
TEAL, LIGHT, ORANGE, SHADOW = "#0F7882", "#D7F0F2", "#F2994A", "#073a3f"
W, H, SHOT_H, SHOT_Y, RADIUS = 1080, 1920, 1600, 300, 44


def font(path: str, size: int, weight: str) -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(path, size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass  # static font: keep its own weight
    return f


def frame(raw: Path, out: Path, title: str, subtitle: str, font_path: str) -> None:
    f1, f2 = font(font_path, 64, "Bold"), font(font_path, 40, "Regular")
    if f1.getlength(title) > W - 120:
        raise SystemExit(f"Caption too wide: {title!r}")
    shot = Image.open(raw).convert("RGB")
    canvas = Image.new("RGB", (W, H), TEAL)
    draw = ImageDraw.Draw(canvas)
    draw.text((W / 2, 120), title, font=f1, fill="#FFFFFF", anchor="mm")
    draw.text((W / 2, 200), subtitle, font=f2, fill=LIGHT, anchor="mm")
    draw.line([(W / 2 - 60 + k * 2, 248 + 4 * math.sin(k / 4.0)) for k in range(61)],
              fill=ORANGE, width=7, joint="curve")
    sw = round(shot.width * SHOT_H / shot.height)
    scaled = shot.resize((sw, SHOT_H), Image.LANCZOS)
    x = (W - sw) // 2
    mask = Image.new("L", (sw, SHOT_H), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, SHOT_H - 1], radius=RADIUS, fill=255)
    shadow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shadow).rounded_rectangle([x, SHOT_Y + 10, x + sw, SHOT_Y + SHOT_H + 10],
                                             radius=RADIUS, fill=110)
    canvas.paste(Image.new("RGB", (W, H), SHADOW), (0, 0), shadow.filter(ImageFilter.GaussianBlur(18)))
    canvas.paste(scaled, (x, SHOT_Y), mask)
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out, optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("lang", choices=sorted(CAPTIONS))
    parser.add_argument("--font", default="/usr/share/fonts/Adwaita/AdwaitaSans-Regular.ttf")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    raw = root / "docs/store/raw-screenshots" / args.lang
    out = root / "fastlane/metadata/android" / LOCALES[args.lang] / "images/phoneScreenshots"
    for name, (title, subtitle) in CAPTIONS[args.lang].items():
        frame(raw / f"{name}.png", out / f"{name}.png", title, subtitle, args.font)
        print(out / f"{name}.png")


if __name__ == "__main__":
    main()
