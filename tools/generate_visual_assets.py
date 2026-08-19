#!/usr/bin/env python3
"""Generate the deterministic FF&FF pixel art and equipment block models.

The generator intentionally uses only Python's standard library.  Every output
is nearest-neighbour Minecraft pixel art: no antialiasing, no external source
images and no third-party logos.
"""

from __future__ import annotations

import binascii
import json
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/peterwolfs_forestfire"
ITEM_TEXTURES = ASSETS / "textures/item"
BLOCK_TEXTURES = ASSETS / "textures/block"
MATERIAL_TEXTURES = BLOCK_TEXTURES / "material"
EQUIPMENT_TEXTURES = ASSETS / "textures/entity/equipment"
BLOCK_MODELS = ASSETS / "models/block"
CLIENT_ITEMS = ASSETS / "items"

TRANSPARENT = (0, 0, 0, 0)
INK = (35, 29, 27, 255)
INK_SOFT = (55, 47, 43, 255)
RED_DARK = (122, 31, 31, 255)
RED = (190, 45, 39, 255)
RED_LIGHT = (225, 67, 45, 255)
ORANGE_DARK = (172, 72, 25, 255)
ORANGE = (239, 112, 31, 255)
YELLOW_DARK = (190, 135, 30, 255)
YELLOW = (247, 196, 49, 255)
REFLECTIVE = (224, 236, 99, 255)
BLUE_DARK = (31, 76, 112, 255)
BLUE = (47, 132, 183, 255)
BLUE_LIGHT = (109, 194, 224, 255)
STEEL_DARK = (65, 76, 84, 255)
STEEL = (117, 134, 143, 255)
STEEL_LIGHT = (190, 204, 207, 255)
BRASS_DARK = (126, 83, 28, 255)
BRASS = (205, 145, 45, 255)
BRASS_LIGHT = (239, 197, 92, 255)
RUBBER = (28, 31, 32, 255)
BROWN_DARK = (76, 48, 34, 255)
BROWN = (121, 77, 43, 255)
WOOD = (153, 97, 50, 255)
WHITE = (238, 235, 219, 255)
SCREEN_DARK = (16, 63, 65, 255)
SCREEN = (72, 202, 171, 255)
FOREST = (38, 87, 62, 255)


