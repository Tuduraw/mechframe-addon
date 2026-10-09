"""Talos Heavy Machinery - fortress frames (cast steel blue-grey / gunmetal / brass, cold white optics).

Concept: "a castle that walks". Talos does not slope its armour like Grauwerk's tank glacis - it
casts it. Every main mass is one thick, rounded-square casting; joints are buried under layered
skirts and kneecaps; the head sits low between the shoulders and looks out through narrow vision
blocks. The heaviest, slowest frames of the field: the most AP and load, the weakest quick boost.

Design rules (what makes a Talos part read as one):
  - cast masses (meshkit.mech.cast / cast_seg, superellipse p ~2.6): thick, rounded-square;
  - joints covered: layered skirt plates (2-3 overlapping rows) over hips, shoulders, knees;
  - brass bands where a casting ends; vision blocks as small recessed lights, never a visor;
  - wide and low: shoulders wider than the legs' stance, the head below the shoulder line."""
import numpy as np

from common import MAKERS
from mg import mk, stack, seg, cyl, cbox, mirror, finish, r3, slab, plate, hbox, cast, cast_seg, nozzle

SCHEME = "th"
MAKER = MAKERS["th"]


def skirt(center, u, v, hu, hv, rows=3, step=0.14, t=0.07, tag="armor2", out=None):
    """Layered skirt: `rows` plates hanging along -v, each a little lower and further out (along
    out, default u x v) than the one above - lamellar armour over a joint."""
    center = np.asarray(center, float)
    u = np.asarray(u, float) / np.linalg.norm(u)
    v = np.asarray(v, float) / np.linalg.norm(v)
    n = np.cross(u, v) if out is None else np.asarray(out, float) / np.linalg.norm(out)
    shells = []
    for i in range(rows):
        c = center - v * step * i + n * 0.03 * i
        shells.append(plate(c, u, v, hu * (1 - 0.04 * i), hv, t, "accent" if i == rows - 1 and False else tag, c=0.05))
    return shells


def band(points, sizes, n=10):
    """Brass band: a short cast ring just proud of a casting's end."""
    return cast_seg(points, sizes, "accent", n=n)


def vision_block(center, u, v, w, h, t=0.05):
    """A vision block: a small light in a dark frame."""
    center = np.asarray(center, float)
    nrm = np.cross(np.asarray(u, float), np.asarray(v, float))
    nrm = nrm / np.linalg.norm(nrm)
    u = np.asarray(u, float)
    v = np.asarray(v, float)
    return [slab([(-w - 0.04, -h - 0.04), (w + 0.04, -h - 0.04), (w + 0.04, h + 0.04), (-w - 0.04, h + 0.04)], center, u, v, t, "frame"),
            slab([(-w, -h), (w, -h), (w, h), (-w, h)], center + nrm * t * 0.5, u, v, t * 0.6, "eye")]


# ---------------------------------------------------------------------------------- legs

