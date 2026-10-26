"""
Builds MyCar's launcher icon from the logo art (docs/icon/mycar-logo.jpg):

    python3 docs/icon/make_icons.py docs/icon/mycar-logo.jpg .

Writes the adaptive icon's foreground at every density, for the release build (blue shackle)
and the debug build (amber shackle), and docs/icon/mycar-icon.png for the README. The
monochrome themed icon is a hand-drawn vector: app/src/main/res/drawable/ic_launcher_monochrome.xml.
"""
import sys, os
import numpy as np
from PIL import Image, ImageDraw

src, repo = sys.argv[1], sys.argv[2]
BG = (0, 6, 17)                 # the art's own background, measured on its border
SUBJECT_C = (702, 732)          # centre of the padlock and its wheels in the source
SUBJECT_R = 552                 # farthest subject pixel from that centre, source pixels
TARGET_R = 0.295                # of the 108dp canvas: inside the 66dp safe circle (0.3056)
FEATHER = 90                    # px of fade from the art's edge into the flat background

art = Image.open(src).convert('RGB')
side = int(round(SUBJECT_R / TARGET_R))
side += side % 2
ox, oy = side // 2 - SUBJECT_C[0], side // 2 - SUBJECT_C[1]

# Alpha that fades the art's edges into the flat fill, so the canvas has no seam.
w, h = art.size
ramp_x = np.minimum(np.arange(w), np.arange(w)[::-1]) / FEATHER
ramp_y = np.minimum(np.arange(h), np.arange(h)[::-1]) / FEATHER
alpha = np.clip(np.minimum.outer(ramp_y, ramp_x), 0, 1)
mask = Image.fromarray((alpha * 255).astype(np.uint8), 'L')

def compose(img):
    canvas = Image.new('RGB', (side, side), BG)
    canvas.paste(img, (ox, oy), mask)
    return canvas

def amber(img):
    """The debug build's art: the blue neon shackle turned amber, everything else unchanged."""
    hsv = np.asarray(img.convert('HSV')).astype(float)
    rgb = np.asarray(img).astype(float)
    shifted = hsv.copy()
    shifted[..., 0] = 28 / 360 * 255          # amber hue
    shifted_rgb = np.asarray(Image.fromarray(shifted.astype(np.uint8), 'HSV').convert('RGB')).astype(float)
    s, v = hsv[..., 1] / 255, hsv[..., 2] / 255
    hue = hsv[..., 0] / 255 * 360
    blue = ((hue > 170) & (hue < 250)).astype(float)
    weight = blue * np.clip((v - 0.10) / 0.20, 0, 1) * np.clip((s - 0.25) / 0.25, 0, 1)
    out = rgb * (1 - weight[..., None]) + shifted_rgb * weight[..., None]
    return Image.fromarray(out.round().astype(np.uint8), 'RGB')

densities = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}
for variant, img, base in [('main', art, 'app/src/main/res'), ('debug', amber(art), 'app/src/debug/res')]:
    full = compose(img)
    for d, px in densities.items():
        out = os.path.join(repo, base, f'mipmap-{d}', 'ic_launcher_foreground.png')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        full.resize((px, px), Image.LANCZOS).save(out, optimize=True)
    if variant == 'main':
        # README / store preview: the visible part of the adaptive icon (72 of 108dp), rounded.
        crop = (side * 18 // 108, side * 18 // 108, side * 90 // 108, side * 90 // 108)
        preview = full.crop(crop).resize((512, 512), Image.LANCZOS)
        m = Image.new('L', (512 * 4, 512 * 4), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, 512 * 4 - 1, 512 * 4 - 1), radius=int(512 * 4 * 0.22), fill=255)
        rounded = preview.convert('RGBA')
        rounded.putalpha(m.resize((512, 512), Image.LANCZOS))
        os.makedirs(os.path.join(repo, 'docs/icon'), exist_ok=True)
        rounded.save(os.path.join(repo, 'docs/icon/mycar-icon.png'), optimize=True)
print('canvas', side, 'offset', ox, oy)
