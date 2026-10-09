"""Grauwerk Heavy Industries - heavy armour, tank legs (olive / grey / hazard yellow).

Design language (heavy): thick, polygonal masses with heavy chamfers and bolted-on armour plates.
Joints are wide axles; arms are as broad as the legs, under big box pauldrons. Fronts are sloped
like a tank's glacis."""
import numpy as np

from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3, plate, slab, hbox

SCHEME = "gw"
MAKER = "Grauwerk Heavy Industries"


def heavy_arm(elbow, grip, k=1.0):
    """Left heavy arm, shoulder at the origin: big box pauldron with a sloped top plate, broad upper
    arm, wide elbow axle, a forearm with an outer shield plate and a heavy fist."""
    elbow = np.asarray(elbow, float)
    grip = np.asarray(grip, float)
    ex = elbow[0]
    upper = [cyl((-0.1, 0, 0), (0.24, 0, 0), 0.24 * k, n=10, tag="frame"),
             hbox(0.24, 0.08, 0.0, 0.66 * k, 0.66 * k, 0.86 * k, "armor", c=0.13, top=0.8),
             # sloped top plate overhanging outward, and a front plate
             plate((0.36, 0.44 * k, 0.0), (0, 0, 1), (1, -0.42, 0), 0.47 * k, 0.32 * k, 0.09, "armor2", c=0.08),
             plate((0.26, 0.04, 0.44 * k), (1, 0, 0), (0, 1, 0), 0.3 * k, 0.3 * k, 0.07, "armor2", c=0.06),
             cbox(0.58 * k, 0.06, 0.0, 0.06, 0.34 * k, 0.5 * k, "accent", c=0.02),
             # upper arm: as broad as a thigh
             stack([(elbow[1] + 0.06, ex, elbow[2] - 0.02, 0.18 * k, 0.2 * k, 0.07),
                    (-0.3, ex, 0.0, 0.2 * k, 0.22 * k, 0.08)], "frame"),
             cyl((ex - 0.26 * k, elbow[1], elbow[2]), (ex + 0.26 * k, elbow[1], elbow[2]), 0.21 * k, n=10, tag="frame")]
    fore = [stack([(elbow[1] - 0.26 * k, ex, (elbow[2] + grip[2]) / 2 - 0.08, 0.2 * k, 0.5, 0.08),
                   (elbow[1] - 0.2 * k, ex, (elbow[2] + grip[2]) / 2 - 0.08, 0.23 * k, 0.54, 0.1),
                   (elbow[1] + 0.18 * k, ex, (elbow[2] + grip[2]) / 2 - 0.08, 0.23 * k, 0.54, 0.1),
                   (elbow[1] + 0.24 * k, ex, (elbow[2] + grip[2]) / 2 - 0.1, 0.19 * k, 0.46, 0.08)], "armor"),
            # outer shield plate
            plate((ex + 0.27 * k, elbow[1] - 0.02, (elbow[2] + grip[2]) / 2 - 0.06), (0, 1, 0), (0, 0, 1),
                  0.3 * k, 0.58, 0.08, "armor2", c=0.07),
            cbox(ex, grip[1], grip[2] - 0.02, 0.32 * k, 0.32 * k, 0.32, "frame", c=0.05)]
    return upper, fore


# ---------------------------------------------------------------------------------- legs