class Canvas:
    def __init__(self, width: int, height: int, fill=TRANSPARENT):
        self.width = width
        self.height = height
        self.pixels = [fill] * (width * height)

    def set(self, x: int, y: int, color) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y * self.width + x] = color

    def get(self, x: int, y: int):
        if 0 <= x < self.width and 0 <= y < self.height:
            return self.pixels[y * self.width + x]
        return TRANSPARENT

    def rect(self, x0: int, y0: int, x1: int, y1: int, color) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def line(self, x0: int, y0: int, x1: int, y1: int, color, width: int = 1) -> None:
        dx = abs(x1 - x0)
        sx = 1 if x0 < x1 else -1
        dy = -abs(y1 - y0)
        sy = 1 if y0 < y1 else -1
        error = dx + dy
        radius = max(0, width - 1) // 2
        while True:
            self.rect(x0 - radius, y0 - radius, x0 + radius, y0 + radius, color)
            if x0 == x1 and y0 == y1:
                break
            twice = 2 * error
            if twice >= dy:
                error += dy
                x0 += sx
            if twice <= dx:
                error += dx
                y0 += sy

    def polygon(self, points: list[tuple[int, int]], color) -> None:
        min_x = min(p[0] for p in points)
        max_x = max(p[0] for p in points)
        min_y = min(p[1] for p in points)
        max_y = max(p[1] for p in points)
        for y in range(min_y, max_y + 1):
            for x in range(min_x, max_x + 1):
                if _point_in_polygon(x + 0.5, y + 0.5, points):
                    self.set(x, y, color)

    def ellipse(self, x0: int, y0: int, x1: int, y1: int, color) -> None:
        cx = (x0 + x1) / 2.0
        cy = (y0 + y1) / 2.0
        rx = max(0.5, (x1 - x0 + 1) / 2.0)
        ry = max(0.5, (y1 - y0 + 1) / 2.0)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                    self.set(x, y, color)

    def replace_opaque(self, old_color, new_color) -> None:
        self.pixels = [new_color if pixel == old_color else pixel for pixel in self.pixels]

    def scaled(self, factor: int) -> "Canvas":
        result = Canvas(self.width * factor, self.height * factor)
        for y in range(result.height):
            for x in range(result.width):
                result.set(x, y, self.get(x // factor, y // factor))
        return result


def _point_in_polygon(x: float, y: float, points: list[tuple[int, int]]) -> bool:
    inside = False
    previous = len(points) - 1
    for current, (xi, yi) in enumerate(points):
        xj, yj = points[previous]
        if (yi > y) != (yj > y):
            boundary = (xj - xi) * (y - yi) / (yj - yi) + xi
            if x < boundary:
                inside = not inside
        previous = current
    return inside


def _chunk(name: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + name
        + payload
        + struct.pack(">I", binascii.crc32(name + payload) & 0xFFFFFFFF)
    )


def write_png(path: Path, canvas: Canvas) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = bytearray()
    for y in range(canvas.height):
        raw.append(0)
        for x in range(canvas.width):
            raw.extend(canvas.get(x, y))
    data = b"\x89PNG\r\n\x1a\n"
    data += _chunk(b"IHDR", struct.pack(">IIBBBBB", canvas.width, canvas.height, 8, 6, 0, 0, 0))
    data += _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    data += _chunk(b"IEND", b"")
    path.write_bytes(data)


def new_icon() -> Canvas:
    return Canvas(16, 16)


def draw_handle(canvas: Canvas, start=(3, 13), end=(11, 3)) -> None:
    canvas.line(*start, *end, INK, 3)
    canvas.line(*start, *end, BROWN, 1)
    canvas.set(start[0], start[1], WOOD)
    canvas.set(end[0], end[1], WOOD)


def fire_axe() -> Canvas:
    c = new_icon()
    draw_handle(c)
    c.polygon([(8, 2), (13, 1), (15, 3), (14, 6), (10, 6), (8, 4)], INK)
    c.polygon([(9, 3), (13, 2), (14, 3), (13, 5), (10, 5)], RED)
    c.rect(9, 3, 10, 4, STEEL_LIGHT)
    c.set(13, 2, RED_LIGHT)
    return c


def pulaski() -> Canvas:
    c = new_icon()
    draw_handle(c, (4, 14), (10, 4))
    c.polygon([(6, 2), (10, 2), (12, 4), (10, 6), (7, 5)], INK)
    c.polygon([(7, 3), (10, 3), (11, 4), (9, 5), (7, 4)], ORANGE)
    c.polygon([(10, 3), (14, 2), (15, 3), (11, 5)], INK)
    c.polygon([(11, 3), (14, 3), (11, 4)], STEEL)
    return c


def fire_shovel() -> Canvas:
    c = new_icon()
    draw_handle(c, (5, 11), (11, 3))
    c.polygon([(3, 9), (7, 10), (8, 13), (6, 15), (2, 14), (1, 12)], INK)
    c.polygon([(3, 10), (6, 11), (7, 13), (5, 14), (2, 13), (2, 12)], STEEL)
    c.line(10, 3, 12, 1, INK, 3)
    c.line(10, 3, 12, 1, BROWN, 1)
    return c


def fire_rake() -> Canvas:
    c = new_icon()
    draw_handle(c, (4, 14), (10, 5))
    c.line(7, 3, 14, 7, INK, 3)
    c.line(7, 3, 14, 7, STEEL, 1)
    for x, y in [(8, 4), (10, 5), (12, 6), (14, 7)]:
        c.line(x, y, x + 1, y - 2, INK, 2)
        c.set(x + 1, y - 2, STEEL_LIGHT)
    return c


def fire_hose_nozzle() -> Canvas:
    c = new_icon()
    c.line(3, 13, 11, 5, INK, 5)
    c.line(3, 13, 11, 5, BRASS, 3)
    c.line(7, 9, 10, 12, INK, 3)
    c.line(7, 9, 9, 11, RED_LIGHT, 1)
    c.polygon([(9, 3), (13, 1), (15, 3), (12, 6)], INK)
    c.polygon([(10, 3), (13, 2), (14, 3), (12, 5)], STEEL_LIGHT)
    c.set(14, 2, BLUE_LIGHT)
    c.set(15, 1, BLUE)
    return c


def intake_strainer() -> Canvas:
    c = new_icon()
    c.polygon([(3, 5), (10, 2), (14, 6), (13, 12), (6, 15), (2, 11)], INK)
    c.polygon([(4, 5), (10, 3), (13, 6), (12, 11), (6, 14), (3, 11)], STEEL)
    for x in range(5, 12, 2):
        c.line(x, 5, x - 1, 12, STEEL_DARK)
    for y in range(6, 12, 2):
        c.line(4, y, 12, y - 1, STEEL_LIGHT)
    c.rect(9, 1, 12, 3, INK)
    c.rect(10, 1, 12, 2, BLUE)
    return c


def pump_fuel_can() -> Canvas:
    c = new_icon()
    c.polygon([(3, 3), (11, 3), (14, 6), (14, 14), (3, 14), (2, 12), (2, 5)], INK)
    c.polygon([(4, 4), (10, 4), (13, 7), (13, 13), (4, 13), (3, 12), (3, 5)], RED)
    c.rect(6, 2, 10, 4, INK)
    c.rect(7, 3, 9, 4, RED_LIGHT)
    c.rect(11, 2, 13, 4, INK)
    c.set(12, 2, YELLOW)
    c.line(5, 6, 11, 12, RED_DARK, 2)
    c.line(11, 6, 5, 12, RED_DARK, 2)
    c.set(4, 5, RED_LIGHT)
    return c


def thermal_scanner() -> Canvas:
    c = new_icon()
    c.polygon([(4, 1), (12, 2), (14, 5), (12, 11), (9, 11), (8, 15), (4, 14), (5, 10), (2, 8)], INK)
    c.polygon([(5, 2), (11, 3), (13, 5), (11, 10), (5, 9), (3, 8)], ORANGE)
    c.rect(5, 3, 11, 7, SCREEN_DARK)
    c.rect(6, 4, 10, 6, SCREEN)
    c.set(8, 5, YELLOW)
    c.polygon([(6, 10), (9, 11), (8, 14), (5, 13)], STEEL_DARK)
    return c


def firefighter_info() -> Canvas:
    c = new_icon()
    c.polygon([(2, 2), (13, 2), (15, 4), (15, 14), (4, 14), (2, 12)], INK)
    c.polygon([(3, 3), (12, 3), (14, 5), (14, 13), (4, 13), (3, 12)], YELLOW)
    c.rect(4, 4, 6, 12, RED)
    c.polygon([(9, 5), (11, 7), (10, 10), (8, 11), (7, 9)], RED_LIGHT)
    c.polygon([(10, 7), (12, 9), (10, 12), (8, 11)], ORANGE)
    c.set(10, 9, YELLOW)
    c.rect(7, 4, 12, 4, WHITE)
    return c


def firefighter_helmet() -> Canvas:
    c = new_icon()
    c.polygon([(3, 9), (4, 5), (7, 2), (12, 3), (14, 6), (14, 10)], INK)
    c.polygon([(4, 9), (5, 5), (8, 3), (11, 4), (13, 6), (13, 10)], YELLOW)
    c.rect(2, 9, 15, 12, INK)
    c.rect(3, 9, 14, 10, YELLOW_DARK)
    c.rect(5, 5, 12, 7, YELLOW)
    c.rect(7, 6, 12, 8, STEEL_DARK)
    c.rect(8, 6, 11, 7, STEEL_LIGHT)
    c.rect(7, 2, 9, 4, RED)
    c.set(8, 2, WHITE)
    return c


def firefighter_jacket() -> Canvas:
    c = new_icon()
    c.polygon([(4, 2), (7, 1), (9, 1), (12, 2), (15, 6), (13, 9), (12, 7), (12, 15), (4, 15), (4, 7), (3, 9), (1, 6)], INK)
    c.polygon([(5, 3), (7, 2), (9, 2), (11, 3), (13, 6), (12, 7), (11, 5), (11, 14), (5, 14), (5, 5), (3, 7), (2, 6)], BROWN_DARK)
    c.rect(5, 4, 11, 5, ORANGE_DARK)
    c.rect(4, 9, 12, 10, REFLECTIVE)
    c.line(8, 3, 8, 14, STEEL_DARK)
    c.rect(2, 7, 4, 8, REFLECTIVE)
    c.rect(12, 7, 14, 8, REFLECTIVE)
    c.set(9, 6, RED_LIGHT)
    return c


def firefighter_trousers() -> Canvas:
    c = new_icon()
    c.polygon([(4, 1), (12, 1), (13, 6), (12, 15), (8, 15), (8, 9), (7, 9), (7, 15), (3, 15), (3, 6)], INK)
    c.polygon([(5, 2), (11, 2), (12, 6), (11, 14), (9, 14), (9, 8), (6, 8), (6, 14), (4, 14), (4, 6)], BROWN_DARK)
    c.rect(4, 3, 12, 4, ORANGE_DARK)
    c.rect(4, 10, 6, 11, REFLECTIVE)
    c.rect(9, 10, 11, 11, REFLECTIVE)
    c.rect(7, 2, 8, 3, STEEL)
    return c


def firefighter_boots() -> Canvas:
    c = new_icon()
    c.polygon([(3, 2), (8, 2), (9, 10), (14, 11), (15, 14), (13, 15), (3, 15), (2, 13)], INK)
    c.polygon([(4, 3), (7, 3), (8, 11), (13, 12), (14, 14), (4, 14), (3, 13)], BROWN_DARK)
    c.rect(4, 4, 7, 6, BROWN)
    c.rect(3, 9, 8, 10, REFLECTIVE)
    c.rect(7, 13, 13, 14, RUBBER)
    return c


def breathing_mask() -> Canvas:
    c = new_icon()
    c.ellipse(2, 2, 13, 12, INK)
    c.polygon([(4, 3), (11, 3), (13, 6), (11, 9), (4, 9), (2, 6)], STEEL_DARK)
    c.rect(4, 4, 11, 7, INK)
    c.rect(5, 4, 10, 6, (223, 177, 70, 255))
    c.rect(6, 5, 9, 6, (245, 217, 128, 255))
    c.polygon([(5, 8), (10, 8), (12, 11), (9, 14), (6, 14), (3, 11)], INK)
    c.polygon([(6, 9), (9, 9), (10, 11), (8, 13), (6, 12), (5, 10)], STEEL)
    c.set(7, 10, RUBBER)
    c.set(8, 10, RUBBER)
    return c


def air_tank() -> Canvas:
    c = new_icon()
    c.polygon([(5, 2), (10, 2), (12, 4), (12, 13), (10, 15), (5, 15), (3, 13), (3, 4)], INK)
    c.polygon([(6, 3), (9, 3), (11, 5), (11, 12), (9, 14), (6, 14), (4, 12), (4, 5)], STEEL)
    c.rect(5, 4, 6, 12, STEEL_LIGHT)
    c.rect(4, 7, 11, 9, BLUE_DARK)
    c.rect(6, 1, 9, 3, INK)
    c.rect(7, 1, 8, 2, BRASS)
    c.rect(3, 5, 4, 12, RUBBER)
    c.rect(11, 5, 12, 12, RUBBER)
    return c


def backpack_sprayer() -> Canvas:
    c = new_icon()
    c.polygon([(4, 2), (11, 2), (13, 4), (13, 13), (11, 15), (4, 15), (2, 13), (2, 4)], INK)
    c.polygon([(5, 3), (10, 3), (12, 5), (12, 12), (10, 14), (5, 14), (3, 12), (3, 5)], BLUE)
    c.rect(4, 4, 5, 12, BLUE_LIGHT)
    c.rect(3, 7, 12, 9, RED_DARK)
    c.rect(5, 1, 10, 3, INK)
    c.rect(6, 2, 9, 3, STEEL)
    c.line(12, 5, 15, 2, RUBBER, 2)
    c.set(15, 1, BRASS_LIGHT)
    return c



def hose_roll_small() -> Canvas:
    return hose_roll_icon((47, 132, 183, 255), 2)

def hose_roll_standard() -> Canvas:
    return hose_roll_icon((247, 196, 49, 255), 3)

def hose_roll_large() -> Canvas:
    return hose_roll_icon((225, 67, 45, 255), 4)

def hose_roll_icon(badge, coils: int) -> Canvas:
    c = Canvas(16, 16)
    c.rect(3, 12, 13, 14, (0, 0, 0, 60))
    for r_out, r_in, col in ((6, 3, RED), (6, 5, RED_DARK), (5, 4, RED_LIGHT), (3, 1, RUBBER), (2, 0, STEEL)):
        for y in range(16):
            for x in range(16):
                d2 = (x - 8) ** 2 + (y - 8) ** 2
                if r_in * r_in <= d2 <= r_out * r_out:
                    c.set(x, y, col)
    c.set(8, 8, BRASS)
    c.rect(12, 6, 14, 9, BRASS)
    c.rect(13, 7, 15, 8, BRASS_LIGHT)
    c.rect(1, 1, 5, 5, badge)
    c.rect(2, 2, 4, 4, WHITE)
    return c

def hose_connector() -> Canvas:
    c = Canvas(16, 16)
    c.rect(6, 2, 9, 13, STEEL)
    c.rect(5, 3, 10, 5, BRASS)
    c.rect(5, 10, 10, 12, BRASS)
    c.rect(4, 5, 6, 10, STEEL_LIGHT)
    c.rect(9, 5, 11, 10, STEEL_LIGHT)
    return c

def hose_anchor() -> Canvas:
    c = Canvas(16, 16)
    c.rect(7, 2, 8, 12, STEEL)
    c.rect(3, 12, 12, 14, STEEL_DARK)
    c.rect(4, 11, 11, 13, STEEL)
    c.rect(6, 1, 9, 3, BRASS)
    return c

ITEM_GENERATORS = {
    "air_tank": air_tank,
    "backpack_sprayer": backpack_sprayer,
    "breathing_mask": breathing_mask,
    "fire_axe": fire_axe,
    "fire_hose_nozzle": fire_hose_nozzle,
    "fire_rake": fire_rake,
    "fire_shovel": fire_shovel,
    "firefighter_boots": firefighter_boots,
    "firefighter_helmet": firefighter_helmet,
    "firefighter_info": firefighter_info,
    "firefighter_jacket": firefighter_jacket,
    "firefighter_trousers": firefighter_trousers,
    "intake_strainer": intake_strainer,
    "pulaski": pulaski,
    "pump_fuel_can": pump_fuel_can,
    "thermal_scanner": thermal_scanner,
    "hose_roll_small": hose_roll_small,
    "hose_roll_standard": hose_roll_standard,
    "hose_roll_large": hose_roll_large,
    "hose_connector": hose_connector,
    "hose_anchor": hose_anchor,
}

BLOCK_ITEMS = (
    "portable_pump",
    "hose_splitter",
    "water_tank_small",
    "water_tank_medium",
    "water_tank_large",
    "portable_sprinkler",
    "command_post",
)

REGISTERED_ITEMS = BLOCK_ITEMS + tuple(ITEM_GENERATORS)


def generate_client_items() -> None:
    CLIENT_ITEMS.mkdir(parents=True, exist_ok=True)
    for name in REGISTERED_ITEMS:
        definition = {
            "model": {
                "type": "minecraft:model",
                "model": f"peterwolfs_forestfire:item/{name}",
            }
        }
        (CLIENT_ITEMS / f"{name}.json").write_text(
            json.dumps(definition, indent=2) + "\n",
            encoding="utf-8",
        )


def material_texture(base, dark, light, pattern: str = "speckle") -> Canvas:
    c = Canvas(16, 16, base)
    for y in range(16):
        for x in range(16):
            value = (x * 11 + y * 7 + x * y * 3) % 29
            if value in (0, 1):
                c.set(x, y, dark)
            elif value == 8:
                c.set(x, y, light)
    if pattern == "metal":
        c.line(0, 0, 15, 0, light)
        c.line(0, 15, 15, 15, dark)
        for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            c.set(x, y, STEEL_LIGHT)
            c.set(x + (1 if x < 8 else -1), y + 1, STEEL_DARK)
    elif pattern == "hose":
        for y in (1, 5, 9, 13):
            c.line(0, y, 15, y, dark)
        for y in (2, 6, 10, 14):
            c.line(0, y, 15, y, light)
    elif pattern == "canvas":
        for i in range(0, 16, 4):
            c.line(i, 0, i, 15, dark)
            c.line(0, i, 15, i, light)
    elif pattern == "screen":
        c.rect(0, 0, 15, 15, SCREEN_DARK)
        c.rect(1, 1, 14, 14, base)
        for i in range(2, 15, 4):
            c.line(i, 1, i, 14, dark)
            c.line(1, i, 14, i, dark)
        c.line(2, 11, 6, 8, light)
        c.line(6, 8, 9, 10, light)
        c.line(9, 10, 13, 5, light)
    return c


def generate_block_textures() -> None:
    textures = {
        BLOCK_TEXTURES / "command_post.png": material_texture(ORANGE_DARK, BROWN_DARK, ORANGE, "canvas"),
        BLOCK_TEXTURES / "fire_hose.png": material_texture(RED, RED_DARK, RED_LIGHT, "hose"),
        BLOCK_TEXTURES / "fire_hose_pressurised.png": material_texture(RED_LIGHT, RED, ORANGE, "hose"),
        BLOCK_TEXTURES / "hose_splitter.png": material_texture(BRASS, BRASS_DARK, BRASS_LIGHT, "metal"),
        BLOCK_TEXTURES / "intake_hose.png": material_texture(BLUE, BLUE_DARK, BLUE_LIGHT, "hose"),
        BLOCK_TEXTURES / "portable_pump.png": material_texture(RED, RED_DARK, RED_LIGHT, "metal"),
        BLOCK_TEXTURES / "portable_sprinkler.png": material_texture(STEEL, STEEL_DARK, STEEL_LIGHT, "metal"),
        BLOCK_TEXTURES / "water_tank_small.png": material_texture((71, 153, 192, 255), BLUE_DARK, BLUE_LIGHT, "canvas"),
        BLOCK_TEXTURES / "water_tank_medium.png": material_texture((52, 137, 181, 255), BLUE_DARK, BLUE_LIGHT, "canvas"),
        BLOCK_TEXTURES / "water_tank_large.png": material_texture((37, 119, 167, 255), BLUE_DARK, BLUE_LIGHT, "canvas"),
        MATERIAL_TEXTURES / "steel.png": material_texture(STEEL, STEEL_DARK, STEEL_LIGHT, "metal"),
        MATERIAL_TEXTURES / "dark_metal.png": material_texture(STEEL_DARK, RUBBER, STEEL, "metal"),
        MATERIAL_TEXTURES / "brass.png": material_texture(BRASS, BRASS_DARK, BRASS_LIGHT, "metal"),
        MATERIAL_TEXTURES / "rubber.png": material_texture(RUBBER, INK, STEEL_DARK, "hose"),
        MATERIAL_TEXTURES / "hazard_yellow.png": material_texture(YELLOW, YELLOW_DARK, REFLECTIVE, "metal"),
        MATERIAL_TEXTURES / "label.png": material_texture(WHITE, STEEL, (255, 250, 229, 255), "canvas"),
        MATERIAL_TEXTURES / "screen.png": material_texture(SCREEN, SCREEN_DARK, REFLECTIVE, "screen"),
        MATERIAL_TEXTURES / "orange_canvas.png": material_texture(ORANGE, ORANGE_DARK, YELLOW, "canvas"),
    }
    for path, texture in textures.items():
        write_png(path, texture)


HUMANOID_MASK = {
    0: [(8, 15)], 1: [(8, 15)], 2: [(8, 15)], 3: [(8, 15)],
    4: [(8, 15)], 5: [(8, 15)], 6: [(8, 15)], 7: [(8, 15)],
    8: [(0, 31)], 9: [(0, 31)], 10: [(0, 31)],
    11: [(0, 8), (11, 12), (15, 31)],
    12: [(0, 3), (11, 12), (20, 31)], 13: [(24, 31)], 14: [(26, 29)],
    16: [(8, 11), (44, 47)], 17: [(8, 11), (44, 47)],
    18: [(8, 11), (44, 47)], 19: [(8, 11), (44, 47)],
    20: [(16, 21), (26, 33), (38, 55)], 21: [(16, 22), (25, 55)],
    22: [(16, 55)], 23: [(16, 55)], 24: [(16, 55)], 25: [(16, 39)],
    26: [(0, 39)], 27: [(0, 39)], 28: [(0, 39)],
    29: [(0, 15), (21, 26), (33, 38)], 30: [(0, 15), (22, 25)], 31: [(0, 15)],
}

LEGGINGS_MASK = {
    16: [(4, 7)], 17: [(4, 7)], 18: [(4, 7)], 19: [(4, 7)],
    20: [(0, 15)], 21: [(0, 15)], 22: [(0, 15)], 23: [(0, 15)],
    24: [(0, 15)], 25: [(0, 15)], 26: [(0, 15)],
    27: [(0, 39)], 28: [(0, 39)], 29: [(16, 39)], 30: [(16, 39)], 31: [(16, 39)],
}


def masked_armor(mask, leggings=False) -> Canvas:
    c = Canvas(64, 32)
    for y, intervals in mask.items():
        for x0, x1 in intervals:
            for x in range(x0, x1 + 1):
                if not leggings and y <= 14:
                    color = YELLOW if (x + y) % 5 else YELLOW_DARK
                else:
                    color = BROWN_DARK if (x * 3 + y) % 7 else INK_SOFT
                c.set(x, y, color)
    bands = {22: REFLECTIVE, 28: REFLECTIVE} if not leggings else {23: ORANGE, 28: REFLECTIVE}
    for y, color in bands.items():
        for x in range(64):
            if c.get(x, y)[3]:
                c.set(x, y, color)
    if not leggings:
        for x in range(8, 16):
            if c.get(x, 8)[3]:
                c.set(x, 8, ORANGE)
        for x, y in [(11, 3), (12, 3), (11, 4), (12, 4)]:
            if c.get(x, y)[3]:
                c.set(x, y, RED)
    return c


def generate_equipment() -> None:
    write_png(EQUIPMENT_TEXTURES / "humanoid/firefighter.png", masked_armor(HUMANOID_MASK))
    write_png(EQUIPMENT_TEXTURES / "humanoid_leggings/firefighter.png", masked_armor(LEGGINGS_MASK, True))


def generate_mod_icon() -> None:
    c = Canvas(32, 32)
    c.rect(1, 1, 30, 30, INK)
    c.rect(2, 2, 29, 29, (22, 39, 40, 255))
    c.rect(3, 3, 28, 28, FOREST)
    c.polygon([(4, 7), (15, 2), (27, 7), (27, 18), (16, 29), (4, 18)], INK)
    c.polygon([(6, 8), (15, 4), (25, 8), (25, 17), (16, 26), (6, 17)], (44, 48, 46, 255))
    c.polygon([(7, 20), (8, 13), (12, 8), (13, 14), (16, 11), (16, 18), (13, 23), (9, 23)], RED)
    c.polygon([(9, 20), (10, 15), (12, 12), (13, 17), (14, 16), (14, 20), (12, 22)], ORANGE)
    c.set(12, 20, YELLOW)
    c.polygon([(18, 20), (21, 10), (25, 18), (24, 23), (20, 24)], BLUE)
    c.polygon([(20, 19), (21, 14), (23, 18), (23, 21), (21, 22)], BLUE_LIGHT)
    c.polygon([(7, 11), (9, 7), (13, 5), (18, 5), (22, 8), (23, 12)], YELLOW)
    c.rect(6, 11, 24, 14, YELLOW_DARK)
    c.rect(8, 11, 22, 12, YELLOW)
    c.rect(12, 5, 17, 8, RED)
    c.rect(14, 5, 15, 6, WHITE)
    write_png(ASSETS / "icon.png", c.scaled(4))


def faces(texture: str) -> dict:
    return {
        direction: {"texture": f"#{texture}"}
        for direction in ("north", "east", "south", "west", "up", "down")
    }


def element(start, end, texture: str, rotation=None, shade=True) -> dict:
    value = {"from": start, "to": end, "faces": faces(texture)}
    if rotation:
        value["rotation"] = rotation
    if not shade:
        value["shade"] = False
    return value


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {
        "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]
    },
    "thirdperson_lefthand": {
        "rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]
    },
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
}


def model(textures: dict, elements: list[dict], ambient=False) -> dict:
    textures = dict(textures)
    textures.setdefault("particle", next(iter(textures.values())))
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": ambient,
        "display": DISPLAY,
        "textures": textures,
        "elements": elements,
    }


