"""Kestrel Dynamics - light, fast frames (white / navy / cyan).

Design language (light): slim from the front, deep and jagged from the side. Main masses - head,
chest, shins, feet - are rounded pentagons pointing forward and stick out front and back; the
connections between them (neck, waist, upper arm, ankle) are thin, so the side view pinches at every
joint. Important joints (shoulder, elbow, knee) keep some thickness. The chest is a sloped prow,
like a tank's glacis."""
import numpy as np

from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3, pstack, pseg, slab, plate

SCHEME = "kd"
MAKER = "Kestrel Dynamics"


# ---------------------------------------------------------------------------------- legs

def _biped_leg(s, hip, knee, ankle, scale=1.0):
    """One light biped leg (side s = +1 left / -1 right): thigh, shin, foot shell lists."""
    k = scale
    hx, kx, ax = hip[0], knee[0], ankle[0]
    thigh = [cyl((hx - 0.17 * s, hip[1], hip[2]), (hx + 0.17 * s, hip[1], hip[2]), 0.17 * k, n=10, tag="frame"),
             # thin strut with a streamlined front plate: narrow from the front, deep from the side
             pseg([hip + [0, -0.05, 0.02], (hip + knee) / 2 + [0, 0, 0.05], knee + [0, 0.14, 0.02]],
                  [(0.16 * k, 0.24 * k, 0.15 * k), (0.15 * k, 0.26 * k, 0.16 * k), (0.11 * k, 0.17 * k, 0.12 * k)],
                  "armor", fwd=(0, 0, 1)),
             pseg([hip + [0, -0.25, 0.22 * k], (hip + knee) / 2 + [0, 0, 0.27 * k]],
                  [(0.09 * k, 0.07, 0.03), (0.07 * k, 0.06, 0.03)], "armor2", fwd=(0, 0, 1))]
    shin = [cyl((kx - 0.15 * s, knee[1], knee[2]), (kx + 0.15 * s, knee[1], knee[2]), 0.15 * k, n=10, tag="frame"),
            # knee guard: a blade pointing forward and up
            pseg([knee + [0, -0.22, 0.12], knee + [0, 0.22, 0.24]], [(0.12 * k, 0.2, 0.08), (0.06 * k, 0.12, 0.05)],
                 "armor2", fwd=(0, 0.3, 1)),
            # shin: shin guard sticks forward, calf sticks back, narrow at the ankle
            pseg([knee + [0, -0.05, 0.02], knee + (ankle - knee) * 0.35, knee + (ankle - knee) * 0.75, ankle + [0, 0.12, 0]],
                 [(0.15 * k, 0.22 * k, 0.2 * k), (0.17 * k, 0.32 * k, 0.3 * k), (0.14 * k, 0.24 * k, 0.2 * k),
                  (0.09 * k, 0.12 * k, 0.1 * k)], "armor", fwd=(0, 0, 1)),
            # calf fin
            slab([(0.0, 0.0), (0.0, -0.75), (-0.3, -0.62), (-0.26, -0.05)],
                 knee + (ankle - knee) * 0.25 + [0, 0, -0.2 * k], (0, 0, 1), (0, 1, 0), 0.05, "accent")]
    foot = [cyl((ax - 0.1 * s, ankle[1], ankle[2]), (ax + 0.1 * s, ankle[1], ankle[2]), 0.11 * k, n=8, tag="frame"),
            pstack([(0.0, ax, ankle[2] + 0.15, 0.17 * k, 0.62 * k, 0.4 * k),
                     (0.16, ax, ankle[2] + 0.14, 0.17 * k, 0.6 * k, 0.4 * k),
                     (ankle[1] + 0.04, ax, ankle[2] + 0.02, 0.1 * k, 0.24 * k, 0.16 * k)], "armor2", sh=0.3),
            pstack([(0.0, ax, ankle[2] - 0.34 * k, 0.08, 0.14, 0.26 * k), (0.12, ax, ankle[2] - 0.34 * k, 0.07, 0.12, 0.22 * k)],
                   "armor", sh=0.5)]
    return thigh, shin, foot


