"""Corvid 'Corvus' transform set: the faceted frame that folds into a flying wing.

Concept: a stealth strike frame. In the air there is no fuselage to speak of - the torso lies flat
on a broad, swept wing with a sawtooth trailing edge, the legs close into the wing's tail with flat
slot nozzles at the heels, and nothing rises above the wing but the slit-eyed head fairing.

Transformation (core is the root):
  - core:   lies flat on its back, head end forward (as Swallow / Kite)
  - head:   pitches forward and down onto the chest - a flat sensor fairing at the nose
  - legs:   stretch aft, close together; the feet turn their heel slots straight aft (the engines)
  - arms:   along the flanks, forearms forward under the wing roots
  - pack:   the two folded wing panels swing out and sweep back 30 degrees; each outer panel
            unfolds forward about its hinge, doubling the chord (as Manta)
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import math

import numpy as np

from common import MAKERS
from mg import mk, cyl, cbox, mirror, finish, r3, slab, chine_seg, chine_stack
import rig
from rig import R3, align, axis_angle
from parts_kf import frame_rot
from parts_ks import chain_of
from parts_cv import raven_leg, facet_core, shard_arm, saw, slot_nozzle, hexaxle

SCHEME = "cv"
MAKER = MAKERS["cv"]
SET = "corvus"
HIP_Y = 3.0
LEG_CLOSED_X = 0.24
ELBOW = np.array([0.12, -0.98, 0.06])
GRIP = np.array([0.12, -1.06, 0.95])
PIVOT_X, PIVOT_Y, PANEL_Z = 0.2, 0.34, -0.34
HINGE_X = 0.7
HINGE_Z = PANEL_Z - 0.0225
HEEL = np.array([0.0, 0.2, -0.46])      # heel slot nozzle, relative to the ankle (x by side)


def legs_corvus():
    """LG-CC Corvus legs: Raven legs with a slot thruster in each heel (the jet's engines)."""
    m = mk.Model("lg_corvus")
    H = HIP_Y
    m.add("pelvis", chine_stack([(H - 0.3, 0, 0.0, 0.28, 0.3, 0.26, {"k": 0.4}), (H - 0.12, 0, 0.0, 0.46, 0.44, 0.34, {"k": 0.4}),
                                 (H + 0.22, 0, 0.0, 0.44, 0.42, 0.32, {"k": 0.4}), (H + 0.3, 0, 0.0, 0.3, 0.3, 0.22, {"k": 0.4})],
                                "armor2"))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.5 * s, H, 0.0])
        knee = np.array([0.52 * s, 1.72, 0.38])
        ankle = np.array([0.52 * s, 0.42, -0.04])
        thigh, shin, foot = raven_leg(s, hip, knee, ankle)
        heel = ankle + HEEL
        foot.append(chine_seg([ankle + [0, 0.06, -0.12], heel + [0, 0.02, 0.06]], [(0.16, 0.12, 0.08), (0.17, 0.1, 0.06)], "armor",
                              up=(0, 1, 0)))
        foot.append(slot_nozzle(heel, (1, 0, 0), (0, 0, -1), 0.15, 0.05, 0.1))
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-CC Corvus Legs", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 21.5, "armor": 370, "points": {"hip": [0, H + 0.3, 0]},
            "stats": {"type": "biped", "load_limit": 58, "walk_speed": 0.32, "boost_multiplier": 1.05, "jump": 0.95,
                      "turn_rate": 16, "step_height": 1.2, "traction": 0.37, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.05, "collision_width": 1.8, "step_length": 1.6, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def core_corvus():
    m = mk.Model("cr_corvus")
    pts = facet_core(m)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-CC Corvus Core", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 16.5, "armor": 570, "points": pts,
            "stats": {"en_capacity": 1800, "en_recharge": 36, "recharge_delay": 11, "defense": 1.0, "fuel": 1000,
                      "stability": 1.0,
                      "jet": {"max_speed": 3.1, "acceleration": 0.05, "turn_speed": 2.3, "stall_fraction": 0.22,
                              "afterburner": 1.4, "afterburner_cost": 9, "collision_height": 1.4}}}
    return m, meta


