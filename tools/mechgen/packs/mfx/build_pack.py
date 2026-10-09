"""Builds the expansion pack: every part (OBJ, texture, part JSON with its fabricator recipe), the
presets (vehicle JSONs), the weapon files, the language files and the pack README.

    python build_pack.py              # everything, with previews
    python build_pack.py --no-preview
    python build_pack.py --look va    # only re-render the presets of one maker (fast iteration)
"""
import json
import os
import shutil
import sys

import common  # noqa: F401  (sets up sys.path and the schemes)
from common import NS, PACK, ASSETS, DATA, PREVIEW, MAKERS, export, ref
import mg
import look as lookmod

lookmod.MAKER_SCHEME.update({v: k for k, v in MAKERS.items()})
lookmod.OUT = PREVIEW
os.makedirs(PREVIEW, exist_ok=True)

import parts_va  # noqa: E402
import parts_va_kite  # noqa: E402
import parts_cv  # noqa: E402
import parts_cv_corvus  # noqa: E402
import parts_th  # noqa: E402
import parts_th_condor  # noqa: E402
import parts_ox  # noqa: E402

MODULES = {"va": [parts_va, parts_va_kite], "cv": [parts_cv, parts_cv_corvus], "th": [parts_th, parts_th_condor],
           "ox": [parts_ox]}

# Mech Fabricator materials by tier and maker (documented in README_pack.md - keep them in step).
TIER_MATERIAL = {1: "minecraft:iron_ingot", 2: "minecraft:iron_ingot", 3: "minecraft:gold_ingot",
                 4: "minecraft:diamond", 5: "minecraft:diamond"}
MAKER_MATERIAL = {"va": "minecraft:feather", "cv": "minecraft:black_dye", "th": "minecraft:iron_block",
                  "ox": "minecraft:ender_pearl"}
SLOT_COST = {"head": (2, 1, [("minecraft:glass", 1)]), "core": (5, 2, [("minecraft:redstone_block", 1)]),
             "arms": (4, 2, []), "legs": (6, 2, []), "booster": (4, 2, [("minecraft:fire_charge", 2)]),
             "weapon": (3, 1, [("minecraft:gunpowder", 2), ("minecraft:redstone", 1)])}


def recipe_for(meta, maker):
    """Inline fabricator recipe (the part JSON's "recipe"): tier metal, the maker's signature
    material, slot extras; transform-set parts also take a phantom membrane."""
    n_metal, n_maker, extra = SLOT_COST[meta["slot"]]
    tier = meta["tier"]
    ing = {TIER_MATERIAL[tier]: n_metal + (2 if tier >= 4 else 0), MAKER_MATERIAL[maker]: n_maker}
    if tier >= 3:
        ing["minecraft:iron_ingot"] = ing.get("minecraft:iron_ingot", 0) + 2
    for item, n in extra:
        ing[item] = ing.get(item, 0) + n
    if meta.get("transform_set"):
        ing["minecraft:phantom_membrane"] = 2
    return {"ingredients": [{"item": k, "count": v} for k, v in ing.items()]}


def build_all(makers=None):
    built = {}
    owner = {}
    for maker, mods in MODULES.items():
        if makers and maker not in makers:
            continue
        for mod in mods:
            if hasattr(mod, "build_set"):
                for pid, pm in mod.build_set().items():
                    built[pid] = pm
                    owner[pid] = maker
            else:
                for b in mod.ALL:
                    m, meta = b()
                    built[m.name] = (m, meta)
                    owner[m.name] = maker
    return built, owner


