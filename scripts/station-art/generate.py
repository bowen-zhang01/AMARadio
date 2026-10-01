#!/usr/bin/env python3
"""Generate the artwork for the stations of the bundled curated playlist.

Each image is a square, full-bleed tile: a two-tone gradient with soft radio-wave arcs,
a small region label, the station's short name and its frequency or full name.
Content stays inside the central circle so the tiles also work when clipped round.

Usage: python3 scripts/station-art/generate.py [--preview contact-sheet.png]
Requires Pillow. Noto Sans SC (SIL Open Font License) is downloaded to a cache dir.
"""
import argparse
import os
import urllib.request

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "curated", "logos")
FONT_CACHE = os.path.expanduser("~/.cache/amaradio-fork/fonts")
FONT_URL = "https://github.com/notofonts/noto-cjk/raw/main/Sans/SubsetOTF/SC/NotoSansSC-{}.otf"

SIZE = 512          # output size in px
SCALE = 4           # supersampling factor for smooth edges

# id, region label, short name, subtitle, gradient (light, dark)
STATIONS = [
    ("beijing-news", "北京", "新闻", "FM 94.5", ("#E5484D", "#A3192D")),
    ("beijing-jjj", "北京", "京津冀", "FM 100.6", ("#12A594", "#0B5A55")),
    ("beijing-traffic", "北京", "交通", "FM 103.9", ("#F0890A", "#C2410C")),
    ("beijing-music", "北京", "音乐", "FM 97.4", ("#9055E0", "#4C2889")),
    ("beijing-arts", "北京", "文艺", "FM 87.6", ("#E93D82", "#9E1C56")),
    ("beijing-city", "北京", "城市", "FM 102.5", ("#3E63DD", "#1F2D7A")),
    ("cnr-china", "中央台", "中国", "中国之声", ("#D93838", "#8A1414")),
    ("cnr-economy", "中央台", "经济", "经济之声", ("#2F9E66", "#145A3B")),
    ("cnr-music", "中央台", "音乐", "音乐之声", ("#B04AD6", "#5E1E8C")),
    ("cnr-classic", "中央台", "经典", "经典音乐广播", ("#5B5BD6", "#2C2C7A")),
    ("cnr-arts", "中央台", "文艺", "文艺之声", ("#E0457B", "#8F1D4D")),
    ("cnr-traffic", "中央台", "交通", "中国交通广播", ("#F08A24", "#B4440E")),
    ("cnr-rural", "中央台", "乡村", "中国乡村之声", ("#4FA34F", "#1F5E2B")),
    ("cnr-senior", "中央台", "老年", "老年之声", ("#D08A3C", "#8A4B16")),
    ("cnr-hongkong", "中央台", "香港", "香港之声", ("#D6409F", "#7E1A5E")),
    ("cnr-zhonghua", "中央台", "中华", "中华之声", ("#2F5BD3", "#1A2F78")),
    ("cnr-shenzhou", "中央台", "神州", "神州之声", ("#0A92B5", "#064B63")),
    ("cnr-huaxia", "中央台", "华夏", "华夏之声", ("#D99A1E", "#8F5B07")),
    ("cnr-minzu", "中央台", "民族", "民族之声", ("#25A07F", "#0E5444")),
    ("cri-global", "国际台", "环球", "环球资讯广播", ("#1E7FD8", "#0A3F73")),
    ("cri-english", "国际台", "英语", "英语资讯广播", ("#5E6AD2", "#2E3470")),
    ("cri-southsea", "国际台", "南海", "南海之声", ("#0B97BE", "#024A63")),
]


def font(weight, size):
    os.makedirs(FONT_CACHE, exist_ok=True)
    path = os.path.join(FONT_CACHE, f"NotoSansSC-{weight}.otf")
    if not os.path.exists(path):
        urllib.request.urlretrieve(FONT_URL.format(weight), path)
    return ImageFont.truetype(path, size)


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def diagonal_gradient(size, light, dark):
    small = 256
    mask = Image.new("L", (small, small))
    mask.putdata([min(255, int((x + y) / (2 * (small - 1)) * 255)) for y in range(small) for x in range(small)])
    mask = mask.resize((size, size), Image.BICUBIC)
    return Image.composite(Image.new("RGB", (size, size), dark), Image.new("RGB", (size, size), light), mask)


