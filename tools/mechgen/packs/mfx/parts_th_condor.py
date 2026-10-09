"""Talos 'Condor' transform set: the fortress frame that flies as a heavy gunship.

Concept: a flying fortress, not a fighter. Slow, wide-turning, hard to bring down: a straight,
thick wing of great span with an engine nacelle on each inner panel, the cast torso as the
fuselage, the legs as a tail boom with an H-tail (stabilisers folded on the calves).

Transformation (core is the root):
  - core:   flat on its back, head end forward, the frontal casting on top
  - head:   pitches forward - the helmet becomes the nose
  - legs:   straight aft, closed into the tail boom; the shins roll 90 degrees so the calf
            stabilisers lie flat (H-tail); the feet point aft
  - arms:   along the flanks, forearms forward - the guns under the nose
  - pack:   the folded wing panels swing out (straight wing, slight dihedral), each outer panel
            unfolds about the hinge at its tip; the nacelles on the inner panels face forward
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import numpy as np

from common import MAKERS
from mg import mk, cyl, cbox, mirror, finish, r3, slab, plate, cast, cast_seg, nozzle
import rig
from rig import R3, align, axis_angle
from parts_kf import frame_rot
from parts_ks import chain_of
from parts_th import colossus_leg, keep_core, gauntlet_arm, skirt, band, vision_block
import parts_th

SCHEME = "th"
MAKER = MAKERS["th"]
SET = "condor"
HIP_Y = 3.2
LEG_CLOSED_X = 0.36
ELBOW = np.array([0.13, -0.98, 0.06])
GRIP = np.array([0.13, -1.08, 0.98])
PZ = -0.52
PIVOT_Y = 0.6
ROOT_X, TIP_X = 0.1, 0.92
HINGE_Y = -1.0


def legs_condor():
    """LG-TC Condor legs: Colossus legs, a little lighter, with a stabiliser plate on each calf."""
    m = mk.Model("lg_condor")
    H = HIP_Y
    m.add("pelvis", cast([(H - 0.32, 0, 0.0, 0.56, 0.4, 0.4), (H - 0.08, 0, 0.0, 0.78, 0.5, 0.48),
                          (H + 0.3, 0, -0.02, 0.76, 0.48, 0.46), (H + 0.42, 0, -0.04, 0.54, 0.34, 0.32)], "armor2", n=10, axis="y"))
    m.add("pelvis", *skirt((0, H + 0.1, 0.5), (1, 0, 0), (0, 1, -0.2), 0.4, 0.24, rows=2, step=0.16, out=(0, 0.2, 1)))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.64 * s, H, 0.0])
        knee = np.array([0.68 * s, 1.8, 0.38])
        ankle = np.array([0.68 * s, 0.48, -0.02])
        thigh, shin, foot = colossus_leg(s, hip, knee, ankle, k=0.9)
        axis = (ankle - knee) / np.linalg.norm(ankle - knee)
        # stabiliser: a broad plate standing back from the calf (flat in flight)
        shin.append(slab([(0.0, 0.0), (0.0, 0.7), (0.62, 0.9), (0.72, 0.78), (0.66, 0.08)],
                         knee + (ankle - knee) * 0.15 + [0, 0, -0.36], (0, 0, -1), axis, 0.07, "armor2"))
        shin.append(slab([(0.6, 0.78), (0.62, 0.9), (0.72, 0.78), (0.7, 0.66)],
                         knee + (ankle - knee) * 0.15 + [0.03 * s, 0, -0.36], (0, 0, -1), axis, 0.08, "accent"))
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=1.3)
    meta = {"slot": "legs", "display_name": "LG-TC Condor Legs", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 40.0, "armor": 820, "points": {"hip": [0, H + 0.42, 0]},
            "stats": {"type": "biped", "load_limit": 118, "walk_speed": 0.23, "boost_multiplier": 0.9, "jump": 0.65,
                      "turn_rate": 10, "step_height": 1.3, "traction": 0.42, "qb_multiplier": 0.72,
                      "hover_multiplier": 0.8, "collision_width": 2.3, "step_length": 1.8, "step_lift": 0.4,
                      "step_ticks": 9, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def core_condor():
    m = mk.Model("cr_condor")
    pts = keep_core(m, w=0.95)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-TC Condor Core", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 26.0, "armor": 940, "points": pts,
            "stats": {"en_capacity": 2100, "en_recharge": 27, "recharge_delay": 14, "defense": 1.22, "fuel": 1400,
                      "stability": 1.25,
                      "jet": {"max_speed": 2.4, "acceleration": 0.035, "turn_speed": 1.5, "stall_fraction": 0.28,
                              "afterburner": 1.3, "afterburner_cost": 12, "collision_height": 1.8}}}
    return m, meta


def head_condor():
    m, meta = parts_th.head_bascinet()
    m.name = "hd_condor"
    finish(m, SCHEME, size=256)
    meta = dict(meta)
    meta.update({"display_name": "HD-TC Condor Head", "tier": 5, "transform_set": SET})
    return m, meta


def arms_condor():
    m = mk.Model("am_condor")
    upper, fore = gauntlet_arm(ELBOW, GRIP, k=0.9)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-TC Condor Arms", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 16.5, "armor": 430, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 34, "aim_speed": 270, "recoil_control": 1.5}}
    return m, meta


def wing_name(s):
    return "l_wing" if s > 0 else "r_wing"


def wing2_name(s):
    return "l_wing_out" if s > 0 else "r_wing_out"


def nacelle_pts(s):
    """Inner-panel nacelle: intake end (outboard edge) and exhaust end, in pack space."""
    y, z = 0.52, PZ - 0.22
    return np.array([(TIP_X + 0.12) * s, y, z]), np.array([0.02 * s, y, z])


def booster_condor():
    """BS-TC Condor pack: a cast hub; two thick wing panels folded double on the back, a big
    engine nacelle lying across each inner panel."""
    m = mk.Model("bs_condor")
    m.add("pack", cast([(0.0, 0, 0.1, 0.62, 0.66, 0.36), (-0.32, 0, 0.0, 0.66, 0.7, 0.4), (-0.42, 0, 0.0, 0.5, 0.56, 0.3)],
                       "armor2", n=10))
    noz = []
    for s in (1, -1):
        p = np.array([0.3 * s, -0.5, -0.3])
        m.add("pack", nozzle(p, (0, -0.4, -0.92), 0.16, 0.28, n=6))
        noz.append({"pos": r3(p + np.array([0, -0.14, -0.3])), "dir": [0, -0.4, -0.92], "size": 0.36})
        g = wing_name(s)
        m.add(g, cyl((0.42 * s, PIVOT_Y, -0.3), (0.42 * s, PIVOT_Y, PZ - 0.06), 0.14, n=8, tag="frame"))
        poly = [(ROOT_X, 0.72), (TIP_X, 0.72), (TIP_X, HINGE_Y), (ROOT_X, HINGE_Y)]
        m.add(g, mk.prism([(x * s, y, PZ + 0.05) for x, y in poly], [(x * s, y, PZ - 0.05) for x, y in poly], tag="armor"))
        # nacelle across the panel: intake to the outboard edge (forward in flight)
        a, b = nacelle_pts(s)
        d = (b - a) / np.linalg.norm(b - a)
        m.add(g, cast_seg([a, (a + b) / 2, b], [(0.22, 0.22), (0.24, 0.24), (0.2, 0.2)], "armor2", side=(0, 1, 0), n=8))
        m.add(g, band([a - d * 0.02, a + d * 0.08], [(0.235, 0.235), (0.235, 0.235)], n=8))
        m.add(g, nozzle(b - d * 0.05, d, 0.16, 0.22, n=6))
        m.add(g, cbox((a[0] + b[0]) / 2, a[1], PZ - 0.1, abs(a[0] - b[0]) * 0.6, 0.12, 0.14, "frame", c=0.02))
        g2 = wing2_name(s)
        z2 = PZ - 0.12
        out = [(ROOT_X + 0.05, HINGE_Y), (TIP_X, HINGE_Y), (TIP_X - 0.1, 0.25), (ROOT_X + 0.2, 0.25)]
        m.add(g2, mk.prism([(x * s, y, z2 + 0.05) for x, y in out], [(x * s, y, z2 - 0.05) for x, y in out], tag="armor"))
        tip = [(ROOT_X + 0.2, 0.25), (TIP_X - 0.1, 0.25), (TIP_X - 0.12, 0.08), (ROOT_X + 0.22, 0.08)]
        m.add(g2, mk.prism([(x * s, y, z2 - 0.05) for x, y in tip], [(x * s, y, z2 - 0.07) for x, y in tip], tag="accent"))
        m.add(g2, cyl((ROOT_X * s, HINGE_Y, (PZ + z2) / 2), (TIP_X * s, HINGE_Y, (PZ + z2) / 2), 0.07, n=6, tag="frame"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-TC Condor Pack", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 11.0, "armor": 220,
            "stats": {"boost_speed": 0.88, "qb_speed": 2.05, "qb_cost": 380, "qb_cooldown": 13, "hover_thrust": 0.14,
                      "hover_cost": 15, "air_boost_cost": 7, "nozzles": noz}}
    return m, meta


ALL = [legs_condor, core_condor, head_condor, arms_condor, booster_condor]
IDS = {"legs": "lg_condor", "core": "cr_condor", "head": "hd_condor", "arms": "am_condor", "booster": "bs_condor"}


def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    r = rig.Rig(rig.robot_keys(parts))
    legs = parts["legs"][1]
    r.root("core:core", R3(rx=-90, ry=180), offset, delay=0.05)
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), -90), slide=(0, 0.1, 0.0), delay=0.2)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y + 0.42, 0), None, delay=0.0)
    r.joint("booster:pack", "core:core", (0, 0, 0), None, delay=0.1)
    Wp = r.world["booster:pack"][:3, :3]
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip, knee, ankle = chain_of(legs, pre)
        d = np.array([0.0, -(hip[1] - ankle[1]), 0.0])
        R_thigh = align(knee - hip, d)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, slide=((LEG_CLOSED_X - hip[0] * s) * s, 0, 0), delay=0.0)
        a = R_thigh.T @ d
        a /= np.linalg.norm(a)
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, axis_angle(a, -90 * s) @ align(ankle - knee, a), delay=0.12)
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, align((0, 0, 1), ankle - knee), delay=0.25)
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 4 * s), delay=0.1)
        e, g = ELBOW * [s, 1, 1], GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.1 * s, 1.0, -0.1)), delay=0.15)
        out = Wp @ np.array([s, 0.0, 0.0])
        out = np.array([out[0], 0.0, out[2]])
        out /= np.linalg.norm(out)
        Wd = frame_rot((0, -1, 0), (s, 0, 0), out + np.array([0, 0.08, 0]), (0, 0, 1))
        r.joint("booster:" + wing_name(s), "booster:pack", (0.42 * s, PIVOT_Y, PZ), Wp.T @ Wd, delay=0.45)
        r.joint("booster:" + wing2_name(s), "booster:" + wing_name(s), (0.5 * s, HINGE_Y, PZ - 0.06),
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
    for s in (1, -1):
        a, b = nacelle_pts(s)
        d = (b - a) / np.linalg.norm(b - a)
        p = r.point("booster:" + wing_name(s), b + d * 0.25)
        dd = r.axis("booster:" + wing_name(s), d)
        jet_noz.append({"pos": r3(p), "dir": r3(dd / np.linalg.norm(dd)), "size": 0.5})
    for n in built[IDS["booster"]][1]["stats"]["nozzles"]:
        p = r.point("booster:pack", n["pos"])
        dd = r.axis("booster:pack", n["dir"])
        jet_noz.append({"pos": r3(p), "dir": r3(dd / np.linalg.norm(dd)), "size": 0.4})
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
        weapons = {"r_arm": load_part("wa_mg"), "l_arm": load_part("wa_bazooka"), "r_back": load_part("wb_cannon"),
                   "l_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