def legs_stride():
    """LG-K1 Stride: light biped. Hip 2.9 m."""
    m = mk.Model("lg_stride")
    # waist: a narrow pentagon pointing forward, with a forward-raked crotch blade
    m.add("pelvis", pstack([(2.58, 0, 0.0, 0.26, 0.3, 0.22), (2.74, 0, 0.0, 0.4, 0.42, 0.28),
                            (3.1, 0, 0.0, 0.38, 0.4, 0.28), (3.2, 0, 0.0, 0.26, 0.28, 0.2)], "armor2"))
    m.add("pelvis", pseg([(0, 3.02, 0.3), (0, 2.6, 0.42)], [(0.12, 0.14, 0.06), (0.05, 0.08, 0.04)], "accent", fwd=(0, 0, 1)))
    chain = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.42 * s, 2.86, 0.0])
        knee = np.array([0.44 * s, 1.62, 0.34])
        ankle = np.array([0.44 * s, 0.38, -0.02])
        thigh, shin, foot = _biped_leg(s, hip, knee, ankle)
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chain.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                      "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1],
                      "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=0.9)
    meta = {"slot": "legs", "display_name": "LG-K1 Stride", "maker": MAKER, "tier": 1,
            "weight": 16.0, "armor": 260,
            "points": {"hip": [0, 2.9, 0]},
            "stats": {"type": "biped", "load_limit": 46, "walk_speed": 0.34, "boost_multiplier": 1.05, "jump": 0.98,
                      "turn_rate": 18, "step_height": 1.15, "traction": 0.36, "qb_multiplier": 1.05,
                      "hover_multiplier": 1.0, "collision_width": 1.6, "step_length": 1.5, "step_lift": 0.5,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chain}}
    return m, meta


