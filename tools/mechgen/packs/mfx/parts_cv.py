"""Corvid Systems - medium, low-observable frames (charcoal / slate / muted red, amber slits).

Concept: "be seen last". Corvid builds the all-rounder of the field and shapes it to scatter a
sensor's gaze: every surface is a flat facet, every section a chined hexagon, every edge one of a
few repeated angles. No round parts are left in view - axles are hexagonal, sensors are recessed
slits, nozzles are flat slots shielded by canted fins. Middle weight, middle armour, good EN.

Design rules (what makes a Corvid part read as one):
  - chined sections (meshkit.mech.chine_seg / chine_stack): a flat face front and back, sharp
    side chines - the silhouette is all straight lines;
  - edges parallel: leading edges of plates, fins and feet share the same ~35 degree sweep;
  - sawtooth hems (small triangular slabs) where panels end;
  - one horizontal amber slit per sensor, nothing sticking out; slot nozzles under fins."""
import numpy as np

from common import MAKERS
from mg import mk, stack, seg, cyl, cbox, mirror, finish, r3, slab, plate, chine_seg, chine_stack, nozzle

SCHEME = "cv"
MAKER = MAKERS["cv"]


def hexaxle(p, r, w, s=1, tag="frame"):
    p = np.asarray(p, float)
    return cyl((p[0] - w * s, p[1], p[2]), (p[0] + w * s, p[1], p[2]), r, n=6, tag=tag)


def saw(origin, u, v, length, teeth=3, depth=0.08, t=0.02, tag="accent"):
    """Sawtooth hem: `teeth` small triangles along u (length), pointing along v."""
    origin = np.asarray(origin, float)
    out = []
    step = length / teeth
    for i in range(teeth):
        c = origin + np.asarray(u, float) / np.linalg.norm(u) * (step * (i + 0.5) - length / 2)
        out.append(slab([(-step * 0.5, 0.0), (step * 0.5, 0.0), (0.0, depth)], c, u, v, t, tag))
    return out


def slot_nozzle(center, along, out, w, h, length, tag="nozzle"):
    """Flat slot nozzle: a wide, thin box (w along `along`, h across) protruding `length` along `out`."""
    center = np.asarray(center, float)
    out = np.asarray(out, float) / np.linalg.norm(out)
    return seg([center, center + out * length], [(w, h, 0.01), (w * 1.05, h * 0.8, 0.01)], tag, side=along)


# ---------------------------------------------------------------------------------- legs

