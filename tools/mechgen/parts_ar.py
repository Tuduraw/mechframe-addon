"""Arachne Systems - multi-legged heavy frames (gunmetal / crimson).

Heavy design language with an insect twist: faceted carapace plates, wide multi-eye heads, broad
arms under angled shoulder carapaces. The leg hulls are big central blocks; each leg joint sits
under a sloped fender that guards it from the front while leaving the joint partly visible."""
import math

import numpy as np

from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3, plate, slab, hbox

SCHEME = "ar"
MAKER = "Arachne Systems"


def _spider_leg(m, name, coxa_pivot, hip, knee, ankle, tip_y=0.0, w=1.0, ridge=True, n=8):
    """One splayed leg: coxa (yaw block), femur rising to the knee, tibia down to a foot spike."""
    cp = np.array(coxa_pivot, float)
    hip = np.array(hip, float)
    knee = np.array(knee, float)
    ankle = np.array(ankle, float)
    out = (hip - cp)
    out[1] = 0
    out /= np.linalg.norm(out)
    side = np.cross([0, 1, 0], out)
    m.add(name + "_coxa", cyl(cp + [0, -0.24, 0], cp + [0, 0.24, 0], 0.24 * w, n=n, tag="frame"),
          seg([cp, hip + out * 0.12], [(0.19 * w, 0.2 * w), (0.18 * w, 0.18 * w)], "armor2", c=0.05, side=side))
    femur = [cyl(hip - side * 0.23 * w, hip + side * 0.23 * w, 0.19 * w, n=n, tag="frame"),
             seg([hip, (hip + knee) / 2 + [0, 0.14, 0], knee], [(0.17 * w, 0.22 * w), (0.2 * w, 0.27 * w), (0.15 * w, 0.19 * w)],
                 "armor", c=0.06, side=side)]
    if ridge:
        # carapace ridge on top of the femur
        femur.append(seg([hip + [0, 0.18 * w, 0] + out * 0.15, (hip + knee) / 2 + [0, 0.38 * w, 0]], [(0.06 * w, 0.06), (0.04 * w, 0.05)],
                         "armor2", c=0.02, side=side))
    m.add(name + "_femur", *femur)
    m.add(name + "_tibia",
          cyl(knee - side * 0.2 * w, knee + side * 0.2 * w, 0.17 * w, n=n, tag="frame"),
          seg([knee + [0, 0.1, 0], knee + (ankle - knee) * 0.35, ankle], [(0.16 * w, 0.22 * w), (0.18 * w, 0.27 * w), (0.08 * w, 0.1 * w)],
              "armor", c=0.05, side=side),
          seg([ankle + [0, 0.05, 0], [ankle[0], tip_y, ankle[2]]], [(0.09 * w, 0.11 * w), (0.03 * w, 0.03 * w)], "armor2", c=0.01, side=side),
          # crimson blade plate along the outer tibia
          seg([knee + (ankle - knee) * 0.12 + out * 0.14, knee + (ankle - knee) * 0.6 + out * 0.14], [(0.12 * w, 0.045), (0.08 * w, 0.03)],
              "armor2", c=0.01, side=side))
    return {"coxa": name + "_coxa", "coxa_pivot": r3(cp), "upper": name + "_femur", "lower": name + "_tibia",
            "hip": r3(hip), "knee": r3(knee), "ankle": r3([ankle[0], ankle[1], ankle[2]]), "pole": [0, 1, 0]}


def _fender(m, cp, hip, top_y):
    """Sloped joint guard bolted to the hull's edge above a coxa: faces up, out and forward
    (sloped armour toward the front), covering the joint's top while its side stays in view."""
    cp = np.asarray(cp, float)
    hip = np.asarray(hip, float)
    out = hip - cp
    out[1] = 0
    out /= np.linalg.norm(out)
    sx = 1.0 if out[0] >= 0 else -1.0
    along = np.cross([0, 1.0, 0], out)            # horizontal, along the hull edge
    if along[2] < 0:
        along = -along
    centre = cp + out * 0.16 + np.array([0, top_y - cp[1], 0])
    n = np.array([0, 0.75, 0]) + out * 0.42 + np.array([0, 0, 0.3])
    n /= np.linalg.norm(n)
    v = np.cross(n, along)
    v /= np.linalg.norm(v)
    m.add("pelvis", plate(centre, along, v, 0.34, 0.24, 0.1, "armor2", c=0.06))
    # the bracket that carries it, from the hull side
    m.add("pelvis", cbox(*(cp + out * 0.02 + np.array([0, top_y - cp[1] - 0.08, 0])), 0.24, 0.16, 0.24, "frame", c=0.03))


