"""Kestrel 'Swallow' transform set: a light biped that stretches out into a long, flat fighter.

Robot form follows the light design language, but the core is a thin open frame (flat_core) so that
lying flat it is only ~0.6 m deep - the airframe is as low as a real fighter.

Transformation (core is the root, laid chest-UP with the head end forward - the cockpit ball, under
its glass, becomes the bubble canopy on top):
  - nose:   the long V chest plate, hinged at the top of the chest, flips 180 degrees over the
            shoulders and becomes the nose cone ahead of the cockpit
  - head:   tips back under the collar and hangs below the nose root as a chin sensor pod
  - legs:   stay straight - knees locked, legs closed together into the rear fuselage; the feet
            point aft (toe thrusters = the engines), the shin blades stand up as twin tail fins
  - arms:   straight back along the flanks, the gun pods turned forward under the hands
  - pack:   rides under the belly; the big wings, stowed raised behind the shoulders, swing out flat (low wing)
Jet space: +Z nose, +Y up, origin on the ground under the airframe."""
import math

import numpy as np

from mg import mk, cyl, cbox, mirror, nozzle, finish, r3, pstack, pseg, slab, plate
import rig
from rig import R3, align, axis_angle
from parts_kf import frame_rot

SCHEME = "ks"
MAKER = "Kestrel Dynamics"
SET = "swallow"

HIP_Y = 2.9
NOSE_HINGE = (0.0, 1.3, 0.3)


# ---------------------------------------------------------------------------------- shared thin core

def flat_core(m, w=1.0):
    """A thin transforming torso: a faceted cockpit pod under a glass hood, held between two side rails,
    a shoulder yoke above and a waist beam below (the cage is open, the pod shows from the front and
    the sides). Only ~0.6 m deep front to back, so it lies flat in jet form. Returns the core's points."""
    m.add("core", pstack([(-0.08, 0, 0.0, 0.2 * w, 0.2, 0.18), (0.42, 0, 0.0, 0.22 * w, 0.22, 0.2)], "frame"))
    m.add("core", pseg([(-0.38 * w, 0.4, 0.0), (0.38 * w, 0.4, 0.0)], [(0.08, 0.2, 0.18), (0.08, 0.2, 0.18)], "armor2",
                       fwd=(0, 0, 1)))
    # faceted cockpit pod and its glass hood
    m.add("core", mk.ellipsoid((0.0, 0.8, 0.0), 0.3 * w, 0.32, 0.26, nu=8, nv=4, tag="armor2"))
    m.add("core", mk.ellipsoid((0.0, 0.86, 0.1), 0.2 * w, 0.22, 0.17, nu=8, nv=4, tag="glass"))
    for s in (1, -1):
        # side rail: narrow from the front, deep from the side, pinched at the waist
        m.add("core", pseg([(0.34 * s * w, 0.34, 0.0), (0.44 * s * w, 0.8, 0.0), (0.42 * s * w, 1.22, 0.0)],
                           [(0.07, 0.18, 0.16), (0.08, 0.26, 0.22), (0.08, 0.24, 0.2)], "armor", fwd=(0, 0, 1)))
        m.add("core", cyl((0.24 * s * w, 0.8, 0.0), (0.38 * s * w, 0.8, 0.0), 0.12, n=8, tag="frame"))
        # chine: an accent blade along the rail's front edge
        m.add("core", slab([(0.0, -0.3), (0.0, 0.3), (0.05, 0.26), (0.05, -0.22)], (0.44 * s * w, 0.8, 0.24),
                           (0, 0, 1), (0, 1, 0), 0.04, "accent"))
    # shoulder yoke with thick joints
    m.add("core", pseg([(-0.56 * w, 1.22, 0.0), (0.0, 1.24, 0.02), (0.56 * w, 1.22, 0.0)],
                       [(0.09, 0.22, 0.2), (0.11, 0.28, 0.22), (0.09, 0.22, 0.2)], "armor", fwd=(0, 0, 1)))
    for s in (1, -1):
        m.add("core", cyl((0.5 * s * w, 1.22, 0), (0.9 * s * w, 1.22, 0), 0.13, n=10, tag="frame"))
        m.add("core", cyl((0.5 * s * w, 1.22, 0), (0.62 * s * w, 1.22, 0), 0.18, n=10, tag="armor2"))
        # weapon pylon (back_l / back_r) on the yoke's rear
        m.add("core", cbox(0.3 * s * w, 1.3, -0.24, 0.16, 0.16, 0.18, "armor2", c=0.03))
    m.add("core", cyl((0, 1.26, 0), (0, 1.46, 0), 0.09, n=8, tag="frame"))
    # booster mount plate on the back
    m.add("core", plate((0, 0.84, -0.24), (1, 0, 0), (0, 1, 0), 0.24, 0.3, 0.08, "armor2", c=0.05))
    return {"neck": [0, 1.44, 0.0], "shoulder_l": [round(0.88 * w, 3), 1.22, 0], "back": [0, 0.84, -0.26],
            "back_l": [round(0.3 * w, 3), 1.3, -0.26], "seat": [0, 0.8, 0.0], "eye": [0, 1.72, 0.3]}


