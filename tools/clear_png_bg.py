from collections import deque
from pathlib import Path
from PIL import Image

src = Path(r"app/src/main/res/drawable/ic_pickup_miel.png")
im = Image.open(src).convert("RGBA")
w, h = im.size
px = im.load()

def near_white(c):
    r, g, b, a = c
    return a > 0 and r >= 245 and g >= 245 and b >= 245

seen = [[False] * w for _ in range(h)]
q = deque()
for x in range(w):
    for y in (0, h - 1):
        if near_white(px[x, y]):
            q.append((x, y))
            seen[y][x] = True
for y in range(h):
    for x in (0, w - 1):
        if not seen[y][x] and near_white(px[x, y]):
            q.append((x, y))
            seen[y][x] = True

cleared = 0
while q:
    x, y = q.popleft()
    px[x, y] = (0, 0, 0, 0)
    cleared += 1
    for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
        if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and near_white(px[nx, ny]):
            seen[ny][nx] = True
            q.append((nx, ny))

# soften leftover near-white fringe
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if a > 0 and r >= 250 and g >= 250 and b >= 250:
            px[x, y] = (0, 0, 0, 0)
            cleared += 1

im.save(src, "PNG")
print("cleared", cleared, "size", w, h, "mode", im.mode)