def legs_hopper():
    """LG-K3 Hopper: double-jointed legs - huge jumps, light. Seen from the side each leg is a zigzag
    like a lightning bolt: a short thigh runs forward and down to the knee, a short middle segment
    rises back to the reversed hock (higher than the knee), and a long shin runs forward down to the ankle. Low stance, torso leaning forward."""
    m = mk.Model("lg_hopper")
    H = 2.5                                   # hip joint height
    m.add("pelvis", pstack([(H - 0.28, 0, -0.02, 0.26, 0.3, 0.22), (H - 0.12, 0, 0.0, 0.42, 0.42, 0.3),
                            (H + 0.22, 0, 0.0, 0.4, 0.4, 0.28), (H + 0.32, 0, -0.02, 0.28, 0.28, 0.2)], "armor2"))
    m.add("pelvis", pseg([(0, H + 0.12, 0.3), (0, H - 0.26, 0.4)], [(0.12, 0.14, 0.06), (0.05, 0.08, 0.04)], "accent", fwd=(0, 0, 1)))
    chain = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        x = 0.46 * s
        hip = np.array([x, H, 0.0])
        knee = np.array([x, 1.6, 0.45])
        hock = np.array([x, 1.82, -0.25])
        ankle = np.array([x, 0.5, 0.15])
        def axle(p, r, w):
            return cyl((p[0] - w * s, p[1], p[2]), (p[0] + w * s, p[1], p[2]), r, n=10, tag="frame")
        thigh = [axle(hip, 0.18, 0.18),
                 # short thigh, raked forward down to the knee
                 pseg([hip + [0, 0.0, 0.02], (hip + knee) / 2 + [0, 0, 0.03], knee + [0, 0.1, -0.02]],
                      [(0.14, 0.22, 0.14), (0.14, 0.25, 0.15), (0.12, 0.2, 0.12)], "armor", fwd=(0, 0.4, 1)),
                 pseg([hip + [0, -0.12, 0.16], knee + [0, 0.16, 0.1]], [(0.08, 0.06, 0.03), (0.06, 0.05, 0.03)], "accent",
                      fwd=(0, 0.6, 1))]
        mid = [axle(knee, 0.16, 0.17),
               # knee guard pointing forward
               pseg([knee + [0, -0.14, 0.06], knee + [0, 0.12, 0.3]], [(0.12, 0.12, 0.08), (0.05, 0.06, 0.05)], "armor2",
                    fwd=(0, 1, 0.3)),
               # short middle segment rising back from the low knee to the higher hock
               pseg([knee + [0, 0.0, -0.04], (knee + hock) / 2, hock + [0, -0.02, 0.06]],
                    [(0.13, 0.18, 0.16), (0.14, 0.2, 0.17), (0.12, 0.16, 0.14)], "armor", fwd=(0, 1, 0.3)),
               pseg([knee + [0, 0.16, -0.08], hock + [0, 0.16, 0.06]], [(0.07, 0.05, 0.03), (0.05, 0.04, 0.03)], "accent",
                    fwd=(0, 1, 0.3)),
               slab([(0.0, 0.0), (0.0, -0.3), (0.16, -0.24), (0.14, -0.04)], (knee + hock) / 2 + [0.14 * s, 0.0, 0.0],
                    (0, 1, 0), (knee - hock) / np.linalg.norm(knee - hock), 0.04, "armor2")]
        shin = [axle(hock, 0.15, 0.16),
                # hock spur pointing back
                pseg([hock + [0, -0.06, -0.06], hock + [0, 0.2, -0.36]], [(0.11, 0.08, 0.14), (0.05, 0.05, 0.08)], "armor2",
                     fwd=(0, -0.3, -1)),
                axle(ankle, 0.09, 0.14),
                # shin raked forward to the ankle, guard on the front
                pseg([hock + [0, -0.02, 0.04], hock + (ankle - hock) * 0.45, ankle + [0, 0.1, -0.04]],
                     [(0.13, 0.18, 0.16), (0.14, 0.24, 0.18), (0.09, 0.12, 0.1)], "armor", fwd=(0, 0.7, 1)),
                slab([(0.0, 0.0), (0.0, -0.5), (0.2, -0.4), (0.18, -0.04)], hock + (ankle - hock) * 0.2 + [0, 0.08, 0.12],
                     (0, 0.7, 0.7), (ankle - hock) * -1 / np.linalg.norm(ankle - hock), 0.04, "accent")]
        foot = [cyl((ankle[0] - 0.1 * s, ankle[1], ankle[2]), (ankle[0] + 0.1 * s, ankle[1], ankle[2]), 0.12, n=8, tag="frame"),
                pstack([(0.0, ankle[0], ankle[2] + 0.2, 0.18, 0.62, 0.36), (0.15, ankle[0], ankle[2] + 0.19, 0.18, 0.6, 0.36),
                        (ankle[1] + 0.04, ankle[0], ankle[2] + 0.04, 0.1, 0.24, 0.16)], "armor2", sh=0.3),
                pstack([(0.0, ankle[0], ankle[2] - 0.32, 0.07, 0.12, 0.3), (0.12, ankle[0], ankle[2] - 0.32, 0.06, 0.1, 0.26)],
                       "frame", sh=0.5)]
        m.add(pre + "thigh", *thigh)
        m.add(pre + "mid", *mid)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *foot)
        chain.append({"name": pre[0], "upper": pre + "thigh", "mid": pre + "mid", "lower": pre + "shin", "foot": pre + "foot",
                      "hip": r3(hip), "knee": r3(knee), "knee2": r3(hock), "ankle": r3(ankle), "pole": [0, 0, 1],
                      "group": 0 if s > 0 else 1})
    finish(m, SCHEME, grime_top=0.9)
    meta = {"slot": "legs", "display_name": "LG-K3 Hopper", "maker": MAKER, "tier": 2,
            "weight": 17.0, "armor": 240,
            "points": {"hip": [0, 2.54, 0]},
            "stats": {"type": "reverse_joint", "core_tilt": 8, "load_limit": 44, "walk_speed": 0.31, "boost_multiplier": 1.0, "jump": 1.55,
                      "turn_rate": 17, "step_height": 1.6, "traction": 0.34, "qb_multiplier": 1.0,
                      "hover_multiplier": 1.1, "collision_width": 1.6, "step_length": 1.6, "step_lift": 0.35,
                      "step_ticks": 6, "pelvis_groups": ["pelvis"], "chains": chain}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def light_core(m, w=1.0, prow_group="core", glass=False, spine=0.9):
    """The light torso: low and long. A spherical cockpit sits at its centre inside an open cage of
    four thin bars; a sloped prow juts forward from the ball and a spine juts back (the booster
    mounts on its tail), so the side view is long and pinched and the cockpit shows through the cage.
    Thin neck and waist, thick shoulder joints on short yokes. The prow can be its own group
    (prow_group) - the Falcon's nose. Returns the core's points."""
    from mg import zloft
    ball_c = np.array([0.0, 0.84, 0.0])
    # waist (thin) and the cockpit ball
    m.add("core", pstack([(-0.08, 0, 0.0, 0.2 * w, 0.2, 0.18), (0.5, 0, 0.0, 0.22 * w, 0.22, 0.2)], "frame"))
    m.add("core", mk.ellipsoid(tuple(ball_c), 0.4 * w, 0.38, 0.42, nu=10, nv=6, tag="armor2"))
    # hub bearings each side of the ball (the cockpit can turn in its cage)
    for s in (1, -1):
        m.add("core", cyl((0.3 * s * w, 0.84, 0.0), (0.42 * s * w, 0.84, 0.0), 0.16, n=8, tag="frame"))
    # cage: two upper and two lower bars from the prow root to the spine, leaving the ball in view
    for s in (1, -1):
        m.add("core", pseg([(0.3 * s * w, 1.16, 0.46), (0.34 * s * w, 1.24, 0.0), (0.3 * s * w, 1.2, -0.42)],
                           [(0.06, 0.09, 0.05), (0.07, 0.1, 0.05), (0.06, 0.09, 0.05)], "armor", fwd=(0, 1, 0)))
        m.add("core", pseg([(0.24 * s * w, 0.52, 0.44), (0.28 * s * w, 0.44, 0.0), (0.24 * s * w, 0.52, -0.4)],
                           [(0.05, 0.05, 0.07), (0.06, 0.05, 0.08), (0.05, 0.05, 0.07)], "armor2", fwd=(0, 1, 0)))
    # prow: juts forward from the ball, ridge on top sloping down to the tip
    prow = zloft([(0.28, 0, 0.86, 0.36 * w, 0.42, 0.34, {"sh": 0.35}), (0.7, 0, 0.84, 0.32 * w, 0.32, 0.28, {"sh": 0.35}),
                  (1.08, 0, 0.8, 0.16 * w, 0.14, 0.12, {"sh": 0.35})], "armor", tip=(0.0, 0.78, 1.34))
    m.add(prow_group, prow)
    for s in (1, -1):
        m.add(prow_group, slab([(0.0, 0.0), (0.62, -0.06), (0.66, 0.0), (0.06, 0.08)], (0.12 * s * w, 1.08, 0.36),
                               (s * 0.25, 0.25, 0.94), (0, 0.97, -0.25), 0.03, "accent"))
    if glass:
        m.add(prow_group, zloft([(0.36, 0, 1.18, 0.16, 0.08, 0.06), (0.86, 0, 1.06, 0.13, 0.07, 0.05)], "glass",
                                tip=(0.0, 0.94, 1.1)))
    else:
        m.add(prow_group, zloft([(0.4, 0, 0.58, 0.24 * w, 0.1, 0.1), (0.9, 0, 0.62, 0.16 * w, 0.08, 0.08)], "armor2"))
    # spine: juts back from the ball and carries the booster on its tail face (point "back")
    k = spine / 0.9
    m.add("core", zloft([(-0.24, 0, 0.88, 0.36 * w, 0.38, 0.34, {"sh": 0.4}), (-0.24 - 0.36 * k, 0, 0.92, 0.32 * w, 0.34, 0.28, {"sh": 0.4}),
                         (-spine + 0.04, 0, 0.94, 0.26 * w, 0.28, 0.22, {"sh": 0.4})], "armor"))
    m.add("core", zloft([(-spine + 0.06, 0, 0.94, 0.2 * w, 0.2, 0.16), (-spine, 0, 0.94, 0.17 * w, 0.17, 0.14)], "armor2"))
    # neck (thin) and the shoulder yokes with thick joints
    m.add("core", cyl((0, 1.12, 0), (0, 1.44, 0), 0.09, n=8, tag="frame"))
    for s in (1, -1):
        m.add("core", pseg([(0.3 * s * w, 1.18, 0.0), (0.66 * s * w, 1.18, 0.0)], [(0.1, 0.12, 0.1), (0.09, 0.1, 0.09)],
                           "armor2", fwd=(0, 1, 0)))
        m.add("core", cyl((0.6 * s * w, 1.18, 0), (1.0 * s * w, 1.18, 0), 0.14, n=10, tag="frame"))
        m.add("core", cyl((0.6 * s * w, 1.18, 0), (0.72 * s * w, 1.18, 0), 0.19, n=10, tag="armor"))
        # weapon pylon on the spine (back_l)
        m.add("core", cbox(0.3 * s * w, 1.26, -0.5 * k, 0.16, 0.16, 0.22, "armor2", c=0.03))
    return {"neck": [0, 1.42, 0.0], "shoulder_l": [0.98 * w, 1.18, 0], "back": [0, 0.94, round(-spine + 0.02, 3)],
            "back_l": [0.3 * w, 1.36, round(-0.5 * k, 3)], "seat": [0, 0.84, 0.0], "eye": [0, 1.72, 0.3]}


def core_swift():
    """CR-K1 Swift: light core."""
    m = mk.Model("cr_swift")
    pts = light_core(m)
    finish(m, SCHEME)
    meta = {"slot": "core", "display_name": "CR-K1 Swift", "maker": MAKER, "tier": 1, "weight": 14.0, "armor": 420,
            "points": pts,
            "stats": {"en_capacity": 1700, "en_recharge": 38, "recharge_delay": 10, "defense": 0.95, "fuel": 700,
                      "stability": 0.9}}
    return m, meta


# ---------------------------------------------------------------------------------- head

def head_lark():
    """HD-K1 Lark: light head - a pointed face under a close-fitting helmet with a short forward peak;
    a visor band wraps the face. Wide lock cone."""
    m = mk.Model("hd_lark")
    m.add("head", cyl((0, -0.08, 0), (0, 0.1, 0), 0.1, n=8, tag="frame"))
    # face: a pentagon pointing forward
    m.add("head", pstack([(0.04, 0, 0.0, 0.13, 0.22, 0.18), (0.24, 0, 0.02, 0.16, 0.32, 0.22),
                          (0.36, 0, -0.02, 0.12, 0.2, 0.2)], "armor2", sh=0.4))
    # visor band hugging the face (its back is under the helmet)
    m.add("head", pstack([(0.19, 0, 0.02, 0.172, 0.345, 0.23), (0.29, 0, 0.01, 0.162, 0.315, 0.225)], "eye", sh=0.4))
    # helmet: covers the top and back of the head down to the neck, its front edge a brim over the visor
    m.add("head", pstack([(0.28, 0, -0.02, 0.19, 0.31, 0.27), (0.42, 0, -0.04, 0.17, 0.27, 0.26),
                          (0.52, 0, -0.07, 0.1, 0.15, 0.17)], "armor", sh=0.42))
    m.add("head", pstack([(0.06, 0, -0.08, 0.13, 0.08, 0.15), (0.3, 0, -0.09, 0.18, 0.1, 0.19)], "armor", sh=0.5, rb=0.7))
    for s in (1, -1):
        # cheek guards from the helmet down the sides of the face
        m.add("head", pseg([(0.16 * s, 0.3, 0.06), (0.15 * s, 0.1, 0.12)], [(0.03, 0.12, 0.08), (0.025, 0.08, 0.05)], "armor",
                           fwd=(0, -0.2, 1)))
    # short peak on the helmet's brow, pointing forward
    m.add("head", pseg([(0, 0.4, 0.18), (0, 0.37, 0.38)], [(0.08, 0.05, 0.03), (0.02, 0.025, 0.015)], "accent", fwd=(0, 1, 0.2)))
    m.add("head", pseg([(0, 0.06, 0.18), (0, -0.02, 0.32)], [(0.05, 0.06, 0.04), (0.02, 0.04, 0.02)], "armor2",
                       fwd=(0, -0.3, 1)))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-K1 Lark", "maker": MAKER, "tier": 1, "weight": 2.2, "armor": 110,
            "points": {"top": [0, 0.52, 0]}, "stats": {"lock_range": 105, "lock_cone": 17, "stability": 0.9}}
    return m, meta