def legs_pillar():
    """LG-G2 Pillar: heavy biped - thick legs under faceted armour."""
    m = mk.Model("lg_pillar")
    m.add("pelvis", hbox(0, 3.2, -0.02, 1.0, 0.62, 0.74, "armor2", c=0.14, top=0.86))
    for s in (1, -1):
        # tassets: sloped skirt plates over the hip joints
        m.add("pelvis", plate((0.42 * s, 2.98, 0.36), (s * 1, 0, 0.25), (0, 0.85, -0.35), 0.26, 0.3, 0.08, "armor", c=0.06))
    m.add("pelvis", plate((0, 3.06, 0.42), (1, 0, 0), (0, 0.9, -0.3), 0.2, 0.28, 0.08, "accent", c=0.05))
    chain = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.56 * s, 3.15, 0.0])
        knee = np.array([0.58 * s, 1.8, 0.4])
        ankle = np.array([0.58 * s, 0.46, -0.02])
        thigh = [cyl((0.3 * s, 3.15, 0), (0.86 * s, 3.15, 0), 0.27, n=10, tag="frame"),
                 seg([(0.57 * s, 3.2, -0.04), (0.58 * s, 2.5, 0.16), (0.58 * s, 1.95, 0.34)],
                     [(0.3, 0.34, 0.1), (0.31, 0.35, 0.1), (0.24, 0.27, 0.08)], "armor", c=0.1),
                 plate((0.9 * s, 2.6, 0.12), (0, 1, 0.25), (0, -0.1, 1) if s > 0 else (0, 0.1, -1), 0.42, 0.3, 0.08, "armor2", c=0.06)]
        shin = [cyl((0.3 * s, 1.8, 0.4), (0.86 * s, 1.8, 0.4), 0.24, n=10, tag="frame"),
                seg([(0.58 * s, 1.95, 0.48), (0.58 * s, 1.25, 0.12), (0.58 * s, 0.58, -0.04)],
                    [(0.3, 0.34, 0.1), (0.34, 0.42, 0.12), (0.24, 0.27, 0.08)], "armor", c=0.11),
                # knee armour: a faceted wedge riding over the joint, sloped forward
                plate((0.58 * s, 1.96, 0.74), (1, 0, 0), (0, 0.85, 0.5), 0.3, 0.34, 0.12, "armor2", c=0.08),
                # sloped shin plate with hazard stripe, and the calf block
                plate((0.58 * s, 1.18, 0.56), (1, 0, 0), (0, 0.95, 0.3), 0.27, 0.36, 0.07, "accent", c=0.05),
                hbox(0.58 * s, 1.15, -0.34, 0.5, 0.8, 0.32, "armor2", c=0.06)]
        foot = [cyl((0.34 * s, 0.46, -0.02), (0.82 * s, 0.46, -0.02), 0.19, n=8, tag="frame"),
                hbox(0.58 * s, 0.2, 0.16, 0.7, 0.4, 1.46, "armor2", c=0.1, top=0.82, bottom=1.0),
                plate((0.58 * s, 0.22, 0.92), (1, 0, 0), (0, 0.7, 0.7), 0.32, 0.18, 0.07, "armor", c=0.05)]
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chain.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                      "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1],
                      "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=1.2)
    meta = {"slot": "legs", "display_name": "LG-G2 Pillar", "maker": MAKER, "tier": 3,
            "weight": 30.0, "armor": 520,
            "points": {"hip": [0, 3.2, 0]},
            "stats": {"type": "biped", "load_limit": 78, "walk_speed": 0.27, "boost_multiplier": 0.9, "jump": 0.8,
                      "turn_rate": 12, "step_height": 1.2, "traction": 0.38, "qb_multiplier": 0.85,
                      "hover_multiplier": 0.85, "collision_width": 2.0, "step_length": 1.7, "step_lift": 0.45,
                      "step_ticks": 8, "pelvis_groups": ["pelvis"], "chains": chain}}
    return m, meta