# ---------------------------------------------------------------------------------- presets
# id: (display name, tier, flare, maker, {mount: part}) - parts without a namespace are this pack's,
# "mechframe:..." the base mod's weapons.
PRESETS = {
    "wisp": ("VA) Wisp", 2, 0, "va", dict(legs="lg_wisp", core="cr_filament", head="hd_pin", arms="am_reed", booster="bs_flare",
                                          r_arm="mechframe:wa_rifle", l_arm="mechframe:wa_blade")),
    "gnat": ("VA) Gnat", 2, 0, "va", dict(legs="lg_gnat", core="cr_filament", head="hd_pin", arms="am_reed", booster="bs_flare",
                                          r_arm="mechframe:wa_mg", l_arm="mechframe:wa_rifle")),
    "skiff": ("VA) Skiff", 2, 0, "va", dict(legs="lg_skiff", core="cr_filament", head="hd_pin", arms="am_reed", booster="bs_flare",
                                            r_arm="mechframe:wa_rifle", l_arm="mechframe:wa_rifle", l_back="mechframe:wb_missile")),
    "raven": ("CV) Raven", 3, 0, "cv", dict(legs="lg_raven", core="cr_facet", head="hd_slit", arms="am_shard", booster="bs_shroud",
                                            r_arm="mechframe:wa_laser", l_arm="mechframe:wa_rifle", r_back="mechframe:wb_missile")),
    "recluse": ("CV) Recluse", 3, 0, "cv", dict(legs="lg_recluse", core="cr_facet", head="hd_slit", arms="am_shard", booster="bs_shroud",
                                                r_arm="mechframe:wa_laser", l_arm="mechframe:wa_laser", l_back="mechframe:wb_gatling")),
    "lynx": ("CV) Lynx", 3, 0, "cv", dict(legs="lg_lynx", core="cr_facet", head="hd_slit", arms="am_shard", booster="bs_shroud",
                                          r_arm="mechframe:wa_mg", l_arm="mechframe:wa_laser", r_back="mechframe:wb_cannon")),
    "corvus": ("CV) Corvus", 5, 1, "cv", dict(legs="lg_corvus", core="cr_corvus", head="hd_corvus", arms="am_corvus",
                                              booster="bs_corvus", r_arm="mechframe:wa_laser", l_arm="mechframe:wa_rifle",
                                              r_back="mechframe:wb_missile")),
    "colossus": ("TH) Colossus", 4, 0, "th", dict(legs="lg_colossus", core="cr_keep", head="hd_bascinet", arms="am_gauntlet",
                                                  booster="bs_furnace", r_arm="mechframe:wa_bazooka", l_arm="mechframe:wa_mg",
                                                  r_back="mechframe:wb_cannon", l_back="mechframe:wb_cannon")),
    "mammoth": ("TH) Mammoth", 4, 0, "th", dict(legs="lg_mammoth", core="cr_keep", head="hd_bascinet", arms="am_gauntlet",
                                                booster="bs_furnace", r_arm="mechframe:wa_mg", l_arm="mechframe:wa_bazooka",
                                                r_back="mechframe:wb_gatling", l_back="mechframe:wb_cannon")),
    "bulwark": ("TH) Bulwark", 4, 0, "th", dict(legs="lg_bulwark", core="cr_keep", head="hd_bascinet", arms="am_gauntlet",
                                                booster="bs_furnace", r_arm="mechframe:wa_mg", l_arm="mechframe:wa_bazooka",
                                                r_back="mechframe:wb_cannon", l_back="mechframe:wb_missile")),
    "condor": ("TH) Condor", 5, 1, "th", dict(legs="lg_condor", core="cr_condor", head="hd_condor", arms="am_condor",
                                              booster="bs_condor", r_arm="mechframe:wa_mg", l_arm="mechframe:wa_bazooka",
                                              r_back="mechframe:wb_cannon", l_back="mechframe:wb_missile")),
    "kite": ("VA) Kite", 4, 1, "va", dict(legs="lg_kite", core="cr_kite", head="hd_kite", arms="am_kite", booster="bs_kite",
                                          r_arm="mechframe:wa_gunpod", l_arm="mechframe:wa_gunpod", l_back="mechframe:wb_missile")),
}


# Orrery's special-standard parts have no stock frame; these combinations are only rendered to check
# how they sit on other makers' frames (not written as presets).
DEMOS = {
    "ox_halo": ("ox", dict(legs="lg_halo", core="cr_facet", head="hd_armillary", arms="am_arbalest", booster="bs_comet",
                           r_arm="wi_arbalest", l_arm="wi_arbalest")),
    "ox_pile": ("ox", dict(legs="lg_raven", core="cr_facet", head="hd_slit", arms="am_shard", booster="bs_comet",
                           r_arm="mechframe:wa_rifle", l_arm="wm_piledriver")),
    "ox_tank": ("ox", dict(legs="lg_halo", core="cr_keep", head="hd_bascinet", arms="am_gauntlet", booster="bs_furnace",
                           r_arm="mechframe:wa_mg", l_arm="wm_piledriver", r_back="mechframe:wb_cannon")),
}


def full(pid):
    return pid if ":" in pid else ref(pid)


