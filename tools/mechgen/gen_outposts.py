"""Mech Frame outposts: world-generated structures guarded by enemy frames (the defence AI).

Writes, into src/main/resources/data/mechframe/:
  structure/outpost/<name>.nbt         structure templates (vanilla structure-block format)
  worldgen/template_pool/outpost/*.json
  worldgen/structure/<name>.json       jigsaw structures (single piece, beard_box terrain adaptation)
  worldgen/structure_set/outposts.json
  tags/worldgen/biome/has_structure/outpost.json

Each outpost holds one Mech Command Post whose block-entity data (faction, territory radius, unit
tiers, unit count, skill) is stored in the template - change it here and rerun.

    python gen_outposts.py
"""
import gzip
import json
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "mechframe")
DATA_VERSION = 4671  # Minecraft 1.21.11

# ------------------------------------------------------------------------------------- tiny NBT writer
TAG_END, TAG_BYTE, TAG_INT, TAG_FLOAT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 5, 8, 9, 10


class Int(int):
    pass


class Float(float):
    pass


def _tag_type(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, Float) or isinstance(v, float):
        return TAG_FLOAT
    if isinstance(v, bool):
        return TAG_BYTE
    return TAG_INT


def _payload(v):
    t = _tag_type(v)
    if t == TAG_COMPOUND:
        out = b""
        for k, x in v.items():
            out += bytes([_tag_type(x)]) + _string(k) + _payload(x)
        return out + bytes([TAG_END])
    if t == TAG_LIST:
        et = _tag_type(v[0]) if v else TAG_END
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(x) for x in v)
    if t == TAG_STRING:
        return _string(v)
    if t == TAG_FLOAT:
        return struct.pack(">f", v)
    if t == TAG_BYTE:
        return struct.pack(">b", 1 if v else 0)
    return struct.pack(">i", int(v))