def head_wren():
    """HD-K2 Wren: a thin, flat head for the reverse-joint frames - wide and low like a visored disc,
    with a slit visor all round the front; nothing sticks out."""
    m = mk.Model("hd_wren")
    m.add("head", cyl((0, -0.08, 0), (0, 0.06, 0), 0.1, n=8, tag="frame"))
    m.add("head", pstack([(0.0, 0, 0.02, 0.17, 0.24, 0.2), (0.07, 0, 0.04, 0.25, 0.38, 0.26),
                          (0.15, 0, 0.03, 0.24, 0.35, 0.25), (0.21, 0, 0.0, 0.15, 0.2, 0.17)], "armor", sh=0.5, rb=0.9))
    # slit visor: a band just proud of the shell around the front half
    m.add("head", pstack([(0.085, 0, 0.04, 0.252, 0.385, 0.18), (0.125, 0, 0.035, 0.248, 0.37, 0.18)], "eye", sh=0.5, rb=0.6))
    # flat dorsal plate and two low side sensors
    m.add("head", pstack([(0.2, 0, -0.02, 0.12, 0.16, 0.16), (0.24, 0, -0.03, 0.09, 0.1, 0.13)], "armor2", sh=0.5))
    for s in (1, -1):
        m.add("head", pseg([(0.22 * s, 0.11, -0.06), (0.27 * s, 0.11, -0.12)], [(0.04, 0.05, 0.04), (0.03, 0.04, 0.03)], "armor2",
                           fwd=(0, 1, 0)))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-K2 Wren", "maker": MAKER, "tier": 2, "weight": 2.0, "armor": 105,
            "points": {"top": [0, 0.25, 0]}, "stats": {"lock_range": 110, "lock_cone": 18, "stability": 0.95}}
    return m, meta


