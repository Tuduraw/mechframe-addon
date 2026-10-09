"""Builds every part (OBJ, texture, part JSON), the projectile models, the crafting recipes, and
assembled previews of every preset.

    python build_all.py            # everything
    python build_all.py --no-preview
"""
import json
import os
import sys

import numpy as np

from mg import mk, export, finish, ASSETS, DATA, cyl, cbox, seg
import parts_kd
import parts_gw
import parts_ar
import parts_kf
import parts_ks
import parts_km
import parts_weapons
import assemble

FACTION = {}  # part id -> faction key for recipes

TIER_MATERIAL = {1: "minecraft:iron_ingot", 2: "minecraft:iron_block", 3: "minecraft:gold_block",
                 4: "minecraft:diamond", 5: "minecraft:diamond"}
FACTION_MATERIAL = {"kd": "minecraft:quartz", "gw": "minecraft:copper_ingot", "ar": "minecraft:amethyst_shard",
                    "kf": "minecraft:phantom_membrane", "ks": "minecraft:feather", "km": "minecraft:prismarine_crystals"}
PATTERNS = {
    "head": ([" F ", "TGT", " T "], {"G": "minecraft:glass"}),
    "core": (["TFT", "TRT", "TTT"], {"R": "minecraft:redstone_block"}),
    "arms": (["T T", "F F", "T T"], {}),
    "legs": (["TFT", "T T", "T T"], {}),
    "booster": (["TFT", "TBT", "F F"], {"B": "minecraft:fire_charge"}),
    "weapon_arm": (["TTF", " GR", "  T"], {"G": "minecraft:gunpowder", "R": "minecraft:redstone"}),
    "weapon_back": (["TGT", "TFT", "R R"], {"G": "minecraft:gunpowder", "R": "minecraft:redstone"}),
    "weapon_melee": (["  F", " T ", "R  "], {"R": "minecraft:redstone_block"}),
}


def faction_of(module):
    return {"parts_kd": "kd", "parts_gw": "gw", "parts_ar": "ar", "parts_kf": "kf", "parts_ks": "ks",
            "parts_km": "km"}.get(module, None)


def recipe(pid, meta, faction, ns="mechframe"):
    """Mech Fabricator recipe (data/<ns>/mech_recipe/<pid>.json): the materials of the former
    crafting-table pattern of the slot, as counts."""
    slot = meta["slot"]
    if slot == "weapon":
        slot = ("weapon_back" if meta["stats"]["mount"] == "back"
                else "weapon_melee" if "melee" in meta["stats"] else "weapon_arm")
    pattern, extra = PATTERNS[slot]
    key = {"T": TIER_MATERIAL[meta["tier"]], "F": FACTION_MATERIAL[faction]}
    key.update(extra)
    counts = {}
    for row in pattern:
        for ch in row:
            if ch != " ":
                counts[key[ch]] = counts.get(key[ch], 0) + 1
    return {"result": {"part": f"{ns}:{pid}"},
            "ingredients": [{"item": item, "count": n} for item, n in counts.items()]}


WEAPON_FACTION = {"wa_rifle": "kd", "wb_missile": "kd", "wa_mg": "gw", "wa_bazooka": "gw", "wb_cannon": "gw",
                  "wa_laser": "ar", "wb_gatling": "ar", "wa_gunpod": "kf", "wa_blade": "kd"}


def bullets():
    """Projectile models referenced by ModelBullet (models/obj/bullet_<name>.obj)."""
    out = []
    m = mk.Model("bullet_mf_shell")
    m.add("bullet", mk.lathe((0, 0, -0.3), (0, 0, 1), [(0, 0.06), (0.4, 0.07), (0.6, 0.0)], n=6, tag="metal"))
    finish(m, "gw", size=64)
    out.append(m)
    m = mk.Model("bullet_mf_missile")
    m.add("bullet", mk.lathe((0, 0, -0.6), (0, 0, 1), [(0, 0.09), (0.9, 0.1), (1.2, 0.0)], n=6, tag="armor"))
    for a in (0, 90):
        rot = mk.rot_z(a)
        fin = mk.box(0, 0, -0.45, 0.36, 0.02, 0.2, tag="accent")
        fin.rotate(rot, (0, 0, -0.45))
        m.add("bullet", fin)
    finish(m, "kd", size=64)
    out.append(m)
    m = mk.Model("bullet_mf_laser")
    m.add("bullet", mk.lathe((0, 0, -0.8), (0, 0, 1), [(0, 0.0), (0.2, 0.06), (1.4, 0.06), (1.6, 0.0)], n=6, tag="eye"))
    finish(m, "ar", size=64)
    out.append(m)
    for b in out:
        mk.save(b, os.path.join(ASSETS, "models", "obj", b.name + ".obj"))
        from PIL import Image
        os.makedirs(os.path.join(ASSETS, "textures", "vehicle"), exist_ok=True)
        Image.fromarray(b.texture).save(os.path.join(ASSETS, "textures", "vehicle", b.name + ".png"))
    return [b.name for b in out]


