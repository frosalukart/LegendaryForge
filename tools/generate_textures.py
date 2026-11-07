"""
Generator tekstur LegendaryForge (64x64, pixel-art ber-shading).
Jalankan:  python3 tools/generate_textures.py   (butuh: pip install pillow numpy)
Tiap senjata digambar dalam kanvas 512px (supersample), diputar 45 derajat, lalu diperkecil ke 64px,
dikuantisasi palet warna, diberi outline 1px dan glow halus.
"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

W = 512
OUT_SIZE = 64
CX = 256

# ------------------------------------------------------------- util
def layer(): return Image.new("RGBA", (W, W), (0, 0, 0, 0))
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))
def mirror(pts, cx=CX): return [(2 * cx - x, y) for x, y in pts]

def poly_mask(pts):
    m = Image.new("L", (W, W), 0); ImageDraw.Draw(m).polygon(pts, fill=255); return m

def grad_img(c1, c2, box, axis="v"):
    x0, y0, x1, y1 = box
    a = np.zeros((W, W, 4), dtype=np.uint8)
    if axis == "v":
        t = np.clip((np.arange(W) - y0) / max(1, y1 - y0), 0, 1)[:, None] * np.ones((1, W))
    else:
        t = np.clip((np.arange(W) - x0) / max(1, x1 - x0), 0, 1)[None, :] * np.ones((W, 1))
    for i in range(3): a[..., i] = (c1[i] + (c2[i] - c1[i]) * t).astype(np.uint8)
    a[..., 3] = 255
    return Image.fromarray(a, "RGBA")

def shade(L, pts, c1, c2, axis="v"):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    g = grad_img(c1, c2, (min(xs), min(ys), max(xs), max(ys)), axis)
    L.paste(g, (0, 0), poly_mask(pts))

def radial(L, c, r, stops):
    """stops: [(t, color), ...] t=0 pusat, 1 tepi"""
    yy, xx = np.mgrid[0:W, 0:W]
    d = np.sqrt((xx - c[0]) ** 2 + (yy - c[1]) ** 2) / r
    a = np.zeros((W, W, 4), dtype=np.uint8)
    for ch in range(3):
        a[..., ch] = np.interp(d, [s[0] for s in stops], [s[1][ch] for s in stops])
    a[..., 3] = np.where(d <= 1, 255, 0)
    L.alpha_composite(Image.fromarray(a, "RGBA"))

def line(L, pts, col, w):
    d = ImageDraw.Draw(L); d.line(pts, fill=col, width=w, joint="curve")
    for p in (pts[0], pts[-1]): d.ellipse([p[0]-w/2, p[1]-w/2, p[0]+w/2, p[1]+w/2], fill=col)

def bez(p0, p1, p2, n=40):
    return [((1-t)**2*p0[0]+2*(1-t)*t*p1[0]+t*t*p2[0], (1-t)**2*p0[1]+2*(1-t)*t*p1[1]+t*t*p2[1])
            for t in [i/n for i in range(n+1)]]

def star(L, c, r, col):
    x, y = c
    ImageDraw.Draw(L).polygon([(x, y-r), (x+r*.18, y-r*.18), (x+r, y), (x+r*.18, y+r*.18),
                               (x, y+r), (x-r*.18, y+r*.18), (x-r, y), (x-r*.18, y-r*.18)], fill=col)

def wrap_bands(L, x0, x1, y0, y1, c1, c2, step=26):
    d = ImageDraw.Draw(L)
    y = y0
    while y < y1:
        d.line([(x0, y + 12), (x1, y - 4)], fill=c1, width=9)
        d.line([(x0, y + 18), (x1, y + 2)], fill=c2, width=4)
        y += step

def make_glow(core, color, blur=14, strength=0.9):
    a = core.split()[3].filter(ImageFilter.GaussianBlur(blur))
    a = a.point(lambda v: min(255, int(v * strength * 1.6)))
    g = Image.new("RGBA", core.size, color + (0,)); g.putalpha(a); return g

def edge_light(L, pts, col, w=5):
    line(L, pts, col, w)

# ------------------------------------------------------------- designs (tip up, pusat x=256)
def sword_common_hilt(C, gold1, gold2, gem, y=338, grip=(60, 38, 30), grip2=(30, 20, 18)):
    # crossguard bersayap
    half = [(256, y-4), (214, y-18), (150, y-34), (96, y-20), (70, y+6), (120, y+10), (150, y+30), (214, y+34), (256, y+30)]
    shade(C, half, gold1, gold2); shade(C, mirror(half), gold2, gold1)
    line(C, [(256, y-4), (214, y-18), (150, y-34), (100, y-20)], lerp(gold1, (255, 255, 255), .5), 5)
    line(C, mirror([(256, y-4), (214, y-18), (150, y-34), (100, y-20)]), lerp(gold1, (255, 255, 255), .4), 5)
    radial(C, (256, y+8), 26, [(0, lerp(gem, (255, 255, 255), .7)), (.45, gem), (1, lerp(gem, (0, 0, 0), .6))])
    # grip
    shade(C, [(240, y+30), (272, y+30), (270, 452), (242, 452)], grip, grip2, "h")
    wrap_bands(C, 242, 270, y+36, 440, lerp(grip, (255, 255, 255), .25), grip2)
    # pommel
    radial(C, (256, 474), 28, [(0, lerp(gem, (255, 255, 255), .6)), (.4, gem), (.75, gold2), (1, lerp(gold2, (0, 0, 0), .5))])

def luminance_scepter():
    C = layer()
    gold1, gold2, gold3 = (255, 232, 140), (222, 168, 40), (130, 82, 14)
    shade(C, [(244, 190), (270, 190), (272, 480), (242, 480)], gold1, gold3, "h")
    line(C, [(247, 200), (246, 470)], (255, 250, 215), 4)
    for y in (300, 392):   # kain putih pembungkus
        shade(C, [(238, y), (278, y), (278, y+34), (238, y+34)], (255, 255, 255), (200, 200, 215), "h")
        line(C, [(238, y+8), (278, y+2)], (230, 230, 240), 3)
    # pommel
    shade(C, [(244, 478), (270, 478), (257, 508)], gold1, gold3, "h")
    # penyangga sabit
    d = ImageDraw.Draw(C)
    crescent = bez((170, 130), (190, 250), (256, 245), 30) + bez((256, 245), (322, 250), (342, 130), 30)
    inner = bez((342, 130), (320, 222), (256, 218), 30) + bez((256, 218), (192, 222), (170, 130), 30)
    shade(C, crescent + inner, gold1, gold3)
    line(C, bez((170, 130), (190, 250), (256, 245)) + bez((256, 245), (322, 250), (342, 130)), (255, 250, 210), 4)
    # sinar matahari
    for i in range(16):
        a = math.radians(i * 22.5 - 90); r0, r1 = 66, 128 if i % 2 == 0 else 100
        w = 0.20 if i % 2 == 0 else 0.15
        p = [(256 + r0 * math.cos(a - w), 118 + r0 * math.sin(a - w)), (256 + r1 * math.cos(a), 118 + r1 * math.sin(a)),
             (256 + r0 * math.cos(a + w), 118 + r0 * math.sin(a + w))]
        shade(C, p, gold1, (255, 150, 30))
    radial(C, (256, 118), 70, [(0, (255, 255, 245)), (.35, (255, 246, 170)), (.75, (255, 196, 60)), (1, (200, 110, 20))])
    d.ellipse([196, 58, 316, 178], outline=(255, 214, 90), width=6)
    star(C, (232, 96), 22, (255, 255, 255)); star(C, (338, 40), 16, (255, 255, 230)); star(C, (180, 230), 12, (255, 255, 230))
    return C, (255, 215, 80)

def void_scythe():
    C = layer()
    d1, d2 = (46, 26, 66), (14, 6, 24)
    shade(C, [(246, 60), (270, 60), (268, 490), (244, 490)], (110, 70, 150), d2, "h")
    line(C, [(248, 90), (247, 480)], (170, 120, 220), 3)
    for y in (210, 300, 400):   # cincin perak-ungu
        shade(C, [(238, y), (278, y), (278, y+16), (238, y+16)], (190, 170, 215), (80, 60, 110), "h")
    for y in (462,):
        shade(C, [(236, y), (280, y), (268, y+40), (248, y+40)], (160, 120, 200), (40, 20, 60), "h")
    # bilah sabit
    outer = bez((256, 52), (70, 24), (24, 262), 50)
    inner = bez((24, 262), (120, 112), (256, 108), 50)
    blade = outer + inner
    shade(C, blade, (30, 6, 52), (160, 40, 200), "h")
    # sisi dalam lebih terang (ketajaman)
    line(C, inner, (240, 190, 255), 7)
    line(C, outer[:38], (130, 60, 190), 5)
    # retakan void
    line(C, [(200, 74), (170, 96), (150, 90), (118, 122), (96, 150)], (255, 120, 255), 3)
    # orb/mata di persambungan
    radial(C, (256, 106), 36, [(0, (255, 240, 255)), (.25, (230, 110, 255)), (.6, (90, 20, 140)), (1, (10, 0, 18))])
    ImageDraw.Draw(C).ellipse([220, 70, 292, 142], outline=(210, 160, 245), width=5)
    ImageDraw.Draw(C).ellipse([248, 92, 264, 120], fill=(10, 0, 18))   # pupil
    for c, r in (((90, 210), 10), ((300, 160), 8), ((60, 130), 7)): star(C, c, r, (240, 170, 255))
    return C, (180, 70, 255)

def stormcaller_blade():
    C = layer()
    blade = [(256, 14), (224, 62), (217, 330), (256, 330)]
    shade(C, blade, (214, 240, 255), (96, 160, 235), "h")
    shade(C, mirror(blade), (62, 110, 200), (24, 56, 130), "h")
    line(C, [(256, 18), (256, 332)], (22, 44, 110), 6)                                     # fuller
    line(C, [(226, 64), (219, 328)], (255, 255, 255), 4)                                   # kilau tepi
    line(C, mirror([(226, 64), (219, 328)]), (110, 190, 255), 3)
    zig = [(256, 70), (238, 122), (262, 140), (236, 196), (264, 214), (240, 268), (258, 316)]  # petir terukir
    line(C, zig, (80, 220, 255), 9); line(C, zig, (255, 255, 255), 3)
    sword_common_hilt(C, (255, 226, 130), (176, 120, 28), (80, 220, 255), 340, (52, 52, 70), (22, 22, 34))
    for c, r in (((176, 150), 12), ((338, 232), 10), ((190, 280), 8)): star(C, c, r, (200, 245, 255))
    return C, (90, 200, 255)

def ignis_core_blade():
    C = layer()
    blade = [(256, 12), (216, 58), (204, 150), (210, 330), (256, 330)]
    shade(C, blade, (110, 72, 70), (34, 22, 26), "h")
    shade(C, mirror(blade), (60, 36, 38), (16, 10, 14), "h")
    line(C, [(222, 52), (209, 150), (214, 326)], (255, 140, 50), 4)                        # tepi menyala
    line(C, mirror([(222, 52), (209, 150), (214, 326)]), (255, 90, 30), 4)
    cracks = [[(256, 40), (246, 90), (264, 120), (244, 168), (268, 210), (250, 262), (262, 322)],
              [(246, 90), (226, 104), (222, 132)], [(264, 120), (290, 134), (296, 168)],
              [(244, 168), (224, 190), (230, 226)], [(268, 210), (292, 226)]]
    for cr in cracks:
        line(C, cr, (255, 96, 20), 9); line(C, cr, (255, 214, 90), 4)
    sword_common_hilt(C, (62, 40, 44), (20, 12, 16), (255, 150, 40), 340, (110, 30, 24), (50, 12, 10))
    line(C, [(100, 322), (150, 308), (214, 322)], (255, 120, 30), 5)
    line(C, mirror([(100, 322), (150, 308), (214, 322)]), (255, 120, 30), 5)
    for c, r in (((330, 120), 9), ((184, 90), 8), ((300, 40), 7)): star(C, c, r, (255, 190, 80))
    return C, (255, 110, 30)

def leviathans_trident():
    C = layer()
    bone1, bone2, bone3 = (236, 252, 250), (130, 205, 215), (40, 110, 150)
    shade(C, [(244, 200), (270, 200), (268, 490), (246, 490)], bone1, bone3, "h")
    for y in range(230, 470, 52):
        shade(C, [(236, y), (280, y), (280, y+14), (236, y+14)], (60, 150, 210), (20, 70, 130), "h")
    # sirip bawah
    shade(C, [(256, 440), (200, 500), (244, 480)], (60, 170, 220), (20, 80, 150))
    shade(C, mirror([(256, 440), (200, 500), (244, 480)]), (40, 120, 190), (14, 56, 120))
    # prong tengah
    mid = [(256, 14), (234, 70), (238, 216), (256, 216)]
    shade(C, mid, bone1, bone2, "h"); shade(C, mirror(mid), bone2, bone3, "h")
    # prong samping melengkung
    left = bez((226, 218), (110, 218), (112, 130), 24) + [(112, 130), (96, 50), (122, 100)] + bez((132, 108), (140, 188), (240, 188), 24)
    shade(C, left, bone1, bone3, "h")
    shade(C, mirror(left), bone2, (28, 84, 130), "h")
    # palang
    shade(C, [(176, 196), (336, 196), (322, 230), (190, 230)], (190, 232, 240), (40, 110, 160))
    line(C, [(182, 202), (330, 202)], (255, 255, 255), 4)
    radial(C, (256, 212), 26, [(0, (230, 255, 255)), (.4, (90, 220, 240)), (1, (10, 70, 150))])
    for c in ((246, 110), (246, 150)): ImageDraw.Draw(C).ellipse([c[0]-4, c[1]-14, c[0]+4, c[1]+14], fill=(60, 160, 200))
    for c, r in (((170, 300), 10), ((350, 380), 9), ((330, 80), 8), ((150, 170), 7)):
        ImageDraw.Draw(C).ellipse([c[0]-r, c[1]-r*1.3, c[0]+r, c[1]+r*1.3], fill=(170, 230, 255, 220))
    return C, (70, 190, 255)

def terra_breaker():
    C = layer()
    wood1, wood2 = (150, 100, 56), (58, 36, 20)
    shade(C, [(240, 150), (276, 150), (274, 492), (242, 492)], wood1, wood2, "h")
    wrap_bands(C, 242, 274, 330, 470, (200, 160, 100), (70, 44, 26), 30)
    for y in (160, 320, 484):
        shade(C, [(234, y), (282, y), (282, y+18), (234, y+18)], (110, 108, 116), (36, 34, 42), "h")
    # kepala kapak (di kiri bilah)
    head = [(238, 52), (160, 20), (70, 40), (26, 120), (56, 210), (130, 250), (238, 226)]
    shade(C, head, (178, 172, 166), (62, 58, 60), "h")
    # sisi tajam netherite
    edge = bez((160, 20), (40, 24), (26, 120), 20) + bez((26, 120), (30, 220), (130, 250), 20)
    line(C, edge, (34, 28, 36), 16); line(C, edge, (96, 84, 100), 5)
    line(C, bez((164, 24), (50, 28), (32, 120), 20), (225, 215, 235), 3)
    # retakan batu + lumut + rune
    for cr in ([(200, 60), (186, 100), (150, 120), (140, 168), (96, 196)], [(186, 100), (214, 140), (206, 200)],
               [(150, 120), (110, 96), (90, 70)]):
        line(C, cr, (255, 160, 30), 8); line(C, cr, (255, 226, 120), 3)
    for c in ((90, 150), (170, 190), (120, 60)):
        ImageDraw.Draw(C).ellipse([c[0]-18, c[1]-10, c[0]+18, c[1]+10], fill=(86, 128, 58))
    # gigi batu
    for p in ([(238, 52), (258, 12), (252, 64)], [(238, 226), (262, 262), (246, 214)], [(150, 8), (160, -4), (176, 22)]):
        shade(C, p, (186, 180, 174), (80, 76, 78))
    radial(C, (240, 140), 28, [(0, (255, 244, 180)), (.4, (255, 176, 40)), (1, (110, 50, 6))])
    for c, r in (((300, 40), 8), ((60, 270), 9)): star(C, c, r, (255, 214, 120))
    return C, (255, 160, 40)

def zephyr_bow(pull=0):
    C = layer()
    sx = 236 - (0, 70, 120, 150)[pull]
    limb = bez((236, 24), (452, 150), (404, 256), 40) + bez((404, 256), (452, 362), (236, 488), 40)
    thick = []
    for off in (-1, 1):
        pass
    # tebal: gambar sebagai garis bergradasi 3 lapis
    line(C, limb, (40, 120, 120), 36)
    line(C, limb, (120, 226, 190), 26)
    line(C, limb, (214, 255, 240), 12)
    line(C, bez((240, 28), (450, 150), (404, 256), 40), (255, 255, 255), 4)
    # pegangan
    line(C, [(398, 220), (408, 256), (398, 292)], (54, 90, 84), 36)
    wrap_bands(C, 386, 420, 222, 294, (110, 180, 160), (28, 56, 52), 20)
    # bulu hias di ujung limb
    for (px, py, sgn) in ((250, 40, 1), (250, 472, -1)):
        shade(C, [(px, py), (px+70, py+sgn*(-4)), (px+30, py+sgn*28)], (255, 255, 255), (150, 235, 215))
        shade(C, [(px+10, py), (px+84, py+sgn*(-22)), (px+46, py+sgn*8)], (230, 255, 250), (110, 215, 200))
    # senar
    string = [(236, 24), (sx, 256), (236, 488)]
    line(C, string, (240, 250, 250), 4)
    # angin
    for arc in (bez((330, 70), (470, 110), (440, 180), 20), bez((330, 440), (470, 400), (440, 332), 20)):
        line(C, arc, (220, 255, 250, 170), 6)
    if pull:  # anak panah
        ax = sx
        line(C, [(ax, 256), (470, 256)], (180, 140, 90), 9)
        shade(C, [(470, 238), (500, 256), (470, 274)], (230, 240, 245), (120, 140, 150))
        shade(C, [(ax-6, 256), (ax+30, 232), (ax+40, 256), (ax+30, 280)], (120, 230, 200), (60, 160, 150))
    star(C, (300, 120), 14, (255, 255, 255)); star(C, (330, 400), 12, (230, 255, 250))
    return C, (120, 255, 210)

DESIGNS = {  # id: (fn, rotasi)  tip-atas -> putar -45 (searah jarum jam); busur putar +45
    "luminance_scepter": (luminance_scepter, -45), "void_scythe": (void_scythe, -45),
    "stormcaller_blade": (stormcaller_blade, -45), "ignis_core_blade": (ignis_core_blade, -45),
    "leviathans_trident": (leviathans_trident, -45), "terra_breaker": (terra_breaker, -45),
}

# ------------------------------------------------------------- finishing
def finish(core, glow_col, rot, size=OUT_SIZE):
    glow = make_glow(core, glow_col)
    core = core.rotate(rot, resample=Image.BICUBIC, expand=False, center=(W/2, W/2))
    glow = glow.rotate(rot, resample=Image.BICUBIC, expand=False, center=(W/2, W/2))
    bb = core.split()[3].point(lambda v: 255 if v > 40 else 0).getbbox()
    pad = 24
    x0, y0, x1, y1 = bb; s = max(x1-x0, y1-y0) + pad*2
    cx, cy = (x0+x1)/2, (y0+y1)/2
    box = (int(cx-s/2), int(cy-s/2), int(cx+s/2), int(cy+s/2))
    core = core.crop(box).resize((size, size), Image.LANCZOS)
    glow = glow.crop(box).resize((size, size), Image.LANCZOS)
    # alpha tegas + palet terbatas (nuansa pixel-art)
    a = core.split()[3].point(lambda v: 255 if v > 110 else 0)
    rgb = Image.new("RGB", (size, size), (0, 0, 0)); rgb.paste(core.convert("RGB"), (0, 0), a)
    q = rgb.quantize(colors=32, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
    core = q.convert("RGBA"); core.putalpha(a)
    # outline 1px
    arr = np.array(core); al = arr[..., 3] > 0
    nb = np.zeros_like(al)
    nb[1:, :] |= al[:-1, :]; nb[:-1, :] |= al[1:, :]; nb[:, 1:] |= al[:, :-1]; nb[:, :-1] |= al[:, 1:]
    ol = nb & ~al
    outl = np.zeros_like(arr); outl[ol] = (16, 12, 22, 255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    g = np.array(glow); g[..., 3] = (g[..., 3] * 0.55).astype(np.uint8)
    out.alpha_composite(Image.fromarray(g, "RGBA"))
    out.alpha_composite(Image.fromarray(outl, "RGBA")); out.alpha_composite(core)
    return out

def main(res_dir, bed_dir):
    os.makedirs(res_dir, exist_ok=True); os.makedirs(bed_dir, exist_ok=True)
    made = {}
    for id_, (fn, rot) in DESIGNS.items():
        core, gc = fn(); made[id_] = finish(core, gc, rot)
    for pull in range(4):
        core, gc = zephyr_bow(pull)
        name = "zephyr_tempest_bow" if pull == 0 else f"zephyr_tempest_bow_pulling_{pull-1}"
        made[name] = finish(core, gc, 45)
    for name, im in made.items():
        im.save(os.path.join(res_dir, name + ".png"))
        if "pulling" not in name: im.save(os.path.join(bed_dir, name + ".png"))
    return made

if __name__ == "__main__":
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    made = main(os.path.join(base, "resourcepack/assets/legendaryforge/textures/item"), os.path.join(base, "bedrock-pack/textures/items"))
    # contact sheet pratinjau
    names = list(made)
    sheet = Image.new("RGBA", (len(names) * 260, 260), (46, 46, 56, 255))
    for i, n in enumerate(names): sheet.alpha_composite(made[n].resize((256, 256), Image.NEAREST), (i * 260 + 2, 2))
    sheet.save("/tmp/sheet.png"); print("OK", names)