# ---------------------------------------------------------------------------------- legs

def legs_swallow():
    """LG-KS Swallow legs: light legs with blade fins on the shins and thrusters in the toes."""
    from parts_kd import _biped_leg
    m = mk.Model("lg_swallow")
    m.add("pelvis", pstack([(2.62, 0, 0.0, 0.26, 0.3, 0.22), (2.76, 0, 0.0, 0.4, 0.4, 0.28),
                            (3.1, 0, 0.0, 0.38, 0.38, 0.28), (3.2, 0, 0.0, 0.26, 0.28, 0.2)], "armor2"))
    m.add("pelvis", pseg([(0, 3.02, 0.3), (0, 2.62, 0.4)], [(0.12, 0.14, 0.06), (0.05, 0.08, 0.04)], "accent", fwd=(0, 0, 1)))
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.42 * s, 2.86, 0.0])
        knee = np.array([0.44 * s, 1.62, 0.34])
        ankle = np.array([0.44 * s, 0.42, -0.02])
        thigh, shin, foot = _biped_leg(s, hip, knee, ankle)
        # shin blade: raked forward from the shin guard (stands up as a tail fin in jet form)
        c = knee + (ankle - knee) * 0.6 + np.array([0.04 * s, 0, 0.16])
        shin.append(slab([(0.0, -0.36), (0.34, -0.2), (0.4, 0.06), (0.0, 0.34)], c, (0.22 * s, 0, 1), (0, 1, 0), 0.04, "armor2"))
        n = np.cross([0.22 * s, 0, 1], [0, 1, 0])
        shin.append(slab([(0.12, -0.18), (0.3, -0.12), (0.33, 0.0), (0.14, 0.08)], c - s * 0.03 * n / np.linalg.norm(n),
                         (0.22 * s, 0, 1), (0, 1, 0), 0.02, "accent"))
        # toe thruster
        foot.append(nozzle((ankle[0], 0.1, ankle[2] + 0.62), (0, 0, 1), 0.1, 0.16))
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1],
                       "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=0.8)
    meta = {"slot": "legs", "display_name": "LG-KS Swallow Legs", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 16.0, "armor": 250, "points": {"hip": [0, HIP_Y, 0]},
            "stats": {"type": "biped", "load_limit": 46, "walk_speed": 0.34, "boost_multiplier": 1.12, "jump": 0.95,
                      "turn_rate": 18, "step_height": 1.15, "traction": 0.35, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.1, "collision_width": 1.6, "step_length": 1.5, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def core_swallow():
    """CR-KS Swallow core: the thin frame torso; a long V plate on the chest is the fighter's nose."""
    m = mk.Model("cr_swallow")
    pts = flat_core(m)
    # nose hinge: a pin on the yoke front; the nose's sleeves share its axis
    hx, hy, hz = NOSE_HINGE
    m.add("core", cyl((-0.1, hy, hz), (0.1, hy, hz), 0.07, n=8, tag="frame"))
    m.add("nose", cyl((-0.2, hy, hz), (-0.06, hy, hz), 0.07, n=8, tag="frame"),
          cyl((0.06, hy, hz), (0.2, hy, hz), 0.07, n=8, tag="frame"))
    # the V chest plate / nose cone: ridge forward (the keel in jet form), flat back over the cockpit
    m.add("nose", pseg([(0, hy, hz + 0.06), (0, 0.9, 0.44), (0, 0.1, 0.38)],
                       [(0.2, 0.14, 0.08), (0.17, 0.15, 0.08), (0.04, 0.05, 0.04)], "armor", fwd=(0, 0, 1)))
    for s in (1, -1):
        m.add("nose", slab([(0.0, 0.0), (0.42, -0.04), (0.44, 0.02), (0.02, 0.07)], (0.07 * s, 1.0, 0.5),
                           (s * 0.18, -0.98, 0.0), (0, 0.1, 1), 0.025, "accent"))
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-KS Swallow Core", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 14.0, "armor": 410, "points": pts,
            "stats": {"en_capacity": 1750, "en_recharge": 37, "recharge_delay": 10, "defense": 0.92, "fuel": 1000,
                      "stability": 0.9,
                      "jet": {"max_speed": 3.6, "acceleration": 0.055, "turn_speed": 2.1, "stall_fraction": 0.26,
                              "afterburner": 1.5, "afterburner_cost": 10, "collision_height": 1.2}}}
    return m, meta


def head_swallow():
    """HD-KS Swallow head: a narrow helmet with a long forward visor and a short swept crest."""
    m = mk.Model("hd_swallow")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=8, tag="frame"))
    m.add("head", pstack([(0.04, 0, 0.0, 0.13, 0.24, 0.18), (0.22, 0, 0.02, 0.16, 0.34, 0.22),
                          (0.36, 0, -0.02, 0.11, 0.2, 0.2), (0.42, 0, -0.05, 0.05, 0.1, 0.14)], "armor", sh=0.45))
    m.add("head", pseg([(0, 0.2, 0.3), (0, 0.24, 0.36)], [(0.12, 0.04, 0.03), (0.1, 0.03, 0.02)], "eye", fwd=(0, 1, 0)))
    m.add("head", slab([(0.0, 0.0), (0.0, 0.12), (-0.3, 0.06), (-0.26, 0.0)], (0, 0.38, 0.1), (0, 0, 1), (0, 1, 0), 0.03, "accent"))
    for s in (1, -1):
        m.add("head", pseg([(0.12 * s, 0.14, 0.0), (0.2 * s, 0.1, -0.12)], [(0.04, 0.06, 0.04), (0.02, 0.04, 0.03)], "armor2",
                           fwd=(0, 0, -1)))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-KS Swallow Head", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 2.2, "armor": 115, "points": {"top": [0, 0.46, 0]},
            "stats": {"lock_range": 125, "lock_cone": 13, "stability": 0.9}}
    return m, meta