def head_corvus():
    """HD-CC Corvus head: the Slit wedge, flatter, with a swept crest - the jet's nose fairing."""
    m = mk.Model("hd_corvus")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=6, tag="frame"))
    m.add("head", chine_stack([(0.02, 0, 0.02, 0.2, 0.36, 0.22, {"k": 0.25}), (0.16, 0, 0.02, 0.24, 0.44, 0.26, {"k": 0.25}),
                               (0.28, 0, -0.02, 0.17, 0.26, 0.24, {"k": 0.25})], "armor"))
    m.add("head", chine_stack([(0.17, 0, 0.02, 0.234, 0.43, 0.1, {"k": 0.25}), (0.21, 0, 0.02, 0.228, 0.42, 0.1, {"k": 0.25})], "eye"))
    m.add("head", slab([(0.1, 0.0), (-0.34, 0.0), (-0.42, 0.14), (-0.06, 0.1)], (0, 0.3, -0.02), (0, 0, 1), (0, 1, 0), 0.035, "accent"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-CC Corvus Head", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 2.2, "armor": 120, "points": {"top": [0, 0.34, 0]},
            "stats": {"lock_range": 125, "lock_cone": 15, "stability": 1.0}}
    return m, meta


def arms_corvus():
    m = mk.Model("am_corvus")
    upper, fore = shard_arm(ELBOW, GRIP, k=0.95)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-CC Corvus Arms", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 10.5, "armor": 280, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 20, "aim_speed": 400, "recoil_control": 1.0}}
    return m, meta


def wing_name(s):
    return "l_wing" if s > 0 else "r_wing"


def wing2_name(s):
    return "l_wing_out" if s > 0 else "r_wing_out"


def booster_corvus():
    """BS-CC Corvus pack: a flat chined pack; two big faceted wing panels hang from it like a crow's
    folded wings, each outer panel folded over its inner one. Sawtooth trailing edges."""
    m = mk.Model("bs_corvus")
    m.add("pack", chine_stack([(-0.5, 0, -0.12, 0.38, 0.16, 0.14, {"k": 0.5}), (0.0, 0, -0.14, 0.46, 0.18, 0.16, {"k": 0.5}),
                               (0.44, 0, -0.12, 0.34, 0.16, 0.14, {"k": 0.5})], "armor2"))
    noz = []
    for s in (1, -1):
        c = np.array([0.24 * s, -0.48, -0.24])
        m.add("pack", slot_nozzle(c, (1, 0, 0), (0, -0.6, -0.8), 0.13, 0.05, 0.12))
        noz.append({"pos": r3(c + np.array([0, -0.12, -0.16])), "dir": [0, -0.6, -0.8], "size": 0.3})
        g = wing_name(s)
        m.add(g, cyl((PIVOT_X * s, PIVOT_Y, -0.2), (PIVOT_X * s, PIVOT_Y, PANEL_Z - 0.03), 0.08, n=6, tag="frame"))
        # inner panel (x along the chord when spread, -y along the span); its inner edge is the trailing edge
        poly = [(0.06, 0.44), (HINGE_X, 0.44), (HINGE_X, -1.9), (0.3, -1.9), (0.06, -1.5)]
        m.add(g, mk.prism([(x * s, y, PANEL_Z + 0.025) for x, y in poly], [(x * s, y, PANEL_Z - 0.025) for x, y in poly], tag="armor"))
        # sawtooth trailing edge
        for i in range(4):
            y = 0.2 - i * 0.42
            m.add(g, mk.prism([(0.08 * s, y, PANEL_Z + 0.02), (0.08 * s, y - 0.3, PANEL_Z + 0.02), (0.005 * s, y - 0.15, PANEL_Z + 0.02)],
                              [(0.08 * s, y, PANEL_Z - 0.02), (0.08 * s, y - 0.3, PANEL_Z - 0.02), (0.005 * s, y - 0.15, PANEL_Z - 0.02)],
                              tag="accent"))
        st = [(0.36, -0.2), (0.52, -0.2), (0.52, -1.5), (0.36, -1.4)]
        m.add(g, mk.prism([(x * s, y, PANEL_Z + 0.025) for x, y in st], [(x * s, y, PANEL_Z + 0.04) for x, y in st], tag="armor2"))
        # outer panel, stowed folded over the inner one (mirrored about the hinge, one layer further back)
        g2 = wing2_name(s)
        unfolded = [(HINGE_X, 0.36), (1.1, 0.1), (1.36, -1.0), (1.3, -1.8), (HINGE_X, -1.9)]
        stowed = [(2 * HINGE_X - x, y) for x, y in unfolded]
        z2 = PANEL_Z - 0.05
        m.add(g2, mk.prism([(x * s, y, z2 + 0.025) for x, y in stowed], [(x * s, y, z2 - 0.025) for x, y in stowed], tag="armor"))
        le = [(1.08, 0.08), (1.36, -1.0), (1.28, -1.0), (1.0, 0.04)]
        le = [(2 * HINGE_X - x, y) for x, y in le]
        m.add(g2, mk.prism([(x * s, y, z2 - 0.025) for x, y in le], [(x * s, y, z2 - 0.04) for x, y in le], tag="accent"))
        m.add(g2, cyl((HINGE_X * s, 0.4, HINGE_Z - 0.005), (HINGE_X * s, -1.86, HINGE_Z - 0.005), 0.035, n=6, tag="frame"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-CC Corvus Pack", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 6.0, "armor": 120,
            "stats": {"boost_speed": 1.05, "qb_speed": 2.55, "qb_cost": 300, "qb_cooldown": 10, "hover_thrust": 0.16,
                      "hover_cost": 12, "air_boost_cost": 5, "nozzles": noz}}
    return m, meta


ALL = [legs_corvus, core_corvus, head_corvus, arms_corvus, booster_corvus]
IDS = {"legs": "lg_corvus", "core": "cr_corvus", "head": "hd_corvus", "arms": "am_corvus", "booster": "bs_corvus"}


def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    r = rig.Rig(rig.robot_keys(parts))
    legs = parts["legs"][1]
    r.root("core:core", R3(rx=-90, ry=180), offset, delay=0.05)
    # head: forward and down onto the chest - the nose fairing
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), -90), slide=(0, -0.05, 0.12), delay=0.2)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y + 0.3, 0), None, delay=0.0)
    r.joint("booster:pack", "core:core", (0, 0, 0), None, delay=0.1)
    Wp = r.world["booster:pack"][:3, :3]
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip, knee, ankle = chain_of(legs, pre)
        d = np.array([0.0, -(hip[1] - ankle[1]), 0.0])
        R_thigh = align(knee - hip, d)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, slide=((LEG_CLOSED_X - hip[0] * s) * s, 0, 0), delay=0.0)
        # shins fold forward under the thighs (a Z-fold): the airframe stays short behind the wing
        b = R_thigh.T @ np.array([0.0, 1.0, -0.3])
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, align(ankle - knee, b), delay=0.12)
        # feet: heel slots straight aft
        Ws = r.world["legs:" + pre + "shin"][:3, :3]
        aft = Ws.T @ r.world["core:core"][:3, :3] @ np.array([0.0, -1.0, 0.0])
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, align((0, 0, -1), aft), delay=0.25)
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 4 * s), delay=0.1)
        e, g = ELBOW * [s, 1, 1], GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.1 * s, 1.0, -0.1)), delay=0.15)
        # wings: out to the side, swept back 30 degrees, flat; the outer panels unfold forward
        side = Wp @ np.array([s, 0.0, 0.0])
        side = np.array([side[0], 0.0, side[2]])
        side /= np.linalg.norm(side)
        sweep = math.radians(30)
        out = side * math.cos(sweep) + np.array([0, 0, -math.sin(sweep)])
        Wd = frame_rot((0, -1, 0), (0, 0, -1), out, (0, -1, 0))
        r.joint("booster:" + wing_name(s), "booster:pack", (PIVOT_X * s, PIVOT_Y, PANEL_Z), Wp.T @ Wd, delay=0.45)
        r.joint("booster:" + wing2_name(s), "booster:" + wing_name(s), (HINGE_X * s, -0.7, HINGE_Z),
                axis_angle((0, 1, 0), 180 * s), delay=0.7)
    return r


