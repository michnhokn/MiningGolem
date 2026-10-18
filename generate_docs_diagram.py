#!/usr/bin/env python3
"""Generates a top-down diagram of the serpentine mining pattern for the README."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_spawn_egg import write_png

SCALE = 4          # pixels per block
TUNNELS = 4        # rows to draw
TUNNEL_LEN = 40    # blocks per tunnel (truncated for the picture)
CONN = 3           # connector blocks
ROWS_SHOWN = TUNNELS

ROCK = (58, 58, 62, 255)
ROCK_DARK = (44, 44, 48, 255)
DUG = (148, 148, 152, 255)
DUG_EDGE = (110, 110, 114, 255)
TORCH = (255, 216, 0, 255)
GOLEM = (232, 120, 60, 255)
ARROW = (240, 240, 240, 255)
LEGEND_TEXT = (230, 230, 230, 255)

# 3x5 pixel font for the legend
FONT = {
    'A': ["###", "# #", "###", "# #", "# #"],
    'C': ["###", "#  ", "#  ", "#  ", "###"],
    'D': ["## ", "# #", "# #", "# #", "## "],
    'E': ["###", "#  ", "###", "#  ", "###"],
    'G': ["###", "#  ", "# #", "# #", "###"],
    'H': ["# #", "# #", "###", "# #", "# #"],
    'I': ["###", " # ", " # ", " # ", "###"],
    'L': ["#  ", "#  ", "#  ", "#  ", "###"],
    'M': ["# #", "###", "###", "# #", "# #"],
    'N': ["# #", "###", "###", "###", "# #"],
    'O': ["###", "# #", "# #", "# #", "###"],
    'P': ["###", "# #", "###", "#  ", "#  "],
    'R': ["###", "# #", "###", "## ", "# #"],
    'S': ["###", "#  ", "###", "  #", "###"],
    'T': ["###", " # ", " # ", " # ", " # "],
    'U': ["# #", "# #", "# #", "# #", "###"],
    ' ': ["   ", "   ", "   ", "   ", "   "],
}


def draw_text(px, text, ox, oy, scale, color):
    cursor = ox
    for ch in text.upper():
        glyph = FONT.get(ch, FONT[' '])
        for gy, row in enumerate(glyph):
            for gx, cell in enumerate(row):
                if cell == '#':
                    for dy in range(scale):
                        for dx in range(scale):
                            yy = oy + gy * scale + dy
                            xx = cursor + gx * scale + dx
                            if 0 <= xx < len(px[0]) and 0 <= yy < len(px):
                                px[yy][xx] = color
        cursor += (3 * scale) + scale


def main():
    # world coords: x grows right, z grows down; advance direction = north (up)
    blocks = {}  # (x, z) = dug
    x, z = 0, 0
    forward = (1, 0)      # east
    advance = (0, -1)     # north
    back = (-forward[0], -forward[1])
    torches = []
    col_counter = 0
    next_unit_start = None
    for tunnel in range(ROWS_SHOWN):
        if next_unit_start is not None:
            # leading connector from the previous return row
            for i in range(CONN):
                x += advance[0]; z += advance[1]
                blocks[(x, z)] = True
        # row A
        for i in range(TUNNEL_LEN):
            x += forward[0]; z += forward[1]
            blocks[(x, z)] = True
            col_counter += 1
            if col_counter % 10 == 0:
                torches.append((x - forward[0] * 2, z))
        # connector to row B
        for i in range(CONN):
            x += advance[0]; z += advance[1]
            blocks[(x, z)] = True
        # row B (return)
        for i in range(TUNNEL_LEN):
            x += back[0]; z += back[1]
            blocks[(x, z)] = True
            col_counter += 1
            if col_counter % 10 == 0:
                torches.append((x - back[0] * 2, z))
        next_unit_start = (x, z)
        # closing rung (branch): reconnect the return row to the previous row
        for i in range(1, 3):
            blocks[(x + advance[0] * i, z + advance[1] * i)] = True

    xs = [x for x, _ in blocks] + [0]
    zs = [z for _, z in blocks] + [0]
    margin = 4
    legend_height = 10 * SCALE
    min_x, max_x = min(xs) - margin, max(xs) + margin + 2
    min_z, max_z = min(zs) - margin, max(zs) + margin
    w = (max_x - min_x + 1) * SCALE
    h = (max_z - min_z + 1) * SCALE + legend_height
    px = [[ROCK_DARK for _ in range(w)] for _ in range(h)]

    def blit_block(bx, bz, color):
        x0 = (bx - min_x) * SCALE
        z0 = (bz - min_z) * SCALE
        for y in range(z0, z0 + SCALE):
            for xx in range(x0, x0 + SCALE):
                if 0 <= xx < w and 0 <= y < h:
                    px[y][xx] = color

    # rock texture
    import random
    random.seed(5)
    for yy in range(h):
        for xx in range(w):
            if random.random() < 0.12:
                px[yy][xx] = ROCK

    for (bx, bz) in blocks:
        blit_block(bx, bz, DUG)
    # subtle floor shading at tunnel edges
    for (bx, bz) in blocks:
        if (bx, bz + 1) not in blocks:
            x0 = (bx - min_x) * SCALE
            z0 = (bz - min_z + 1) * SCALE - 1
            for xx in range(x0, x0 + SCALE):
                px[z0][xx] = DUG_EDGE

    # torches
    for (tx, tz) in torches:
        blit_block(tx, tz, TORCH)

    # golem start marker
    blit_block(0, 0, GOLEM)

    # direction arrows: triangle at the outer end of each row, pointing along the row
    def arrow(bx, bz, direction):
        for step in range(3):
            length = 3 - step
            cx = bx + direction[0] * step
            cz = bz + direction[1] * step
            for i in range(length):
                if direction[1] != 0:  # vertical arrow
                    blit_block(cx - i, cz, ARROW)
                else:  # horizontal arrow
                    blit_block(cx, cz - i, ARROW)

    # compute row directions & ends from blocks
    rows = {}
    for (bx, bz) in blocks:
        rows.setdefault(bz, []).append(bx)
    row_index = 0
    for bz, xs_in_row in sorted(rows.items()):
        xs_in_row.sort()
        if len(xs_in_row) < TUNNEL_LEN // 2:
            continue
        if row_index % 2 == 0:
            arrow(xs_in_row[-1] + 1, bz, (1, 0))
        else:
            arrow(xs_in_row[0] - 1, bz, (-1, 0))
        row_index += 1

    # legend (two rows)
    ly = h - legend_height + 2 * SCALE
    lx = 2 * SCALE

    def legend_swatch(color):
        nonlocal lx
        for yy in range(ly, ly + 3 * SCALE):
            for xx in range(lx, lx + 3 * SCALE):
                px[yy][xx] = color
        lx += 4 * SCALE

    def legend_text(text):
        nonlocal lx
        draw_text(px, text, lx, ly, SCALE - 1, LEGEND_TEXT)
        lx += len(text) * 4 * (SCALE - 1) + 3 * SCALE

    legend_swatch(GOLEM); legend_text("GOLEM")
    legend_swatch(TORCH); legend_text("TORCH")
    ly += 4 * SCALE
    lx = 2 * SCALE
    legend_swatch(ARROW); legend_text("DIG")
    legend_swatch(DUG);   legend_text("TUNNEL")

    write_png(os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs", "mining_pattern.png"), w, h, px)


if __name__ == "__main__":
    main()
