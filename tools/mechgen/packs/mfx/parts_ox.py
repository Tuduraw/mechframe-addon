"""Orrery Experimental - special-standard prototypes (pearl white / gold / teal glow).

Concept: parts that do not fit any standard frame, sold without one. Orrery builds what the big
makers will not: a legless hover skirt, arms with their weapons built in, a pile driver, a booster
that gives up hovering for the strongest quick boost there is, a long-range sensor head. None of them
comes in a stock frame - they exist to be mixed into other makers' frames.

Design rules: rings and spheres (an orrery's armillary bands), gold bands on pearl-white shells,
teal light where energy flows (fans, rails, coils); exposed mechanisms framed like instruments."""
import math

import numpy as np

from common import MAKERS, NS
from mg import mk, stack, seg, cyl, cbox, mirror, finish, r3, slab, plate, cast, cast_seg, nozzle, tube

SCHEME = "ox"
MAKER = MAKERS["ox"]


def ring(center, axis, r, w, tube_r, n=12, tag="armor2"):
    """A flat band ring (an armillary band) around `axis`: n flat segments (each a 4-sided prism,
    12 triangles), radius r, band width w along the axis, thickness 2 x tube_r."""
    center = np.asarray(center, float)
    axis = np.asarray(axis, float) / np.linalg.norm(axis)
    a = np.cross(axis, [0, 1, 0] if abs(axis[1]) < 0.9 else [1, 0, 0])
    a /= np.linalg.norm(a)
    b = np.cross(axis, a)
    out = []
    for k in range(n):
        t0 = 2 * math.pi * (k - 0.04) / n
        t1 = 2 * math.pi * (k + 1.04) / n
        d0 = math.cos(t0) * a + math.sin(t0) * b
        d1 = math.cos(t1) * a + math.sin(t1) * b
        quad = [center + d0 * (r - tube_r), center + d0 * (r + tube_r), center + d1 * (r + tube_r), center + d1 * (r - tube_r)]
        out.append(mk.prism([q - axis * w / 2 for q in quad], [q + axis * w / 2 for q in quad], tag=tag))
    return out


# ---------------------------------------------------------------------------------- hover legs

def legs_halo():
    """LG-X1 Halo: hover unit - a pearl skirt riding on four ducted lift fans, a gold band and a
    teal halo of light underneath; a column carries the waist. No legs: no steps, no jumps - it
    glides, drifts in turns and slides on when let go (special standard: leg type "hover")."""
    m = mk.Model("lg_halo")
    g = "skirt"
    # skirt: a wide shallow lathe, its underside 0.42 m off the ground
    m.add(g, mk.lathe((0, 0.42, 0), (0, 1, 0), [(0.0, 1.0), (0.14, 1.32), (0.36, 1.36), (0.58, 1.1), (0.76, 0.5)], n=12, tag="armor"))
    m.add(g, *ring((0, 0.78, 0), (0, 1, 0), 1.33, 0.12, 0.04, n=12, tag="armor2"))
    # halo of light under the skirt, and four lift fan ducts round it
    m.add(g, mk.lathe((0, 0.38, 0), (0, 1, 0), [(0.0, 1.1), (0.06, 1.12), (0.08, 0.9)], n=12, tag="eye"))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        c = np.array([math.cos(a) * 1.12, 0.62, math.sin(a) * 1.12])
        m.add(g, cyl(c + [0, -0.28, 0], c + [0, 0.18, 0], 0.36, n=8, tag="armor2"))
        m.add(g, cyl(c + [0, -0.3, 0], c + [0, -0.24, 0], 0.3, n=8, tag="eye"))
    # column: the waist mount on a gold-banded neck
    m.add(g, cast([(1.1, 0, 0.0, 0.5, 0.5, 0.5), (1.5, 0, 0.0, 0.42, 0.42, 0.42), (1.7, 0, 0.0, 0.36, 0.36, 0.36)], "armor", n=10,
                  axis="y"))
    m.add(g, *ring((0, 1.46, 0), (0, 1, 0), 0.43, 0.08, 0.03, n=10, tag="armor2"))
    # front sensor nub
    m.add(g, cast_seg([(0, 0.82, 1.2), (0, 0.8, 1.46)], [(0.18, 0.1), (0.08, 0.05)], "accent", n=8))
    finish(m, SCHEME, size=512, grime_top=0.5)
    meta = {"slot": "legs", "display_name": "LG-X1 Halo", "maker": MAKER, "tier": 4, "weight": 18.0, "armor": 380,
            "points": {"hip": [0, 1.68, 0]},
            "stats": {"type": "hover", "load_limit": 62, "walk_speed": 0.46, "boost_multiplier": 1.25, "jump": 0.0,
                      "turn_rate": 16, "step_height": 1.05, "traction": 0.24, "qb_multiplier": 1.05,
                      "hover_multiplier": 1.6, "collision_width": 2.7, "pelvis_groups": ["skirt"], "chains": []}}
    return m, meta


