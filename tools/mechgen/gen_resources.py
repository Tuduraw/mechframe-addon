"""Everything that isn't a part: presets (vehicle JSONs), weapon files, sounds.json, language files,
item/block models and textures, loot table, mod icon and the HUD script."""
import json
import os

import numpy as np
from PIL import Image, ImageDraw

from mg import ASSETS, DATA
from build_all import PRESETS

PRESET_INFO = {
    # id: (display name, tier, flare_type)
    "striker": ("KD) Striker", 1, 0),
    "hopper": ("KD) Hopper", 2, 0),
    "rampart": ("GW) Rampart", 2, 0),
    "bastion": ("GW) Bastion", 3, 0),
    "weaver": ("AR) Weaver", 3, 0),
    "strider": ("AR) Strider", 4, 0),
    "falcon": ("KD) Falcon", 5, 1),
    "swallow": ("KD) Swallow", 5, 1),
    "manta": ("KD) Manta", 5, 1),
}

WEAPONS = {
    "mf_burst_rifle": dict(DisplayName="WR-K1 Burst Rifle", Type="MachineGun2", Power=28, Acceleration=8.0, Gravity=-0.01,
                           Accuracy=0.8, Round=30, ReloadTime=60, Delay=3, MaxAmmo=600, SuppliedNum=30,
                           Item=["3, iron_ingot", "1, gunpowder"], Sound="mf_rifle", SoundVolume=2.5, ModelBullet="mf_shell",
                           Recoil=0.6, AddMuzzleFlash="0.7, 0.3, 1, 200, 255, 230, 190"),
    "mf_assault_mg": dict(DisplayName="WR-G2 Assault MG", Type="MachineGun2", Power=14, Acceleration=7.0, Gravity=-0.01,
                          Accuracy=2.0, Round=80, ReloadTime=80, Delay=1, MaxAmmo=960, SuppliedNum=80,
                          Item=["4, iron_ingot", "2, gunpowder"], Sound="mf_mg", SoundVolume=2.2, ModelBullet="mf_shell",
                          Recoil=0.3, AddMuzzleFlash="0.6, 0.25, 1, 200, 255, 220, 170"),
    "mf_bazooka": dict(DisplayName="WR-G3 Bazooka", Type="MachineGun2", Power=110, Explosion=3.0, Acceleration=4.0,
                       Gravity=-0.04, Accuracy=0.5, Round=6, ReloadTime=100, Delay=30, MaxAmmo=36, SuppliedNum=6,
                       Item=["4, iron_ingot", "4, gunpowder"], Sound="mf_bazooka", SoundVolume=3.0, ModelBullet="mf_missile",
                       Recoil=1.5, AddMuzzleFlash="1.2, 0.5, 1, 200, 255, 200, 140"),
    "mf_pulse_laser": dict(DisplayName="WR-A4 Pulse Laser", Type="MachineGun2", Power=45, Acceleration=14.0, Gravity=0.0,
                           Accuracy=0.3, Round=20, ReloadTime=70, Delay=6, MaxAmmo=400, SuppliedNum=20,
                           Item=["2, iron_ingot", "3, redstone"], Sound="mf_laser", SoundVolume=2.0, ModelBullet="mf_laser",
                           AddMuzzleFlash="0.6, 0.3, 1, 200, 255, 90, 80"),
    "mf_gun_pod": dict(DisplayName="WR-KF Gun Pod", Type="MachineGun2", Power=18, Acceleration=9.0, Gravity=-0.01,
                       Accuracy=1.2, Round=60, ReloadTime=70, Delay=1, MaxAmmo=900, SuppliedNum=60,
                       Item=["3, iron_ingot", "2, gunpowder"], Sound="mf_gunpod", SoundVolume=2.2, ModelBullet="mf_shell",
                       Recoil=0.3, AddMuzzleFlash="0.6, 0.25, 1, 200, 255, 230, 190"),
    "mf_missile_pod": dict(DisplayName="WB-K1 Missile Pod", Type="Missile", Power=60, Explosion=2.5, Acceleration=2.6,
                           Gravity=-0.01, LockTime=12, LockRange=140, Round=8, ReloadTime=160, Delay=6, MaxAmmo=64,
                           SuppliedNum=8, Item=["4, iron_ingot", "2, gunpowder", "1, redstone"], Sound="mf_missile",
                           SoundVolume=2.5, ModelBullet="mf_missile"),
    "mf_shoulder_cannon": dict(DisplayName="WB-G2 Shoulder Cannon", Type="MachineGun2", Power=160, Explosion=3.5,
                               Acceleration=7.0, Gravity=-0.03, Accuracy=0.3, Round=4, ReloadTime=120, Delay=40, MaxAmmo=40,
                               SuppliedNum=4, Item=["5, iron_ingot", "5, gunpowder"], Sound="mf_cannon", SoundVolume=4.0,
                               ModelBullet="mf_shell", Recoil=2.0, AddMuzzleFlash="1.6, 0.7, 1, 200, 255, 210, 160"),
    "mf_back_gatling": dict(DisplayName="WB-A3 Back Gatling", Type="MachineGun2", Power=9, Acceleration=8.0, Gravity=-0.01,
                            Accuracy=2.5, Round=200, ReloadTime=100, Delay=0, MaxAmmo=2000, SuppliedNum=200,
                            Item=["4, iron_ingot", "3, gunpowder"], Sound="mf_gatling", SoundVolume=2.0, ModelBullet="mf_shell",
                            AddMuzzleFlash="0.5, 0.25, 1, 200, 255, 220, 170"),
}

