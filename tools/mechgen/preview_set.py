"""Builds a list of part builders, exports them and renders an assembled preview.

    python preview_set.py kd            # parts_kd + rifle, assembled
"""
import os
import sys

from mg import view, export, PREVIEW
import assemble


def build(builders):
    out = {}
    for b in builders:
        m, meta = b()
        out[m.name] = (m, meta)
    return out


def show_assembly(name, parts, views=("iso", "iso_rear", "left", "front", "top"), form="robot"):
    m = assemble.assemble(parts, form=form, name=name)
    img = view.sheet(m, views=views, W=420, H=420, mode="parts", wire=True, cols=len(views))
    path = os.path.join(PREVIEW, f"asm_{name}_{form}.png")
    img.save(path)
    return path, m.tri_count(), m.summary()["size"]


if __name__ == "__main__":
    import parts_kd
    import parts_weapons
    built = build(parts_kd.ALL + [parts_weapons.rifle, parts_weapons.missile_pod])
    for pid, (m, meta) in built.items():
        print(export(pid, m, meta))
    parts = {"legs": built["lg_stride"], "core": built["cr_swift"], "head": built["hd_lark"], "arms": built["am_talon"],
             "booster": built["bs_zephyr"], "r_arm": built["wa_rifle"], "l_back": built["wb_missile"]}
    print(show_assembly("striker", parts))
