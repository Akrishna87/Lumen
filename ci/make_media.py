"""Makes the smoke test's photos: camera shots with EXIF camera details, screenshots and a panorama.

Usage: make_media.py <output dir>
"""
import colorsys
import datetime
import os
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
for sub in ("camera", "screenshots", "panoramas"):
    os.makedirs(os.path.join(out, sub), exist_ok=True)


def picture(size, hue):
    """A sky-to-ground gradient with a sun and hills, so each photo looks different."""
    w, h = size
    img = Image.new("RGB", size)
    draw = ImageDraw.Draw(img)
    for y in range(h):
        r, g, b = colorsys.hsv_to_rgb((hue + y / h * 0.15) % 1, 0.55, 1 - y / h * 0.45)
        draw.line([(0, y), (w, y)], fill=(int(r * 255), int(g * 255), int(b * 255)))
    draw.ellipse([w * 0.65, h * 0.15, w * 0.65 + h * 0.2, h * 0.35], fill=(255, 236, 170))
    draw.polygon([(0, h), (w * 0.35, h * 0.5), (w * 0.7, h), ], fill=(40, 70, 60))
    draw.polygon([(w * 0.4, h), (w * 0.75, h * 0.6), (w, h * 0.8), (w, h)], fill=(30, 55, 50))
    return img


def exif():
    e = Image.Exif()
    e[0x010F] = "Google"       # Make
    e[0x0110] = "Pixel Smoke"  # Model
    sub = e.get_ifd(0x8769)    # Exif IFD
    sub[0x829D] = 1.8          # FNumber
    sub[0x8827] = 64           # ISO
    sub[0x829A] = 1 / 120      # ExposureTime
    sub[0x920A] = 6.9          # FocalLength
    return e


def a_year_ago():
    """Today's date and time a year back, for the "On This Day" memory."""
    now = datetime.datetime.now()
    try:
        return now.replace(year=now.year - 1)
    except ValueError:  # 29 February
        return now.replace(year=now.year - 1, day=28)


def dms(value):
    """Degrees as EXIF's (degrees, minutes, seconds)."""
    value = abs(value)
    d = int(value)
    m = int((value - d) * 60)
    return (float(d), float(m), round((value - d - m / 60) * 3600, 2))


# Places: photos 1-3 were taken in Chennai, 4-5 in Paris (the delete, edit and hide steps use 7 and 8).
PLACES = {0: (13.0827, 80.2707), 1: (13.0850, 80.2101), 2: (13.0500, 80.2824), 3: (48.8566, 2.3522), 4: (48.8606, 2.3376)}

for i in range(8):
    e = exif()
    if i == 0:  # taken on this day last year
        taken = a_year_ago().astimezone()
        e.get_ifd(0x8769)[0x9003] = taken.strftime("%Y:%m:%d %H:%M:%S")  # DateTimeOriginal
        # OffsetTimeOriginal: without a time zone, Android 11+ ignores a date that doesn't match the
        # file's own time, as cameras always write one.
        e.get_ifd(0x8769)[0x9011] = taken.strftime("%z")[:3] + ":" + taken.strftime("%z")[3:]
    if i in PLACES:
        lat, lon = PLACES[i]
        gps = e.get_ifd(0x8825)
        gps[1], gps[2] = ("N" if lat >= 0 else "S"), dms(lat)
        gps[3], gps[4] = ("E" if lon >= 0 else "W"), dms(lon)
    picture((1600, 1200) if i % 3 else (1200, 1600), i / 8).save(
        os.path.join(out, "camera", f"PXL_smoke_{i + 1:02d}.jpg"), quality=88, exif=e)
for i in range(2):
    picture((1080, 2400), 0.55 + i / 10).save(os.path.join(out, "screenshots", f"Screenshot_smoke_{i + 1}.png"))
picture((4200, 1200), 0.08).save(os.path.join(out, "panoramas", "PANO_smoke.jpg"), quality=85)

# The screenshots and panorama are a little older, so the newest photo (the one the test opens) is
# a camera shot with EXIF details. adb push keeps these times.
for sub in ("screenshots", "panoramas"):
    for name in os.listdir(os.path.join(out, sub)):
        path = os.path.join(out, sub, name)
        t = os.path.getmtime(path) - 600
        os.utime(path, (t, t))
print("made test photos in", out)
