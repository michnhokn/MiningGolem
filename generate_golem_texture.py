#!/usr/bin/env python3
"""Recolors the vanilla copper golem textures into diamond tones for the Mining Golem."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_spawn_egg import read_png, write_png  # reuse the PNG reader/writer

SRC_DIR = "/var/folders/b1/bf9qft3926n5g7c_0r1913140000gn/T/opencode/mcclient/assets/minecraft/textures/entity/copper_golem"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "src", "main", "resources", "assets", "mininggolem", "textures", "entity")

# diamond palette keyed on luminance: dark teal -> cyan -> bright ice white
STOPS = [
    (0.0, (24, 92, 92)),
    (0.4, (62, 158, 160)),
    (0.7, (98, 214, 212)),
    (1.0, (224, 255, 251)),
]


def diamond(lum):
    for (l0, c0), (l1, c1) in zip(STOPS, STOPS[1:]):
        if lum <= l1:
            t = (lum - l0) / (l1 - l0) if l1 > l0 else 0
            return tuple(round(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    return STOPS[-1][1]


def recolor(name, out_name):
    width, height, channels, raw = read_png(os.path.join(SRC_DIR, name))
    assert channels == 4, f"expected RGBA, got {channels} channels in {name}"
    pixels = []
    idx = 0
    for _ in range(height):
        row = []
        for _ in range(width):
            r, g, b, a = raw[idx:idx + 4]
            idx += 4
            if a > 0:
                lum = (0.3 * r + 0.6 * g + 0.1 * b) / 255.0
                nr, ng, nb = diamond(lum)
                row.append((nr, ng, nb, a))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(os.path.join(OUT_DIR, out_name), width, height, pixels)


if __name__ == "__main__":
    recolor("copper_golem.png", "mining_golem.png")
    recolor("copper_golem_eyes.png", "mining_golem_eyes.png")
