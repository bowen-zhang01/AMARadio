#!/usr/bin/env python3
"""Generate the Xiangyin (乡音) launcher icon and in-app logo as Android vector drawables.

Motif: "海上生明月，天涯共此时" — an amber moon rising over the sea at night. The sea is
drawn as waves that cut through the moon and reflect below it, doubling as a sound wave.

Usage: python3 scripts/app-icon/generate.py [--preview-svg out.svg]
All shapes live on the 108x108 adaptive-icon grid; visible content stays inside the
66 dp safe zone (radius 33 around the centre).
"""
import argparse
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")

NIGHT_TOP, NIGHT_BOTTOM = "#232A63", "#0B0F29"
GLOW = "#FFB547"
MOON_LIGHT, MOON_DARK = "#FFD27A", "#FF8F00"
WAVE = "#FFA726"

MOON_C, MOON_R = (54.0, 45.0), 18.0
STRIPES = [52.0, 58.0]          # sea lines cutting the moon
STRIPE_HALF = 1.35
AMP, HALF_WAVE = 1.1, 6.0
X0, X1 = 30.0, 78.0
REFLECTIONS = [(69.0, 39.0, 69.0, 0.95), (75.5, 43.5, 64.5, 0.6), (82.0, 48.0, 60.0, 0.32)]
STARS = [(28.0, 30.0, 0.7), (79.0, 25.0, 0.6), (85.0, 45.0, 0.5), (23.0, 52.0, 0.5), (70.0, 17.0, 0.55), (38.0, 19.0, 0.45)]


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def wave_segments(y, x_from, x_to, amp=AMP):
    """Quadratic-bezier humps from x_from to x_to (x_from < x_to), first hump upwards."""
    segs, x, up = [], x_from, True
    while x < x_to - 1e-6:
        nx = min(x + HALF_WAVE, x_to)
        cy = y - 2 * amp if up else y + 2 * amp
        segs.append(((x, y), ((x + nx) / 2, cy), (nx, y)))
        x, up = nx, not up
    return segs


def wave(y, x_from, x_to, amp=AMP):
    """Path commands for a wave; x_to < x_from walks the very same curve backwards."""
    if x_from <= x_to:
        segs = wave_segments(y, x_from, x_to, amp)
        return " ".join(f"Q{f(c[0])},{f(c[1])} {f(e[0])},{f(e[1])}" for _, c, e in segs)
    segs = wave_segments(y, x_to, x_from, amp)
    return " ".join(f"Q{f(c[0])},{f(c[1])} {f(s_[0])},{f(s_[1])}" for s_, c, _ in reversed(segs))


def moon_clip():
    (y1, y2), h = STRIPES, STRIPE_HALF
    top, bottom = MOON_C[1] - MOON_R - 2, MOON_C[1] + MOON_R + 2
    a = f"M{f(X0)},{f(top)} L{f(X0)},{f(y1 - h)} {wave(y1 - h, X0, X1)} L{f(X1)},{f(top)} Z"
    b = f"M{f(X0)},{f(y1 + h)} {wave(y1 + h, X0, X1)} L{f(X1)},{f(y2 - h)} {wave(y2 - h, X1, X0)} Z"
    c = f"M{f(X0)},{f(y2 + h)} {wave(y2 + h, X0, X1)} L{f(X1)},{f(bottom)} L{f(X0)},{f(bottom)} Z"
    return f"{a} {b} {c}"


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 1,1 {f(cx + r)},{f(cy)} A{f(r)},{f(r)} 0 1,1 {f(cx - r)},{f(cy)} Z"


def reflection_paths():
    return [(f"M{f(xa)},{f(y)} {wave(y, xa, xb, amp=0.9)}", alpha) for y, xa, xb, alpha in REFLECTIONS]


# ---------- Android vector drawables ----------

AAPT = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"'


def hex_alpha(color, alpha):
    return f"#{int(round(alpha * 255)):02X}{color.lstrip('#')}"


def vd_background(rounded=None):
    shape = "M0,0 H108 V108 H0 Z" if rounded is None else (
        f"M{rounded},0 H{108 - rounded} A{rounded},{rounded} 0 0,1 108,{rounded} V{108 - rounded} "
        f"A{rounded},{rounded} 0 0,1 {108 - rounded},108 H{rounded} A{rounded},{rounded} 0 0,1 0,{108 - rounded} "
        f"V{rounded} A{rounded},{rounded} 0 0,1 {rounded},0 Z")
    stars = "\n".join(
        f'    <path android:fillColor="{hex_alpha("#FFFFFF", 0.55)}" android:pathData="{circle(x, y, r)}" />'
        for x, y, r in STARS)
    return f'''    <path android:pathData="{shape}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"
                android:startColor="{NIGHT_TOP}" android:endColor="{NIGHT_BOTTOM}" />
        </aapt:attr>
    </path>
    <path android:pathData="{shape}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{f(MOON_C[0])}" android:centerY="{f(MOON_C[1])}" android:gradientRadius="30"
                android:startColor="{hex_alpha(GLOW, 0.34)}" android:endColor="{hex_alpha(GLOW, 0.0)}" />
        </aapt:attr>
    </path>
{stars}'''