def legs_rampart():
    """LG-G4 Rampart: tank legs - fast on the ground, heavy load, no jump (Space hovers).

    Seen from the side the tracks are a tank's trapezoid: a short ground run under the road wheels,
    the belt climbing forward to the idler and back to a raised drive sprocket, and a long top run on
    small return rollers, level with the waist joint so the track blocks shield it. The front is one
    shallow wedge, like a modern tank: a low glacis between the tracks continued by sloped fenders."""
    m = mk.Model("lg_rampart")
    slope = 0.30                       # glacis / fender rise per metre back from the nose
    # hull between the tracks: lower nose plate, long shallow glacis, flat deck under the waist ring
    m.add("hull", mk.extrude_zy([(-1.85, 0.35), (2.7, 0.35), (3.1, 0.88), (1.05, 0.88 + 2.05 * slope), (-1.85, 1.5),
                                 (-2.02, 1.12)], -0.8, 0.8, tag="armor"))
    g = np.array([0.0, slope, -1.0])
    # add-on armour block and hazard plate on the glacis, rear deck plate
    m.add("hull", plate((0, 0.88 + 0.75 * slope + 0.05, 3.1 - 0.75), (1, 0, 0), g, 0.6, 0.42, 0.1, "armor2", c=0.08))
    m.add("hull", plate((0, 0.88 + 1.55 * slope + 0.03, 3.1 - 1.55), (1, 0, 0), g, 0.42, 0.14, 0.06, "accent", c=0.04))
    m.add("hull", plate((0, 1.3, -1.94), (1, 0, 0), (0, 0.9, 0.45), 0.66, 0.2, 0.08, "armor2", c=0.06))
    wheels = []
    top = 1.9
    for s, pre in ((1, "l_"), (-1, "r_")):
        x0, x1 = sorted((0.78 * s, 1.46 * s))
        # belt: short ground run, climbing to the idler in front and the sprocket behind, long top run
        prof = [(-1.95, 0.0), (2.1, 0.0), (3.0, 1.0), (2.95, 1.22), (1.0, top), (-2.45, top), (-2.68, 1.6), (-2.64, 1.25)]
        m.add(pre + "track", mk.extrude_zy(prof, x0, x1, tag="frame"))
        # fenders: flat over the top run, then sloping down to the nose in line with the glacis
        f0, f1 = sorted((0.74 * s, 1.4 * s))
        m.add(pre + "track", mk.extrude_zy([(-2.56, top + 0.02), (0.92, top + 0.02), (0.92, top + 0.3), (-2.62, top + 0.3),
                                            (-2.7, top + 0.14)], f0, f1, tag="armor"))
        m.add(pre + "track", mk.extrude_zy([(0.9, top + 0.02), (3.04, top + 0.02 - 2.14 * slope), (3.16, top + 0.26 - 2.26 * slope),
                                            (0.9, top + 0.3)], f0, f1, tag="armor"))
        # side skirt over the middle: road wheels below and return rollers above stay in view
        m.add(pre + "track", plate((1.6 * s, 1.18, 0.1), (0, 0, 1), (0, 1, 0), 2.1, 0.24, 0.07, "armor2", c=0.06))
        m.add(pre + "track", plate((1.66 * s, 1.18, 1.1), (0, 0, 1), (0, 1, 0), 0.5, 0.14, 0.03, "accent", c=0.02))
        for i, z in enumerate((-1.45, -0.45, 0.55, 1.55)):
            g_ = f"{pre}w{i}"
            m.add(g_, mk.cylinder((1.44 * s, 0.52, z), (1.56 * s, 0.52, z), 0.36, n=8, tag="armor2"))
            m.add(g_, cbox(1.58 * s, 0.52, z, 0.06, 0.36, 0.08, "accent", c=0.01))
            wheels.append({"group": g_, "pivot": r3((1.5 * s, 0.52, z)), "radius": 0.36})
        # idler low in front where the belt turns up, drive sprocket raised at the rear
        for name, (y, z) in (("idler", (0.98, 2.68)), ("sprocket", (1.5, -2.32))):
            g_ = f"{pre}{name}"
            m.add(g_, mk.cylinder((1.44 * s, y, z), (1.6 * s, y, z), 0.3, n=8, tag="armor2"))
            wheels.append({"group": g_, "pivot": r3((1.51 * s, y, z)), "radius": 0.3})
        # small return rollers carrying the top run
        for i, z in enumerate((-1.5, -0.3, 0.9)):
            g_ = f"{pre}r{i}"
            m.add(g_, mk.cylinder((1.42 * s, top - 0.16, z), (1.58 * s, top - 0.16, z), 0.14, n=6, tag="armor2"))
            wheels.append({"group": g_, "pivot": r3((1.5 * s, top - 0.16, z)), "radius": 0.14})
    m.add("ring", cyl((0, 1.45, 0), (0, 1.8, 0), 0.62, n=12, tag="armor2"))
    finish(m, SCHEME, grime_top=1.1)
    meta = {"slot": "legs", "display_name": "LG-G4 Rampart", "maker": MAKER, "tier": 2,
            "weight": 34.0, "armor": 600,
            "points": {"hip": [0, 1.76, 0]},
            "stats": {"type": "tank", "load_limit": 95, "walk_speed": 0.5, "boost_multiplier": 1.15, "jump": 0.0,
                      "turn_rate": 11, "step_height": 1.05, "traction": 0.3, "qb_multiplier": 0.75,
                      "hover_multiplier": 0.7, "collision_width": 3.0, "pelvis_groups": ["ring"], "chains": [],
                      "wheels": wheels}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def core_bastion():
    """CR-G2 Bastion: heavy core - a wide chest with a sloped glacis and a gorget around the neck."""
    m = mk.Model("cr_bastion")
    m.add("core", stack([(-0.08, 0, 0.0, 0.4, 0.34, 0.1), (0.5, 0, 0.0, 0.46, 0.38, 0.1)], "frame"))
    # chest: widest at the shoulders, the upper front sloping back (glacis)
    m.add("core", stack([(0.4, 0, 0.0, 0.66, 0.5, 0.14), (0.8, 0, 0.04, 0.98, 0.64, 0.2),
                         (1.5, 0, 0.04, 1.02, 0.64, 0.2), (2.02, 0, -0.14, 0.72, 0.46, 0.14)], "armor"))
    # sloped glacis plate and the V of the chest plates
    m.add("core", plate((0, 1.78, 0.5), (1, 0, 0), (0, 0.72, -0.7), 0.62, 0.26, 0.1, "armor2", c=0.08))
    for s in (1, -1):
        m.add("core", plate((0.42 * s, 1.08, 0.7), (s * 0.94, 0, -0.34), (0, 1, 0.1), 0.34, 0.36, 0.09, "armor2", c=0.07))
        m.add("core", cbox(0.5 * s, 1.5, 0.72, 0.3, 0.1, 0.1, "accent", c=0.02))
        # side armour boxes and big shoulder axles
        m.add("core", hbox(0.96 * s, 1.0, 0.0, 0.2, 0.9, 0.9, "armor2", c=0.06))
        m.add("core", cyl((0.82 * s, 1.62, 0), (1.28 * s, 1.62, 0), 0.27, n=10, tag="frame"))
        m.add("core", hbox(0.62 * s, 1.94, -0.46, 0.3, 0.22, 0.3, "armor2", c=0.04))
    # gorget
    m.add("core", stack([(1.84, 0, -0.06, 0.42, 0.36, 0.1), (2.1, 0, -0.06, 0.36, 0.3, 0.08)], "armor2"))
    m.add("core", cyl((0, 1.94, -0.04), (0, 2.16, -0.04), 0.18, n=8, tag="frame"))
    # back mount plate (booster)
    m.add("core", stack([(0.9, 0, -0.62, 0.52, 0.06, 0.04), (1.72, 0, -0.62, 0.52, 0.06, 0.04)], "armor2"))
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-G2 Bastion", "maker": MAKER, "tier": 3, "weight": 24.0, "armor": 820,
            "points": {"neck": [0, 2.12, -0.04], "shoulder_l": [1.26, 1.62, 0], "back": [0, 1.3, -0.66],
                       "back_l": [0.62, 2.04, -0.46], "seat": [0, 1.1, 0.12], "eye": [0, 2.46, 0.36]},
            "stats": {"en_capacity": 2100, "en_recharge": 30, "recharge_delay": 14, "defense": 1.3, "fuel": 1000,
                      "stability": 1.3}}
    return m, meta


