"""Vesper 'Kite' transform set: the skeletal frame that unfolds into a sailplane-like interceptor.

Concept: the lightest transforming frame. It carries no heavy flight surfaces of its own - the wings
are two long, narrow double-folded panels hung on the back like a closed umbrella. In the air it
becomes a glider with an enormous span: long range, very tight turns, little top speed.

Transformation (core is the root):
  - core:   lies flat on its back, head end forward, the capsule's canopy on top (as Swallow)
  - head:   pitches forward on the neck - the optic barrel becomes the nose probe
  - legs:   stretch straight aft and close together into one slim lattice tail boom; the shins roll
            outward so their calf fins stand as a V-tail; the claw feet point aft
  - arms:   fold along the flanks, forearms forward, the guns under the wing roots
  - pack:   the two wing panels swing out sideways (span), then each outer panel unfolds about the
            hinge at its tip, doubling the span
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import math

import numpy as np

from common import MAKERS
from mg import mk, cyl, cbox, mirror, finish, r3, pseg, slab, nozzle
import rig
from rig import R3, align, axis_angle
from parts_kf import frame_rot
from parts_ks import chain_of
import parts_va
from parts_va import axle, ram, claw_foot, tube, truss, roll_cage, capsule, reed_arm, engine_pod, wisp_leg, wisp_pelvis, beam

SCHEME = "va"
MAKER = MAKERS["va"]
SET = "kite"
HIP_Y = 3.0
LEG_CLOSED_X = 0.15
ELBOW = np.array([0.1, -0.95, 0.06])
GRIP = np.array([0.1, -1.02, 0.92])
PZ = -0.2                     # inner wing panels' plane (pack space)
PIVOT_Y = 0.45
ROOT_X, TIP_X = 0.08, 0.5     # inner panel chord (x) range
HINGE_Y = -1.4                # inner panel's lower edge = the outer panel's hinge


def legs_kite():
    """LG-VK Kite legs: Wisp legs (no spring strut) with a calf fin on each shin (the V-tail in flight)."""
    m = mk.Model("lg_kite")
    H = HIP_Y
    wisp_pelvis(m, H, half=0.5)
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.46 * s, H, 0.0])
        knee = np.array([0.48 * s, 1.66, 0.36])
        ankle = np.array([0.5 * s, 0.36, -0.06])
        thigh, shin = wisp_leg(hip, knee, ankle, s, spring=False, fin=True)
        foot = claw_foot(ankle, s)
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=0.6)
    meta = {"slot": "legs", "display_name": "LG-VK Kite Legs", "maker": MAKER, "tier": 4, "transform_set": SET,
            "weight": 9.5, "armor": 160, "points": {"hip": [0, H + 0.18, 0]},
            "stats": {"type": "biped", "load_limit": 34, "walk_speed": 0.38, "boost_multiplier": 1.12, "jump": 1.05,
                      "turn_rate": 20, "step_height": 1.2, "traction": 0.4, "qb_multiplier": 1.1,
                      "hover_multiplier": 1.2, "collision_width": 1.5, "step_length": 1.7, "step_lift": 0.55,
                      "step_ticks": 5, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def core_kite():
    """CR-VK Kite core: the capsule in its roll cage (the glider's cockpit)."""
    m = mk.Model("cr_kite")
    capsule(m)
    roll_cage(m, nose=False)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-VK Kite Core", "maker": MAKER, "tier": 4, "transform_set": SET,
            "weight": 8.5, "armor": 270,
            "points": {"neck": [0, 1.46, 0.0], "shoulder_l": [0.96, 1.2, 0], "back": [0, 0.86, -0.74],
                       "back_l": [0.32, 1.42, -0.46], "seat": [0, 0.82, 0.06], "eye": [0, 1.72, 0.3]},
            "stats": {"en_capacity": 1350, "en_recharge": 48, "recharge_delay": 8, "defense": 0.8, "fuel": 1000,
                      "stability": 0.85,
                      "jet": {"max_speed": 2.6, "acceleration": 0.06, "turn_speed": 3.2, "stall_fraction": 0.18,
                              "afterburner": 1.35, "afterburner_cost": 8, "collision_height": 1.4}}}
    return m, meta