def raven_leg(s, hip, knee, ankle, k=1.0):
    """One faceted biped leg: chined thigh, a knee wedge, a chined shin with a flat front and a hard
    calf chine, a hexagonal wedge foot. Returns (thigh, shin, foot) shell lists."""
    F = (0, 0, 1)
    thigh = [hexaxle(hip, 0.18 * k, 0.2, s),
             chine_seg([hip + [0, 0.05, 0.0], (hip + knee) / 2 + [0, 0, 0.04], knee + [0, 0.16, 0.0]],
                       [(0.2 * k, 0.24 * k, 0.2 * k), (0.21 * k, 0.27 * k, 0.21 * k), (0.15 * k, 0.18 * k, 0.15 * k)], "armor", up=F, k=0.5),
             # flat outer plate on the thigh, its lower edge swept like every other edge
             slab([(0.0, 0.25), (0.0, -0.45), (0.3, -0.3), (0.3, 0.32)], (hip + knee) / 2 + [0.2 * s * k, 0.05, -0.08],
                  (0, 0, 1), (0, 1, 0), 0.04, "armor2")]
    shin = [hexaxle(knee, 0.16 * k, 0.18, s),
            # knee wedge: a chined block pointing forward and up over the joint
            chine_seg([knee + [0, -0.18, 0.12], knee + [0, 0.22, 0.3]], [(0.17 * k, 0.12, 0.1), (0.08 * k, 0.06, 0.05)], "armor2",
                      up=(0, 0.5, 1), k=0.45),
            chine_seg([knee + [0, -0.05, 0.02], knee + (ankle - knee) * 0.4, knee + (ankle - knee) * 0.85, ankle + [0, 0.1, 0.0]],
                      [(0.17 * k, 0.24 * k, 0.24 * k), (0.2 * k, 0.28 * k, 0.32 * k), (0.15 * k, 0.2 * k, 0.2 * k),
                       (0.1 * k, 0.12 * k, 0.12 * k)], "armor", up=F, k=0.55, kb=0.35)]
    a0 = knee + (ankle - knee) * 0.2
    a1 = knee + (ankle - knee) * 0.62
    shin.append(chine_seg([a0 + [0, 0, 0.24 * k], a1 + [0, 0, 0.27 * k]], [(0.15 * k, 0.03, 0.03), (0.17 * k, 0.03, 0.03)], "armor2",
                          up=(0, 0, 1), k=0.7))
    shin += saw(a1 + [0, 0, 0.27 * k], (1, 0, 0), (0, -1, 0.3), 0.26 * k, teeth=3, depth=0.09)
    foot = [hexaxle(ankle, 0.1 * k, 0.12, s),
            chine_stack([(0.0, ankle[0], ankle[2] + 0.12, 0.2 * k, 0.72 * k, 0.42 * k, {"k": 0.25, "kb": 0.7}),
                         (0.16, ankle[0], ankle[2] + 0.1, 0.19 * k, 0.66 * k, 0.4 * k, {"k": 0.25, "kb": 0.7}),
                         (ankle[1] + 0.02, ankle[0], ankle[2], 0.1 * k, 0.24 * k, 0.16 * k, {"k": 0.3, "kb": 0.6})], "armor2"),
            slab([(-0.12, 0.0), (0.12, 0.0), (0.0, 0.18)], (ankle[0], 0.12, ankle[2] + 0.76 * k), (1, 0, 0), (0, 0, 1), 0.04, "accent")]
    return thigh, shin, foot


