#!/usr/bin/env python3
"""Generates the Mining Golem textures (entity + GUI) as PNGs using only stdlib."""
import struct
import zlib
import random
import os

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src", "main", "resources", "assets", "mininggolem")


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


def gen_gui_texture():
    # 256x256 canvas; panel occupies 256x228.
    random.seed(7)
    w, h = 256, 256
    px = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < w and 0 <= y < h:
                    px[y][x] = color

    # panel with vanilla-style border
    rect(0, 0, 256, 228, PANEL)
    rect(0, 0, 256, 1, HIGHLIGHT)
    rect(0, 0, 1, 228, HIGHLIGHT)
    rect(0, 227, 256, 228, SHADOW)
    rect(255, 0, 256, 228, SHADOW)
    rect(0, 0, 256, 228, BORDER)
    rect(1, 1, 255, 227, PANEL)
    rect(1, 1, 255, 2, HIGHLIGHT)
    rect(1, 1, 2, 227, HIGHLIGHT)
    rect(1, 226, 255, 227, SHADOW)
    rect(254, 1, 255, 227, SHADOW)

    def slot(x, y):
        # 18x18 cell whose top-left is (slotX-1, slotY-1)
        rect(x, y, x + 18, y + 18, SLOT_DARK)
        rect(x + 1, y + 1, x + 17, y + 17, SLOT_BG)
        for i in range(2, 17):
            px[y + 17][x + i] = HIGHLIGHT
            px[y + i][x + 17] = HIGHLIGHT

    # slot item origins
    tool_slots = [(8, 20 + row * 18) for row in range(4)]
    torch_slots = [(230, 20 + row * 18) for row in range(4)]
    loot_slots = [(44 + col * 18, 20 + row * 18) for row in range(4) for col in range(9)]
    inv_slots = [(9 + col * 18, 146 + row * 18) for row in range(3) for col in range(9)]
    hotbar_slots = [(9 + col * 18, 204) for col in range(9)]

    for x, y in tool_slots + torch_slots + loot_slots + inv_slots + hotbar_slots:
        slot(x - 1, y - 1)

    # placeholder icons in tool and torch columns (semi-transparent, only visible when empty)
    ghost = 150

    def pickaxe_icon(ox, oy):
        head = (168, 173, 159, ghost)
        head_dark = (127, 132, 120, ghost)
        handle = (136, 102, 62, ghost)
        handle_dark = (96, 70, 42, ghost)
        # head: shallow arc across the top
        for x in range(3, 13):
            px[oy + 2][ox + x] = head
        for x in (2, 3, 12, 13):
            px[oy + 3][ox + x] = head
        for x in (1, 2, 13, 14):
            px[oy + 4][ox + x] = head_dark
        px[oy + 3][ox + 1] = head_dark
        px[oy + 3][ox + 14] = head_dark
        px[oy + 5][ox + 1] = head_dark
        px[oy + 5][ox + 14] = head_dark
        px[oy + 4][ox + 2] = head
        px[oy + 4][ox + 13] = head
        # handle: 2px diagonal band from bottom-left towards the head
        for i in range(8):
            px[oy + 12 - i][ox + 4 + i] = handle
            px[oy + 13 - i][ox + 4 + i] = handle_dark

    def torch_icon(ox, oy):
        stick = (136, 102, 62, ghost)
        stick_dark = (96, 70, 42, ghost)
        flame = (255, 216, 0, ghost)
        flame_dark = (255, 144, 0, ghost)
        for y in range(6, 15):
            px[oy + y][ox + 7] = stick
            px[oy + y][ox + 8] = stick_dark
        for x in range(6, 10):
            px[oy + 5][ox + x] = flame_dark
        for x in range(7, 9):
            px[oy + 3][ox + x] = flame
            px[oy + 4][ox + x] = flame
        px[oy + 2][ox + 7] = flame
        px[oy + 2][ox + 8] = flame_dark

    for x, y in tool_slots:
        pickaxe_icon(x, y)
    for x, y in torch_slots:
        torch_icon(x, y)

    write_png(os.path.join(OUT_DIR, "textures", "gui", "mining_golem.png"), w, h, px)


if __name__ == "__main__":
    gen_entity_texture()
    gen_gui_texture()
