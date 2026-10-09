"""Kestrel 'Falcon' transform set: a light biped that folds into a compact fighter.

Robot form follows the light design language (see parts_kd). The jet form is not typed in as
absolute placements: every group hangs from its parent at a joint (rig.Rig), and the jet placement is
the robot one turned about that joint - so every piece stays attached in both forms, and the mod's
jointed blend keeps it attached throughout the transformation.

Transformation (core is the root, rotated nose-forward, chest down):
  - nose:   the chest prow, hinged at the top of the chest, flips forward over the neck (its
            outer face, with the canopy, ends on top)
  - head:   folds back behind the nose and sinks onto the shoulders
  - pelvis: hinged at the front of the waist, swings the legs forward under the belly
  - legs:   Z-fold - thighs along the belly, knees fully bent, shins (the engines) back underneath,
            feet (the nozzles) turned to blow aft; the legs close together
  - arms:   hang back along the flanks, forearms folded forward, hands holding the gun pods forward
  - pack:   wings swing out from the pack's sides, tail fins stand up from its lower edge
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import math

import numpy as np

from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3, pstack, pseg, slab, pent_ring
import rig
from rig import R3, align, axis_angle

SCHEME = "kf"
MAKER = "Kestrel Dynamics"
SET = "falcon"

HIP_Y = 2.9
NOSE_HINGE = (0.0, 0.84, 0.0)     # the cockpit ball's centre


# ---------------------------------------------------------------------------------- legs

def thigh_pod(hip, knee, s):
    """Where the thigh's nozzle pod ends (its exit), outside the thigh just above the knee."""
    return hip + (knee - hip) * 0.62 + np.array([0.2 * s, 0, -0.02])


def legs_falcon():
    """LG-KF Falcon legs: light legs whose thighs carry the jet nozzles just above the knees (they
    blow straight aft once the legs stretch back), the shins styled as engine nacelles."""
    from parts_kd import _biped_leg
    m = mk.Model("lg_falcon")
    m.add("pelvis", pstack([(2.62, 0, 0.0, 0.26, 0.3, 0.22), (2.76, 0, 0.0, 0.4, 0.4, 0.28),
                            (3.1, 0, 0.0, 0.38, 0.38, 0.28), (3.2, 0, 0.0, 0.26, 0.28, 0.2)], "armor2"))
    m.add("pelvis", pseg([(0, 3.02, 0.3), (0, 2.62, 0.4)], [(0.12, 0.14, 0.06), (0.05, 0.08, 0.04)], "accent", fwd=(0, 0, 1)))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.42 * s, 2.86, 0.0])
        knee = np.array([0.44 * s, 1.62, 0.34])
        ankle = np.array([0.44 * s, 0.42, -0.02])
        thigh, shin, foot = _biped_leg(s, hip, knee, ankle)
        axis = (knee - hip) / np.linalg.norm(knee - hip)
        # nozzle pod on the outside of the thigh, opening just above the knee along the thigh
        pod = thigh_pod(hip, knee, s)
        thigh.append(pseg([pod - axis * 0.42, pod], [(0.1, 0.13, 0.1), (0.13, 0.15, 0.13)], "armor2", fwd=(0, 0, 1)))
        thigh.append(nozzle(pod - axis * 0.02, axis, 0.13, 0.22, n=8))
        # tail fin: a blade raked back from the thigh's rear face (stands up at the tail in jet form)
        fin_root = pod - axis * 0.36 + np.array([-0.2 * s, 0, -0.1])
        thigh.append(slab([(0.0, 0.0), (0.5, 0.0), (0.56, -0.36), (0.28, -0.4)], fin_root, axis, (0, 0, -1), 0.04, "armor"))
        thigh.append(slab([(0.34, -0.16), (0.52, -0.16), (0.55, -0.3), (0.38, -0.32)], fin_root + np.array([0.025 * s, 0, 0]),
                          axis, (0, 0, -1), 0.02, "accent"))
        # shin: engine intake slots under the knee
        shin.append(cbox(knee[0], knee[1] - 0.34, knee[2] + 0.2, 0.26, 0.24, 0.1, "frame", c=0.03))
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1],
                       "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-KF Falcon Legs", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 17.0, "armor": 270, "points": {"hip": [0, HIP_Y, 0]},
            "stats": {"type": "biped", "load_limit": 48, "walk_speed": 0.33, "boost_multiplier": 1.1, "jump": 0.95,
                      "turn_rate": 18, "step_height": 1.15, "traction": 0.35, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.15, "collision_width": 1.6, "step_length": 1.5, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def core_falcon():
    """CR-KF Falcon core: the light torso (cockpit ball in an open cage); its prow, with the canopy on
    the ridge, is the fighter's nose and pivots about the ball."""
    from parts_kd import light_core
    m = mk.Model("cr_falcon")
    pts = light_core(m, prow_group="nose", glass=True, spine=0.7)
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-KF Falcon Core", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 15.0, "armor": 440, "points": pts,
            "stats": {"en_capacity": 1800, "en_recharge": 36, "recharge_delay": 10, "defense": 0.95, "fuel": 900,
                      "stability": 0.95,
                      "jet": {"max_speed": 3.2, "acceleration": 0.05, "turn_speed": 2.4, "stall_fraction": 0.24,
                              "afterburner": 1.45, "afterburner_cost": 9, "collision_height": 1.6}}}
    return m, meta