def radial_mask(size, cx, cy, radius):
    small = 256
    mask = Image.new("L", (small, small))
    data = []
    for y in range(small):
        for x in range(small):
            d = ((x / small - cx) ** 2 + (y / small - cy) ** 2) ** 0.5 / radius
            data.append(int(max(0.0, 1.0 - d) ** 2 * 255))
    mask.putdata(data)
    return mask.resize((size, size), Image.BICUBIC)


def draw_spaced(draw, center_x, y, text, fnt, fill, spacing):
    widths = [draw.textlength(ch, font=fnt) for ch in text]
    total = sum(widths) + spacing * (len(text) - 1)
    x = center_x - total / 2
    for ch, w in zip(text, widths):
        draw.text((x, y), ch, font=fnt, fill=fill, anchor="ls")
        x += w + spacing


def render(region, name, subtitle, colors):
    s = SIZE * SCALE
    light, dark = (hex_rgb(c) for c in colors)
    img = diagonal_gradient(s, light, dark).convert("RGBA")

    # Soft light from the top-left corner.
    glow = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    glow.putalpha(radial_mask(s, 0.1, 0.05, 0.9).point(lambda v: int(v * 0.22)))
    img = Image.alpha_composite(img, glow)

    # Radio waves from the bottom-right corner.
    waves = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    wd = ImageDraw.Draw(waves)
    for i in range(4):
        r = s * (0.30 + 0.19 * i)
        wd.arc((s - r, s - r, s + r, s + r), 180, 270, fill=(255, 255, 255, 30 - i * 5), width=int(s * 0.026))
    img = Image.alpha_composite(img, waves)

    # Typography: region label, short name, subtitle.
    cx = s / 2
    main_size = {1: 0.36, 2: 0.30, 3: 0.235}.get(len(name), 0.18) * s
    main_font = font("Black", int(main_size))

    shadow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.text((cx, s * 0.535 + s * 0.008), name, font=main_font, fill=(0, 0, 0, 70), anchor="mm")
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(s * 0.012)))

    text_layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    td = ImageDraw.Draw(text_layer)
    draw_spaced(td, cx, s * 0.285, region, font("Medium", int(s * 0.072)), (255, 255, 255, 205), s * 0.018)
    td.text((cx, s * 0.535), name, font=main_font, fill=(255, 255, 255, 255), anchor="mm")
    sub_font = font("Medium", int(s * (0.078 if len(subtitle) <= 6 else 0.066)))
    draw_spaced(td, cx, s * 0.80, subtitle, sub_font, (255, 255, 255, 225), s * 0.008)
    img = Image.alpha_composite(img, text_layer)

    return img.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="also write a contact sheet to this path")
    args = parser.parse_args()

    os.makedirs(OUT_DIR, exist_ok=True)
    tiles = []
    for station_id, region, name, subtitle, colors in STATIONS:
        tile = render(region, name, subtitle, colors)
        tile.save(os.path.join(OUT_DIR, f"{station_id}.png"), optimize=True)
        tiles.append(tile)
        print("wrote", station_id)

    if args.preview:
        cols, cell, gap = 6, 200, 16
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (cell + gap) + gap, rows * (cell + gap) + gap), (245, 245, 247))
        mask = Image.new("L", (cell, cell), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, cell, cell), radius=cell * 0.22, fill=255)
        for i, tile in enumerate(tiles):
            x = gap + (i % cols) * (cell + gap)
            y = gap + (i // cols) * (cell + gap)
            sheet.paste(tile.resize((cell, cell), Image.LANCZOS), (x, y), mask)
        sheet.save(args.preview)


if __name__ == "__main__":
    main()
