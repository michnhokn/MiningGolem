#!/usr/bin/env python3
"""Recolors the vanilla copper golem spawn egg texture into the Mining Golem egg."""
import struct
import zlib
import os

SRC = "/var/folders/b1/bf9qft3926n5g7c_0r1913140000gn/T/opencode/mcclient/assets/minecraft/textures/item/copper_golem_spawn_egg.png"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "src", "main", "resources", "assets", "mininggolem", "textures", "item", "mining_golem_spawn_egg.png")


def read_png(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    width = height = None
    idat = b""
    palette = None
    trns = b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, bit_depth, color_type = struct.unpack(">IIBB", chunk[:10])
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"PLTE":
            palette = chunk
        elif tag == b"tRNS":
            trns = chunk
        pos += 12 + length
    raw = zlib.decompress(idat)
    if color_type == 3:
        # palette image: unfilter packed bytes, then unpack sub-byte indices row by row
        bits = bit_depth
        stride = (width * bits + 7) // 8
        packed = _unfilter(raw, stride, height, 1)
        mask = (1 << bits) - 1
        per_byte = 8 // bits
        pixels = bytearray()
        for row_start in range(0, len(packed), stride):
            count = 0
            for i in packed[row_start:row_start + stride]:
                for slot in range(per_byte):
                    if count < width:
                        shift = 8 - bits * (slot + 1)
                        idx = (i >> shift) & mask
                        r, g, b = palette[idx * 3], palette[idx * 3 + 1], palette[idx * 3 + 2]
                        a = trns[idx] if idx < len(trns) else 255
                        pixels += bytes((r, g, b, a))
                        count += 1
        return width, height, 4, bytes(pixels)
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color_type]
    return width, height, channels, _unfilter(raw, width, height, channels)


def _unfilter(raw, width, height, channels):
    stride = width * channels
    out = bytearray()
    prev = bytearray(stride)
    for y in range(height):
        filter_type = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        if filter_type == 1:  # sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filter_type == 2:  # up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filter_type == 3:  # average
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filter_type == 4:  # paeth
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out += line
        prev = line
    return out


def read_png_rgb(path):
    width, height, channels, raw = read_png(path)


def write_png(path, width, height, pixels):
    def chunk(tag, body):
        c = struct.pack(">I", len(body)) + tag + body
        return c + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

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
    print(f"wrote {path}")


def main():
    width, height, channels, raw = read_png(SRC)
    # diamond palette: dark teal -> cyan -> bright ice white, keyed on luminance
    stops = [
        (0.0, (24, 92, 92)),
        (0.4, (62, 158, 160)),
        (0.7, (98, 214, 212)),
        (1.0, (224, 255, 251)),
    ]

    def copper(lum):
        for (l0, c0), (l1, c1) in zip(stops, stops[1:]):
            if lum <= l1:
                t = (lum - l0) / (l1 - l0) if l1 > l0 else 0
                return tuple(round(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
        return stops[-1][1]

    pixels = []
    idx = 0
    for _ in range(height):
        row = []
        for _ in range(width):
            if channels == 4:
                r, g, b, a = raw[idx:idx + 4]
                idx += 4
            else:
                r = g = b = raw[idx]
                a = raw[idx + 1] if channels == 2 else 255
                idx += channels
            if a > 0:
                lum = (0.3 * r + 0.6 * g + 0.1 * b) / 255.0
                nr, ng, nb = copper(lum)
                row.append((nr, ng, nb, a))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    write_png(OUT, width, height, pixels)


if __name__ == "__main__":
    main()