# ---------------------------------------------------------------------------------- integrated-weapon arms

ARB_ELBOW = np.array([0.12, -0.92, 0.06])
ARB_GRIP = np.array([0.12, -1.0, 0.62])


def arms_arbalest():
    """AM-X2 Arbalest: arms whose forearms ARE rail launchers (special standard: built-in weapons).
    Both arm weapon mounts always carry the WI-X2 rail and take nothing else."""
    m = mk.Model("am_arbalest")
    e, g = ARB_ELBOW, ARB_GRIP
    upper = [cyl((-0.04, 0, 0), (0.16, 0, 0), 0.14, n=8, tag="frame"),
             mk.ellipsoid((0.12, 0.08, 0.0), 0.24, 0.24, 0.26, nu=8, nv=5, tag="armor"),
             *ring((0.12, 0.08, 0.0), (1, 0, 0), 0.25, 0.06, 0.025, n=10, tag="armor2"),
             cast_seg([(e[0], -0.14, 0.0), (e[0], e[1] + 0.12, e[2])], [(0.1, 0.1), (0.09, 0.09)], "armor", n=8),
             cyl((e[0] - 0.13, e[1], e[2]), (e[0] + 0.13, e[1], e[2]), 0.12, n=8, tag="frame")]
    # forearm: the rail's breech housing - a gold-banded block with teal coils
    fore = [cast_seg([e + [0, 0, -0.1], g + [0, 0.04, -0.04]], [(0.16, 0.18), (0.15, 0.17)], "armor", side=(1, 0, 0), n=8),
            *ring(e + (g - e) * 0.35 + [0, 0.02, 0], g - e, 0.19, 0.05, 0.03, n=8, tag="armor2"),
            *ring(e + (g - e) * 0.7 + [0, 0.03, 0], g - e, 0.18, 0.05, 0.03, n=8, tag="eye")]
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-X2 Arbalest", "maker": MAKER, "tier": 4, "weight": 8.0, "armor": 220,
            "points": {"elbow": r3(e), "grip": r3(g)},
            "stats": {"load_limit": 22, "aim_speed": 380, "recoil_control": 1.2,
                      "integrated": {"r_arm": f"{NS}:wi_arbalest", "l_arm": f"{NS}:wi_arbalest"}}}
    return m, meta


def weapon_arbalest():
    """WI-X2 Arbalest Rail: the rail built into the Arbalest arms - two long gold prongs with a teal
    field between them. Only exists as part of those arms (mount "integrated", no recipe)."""
    m = mk.Model("wi_arbalest")
    for sy in (1, -1):
        m.add("gun", seg([(0, 0.08 * sy, -0.02), (0, 0.09 * sy, 1.9)], [(0.05, 0.03, 0.01), (0.04, 0.025, 0.008)], "armor2", side=(1, 0, 0)))
    m.add("gun", seg([(0, 0.0, 0.2), (0, 0.0, 1.8)], [(0.015, 0.05, 0.004), (0.012, 0.05, 0.004)], "eye", side=(1, 0, 0)))
    for z in (0.3, 1.0, 1.7):
        m.add("gun", cbox(0, 0.0, z, 0.12, 0.24, 0.06, "frame", c=0.01))
    m.add("gun", cbox(0, 0.0, 0.0, 0.2, 0.3, 0.2, "armor", c=0.03))
    finish(m, SCHEME, size=128)
    meta = {"slot": "weapon", "display_name": "WI-X2 Arbalest Rail", "maker": MAKER, "tier": 4, "weight": 3.0, "armor": 30,
            "points": {"muzzle": [0.0, 0.0, 1.95]}, "no_recipe": True,
            "stats": {"weapon": "mfx_arbalest_rail", "mount": "integrated", "projectile_item": "minecraft:prismarine_crystals",
                      "recoil_distance": 0.18}}
    return m, meta


# ---------------------------------------------------------------------------------- pile driver