def legs_raven():
    """LG-C2 Raven: faceted medium biped. Hip 3.0 m."""
    m = mk.Model("lg_raven")
    H = 3.0
    m.add("pelvis", chine_stack([(H - 0.3, 0, 0.0, 0.28, 0.3, 0.26, {"k": 0.4}), (H - 0.12, 0, 0.0, 0.46, 0.44, 0.34, {"k": 0.4}),
                                 (H + 0.22, 0, 0.0, 0.44, 0.42, 0.32, {"k": 0.4}), (H + 0.3, 0, 0.0, 0.3, 0.3, 0.22, {"k": 0.4})],
                                "armor2"))
    m.add("pelvis", chine_seg([(0, H + 0.12, 0.34), (0, H - 0.3, 0.46)], [(0.14, 0.05, 0.05), (0.05, 0.03, 0.03)], "accent",
                              up=(0, 0, 1)))
    for s in (1, -1):
        # tasset: a flat facet over each hip joint, swept hem
        m.add("pelvis", slab([(-0.24, 0.0), (0.24, 0.0), (0.2, -0.42), (-0.12, -0.5)], (0.62 * s, H + 0.18, 0.12), (0, 0.15, 1),
                             (0, 1, 0.1), 0.05, "armor"))
        m.add("pelvis", *saw((0.62 * s, H - 0.3, 0.16), (0, 0, 1), (0, -1, 0), 0.32, teeth=3, depth=0.07))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.5 * s, H, 0.0])
        knee = np.array([0.52 * s, 1.72, 0.38])
        ankle = np.array([0.52 * s, 0.42, -0.04])
        thigh, shin, foot = raven_leg(s, hip, knee, ankle)
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-C2 Raven", "maker": MAKER, "tier": 3, "weight": 21.0, "armor": 360,
            "points": {"hip": [0, H + 0.3, 0]},
            "stats": {"type": "biped", "load_limit": 58, "walk_speed": 0.32, "boost_multiplier": 1.02, "jump": 0.95,
                      "turn_rate": 16, "step_height": 1.2, "traction": 0.37, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.0, "collision_width": 1.8, "step_length": 1.6, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def _cv_spider_leg(m, name, cp, hip, knee, ankle, w=1.0):
    cp, hip, knee, ankle = (np.asarray(v, float) for v in (cp, hip, knee, ankle))
    out = hip - cp
    out[1] = 0
    out /= np.linalg.norm(out)
    side = np.cross([0, 1, 0], out)
    m.add(name + "_coxa", cyl(cp + [0, -0.2, 0], cp + [0, 0.2, 0], 0.2 * w, n=6, tag="frame"),
          chine_seg([cp, hip + out * 0.1], [(0.16 * w, 0.16, 0.16), (0.15 * w, 0.14, 0.14)], "armor2", up=(0, 1, 0)))
    m.add(name + "_femur", cyl(hip - side * 0.2 * w, hip + side * 0.2 * w, 0.16 * w, n=6, tag="frame"),
          chine_seg([hip, (hip + knee) / 2 + [0, 0.1, 0], knee], [(0.16 * w, 0.2 * w, 0.14 * w), (0.18 * w, 0.24 * w, 0.16 * w),
                                                               (0.13 * w, 0.16 * w, 0.12 * w)], "armor", up=(0, 1, 0), k=0.45))
    tib = [cyl(knee - side * 0.17 * w, knee + side * 0.17 * w, 0.14 * w, n=6, tag="frame"),
           chine_seg([knee + [0, 0.06, 0], knee + (ankle - knee) * 0.35, ankle + [0, 0.05, 0]],
                     [(0.15 * w, 0.2 * w, 0.16 * w), (0.17 * w, 0.24 * w, 0.18 * w), (0.06 * w, 0.06 * w, 0.06 * w)], "armor",
                     up=tuple(out), k=0.5),
           seg([ankle + [0, 0.06, 0], [ankle[0], 0.0, ankle[2]]], [(0.07 * w, 0.07 * w, 0.01), (0.02, 0.02, 0.004)], "armor2", side=side)]
    tib += saw(knee + (ankle - knee) * 0.45 + out * 0.2 * w, ankle - knee, out, 0.5 * w, teeth=3, depth=0.1)
    m.add(name + "_tibia", *tib)
    return {"coxa": name + "_coxa", "coxa_pivot": r3(cp), "upper": name + "_femur", "lower": name + "_tibia",
            "hip": r3(hip), "knee": r3(knee), "ankle": r3([ankle[0], 0.0, ankle[2]]), "pole": [0, 1, 0]}


def legs_recluse():
    """LG-C4 Recluse: faceted hexapod - a low, flat chined hull like a stealth boat, six legs under
    swept fenders with sawtooth trailing edges."""
    m = mk.Model("lg_recluse")
    y0 = 1.5
    m.add("pelvis", chine_stack([(y0 - 0.22, 0, 0.0, 0.7, 1.2, 1.1, {"k": 0.35, "kb": 0.5}),
                                 (y0 + 0.02, 0, 0.0, 0.95, 1.5, 1.35, {"k": 0.35, "kb": 0.5}),
                                 (y0 + 0.34, 0, 0.0, 0.88, 1.4, 1.25, {"k": 0.35, "kb": 0.5}),
                                 (y0 + 0.5, 0, -0.05, 0.6, 1.0, 0.9, {"k": 0.35, "kb": 0.5})], "armor"))
    m.add("pelvis", chine_seg([(0, y0 + 0.48, 0.7), (0, y0 + 0.4, 1.25)], [(0.18, 0.04, 0.04), (0.05, 0.02, 0.02)], "accent", up=(0, 1, 0)))
    m.add("pelvis", stack([(y0 + 0.46, 0, 0, 0.4, 0.4, 0.08), (y0 + 0.62, 0, 0, 0.36, 0.36, 0.06)], "armor2"))
    chains = []
    for z, k in ((0.95, "1"), (0.0, "2"), (-0.95, "3")):
        for sx, side in ((1, "l"), (-1, "r")):
            name = side + k
            group = 0 if (side == "l") == (k != "2") else 1
            spread = 1.0 + 0.3 * abs(z)
            cp = (0.72 * sx, y0 + 0.08, z)
            hip = (0.98 * sx, y0 + 0.1, z * 1.08)
            c = _cv_spider_leg(m, name, cp, hip, (1.9 * sx, y0 + 1.0, z * spread * 1.3), (2.6 * sx, 0.3, z * spread * 1.6), w=0.95)
            # swept fender over the leg root
            m.add("pelvis", slab([(-0.32, 0.0), (0.32, 0.0), (0.18, 0.34), (-0.4, 0.3)], (0.9 * sx, y0 + 0.4, z),
                                 (0, 0, 1), (sx, 0.5, 0), 0.06, "armor2"))
            c.update({"name": name, "group": group})
            chains.append(c)
    finish(m, SCHEME, size=512, grime_top=0.7)
    meta = {"slot": "legs", "display_name": "LG-C4 Recluse", "maker": MAKER, "tier": 3, "weight": 27.0, "armor": 430,
            "points": {"hip": [0, y0 + 0.62, 0]},
            "stats": {"type": "hexapod", "load_limit": 78, "walk_speed": 0.35, "boost_multiplier": 0.98, "jump": 0.75,
                      "turn_rate": 15, "step_height": 2.0, "traction": 0.4, "qb_multiplier": 0.9,
                      "hover_multiplier": 1.2, "collision_width": 2.4, "step_length": 1.3, "step_lift": 0.65,
                      "step_ticks": 5, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def legs_lynx():
    """LG-C5 Lynx: faceted tank legs - a low wedge hull; faceted skirts hide the upper run of the
    tracks, the road wheels show under their sawtooth hem."""
    m = mk.Model("lg_lynx")
    m.add("hull", mk.extrude_zy([(-2.0, 0.42), (2.3, 0.42), (2.75, 0.78), (0.6, 1.36), (-1.7, 1.36), (-2.15, 0.95)], -0.9, 0.9,
                                tag="armor"))
    m.add("hull", plate((0, 1.06, 1.7), (1, 0, 0), (0, 0.27, -1.0), 0.6, 0.36, 0.06, "armor2", c=0.04))
    for s in (1, -1):
        # chined sensor bars along the deck edges, and a red line on the glacis
        m.add("hull", chine_seg([(0.7 * s, 1.36, 0.4), (0.7 * s, 1.36, -1.5)], [(0.12, 0.08, 0.02), (0.1, 0.06, 0.02)], "armor2", up=(0, 1, 0)))
        m.add("hull", slab([(-0.05, 0.0), (0.05, 0.0), (0.05, 0.9), (-0.05, 0.9)], (0.5 * s, 1.0, 2.05), (1, 0, 0), (0, 0.27, -1.0), 0.03,
                           "accent"))
    wheels = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        x0, x1 = sorted((0.86 * s, 1.4 * s))
        prof = [(-2.2, 0.0), (2.2, 0.0), (2.8, 0.55), (2.6, 0.88), (-2.4, 0.88), (-2.6, 0.5)]
        m.add(pre + "track", mk.extrude_zy(prof, x0, x1, tag="frame"))
        # faceted skirt: outer face leaning in, top chamfer, covering the top run down to the hubs
        xo = 1.48 * s
        m.add(pre + "track", mk.extrude_zy([(-2.5, 0.52), (2.62, 0.52), (2.95, 0.86), (2.4, 1.18), (-2.3, 1.18), (-2.62, 0.86)],
                                          *sorted((xo, xo - 0.08 * s)), tag="armor2"))
        m.add(pre + "track", mk.extrude_zy([(-2.3, 1.1), (2.4, 1.1), (2.0, 1.32), (-2.0, 1.32)], *sorted((0.84 * s, 1.5 * s)),
                                          tag="armor"))
        m.add(pre + "track", *saw((xo + 0.03 * s, 0.52, 0.0), (0, 0, 1), (0, -1, 0), 4.6, teeth=8, depth=0.1))
        for i, z in enumerate((-1.5, -0.5, 0.5, 1.5)):
            g_ = f"{pre}w{i}"
            m.add(g_, mk.cylinder((1.36 * s, 0.38, z), (1.46 * s, 0.38, z), 0.32, n=6, tag="armor"))
            wheels.append({"group": g_, "pivot": r3((1.41 * s, 0.38, z)), "radius": 0.32})
    m.add("ring", cyl((0, 1.3, 0), (0, 1.62, 0), 0.6, n=6, tag="armor2"))
    finish(m, SCHEME, size=512, grime_top=1.0)
    meta = {"slot": "legs", "display_name": "LG-C5 Lynx", "maker": MAKER, "tier": 3, "weight": 30.0, "armor": 520,
            "points": {"hip": [0, 1.6, 0]},
            "stats": {"type": "tank", "load_limit": 90, "walk_speed": 0.52, "boost_multiplier": 1.15, "jump": 0.0,
                      "turn_rate": 12, "step_height": 1.05, "traction": 0.31, "qb_multiplier": 0.8,
                      "hover_multiplier": 0.75, "collision_width": 3.0, "pelvis_groups": ["ring"], "wheels": wheels}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def facet_core(m, group="core", w=1.0):
    """The faceted torso: a chined waist rising into a diamond chest (plan pointing forward), a flat
    sloped breastplate, faceted shoulder cowls on hex axles, a chined hump carrying the booster."""
    m.add(group, chine_stack([(-0.1, 0, 0.0, 0.26 * w, 0.26, 0.24, {"k": 0.45}), (0.42, 0, 0.0, 0.3 * w, 0.28, 0.26, {"k": 0.45})],
                             "armor2"))
    m.add(group, chine_stack([(0.36, 0, 0.0, 0.5 * w, 0.48, 0.4, {"k": 0.3, "kb": 0.6}),
                              (0.82, 0, 0.04, 0.78 * w, 0.7, 0.5, {"k": 0.3, "kb": 0.6}),
                              (1.26, 0, 0.02, 0.76 * w, 0.62, 0.5, {"k": 0.3, "kb": 0.6}),
                              (1.5, 0, -0.04, 0.42 * w, 0.36, 0.36, {"k": 0.3, "kb": 0.6})], "armor"))
    # breastplate: a flat facet tilted back, red line along its lower edge
    m.add(group, slab([(-0.44, 0.0), (0.44, 0.0), (0.26, 0.42), (-0.26, 0.42)], (0, 0.86, 0.66), (1, 0, 0), (0, 0.75, -0.66), 0.05,
                      "armor2"))
    m.add(group, slab([(-0.4, 0.0), (0.4, 0.0), (0.36, 0.05), (-0.36, 0.05)], (0, 0.84, 0.69), (1, 0, 0), (0, 0.75, -0.66), 0.05,
                      "accent"))
    for s in (1, -1):
        m.add(group, cyl((0.62 * s * w, 1.24, 0), (1.06 * s * w, 1.24, 0), 0.16, n=6, tag="frame"))
        m.add(group, chine_seg([(0.58 * s * w, 1.38, 0.0), (1.04 * s * w, 1.36, 0.0)], [(0.26, 0.2, 0.06), (0.24, 0.16, 0.06)], "armor2",
                               up=(0, 1, 0), k=0.5))
        # pylon for the back weapon
        m.add(group, chine_seg([(0.34 * s * w, 1.36, -0.42), (0.34 * s * w, 1.5, -0.5)], [(0.1, 0.1, 0.1), (0.09, 0.08, 0.08)], "frame",
                               up=(0, 0, 1)))
    # centre ridge down the chest and two dark intake slits beside it
    m.add(group, chine_seg([(0, 1.34, 0.5), (0, 0.9, 0.78)], [(0.08, 0.04, 0.04), (0.06, 0.03, 0.03)], "armor2", up=(0, 0.5, 1)))
    for s in (1, -1):
        m.add(group, slab([(-0.12, 0.0), (0.12, 0.0), (0.1, 0.05), (-0.1, 0.05)], (0.3 * s * w, 1.14, 0.62), (1, 0, 0), (0, 0.6, -0.8),
                          0.04, "frame"))
        # upper shoulder facet over the cowl
        m.add(group, chine_seg([(0.66 * s * w, 1.56, -0.06), (1.0 * s * w, 1.5, -0.08)], [(0.2, 0.1, 0.04), (0.16, 0.08, 0.04)], "armor",
                               up=(0, 1, 0.2), k=0.5))
    # hump: chined block behind the chest, flat tail face for the booster
    m.add(group, chine_seg([(0, 0.88, -0.3), (0, 0.9, -0.78)], [(0.42 * w, 0.36, 0.3), (0.34 * w, 0.28, 0.24)], "armor", up=(0, 1, 0),
                           k=0.5))
    m.add(group, cyl((0, 1.46, 0.0), (0, 1.62, 0.0), 0.1, n=6, tag="frame"))
    return {"neck": [0, 1.6, 0.0], "shoulder_l": [1.04 * w, 1.24, 0], "back": [0, 0.9, -0.78],
            "back_l": [0.34 * w, 1.56, -0.5], "seat": [0, 0.84, 0.06], "eye": [0, 1.84, 0.3]}


def core_facet():
    """CR-C2 Facet: faceted medium core - balanced armour and EN."""
    m = mk.Model("cr_facet")
    pts = facet_core(m)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-C2 Facet", "maker": MAKER, "tier": 3, "weight": 16.0, "armor": 560,
            "points": pts,
            "stats": {"en_capacity": 1750, "en_recharge": 36, "recharge_delay": 11, "defense": 1.0, "fuel": 800,
                      "stability": 1.0}}
    return m, meta


# ---------------------------------------------------------------------------------- head

def head_slit():
    """HD-C2 Slit: a low faceted wedge with a single horizontal amber slit; nothing sticks out."""
    m = mk.Model("hd_slit")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=6, tag="frame"))
    m.add("head", chine_stack([(0.02, 0, 0.0, 0.18, 0.3, 0.22, {"k": 0.3}), (0.18, 0, 0.0, 0.22, 0.36, 0.26, {"k": 0.3}),
                               (0.32, 0, -0.04, 0.16, 0.22, 0.24, {"k": 0.3}), (0.4, 0, -0.08, 0.08, 0.1, 0.16, {"k": 0.3})], "armor"))
    # slit: a thin band set into the front facets
    m.add("head", chine_stack([(0.19, 0, 0.0, 0.214, 0.352, 0.1, {"k": 0.3}), (0.23, 0, 0.0, 0.208, 0.34, 0.1, {"k": 0.3})], "eye"))
    m.add("head", slab([(0.0, 0.0), (-0.3, 0.0), (-0.36, 0.12), (-0.1, 0.1)], (0, 0.36, -0.05), (0, 0, 1), (0, 1, 0), 0.03, "accent"))
    for s in (1, -1):
        m.add("head", slab([(0.1, 0.0), (-0.22, 0.0), (-0.3, 0.1), (0.0, 0.08)], (0.2 * s, 0.22, -0.08), (0, 0, 1), (s * 0.4, 1, 0), 0.03,
                           "armor2"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-C2 Slit", "maker": MAKER, "tier": 3, "weight": 2.2, "armor": 120,
            "points": {"top": [0, 0.42, 0]}, "stats": {"lock_range": 120, "lock_cone": 15, "stability": 1.0}}
    return m, meta


# ---------------------------------------------------------------------------------- arms

def shard_arm(elbow, grip, k=1.0):
    """Left faceted arm, shoulder at the origin: chined cowl, chined upper arm, hex elbow, a forearm
    with a flat swept outer blade and a sawtooth hem."""
    elbow = np.asarray(elbow, float)
    grip = np.asarray(grip, float)
    upper = [cyl((-0.06, 0, 0), (0.18, 0, 0), 0.15, n=6, tag="frame"),
             chine_seg([(0.02, 0.12, 0.0), (0.4, 0.04, 0.0)], [(0.3 * k, 0.24, 0.12), (0.24 * k, 0.16, 0.1)], "armor", up=(0, 1, 0), k=0.5),
             chine_seg([(elbow[0], -0.14, 0.0), (elbow[0], elbow[1] + 0.12, elbow[2])], [(0.1 * k, 0.1, 0.1), (0.09 * k, 0.09, 0.09)], "armor2",
                       up=(0, 0, 1)),
             cyl((elbow[0] - 0.13, elbow[1], elbow[2]), (elbow[0] + 0.13, elbow[1], elbow[2]), 0.12, n=6, tag="frame")]
    fore = [chine_seg([elbow + [0, 0, -0.08], elbow + (grip - elbow) * 0.5, grip + [0, 0.04, -0.14]],
                      [(0.13 * k, 0.16, 0.12), (0.14 * k, 0.18, 0.12), (0.11 * k, 0.12, 0.1)], "armor", up=(0, 1, 0), k=0.5),
            cbox(grip[0], grip[1] + 0.02, grip[2], 0.2, 0.2, 0.2, "frame", c=0.04),
            slab([(-0.08, 0.0), (0.56, 0.0), (0.66, 0.12), (0.02, 0.18)], elbow + [0.16, 0.02, 0.0], (0, 0, 1), (0, 1, 0), 0.04, "armor2")]
    fore += saw(elbow + (grip - elbow) * 0.45 + [0.17, -0.02, 0], (0, 0, 1), (0, -1, 0), 0.4, teeth=3, depth=0.07)
    return upper, fore


def arms_shard():
    """AM-C2 Shard: faceted medium arms."""
    m = mk.Model("am_shard")
    elbow, grip = (0.12, -0.98, 0.06), (0.12, -1.06, 0.95)
    upper, fore = shard_arm(elbow, grip)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-C2 Shard", "maker": MAKER, "tier": 3, "weight": 10.5, "armor": 280,
            "points": {"elbow": list(elbow), "grip": list(grip)},
            "stats": {"load_limit": 20, "aim_speed": 400, "recoil_control": 1.0}}
    return m, meta


# ---------------------------------------------------------------------------------- booster

def booster_shroud():
    """BS-C2 Shroud: a flat chined pack; two slot nozzles low on its sides, each under a canted fin
    (no glowing bells in view). Balanced boost, frugal hover."""
    m = mk.Model("bs_shroud")
    m.add("booster", chine_stack([(-0.5, 0, -0.1, 0.36, 0.16, 0.14, {"k": 0.5}), (0.0, 0, -0.12, 0.46, 0.18, 0.16, {"k": 0.5}),
                                  (0.46, 0, -0.1, 0.34, 0.16, 0.14, {"k": 0.5})], "armor2"))
    noz = []
    for s in (1, -1):
        c = np.array([0.3 * s, -0.46, -0.3])
        m.add("booster", chine_seg([(0.2 * s, 0.1, -0.2), c + [0, 0.06, 0.04]], [(0.12, 0.12, 0.1), (0.14, 0.08, 0.08)], "armor",
                                   up=(0, 0, -1)))
        m.add("booster", slot_nozzle(c, (1, 0, 0), (0, -0.6, -0.8), 0.16, 0.05, 0.14))
        # tall canted fin rising above the shoulder (a crow's folded wingtip), red tip
        m.add("booster", slab([(0.0, -0.5), (0.0, 0.3), (0.18, 1.25), (0.36, 1.2), (0.3, -0.36)], (0.3 * s, 0.2, -0.28),
                              (s * 0.6, 0.0, -0.8), (s * 0.25, 1, 0.1), 0.04, "armor"))
        m.add("booster", slab([(0.12, 0.95), (0.18, 1.25), (0.36, 1.2), (0.33, 0.9)], (0.3 * s, 0.2, -0.28) + np.array([0, 0, -0.012]),
                              (s * 0.6, 0.0, -0.8), (s * 0.25, 1, 0.1), 0.05, "accent"))
        noz.append({"pos": r3(c + np.array([0, -0.12, -0.16])), "dir": [0, -0.6, -0.8], "size": 0.3})
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-C2 Shroud", "maker": MAKER, "tier": 3, "weight": 5.0, "armor": 110,
            "stats": {"boost_speed": 1.02, "qb_speed": 2.5, "qb_cost": 290, "qb_cooldown": 10, "hover_thrust": 0.16,
                      "hover_cost": 11, "air_boost_cost": 5, "nozzles": noz}}
    return m, meta


ALL = [legs_raven, legs_recluse, legs_lynx, core_facet, head_slit, arms_shard, booster_shroud]