def apply_rig(built, weapons):
    parts = {k: built[v] for k, v in IDS.items()}
    parts.update(weapons)
    r = build_rig(parts)
    lo, hi = rig.model_at(parts, r.world, "probe").bbox()
    r = build_rig(parts, (0.0, -lo[1] + 0.12, -(lo[2] + hi[2]) / 2))
    for mount, pid in IDS.items():
        model, meta = built[pid]
        meta["jet_pose"] = {g: r.pose_json(mount + ":" + g) for g in model.order}
    jet_noz = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        ch = next(c for c in parts["legs"][1]["stats"]["chains"] if c["upper"] == pre + "thigh")
        ankle = np.array(ch["ankle"], float)
        p = r.point("legs:" + pre + "foot", ankle + HEEL + np.array([0, 0, -0.12]))
        d = r.axis("legs:" + pre + "foot", (0, 0, -1))
        jet_noz.append({"pos": r3(p), "dir": r3(d / np.linalg.norm(d)), "size": 0.42})
    built[IDS["booster"]][1]["stats"]["jet_nozzles"] = jet_noz
    lo, hi = rig.model_at(parts, r.world, "jet").bbox()
    built[IDS["core"]][1]["stats"]["jet"]["collision_height"] = round(float(hi[1] - lo[1]) * 0.85, 2)
    return parts


def build_set(weapons=None):
    built = {}
    for b in ALL:
        m, meta = b()
        built[m.name] = (m, meta)
    if weapons is None:
        from mg import load_part
        weapons = {"r_arm": load_part("wa_laser"), "l_arm": load_part("wa_rifle"), "r_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