def portable_pump_model() -> dict:
    textures = {
        "body": "peterwolfs_forestfire:block/portable_pump",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
        "brass": "peterwolfs_forestfire:block/material/brass",
        "yellow": "peterwolfs_forestfire:block/material/hazard_yellow",
        "label": "peterwolfs_forestfire:block/material/label",
    }
    parts = [
        element([1, 0, 2], [3, 2, 14], "dark"), element([13, 0, 2], [15, 2, 14], "dark"),
        element([1, 1, 3], [15, 3, 5], "steel"), element([1, 1, 11], [15, 3, 13], "steel"),
        element([3, 3, 4], [13, 11, 12], "body"), element([4, 11, 5], [12, 14, 11], "body"),
        element([5, 5, 3.5], [11, 10, 4], "dark"), element([5, 8, 3.35], [11, 10, 3.55], "label", shade=False),
        element([1, 5, 6], [3, 9, 10], "steel"), element([0, 6, 7], [2, 8, 9], "brass"),
        element([13, 5, 6], [15, 9, 10], "brass"), element([14, 6, 7], [16, 8, 9], "steel"),
        element([2, 12, 2], [3, 15, 14], "yellow"), element([13, 12, 2], [14, 15, 14], "yellow"),
        element([2, 14, 2], [14, 15, 3], "yellow"), element([2, 14, 13], [14, 15, 14], "yellow"),
    ]
    return model(textures, parts)


