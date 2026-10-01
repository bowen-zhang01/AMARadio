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
    ("btv-satellite", "北京电视", "卫视", "BTV-1", ("#2563C9", "#0F2A66")),
    ("btv-news", "北京电视", "新闻", "BTV-9", ("#D23B3B", "#7C1616")),
    ("btv-arts", "北京电视", "文艺", "BTV-2", ("#C2459A", "#6B1C55")),
    ("btv-science", "北京电视", "科教", "BTV-3", ("#0E9F9A", "#06504E")),
    ("btv-finance", "北京电视", "财经", "BTV-5", ("#D49A12", "#7D5605")),
    ("btv-life", "北京电视", "生活", "BTV-7", ("#4E9F4E", "#1F5426")),
    ("district-yanqing", "北京", "延庆", "延庆之声", ("#6C8F2D", "#34471A")),
    ("district-shunyi", "北京", "顺义", "FM 92.9", ("#2E8BC9", "#154670")),
    ("district-daxing", "北京", "大兴", "大兴广播", ("#C66A3B", "#6B3419")),
    ("district-huairou", "北京", "怀柔", "综合广播", ("#3A9E86", "#185244")),
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
    # Chinese underground & indie playlist (curated/chinese-underground.m3u)
    ("citypop-four", "台灣", "城市流行", "City Pop Four", ("#F06292", "#5E35B1")),
    ("shanghai-lido", "老上海", "麗都", "1940s 時代曲", ("#C9A227", "#3B2F1E")),
    ("livesshhs", "中国", "摇滚", "LIVESSHHS", ("#C62828", "#1C1C1C")),
    ("hkcr", "香港", "HKCR", "社區電台", ("#00BFA5", "#004D40")),
    ("belowground", "香港", "地庫", "Belowground", ("#7C4DFF", "#1A1035")),
    ("drhk", "香港", "數碼", "粵語金曲", ("#29B6F6", "#01579B")),
    ("shirley-spinoza", "大理", "怪奇", "S&S Radio", ("#FF8A65", "#5D4037")),
    ("chinese-music-world", "民乐", "古琴", "传统器乐", ("#A1887F", "#3E2723")),
    ("thmr", "二次元", "东方", "THMR", ("#EC407A", "#4A148C")),
    ("phate", "二次元", "飛特", "Phate Radio", ("#26C6DA", "#1A237E")),
    ("formosa-new-voice", "台北", "寶島新聲", "FM 98.5", ("#43A047", "#1B5E20")),
    ("green-peace", "台北", "綠色和平", "FM 97.3", ("#9CCC65", "#33691E")),
    ("formosa-hakka", "客家", "寶島客家", "FM 93.7", ("#FFB300", "#E65100")),
    ("xiagang", "高雄", "下港", "FM 90.5", ("#EF5350", "#880E4F")),
    ("dajin", "金門", "大金", "FM 106.3", ("#F9A825", "#7A4A00")),
    ("victory-am774", "台南", "勝利", "AM 774", ("#F4511E", "#6D1B00")),
    ("radio-1766", "台灣", "1766", "線上電臺", ("#5C6BC0", "#1A237E")),
    ("kuanghua", "心戰", "光華", "1963", ("#1E88E5", "#0D2B5E")),
    ("fuhsing", "心戰", "復興", "1957", ("#5E35B1", "#1F0F4D")),
    ("rti-mandarin", "央廣", "華語", "Rti", ("#00897B", "#00332E")),
    ("rfa-mandarin", "境外", "自由亚洲", "RFA", ("#C62828", "#3B0A0A")),
    ("rfi-chinese", "境外", "法广", "RFI 华语", ("#E53935", "#1A237E")),
    ("vatican-chinese", "境外", "梵蒂冈", "中文广播", ("#FBC02D", "#795548")),
    ("police-radio", "台灣", "警廣", "治安交通網", ("#1565C0", "#0A2A5C")),
    ("voice-of-han", "國軍", "漢聲", "調頻網", ("#558B2F", "#1B3A0B")),
    ("fisheries", "台灣", "漁業", "漁民電台", ("#0288D1", "#002F4B")),
    ("taipei-indigenous", "原住民", "海洋", "AM 1134", ("#FF7043", "#4E342E")),
    ("cancheers", "公益", "牽手", "CanCheers", ("#F48FB1", "#880E4F")),
    ("vila-verde", "澳門", "綠邨", "FM 99.5", ("#2E7D32", "#0B3D0E")),
    ("nccu", "校園", "政大", "FM 88.7", ("#3949AB", "#151B54")),
    ("shih-hsin", "校園", "世新", "FM 88.1", ("#D81B60", "#560027")),
    ("ntua", "校園", "臺藝", "FM 88.3", ("#8E24AA", "#2A0838")),
    ("fju", "校園", "輔大", "FM 88.5", ("#00ACC1", "#00363D")),
    ("2cr", "悉尼", "2CR", "中文廣播", ("#FB8C00", "#7A3300")),
    ("2ac-cantonese", "悉尼", "2AC", "粵語台", ("#E91E63", "#5A0A26")),
    ("ac878", "悉尼", "878", "AC 華語 FM", ("#7CB342", "#2E4A12")),
    ("nz-906", "奥克兰", "中文台", "FM 90.6", ("#26A69A", "#0B3B37")),
    ("chmb-1320", "溫哥華", "華僑", "AM 1320", ("#D32F2F", "#4A0E0E")),
    ("fairchild-1430", "多倫多", "中文台", "AM 1430", ("#1976D2", "#0B2D57")),
    ("kazn-1300", "洛杉磯", "KAZN", "AM 1300", ("#FFA000", "#6B3E00")),
    ("wzrc-1480", "紐約", "粵語", "AM 1480", ("#546E7A", "#101C22")),
    ("dubai-chinese", "中东", "迪拜", "华人广播", ("#FFB74D", "#8D4A00")),
    ("jakarta-983", "雅加达", "华语台", "FM 98.3", ("#EC407A", "#6A1B4D")),
    ("bigcow", "大马", "大牛", "BIGCOWFM", ("#43A047", "#123D17")),
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