def head_falcon():
    """HD-KF Falcon Head: a helmet with a centre blade and swept twin antennas."""
    m = mk.Model("hd_falcon")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=8, tag="frame"))
    m.add("head", pstack([(0.04, 0, 0.0, 0.14, 0.24, 0.2), (0.24, 0, 0.02, 0.18, 0.32, 0.24),
                          (0.4, 0, -0.03, 0.12, 0.18, 0.22), (0.46, 0, -0.06, 0.05, 0.08, 0.16)], "armor", sh=0.45))
    m.add("head", cbox(0, 0.24, 0.27, 0.26, 0.07, 0.08, "eye", c=0.015))
    m.add("head", slab([(0.0, 0.0), (0.0, 0.16), (-0.34, 0.12), (-0.3, 0.0)], (0, 0.4, 0.06), (0, 0, 1), (0, 1, 0), 0.03, "accent"))
    for s in (1, -1):
        m.add("head", seg([(0.13 * s, 0.32, 0.0), (0.2 * s, 0.56, -0.24)], [(0.016, 0.03), (0.008, 0.014)], "accent", c=0.005))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-KF Falcon Head", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 2.3, "armor": 120, "points": {"top": [0, 0.48, 0]},
            "stats": {"lock_range": 120, "lock_cone": 15, "stability": 0.9}}
    return m, meta


ELBOW = np.array([0.11, -0.96, 0.07])
GRIP = np.array([0.11, -1.05, 0.95])


def arms_falcon():
    from parts_kd import light_arm
    m = mk.Model("am_falcon")
    upper, fore = light_arm(ELBOW, GRIP, w=0.95, blade=False)
    fore.append(slab([(-0.05, 0.0), (0.66, 0.0), (0.7, 0.06), (0.0, 0.12)], ELBOW + [0.16, 0.0, 0.0],
                     (0, 0, 1), (0, 1, 0), 0.04, "accent"))
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-KF Falcon Arms", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 9.5, "armor": 250, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 16, "aim_speed": 400, "recoil_control": 0.95}}
    return m, meta


WING_ROOT_Y = 0.36
WING_X = 0.4
FIN_Y = -0.42