def head_visor():
    """HD-G2 Visor: heavy head - wide and low, a single visor slit under a brow plate; long lock range."""
    m = mk.Model("hd_visor")
    m.add("head", cyl((0, -0.08, 0), (0, 0.12, 0), 0.17, n=8, tag="frame"))
    m.add("head", hbox(0, 0.22, 0.0, 0.64, 0.42, 0.56, "armor", c=0.1, top=0.78, bottom=0.92))
    m.add("head", cbox(0, 0.24, 0.27, 0.5, 0.07, 0.06, "eye", c=0.015))
    m.add("head", plate((0, 0.36, 0.25), (1, 0, 0), (0, 0.5, -0.86), 0.32, 0.12, 0.06, "armor2", c=0.04))
    for s in (1, -1):
        m.add("head", hbox(0.36 * s, 0.18, -0.04, 0.12, 0.34, 0.42, "armor2", c=0.04))
    m.add("head", cbox(0.2, 0.47, -0.12, 0.06, 0.12, 0.2, "accent", c=0.01))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-G2 Visor", "maker": MAKER, "tier": 2, "weight": 3.5, "armor": 200,
            "points": {"top": [0, 0.44, 0]}, "stats": {"lock_range": 150, "lock_cone": 11, "stability": 1.2}}
    return m, meta


