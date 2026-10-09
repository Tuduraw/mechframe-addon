"""Kestrel 'Manta' transform set: a light biped that lies back into a flat, wide ray-shaped fighter.

Transformation (core is the root; the frame falls back onto its back - head AFT, chest and canopy up):
  - legs:   swing forward, straight and closed side by side into one long, slender nose, the strakes
            along their outer edges; the feet point ahead as the nose tip
  - arms:   straight forward along the flanks, the gun pods ahead of the hands
  - pack:   turns 180 degrees on its hub (the thrusters now blow aft) and fans the two wing panels -
            stowed as a double-layered cape on its back - out sideways; then each outer layer unfolds
            forward about its hinge, doubling the chord into a broad ray's fin
  - head:   tips up at the tail; its tall crest becomes the vertical fin
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import math

import numpy as np

from mg import mk, cyl, cbox, mirror, nozzle, finish, r3, pstack, pseg, slab
import rig
from rig import R3, align, axis_angle
from parts_kf import frame_rot
from parts_ks import flat_core, chain_of

SCHEME = "km"
MAKER = "Kestrel Dynamics"
SET = "manta"

HIP_Y = 2.9
# Jet form: the legs' half-spacing with the legs closed (the shins touch along the middle).
LEG_CLOSED_X = 0.19


# ---------------------------------------------------------------------------------- legs

def strake(a, b, s, width, t=0.04, tag="armor2"):
    """A flat blade along the outer side of a limb segment a->b (in the plane of the segment and X)."""
    a = np.asarray(a, float); b = np.asarray(b, float)
    L = np.linalg.norm(b - a)
    v = (a - b) / L
    c = (a + b) / 2
    return slab([(0.0, L * 0.45), (width * 0.7, L * 0.3), (width, -L * 0.25), (0.0, -L * 0.48)], c, (s, 0, 0), v, t, tag)


def legs_manta():
    """LG-KM Manta legs: light legs with flat strakes along the outer thighs and shins."""
    from parts_kd import _biped_leg
    m = mk.Model("lg_manta")
    m.add("pelvis", pstack([(2.62, 0, 0.0, 0.26, 0.3, 0.22), (2.76, 0, 0.0, 0.42, 0.4, 0.28),
                            (3.1, 0, 0.0, 0.4, 0.38, 0.28), (3.2, 0, 0.0, 0.28, 0.28, 0.2)], "armor2"))
    m.add("pelvis", pseg([(0, 3.02, 0.3), (0, 2.62, 0.4)], [(0.12, 0.14, 0.06), (0.05, 0.08, 0.04)], "accent", fwd=(0, 0, 1)))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.42 * s, 2.86, 0.0])
        knee = np.array([0.44 * s, 1.62, 0.34])
        ankle = np.array([0.44 * s, 0.42, -0.02])
        thigh, shin, foot = _biped_leg(s, hip, knee, ankle)
        o = np.array([0.1 * s, 0, 0])
        thigh.append(strake(hip + o + [0, -0.3, 0.04], knee + o + [0, 0.2, 0.0], s, 0.26))
        shin.append(strake(knee + o + [0, -0.2, 0.02], ankle + o + [0, 0.25, 0.04], s, 0.3))
        shin.append(strake(knee + o * 1.6 + [0, -0.5, 0.0], knee + o * 1.6 + (ankle - knee) * 0.6, s, 0.32, t=0.02, tag="accent"))
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1],
                       "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-KM Manta Legs", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 17.5, "armor": 280, "points": {"hip": [0, HIP_Y, 0]},
            "stats": {"type": "biped", "load_limit": 50, "walk_speed": 0.32, "boost_multiplier": 1.08, "jump": 0.95,
                      "turn_rate": 18, "step_height": 1.15, "traction": 0.36, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.15, "collision_width": 1.6, "step_length": 1.5, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def core_manta():
    """CR-KM Manta core: the thin frame torso with swept collar blades; in jet form it lies chest-up,
    the cockpit pod under its glass hood on top."""
    m = mk.Model("cr_manta")
    pts = flat_core(m, w=1.04)
    for s in (1, -1):
        # collar blades: swept up and back from the yoke (they trail aft in jet form)
        m.add("core", slab([(0.0, 0.0), (0.3, 0.0), (0.44, 0.34), (0.36, 0.38)], (0.18 * s, 1.3, -0.04),
                           (s, 0, 0), (0, 1, -0.2), 0.04, "accent"))
    pts["jet_eye"] = [0, 0.84, 0.45]
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-KM Manta Core", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 14.5, "armor": 430, "points": pts,
            "stats": {"en_capacity": 1850, "en_recharge": 35, "recharge_delay": 11, "defense": 0.97, "fuel": 900,
                      "stability": 0.95,
                      "jet": {"max_speed": 3.0, "acceleration": 0.05, "turn_speed": 2.9, "stall_fraction": 0.2,
                              "afterburner": 1.4, "afterburner_cost": 9, "collision_height": 1.2}}}
    return m, meta


def head_manta():
    """HD-KM Manta head: a low wedge with twin eyes and a tall swept crest (the tail fin in jet form)."""
    m = mk.Model("hd_manta")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=8, tag="frame"))
    m.add("head", pstack([(0.04, 0, 0.0, 0.15, 0.24, 0.2), (0.2, 0, 0.02, 0.18, 0.32, 0.22),
                          (0.34, 0, -0.02, 0.12, 0.18, 0.2)], "armor", sh=0.4))
    for s in (1, -1):
        m.add("head", slab([(0.0, -0.03), (0.1, -0.03), (0.1, 0.03), (0.0, 0.03)], (0.06 * s, 0.22, 0.3),
                           (s * 0.9, 0, -0.44), (0, 1, 0), 0.04, "eye"))
    # crest: root along the top of the head, swept up and back
    m.add("head", slab([(0.2, 0.0), (-0.22, 0.0), (-0.5, 0.42), (-0.34, 0.44)], (0.0, 0.32, 0.0), (0, 0, 1), (0, 1, 0), 0.05, "armor2"))
    m.add("head", slab([(-0.3, 0.3), (-0.44, 0.3), (-0.49, 0.4), (-0.36, 0.41)], (0.03, 0.32, 0.0), (0, 0, 1), (0, 1, 0), 0.02, "accent"))
    m.add("head", slab([(-0.3, 0.3), (-0.44, 0.3), (-0.49, 0.4), (-0.36, 0.41)], (-0.03, 0.32, 0.0), (0, 0, 1), (0, 1, 0), 0.02, "accent"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-KM Manta Head", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 2.4, "armor": 120, "points": {"top": [0, 0.72, 0]},
            "stats": {"lock_range": 115, "lock_cone": 16, "stability": 0.95}}
    return m, meta


ELBOW = np.array([0.11, -0.96, 0.07])
GRIP = np.array([0.11, -1.05, 0.95])


def arms_manta():
    from parts_kd import light_arm
    m = mk.Model("am_manta")
    upper, fore = light_arm(ELBOW, GRIP, w=1.0, blade=False)
    # canard: a flat blade along the outside of the forearm
    fore.append(strake(ELBOW + [0.12, 0.0, 0.05], GRIP + [0.12, 0.02, -0.15], 1, 0.26))
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-KM Manta Arms", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 9.5, "armor": 250, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 16, "aim_speed": 400, "recoil_control": 0.95}}
    return m, meta


PIVOT_X, PIVOT_Y, PANEL_Z = 0.2, 0.32, -0.33
HINGE_X = 0.62
HINGE_Z = PANEL_Z - 0.0225


def wing_name(s):
    return "l_wing" if s > 0 else "r_wing"


def wing2_name(s):
    return "l_wing_out" if s > 0 else "r_wing_out"


def booster_manta():
    """BS-KM Manta pack: a flat pack on a turning hub; two double-layered wing panels hang from it like
    a cape (each outer panel is folded over its inner one)."""
    m = mk.Model("bs_manta")
    m.add("pack", cyl((0, 0, 0.04), (0, 0, -0.14), 0.18, n=10, tag="frame"))
    m.add("pack", pstack([(-0.46, 0, -0.18, 0.34, 0.1, 0.12), (0.0, 0, -0.18, 0.4, 0.12, 0.13),
                          (0.42, 0, -0.18, 0.3, 0.1, 0.11)], "armor2", sh=0.7, rb=0.85))
    noz = []
    for s in (1, -1):
        m.add("pack", nozzle((0.2 * s, -0.42, -0.18), (0, -0.94, -0.34), 0.11, 0.2))
        noz.append({"pos": r3((0.2 * s, -0.6, -0.24)), "dir": [0, -0.94, -0.34], "size": 0.3})
        g = wing_name(s)
        m.add(g, cyl((PIVOT_X * s, PIVOT_Y, -0.26), (PIVOT_X * s, PIVOT_Y, -0.37), 0.07, n=8, tag="frame"))
        # inner panel: its outer edge (x = HINGE_X) is the hinge of the outer panel folded over its back
        poly = [(0.06, 0.4), (HINGE_X, 0.4), (HINGE_X, -1.8), (0.06, -1.4)]
        m.add(g, mk.prism([(x * s, y, PANEL_Z + 0.02) for x, y in poly], [(x * s, y, PANEL_Z - 0.02) for x, y in poly], tag="armor"))
        st = [(0.46, -0.6), (0.58, -0.6), (0.58, -1.55), (0.46, -1.45)]
        m.add(g, mk.prism([(x * s, y, PANEL_Z + 0.02) for x, y in st], [(x * s, y, PANEL_Z + 0.035) for x, y in st], tag="accent"))
        # outer panel, stowed folded over the inner one (mirrored about the hinge line, one layer further back)
        g2 = wing2_name(s)
        unfolded = [(HINGE_X, 0.3), (0.98, 0.02), (1.2, -0.9), (1.12, -1.7), (HINGE_X, -1.8)]
        stowed = [(2 * HINGE_X - x, y) for x, y in unfolded]
        z2 = PANEL_Z - 0.045
        m.add(g2, mk.prism([(x * s, y, z2 + 0.02) for x, y in stowed], [(x * s, y, z2 - 0.02) for x, y in stowed], tag="armor"))
        le = [(0.96, 0.0), (1.2, -0.88), (1.12, -0.9), (0.9, -0.06)]
        le = [(2 * HINGE_X - x, y) for x, y in le]
        m.add(g2, mk.prism([(x * s, y, z2 - 0.02) for x, y in le], [(x * s, y, z2 - 0.035) for x, y in le], tag="accent"))
        tipst = [(1.0, -0.6), (1.14, -0.95), (1.08, -1.55), (0.95, -1.2)]
        tipst = [(2 * HINGE_X - x, y) for x, y in tipst]
        m.add(g2, mk.prism([(x * s, y, z2 - 0.02) for x, y in tipst], [(x * s, y, z2 - 0.035) for x, y in tipst], tag="accent"))
        m.add(g2, cyl((HINGE_X * s, 0.36, HINGE_Z), (HINGE_X * s, -1.76, HINGE_Z), 0.032, n=6, tag="frame"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-KM Manta Pack", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 5.8, "armor": 115,
            "stats": {"boost_speed": 1.08, "qb_speed": 2.65, "qb_cost": 325, "qb_cooldown": 9, "hover_thrust": 0.17,
                      "hover_cost": 14, "air_boost_cost": 5, "nozzles": noz}}
    return m, meta


ALL = [legs_manta, core_manta, head_manta, arms_manta, booster_manta]
IDS = {"legs": "lg_manta", "core": "cr_manta", "head": "hd_manta", "arms": "am_manta", "booster": "bs_manta"}


# ---------------------------------------------------------------------------------- jet rig

def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    r = rig.Rig(rig.robot_keys(parts))
    legs = parts["legs"][1]
    # core: falls back onto its back - head aft, chest (and the canopy) up, legs forward
    r.root("core:core", R3(rx=-90), offset, delay=0.05)
    # head: tips up at the tail - the crest stands up as the fin
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), 90), slide=(0, 0.02, -0.12), delay=0.2)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y, 0), None, delay=0.0)
    # pack: turns half a turn on its hub (thrusters aft), then the wings fan out
    r.joint("booster:pack", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 180), delay=0.15)
    core_w = r.world["core:core"][:3, :3]
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip, knee, ankle = chain_of(legs, pre)
        # legs straight and closed together: one long, slender nose ahead of the body
        # (the hips slide in under the pelvis, so the legs lie parallel and touch along their length)
        d = np.array([0.0, -(hip[1] - ankle[1]), 0.0])
        R_thigh = align(knee - hip, d)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, slide=((LEG_CLOSED_X - hip[0] * s) * s, 0, 0), delay=0.0)
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, align(ankle - knee, R_thigh.T @ d), delay=0.1)
        # feet pointed ahead along the legs: the prong tips
        W = r.world["legs:" + pre + "shin"][:3, :3]
        yaw = math.degrees(math.atan2(d[0], -d[1]))
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, W.T @ core_w @ R3(rz=yaw) @ R3(rx=90), delay=0.25)
        # arms straight forward along the flanks
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 3 * s), delay=0.1)
        e, g = ELBOW * [s, 1, 1], GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.03 * s, -1.0, 0.0)), delay=0.15)
        # wings: fanned out about their pivots, swept back, flat under the body
        Wp = r.world["booster:pack"][:3, :3]
        sweep = math.radians(12)
        out = np.array([-s * math.cos(sweep), 0.0, -math.sin(sweep)])    # the pack's left panel ends up on the jet's right
        Wd = frame_rot((0, -1, 0), (0, 0, -1), out, (0, -1, 0))
        r.joint("booster:" + wing_name(s), "booster:pack", (PIVOT_X * s, PIVOT_Y, PANEL_Z), Wp.T @ Wd, delay=0.5)
        # outer panel unfolds forward about the hinge: the wing doubles its chord
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
    for n in built[IDS["booster"]][1]["stats"]["nozzles"]:
        p = r.point("booster:pack", n["pos"])
        d = r.axis("booster:pack", n["dir"])
        jet_noz.append({"pos": r3(p), "dir": r3(d / np.linalg.norm(d)), "size": 0.4})
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
        weapons = {"r_arm": load_part("wa_gunpod"), "l_arm": load_part("wa_gunpod"), "r_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