def colossus_leg(s, hip, knee, ankle, k=1.0):
    """One fortress leg: cast thigh under a 3-row hip skirt, a cast kneecap with a brass band, a cast
    column of a shin with a layered greave, a broad cast foot."""
    thigh = [cyl((hip[0] - 0.36 * s, hip[1], hip[2]), (hip[0] + 0.36 * s, hip[1], hip[2]), 0.3 * k, n=10, tag="frame"),
             cast_seg([hip + [0, 0.05, -0.02], (hip + knee) / 2 + [0, 0, 0.04], knee + [0, 0.22, 0.0]],
                      [(0.38 * k, 0.42 * k), (0.42 * k, 0.46 * k), (0.32 * k, 0.36 * k)], "armor", n=10)]
    thigh += skirt(hip + [0.22 * s * k, -0.15, 0.1], (0, 0, 1), (0, 1, 0), 0.4 * k, 0.28, rows=2, out=(s, 0, 0.2))
    shin = [cyl((knee[0] - 0.32 * s, knee[1], knee[2]), (knee[0] + 0.32 * s, knee[1], knee[2]), 0.27 * k, n=10, tag="frame"),
            # kneecap: a cast dome over the joint, a brass band round its rim
            cast_seg([knee + [0, -0.22, 0.2], knee + [0, 0.26, 0.4]], [(0.34 * k, 0.24 * k), (0.24 * k, 0.16 * k)], "armor2", n=10),
            band([knee + [0, -0.26, 0.19], knee + [0, -0.18, 0.22]], [(0.355 * k, 0.255 * k), (0.355 * k, 0.255 * k)], n=10),
            cast_seg([knee + [0, -0.05, 0.0], knee + (ankle - knee) * 0.45, ankle + [0, 0.2, 0.0]],
                     [(0.4 * k, 0.46 * k), (0.44 * k, 0.5 * k), (0.34 * k, 0.38 * k)], "armor", n=10)]
    # greave: layered plates down the shin front
    g0 = knee + (ankle - knee) * 0.2 + [0, 0, 0.46 * k]
    shin += skirt(g0, (1, 0, 0), (0, 1, -0.12), 0.34 * k, 0.24, rows=3, step=0.3, out=(0, 0.1, 1))
    foot = [cyl((ankle[0] - 0.24 * s, ankle[1], ankle[2]), (ankle[0] + 0.24 * s, ankle[1], ankle[2]), 0.24, n=8, tag="frame"),
            cast([(0.0, ankle[0], ankle[2] + 0.12, 0.5 * k, 0.86 * k, 0.6 * k), (0.32, ankle[0], ankle[2] + 0.1, 0.48 * k, 0.8 * k, 0.56 * k),
                  (ankle[1] + 0.1, ankle[0], ankle[2], 0.3 * k, 0.4 * k, 0.34 * k)], "armor2", n=10, axis="y"),
            plate((ankle[0], 0.26, ankle[2] + 0.9 * k), (1, 0, 0), (0, 0.7, 0.7), 0.4 * k, 0.16, 0.07, "accent", c=0.04)]
    return thigh, shin, foot