# ---------------------------------------------------------------------------------- arms

def light_arm(elbow, grip, w=1.0, blade=True):
    """Left light arm (shoulder at the origin): thick shoulder pod and elbow, thin upper arm, a
    bladed forearm. Returns (upper, fore) shell lists."""
    elbow = np.asarray(elbow, float)
    grip = np.asarray(grip, float)
    upper = [cyl((-0.06, 0, 0), (0.14, 0, 0), 0.15, n=10, tag="frame"),
             # shoulder pod: a rounded pentagon pointing forward, top sloping inward
             pstack([(-0.24, 0.12, 0.0, 0.17 * w, 0.3, 0.26), (0.04, 0.12, 0.0, 0.23 * w, 0.38, 0.3),
                     (0.24, 0.08, -0.02, 0.18 * w, 0.28, 0.25), (0.32, 0.04, -0.03, 0.1 * w, 0.14, 0.14)], "armor", sh=0.4),
             # thin upper arm
             pseg([(elbow[0], -0.16, 0.0), (elbow[0], elbow[1] + 0.1, elbow[2] - 0.02)],
                  [(0.085, 0.1, 0.09), (0.08, 0.09, 0.08)], "armor2", fwd=(0, 0, 1)),
             cyl((elbow[0] - 0.14, elbow[1], elbow[2]), (elbow[0] + 0.14, elbow[1], elbow[2]), 0.13, n=10, tag="frame")]
    fore = [pseg([elbow + [0, 0, -0.1], elbow + (grip - elbow) * 0.5, grip + [0, 0.04, -0.14]],
                 [(0.12, 0.15, 0.1), (0.12, 0.17, 0.1), (0.1, 0.12, 0.09)], "armor", fwd=(1, 0, 0)),
            cbox(grip[0], grip[1] + 0.02, grip[2], 0.18, 0.2, 0.2, "frame", c=0.03)]
    if blade:
        # outer guard: a blade raked forward along the forearm
        fore.append(slab([(-0.1, 0.0), (0.62, 0.0), (0.74, 0.08), (0.0, 0.16)],
                         elbow + [0.17, 0.02, 0.0], (0, 0, 1), (0, 1, 0.0), 0.04, "accent"))
    return upper, fore