def arms_maul():
    """AM-G2 Maul: heavy arms, high load."""
    m = mk.Model("am_maul")
    elbow, grip = (0.18, -1.02, 0.08), (0.18, -1.14, 0.98)
    upper, fore = heavy_arm(elbow, grip)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-G2 Maul", "maker": MAKER, "tier": 2, "weight": 15.0, "armor": 420,
            "points": {"elbow": list(elbow), "grip": list(grip)},
            "stats": {"load_limit": 26, "aim_speed": 280, "recoil_control": 1.4}}
    return m, meta


def booster_thrust():
    """BS-G2 Thrust: heavy booster, strong hover - a slab-sided box with four big nozzles."""
    m = mk.Model("bs_thrust")
    m.add("booster", hbox(0, 0.0, -0.24, 1.12, 1.04, 0.62, "armor2", c=0.1, top=0.86, bottom=0.92))
    noz = []
    for s in (1, -1):
        m.add("booster", nozzle((0.32 * s, -0.46, -0.4), (0, -0.75, -0.66), 0.18, 0.32))
        m.add("booster", nozzle((0.3 * s, 0.24, -0.52), (0, 0, -1), 0.15, 0.26))
        m.add("booster", plate((0.6 * s, 0.0, -0.24), (0, 1, 0), (0, 0, 1) if s > 0 else (0, 0, -1), 0.44, 0.28, 0.08, "armor", c=0.06))
        m.add("booster", cbox(0.66 * s, 0.0, -0.24, 0.04, 0.5, 0.36, "accent", c=0.01))
        noz.append({"pos": r3((0.32 * s, -0.68, -0.6)), "dir": [0, -0.75, -0.66], "size": 0.35})
        noz.append({"pos": r3((0.3 * s, 0.24, -0.82)), "dir": [0, 0, -1], "size": 0.3})
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-G2 Thrust", "maker": MAKER, "tier": 2, "weight": 7.0, "armor": 140,
            "stats": {"boost_speed": 0.95, "qb_speed": 2.2, "qb_cost": 380, "qb_cooldown": 12, "hover_thrust": 0.19,
                      "hover_cost": 13, "air_boost_cost": 6, "nozzles": noz}}
    return m, meta


ALL = [legs_pillar, legs_rampart, core_bastion, head_visor, arms_maul, booster_thrust]