def booster_falcon():
    """BS-KF Falcon pack: a flat streamlined pack; the wings fold down its sides."""
    m = mk.Model("bs_falcon")
    m.add("pack", pstack([(-0.44, 0, -0.1, 0.34, 0.14, 0.14), (0.0, 0, -0.1, 0.4, 0.16, 0.18),
                          (0.44, 0, -0.1, 0.3, 0.14, 0.14)], "armor2", sh=0.7, rb=0.85))
    noz = []
    for s in (1, -1):
        m.add("pack", nozzle((0.2 * s, -0.4, -0.2), (0, -0.7, -0.7), 0.11, 0.22))
        m.add("pack", cyl((WING_X * s, WING_ROOT_Y, 0.02), (WING_X * s, WING_ROOT_Y, -0.26), 0.07, n=8, tag="frame"))
        noz.append({"pos": r3((0.2 * s, -0.56, -0.36)), "dir": [0, -0.7, -0.7], "size": 0.3})
        # wing: folded down the pack side - span along -Y, chord along Z (leading edge toward +Z)
        x0 = (WING_X + 0.07) * s
        x1 = (WING_X + 0.11) * s
        plan = [(WING_ROOT_Y, 0.04), (WING_ROOT_Y, -0.42), (-1.55, -0.74), (-1.55, -0.48)]
        wing = mk.prism([(x0, y, z) for y, z in plan], [(x1, y, z) for y, z in plan], tag="armor")
        stripe = mk.prism([(x1, -1.05, -0.55), (x1, -1.05, -0.3), (x1, -1.35, -0.4), (x1, -1.35, -0.64)],
                          [(x1 + 0.02 * s, -1.05, -0.55), (x1 + 0.02 * s, -1.05, -0.3), (x1 + 0.02 * s, -1.35, -0.4), (x1 + 0.02 * s, -1.35, -0.64)],
                          tag="accent")
        m.add(pre_w(s), wing, stripe)
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-KF Falcon Pack", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 5.5, "armor": 110,
            "stats": {"boost_speed": 1.1, "qb_speed": 2.6, "qb_cost": 320, "qb_cooldown": 9, "hover_thrust": 0.17,
                      "hover_cost": 14, "air_boost_cost": 5, "nozzles": noz}}
    return m, meta


def pre_w(s):
    return "l_wing" if s > 0 else "r_wing"


def pre_f(s):
    return "l_fin" if s > 0 else "r_fin"


ALL = [legs_falcon, core_falcon, head_falcon, arms_falcon, booster_falcon]


# ---------------------------------------------------------------------------------- jet rig

def frame_rot(u0, v0, u1, v1):
    """Rotation taking direction u0 -> u1 and (the plane of) v0 -> v1."""
    def basis(u, v):
        u = np.asarray(u, float); u = u / np.linalg.norm(u)
        v = np.asarray(v, float); v = v - u * np.dot(u, v); v = v / np.linalg.norm(v)
        return np.column_stack([u, v, np.cross(u, v)])
    return basis(u1, v1) @ basis(u0, v0).T