def weapon_piledriver():
    """WM-X3 Pile Driver: a thrust melee weapon - a gold sleeve over a hydraulic ram that drives a
    tungsten spike. The frame dashes in and punches it straight through; narrow, slow, devastating."""
    m = mk.Model("wm_piledriver")
    m.add("pile", cbox(0, 0.0, 0.06, 0.26, 0.3, 0.3, "frame", c=0.03))
    m.add("pile", cast_seg([(0, 0.06, 0.1), (0, 0.06, 1.0)], [(0.17, 0.17), (0.15, 0.15)], "armor", n=8))
    m.add("pile", *ring((0, 0.06, 0.24), (0, 0, 1), 0.18, 0.08, 0.03, n=8, tag="armor2"))
    m.add("pile", *ring((0, 0.06, 0.86), (0, 0, 1), 0.17, 0.08, 0.03, n=8, tag="armor2"))
    m.add("pile", cyl((0, 0.06, 0.96), (0, 0.06, 1.3), 0.08, n=6, tag="metal"))
    m.add("pile", mk.lathe((0, 0.06, 1.28), (0, 0, 1), [(0.0, 0.1), (0.12, 0.09), (0.44, 0.0)], n=6, tag="accent"))
    finish(m, SCHEME, size=128)
    meta = {"slot": "weapon", "display_name": "WM-X3 Pile Driver", "maker": MAKER, "tier": 4, "weight": 4.5, "armor": 60,
            "points": {"muzzle": [0.0, 0.06, 1.7]},
            "stats": {"weapon": "mfx_pile_driver", "mount": "arm", "projectile_item": "minecraft:iron_nugget", "recoil_distance": 0.0,
                      "fcs": False,
                      "melee": {"style": "thrust", "damage": 680, "reach": 3.4, "arc": 25, "lunge_speed": 1.8, "lunge_range": 12,
                                "en_cost": 460, "cooldown": 42}}}
    return m, meta


# ---------------------------------------------------------------------------------- booster

def booster_comet():
    """BS-X4 Comet: six radial thrusters on a gold ring around a pearl hub. The strongest quick boost
    of all, at a high EN price - and no hover at all (special standard: Space does nothing in the air)."""
    m = mk.Model("bs_comet")
    m.add("booster", cast([(0.04, 0, 0.0, 0.3, 0.3, 0.3), (-0.2, 0, 0.0, 0.36, 0.36, 0.36), (-0.42, 0, 0.0, 0.24, 0.24, 0.24)],
                          "armor", n=10))
    m.add("booster", *ring((0, 0, -0.22), (0, 0, 1), 0.66, 0.1, 0.05, n=12, tag="armor2"))
    noz = []
    for k in range(6):
        a = math.radians(90 + 60 * k)
        d = np.array([math.cos(a), math.sin(a), 0.0])
        p = d * 0.66 + np.array([0, 0, -0.26])
        m.add("booster", cyl(d * 0.3 + [0, 0, -0.22], p, 0.05, n=5, tag="frame"))
        out = d * 0.45 + np.array([0, 0, -0.9])
        out /= np.linalg.norm(out)
        m.add("booster", cast_seg([p - out * 0.1, p + out * 0.14], [(0.11, 0.11), (0.1, 0.1)], "armor", n=8))
        m.add("booster", nozzle(p + out * 0.12, out, 0.08, 0.16, n=6))
        noz.append({"pos": r3(p + out * 0.3), "dir": r3(out), "size": 0.26})
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-X4 Comet", "maker": MAKER, "tier": 4, "weight": 6.0, "armor": 110,
            "stats": {"boost_speed": 0.95, "qb_speed": 4.2, "qb_cost": 640, "qb_cooldown": 6, "hover_thrust": 0.0,
                      "hover_cost": 0, "air_boost_cost": 10, "nozzles": noz}}
    return m, meta


# ---------------------------------------------------------------------------------- head

def head_armillary():
    """HD-X5 Armillary: a sensor sphere in three gold bands - the longest lock range, a pin-hole cone."""
    m = mk.Model("hd_armillary")
    m.add("head", cyl((0, -0.08, 0), (0, 0.12, 0), 0.09, n=8, tag="frame"))
    m.add("head", mk.ellipsoid((0, 0.32, 0), 0.2, 0.2, 0.2, nu=10, nv=6, tag="armor"))
    m.add("head", cyl((0, 0.32, 0.12), (0, 0.32, 0.24), 0.07, n=8, tag="eye"))
    m.add("head", *ring((0, 0.32, 0), (0, 1, 0), 0.27, 0.05, 0.022, n=12, tag="armor2"))
    m.add("head", *ring((0, 0.32, 0), (1, 0, 0), 0.27, 0.05, 0.022, n=12, tag="armor2"))
    m.add("head", *ring((0, 0.32, 0), (0.6, 0.0, 0.8), 0.3, 0.04, 0.02, n=12, tag="accent"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-X5 Armillary", "maker": MAKER, "tier": 4, "weight": 2.4, "armor": 90,
            "points": {"top": [0, 0.6, 0]}, "stats": {"lock_range": 190, "lock_cone": 6, "stability": 1.05}}
    return m, meta


ALL = [legs_halo, arms_arbalest, weapon_arbalest, weapon_piledriver, booster_comet, head_armillary]
