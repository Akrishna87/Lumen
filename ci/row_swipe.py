"""Print "x1 y x2 y": a sideways swipe across one full row of photos in the grid, away from the
screen edges (where dragging auto-scrolls).

Usage: row_swipe.py <uiautomator dump>
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

nodes = list(ET.parse(sys.argv[1]).iter("node"))
screen = max(int(re.findall(r"\d+", n.get("bounds"))[3]) for n in nodes)
rows = defaultdict(list)
for n in nodes:
    desc = n.get("content-desc") or ""
    if desc.startswith(("Photo, ", "Video, ")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        if y2 - y1 > 50 and abs((x2 - x1) - (y2 - y1)) < 8:  # a whole, square cell
            rows[y1].append(((x1 + x2) // 2, (y1 + y2) // 2))
usable = [cells for top, cells in sorted(rows.items()) if len(cells) >= 3 and 0.2 * screen < cells[0][1] < 0.75 * screen]
if not usable:
    sys.exit("no full row of photos in the middle of the screen")
cells = sorted(usable[-1])
print(cells[0][0], cells[0][1], cells[-1][0], cells[-1][1])
