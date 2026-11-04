"""Build launcher resources from the approved artwork. Requires Pillow (pip install Pillow).
Retains the selected key pixels; removes the preview's background and white corners.
Adaptive foreground keeps all visible pixels within the central 66dp safe circle.
"""
from pathlib import Path
from PIL import Image
import math
ROOT = Path(__file__).resolve().parents[1]
im = Image.open(ROOT / 'artwork/key-icon-approved.png').convert('RGBA')
w, h = im.size
out = Image.new('RGBA', im.size)
for y in range(h):
    for x in range(w):
        r, g, b, _ = im.getpixel((x, y))
        if .13*w < x < .89*w and .13*h < y < .89*h:
            alpha = round(255 * max(0, min(1, (g - 140) / 30)))
            if alpha:
                out.putpixel((x, y), (r, g, b, alpha))
res = ROOT / 'app/src/main/res'
for density, scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
    folder = res / ('mipmap-' + density)
    folder.mkdir(exist_ok=True)
    size = round(108*scale)
    fg = Image.new('RGBA', (size,size))
    key_size = round(74*scale)
    fg.alpha_composite(out.resize((key_size,key_size), Image.Resampling.LANCZOS), ((size-key_size)//2, (size-key_size)//2))
    fg.save(folder / 'ic_key_attestation_foreground.png', optimize=True)
    Image.new('RGB',(size,size),'#146C60').save(folder / 'ic_key_attestation_background.png', optimize=True)
    # Legacy icon uses the same layout without Android's adaptive outer inset.
    legacy = Image.new('RGBA', (size,size), '#146C60')
    legacy.alpha_composite(fg)
    left = round(18*scale)
    legacy = legacy.crop((left,left,size-left,size-left)).resize((round(48*scale),round(48*scale)),Image.Resampling.LANCZOS)
    legacy.convert('RGB').save(folder / 'ic_key_attestation.png', optimize=True)
# White silhouette for Android 13+ themed icons; alpha matches the approved key.
fg = Image.open(res/'mipmap-xxxhdpi/ic_key_attestation_foreground.png').convert('RGBA')
mono = Image.new('RGBA',fg.size,'white'); mono.putalpha(fg.getchannel('A'))
(res/'drawable-nodpi').mkdir(exist_ok=True)
mono.save(res/'drawable-nodpi/ic_launcher_monochrome.png', optimize=True)
# Validate adaptive safe area, including antialiased edge pixels.
c = fg.width / 2
for y in range(fg.height):
    for x in range(fg.width):
        if fg.getpixel((x,y))[3] > 32:
            assert math.hypot(x-c,y-c) <= 33*4, (x,y)
print('Generated 5 densities, adaptive/legacy and monochrome resources; safe area checked.')