SOUND_EVENTS = ["quick_boost", "land", "step", "transform", "brake", "jump"]


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def presets():
    for pid, combo in PRESETS.items():
        name, tier, flare = PRESET_INFO[pid]
        core = combo["core"]
        obj = {
            "entity_type": "mechframe:mech",
            "model": f"mechframe:models/obj/parts/{core}.obj",
            "texture": f"mechframe:textures/part/{core}.png",
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
            "mechframe": {"assembly": {k: "mechframe:" + v for k, v in combo.items()}},
        }
        write_json(os.path.join(DATA, "vehicles", pid + ".json"), obj)


def weapons():
    d = os.path.join(ASSETS, "weapons")
    os.makedirs(d, exist_ok=True)
    for name, keys in WEAPONS.items():
        lines = [f"; Mech Frame Addon - {keys['DisplayName']}"]
        for k, v in keys.items():
            for item in (v if isinstance(v, list) else [v]):
                lines.append(f"{k} = {item}")
        with open(os.path.join(d, name + ".txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


def sounds_json():
    obj = {e: {"sounds": ["mechframe:" + e], "subtitle": "subtitles.mechframe." + e} for e in SOUND_EVENTS}
    write_json(os.path.join(ASSETS, "sounds.json"), obj)


# ------------------------------------------------------------------------------- pixel icons

def icon(draw_fn, size=16):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im))
    return im


def save_icon(name, im, folder="item"):
    p = os.path.join(ASSETS, "textures", folder, name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)