ELBOW = np.array([0.11, -0.96, 0.07])
GRIP = np.array([0.11, -1.05, 0.95])


def arms_swallow():
    from parts_kd import light_arm
    m = mk.Model("am_swallow")
    upper, fore = light_arm(ELBOW, GRIP, w=0.9, blade=True)
    upper.append(slab([(0.0, 0.0), (0.3, 0.0), (0.34, 0.05), (0.04, 0.09)], (0.2, 0.16, 0.08),
                      (0, 0, 1), (0, 1, 0), 0.03, "accent"))
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-KS Swallow Arms", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 9.0, "armor": 240, "points": {"elbow": r3(ELBOW), "grip": r3(GRIP)},
            "stats": {"load_limit": 15, "aim_speed": 410, "recoil_control": 0.92}}
    return m, meta


WING_ROOT_Y = 0.32
WING_X = 0.42


def wing_name(s):
    return "l_wing" if s > 0 else "r_wing"


def wing_axes(s):
    """Stowed wing frame (pack space): span u (up, splayed out 20 deg, leaning back 14 deg), chord v
    (toward the leading edge, forward), normal."""
    u = np.array([math.sin(math.radians(20)) * s, math.cos(math.radians(20)) * math.cos(math.radians(14)), -math.sin(math.radians(14))])
    u /= np.linalg.norm(u)
    v = np.array([0.0, 0.0, 1.0]) - u * u[2]
    v /= np.linalg.norm(v)
    return u, v, np.cross(u, v)