def legs_strider():
    """LG-A5 Strider: quad legs - stable, heavy load, long hover."""
    m = mk.Model("lg_strider")
    m.add("pelvis", stack([(2.0, 0, 0, 0.56, 0.8, 0.22), (2.25, 0, 0, 0.78, 1.0, 0.28), (2.75, 0, 0, 0.76, 0.95, 0.28),
                           (2.98, 0, 0, 0.54, 0.68, 0.22)], "armor"))
    m.add("pelvis", plate((0, 2.62, 1.0), (1, 0, 0), (0, 0.7, -0.7), 0.5, 0.26, 0.09, "armor2", c=0.06))
    m.add("pelvis", cbox(0, 2.5, -0.98, 0.64, 0.32, 0.14, "armor2", c=0.03))
    chains = []
    corners = [("fl", 1, 1, 0), ("fr", -1, 1, 1), ("rl", 1, -1, 1), ("rr", -1, -1, 0)]
    for name, sx, sz, group in corners:
        cp = (0.6 * sx, 2.45, 0.66 * sz)
        hip = (0.86 * sx, 2.5, 0.92 * sz)
        c = _spider_leg(m, name, cp, hip, (1.72 * sx, 3.15, 1.78 * sz), (2.3 * sx, 0.4, 2.36 * sz), w=1.15)
        _fender(m, cp, hip, 2.98)
        c.update({"name": name, "group": group})
        chains.append(c)
    finish(m, SCHEME, grime_top=0.9)
    meta = {"slot": "legs", "display_name": "LG-A5 Strider", "maker": MAKER, "tier": 4,
            "weight": 32.0, "armor": 480,
            "points": {"hip": [0, 2.95, 0]},
            "stats": {"type": "quad", "load_limit": 88, "walk_speed": 0.3, "boost_multiplier": 0.95, "jump": 0.75,
                      "turn_rate": 13, "step_height": 1.6, "traction": 0.32, "qb_multiplier": 0.9,
                      "hover_multiplier": 1.6, "collision_width": 2.4, "step_length": 1.5, "step_lift": 0.75,
                      "step_ticks": 7, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def legs_weaver():
    """LG-A6 Weaver: hexapod - very stable, high steps, a low wide hull with guarded leg joints."""
    m = mk.Model("lg_weaver")
    m.add("pelvis", stack([(1.35, 0, -0.05, 0.64, 1.15, 0.26), (1.55, 0, 0, 0.86, 1.4, 0.32), (1.95, 0, 0, 0.84, 1.35, 0.32),
                           (2.14, 0, -0.05, 0.62, 1.04, 0.26)], "armor"))
    m.add("pelvis", plate((0, 1.86, 1.42), (1, 0, 0), (0, 0.65, -0.76), 0.56, 0.24, 0.1, "armor2", c=0.07))
    m.add("pelvis", cbox(0, 1.75, -1.38, 0.9, 0.3, 0.14, "armor2", c=0.03))
    chains = []
    rows = [(1.0, "1"), (0.0, "2"), (-1.0, "3")]
    for z, k in rows:
        for sx, side in ((1, "l"), (-1, "r")):
            name = side + k
            group = 0 if (side == "l") == (k != "2") else 1
            spread = 1.0 + 0.35 * abs(z)
            cp = (0.7 * sx, 1.7, z)
            hip = (0.98 * sx, 1.72, z * 1.12)
            c = _spider_leg(m, name, cp, hip, (2.0 * sx, 2.75, z * spread * 1.35), (2.75 * sx, 0.32, z * spread * 1.7), w=1.0,
                            ridge=False, n=6)
            _fender(m, cp, hip, 2.12)
            c.update({"name": name, "group": group})
            chains.append(c)
    finish(m, SCHEME, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-A6 Weaver", "maker": MAKER, "tier": 3,
            "weight": 28.0, "armor": 430,
            "points": {"hip": [0, 2.1, 0]},
            "stats": {"type": "hexapod", "load_limit": 80, "walk_speed": 0.36, "boost_multiplier": 0.95, "jump": 0.7,
                      "turn_rate": 15, "step_height": 2.2, "traction": 0.4, "qb_multiplier": 0.85,
                      "hover_multiplier": 1.2, "collision_width": 2.4, "step_length": 1.3, "step_lift": 0.7,
                      "step_ticks": 5, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def core_thorax():
    """CR-A3 Thorax: heavy core - a faceted insect thorax under crimson carapace plates."""
    m = mk.Model("cr_thorax")
    m.add("core", stack([(-0.08, 0, -0.02, 0.38, 0.32, 0.1), (0.48, 0, 0.0, 0.44, 0.36, 0.1)], "frame"))
    m.add("core", stack([(0.38, 0, -0.04, 0.58, 0.42, 0.16), (0.85, 0, 0.1, 0.94, 0.56, 0.26),
                         (1.45, 0, 0.06, 0.92, 0.54, 0.24), (1.86, 0, -0.08, 0.56, 0.38, 0.16)], "armor"))
    for s in (1, -1):
        # carapace: angled crimson plates over the upper chest, meeting in a ridge
        m.add("core", plate((0.36 * s, 1.5, 0.52), (s * 0.82, -0.2, -0.55), (0, 0.9, -0.3), 0.36, 0.3, 0.09, "armor2", c=0.06))
        m.add("core", plate((0.52 * s, 0.86, 0.62), (s * 0.7, 0, -0.7), (0, 1, 0.15), 0.26, 0.3, 0.08, "armor", c=0.05))
        m.add("core", cyl((0.74 * s, 1.5, 0), (1.14 * s, 1.5, 0), 0.24, n=10, tag="frame"))
        m.add("core", hbox(0.86 * s, 1.0, -0.02, 0.18, 0.8, 0.78, "armor2", c=0.05))
        m.add("core", hbox(0.5 * s, 1.74, -0.36, 0.28, 0.22, 0.28, "armor2", c=0.04))
        # sensor horns
        m.add("core", seg([(0.5 * s, 1.6, 0.42), (0.86 * s, 2.02, 0.2)], [(0.05, 0.08), (0.02, 0.03)], "accent", c=0.01))
    m.add("core", cyl((0, 1.7, 0.02), (0, 1.92, 0.02), 0.16, n=8, tag="frame"))
    m.add("core", stack([(0.82, 0, -0.5, 0.42, 0.06, 0.04), (1.48, 0, -0.5, 0.42, 0.06, 0.04)], "armor2"))
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-A3 Thorax", "maker": MAKER, "tier": 2, "weight": 18.0, "armor": 600,
            "points": {"neck": [0, 1.88, 0.04], "shoulder_l": [1.12, 1.5, 0], "back": [0, 1.15, -0.54],
                       "back_l": [0.5, 1.82, -0.36], "seat": [0, 1.0, 0.1], "eye": [0, 2.24, 0.42]},
            "stats": {"en_capacity": 1900, "en_recharge": 34, "recharge_delay": 12, "defense": 1.1, "fuel": 850,
                      "stability": 1.1}}
    return m, meta


def head_ocellus():
    """HD-A3 Ocellus: multi-eye head - wide and low, two rows of eyes under a crimson brow."""
    m = mk.Model("hd_ocellus")
    m.add("head", cyl((0, -0.08, 0), (0, 0.12, 0), 0.15, n=8, tag="frame"))
    m.add("head", stack([(0.06, 0, 0.0, 0.26, 0.24, 0.08), (0.2, 0, 0.06, 0.32, 0.32, 0.12),
                         (0.36, 0, 0.02, 0.28, 0.28, 0.1), (0.44, 0, -0.04, 0.18, 0.2, 0.08)], "armor"))
    for x, y in ((0.1, 0.28), (-0.1, 0.28), (0.24, 0.24), (-0.24, 0.24), (0.06, 0.16), (-0.06, 0.16), (0.18, 0.14), (-0.18, 0.14)):
        m.add("head", mk.box(x, y, 0.37 - abs(x) * 0.25, 0.08, 0.06, 0.06, tag="eye"))
    m.add("head", plate((0, 0.38, 0.22), (1, 0, 0), (0, 0.5, -0.86), 0.3, 0.1, 0.06, "armor2", c=0.04))
    for s in (1, -1):
        # mandible plates under the cheeks
        m.add("head", slab([(0.0, 0.0), (0.18, -0.02), (0.22, -0.16), (0.04, -0.12)], (0.14 * s, 0.1, 0.3),
                           (s, 0, 0.25), (0, 1, 0), 0.05, "armor2"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-A3 Ocellus", "maker": MAKER, "tier": 2, "weight": 2.8, "armor": 150,
            "points": {"top": [0, 0.46, 0]}, "stats": {"lock_range": 125, "lock_cone": 15, "stability": 1.0}}
    return m, meta


def arms_mandible():
    """AM-A3 Mandible: heavy arms under angled shoulder carapaces, forearms with crimson blades."""
    m = mk.Model("am_mandible")
    elbow = np.array([0.16, -1.0, 0.08])
    grip = np.array([0.16, -1.11, 0.98])
    ex = elbow[0]
    upper = [cyl((-0.1, 0, 0), (0.22, 0, 0), 0.23, n=10, tag="frame"),
             hbox(0.22, 0.04, 0.0, 0.56, 0.56, 0.7, "armor", c=0.12, top=0.75),
             # shoulder carapace: a pentagonal shell swept back over the joint
             slab([(-0.4, -0.1), (0.32, -0.16), (0.48, 0.16), (0.1, 0.34), (-0.36, 0.24)], (0.32, 0.42, -0.04),
                  (0, 0, 1), (1, -0.55, 0), 0.1, "armor2"),
             seg([(0.5, 0.3, -0.2), (0.62, 0.56, -0.5)], [(0.05, 0.12), (0.02, 0.04)], "armor2", c=0.01),
             stack([(elbow[1] + 0.06, ex, elbow[2] - 0.02, 0.17, 0.19, 0.07), (-0.28, ex, 0.0, 0.19, 0.21, 0.08)], "frame"),
             cyl((ex - 0.24, elbow[1], elbow[2]), (ex + 0.24, elbow[1], elbow[2]), 0.2, n=10, tag="frame")]
    zc = (elbow[2] + grip[2]) / 2 - 0.06
    fore = [hbox(ex, elbow[1] - 0.02, zc, 0.42, 0.44, 0.98, "armor", c=0.1, top=0.82, bottom=0.88),
            # crimson blade raked forward along the outer forearm
            slab([(-0.4, 0.0), (0.5, 0.0), (0.72, 0.1), (0.4, 0.2), (-0.36, 0.16)], (ex + 0.24, elbow[1] + 0.02, zc),
                 (0, 0, 1), (0, 1, 0), 0.06, "armor2"),
            cbox(ex, grip[1], grip[2] - 0.02, 0.3, 0.3, 0.3, "frame", c=0.05)]
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-A3 Mandible", "maker": MAKER, "tier": 2, "weight": 11.0, "armor": 320,
            "points": {"elbow": r3(elbow), "grip": r3(grip)},
            "stats": {"load_limit": 20, "aim_speed": 360, "recoil_control": 1.1}}
    return m, meta


def booster_glide():
    """BS-A3 Glide: efficient booster - a carapace shell over two angled thrusters."""
    m = mk.Model("bs_glide")
    m.add("booster", hbox(0, 0.0, -0.18, 0.78, 0.86, 0.48, "armor", c=0.1, top=0.82, bottom=0.86))
    m.add("booster", plate((0, 0.18, -0.46), (1, 0, 0), (0, 0.9, -0.4), 0.36, 0.34, 0.08, "armor2", c=0.06))
    noz = []
    for s in (1, -1):
        m.add("booster", seg([(0.3 * s, 0.2, -0.24), (0.86 * s, 0.42, -0.58)], [(0.04, 0.2), (0.03, 0.08)], "armor2", c=0.01,
                             side=(0, 1, 0)))
        m.add("booster", nozzle((0.22 * s, -0.36, -0.32), (0, -0.6, -0.8), 0.13, 0.26))
        noz.append({"pos": r3((0.22 * s, -0.52, -0.54)), "dir": [0, -0.6, -0.8], "size": 0.28})
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-A3 Glide", "maker": MAKER, "tier": 2, "weight": 5.0, "armor": 100,
            "stats": {"boost_speed": 1.0, "qb_speed": 2.4, "qb_cost": 280, "qb_cooldown": 10, "hover_thrust": 0.15,
                      "hover_cost": 10, "air_boost_cost": 4, "nozzles": noz}}
    return m, meta


ALL = [legs_strider, legs_weaver, core_thorax, head_ocellus, arms_mandible, booster_glide]
