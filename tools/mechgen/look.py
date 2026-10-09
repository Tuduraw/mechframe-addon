"""Quick look while designing: build some part builders, assemble presets with them (other parts
loaded from the exported files), check contacts and render previews.

    python look.py striker hopper          # robot form of these presets
    python look.py falcon --tf             # plus mid-transformation frames
"""
import json
import os
import sys

import numpy as np

from mg import mk, view, DATA, ASSETS, PREVIEW
import rig

OUT = os.environ.get("LOOK_OUT", PREVIEW)

# Paint-scheme preview: faces coloured by their scheme colour (mode "tags" with tags "scheme|tag").
from mg import SCHEMES
from meshkit import view as _view
_orig_color = _view._color_for
SET_SCHEME = {"falcon": "kf", "swallow": "ks", "manta": "km"}
MAKER_SCHEME = {"Kestrel Dynamics": "kd", "Grauwerk Heavy Industries": "gw", "Arachne Systems": "ar"}


def _scheme_color(name, *a):
    if "|" in name:
        sc, tag = name.split("|", 1)
        c = SCHEMES[sc].get(tag, SCHEMES[sc]["armor"])
        return np.array(c, np.float32)
    return _orig_color(name, *a)


_view._color_for = _scheme_color


def painted(parts, placements, name):
    m = rig.model_at(parts, placements, name)
    for pname in m.order:
        mount = pname.split(":")[0]
        meta = parts[mount][1]
        sc = SET_SCHEME.get(meta.get("transform_set"), MAKER_SCHEME.get(meta.get("maker"), "kd"))
        for sh in m.parts[pname].shells:
            sh.tags = [t if "|" in t else f"{sc}|{t}" for t in sh.tags]
    return m


def load(pid):
    m = mk.load(os.path.join(ASSETS, "models", "obj", "parts", pid + ".obj"))
    meta = json.load(open(os.path.join(DATA, "mech_parts", pid + ".json")))
    return m, meta


def parts_for(asm, built):
    return {k: (built[v] if v in built else load(v)) for k, v in asm.items()}


def look(name, parts, tf=False, views=("iso", "iso_rear", "left", "front", "top"), W=380):
    rob = painted(parts, rig.robot_keys(parts), name)
    fl = rig.floating_groups(rob)
    img = view.sheet(rob, views=views, W=W, H=W, mode="tags", wire=False, cols=len(views))
    p = os.path.join(OUT, f"look_{name}.png")
    img.save(p)
    out = {"name": name, "tris": rob.tri_count(), "size": [round(float(x), 2) for x in rob.summary()["size"]],
           "floating": fl, "img": p}
    if tf and parts["core"][1].get("jet_pose"):
        frames = []
        for t in (0.25, 0.5, 0.75, 1.0):
            m = painted(parts, rig.blend_keys(parts, t), f"{name}_t{t}")
            out[f"floating_t{t}"] = rig.floating_groups(m)
            frames.append(m)
        out["jet_size"] = [round(float(x), 2) for x in frames[-1].summary()["size"]]
        for i, m in enumerate(frames):
            img = view.sheet(m, views=("iso", "left", "front", "top"), W=W, H=W, mode="tags", wire=False, cols=4)
            pth = os.path.join(OUT, f"look_{name}_tf{i}.png")
            img.save(pth)
    return out


if __name__ == "__main__":
    from build_all import PRESETS
    import parts_kd, parts_gw, parts_ar, parts_kf, parts_ks, parts_km
    names = [a for a in sys.argv[1:] if not a.startswith("--")]
    tf = "--tf" in sys.argv
    built = {}
    mods = {"striker": [parts_kd], "hopper": [parts_kd], "rampart": [parts_gw], "bastion": [parts_gw],
            "weaver": [parts_ar], "strider": [parts_ar], "falcon": [parts_kf],
            "swallow": [parts_ks], "manta": [parts_km]}
    for n in names:
        for mod in mods[n]:
            if hasattr(mod, "build_set"):
                built.update(mod.build_set())
            else:
                for b in mod.ALL:
                    m, meta = b()
                    built[m.name] = (m, meta)
    for n in names:
        print(json.dumps(look(n, parts_for(PRESETS[n], built), tf=tf)))