def vd_foreground(mono=False):
    moon_fill = ('android:fillColor="#FFFFFFFF"' if mono else "")
    moon_gradient = "" if mono else f'''
            <aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="44" android:startY="31" android:endX="64" android:endY="63"
                    android:startColor="{MOON_LIGHT}" android:endColor="{MOON_DARK}" />
            </aapt:attr>'''
    reflections = "\n".join(
        f'    <path android:pathData="{d}" android:strokeColor="{hex_alpha("#FFFFFF" if mono else WAVE, alpha)}" '
        f'android:strokeWidth="2.6" android:strokeLineCap="round" android:strokeLineJoin="round" />'
        for d, alpha in reflection_paths())
    return f'''    <group android:name="moon">
        <clip-path android:pathData="{moon_clip()}" />
        <path android:pathData="{circle(*MOON_C, MOON_R)}" {moon_fill}>{moon_gradient}
        </path>
    </group>
{reflections}'''


def vector(body, size=108):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by scripts/app-icon/generate.py; edit the script, not this file. -->
<vector {AAPT}
    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="108" android:viewportHeight="108">
{body}
</vector>
'''


def adaptive(foreground, background, monochrome):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{background}" />
    <foreground android:drawable="@drawable/{foreground}" />
    <monochrome android:drawable="@drawable/{monochrome}" />
</adaptive-icon>
'''


# ---------- SVG preview ----------

def svg_preview():
    refl = "".join(f'<path d="{d}" stroke="{WAVE}" stroke-opacity="{alpha}" stroke-width="2.6" stroke-linecap="round" fill="none"/>'
                   for d, alpha in reflection_paths())
    stars = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#fff" fill-opacity="0.55"/>' for x, y, r in STARS)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
<defs>
 <linearGradient id="night" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{NIGHT_TOP}"/><stop offset="1" stop-color="{NIGHT_BOTTOM}"/></linearGradient>
 <radialGradient id="glow" cx="{MOON_C[0]}" cy="{MOON_C[1]}" r="30" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{GLOW}" stop-opacity="0.34"/><stop offset="1" stop-color="{GLOW}" stop-opacity="0"/></radialGradient>
 <linearGradient id="moon" x1="44" y1="31" x2="64" y2="63" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{MOON_LIGHT}"/><stop offset="1" stop-color="{MOON_DARK}"/></linearGradient>
 <clipPath id="sea"><path d="{moon_clip()}"/></clipPath>
</defs>
<rect width="108" height="108" fill="url(#night)"/><rect width="108" height="108" fill="url(#glow)"/>{stars}
<path d="{circle(*MOON_C, MOON_R)}" fill="url(#moon)" clip-path="url(#sea)"/>{refl}
</svg>'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-svg")
    args = parser.parse_args()
    drawable = os.path.join(RES, "drawable")
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(drawable, exist_ok=True)
    os.makedirs(anydpi, exist_ok=True)
    files = {
        os.path.join(drawable, "ic_launcher_xiangyin_background.xml"): vector(vd_background()),
        os.path.join(drawable, "ic_launcher_xiangyin_foreground.xml"): vector(vd_foreground()),
        os.path.join(drawable, "ic_launcher_xiangyin_monochrome.xml"): vector(vd_foreground(mono=True)),
        os.path.join(drawable, "ic_xiangyin_logo.xml"): vector(vd_background(rounded=30) + "\n" + vd_foreground()),
    }
    # Status bar icon: the monochrome mark cropped to its content (white, alpha only).
    files[os.path.join(drawable, "ic_stat_xiangyin.xml")] = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by scripts/app-icon/generate.py; edit the script, not this file. -->
<vector {AAPT}
    android:width="24dp" android:height="24dp" android:viewportWidth="64" android:viewportHeight="64">
    <group android:translateX="-22" android:translateY="-22">
{vd_foreground(mono=True)}
    </group>
</vector>
'''
    for name in ("ic_launcher_xiangyin", "ic_launcher_xiangyin_round"):
        files[os.path.join(anydpi, f"{name}.xml")] = adaptive(
            "ic_launcher_xiangyin_foreground", "ic_launcher_xiangyin_background", "ic_launcher_xiangyin_monochrome")
    for path, content in files.items():
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(content)
        print("wrote", os.path.relpath(path, ROOT))
    if args.preview_svg:
        with open(args.preview_svg, "w", encoding="utf-8") as fh:
            fh.write(svg_preview())


if __name__ == "__main__":
    main()
