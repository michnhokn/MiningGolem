#!/usr/bin/env python3
"""Generates the Mining Golem textures (entity + GUI) as PNGs using only stdlib."""
import struct
import zlib
import random
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "mininggolem")
VANILLA_DIR = os.path.join(ROOT, "assets-src", "vanilla")


def write_png(path, width, height, pixels):
    # pixels: list of rows, each row a list of (r, g, b, a)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path} ({width}x{height})")


def noise(base, amount=8):
    r, g, b = base
    d = random.randint(-amount, amount)
    return (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)


def read_png(path):
    """Minimal stdlib PNG decoder for 8-bit images (all color types)."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    idat = b""
    palette, trns = [], b""
    meta = None
    pos = 8
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            meta = struct.unpack(">IIBBBBB", chunk)
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"PLTE":
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif tag == b"tRNS":
            trns = chunk
        pos += 12 + length
    w, h, depth, ctype, _comp, _filt, inter = meta
    assert inter == 0, f"interlaced PNG unsupported: {path}"
    assert depth == 8 or ctype == 3, f"unsupported PNG: {path}"
    raw = zlib.decompress(idat)
    if ctype == 3:
        bytes_per = 1                      # palette indices, possibly packed sub-byte
        stride = (w * depth + 7) // 8
    else:
        bytes_per = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
        stride = w * bytes_per
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for _ in range(h):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        for x in range(stride):
            a = line[x - bytes_per] if x >= bytes_per else 0
            b = prev[x]
            c = prev[x - bytes_per] if x >= bytes_per else 0
            if filt == 1:
                line[x] = (line[x] + a) & 255
            elif filt == 2:
                line[x] = (line[x] + b) & 255
            elif filt == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif filt == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + c - 2 * b)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out += line
        prev = line

    def palette_index(line, x):
        if depth == 8:
            return line[x]
        per_byte = 8 // depth
        byte = line[x // per_byte]
        shift = (per_byte - 1 - (x % per_byte)) * depth
        return (byte >> shift) & ((1 << depth) - 1)

    rows = []
    for y in range(h):
        line = out[y * stride:(y + 1) * stride]
        row = []
        for x in range(w):
            if ctype == 6:
                px = tuple(line[x * 4:x * 4 + 4])
            elif ctype == 2:
                px = tuple(line[x * 3:x * 3 + 3]) + (255,)
            elif ctype == 0:
                v = line[x]
                px = (v, v, v, 255)
            elif ctype == 4:
                v, al = line[x * 2], line[x * 2 + 1]
                px = (v, v, v, al)
            else:
                pi = palette_index(line, x)
                al = trns[pi] if pi < len(trns) else 255
                px = palette[pi] + (al,)
            row.append(px)
        rows.append(row)
    return rows


def gen_entity_texture():
    # 64x32 copper-golem texture; uniform copper tones with rust noise.
    random.seed(263)
    w, h = 64, 32
    px = [[noise((186, 108, 74)) for _ in range(w)] for _ in range(h)]
    # face plate on head region (eyes)
    for x in range(9, 13):
        for y in range(2, 6):
            px[y][x] = (40, 40, 46, 255)
    for x in range(20, 24):
        for y in range(2, 6):
            px[y][x] = (40, 40, 46, 255)
    # rust patches
    for _ in range(140):
        x, y = random.randrange(w), random.randrange(h)
        px[y][x] = noise((132, 74, 48), 6)
    # darker joint stripes on arms/legs
    for x in range(32, 64):
        for y in (0, 1, 12, 13, 14, 15, 27, 28):
            px[y][x] = noise((120, 66, 44), 5)
    write_png(os.path.join(OUT_DIR, "textures", "entity", "mining_golem.png"), w, h, px)


PANEL = (198, 198, 198, 255)
BORDER = (0, 0, 0, 255)
HIGHLIGHT = (255, 255, 255, 255)
SHADOW = (85, 85, 85, 255)
SLOT_BG = (139, 139, 139, 255)
SLOT_DARK = (55, 55, 55, 255)
PANEL_HEIGHT = 234


def gen_gui_texture():
    # 256x256 canvas; panel occupies 256x234.
    random.seed(7)
    w, h = 256, 256
    px = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]
    W, H = 256, PANEL_HEIGHT

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < w and 0 <= y < h:
                    px[y][x] = color

    # vanilla-style panel: 1px black outline, 2px white highlight (top/left),
    # 2px gray shadow (bottom/right), stepped rounded corners like the
    # vanilla container backgrounds (measured from generic_54.png)
    for y in range(H):
        for x in range(W):
            dx = min(x, W - 1 - x)
            dy = min(y, H - 1 - y)
            if dx + dy < 3:
                continue  # transparent corner cut
            if dx == 0 or dy == 0 or dx + dy == 3:
                px[y][x] = BORDER
            elif y in (1, 2) or x in (1, 2) or (x, y) == (3, 3):
                px[y][x] = HIGHLIGHT
            elif y in (H - 3, H - 2) or x in (W - 3, W - 2) or (x, y) == (W - 4, H - 4):
                px[y][x] = SHADOW
            else:
                px[y][x] = PANEL

    def slot(x, y):
        # 18x18 cell whose top-left is (slotX-1, slotY-1)
        rect(x, y, x + 18, y + 18, SLOT_DARK)
        rect(x + 1, y + 1, x + 17, y + 17, SLOT_BG)
        for i in range(2, 17):
            px[y + 17][x + i] = HIGHLIGHT
            px[y + i][x + 17] = HIGHLIGHT

    # slot item origins — golem inventory below the controls
    tool_slots = [(8, 67 + row * 18) for row in range(4)]
    torch_slots = [(230, 67 + row * 18) for row in range(4)]
    loot_slots = [(44 + col * 18, 67 + row * 18) for row in range(4) for col in range(9)]
    inv_slots = [(9 + col * 18, 153 + row * 18) for row in range(3) for col in range(9)]
    hotbar_slots = [(9 + col * 18, 211) for col in range(9)]

    for x, y in tool_slots + torch_slots + loot_slots + inv_slots + hotbar_slots:
        slot(x - 1, y - 1)

    # ghost icons in tool and torch columns: greyed-out vanilla item art
    # (real textures from the Minecraft client jar, checked into assets-src/vanilla)
    GHOST_ALPHA = 0.8

    def grey_ghost(pixels):
        # desaturate to luminance, stretch contrast around mid-grey so the
        # silhouette clearly separates from the slot background in both directions
        greyed = []
        for row in pixels:
            out_row = []
            for r, g, b, a in row:
                if a == 0:
                    out_row.append((0, 0, 0, 0))
                    continue
                l = 0.30 * r + 0.59 * g + 0.11 * b
                l = max(0, min(255, int((l - 150) * 1.6 + 150)))
                out_row.append((l, l, l, a))
            greyed.append(out_row)
        return greyed

    def stamp_ghost(slot_px, ox, oy):
        # pre-blend over the slot background so the icon renders identically everywhere
        for j, row in enumerate(slot_px):
            for i, (r, g, b, a) in enumerate(row):
                if a == 0 or i > 15 or j > 15:
                    continue
                dst = px[oy + j][ox + i]
                mixed = tuple(
                    max(0, min(255, int(GHOST_ALPHA * c + (1 - GHOST_ALPHA) * d)))
                    for c, d in zip((r, g, b), dst[:3])
                )
                px[oy + j][ox + i] = mixed + (255,)

    pickaxe = grey_ghost(read_png(os.path.join(VANILLA_DIR, "diamond_pickaxe.png")))
    shovel = grey_ghost(read_png(os.path.join(VANILLA_DIR, "diamond_shovel.png")))
    torch_tex = grey_ghost(read_png(os.path.join(VANILLA_DIR, "torch.png")))

    # slot 0 hints pickaxe, slot 1 hints shovel — both go in the tool column
    stamp_ghost(pickaxe, *tool_slots[0])
    stamp_ghost(shovel, *tool_slots[1])
    for x, y in torch_slots:
        stamp_ghost(torch_tex, x, y)

    write_png(os.path.join(OUT_DIR, "textures", "gui", "mining_golem.png"), w, h, px)


if __name__ == "__main__":
    import sys
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "entity"):
        gen_entity_texture()
    if what in ("all", "gui"):
        gen_gui_texture()
