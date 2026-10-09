"""Mech Frame Expansion 1 "Frontier Frames" (namespace mfx) - shared setup for the pack's part scripts.

The pack is written as a tudursvehiclemod-addons folder (packs/mechframe_expansion1/ at the project
root): assets/mfx (OBJ, textures, lang), data/mfx (mech_parts, vehicles = presets, mech_recipe).
Everything else (shapes, texturing, rig) comes from the base generator (tools/mechgen, mg / rig) and
meshkit.mech."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MECHGEN = os.path.abspath(os.path.join(HERE, "..", ".."))
if MECHGEN not in sys.path:
    sys.path.insert(0, MECHGEN)

import mg  # noqa: E402
from mg import SCHEMES, ROOT  # noqa: E402

NS = "mfx"
PACK_NAME = "mechframe_expansion1"
PACK = os.path.join(ROOT, "packs", PACK_NAME)
ASSETS = os.path.join(PACK, "assets", NS)
DATA = os.path.join(PACK, "data", NS)
PREVIEW = os.path.join(ROOT, "tools", "preview", "mfx")

# ---------------------------------------------------------------------------------- paint schemes
SCHEMES.update({
    # Vesper Aeronautics: ultralight - ivory capsules on bare titanium frames, signal red-orange
    "va": {"armor": (236, 234, 224), "armor2": (150, 156, 164), "frame": (54, 56, 62), "accent": (255, 92, 44),
           "eye": (196, 255, 80), "nozzle": (62, 62, 66), "metal": (128, 132, 138), "glass": (60, 140, 170)},
    # Corvid Systems: low-observable - charcoal facets, slate, muted red, amber slit sensors
    "cv": {"armor": (84, 86, 94), "armor2": (50, 52, 60), "frame": (32, 32, 36), "accent": (190, 66, 52),
           "eye": (255, 170, 40), "nozzle": (38, 38, 42), "metal": (72, 74, 80), "glass": (150, 124, 64)},
    # Talos Heavy Machinery: fortress - cast steel blue-grey, gunmetal, brass trim, cold white optics
    "th": {"armor": (96, 108, 120), "armor2": (60, 64, 72), "frame": (40, 42, 46), "accent": (186, 142, 66),
           "eye": (176, 224, 255), "nozzle": (54, 54, 58), "metal": (92, 94, 98), "glass": (90, 130, 160)},
    # Orrery Experimental: prototypes - pearl white, gold, teal glow
    "ox": {"armor": (226, 228, 232), "armor2": (196, 160, 72), "frame": (48, 52, 60), "accent": (40, 210, 200),
           "eye": (90, 255, 230), "nozzle": (58, 60, 66), "metal": (110, 114, 122), "glass": (70, 160, 170)},
})

MAKERS = {"va": "Vesper Aeronautics", "cv": "Corvid Systems", "th": "Talos Heavy Machinery", "ox": "Orrery Experimental"}


def export(pid, model, meta, previews=True):
    # tudursvehiclemod only indexes addon textures under textures/vehicle/
    return mg.export(pid, model, meta, previews=previews, ns=NS, assets=ASSETS, data=DATA, preview=PREVIEW,
                     tex_sub="textures/vehicle/part")


def ref(pid):
    """Full id of a pack part."""
    return f"{NS}:{pid}"