def icons():
    W, G, D, C, Y = (220, 224, 228, 255), (120, 126, 134, 255), (52, 56, 62, 255), (60, 200, 230, 255), (230, 180, 40, 255)
    shapes = {
        "part_head": lambda d: (d.rectangle([4, 4, 11, 11], fill=W, outline=D), d.rectangle([5, 7, 10, 8], fill=C),
                                d.line([5, 2, 6, 4], fill=D), d.line([10, 2, 9, 4], fill=D), d.rectangle([6, 12, 9, 13], fill=G)),
        "part_core": lambda d: (d.polygon([(3, 3), (12, 3), (13, 6), (12, 12), (3, 12), (2, 6)], fill=W, outline=D),
                                d.rectangle([6, 5, 9, 9], fill=G), d.rectangle([7, 12, 8, 14], fill=D)),
        "part_arms": lambda d: (d.rectangle([2, 2, 6, 6], fill=W, outline=D), d.rectangle([3, 6, 5, 10], fill=G),
                                d.rectangle([3, 10, 12, 12], fill=W, outline=D), d.rectangle([12, 10, 14, 13], fill=D)),
        "part_legs": lambda d: (d.rectangle([4, 1, 11, 4], fill=G, outline=D), d.rectangle([4, 4, 6, 9], fill=W),
                                d.rectangle([9, 4, 11, 9], fill=W), d.rectangle([4, 9, 6, 13], fill=W, outline=D),
                                d.rectangle([9, 9, 11, 13], fill=W, outline=D), d.rectangle([3, 13, 7, 14], fill=D),
                                d.rectangle([8, 13, 12, 14], fill=D)),
        "part_booster": lambda d: (d.rectangle([4, 2, 11, 10], fill=G, outline=D), d.polygon([(5, 10), (7, 10), (8, 14), (4, 14)], fill=D),
                                   d.polygon([(9, 10), (11, 10), (12, 14), (8, 14)], fill=D), d.point([(6, 13), (10, 13)], fill=C)),
        "part_weapon": lambda d: (d.rectangle([1, 6, 10, 9], fill=G, outline=D), d.rectangle([10, 7, 15, 8], fill=D),
                                  d.rectangle([4, 9, 6, 13], fill=D), d.rectangle([3, 4, 7, 5], fill=Y)),
    }
    for name, fn in shapes.items():
        save_icon(name, icon(fn))
    for tier in range(1, 6):
        tint = [(180, 186, 192), (110, 170, 110), (90, 140, 210), (170, 100, 210), (230, 170, 50)][tier - 1]

        def spawner(d, tint=tint, tier=tier):
            d.rectangle([2, 2, 13, 13], fill=tint + (255,), outline=D)
            d.rectangle([5, 4, 10, 7], fill=W)
            d.rectangle([4, 8, 6, 12], fill=D)
            d.rectangle([9, 8, 11, 12], fill=D)
            for i in range(tier):
                d.point((3 + i * 2, 14), fill=Y)
        save_icon(f"mech_spawner_t{tier}", icon(spawner))
    # garage block faces
    side = Image.new("RGBA", (16, 16), (110, 114, 120, 255))
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=(60, 62, 66, 255))
    for x in range(1, 15, 2):
        d.line([x, 4, x, 14], fill=(86, 90, 96, 255))
    d.rectangle([0, 0, 15, 2], fill=(230, 180, 40, 255))
    for x in range(0, 16, 4):
        d.polygon([(x, 0), (x + 2, 0), (x, 2)], fill=(40, 40, 40, 255))
    save_icon("garage_side", side, "block")
    top = Image.new("RGBA", (16, 16), (96, 100, 106, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=(60, 62, 66, 255))
    d.rectangle([3, 3, 12, 12], outline=(230, 180, 40, 255))
    d.line([3, 3, 12, 12], fill=(70, 72, 78, 255))
    d.line([12, 3, 3, 12], fill=(70, 72, 78, 255))
    save_icon("garage_top", top, "block")
    # fabricator faces: a workbench with a robotic arm gantry
    side = Image.new("RGBA", (16, 16), (96, 102, 110, 255))
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=(56, 58, 62, 255))
    d.rectangle([0, 0, 15, 3], fill=(60, 200, 230, 255))
    d.rectangle([2, 6, 13, 13], fill=(70, 74, 80, 255), outline=(48, 50, 54, 255))
    d.line([4, 12, 8, 7], fill=(230, 180, 40, 255))
    d.line([8, 7, 11, 9], fill=(230, 180, 40, 255))
    d.point([(11, 10)], fill=(255, 120, 60, 255))
    save_icon("fabricator_side", side, "block")
    top = Image.new("RGBA", (16, 16), (120, 126, 134, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=(56, 58, 62, 255))
    d.rectangle([2, 2, 13, 13], outline=(60, 200, 230, 255))
    for k in range(4, 13, 3):
        d.line([2, k, 13, k], fill=(100, 106, 114, 255))
    d.rectangle([6, 6, 9, 9], fill=(48, 50, 54, 255))
    save_icon("fabricator_top", top, "block")
    # command post faces: a hardened red-striped console with a radar dish on top
    side = Image.new("RGBA", (16, 16), (70, 72, 78, 255))
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=(36, 36, 40, 255))
    for x in range(-4, 16, 4):
        d.polygon([(x, 15), (x + 2, 15), (x + 6, 11), (x + 4, 11)], fill=(200, 50, 40, 255))
    d.rectangle([3, 3, 12, 8], fill=(30, 34, 40, 255), outline=(20, 20, 24, 255))
    d.line([4, 6, 7, 5], fill=(255, 90, 60, 255))
    d.line([7, 5, 9, 7], fill=(255, 90, 60, 255))
    d.point([(10, 4), (11, 4)], fill=(255, 210, 60, 255))
    save_icon("command_post_side", side, "block")
    top = Image.new("RGBA", (16, 16), (84, 86, 92, 255))
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=(36, 36, 40, 255))
    d.ellipse([3, 3, 12, 12], outline=(200, 50, 40, 255))
    d.ellipse([6, 6, 9, 9], fill=(255, 90, 60, 255))
    d.line([8, 8, 13, 3], fill=(220, 220, 220, 255))
    save_icon("command_post_top", top, "block")

    def terminal(d):
        d.rectangle([3, 1, 12, 14], fill=(70, 74, 80, 255), outline=D)
        d.rectangle([4, 2, 11, 8], fill=(30, 40, 46, 255))
        d.line([5, 6, 7, 4], fill=(90, 230, 120, 255))
        d.line([7, 4, 10, 6], fill=(90, 230, 120, 255))
        d.point([(5, 10), (7, 10), (9, 10), (5, 12), (7, 12), (9, 12)], fill=Y)
        d.line([12, 0, 12, 2], fill=W)
    save_icon("command_terminal", icon(terminal))

    # mod icon
    ic = Image.new("RGBA", (128, 128), (30, 34, 40, 255))
    d = ImageDraw.Draw(ic)
    d.polygon([(44, 20), (84, 20), (92, 34), (84, 62), (44, 62), (36, 34)], fill=(214, 218, 222, 255))
    d.rectangle([54, 8, 74, 22], fill=(214, 218, 222, 255))
    d.rectangle([56, 13, 72, 16], fill=(60, 200, 230, 255))
    d.rectangle([46, 62, 58, 110], fill=(140, 146, 152, 255))
    d.rectangle([70, 62, 82, 110], fill=(140, 146, 152, 255))
    d.rectangle([40, 108, 60, 118], fill=(54, 66, 92, 255))
    d.rectangle([68, 108, 88, 118], fill=(54, 66, 92, 255))
    d.rectangle([16, 30, 34, 44], fill=(214, 218, 222, 255))
    d.rectangle([94, 30, 112, 44], fill=(214, 218, 222, 255))
    d.rectangle([94, 44, 104, 60], fill=(54, 66, 92, 255))
    d.rectangle([96, 58, 126, 64], fill=(82, 86, 92, 255))
    ic.save(os.path.join(ASSETS, "icon.png"))


