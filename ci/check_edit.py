"""Checks an edited copy against its original, from `content query` listings taken before and after,
and from the copy's pixels: it must be the original turned a quarter (width and height swapped),
dated like the original, and black and white (the Mono filter).

Usage: check_edit.py <listing> <original name> <copy name> <copy file>
"""
import re
import sys

from PIL import Image, ImageChops, ImageStat

listing, original, copy, copy_file = sys.argv[1:5]


def row(name):
    for line in open(listing):
        if f"_display_name={name}," in line:
            return dict(re.findall(r"(\w+)=([^,]*)", line))
    sys.exit(f"{name} isn't in the media library")


o, c = row(original), row(copy)
ow, oh, cw, ch = int(o["width"]), int(o["height"]), int(c["width"]), int(c["height"])
if (cw, ch) != (oh, ow):
    sys.exit(f"the copy is {cw}x{ch}; turning the {ow}x{oh} original a quarter should give {oh}x{ow}")

taken = int(o["datetaken"]) if o["datetaken"] != "NULL" else int(o["date_modified"]) * 1000
if c["datetaken"] == "NULL" or abs(int(c["datetaken"]) - taken) > 2000:
    sys.exit(f"the copy is dated {c['datetaken']}, the original {taken}")

image = Image.open(copy_file).convert("RGB")
if image.size != (cw, ch):
    sys.exit(f"the copy's pixels are {image.size}, the library says {cw}x{ch}")
r, g, b = image.split()
# Mono leaves every pixel grey, so the channels match pixel by pixel (allowing for JPEG noise).
spread = max(ImageStat.Stat(ImageChops.difference(x, y)).mean[0] for x, y in ((r, g), (g, b), (r, b)))
if spread > 3:
    sys.exit(f"the copy still has colour (channels differ by {spread:.1f} on average); Mono should leave it grey")
print(f"copy {cw}x{ch} from {ow}x{oh}, dated like the original, grey (channels within {spread:.1f})")