def hose_model(pressurised=False) -> dict:
    hose_texture = "peterwolfs_forestfire:block/fire_hose_pressurised" if pressurised else "peterwolfs_forestfire:block/fire_hose"
    textures = {
        "hose": hose_texture,
        "brass": "peterwolfs_forestfire:block/material/brass",
        "dark": "peterwolfs_forestfire:block/material/rubber",
    }
    height = 3.5 if pressurised else 2.5
    thickness = 5.0 if pressurised else 4.0
    z0 = 8 - thickness / 2
    z1 = 8 + thickness / 2
    parts = [
        element([0, 0, z0], [16, height, z1], "hose"),
        element([0, 0, z0 - 0.5], [2, height + 0.5, z1 + 0.5], "brass"),
        element([14, 0, z0 - 0.5], [16, height + 0.5, z1 + 0.5], "brass"),
        element([2, 0.25, z0 - 0.25], [2.5, height + 0.25, z1 + 0.25], "dark"),
        element([13.5, 0.25, z0 - 0.25], [14, height + 0.25, z1 + 0.25], "dark"),
    ]
    return model(textures, parts)


def intake_hose_model() -> dict:
    textures = {
        "hose": "peterwolfs_forestfire:block/intake_hose",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
    }
    parts = [element([0, 0, 5.75], [16, 3, 10.25], "hose")]
    for x in (2, 4, 6, 8, 10, 12, 14):
        parts.append(element([x, 0, 5.5], [x + 0.5, 3.5, 10.5], "dark"))
    parts += [
        element([0, 0, 5.25], [2, 4, 10.75], "steel"),
        element([14, 0, 5.25], [16, 4, 10.75], "steel"),
    ]
    return model(textures, parts)