PRESETS = {
    "striker": dict(legs="lg_stride", core="cr_swift", head="hd_lark", arms="am_talon", booster="bs_zephyr",
                    r_arm="wa_rifle", l_arm="wa_rifle", l_back="wb_missile"),
    "hopper": dict(legs="lg_hopper", core="cr_swift", head="hd_wren", arms="am_talon", booster="bs_zephyr",
                   r_arm="wa_rifle", l_arm="wa_mg", r_back="wb_missile"),
    "rampart": dict(legs="lg_rampart", core="cr_bastion", head="hd_visor", arms="am_maul", booster="bs_thrust",
                    r_arm="wa_mg", l_arm="wa_bazooka", r_back="wb_cannon", l_back="wb_cannon"),
    "bastion": dict(legs="lg_pillar", core="cr_bastion", head="hd_visor", arms="am_maul", booster="bs_thrust",
                    r_arm="wa_mg", l_arm="wa_bazooka", r_back="wb_cannon", l_back="wb_missile"),
    "weaver": dict(legs="lg_weaver", core="cr_thorax", head="hd_ocellus", arms="am_mandible", booster="bs_glide",
                   r_arm="wa_laser", l_arm="wa_mg", r_back="wb_gatling"),
    "strider": dict(legs="lg_strider", core="cr_thorax", head="hd_ocellus", arms="am_mandible", booster="bs_glide",
                    r_arm="wa_laser", l_arm="wa_laser", r_back="wb_gatling", l_back="wb_missile"),
    "falcon": dict(legs="lg_falcon", core="cr_falcon", head="hd_falcon", arms="am_falcon", booster="bs_falcon",
                   r_arm="wa_gunpod", l_arm="wa_gunpod", l_back="wb_missile"),
    "swallow": dict(legs="lg_swallow", core="cr_swallow", head="hd_swallow", arms="am_swallow", booster="bs_swallow",
                    r_arm="wa_gunpod", l_arm="wa_gunpod", l_back="wb_missile"),
    "manta": dict(legs="lg_manta", core="cr_manta", head="hd_manta", arms="am_manta", booster="bs_manta",
                  r_arm="wa_gunpod", l_arm="wa_gunpod", r_back="wb_missile"),
}


def main():
    previews = "--no-preview" not in sys.argv
    built = {}
    for module in (parts_weapons, parts_kd, parts_gw, parts_ar):
        for builder in module.ALL:
            m, meta = builder()
            built[m.name] = (m, meta)
            FACTION[m.name] = faction_of(module.__name__) or WEAPON_FACTION[m.name]
    # The transform sets' jet poses are rigged on their assembled presets (they depend on the weapons carried).
    for preset, module, fac in (("falcon", parts_kf, "kf"), ("swallow", parts_ks, "ks"), ("manta", parts_km, "km")):
        combo = PRESETS[preset]
        tf = module.build_set({k: built[v] for k, v in combo.items() if k in ("r_arm", "l_arm", "r_back", "l_back")})
        for pid, pm in tf.items():
            built[pid] = pm
            FACTION[pid] = fac
    report = []
    for pid, (m, meta) in built.items():
        report.append(export(pid, m, meta, previews=previews))
    bad = [r for r in report if r["issues"]]
    print(f"{len(report)} parts, {sum(r['tris'] for r in report)} tris total, issues in: {[r['id'] for r in bad]}")

    # Parts are made at the Mech Fabricator (mech_recipe), no longer at the crafting table.
    old = os.path.join(DATA, "recipe")
    for fn in os.listdir(old) if os.path.isdir(old) else []:
        if fn.startswith("part_"):
            os.remove(os.path.join(old, fn))
    rdir = os.path.join(DATA, "mech_recipe")
    os.makedirs(rdir, exist_ok=True)
    for pid, (m, meta) in built.items():
        with open(os.path.join(rdir, pid + ".json"), "w") as f:
            json.dump(recipe(pid, meta, FACTION[pid]), f, indent=2)
    print("bullets:", bullets())

    import rig
    from look import look
    problems = []
    for name, combo in PRESETS.items():
        parts = {k: built[v] for k, v in combo.items()}
        res = look(name, parts, tf=True) if previews else {
            "tris": rig.model_at(parts, rig.robot_keys(parts)).tri_count(),
            "floating": rig.floating_groups(rig.model_at(parts, rig.robot_keys(parts)))}
        print(name, {k: v for k, v in res.items() if k != "img"})
        if res["tris"] > 5000:
            problems.append(f"{name}: {res['tris']} tris")
        for k, v in res.items():
            if k.startswith("floating") and v:
                problems.append(f"{name} {k}: {v}")
    if problems:
        raise SystemExit("assembly problems: " + "; ".join(problems))


if __name__ == "__main__":
    main()