def preset_parts(combo, built):
    out = {}
    for mount, pid in combo.items():
        if ":" in pid:
            out[mount] = mg.load_part(pid.split(":")[1])
        else:
            out[mount] = built[pid]
    return out


def write_presets(built, only=None):
    for pid, (name, tier, flare, maker, combo) in PRESETS.items():
        if only and maker not in only:
            continue
        core = combo["core"]
        obj = {
            "entity_type": "mechframe:mech",
            "model": f"{NS}:models/obj/parts/{core}.obj",
            "texture": f"{NS}:textures/vehicle/part/{core}.png",
            "width": 1.8, "height": 4.0,
            "spawn_item": {"display_name": name, "tier": tier},
            "hud": "mech_hud",
            "engine_sound": "mech_engine",
            "engine_sound_volume": 1.2,
            "weight_type": "car",
            "max_health": 1000,
            "armor_damage_factor": 0.8,
            "armor_min_damage": 1.0,
            "armor_max_damage": 260,
            "damage_factor": 1.0,
            "fuel_consumption": 0.25,
            "inventory_size": 18,
            "submerged_damage_height": 3.0,
            "flare_type": flare,
            "passenger_display": {"hide_entity": True},
            "mechframe": {"assembly": {k: full(v) for k, v in combo.items()}},
        }
        mg_write(os.path.join(DATA, "vehicles", pid + ".json"), obj)


def mg_write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def look_presets(built, only=None, tf=True):
    problems = []
    items = [(pid, maker, combo) for pid, (name, tier, flare, maker, combo) in PRESETS.items()]
    items += [(pid, maker, combo) for pid, (maker, combo) in DEMOS.items()]
    for pid, maker, combo in items:
        if only and maker not in only:
            continue
        parts = preset_parts(combo, built)
        res = lookmod.look(pid, parts, tf=tf)
        print(pid, {k: v for k, v in res.items() if k != "img"})
        if res["tris"] > 5000:
            problems.append(f"{pid}: {res['tris']} tris")
        for k, v in res.items():
            if k.startswith("floating") and v:
                problems.append(f"{pid} {k}: {v}")
    return problems


WEAPONS = {
    # The rail built into the Orrery Arbalest arms (one file for both arms; the mod aliases it per mount).
    "mfx_arbalest_rail": dict(DisplayName="WI-X2 Arbalest Rail", Type="MachineGun2", Power=95, Acceleration=16.0, Gravity=0.0,
                              Accuracy=0.15, Round=4, ReloadTime=90, Delay=16, MaxAmmo=48, SuppliedNum=4,
                              Item=["2, iron_ingot", "2, prismarine_crystals"], Sound="mf_laser", SoundVolume=2.6,
                              ModelBullet="mf_laser", Recoil=1.2, AddMuzzleFlash="0.9, 0.4, 1, 200, 90, 255, 230"),
}


def write_weapons():
    d = os.path.join(ASSETS, "weapons")
    os.makedirs(d, exist_ok=True)
    for name, keys in WEAPONS.items():
        lines = [f"; Mech Frame Expansion 1 - {keys['DisplayName']}"]
        for k, v in keys.items():
            for item in (v if isinstance(v, list) else [v]):
                lines.append(f"{k} = {item}")
        with open(os.path.join(d, name + ".txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


def main():
    args = sys.argv[1:]
    previews = "--no-preview" not in args
    if "--look" in args:
        makers = args[args.index("--look") + 1:]
        built, owner = build_all(makers)
        probs = look_presets(built, makers)
        print("problems:", probs)
        return
    built, owner = build_all()
    report = []
    for pid, (m, meta) in built.items():
        meta = dict(meta)
        if not meta.get("no_recipe"):
            meta["recipe"] = recipe_for(meta, owner[pid])
        meta.pop("no_recipe", None)
        report.append(export(pid, m, meta, previews=previews))
    bad = [r for r in report if r["issues"]]
    print(f"{len(report)} parts, {sum(r['tris'] for r in report)} tris, issues in: {[(r['id'], r['first_issue']) for r in bad]}")
    write_presets(built)
    write_weapons()
    shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), "README_pack.md"), os.path.join(PACK, "README.md"))
    probs = look_presets(built, tf=True) if previews else []
    if probs:
        raise SystemExit("assembly problems: " + "; ".join(probs))


if __name__ == "__main__":
    main()