def booster_swallow():
    """BS-KS Swallow pack: twin flat thruster pods on a bridge; two big wings stand behind the shoulders
    like binders (the main wings in jet form)."""
    m = mk.Model("bs_swallow")
    m.add("pack", pstack([(-0.36, 0, -0.1, 0.3, 0.14, 0.12), (0.32, 0, -0.1, 0.3, 0.14, 0.12)], "armor2", sh=0.7, rb=0.85))
    noz = []
    for s in (1, -1):
        m.add("pack", pstack([(-0.5, 0.24 * s, -0.16, 0.12, 0.12, 0.14), (0.0, 0.24 * s, -0.18, 0.15, 0.14, 0.18),
                              (0.42, 0.24 * s, -0.16, 0.11, 0.12, 0.12)], "armor", sh=0.6))
        m.add("pack", nozzle((0.24 * s, -0.48, -0.2), (0, -0.8, -0.6), 0.1, 0.2))
        m.add("pack", cyl((WING_X * s, WING_ROOT_Y, 0.02), (WING_X * s, WING_ROOT_Y, -0.28), 0.07, n=8, tag="frame"))
        noz.append({"pos": r3((0.24 * s, -0.62, -0.32)), "dir": [0, -0.8, -0.6], "size": 0.3})
        # wing: stowed raised behind the shoulder like a binder, splayed out and leaning back.
        # Planform in (span t, chord c): long, broad and swept, leading edge toward +c.
        u, v, nrm = wing_axes(s)
        root = np.array([(WING_X + 0.09) * s, WING_ROOT_Y, -0.12])
        plan = [(0.0, 0.42), (0.0, -0.86), (2.3, -1.2), (2.36, -0.92), (1.1, -0.18)]
        def pts(poly, off):
            return [root + u * t + v * c + nrm * off for t, c in poly]
        wing = mk.prism(pts(plan, -0.035), pts(plan, 0.035), tag="armor")
        glove = pseg([root - u * 0.05, root + u * 0.7], [(0.07, 0.5, 0.6), (0.05, 0.3, 0.4)], "armor2", fwd=v)
        hub = cyl((WING_X * s, WING_ROOT_Y, -0.02), (WING_X * s, WING_ROOT_Y, -0.22), 0.11, n=8, tag="frame")
        edge = [(0.25, 0.3), (0.25, 0.14), (2.2, -0.9), (2.3, -0.88)]
        tip = [(1.75, -0.62), (1.75, -1.1), (2.3, -1.2), (2.36, -0.92)]
        stripe = mk.prism(pts(edge, 0.03), pts(edge, 0.05), tag="accent")
        stripe2 = mk.prism(pts(tip, 0.03), pts(tip, 0.05), tag="armor2")
        m.add(wing_name(s), wing, glove, hub, stripe2)
        wing = stripe
        m.add(wing_name(s), wing)
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-KS Swallow Pack", "maker": MAKER, "tier": 5, "transform_set": SET,
            "weight": 5.2, "armor": 105,
            "stats": {"boost_speed": 1.12, "qb_speed": 2.6, "qb_cost": 320, "qb_cooldown": 9, "hover_thrust": 0.16,
                      "hover_cost": 14, "air_boost_cost": 5, "nozzles": noz}}
    return m, meta