def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    robot = rig.robot_keys(parts)
    r = rig.Rig(robot)
    legs = parts["legs"][1]
    # core: lies flat - its +Y (head end) to the nose, its front (+Z) down, the spine on top
    r.root("core:core", R3(rx=90), offset, delay=0.1)
    # nose: the prow swings up about the cockpit ball, ending ahead of it with the canopy on top
    r.joint("core:nose", "core:core", NOSE_HINGE, axis_angle((1, 0, 0), -90), delay=0.3)
    # head: folds back off the nose's path and settles on the spine behind the canopy
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), -90), slide=(0, -0.3, -0.34), delay=0.15)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y, 0), None, delay=0.0)
    r.joint("booster:pack", "core:core", (0, 0, 0), None, delay=0.1)
    for s, pre in ((1, "l_"), (-1, "r_")):
        ch = next(c for c in legs["stats"]["chains"] if c["upper"] == pre + "thigh")
        hip, knee, ankle = (np.array(ch[k], float) for k in ("hip", "knee", "ankle"))
        # thighs: stretched straight aft (the fuselage's tail) and closed together
        v0 = knee - hip
        L = np.linalg.norm(v0)
        dx = 0.26 * s - hip[0]
        v_des = np.array([dx, -math.sqrt(max(1e-6, L * L - dx * dx)), 0.0])
        R_thigh = align(v0, v_des)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, delay=0.0)
        # knees: fold the shins forward over the thighs, onto the back (toward the thigh's rear face,
        # which is the airframe's top): half of each leg is carried on the back
        a = -v_des / np.linalg.norm(v_des)
        b = axis_angle(np.cross(a, [0, 0, -1.0]), 21) @ a
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, align(ankle - knee, R_thigh.T @ b), delay=0.12)
        # feet: level, toes forward, resting on the folded shins
        W = r.world["legs:" + pre + "shin"][:3, :3]
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, W.T @ r.world["core:core"][:3, :3] @ R3(rx=-90), delay=0.25)
        # arms: hang back along the flanks; forearm folded forward, splayed out a little
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 6 * s), delay=0.1)
        e = ELBOW * [s, 1, 1]
        g = GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.22 * s, 1.0, -0.12)), delay=0.15)
        # pack: wings swing out flat (span sideways, leading edge toward the nose), fins stand up aft
        r.joint("booster:" + pre_w(s), "booster:pack", (WING_X * s, WING_ROOT_Y, -0.12),
                frame_rot((0, -1, 0), (0, 0, 1), (s, 0, -0.05), (0, 1, 0)), delay=0.55)
    return r


def apply_rig(built, weapons):
    """Writes jet_pose (and the booster's jet nozzles, the core's jet collision height) into the
    falcon parts' meta, placing the airframe on the ground and centred along Z."""
    parts = {"legs": built["lg_falcon"], "core": built["cr_falcon"], "head": built["hd_falcon"],
             "arms": built["am_falcon"], "booster": built["bs_falcon"]}
    parts.update(weapons)
    r = build_rig(parts, (0, 0, 0))
    lo, hi = rig.model_at(parts, r.world, "probe").bbox()
    r = build_rig(parts, (0.0, -lo[1] + 0.12, -(lo[2] + hi[2]) / 2))
    for mount, pid in (("legs", "lg_falcon"), ("core", "cr_falcon"), ("head", "hd_falcon"), ("arms", "am_falcon"),
                       ("booster", "bs_falcon")):
        model, meta = built[pid]
        meta["jet_pose"] = {g: r.pose_json(mount + ":" + g) for g in model.order}
    jet_noz = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        ch = next(c for c in parts["legs"][1]["stats"]["chains"] if c["upper"] == pre + "thigh")
        hip, knee = np.array(ch["hip"], float), np.array(ch["knee"], float)
        axis = (knee - hip) / np.linalg.norm(knee - hip)
        p = r.point("legs:" + pre + "thigh", thigh_pod(hip, knee, s) + axis * 0.2)
        d = r.axis("legs:" + pre + "thigh", axis)
        jet_noz.append({"pos": r3(p), "dir": r3(d / np.linalg.norm(d)), "size": 0.4})
    built["bs_falcon"][1]["stats"]["jet_nozzles"] = jet_noz
    lo, hi = rig.model_at(parts, r.world, "jet").bbox()
    built["cr_falcon"][1]["stats"]["jet"]["collision_height"] = round(float(hi[1] - lo[1]) * 0.85, 2)
    return parts


def build_set(weapons=None):
    """All five parts with their jet poses. weapons: {mount: (model, meta)} carried in the preset
    (only used to size the airframe); loaded from the exported files when not given."""
    built = {}
    for b in ALL:
        m, meta = b()
        built[m.name] = (m, meta)
    if weapons is None:
        from mg import load_part
        weapons = {"r_arm": load_part("wa_gunpod"), "l_arm": load_part("wa_gunpod"), "l_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
