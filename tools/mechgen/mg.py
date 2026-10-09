"""Shared helpers for the Mech Frame part generator (meshkit).

Coordinates: +X left, +Y up, +Z forward, 1 unit = 1 m - the mod's model space.
Every part is a meshkit Model whose meshkit parts are the OBJ groups the mod animates
(see the Java MechPart doc for the conventions of each part kind).
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
MESHKIT = os.environ.get("MESHKIT_PATH", os.path.join(HERE, "..", "..", "..", "meshkit_src", "meshkit"))
sys.path.insert(0, os.path.abspath(MESHKIT))

import meshkit as mk  # noqa: E402
from meshkit import check, view, detail  # noqa: E402
from meshkit.texture import texture_model, value_noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "mechframe")
DATA = os.path.join(RES, "data", "mechframe")
PREVIEW = os.path.join(ROOT, "tools", "preview")

# ---------------------------------------------------------------------------------- shapes
# The shape helpers live in meshkit (meshkit.mech) - shared with other robot projects. They are
# re-exported here so the part scripts keep importing them from mg.
from meshkit.mech import (rrect, stack, ring_frame, seg, cyl, cbox, wedge_xz, mirror, nozzle,  # noqa: E402,F401
                          _round_poly, pent2d, pent_ring, pstack, pseg, zloft, plate, slab, hbox,
                          tube, truss, chine2d, chine_ring, chine_seg, chine_stack, cast, cast_seg, split_warped)


# ---------------------------------------------------------------------------------- paint

SCHEMES = {
    # Kestrel Dynamics: light, fast - white with navy and cyan
    "kd": {"armor": (214, 218, 222), "armor2": (54, 66, 92), "frame": (58, 60, 66), "accent": (48, 196, 226),
           "eye": (90, 240, 255), "nozzle": (70, 72, 78), "metal": (82, 86, 92)},
    # Grauwerk Heavy Industries: armour - olive, dark grey, hazard yellow
    "gw": {"armor": (104, 110, 78), "armor2": (70, 74, 70), "frame": (50, 50, 48), "accent": (226, 184, 44),
           "eye": (255, 170, 60), "nozzle": (62, 62, 60), "metal": (76, 78, 76)},
    # Arachne Systems: gunmetal and crimson
    "ar": {"armor": (66, 70, 78), "armor2": (150, 30, 40), "frame": (38, 38, 42), "accent": (190, 186, 172),
           "eye": (255, 70, 60), "nozzle": (56, 56, 60), "metal": (70, 72, 78)},
    # Falcon (Kestrel's transforming line): white, slate and signal orange
    "kf": {"armor": (230, 232, 236), "armor2": (70, 78, 96), "frame": (56, 58, 64), "accent": (238, 128, 30),
           "eye": (120, 255, 160), "nozzle": (66, 66, 70), "metal": (80, 82, 88), "glass": (60, 120, 150)},
    # Swallow (transforming line): white, royal blue and red
    "ks": {"armor": (234, 236, 240), "armor2": (44, 66, 140), "frame": (54, 56, 62), "accent": (214, 44, 52),
           "eye": (255, 214, 80), "nozzle": (64, 64, 70), "metal": (80, 82, 88), "glass": (70, 120, 170)},
    # Manta (transforming line): gunmetal blue, near-black and teal
    "km": {"armor": (82, 90, 106), "armor2": (34, 38, 46), "frame": (46, 48, 54), "accent": (70, 214, 190),
           "eye": (255, 96, 196), "nozzle": (58, 58, 64), "metal": (74, 76, 84), "glass": (80, 170, 180)},
}

FLAT = ("frame", "nozzle", "metal", "eye")


def finish(model, scheme, size=512, stripes=None, grime_top=None):
    """Texture: projected armour colours with tone noise, ink lines and panel lines; small dark
    parts on flat swatches."""
    col = SCHEMES[scheme]
    tags = set()
    for p in model.order:
        for sh in model.parts[p].shells:
            tags.update(sh.tags)
    flat = {t: col[t] for t in FLAT if t in tags}
    colors = {t: col.get(t, col["armor"]) for t in tags if t not in flat}
    glass = tuple(t for t in ("glass",) if t in tags)

    def paint(atlas, m):
        detail.tone(atlas, [t for t in colors], amount=0.06, scale=0.9)
        if stripes:
            for tag, fn in stripes:
                if tag in colors:
                    atlas.paint3d(fn, tags=[tag])
        detail.panel_lines(atlas, [t for t in ("armor", "armor2") if t in colors], frame_step=0.9, span_step=0.7,
                           row_step=0.45, offset=0.2, k=0.8)
        detail.ink(atlas, None, depth_m=0.03, strength=0.45, halo=0.12)
        if grime_top is not None:
            detail.grime(atlas, [t for t in colors], y_top=grime_top, amount=0.3)
        if glass:
            atlas.paint_alpha(lambda X, Y, Z: np.full(len(X), 215.0), tags=list(glass))

    texture_model(model, (size, size), colors=colors, flat=flat, paint=paint, glass_tags=glass)
    return model


# ---------------------------------------------------------------------------------- export

def export(part_id, model, meta, previews=True, ns="mechframe", assets=None, data=None, preview=None, tex_sub="textures/part"):
    """Writes the OBJ, the PNG and the part JSON; returns the summary dict. ns / assets / data /
    preview redirect the output (an expansion pack: its own namespace and folder). tex_sub: where the
    texture goes under assets - an addons-folder pack must use textures/vehicle/..., the only texture
    folder the base mod indexes there."""
    assets = assets or ASSETS
    data = data or DATA
    preview = preview or PREVIEW
    obj_dir = os.path.join(assets, "models", "obj", "parts")
    tex_dir = os.path.join(assets, *tex_sub.split("/"))
    json_dir = os.path.join(data, "mech_parts")
    for d in (obj_dir, tex_dir, json_dir, preview):
        os.makedirs(d, exist_ok=True)
    rep = check.report(model, contacts=False)
    issues = rep.get("issues", [])
    mk.save(model, os.path.join(obj_dir, part_id + ".obj"))
    # meshkit writes the texture next to the OBJ; move it where the mod expects it.
    from PIL import Image
    tex = os.path.join(tex_dir, part_id + ".png")
    Image.fromarray(model.texture).save(tex)
    for ext in (".png", ".glb"):
        stray = os.path.join(obj_dir, part_id + ext)
        if os.path.exists(stray):
            os.remove(stray)
    meta = dict(meta)
    meta.setdefault("model", f"{ns}:models/obj/parts/{part_id}.obj")
    meta.setdefault("texture", f"{ns}:{tex_sub}/{part_id}.png")
    with open(os.path.join(json_dir, part_id + ".json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)
    if previews:
        img = view.sheet(model, views=("iso", "iso_rear", "left", "front"), W=360, H=300, mode="texture", wire=True, cols=4)
        img.save(os.path.join(preview, part_id + ".png"))
    return {"id": part_id, "tris": model.tri_count(), "issues": len(issues), "first_issue": issues[:2]}


def load_part(pid):
    """(model, meta) of an already exported part."""
    m = mk.load(os.path.join(ASSETS, "models", "obj", "parts", pid + ".obj"))
    with open(os.path.join(DATA, "mech_parts", pid + ".json"), encoding="utf-8") as f:
        meta = json.load(f)
    return m, meta


def r3(v):
    return [round(float(x), 4) for x in v]