def splitter_model() -> dict:
    textures = {
        "body": "peterwolfs_forestfire:block/hose_splitter",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
        "red": "peterwolfs_forestfire:block/portable_pump",
    }
    parts = [
        element([3, 0, 5], [13, 2, 11], "dark"),
        element([4, 2, 6], [12, 6, 10], "body"),
        element([0, 2.5, 6.5], [4, 5.5, 9.5], "steel"),
        element([12, 2.5, 6.5], [16, 5.5, 9.5], "steel"),
        element([6.5, 2.5, 10], [9.5, 5.5, 16], "steel"),
        element([1, 2, 6], [2.5, 6, 10], "red"),
        element([13.5, 2, 6], [15, 6, 10], "red"),
        element([6, 2, 13.5], [10, 6, 15], "red"),
        element([6, 6, 6], [10, 8, 10], "body"),
    ]
    return model(textures, parts)


def tank_model(size: str) -> dict:
    configs = {
        "small": (3, 13, 2, 10, "water_tank_small"),
        "medium": (2, 14, 1, 13, "water_tank_medium"),
        "large": (1, 15, 1, 15, "water_tank_large"),
    }
    lo, hi, bottom, top, texture = configs[size]
    textures = {
        "tank": f"peterwolfs_forestfire:block/{texture}",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
        "label": "peterwolfs_forestfire:block/material/label",
    }
    body_lo, body_hi = lo + 1, hi - 1
    parts = [
        element([body_lo, bottom, body_lo], [body_hi, top, body_hi], "tank"),
        element([lo, 0, lo], [lo + 1, top + 1, lo + 1], "steel"),
        element([hi - 1, 0, lo], [hi, top + 1, lo + 1], "steel"),
        element([lo, 0, hi - 1], [lo + 1, top + 1, hi], "steel"),
        element([hi - 1, 0, hi - 1], [hi, top + 1, hi], "steel"),
        element([lo, bottom, lo], [hi, bottom + 1, lo + 1], "steel"),
        element([lo, bottom, hi - 1], [hi, bottom + 1, hi], "steel"),
        element([lo, top, lo], [hi, top + 1, lo + 1], "steel"),
        element([lo, top, hi - 1], [hi, top + 1, hi], "steel"),
        element([7, top, 7], [9, min(16, top + 2), 9], "dark"),
        element([body_lo - 0.1, bottom + 2, lo - 0.2], [body_hi + 0.1, min(top - 1, bottom + 5), lo], "label", shade=False),
    ]
    return model(textures, parts)