def arms_talon():
    """AM-K1 Talon: light arms, fast aiming."""
    m = mk.Model("am_talon")
    elbow, grip = (0.11, -0.96, 0.07), (0.11, -1.05, 0.95)
    upper, fore = light_arm(elbow, grip)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-K1 Talon", "maker": MAKER, "tier": 1, "weight": 9.0, "armor": 240,
            "points": {"elbow": list(elbow), "grip": list(grip)},
            "stats": {"load_limit": 15, "aim_speed": 420, "recoil_control": 0.9}}
    return m, meta


# ---------------------------------------------------------------------------------- booster

def booster_zephyr():
    """BS-K1 Zephyr: light booster - a slim spine with two swept thruster pods, strong quick boost."""
    m = mk.Model("bs_zephyr")
    # spine: pentagon pointing back (away from the body), front face sunk into the core's mount plate
    m.add("booster", pstack([(-0.42, 0, -0.12, 0.2, 0.22, 0.12), (0.0, 0, -0.14, 0.26, 0.3, 0.16),
                             (0.4, 0, -0.12, 0.2, 0.24, 0.12)], "armor2", sh=0.45, rb=0.9))
    noz = []
    for s in (1, -1):
        # swept thruster pod: rounded, pointing up/forward, opening down/back
        pod_top = np.array([0.36 * s, 0.42, -0.24])
        pod_bot = np.array([0.5 * s, -0.32, -0.42])
        m.add("booster", pseg([pod_top, (pod_top + pod_bot) / 2, pod_bot], [(0.1, 0.16, 0.12), (0.13, 0.18, 0.14), (0.12, 0.14, 0.12)],
                              "armor", fwd=(0, 0, 1)))
        m.add("booster", seg([(0.16 * s, 0.05, -0.2), (0.4 * s, 0.05, -0.32)], [(0.07, 0.07), (0.06, 0.06)], "frame", c=0.02))
        m.add("booster", nozzle(pod_bot + [0, 0.06, 0.02], (0, -0.7, -0.7), 0.12, 0.24))
        # wing blade
        m.add("booster", slab([(0.0, 0.0), (0.5, -0.18), (0.62, -0.1), (0.1, 0.12)], pod_top + [0.06 * s, -0.2, -0.04],
                              (s, 0, 0), (0, 1, 0.3), 0.03, "accent"))
        noz.append({"pos": r3(pod_bot + [0, -0.12, -0.16]), "dir": [0, -0.7, -0.7], "size": 0.3})
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-K1 Zephyr", "maker": MAKER, "tier": 1, "weight": 4.5, "armor": 90,
            "stats": {"boost_speed": 1.05, "qb_speed": 2.7, "qb_cost": 330, "qb_cooldown": 9, "hover_thrust": 0.15,
                      "hover_cost": 15, "air_boost_cost": 6, "nozzles": noz}}
    return m, meta


ALL = [head_wren, legs_stride, core_swift, head_lark, arms_talon, booster_zephyr, legs_hopper]