def _string(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def write_nbt(path, root):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = bytes([TAG_COMPOUND]) + _string("") + _payload(root)
    with gzip.open(path, "wb") as f:
        f.write(data)


# ------------------------------------------------------------------------------------- template builder
class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.nbt = {}

    def set(self, x, y, z, state):
        sx, sy, sz = self.size
        if 0 <= x < sx and 0 <= y < sy and 0 <= z < sz:
            self.blocks[(x, y, z)] = state

    def box(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def write(self, path):
        palette, index = [], {}
        blocks = []
        for (x, y, z), state in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if state not in index:
                index[state] = len(palette)
                name, _, props = state.partition("[")
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(p.split("=") for p in props.rstrip("]").split(","))
                palette.append(entry)
            b = {"pos": [Int(x), Int(y), Int(z)], "state": Int(index[state])}
            if (x, y, z) in self.nbt:
                b["nbt"] = self.nbt[(x, y, z)]
            blocks.append(b)
        write_nbt(path, {"DataVersion": Int(DATA_VERSION), "size": [Int(v) for v in self.size], "palette": palette,
                         "blocks": blocks, "entities": []})


AIR = "minecraft:air"


def post_nbt(radius, tier_min, tier_max, count, skill):
    return {"id": "mechframe:command_post", "Faction": "enemy", "Radius": Int(radius), "TierMin": Int(tier_min),
            "TierMax": Int(tier_max), "Count": Int(count), "Skill": Float(skill), "Units": "", "RespawnAt": ""}


def outpost():
    """A walled compound (31 x 31): four low walls with gates in the middle of each side, lamp
    pillars at the corners, a raised pad with the command post in the middle, supply crates. One
    unit, tiers 1-3."""
    S, H = 31, 9
    t = Template(S, H, S)
    t.box(0, 1, 0, S - 1, H - 1, S - 1, AIR)                       # clear the site
    t.box(0, 0, 0, S - 1, 0, S - 1, "minecraft:polished_andesite")
    for k in range(3, S - 3, 6):                                   # floor joints
        t.box(k, 0, 1, k, 0, S - 2, "minecraft:gray_concrete")
        t.box(1, 0, k, S - 2, 0, k, "minecraft:gray_concrete")
    # walls with gates
    for y in range(1, 4):
        for i in range(S):
            for (x, z) in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
                if abs(i - S // 2) <= 2:
                    continue
                t.set(x, y, z, "minecraft:stone_bricks" if y < 3 else "minecraft:polished_deepslate")
    # corner pillars with lamps
    for (x, z) in ((0, 0), (0, S - 1), (S - 1, 0), (S - 1, S - 1)):
        t.box(x, 1, z, x, 5, z, "minecraft:deepslate_tiles")
        t.set(x, 6, z, "minecraft:sea_lantern")
    # central pad + command post
    c = S // 2
    t.box(c - 3, 1, c - 3, c + 3, 1, c + 3, "minecraft:smooth_stone")
    t.box(c - 3, 1, c, c + 3, 1, c, "minecraft:red_concrete")
    t.box(c, 1, c - 3, c, 1, c + 3, "minecraft:red_concrete")
    t.set(c, 2, c, "mechframe:command_post")
    t.nbt[(c, 2, c)] = post_nbt(36, 1, 3, 1, 0.45)
    for (x, z) in ((c - 3, c - 3), (c + 3, c - 3), (c - 3, c + 3), (c + 3, c + 3)):
        t.set(x, 2, z, "minecraft:iron_bars")
    # supply crates in two corners
    for (x, z) in ((3, 3), (4, 3), (3, 4), (S - 4, S - 4), (S - 5, S - 4)):
        t.set(x, 1, z, "minecraft:barrel[facing=up]")
    t.set(3, 2, 3, "minecraft:barrel[facing=up]")
    return t


def fort():
    """A fortified base (45 x 45): thick walls with a walkway, four towers, a gate on each side,
    an inner deployment pad with the command post. Two units, tiers 2-5, better pilots."""
    S, H = 45, 12
    t = Template(S, H, S)
    t.box(0, 1, 0, S - 1, H - 1, S - 1, AIR)
    t.box(0, 0, 0, S - 1, 0, S - 1, "minecraft:polished_blackstone")
    t.box(6, 0, 6, S - 7, 0, S - 7, "minecraft:smooth_stone")
    for k in range(8, S - 7, 5):
        t.box(k, 0, 6, k, 0, S - 7, "minecraft:yellow_concrete" if (k // 5) % 2 else "minecraft:black_concrete")
    # thick walls (2) with a crenellated top
    for y in range(1, 6):
        for i in range(S):
            for d in (0, 1):
                for (x, z) in ((i, d), (i, S - 1 - d), (d, i), (S - 1 - d, i)):
                    if abs(i - S // 2) <= 3:
                        continue
                    t.set(x, y, z, "minecraft:deepslate_bricks" if y < 5 else "minecraft:polished_deepslate")
    for i in range(0, S, 2):
        for (x, z) in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            if abs(i - S // 2) > 3:
                t.set(x, 6, z, "minecraft:deepslate_brick_wall")
    # gate frames
    for (x0, z0, x1, z1) in ((S // 2 - 4, 0, S // 2 + 4, 1), (S // 2 - 4, S - 2, S // 2 + 4, S - 1),
                             (0, S // 2 - 4, 1, S // 2 + 4), (S - 2, S // 2 - 4, S - 1, S // 2 + 4)):
        t.box(x0, 6, z0, x1, 6, z1, "minecraft:polished_deepslate")
    # towers
    for (x, z) in ((0, 0), (0, S - 5), (S - 5, 0), (S - 5, S - 5)):
        t.box(x, 1, z, x + 4, 8, z + 4, "minecraft:deepslate_tiles")
        t.box(x + 1, 1, z + 1, x + 3, 7, z + 3, AIR)
        t.box(x, 9, z, x + 4, 9, z + 4, "minecraft:polished_deepslate")
        t.set(x + 2, 10, z + 2, "minecraft:sea_lantern")
        for (dx, dz) in ((0, 0), (4, 0), (0, 4), (4, 4)):
            t.set(x + dx, 10, z + dz, "minecraft:deepslate_tile_wall")
    # inner pad + command post on a plinth
    c = S // 2
    t.box(c - 4, 1, c - 4, c + 4, 1, c + 4, "minecraft:polished_andesite")
    t.box(c - 4, 1, c, c + 4, 1, c, "minecraft:red_concrete")
    t.box(c, 1, c - 4, c, 1, c + 4, "minecraft:red_concrete")
    t.box(c - 1, 2, c - 1, c + 1, 2, c + 1, "minecraft:polished_blackstone")
    t.set(c, 3, c, "mechframe:command_post")
    t.nbt[(c, 3, c)] = post_nbt(50, 2, 5, 2, 0.7)
    for (x, z) in ((c - 4, c - 4), (c + 4, c - 4), (c - 4, c + 4), (c + 4, c + 4)):
        t.box(x, 2, z, x, 4, z, "minecraft:iron_bars")
        t.set(x, 5, z, "minecraft:sea_lantern")
    # hangar racks along one inner wall
    for x in range(8, S - 8, 6):
        t.box(x, 1, 3, x, 4, 3, "minecraft:iron_block")
        t.box(x, 5, 2, x, 5, 4, "minecraft:polished_deepslate")
        t.set(x + 2, 1, 3, "minecraft:barrel[facing=up]")
    return t


STRUCTURES = {"outpost": outpost, "fort": fort}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    for name, build in STRUCTURES.items():
        build().write(os.path.join(DATA, "structure", "outpost", name + ".nbt"))
        write_json(os.path.join(DATA, "worldgen", "template_pool", "outpost", name + ".json"),
                   {"fallback": "minecraft:empty",
                    "elements": [{"weight": 1, "element": {"element_type": "minecraft:single_pool_element",
                                                           "location": f"mechframe:outpost/{name}",
                                                           "projection": "rigid", "processors": "minecraft:empty"}}]})
        write_json(os.path.join(DATA, "worldgen", "structure", name + ".json"),
                   {"type": "minecraft:jigsaw", "biomes": "#mechframe:has_structure/outpost",
                    "step": "surface_structures", "spawn_overrides": {}, "terrain_adaptation": "beard_box",
                    "start_pool": f"mechframe:outpost/{name}", "size": 1, "start_height": {"absolute": 0},
                    "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80,
                    "use_expansion_hack": False})
    write_json(os.path.join(DATA, "worldgen", "structure_set", "outposts.json"),
               {"structures": [{"structure": "mechframe:outpost", "weight": 3}, {"structure": "mechframe:fort", "weight": 1}],
                "placement": {"type": "minecraft:random_spread", "spacing": 34, "separation": 12, "salt": 73190526}})
    write_json(os.path.join(DATA, "tags", "worldgen", "biome", "has_structure", "outpost.json"),
               {"values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna",
                           "minecraft:savanna_plateau", "minecraft:desert", "minecraft:snowy_plains", "minecraft:taiga",
                           "minecraft:snowy_taiga", "minecraft:badlands", "minecraft:sparse_jungle",
                           "minecraft:windswept_savanna"]})
    print("outposts written")


if __name__ == "__main__":
    main()
