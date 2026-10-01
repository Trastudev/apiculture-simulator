"""Quita el fondo blanco de botes y badges de flora (inundación desde el borde)."""
from collections import deque
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable"


def near_white(c, thresh=248):
    r, g, b, a = c
    return a > 0 and r >= thresh and g >= thresh and b >= thresh


def punch(path: Path) -> int:
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    px = im.load()
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
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0 and r >= 252 and g >= 252 and b >= 252:
                px[x, y] = (0, 0, 0, 0)
                cleared += 1
    im.save(path, "PNG")
    return cleared


def main():
    names = []
    for p in sorted(ROOT.glob("ic_*.png")):
        n = p.name
        if n.startswith("ic_flora_badge_") or n.startswith("ic_"):
            if n.startswith("ic_flag") or n.startswith("ic_lock") or n.startswith("ic_map"):
                continue
            if n.startswith("ic_apiario") or n.startswith("ic_pickup") or n.startswith("ic_almacen"):
                continue
            names.append(p)
    for p in names:
        c = punch(p)
        print(f"{p.name}: cleared {c}")


if __name__ == "__main__":
    main()