def head_kite():
    """HD-VK Kite head: the Pin sensor with a longer optic - the glider's nose probe."""
    m, meta = parts_va.head_pin()
    m.name = "hd_kite"
    m.add("head", cyl((0, 0.24, 0.38), (0, 0.24, 0.6), 0.03, n=5, tag="metal"))
    finish(m, SCHEME, size=256)
    meta = dict(meta)
    meta.update({"display_name": "HD-VK Kite Head", "tier": 4, "transform_set": SET, "weight": 1.1})
    return m, meta


def arms_kite():
    m = mk.Model("am_kite")
    upper, fore = reed_arm(ELBOW, GRIP)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-VK Kite Arms", "maker": MAKER, "tier": 4, "transform_set": SET,
            "weight": 5.2, "armor": 130, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 12, "aim_speed": 490, "recoil_control": 0.75}}
    return m, meta


def wing_name(s):
    return "l_wing" if s > 0 else "r_wing"


def wing2_name(s):
    return "l_wing_out" if s > 0 else "r_wing_out"


def booster_kite():
    """BS-VK Kite pack: a small hub with two engine pods; the long wing panels hang down the back,
    each folded double at its tip."""
    m = mk.Model("bs_kite")
    m.add("pack", cbox(0, 0.1, -0.06, 0.46, 0.66, 0.12, "frame", c=0.02))
    m.add("pack", cyl((0, 0.45, -0.05), (0, 0.45, -0.16), 0.12, n=8, tag="accent"))
    noz = []
    for s in (1, -1):
        top = np.array([0.66 * s, 0.6, -0.12])
        bot = np.array([0.66 * s, -0.7, -0.16])
        m.add("pack", *engine_pod(top, bot, 0.15))
        m.add("pack", *tube([(0.2 * s, 0.3, -0.08), (0.52 * s, 0.25, -0.12)], 0.035))
        m.add("pack", *tube([(0.2 * s, -0.12, -0.08), (0.52 * s, -0.15, -0.13)], 0.035))
        d = (bot - top) / np.linalg.norm(bot - top)
        noz.append({"pos": r3(bot + d * 0.15), "dir": r3(d), "size": 0.32})
        g = wing_name(s)
        # inner panel: chord along X, span down -Y; the hinge of the outer panel on its lower edge
        m.add(g, cyl((0.29 * s, PIVOT_Y, -0.06), (0.29 * s, PIVOT_Y, PZ - 0.03), 0.06, n=6, tag="frame"))
        poly = [(ROOT_X, 0.55), (TIP_X, 0.5), (TIP_X, HINGE_Y), (ROOT_X + 0.04, HINGE_Y)]
        m.add(g, mk.prism([(x * s, y, PZ + 0.02) for x, y in poly], [(x * s, y, PZ - 0.02) for x, y in poly], tag="armor"))
        # spar: two square booms with diagonal ties on the back of the panel
        za = PZ - 0.035
        m.add(g, beam((0.24 * s, 0.4, za), (0.24 * s, HINGE_Y + 0.05, za), 0.022),
              beam((0.36 * s, 0.4, za), (0.36 * s, HINGE_Y + 0.05, za), 0.022),
              beam((0.24 * s, 0.4, za), (0.36 * s, -0.5, za), 0.014, n=3),
              beam((0.36 * s, -0.5, za), (0.24 * s, HINGE_Y + 0.05, za), 0.014, n=3))
        g2 = wing2_name(s)
        # outer panel, stowed folded up over the inner one (one layer further back); tip with a red winglet
        z2 = PZ - 0.06
        out = [(ROOT_X + 0.04, HINGE_Y), (TIP_X, HINGE_Y), (TIP_X - 0.06, 0.2), (ROOT_X + 0.16, 0.25)]
        m.add(g2, mk.prism([(x * s, y, z2 + 0.02) for x, y in out], [(x * s, y, z2 - 0.02) for x, y in out], tag="armor"))
        tip = [(ROOT_X + 0.16, 0.25), (TIP_X - 0.06, 0.2), (TIP_X - 0.08, 0.02), (ROOT_X + 0.18, 0.06)]
        m.add(g2, mk.prism([(x * s, y, z2 - 0.02) for x, y in tip], [(x * s, y, z2 - 0.035) for x, y in tip], tag="accent"))
        m.add(g2, cyl((ROOT_X * s, HINGE_Y, PZ - 0.03), (TIP_X * s, HINGE_Y, PZ - 0.03), 0.03, n=5, tag="frame"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-VK Kite Pack", "maker": MAKER, "tier": 4, "transform_set": SET,
            "weight": 5.5, "armor": 90,
            "stats": {"boost_speed": 1.28, "qb_speed": 3.0, "qb_cost": 300, "qb_cooldown": 8, "hover_thrust": 0.2,
                      "hover_cost": 16, "air_boost_cost": 6, "nozzles": noz}}
    return m, meta


ALL = [legs_kite, core_kite, head_kite, arms_kite, booster_kite]
IDS = {"legs": "lg_kite", "core": "cr_kite", "head": "hd_kite", "arms": "am_kite", "booster": "bs_kite"}


def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    r = rig.Rig(rig.robot_keys(parts))
    legs = parts["legs"][1]
    # core: flat on its back, head end forward, canopy up
    r.root("core:core", R3(rx=-90, ry=180), offset, delay=0.05)
    # head: pitches forward - the optic barrel is the nose probe
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), -90), slide=(0, 0.05, 0.0), delay=0.2)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y + 0.18, 0), None, delay=0.0)
    r.joint("booster:pack", "core:core", (0, 0, 0), None, delay=0.1)
    Wp = r.world["booster:pack"][:3, :3]
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip, knee, ankle = chain_of(legs, pre)
        # legs: straight aft, closed into one boom; the shins roll their fins out into a V-tail
        d = np.array([0.0, -(hip[1] - ankle[1]), 0.0])
        R_thigh = align(knee - hip, d)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, slide=((LEG_CLOSED_X - hip[0] * s) * s, 0, 0), delay=0.0)
        a = R_thigh.T @ d
        a /= np.linalg.norm(a)
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, axis_angle(a, -40 * s) @ align(ankle - knee, a), delay=0.12)
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, align((0, 0, 1), ankle - knee), delay=0.25)
        # arms along the flanks, forearms folded forward
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 4 * s), delay=0.1)
        e, g = ELBOW * [s, 1, 1], GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.12 * s, 1.0, -0.1)), delay=0.15)
        # wings: swing out to the side (span), chord along the flight path; then the outer panels unfold
        out = Wp @ np.array([s, 0.0, 0.0])
        out = np.array([out[0], 0.0, out[2]])
        out /= np.linalg.norm(out)
        Wd = frame_rot((0, -1, 0), (s, 0, 0), out + np.array([0, 0.04, 0]), (0, 0, 1))
        r.joint("booster:" + wing_name(s), "booster:pack", (0.29 * s, PIVOT_Y, PZ), Wp.T @ Wd, delay=0.45)
        r.joint("booster:" + wing2_name(s), "booster:" + wing_name(s), (0.29 * s, HINGE_Y, PZ - 0.03),
                axis_angle((1, 0, 0), 180), delay=0.7)
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
    for n in built[IDS["booster"]][1]["stats"]["nozzles"]:
        p = r.point("booster:pack", n["pos"])
        d = r.axis("booster:pack", n["dir"])
        jet_noz.append({"pos": r3(p), "dir": r3(d / np.linalg.norm(d)), "size": 0.36})
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
        weapons = {"r_arm": load_part("wa_gunpod"), "l_arm": load_part("wa_gunpod"), "l_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