def sprinkler_model() -> dict:
    textures = {
        "body": "peterwolfs_forestfire:block/portable_sprinkler",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "brass": "peterwolfs_forestfire:block/material/brass",
        "blue": "peterwolfs_forestfire:block/intake_hose",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
    }
    parts = [
        element([1, 0, 7], [15, 1.5, 9], "dark"), element([7, 0, 1], [9, 1.5, 15], "dark"),
        element([6, 1, 6], [10, 3, 10], "steel"), element([7, 2, 7], [9, 12, 9], "steel"),
        element([6.5, 11, 6.5], [9.5, 14, 9.5], "brass"),
        element([2, 12, 7], [14, 13.5, 9], "blue"),
        element([1, 11.5, 6.5], [3, 14, 9.5], "brass"),
        element([13, 11.5, 6.5], [15, 14, 9.5], "brass"),
    ]
    return model(textures, parts)


def command_post_model() -> dict:
    textures = {
        "case": "peterwolfs_forestfire:block/command_post",
        "canvas": "peterwolfs_forestfire:block/material/orange_canvas",
        "dark": "peterwolfs_forestfire:block/material/dark_metal",
        "steel": "peterwolfs_forestfire:block/material/steel",
        "screen": "peterwolfs_forestfire:block/material/screen",
        "label": "peterwolfs_forestfire:block/material/label",
        "yellow": "peterwolfs_forestfire:block/material/hazard_yellow",
    }
    parts = [
        element([2, 0, 2], [14, 7, 14], "case"),
        element([1, 6, 1], [15, 8, 15], "canvas"),
        element([2, 1, 1.5], [14, 4, 2], "label", shade=False),
        element([4, 8, 7], [12, 14, 9], "dark"),
        element([5, 9, 6.75], [11, 13, 7], "screen", shade=False),
        element([5, 9, 9], [11, 13, 9.25], "screen", shade=False),
        element([3, 8, 3], [7, 11, 6], "dark"),
        element([3.5, 10.5, 3.5], [6.5, 11.5, 5.5], "steel"),
        element([4.5, 11, 4.25], [5, 16, 4.75], "steel"),
        element([12, 8, 11], [14, 11, 13], "yellow"),
        element([2, 7.5, 2], [3, 9, 3], "steel"),
        element([13, 7.5, 2], [14, 9, 3], "steel"),
        element([2, 7.5, 13], [3, 9, 14], "steel"),
        element([13, 7.5, 13], [14, 9, 14], "steel"),
    ]
    return model(textures, parts)


def write_model(name: str, value: dict) -> None:
    path = BLOCK_MODELS / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def generate_models() -> None:
    write_model("portable_pump", portable_pump_model())
    write_model("hose_splitter", splitter_model())
    write_model("water_tank_small", tank_model("small"))
    write_model("water_tank_medium", tank_model("medium"))
    write_model("water_tank_large", tank_model("large"))
    write_model("portable_sprinkler", sprinkler_model())
    write_model("command_post", command_post_model())


def main() -> None:
    for name, generator in ITEM_GENERATORS.items():
        write_png(ITEM_TEXTURES / f"{name}.png", generator())
    generate_client_items()
    generate_block_textures()
    generate_equipment()
    generate_mod_icon()
    generate_models()
    print(
        f"Generated {len(ITEM_GENERATORS)} item icons, {len(REGISTERED_ITEMS)} client item definitions, "
        "18 block/material textures, 2 armor textures, 10 block models and the 128px mod icon."
    )


if __name__ == "__main__":
    main()