ALL = [legs_swallow, core_swallow, head_swallow, arms_swallow, booster_swallow]
IDS = {"legs": "lg_swallow", "core": "cr_swallow", "head": "hd_swallow", "arms": "am_swallow", "booster": "bs_swallow"}


# ---------------------------------------------------------------------------------- jet rig

def chain_of(legs, pre):
    ch = next(c for c in legs["stats"]["chains"] if c["upper"] == pre + "thigh")
    return (np.array(ch[k], float) for k in ("hip", "knee", "ankle"))


def build_rig(parts, offset=(0.0, 0.0, 0.0)):
    r = rig.Rig(rig.robot_keys(parts))
    legs = parts["legs"][1]
    # core: flat on its back - head end to the nose, chest (and the canopy) up
    r.root("core:core", R3(rx=-90, ry=180), offset, delay=0.1)
    # nose: the chest plate flips over the shoulders about its hinge
    r.joint("core:nose", "core:core", NOSE_HINGE, axis_angle((1, 0, 0), 180), delay=0.3)
    # head: tips back under the collar - a chin pod looking forward
    r.joint("head:head", "core:core", (0, 0, 0), axis_angle((1, 0, 0), -90), slide=(0, 0, -0.08), delay=0.0)
    r.joint("legs:pelvis", "core:core", (0, HIP_Y, 0), None, delay=0.0)
    r.joint("booster:pack", "core:core", (0, 0, 0), None, delay=0.1)
    core_w = r.world["core:core"][:3, :3]
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip, knee, ankle = chain_of(legs, pre)
        # legs straight, closed together: the rear fuselage
        d = np.array([0.24 * s - hip[0], -(hip[1] - ankle[1]), 0.0])
        R_thigh = align(knee - hip, d)
        r.joint("legs:" + pre + "thigh", "legs:pelvis", hip, R_thigh, delay=0.0)
        r.joint("legs:" + pre + "shin", "legs:" + pre + "thigh", knee, align(ankle - knee, R_thigh.T @ d), delay=0.1)
        # feet pointed aft along the legs: the toe thrusters are the engines
        W = r.world["legs:" + pre + "shin"][:3, :3]
        r.joint("legs:" + pre + "foot", "legs:" + pre + "shin", ankle, W.T @ core_w @ R3(rx=90), delay=0.25)
        # arms straight back along the flanks
        r.joint("arms:" + pre + "upper", "core:core", (0, 0, 0), axis_angle((0, 0, 1), 4 * s), delay=0.1)
        e, g = ELBOW * [s, 1, 1], GRIP * [s, 1, 1]
        r.joint("arms:" + pre + "fore", "arms:" + pre + "upper", e, align(g - e, (0.04 * s, -1.0, 0.0)), delay=0.15)
        # wings swing out under the belly: span outward and a little aft, leading edge to the nose
        Wp = r.world["booster:pack"][:3, :3]
        sweep = math.radians(14)
        out = np.array([-s * math.cos(sweep), 0.0, -math.sin(sweep)])     # jet space (robot left is jet right)
        u0, v0, _ = wing_axes(s)
        Wd = frame_rot(u0, v0, out, (0, 0, 1))
        r.joint("booster:" + wing_name(s), "booster:pack", (WING_X * s, WING_ROOT_Y, -0.12), Wp.T @ Wd, delay=0.55)
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
        hip, knee, ankle = chain_of(parts["legs"][1], pre)
        p = r.point("legs:" + pre + "foot", (ankle[0], 0.1, ankle[2] + 0.8))
        d = r.axis("legs:" + pre + "foot", (0, 0, 1))
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
        weapons = {"r_arm": load_part("wa_gunpod"), "l_arm": load_part("wa_gunpod"), "l_back": load_part("wb_missile")}
    apply_rig(built, weapons)
    return built