def legs_colossus():
    """LG-T3 Colossus: fortress biped - cast column legs under layered skirts. Hip 3.3 m."""
    m = mk.Model("lg_colossus")
    H = 3.3
    m.add("pelvis", cast([(H - 0.34, 0, 0.0, 0.6, 0.42, 0.42), (H - 0.1, 0, 0.0, 0.82, 0.52, 0.5),
                          (H + 0.3, 0, -0.02, 0.8, 0.5, 0.48), (H + 0.42, 0, -0.04, 0.56, 0.36, 0.34)], "armor2", n=10, axis="y"))
    m.add("pelvis", *skirt((0, H + 0.1, 0.52), (1, 0, 0), (0, 1, -0.2), 0.42, 0.26, rows=3, step=0.16, out=(0, 0.2, 1)))
    m.add("pelvis", band([(0, H + 0.36, 0.0), (0, H + 0.44, 0.0)], [(0.82, 0.5), (0.8, 0.48)]))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.68 * s, H, 0.0])
        knee = np.array([0.72 * s, 1.86, 0.4])
        ankle = np.array([0.72 * s, 0.5, -0.02])
        thigh, shin, foot = colossus_leg(s, hip, knee, ankle)
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=1.4)
    meta = {"slot": "legs", "display_name": "LG-T3 Colossus", "maker": MAKER, "tier": 4, "weight": 46.0, "armor": 900,
            "points": {"hip": [0, H + 0.42, 0]},
            "stats": {"type": "biped", "load_limit": 130, "walk_speed": 0.22, "boost_multiplier": 0.85, "jump": 0.6,
                      "turn_rate": 10, "step_height": 1.3, "traction": 0.42, "qb_multiplier": 0.7,
                      "hover_multiplier": 0.7, "collision_width": 2.4, "step_length": 1.8, "step_lift": 0.4,
                      "step_ticks": 9, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def legs_mammoth():
    """LG-T4 Mammoth: fortress quad - a cast hull on four column legs; the knees barely rise above
    the hull, the shins stand nearly upright on round cast foot pads, like an elephant's."""
    m = mk.Model("lg_mammoth")
    y0 = 2.2
    m.add("pelvis", cast([(y0 - 0.5, 0, 0.0, 1.0, 1.25, 1.2), (y0 - 0.1, 0, 0.0, 1.25, 1.5, 1.45),
                          (y0 + 0.45, 0, 0.0, 1.2, 1.42, 1.38), (y0 + 0.7, 0, -0.05, 0.86, 1.0, 0.96)], "armor", n=10, axis="y"))
    m.add("pelvis", band([(0, y0 + 0.62, -0.03), (0, y0 + 0.72, -0.04)], [(0.9, 1.04), (0.86, 1.0)]))
    m.add("pelvis", *skirt((0, y0 + 0.15, 1.5), (1, 0, 0), (0, 1, -0.25), 0.7, 0.24, rows=2, step=0.2, out=(0, 0.25, 1)))
    m.add("pelvis", *vision_block((0, y0 + 0.4, 1.47), (1, 0, 0), (0, 1, -0.3), 0.3, 0.05))
    chains = []
    corners = [("fl", 1, 1, 0), ("fr", -1, 1, 1), ("rl", 1, -1, 1), ("rr", -1, -1, 0)]
    for name, sx, sz, group in corners:
        d = np.array([sx * 1.0, 0.0, sz * 0.75])
        d /= np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        cp = np.array([0.9 * sx, y0 - 0.1, 1.0 * sz])
        hip = cp + d * 0.42
        knee = hip + d * 0.9 + [0, 0.35, 0]
        ankle = hip + d * 1.4 + [0, -(y0 - 0.1) + 0.42, 0]
        m.add(name + "_coxa", cyl(cp + [0, -0.34, 0], cp + [0, 0.34, 0], 0.36, n=10, tag="frame"),
              cast_seg([cp, hip + d * 0.12], [(0.32, 0.34), (0.3, 0.3)], "armor2", side=tuple(side), n=10))
        m.add(name + "_femur", cyl(hip - side * 0.34, hip + side * 0.34, 0.3, n=10, tag="frame"),
              cast_seg([hip, (hip + knee) / 2 + [0, 0.1, 0], knee], [(0.34, 0.4), (0.38, 0.44), (0.3, 0.34)], "armor", side=tuple(side), n=10),
              *skirt(hip + [0, 0.45, 0] + d * 0.3, tuple(side), (0, 1, 0), 0.36, 0.2, rows=1, step=0.14, out=tuple(d + [0, 0.4, 0])))
        tib = [cyl(knee - side * 0.32, knee + side * 0.32, 0.28, n=10, tag="frame"),
               cast_seg([knee + [0, 0.1, 0], knee + (ankle - knee) * 0.5, ankle + [0, 0.24, 0]], [(0.4, 0.4), (0.44, 0.44), (0.36, 0.36)],
                        "armor", side=tuple(side), n=8),
               band([knee + (ankle - knee) * 0.7, knee + (ankle - knee) * 0.76], [(0.43, 0.43), (0.42, 0.42)]),
               cast([(0.0, ankle[0], ankle[2], 0.56, 0.56, 0.56), (0.3, ankle[0], ankle[2], 0.54, 0.54, 0.54),
                     (0.5, ankle[0], ankle[2], 0.42, 0.42, 0.42)], "armor2", n=8, axis="y")]
        m.add(name + "_tibia", *tib)
        chains.append({"name": name, "coxa": name + "_coxa", "coxa_pivot": r3(cp), "upper": name + "_femur",
                       "lower": name + "_tibia", "hip": r3(hip), "knee": r3(knee), "ankle": r3([ankle[0], 0.0, ankle[2]]),
                       "pole": [0, 1, 0], "group": group})
    finish(m, SCHEME, size=512, grime_top=1.2)
    meta = {"slot": "legs", "display_name": "LG-T4 Mammoth", "maker": MAKER, "tier": 4, "weight": 52.0, "armor": 1000,
            "points": {"hip": [0, y0 + 0.72, 0]},
            "stats": {"type": "quad", "load_limit": 150, "walk_speed": 0.24, "boost_multiplier": 0.85, "jump": 0.5,
                      "turn_rate": 10, "step_height": 1.6, "traction": 0.45, "qb_multiplier": 0.65,
                      "hover_multiplier": 0.9, "collision_width": 3.2, "step_length": 1.2, "step_lift": 0.6,
                      "step_ticks": 8, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def legs_bulwark():
    """LG-T5 Bulwark: fortress tank legs - a cast hull riding on FOUR tracks, two side by side on
    each flank, under layered side skirts. Nothing on the field carries more."""
    m = mk.Model("lg_bulwark")
    m.add("hull", cast([(-2.4, 0, 0.98, 1.05, 0.4, 0.36), (-1.0, 0, 1.05, 1.2, 0.5, 0.42), (1.2, 0, 1.0, 1.2, 0.46, 0.4),
                        (2.5, 0, 0.82, 1.0, 0.24, 0.3)], "armor", n=10))
    m.add("hull", *vision_block((0, 1.18, 2.42), (1, 0, 0), (0, 0.6, -0.8), 0.4, 0.05))
    wheels = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        for j, (xa, xb) in enumerate(((0.92, 1.38), (1.44, 1.9))):
            x0, x1 = sorted((xa * s, xb * s))
            prof = [(-2.5, 0.0), (2.4, 0.0), (2.9, 0.52), (2.7, 0.98), (-2.6, 0.98), (-2.85, 0.5)]
            m.add(f"{pre}track{j}", mk.extrude_zy(prof, x0, x1, tag="frame"))
            for i, z in enumerate((-1.7, -0.6, 0.5, 1.6)):
                g_ = f"{pre}t{j}w{i}"
                xm = (xa + xb) / 2 * s
                m.add(g_, mk.cylinder((xm - 0.26, 0.42, z), (xm + 0.26, 0.42, z), 0.4, n=6, tag="armor2"))
                wheels.append({"group": g_, "pivot": r3((xm, 0.42, z)), "radius": 0.4})
        # layered skirt over both tracks: three rows of cast plates
        for z in (-1.5, 0.0, 1.5):
            m.add(f"{pre}track1", *skirt((1.96 * s, 1.0, z), (0, 0, 1), (0, 1, 0), 0.78, 0.26, rows=2, step=0.2, out=(s, 0, 0)))
        m.add(f"{pre}track1", cbox(1.4 * s, 1.08, 0.0, 1.06, 0.2, 5.0, "armor2", c=0.06))
        m.add(f"{pre}track1", band([(1.98 * s, 1.36, -2.3), (1.98 * s, 1.36, 2.3)], [(0.1, 0.06), (0.1, 0.06)], n=8))
    m.add("ring", cast([(1.42, 0, 0.0, 0.86, 0.86, 0.86), (1.82, 0, 0.0, 0.8, 0.8, 0.8)], "armor2", n=10, axis="y"))
    finish(m, SCHEME, size=512, grime_top=1.2)
    meta = {"slot": "legs", "display_name": "LG-T5 Bulwark", "maker": MAKER, "tier": 4, "weight": 60.0, "armor": 1150,
            "points": {"hip": [0, 1.8, 0]},
            "stats": {"type": "tank", "load_limit": 175, "walk_speed": 0.36, "boost_multiplier": 0.95, "jump": 0.0,
                      "turn_rate": 9, "step_height": 1.1, "traction": 0.28, "qb_multiplier": 0.6,
                      "hover_multiplier": 0.55, "collision_width": 3.8, "pelvis_groups": ["ring"], "wheels": wheels}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def keep_core(m, group="core", w=1.0):
    """The fortress torso: one cast body wider than it is tall, a thick frontal casting with a row
    of vision blocks, cast pauldron mounts, a collar the head sits down into, a cast hump behind."""
    m.add(group, cast([(-0.12, 0, 0.0, 0.5 * w, 0.4, 0.4), (0.3, 0, 0.0, 0.62 * w, 0.48, 0.46),
                       (0.7, 0, 0.04, 1.0 * w, 0.66, 0.56), (1.3, 0, 0.02, 1.02 * w, 0.62, 0.58),
                       (1.62, 0, -0.04, 0.74 * w, 0.46, 0.44)], "armor", n=10, axis="y"))
    # frontal casting: a thick slab with three vision blocks, brass band along its top
    m.add(group, cast_seg([(0, 0.62, 0.56), (0, 1.3, 0.62)], [(0.66 * w, 0.14), (0.6 * w, 0.12)], "armor2", n=10))
    for x in (-0.3, 0.0, 0.3):
        m.add(group, *vision_block((x * w, 1.06, 0.76), (1, 0, 0), (0, 1, 0), 0.08, 0.035))
    m.add(group, band([(0, 1.3, 0.62), (0, 1.36, 0.62)], [(0.62 * w, 0.13), (0.6 * w, 0.12)], n=10))
    # collar: the head sits down in it
    m.add(group, cast([(1.5, 0, 0.0, 0.42, 0.38, 0.36), (1.74, 0, 0.0, 0.38, 0.34, 0.32)], "armor2", n=10, axis="y"))
    for s in (1, -1):
        m.add(group, cyl((0.8 * s * w, 1.24, 0), (1.26 * s * w, 1.24, 0), 0.3, n=10, tag="frame"))
        # weapon pylon on the hump
        m.add(group, cbox(0.5 * s * w, 1.62, -0.48, 0.3, 0.2, 0.36, "armor2", c=0.05))
    m.add(group, cast_seg([(0, 0.95, -0.4), (0, 0.98, -0.84)], [(0.66 * w, 0.48), (0.54 * w, 0.4)], "armor", n=10))
    return {"neck": [0, 1.6, 0.0], "shoulder_l": [1.26 * w, 1.24, 0], "back": [0, 0.98, -0.86],
            "back_l": [0.5 * w, 1.72, -0.48], "seat": [0, 0.9, 0.08], "eye": [0, 2.0, 0.3]}


def core_keep():
    """CR-T3 Keep: fortress core - the most armour and EN, slow to recharge."""
    m = mk.Model("cr_keep")
    pts = keep_core(m)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-T3 Keep", "maker": MAKER, "tier": 4, "weight": 28.0, "armor": 980,
            "points": pts,
            "stats": {"en_capacity": 2200, "en_recharge": 26, "recharge_delay": 15, "defense": 1.25, "fuel": 950,
                      "stability": 1.3}}
    return m, meta


# ---------------------------------------------------------------------------------- head

def head_bascinet():
    """HD-T3 Bascinet: a small cast helmet sunk into the collar - a pointed visor of cast plate with
    two vision slits, a brass crest. Short lock range, very stable."""
    m = mk.Model("hd_bascinet")
    m.add("head", cyl((0, -0.1, 0), (0, 0.06, 0), 0.14, n=8, tag="frame"))
    m.add("head", cast([(-0.02, 0, 0.0, 0.26, 0.3, 0.28), (0.18, 0, 0.0, 0.3, 0.36, 0.32), (0.36, 0, -0.03, 0.22, 0.24, 0.26),
                        (0.44, 0, -0.05, 0.1, 0.12, 0.14)], "armor", n=10, axis="y"))
    m.add("head", cast_seg([(0, 0.18, 0.2), (0, 0.18, 0.46)], [(0.18, 0.12), (0.04, 0.03)], "armor2", n=8))
    for s in (1, -1):
        m.add("head", *vision_block((0.13 * s, 0.22, 0.27), (1, 0, -0.8 * s), (0, 1, 0), 0.05, 0.018, t=0.03))
    m.add("head", cast_seg([(0, 0.3, 0.2), (0, 0.44, -0.08), (0, 0.38, -0.3)], [(0.03, 0.06), (0.03, 0.07), (0.025, 0.04)], "accent", n=6))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-T3 Bascinet", "maker": MAKER, "tier": 4, "weight": 3.6, "armor": 220,
            "points": {"top": [0, 0.46, 0]}, "stats": {"lock_range": 100, "lock_cone": 12, "stability": 1.3}}
    return m, meta


# ---------------------------------------------------------------------------------- arms

def gauntlet_arm(elbow, grip, k=1.0):
    """Left fortress arm: a huge cast pauldron with a 3-row skirt, a cast upper arm, a cast elbow
    ball, a thick forearm casting with a brass cuff, a heavy fist."""
    elbow = np.asarray(elbow, float)
    grip = np.asarray(grip, float)
    upper = [cyl((-0.1, 0, 0), (0.3, 0, 0), 0.3 * k, n=10, tag="frame"),
             cast([(-0.3, 0.3, 0.0, 0.46 * k, 0.5 * k, 0.5 * k), (0.15, 0.32, 0.0, 0.5 * k, 0.56 * k, 0.56 * k),
                   (0.5, 0.26, -0.02, 0.36 * k, 0.4 * k, 0.4 * k)], "armor", n=10, axis="y"),
             cast_seg([(elbow[0], -0.2, 0.0), (elbow[0], elbow[1] + 0.16, elbow[2])], [(0.22 * k, 0.24 * k), (0.2 * k, 0.22 * k)], "armor2", n=10),
             mk.ellipsoid(tuple(elbow), 0.24 * k, 0.24 * k, 0.24 * k, nu=8, nv=5, tag="frame")]
    upper += skirt((0.42 * k, -0.14, 0.0), (0, 0, 1), (0, 1, 0), 0.46 * k, 0.2, rows=2, step=0.15, out=(1, 0, 0))
    fore = [cast_seg([elbow + [0, 0, -0.1], elbow + (grip - elbow) * 0.5, grip + [0, 0.04, -0.2]],
                     [(0.26 * k, 0.3 * k), (0.3 * k, 0.32 * k), (0.25 * k, 0.26 * k)], "armor", side=(1, 0, 0), n=10),
            band([grip + [0, 0.04, -0.26], grip + [0, 0.04, -0.18]], [(0.27 * k, 0.28 * k), (0.27 * k, 0.28 * k)]),
            cbox(grip[0], grip[1] + 0.02, grip[2], 0.36 * k, 0.36 * k, 0.34, "frame", c=0.06),
            plate((elbow[0] + 0.3 * k, elbow[1] - 0.02, (elbow[2] + grip[2]) / 2), (0, 1, 0), (0, 0, 1), 0.3 * k, 0.5, 0.08, "armor2", c=0.06)]
    return upper, fore


def arms_gauntlet():
    """AM-T3 Gauntlet: fortress arms - the heaviest load, the slowest aim, recoil soaked up."""
    m = mk.Model("am_gauntlet")
    elbow, grip = (0.14, -1.0, 0.06), (0.14, -1.1, 1.0)
    upper, fore = gauntlet_arm(elbow, grip)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-T3 Gauntlet", "maker": MAKER, "tier": 4, "weight": 18.0, "armor": 460,
            "points": {"elbow": list(elbow), "grip": list(grip)},
            "stats": {"load_limit": 36, "aim_speed": 260, "recoil_control": 1.6}}
    return m, meta


# ---------------------------------------------------------------------------------- booster

def booster_furnace():
    """BS-T3 Furnace: a cast block with four square nozzles and stacked radiator fins - raw thrust to
    move the weight, a weak quick boost, a short hover."""
    m = mk.Model("bs_furnace")
    m.add("booster", cast([(0.0, 0, 0.2, 0.62, 0.62, 0.3), (-0.3, 0, -0.1, 0.66, 0.66, 0.36), (-0.6, 0, -0.06, 0.52, 0.52, 0.3)],
                          "armor2", n=10))
    noz = []
    for sx in (1, -1):
        for sy in (1, -1):
            p = np.array([0.32 * sx, -0.18 + 0.3 * sy, -0.56])
            m.add("booster", cbox(p[0], p[1], p[2] + 0.02, 0.26, 0.26, 0.16, "frame", c=0.03))
            m.add("booster", nozzle(p + [0, -0.02, -0.06], (0, -0.35, -0.94), 0.12, 0.22, n=4))
            noz.append({"pos": r3(p + np.array([0, -0.1, -0.3])), "dir": [0, -0.35, -0.94], "size": 0.32})
    for i in range(4):
        y = 0.62 - i * 0.12
        m.add("booster", plate((0, y, -0.1), (1, 0, 0), (0, 0, 1), 0.5, 0.3, 0.04, "metal", c=0.02))
    m.add("booster", band([(0, 0.2, -0.38), (0, 0.2, -0.46)], [(0.67, 0.67), (0.66, 0.66)]))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-T3 Furnace", "maker": MAKER, "tier": 4, "weight": 9.0, "armor": 200,
            "stats": {"boost_speed": 0.85, "qb_speed": 2.0, "qb_cost": 380, "qb_cooldown": 14, "hover_thrust": 0.13,
                      "hover_cost": 16, "air_boost_cost": 7, "nozzles": noz}}
    return m, meta


ALL = [legs_colossus, legs_mammoth, legs_bulwark, core_keep, head_bascinet, arms_gauntlet, booster_furnace]