def models():
    for slot in ("head", "core", "arms", "legs", "booster", "weapon"):
        write_json(os.path.join(ASSETS, "items", f"part_{slot}.json"),
                   {"model": {"type": "minecraft:model", "model": f"mechframe:item/part_{slot}"}})
        write_json(os.path.join(ASSETS, "models", "item", f"part_{slot}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"mechframe:item/part_{slot}"}})
    for tier in range(1, 6):
        write_json(os.path.join(ASSETS, "items", f"mech_spawner_t{tier}.json"),
                   {"model": {"type": "minecraft:model", "model": f"mechframe:item/mech_spawner_t{tier}"}})
        write_json(os.path.join(ASSETS, "models", "item", f"mech_spawner_t{tier}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"mechframe:item/mech_spawner_t{tier}"}})
    write_json(os.path.join(ASSETS, "blockstates", "garage.json"), {"variants": {"": {"model": "mechframe:block/garage"}}})
    write_json(os.path.join(ASSETS, "models", "block", "garage.json"),
               {"parent": "minecraft:block/cube_bottom_top",
                "textures": {"side": "mechframe:block/garage_side", "top": "mechframe:block/garage_top",
                             "bottom": "mechframe:block/garage_top"}})
    write_json(os.path.join(ASSETS, "items", "garage.json"), {"model": {"type": "minecraft:model", "model": "mechframe:block/garage"}})
    write_json(os.path.join(DATA, "loot_table", "blocks", "garage.json"),
               {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "mechframe:garage"}],
                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "recipe", "garage.json"),
               {"type": "minecraft:crafting_shaped", "category": "misc",
                "key": {"I": "minecraft:iron_block", "C": "minecraft:crafting_table", "R": "minecraft:redstone_block",
                        "A": "minecraft:anvil"},
                "pattern": ["IRI", "ICI", "IAI"], "result": {"id": "mechframe:garage", "count": 1}})
    # Mech Fabricator: a workbench block; the parts are made in it (data/mechframe/mech_recipe).
    write_json(os.path.join(ASSETS, "blockstates", "fabricator.json"), {"variants": {"": {"model": "mechframe:block/fabricator"}}})
    write_json(os.path.join(ASSETS, "models", "block", "fabricator.json"),
               {"parent": "minecraft:block/cube_bottom_top",
                "textures": {"side": "mechframe:block/fabricator_side", "top": "mechframe:block/fabricator_top",
                             "bottom": "mechframe:block/garage_top"}})
    write_json(os.path.join(ASSETS, "items", "fabricator.json"), {"model": {"type": "minecraft:model", "model": "mechframe:block/fabricator"}})
    write_json(os.path.join(DATA, "loot_table", "blocks", "fabricator.json"),
               {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "mechframe:fabricator"}],
                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write_json(os.path.join(DATA, "recipe", "fabricator.json"),
               {"type": "minecraft:crafting_shaped", "category": "misc",
                "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone_block", "C": "minecraft:crafting_table",
                        "P": "minecraft:piston", "S": "minecraft:smooth_stone"},
                "pattern": ["IRI", "PCP", "SSS"], "result": {"id": "mechframe:fabricator", "count": 1}})
    # Defence AI: the outposts' command post (no drop, no recipe) and the command terminal.
    write_json(os.path.join(ASSETS, "blockstates", "command_post.json"), {"variants": {"": {"model": "mechframe:block/command_post"}}})
    write_json(os.path.join(ASSETS, "models", "block", "command_post.json"),
               {"parent": "minecraft:block/cube_bottom_top",
                "textures": {"side": "mechframe:block/command_post_side", "top": "mechframe:block/command_post_top",
                             "bottom": "mechframe:block/garage_top"}})
    write_json(os.path.join(ASSETS, "items", "command_post.json"), {"model": {"type": "minecraft:model", "model": "mechframe:block/command_post"}})
    write_json(os.path.join(DATA, "loot_table", "blocks", "command_post.json"), {"type": "minecraft:block", "pools": []})
    write_json(os.path.join(ASSETS, "items", "command_terminal.json"),
               {"model": {"type": "minecraft:model", "model": "mechframe:item/command_terminal"}})
    write_json(os.path.join(ASSETS, "models", "item", "command_terminal.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "mechframe:item/command_terminal"}})
    write_json(os.path.join(DATA, "mech_recipe", "command_terminal.json"),
               {"category": "items", "result": {"item": "mechframe:command_terminal"},
                "ingredients": [{"item": "minecraft:compass", "count": 1}, {"item": "minecraft:redstone", "count": 4},
                                {"item": "minecraft:iron_ingot", "count": 3}, {"item": "minecraft:glass_pane", "count": 1}]})
    # Frame kits (tiered spawners: pick any frame up to that tier) are fabricated too.
    kits = {1: [("minecraft:iron_block", 4), ("minecraft:redstone", 8)],
            2: [("minecraft:iron_block", 6), ("minecraft:redstone_block", 2), ("minecraft:quartz", 8)],
            3: [("minecraft:iron_block", 8), ("minecraft:gold_block", 2), ("minecraft:redstone_block", 3)],
            4: [("minecraft:iron_block", 8), ("minecraft:diamond", 4), ("minecraft:redstone_block", 4)],
            5: [("minecraft:iron_block", 10), ("minecraft:diamond", 8), ("minecraft:netherite_ingot", 1)]}
    for tier, ing in kits.items():
        write_json(os.path.join(DATA, "mech_recipe", f"mech_spawner_t{tier}.json"),
                   {"category": "frames", "order": tier, "result": {"item": f"mechframe:mech_spawner_t{tier}"},
                    "ingredients": [{"item": i, "count": n} for i, n in ing]})
    write_json(os.path.join(DATA, "tags", "block", "mineable", "pickaxe.json"), {"values": ["mechframe:garage", "mechframe:fabricator", "mechframe:command_post"]})


def lang():
    en = {
        "itemGroup.mechframe.mechs": "Mech Frames",
        "item.mechframe.category.mech": "Mech Frame",
        "entity.mechframe.mech": "Mech Frame",
        "block.mechframe.garage": "Mech Garage",
        "block.mechframe.fabricator": "Mech Fabricator",
        "block.mechframe.command_post": "Mech Command Post",
        "item.mechframe.command_terminal": "Command Terminal",
        "entity.mechframe.unit.enemy": "Hostile Unit %s",
        "entity.mechframe.unit.friendly": "Allied Unit %s",
        "message.mechframe.faction.enemy": "Enemy",
        "message.mechframe.faction.friendly": "Friendly",
        "message.mechframe.post.status": "%s command post: %s/%s units deployed, next deployment in %s",
        "message.mechframe.post.switched": "Command post switched to: %s",
        "message.mechframe.command.guard": "%s: base defence - guarding this area",
        "message.mechframe.command.escort": "%s: wingman - following you",
        "message.mechframe.command.off": "%s: orders cancelled",
        "message.mechframe.command.enemy": "Enemy frames do not take your orders",
        "message.mechframe.command.not_yours": "This frame takes orders from someone else",
        "gui.mechframe.fab.search": "Search...",
        "gui.mechframe.fab.craft": "Fabricate",
        "gui.mechframe.fab.materials": "Materials",
        "gui.mechframe.fab.pick": "Pick something to make",
        "gui.mechframe.fab.creative": "Creative: no materials needed",
        "gui.mechframe.fab.category.frames": "Frame kits",
        "gui.mechframe.fab.category.head": "Heads",
        "gui.mechframe.fab.category.core": "Cores",
        "gui.mechframe.fab.category.arms": "Arms",
        "gui.mechframe.fab.category.legs": "Legs",
        "gui.mechframe.fab.category.booster": "Boosters",
        "gui.mechframe.fab.category.weapon": "Weapons",
        "gui.mechframe.fab.category.items": "Items",
        "key.category.mechframe.mech": "Mech Frame Addon",
        "key.mechframe.boost": "Boost (hold)",
        "key.mechframe.quick_boost": "Quick Boost",
        "key.mechframe.transform": "Transform",
        "key.mechframe.fire_left": "Left Trigger",
        "key.mechframe.fire_right": "Right Trigger",
        "key.mechframe.swap_left": "Swap Left Weapon (Arm / Back)",
        "key.mechframe.swap_right": "Swap Right Weapon (Arm / Back)",
        "gui.mechframe.no_frame": "No frame within 16 blocks",
        "gui.mechframe.stock": "Stock",
        "gui.mechframe.empty": "empty",
        "gui.mechframe.reset_stock": "Reset to stock",
        "gui.mechframe.transform_yes": "Transform: OK",
        "gui.mechframe.transform_no": "Transform: -",
        "message.mechframe.garage.no_frame": "That frame isn't parked by this garage",
        "message.mechframe.garage.destroyed": "The frame is destroyed",
        "message.mechframe.garage.occupied": "Get everyone out of the frame first",
        "message.mechframe.garage.required": "That mount can't be left empty",
        "message.mechframe.garage.incompatible": "That part doesn't fit this mount",
        "message.mechframe.garage.integrated": "The fitted arms have a weapon built in here",
        "gui.mechframe.integrated": "Built-in",
        "tooltip.mechframe.integrated": "Built-in weapons: %s",
        "tooltip.mechframe.mount.integrated": "Built into arms only",
        "tooltip.mechframe.legtype.hover": "Hover",
        "tooltip.mechframe.melee_thrust": "Thrust",
        "message.mechframe.garage.not_stored": "That part isn't stored in this garage",
        "message.mechframe.garage.full": "The garage has no room for the removed part",
        "message.mechframe.garage.unknown_part": "The installed part is no longer defined, so it can't be returned to the garage",
        "tooltip.mechframe.unknown_part": "Unknown part: %s",
        "tooltip.mechframe.weight": "Weight", "tooltip.mechframe.armor": "Armour",
        "tooltip.mechframe.lock": "Lock range / cone", "tooltip.mechframe.en": "EN",
        "tooltip.mechframe.arm_load": "Arm load", "tooltip.mechframe.leg_load": "Load limit",
        "tooltip.mechframe.speed": "Walk", "tooltip.mechframe.boost": "Boost", "tooltip.mechframe.qb": "Quick boost",
        "tooltip.mechframe.transformable": "Transformable core",
        "tooltip.mechframe.transform_set": "Transform set: %s",
        "tooltip.mechframe.mount.arm": "Arm weapon", "tooltip.mechframe.mount.back": "Back weapon",
        "tooltip.mechframe.mount.any": "Arm or back weapon",
        "tooltip.mechframe.melee": "Melee: %s dmg, reach %sm",
    }
    ja = {
        "itemGroup.mechframe.mechs": "メックフレーム",
        "item.mechframe.category.mech": "メックフレーム",
        "entity.mechframe.mech": "メックフレーム",
        "block.mechframe.garage": "メックガレージ",
        "block.mechframe.fabricator": "メック製作台",
        "block.mechframe.command_post": "機体指令所",
        "item.mechframe.command_terminal": "指揮端末",
        "entity.mechframe.unit.enemy": "敵機 %s",
        "entity.mechframe.unit.friendly": "味方機 %s",
        "message.mechframe.faction.enemy": "エネミー",
        "message.mechframe.faction.friendly": "フレンドリー",
        "message.mechframe.post.status": "%s指令所: 配備 %s/%s機、次の配備まで %s",
        "message.mechframe.post.switched": "指令所を切り替えました: %s",
        "message.mechframe.command.guard": "%s: 基地防衛 - この周辺を警備します",
        "message.mechframe.command.escort": "%s: 僚機 - あなたに随伴します",
        "message.mechframe.command.off": "%s: 命令を解除しました",
        "message.mechframe.command.enemy": "敵機には命令できません",
        "message.mechframe.command.not_yours": "この機体は他のプレイヤーの命令で動いています",
        "gui.mechframe.fab.search": "検索...",
        "gui.mechframe.fab.craft": "製作",
        "gui.mechframe.fab.materials": "材料",
        "gui.mechframe.fab.pick": "作るものを選んでください",
        "gui.mechframe.fab.creative": "クリエイティブ: 材料なしで作れます",
        "gui.mechframe.fab.category.frames": "機体キット",
        "gui.mechframe.fab.category.head": "頭部",
        "gui.mechframe.fab.category.core": "コア",
        "gui.mechframe.fab.category.arms": "腕部",
        "gui.mechframe.fab.category.legs": "脚部",
        "gui.mechframe.fab.category.booster": "ブースター",
        "gui.mechframe.fab.category.weapon": "武器",
        "gui.mechframe.fab.category.items": "アイテム",
        "key.category.mechframe.mech": "Mech Frame Addon",
        "key.mechframe.boost": "ブースト(押し続け)",
        "key.mechframe.quick_boost": "クイックブースト",
        "key.mechframe.transform": "変形",
        "key.mechframe.fire_left": "左の武器を使う",
        "key.mechframe.fire_right": "右の武器を使う",
        "key.mechframe.swap_left": "左の武器の切り替え(腕/肩)",
        "key.mechframe.swap_right": "右の武器の切り替え(腕/肩)",
        "gui.mechframe.no_frame": "16ブロック以内に機体がありません",
        "gui.mechframe.stock": "標準",
        "gui.mechframe.empty": "なし",
        "gui.mechframe.reset_stock": "標準構成に戻す",
        "gui.mechframe.transform_yes": "変形: 可",
        "gui.mechframe.transform_no": "変形: 不可",
        "message.mechframe.garage.no_frame": "その機体はこのガレージの範囲にありません",
        "message.mechframe.garage.destroyed": "機体が破壊されています",
        "message.mechframe.garage.occupied": "搭乗者を降ろしてください",
        "message.mechframe.garage.required": "この部位は空にできません",
        "message.mechframe.garage.incompatible": "このパーツはこの部位に取り付けられません",
        "message.mechframe.garage.integrated": "取り付け中の腕部には、この部位に武器が内蔵されています",
        "gui.mechframe.integrated": "内蔵",
        "tooltip.mechframe.integrated": "内蔵武器: %s",
        "tooltip.mechframe.mount.integrated": "腕部内蔵専用",
        "tooltip.mechframe.legtype.hover": "ホバー",
        "tooltip.mechframe.melee_thrust": "突き",
        "message.mechframe.garage.not_stored": "そのパーツはガレージにありません",
        "message.mechframe.garage.full": "外したパーツを入れる空きがガレージにありません",
        "message.mechframe.garage.unknown_part": "取り付け中のパーツの定義が見つからないため、ガレージに戻せません",
        "tooltip.mechframe.unknown_part": "不明なパーツ: %s",
        "tooltip.mechframe.weight": "重量", "tooltip.mechframe.armor": "装甲",
        "tooltip.mechframe.lock": "ロック距離 / 範囲", "tooltip.mechframe.en": "EN",
        "tooltip.mechframe.arm_load": "腕部積載", "tooltip.mechframe.leg_load": "積載上限",
        "tooltip.mechframe.speed": "歩行", "tooltip.mechframe.boost": "ブースト", "tooltip.mechframe.qb": "クイックブースト",
        "tooltip.mechframe.transformable": "変形対応コア",
        "tooltip.mechframe.transform_set": "変形セット: %s",
        "tooltip.mechframe.mount.arm": "腕武器", "tooltip.mechframe.mount.back": "背部武器",
        "tooltip.mechframe.mount.any": "腕・背部武器",
        "tooltip.mechframe.melee": "近接: 威力%s 間合い%sm",
    }
    slots_en = {"head": "Head", "core": "Core", "arms": "Arms", "legs": "Legs", "booster": "Booster", "weapon": "Weapon"}
    slots_ja = {"head": "頭部", "core": "コア", "arms": "腕部", "legs": "脚部", "booster": "ブースター", "weapon": "武器"}
    for k in slots_en:
        en[f"item.mechframe.part_{k}"] = f"Mech Part ({slots_en[k]})"
        ja[f"item.mechframe.part_{k}"] = f"メックパーツ({slots_ja[k]})"
        en[f"tooltip.mechframe.slot.{k}"] = slots_en[k]
        ja[f"tooltip.mechframe.slot.{k}"] = slots_ja[k]
    mounts_en = {"head": "Head", "core": "Core", "arms": "Arms", "legs": "Legs", "booster": "Booster",
                 "r_arm": "R-Arm", "l_arm": "L-Arm", "r_back": "R-Back", "l_back": "L-Back"}
    mounts_ja = {"head": "頭部", "core": "コア", "arms": "腕部", "legs": "脚部", "booster": "ブースター",
                 "r_arm": "右腕", "l_arm": "左腕", "r_back": "右背", "l_back": "左背"}
    for k in mounts_en:
        en[f"mount.mechframe.{k}"] = mounts_en[k]
        ja[f"mount.mechframe.{k}"] = mounts_ja[k]
    legs_en = {"biped": "Biped", "reverse_joint": "Reverse joint", "tank": "Tank", "quad": "Quad", "hexapod": "Hexapod"}
    legs_ja = {"biped": "二脚", "reverse_joint": "逆関節", "tank": "タンク", "quad": "四脚", "hexapod": "六脚"}
    for k in legs_en:
        en[f"tooltip.mechframe.legtype.{k}"] = legs_en[k]
        ja[f"tooltip.mechframe.legtype.{k}"] = legs_ja[k]
    for tier in range(1, 6):
        en[f"item.mechframe.mech_spawner_t{tier}"] = f"Mech Frame (Tier {tier})"
        ja[f"item.mechframe.mech_spawner_t{tier}"] = f"メックフレーム(ティア{tier})"
    subs_en = {"quick_boost": "Quick boost", "land": "Frame lands", "step": "Frame footstep", "transform": "Frame transforms",
               "brake": "Frame skids", "jump": "Frame jumps"}
    subs_ja = {"quick_boost": "クイックブースト", "land": "機体の着地", "step": "機体の足音", "transform": "機体の変形",
               "brake": "機体の急停止", "jump": "機体のジャンプ"}
    for k in subs_en:
        en[f"subtitles.mechframe.{k}"] = subs_en[k]
        ja[f"subtitles.mechframe.{k}"] = subs_ja[k]
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), en)
    write_json(os.path.join(ASSETS, "lang", "ja_jp.json"), ja)


HUD = r"""; Mech Frame HUD (mechframe) - referenced by every frame preset as "hud": "mech_hud".
; Uses the base mod's variables plus this mod's own mf_* variables (client.hud.MechHud).

; ---- reticle --------------------------------------------------------------------------------
Color = 200, 120, 255, 200
DrawRect = -9, 0, 5, 1
DrawRect = 5, 0, 5, 1
DrawRect = 0, -9, 1, 5
DrawRect = 0, 5, 1, 5
DrawLine = -24, -18, -30, -18, -30, -12
DrawLine = 24, -18, 30, -18, 30, -12
DrawLine = -24, 18, -30, 18, -30, 12
DrawLine = 24, 18, 30, 18, 30, 12

; ---- FCS target ---------------------------------------------------------------------------------
If = mf_soft
Color = 230, 255, 160, 40
DrawCenteredString = 0, -40, "FCS %s  %.0fm", mf_tgt_name, mf_tgt_dist
EndIf
If = mf_lock
Color = 255, 255, 70, 60
DrawCenteredString = 0, -40, "LOCK %s  %.0fm", mf_tgt_name, mf_tgt_dist
EndIf
If = mf_tgt
Color = 140, 40, 40, 40
DrawRect = -30, -30, 60, 3
Color = 230, 255, 90, 70
DrawRect = -30, -30, 60 * mf_tgt_hp_rto, 3
EndIf

; ---- missile lock-on (base mod) -----------------------------------------------------------------
If = sight_type == 2
Color = 160, 40, 40, 40
DrawRect = -40, 26, 80, 3
Color = 230, 255, 200, 60
DrawRect = -40, 26, 80 * lock_progress, 3
EndIf

; ---- EN gauge (bottom centre) -------------------------------------------------------------------
; Laid out for the smallest common GUI (427x240 scaled): everything stays above the hotbar
; (y < 98 in the centre column) and inside x = +-212.
Color = 150, 20, 26, 34
DrawRect = -80, 62, 160, 6
Color = 230, 90, 220, 255
DrawRect = -80, 62, 160 * mf_en_rto, 6
If = mf_overheat
Color = 255, 255, 70, 60
DrawRect = -80, 62, 160 * mf_en_rto, 6
DrawCenteredString = 0, 71, "EN OUT"
EndIf
Color = 220, 200, 230, 240
DrawString = -80, 52, "EN %.0f", mf_en
If = mf_qb_ready
Color = 230, 120, 255, 160
DrawString = 68, 52, "QB"
EndIf
If = mf_boost
Color = 240, 120, 220, 255
DrawCenteredString = 0, 52, "BOOST"
EndIf
If = mf_hover
Color = 240, 120, 220, 255
DrawCenteredString = 0, 42, "HOVER"
EndIf

; ---- frame status (left) --------------------------------------------------------------------------
Color = 220, 200, 230, 240
DrawString = -210, 22, "%s", mf_frame
DrawString = -210, 32, "AP  %d", hp
Color = 150, 20, 26, 34
DrawRect = -210, 42, 80, 3
Color = 230, 120, 230, 140
DrawRect = -210, 42, 80 * hp_rto, 3
Color = 220, 200, 230, 240
DrawString = -210, 48, "SPD %d km/h", speed_kbh
DrawString = -210, 58, "ALT %d", altitude
If = mf_overload
Color = 255, 255, 170, 40
DrawString = -210, 80, "OVERWEIGHT"
EndIf
If = mf_arm_overload
Color = 255, 255, 170, 40
DrawString = -210, 90, "ARM OVERLOAD"
EndIf
If = hp_per < 25
Color = 255, 255, 70, 60
DrawString = -210, 100, "AP CRITICAL"
EndIf

; ---- form ------------------------------------------------------------------------------------------
If = mf_show_mode
Color = 220, 200, 230, 240
DrawCenteredString = 0, -64, "%s", mf_mode
EndIf
If = mf_jet
Color = 220, 200, 230, 240
DrawString = -210, 68, "THR %d%%", throttle * 100
DrawGraduationPitch2 = pitch, roll, 0, 0
EndIf

; ---- weapons (right): slot + ammo, then the weapon name ---------------------------------------------
Color = 220, 200, 230, 240
If = mf_ra_has
DrawString = 98, 22, "%s", mf_ra_slot
DrawString = 98, 31, "%s", mf_ra_name
EndIf
If = mf_la_has
DrawString = 98, 44, "%s", mf_la_slot
DrawString = 98, 53, "%s", mf_la_name
EndIf
If = mf_rb_has
DrawString = 98, 66, "%s", mf_rb_slot
DrawString = 98, 75, "%s", mf_rb_name
EndIf
If = mf_lb_has
DrawString = 98, 88, "%s", mf_lb_slot
DrawString = 98, 97, "%s", mf_lb_name
EndIf
; Which key fires what: each arm's own trigger (Fire Left / Right Arm), the base mod's fire key for the selected weapon.
Color = 255, 255, 200, 80
If = mf_ra_sel
DrawString = 91, 22, ">"
EndIf
If = mf_la_sel
DrawString = 91, 44, ">"
EndIf
If = mf_rb_sel
DrawString = 91, 66, ">"
EndIf
If = mf_lb_sel
DrawString = 91, 88, ">"
EndIf
Color = 255, 140, 220, 255
If = mf_ra_keyed
DrawString = 186, 22, "%s", mf_ra_key
EndIf
If = mf_la_keyed
DrawString = 186, 44, "%s", mf_la_key
EndIf
If = mf_rb_keyed
DrawString = 186, 66, "%s", mf_rb_key
EndIf
If = mf_lb_keyed
DrawString = 186, 88, "%s", mf_lb_key
EndIf
If = reloading
Color = 230, 255, 170, 60
DrawString = 98, 108, "RELOADING"
EndIf
"""


def hud():
    p = os.path.join(ASSETS, "hud", "mech_hud.txt")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(HUD)


if __name__ == "__main__":
    presets()
    weapons()
    sounds_json()
    icons()
    models()
    lang()
    hud()
    print("resources written")
