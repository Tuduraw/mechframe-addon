"""Vesper Aeronautics - ultralight frames (ivory / bare titanium / signal red-orange).

Concept: "everything that does not fly is weight". Vesper builds a frame as a light but properly
triangulated space frame of square titanium tube, and hangs the armour on it as separate thin ivory
plates on stand-off posts, with air gaps between plate and frame and between neighbouring plates -
nothing more than the pilot and the joints need. The boosters are oversized for the frame.
Durability and load are sacrificed for speed: low AP and load limits, the highest boost and
quick-boost of any maker.

Design rules (what makes a Vesper part read as one):
  - a space frame you can see into: chords of square tube with diagonal webs (girder) and X-braces,
    never a single bare rod - it has to look like it carries its own weight;
  - the armour is a set of separate pointed plates, each on its own posts, following the frame
    faces with a visible gap; plate tips point up, forward or aft - a spiky, light silhouette;
  - the only rounded masses are the ivory capsules (cockpit, sensor, engine pods);
  - joints are discs on bare axles; a red-orange accent on each actuator;
  - tall and narrow: long shins, high hips, wide stance, toes like claws;
  - even the lightest tank hull is a sealed, watertight boat shape."""
import math

import numpy as np

from common import NS, MAKERS, export  # noqa: F401
from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3, pstack, pseg, slab, plate, zloft
from mg import tube as _tube, truss as _truss
from meshkit.mech import unit, beam, girder, at, panel, claw


def tube(points, r, tag="metal", n=5, **kw):
    return _tube(points, r, tag=tag, n=n, **kw)


def truss(p0, p1, width, depth=None, n=4, **kw):
    """Lattice girder with square members (4-sided): the see-through look at a third of the triangles."""
    return _truss(p0, p1, width, depth, n=n, **kw)


SCHEME = "va"
MAKER = MAKERS["va"]



def axle(p, r, w, s=1, n=10, tag="frame"):
    """A joint axle across X centred at p (half width w)."""
    p = np.asarray(p, float)
    return cyl((p[0] - w * s, p[1], p[2]), (p[0] + w * s, p[1], p[2]), r, n=n, tag=tag)


def ram(a, b, r=0.045, tag="metal", sleeve="accent"):
    """Hydraulic ram from a to b: a red sleeve over the first 55%, a bare rod to the end, eye
    blocks at both ends. Returns shells."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    m = a + (b - a) * 0.55
    return [cyl(a, m, r * 1.6, n=4, tag=sleeve), cyl(m - (b - a) * 0.04, b, r, n=3, tag=tag),
            cyl(a - (b - a) * 0.03, a + (b - a) * 0.03, r * 2.0, n=3, tag="frame"),
            cyl(b - (b - a) * 0.03, b + (b - a) * 0.03, r * 1.7, n=3, tag="frame")]


def claw_foot(ankle, s=1, k=1.0):
    """Skeletal foot: ankle axle, a long toe blade forward, two splayed side toes and a heel spur -
    a tripod of claws, each a thin chined blade."""
    ankle = np.asarray(ankle, float)
    ax, ay, az = ankle
    out = [axle(ankle, 0.09 * k, 0.1, s, n=6),
           pstack([(0.0, ax, az + 0.02, 0.09 * k, 0.16, 0.12), (ay - 0.04, ax, az, 0.07 * k, 0.1, 0.08)], "metal", sh=0.4)]
    toes = [((0.0, 0.0, 0.72), 0.07, "accent"), ((0.22, 0.0, 0.42), 0.05, "armor2"), ((-0.22, 0.0, 0.42), 0.05, "armor2"),
            ((0.0, 0.0, -0.42), 0.05, "armor2")]
    for (dx, dy, dz), w, tag in toes:
        tip = np.array([ax + dx * s * k, 0.02, az + dz * k])
        root = np.array([ax + dx * 0.25 * s, 0.14, az + dz * 0.15])
        out.append(claw(root, tip, w, 0.06, tag))
    return out




# ---------------------------------------------------------------------------------- legs

def wisp_leg(hip, knee, ankle, s, spring=True, fin=False):
    """One Vesper biped leg: triangular girders for thigh and shin (plates on their flat faces), a
    ram along the front of the thigh, a pointed knee cap, a spring strut (or the Kite's calf fin)
    behind the shin. Returns (thigh shells, shin shells)."""
    hip, knee, ankle = (np.asarray(p, float) for p in (hip, knee, ankle))
    out_ = np.array([float(s), 0.0, 0.0])
    # thigh: girder with its flat face outward
    thigh = [axle(hip, 0.13, 0.15, s, n=6), axle(hip, 0.16, 0.03, s, n=6, tag="accent")]
    w = 0.26
    g, L, (d, f, a) = girder(hip + [0, -0.04, 0], knee + [0, 0.05, 0], out_, w=w, r=0.046, web_r=0.026)
    thigh += g
    front, rear = ("f1", "f2") if a[2] > 0 else ("f2", "f1")
    fwd = a if a[2] > 0 else -a
    thigh += ram(at(L[front], 0.14) + fwd * 0.1, at(L[front], 0.84) + fwd * 0.1)
    base = lambda t: hip + (knee - hip) * t + f * (w * 0.32 + 0.1)
    # outer plates: the upper one with a fin swept up and back past the hip, a smaller one above the knee
    thigh += panel([(-0.62, -0.24), (-0.22, 0.18), (0.28, 0.17), (0.32, -0.16), (-0.1, -0.21)], base(0.25), d, fwd,
                   [at(L[front], 0.25), at(L[rear], 0.25)])
    thigh += panel([(-0.2, 0.17), (0.22, 0.18), (0.36, 0.0), (0.2, -0.17), (-0.2, -0.17)], base(0.66), d, fwd,
                   [at(L[front], 0.66), at(L[rear], 0.66)])
    # shin: girder with its flat face forward
    shin = [axle(knee, 0.12, 0.14, s, n=6), axle(knee, 0.15, 0.03, s, n=6, tag="accent")]
    w2 = 0.22
    # shin: girder with its flat face outward, so the plates run edge-on to the airflow
    g, L2, (d2, f2, a2) = girder(knee + [0, -0.04, 0], ankle + [0, 0.06, 0], out_, w=w2, r=0.042, web_r=0.024)
    shin += g
    fwd2 = a2 if a2[2] > 0 else -a2
    fr2, re2 = ("f1", "f2") if a2[2] > 0 else ("f2", "f1")
    base2 = lambda t: knee + (ankle - knee) * t + f2 * (w2 * 0.32 + 0.09)
    # outer shin plates: leading edge swept forward to a point, trailing edge cut straight
    shin += panel([(-0.3, -0.16), (-0.34, 0.12), (0.0, 0.3), (0.22, 0.18), (0.22, -0.16)], base2(0.3), d2, fwd2,
                  [at(L2[fr2], 0.3), at(L2[re2], 0.3)])
    shin += panel([(-0.17, -0.15), (-0.2, 0.12), (0.06, 0.24), (0.2, 0.12), (0.2, -0.15)], base2(0.72), d2, fwd2,
                  [at(L2[fr2], 0.72), at(L2[re2], 0.72)])
    # knee fin: a blade in the leg's own plane, edge-on to the airflow, tip forward and up
    shin += panel([(-0.12, -0.24), (0.12, -0.24), (0.3, 0.1), (0.06, 0.34), (-0.12, 0.1)], knee + fwd2 * 0.2,
                  -d2, fwd2, [knee])
    if spring:
        sp0 = knee + [0, -0.18, -0.2]
        sp1 = knee + (ankle - knee) * 0.62 + [0, 0, -0.17]
        shin += [cyl(sp0, sp1, 0.06, n=5, tag="accent"), cyl(sp0 + (sp1 - sp0) * 0.2, sp0 + (sp1 - sp0) * 0.8, 0.075, n=5, tag="frame"),
                 cbox(sp0[0], sp0[1] + 0.04, sp0[2] + 0.08, 0.08, 0.1, 0.2, "frame", c=0.02),
                 beam(sp1, at(L2["apex"], 0.62), 0.03)]
    if fin:
        # calf fin: a swept blade behind the shin - the Kite's V-tail in flight
        axis = (ankle - knee) / np.linalg.norm(ankle - knee)
        shin.append(slab([(0.0, 0.0), (0.0, 0.62), (0.5, 0.95), (0.62, 0.86), (0.55, 0.12)],
                         knee + (ankle - knee) * 0.22 + [0, 0, -0.06], (0, 0, -1), axis, 0.035, "armor"))
        shin.append(slab([(0.42, 0.7), (0.5, 0.95), (0.62, 0.86), (0.56, 0.62)],
                         knee + (ankle - knee) * 0.22 + [0.02 * s, 0, -0.06], (0, 0, -1), axis, 0.045, "accent"))
    return thigh, shin


def wisp_pelvis(m, H, half=0.52):
    m.add("pelvis", cyl((-half, H, 0), (half, H, 0), 0.08, n=8, tag="frame"))
    m.add("pelvis", pstack([(H - 0.16, 0, 0.02, 0.16, 0.2, 0.16), (H + 0.12, 0, 0.0, 0.18, 0.22, 0.18),
                            (H + 0.2, 0, -0.02, 0.12, 0.14, 0.12)], "armor2", sh=0.45))
    for s in (1, -1):
        m.add("pelvis", beam((0.08 * s, H + 0.08, 0.1), (0.4 * s, H, 0.0), 0.04),
              beam((0.08 * s, H + 0.08, -0.1), (0.4 * s, H, 0.0), 0.04))
    m.add("pelvis", slab([(0.0, 0.0), (0.12, -0.3), (0.0, -0.36), (-0.12, -0.3)], (0, H + 0.08, 0.22), (1, 0, 0), (0, 1, 0.15),
                         0.025, "accent"))


def legs_wisp():
    """LG-V1 Wisp: ultralight biped. Girder thighs and shins with a ram in front and a spring strut
    behind, separate pointed plates on posts, claw feet. High hips (3.1 m), wide stance."""
    m = mk.Model("lg_wisp")
    H = 3.1
    wisp_pelvis(m, H)
    chains = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        hip = np.array([0.48 * s, H, 0.0])
        knee = np.array([0.5 * s, 1.72, 0.38])
        ankle = np.array([0.52 * s, 0.36, -0.06])
        thigh, shin = wisp_leg(hip, knee, ankle, s)
        m.add(pre + "thigh", *thigh)
        m.add(pre + "shin", *shin)
        m.add(pre + "foot", *claw_foot(ankle, s))
        chains.append({"name": pre[0], "upper": pre + "thigh", "lower": pre + "shin", "foot": pre + "foot",
                       "hip": r3(hip), "knee": r3(knee), "ankle": r3(ankle), "pole": [0, 0, 1], "group": 0 if s > 0 else 1})
    finish(m, SCHEME, size=512, grime_top=0.6)
    meta = {"slot": "legs", "display_name": "LG-V1 Wisp", "maker": MAKER, "tier": 2, "weight": 9.0, "armor": 150,
            "points": {"hip": [0, H + 0.18, 0]},
            "stats": {"type": "biped", "load_limit": 32, "walk_speed": 0.4, "boost_multiplier": 1.15, "jump": 1.1,
                      "turn_rate": 20, "step_height": 1.2, "traction": 0.42, "qb_multiplier": 1.15,
                      "hover_multiplier": 1.1, "collision_width": 1.5, "step_length": 1.7, "step_lift": 0.55,
                      "step_ticks": 5, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def legs_gnat():
    """LG-V3 Gnat: ultralight quad - a water strider. A small hub; four long girder legs rise steeply
    to knees far above the body and reach down to needle feet well outside it. A spiked plate on top
    of each femur, a long plate on the outside of each tibia. Fast, nimble, fragile."""
    m = mk.Model("lg_gnat")
    hub_y = 1.9
    m.add("pelvis", pstack([(hub_y - 0.3, 0, 0, 0.32, 0.42, 0.36), (hub_y - 0.05, 0, 0, 0.4, 0.5, 0.42),
                            (hub_y + 0.18, 0, 0, 0.3, 0.38, 0.32)], "armor2", sh=0.5, rb=0.9))
    m.add("pelvis", cyl((0, hub_y + 0.12, 0), (0, hub_y + 0.32, 0), 0.26, n=10, tag="frame"))
    m.add("pelvis", pseg([(0, hub_y + 0.02, 0.4), (0, hub_y - 0.1, 0.62)], [(0.12, 0.08, 0.06), (0.04, 0.04, 0.03)], "accent",
                         fwd=(0, 1, 0)))
    chains = []
    corners = [("fl", 1, 1, 0), ("fr", -1, 1, 1), ("rl", 1, -1, 1), ("rr", -1, -1, 0)]
    for name, sx, sz, group in corners:
        d = np.array([sx, 0.0, sz * 0.9])
        d /= np.linalg.norm(d)
        cp = np.array([0.3 * d[0], hub_y - 0.08, 0.3 * d[2]])
        hip = cp + d * 0.26 + [0, 0.02, 0]
        knee = hip + d * 1.15 + [0, 1.2, 0]
        ankle = hip + d * 2.55 + [0, -hub_y + 0.32, 0]
        side = np.cross([0, 1, 0], d)
        m.add(name + "_coxa", cyl(cp + [0, -0.14, 0], cp + [0, 0.14, 0], 0.13, n=8, tag="frame"),
              beam(cp, hip + d * 0.06, 0.1, tag="armor2"))
        femur = [cyl(hip - side * 0.13, hip + side * 0.13, 0.1, n=6, tag="frame")]
        g, L, (dd, ff, aa) = girder(hip + [0, 0.02, 0], knee, (0, 1, 0), w=0.2, r=0.04, web_r=0.022, webs=("f1",))
        femur += g
        femur += panel([(-0.42, -0.14), (-0.42, 0.14), (0.3, 0.15), (0.66, 0.0), (0.3, -0.15)], at((hip, knee), 0.55) + ff * 0.16,
                       dd, aa, [at(L["f1"], 0.45), at(L["f2"], 0.65)])
        tibia = [cyl(knee - side * 0.11, knee + side * 0.11, 0.09, n=6, tag="frame"),
                 cyl(knee - side * 0.14, knee - side * 0.12, 0.11, n=6, tag="accent")]
        g, L2, (d2, f2, a2) = girder(knee, ankle + [0, 0.1, 0], d, w=0.16, r=0.036, web_r=0.02, webs=("f1",))
        tibia += g
        tibia += panel([(-0.36, -0.12), (-0.4, 0.12), (0.3, 0.12), (0.46, 0.0), (0.3, -0.12)], at((knee, ankle), 0.36) + f2 * 0.14,
                       d2, a2, [at(L2["f1"], 0.36), at(L2["f2"], 0.36)])
        # needle foot
        tibia.append(pseg([ankle + [0, 0.16, 0], ankle + [0, -0.3, 0]], [(0.05, 0.05, 0.05), (0.01, 0.01, 0.01)], "accent",
                          fwd=tuple(d)))
        m.add(name + "_femur", *femur)
        m.add(name + "_tibia", *tibia)
        chains.append({"name": name, "coxa": name + "_coxa", "coxa_pivot": r3(cp), "upper": name + "_femur",
                       "lower": name + "_tibia", "hip": r3(hip), "knee": r3(knee), "ankle": r3([ankle[0], 0.02, ankle[2]]),
                       "pole": [0, 1, 0], "group": group})
    finish(m, SCHEME, size=512, grime_top=0.5)
    meta = {"slot": "legs", "display_name": "LG-V3 Gnat", "maker": MAKER, "tier": 2, "weight": 13.0, "armor": 190,
            "points": {"hip": [0, hub_y + 0.3, 0]},
            "stats": {"type": "quad", "load_limit": 40, "walk_speed": 0.38, "boost_multiplier": 1.12, "jump": 0.95,
                      "turn_rate": 19, "step_height": 1.5, "traction": 0.4, "qb_multiplier": 1.1,
                      "hover_multiplier": 1.4, "collision_width": 2.0, "step_length": 1.6, "step_lift": 0.7,
                      "step_ticks": 5, "pelvis_groups": ["pelvis"], "chains": chains}}
    return m, meta


def skiff_hull():
    """Sealed, watertight hull between the tracks: flat deck, vertical sides, chines and a shallow V
    bottom, a long pointed bow. One closed shell."""
    def ring(z, hw, yt, yc, hk, yb):
        return np.array([(hw, yt, z), (-hw, yt, z), (-hw, yc, z), (-hk, yb, z), (hk, yb, z), (hw, yc, z)], float)
    rings = [ring(-2.02, 0.68, 1.08, 0.74, 0.3, 0.56), ring(-1.76, 0.8, 1.14, 0.7, 0.36, 0.48), ring(1.62, 0.8, 1.14, 0.7, 0.36, 0.48),
             ring(2.18, 0.56, 1.1, 0.78, 0.2, 0.64), np.array([(0.0, 0.98, 2.66)], float)]
    return mk.loft(rings, "armor2")


def legs_skiff():
    """LG-V4 Skiff: ultralight tank legs - a sealed, watertight boat hull between two narrow tracks,
    the road wheels and suspension arms in plain view, no fenders; separate pointed plates on posts
    along the deck edges and a chevron on the bow. The fastest tank legs."""
    m = mk.Model("lg_skiff")
    wheels = []
    for s, pre in ((1, "l_"), (-1, "r_")):
        x0, x1 = sorted((0.86 * s, 1.18 * s))
        prof = [(-1.7, 0.0), (1.8, 0.0), (2.25, 0.42), (2.0, 0.78), (-1.95, 0.78), (-2.2, 0.42)]
        m.add(pre + "track", mk.extrude_zy(prof, x0, x1, tag="frame"))
        for i, z in enumerate((-1.25, -0.4, 0.45, 1.3)):
            g_ = f"{pre}w{i}"
            m.add(g_, mk.cylinder((1.15 * s, 0.34, z), (1.3 * s, 0.34, z), 0.26, n=8, tag="armor2"))
            m.add(g_, cyl((1.28 * s, 0.34, z), (1.33 * s, 0.34, z), 0.1, n=4, tag="accent"))
            wheels.append({"group": g_, "pivot": r3((1.25 * s, 0.34, z)), "radius": 0.26})
            # bare suspension arm from the hull side to the wheel hub
            m.add(pre + "track", beam((0.78 * s, 0.86, z + 0.12), (1.16 * s, 0.36, z - 0.06), 0.04))
        for name, (y, z) in (("idler", (0.42, 2.0)), ("sprocket", (0.42, -1.92))):
            g_ = f"{pre}{name}"
            m.add(g_, mk.cylinder((1.15 * s, y, z), (1.32 * s, y, z), 0.3, n=8, tag="armor2"))
            wheels.append({"group": g_, "pivot": r3((1.25 * s, y, z)), "radius": 0.3})
        m.add(pre + "track", cbox(1.02 * s, 0.82, 2.1, 0.32, 0.12, 0.1, "accent", c=0.02))
        m.add(pre + "track", beam((0.78 * s, 0.9, 1.95), (0.9 * s, 0.82, 2.06), 0.04))
    m.add("hull", skiff_hull())
    # deck-edge plates (tilted up and out) and a bow chevron, each on its own posts
    left = []
    o = unit((0.5, 0.87, 0.0))
    v = np.cross((0, 0, 1), o)
    left += panel([(-0.62, -0.2), (0.6, -0.2), (0.9, 0.04), (0.5, 0.2), (-0.62, 0.18)], (0.7, 1.24, 0.95), (0, 0, 1), v,
                  [(0.62, 1.12, 0.5), (0.62, 1.12, 1.3)])
    left += panel([(-0.86, -0.2), (0.62, -0.2), (0.62, 0.18), (-0.5, 0.2), (-1.05, 0.06)], (0.7, 1.24, -0.88), (0, 0, 1), v,
                  [(0.62, 1.12, -0.4), (0.62, 1.12, -1.4)])
    left += panel([(-0.3, -0.1), (0.3, -0.1), (0.36, 0.2), (-0.1, 0.24)], (0.26, 1.2, 2.05), unit((0, 0.18, 1.0)),
                  unit((1.0, 0.0, 0.0)), [(0.22, 1.1, 2.0)])
    m.add("hull", *left, *mirror(left))
    m.add("hull", cbox(0, 1.16, -1.5, 0.5, 0.06, 0.44, "frame", c=0.02))
    m.add("ring", cyl((0, 1.1, 0), (0, 1.42, 0), 0.4, n=10, tag="frame"))
    finish(m, SCHEME, size=512, grime_top=0.9)
    meta = {"slot": "legs", "display_name": "LG-V4 Skiff", "maker": MAKER, "tier": 2, "weight": 15.0, "armor": 260,
            "points": {"hip": [0, 1.4, 0]},
            "stats": {"type": "tank", "load_limit": 52, "walk_speed": 0.62, "boost_multiplier": 1.3, "jump": 0.0,
                      "turn_rate": 15, "step_height": 1.0, "traction": 0.34, "qb_multiplier": 1.0,
                      "hover_multiplier": 0.9, "collision_width": 2.6, "pelvis_groups": ["ring"], "wheels": wheels}}
    return m, meta


# ---------------------------------------------------------------------------------- core

def cone_facet(A1, A2, N, t0=0.06, t1=0.84, shrink=0.84, off=0.08, axis_y=0.8):
    """One facet of a nose cone: the triangle A1-A2-N between two stringers. A diagonal brace
    across it, and a plate (narrower than the facet - gaps along the stringers - with a pointed
    front) held off its outer side on two posts from the brace. Returns shells."""
    A1, A2, N = (np.asarray(p, float) for p in (A1, A2, N))
    n = unit(np.cross(A2 - A1, N - A1))
    mid = (A1 + A2 + N) / 3
    if np.dot(n, mid - np.array([0.0, axis_y, mid[2]])) < 0:
        n = -n
    S1 = lambda t: A1 + (N - A1) * t
    S2 = lambda t: A2 + (N - A2) * t
    c = (S1(t0) + S2(t0) + S1(t1) + S2(t1)) / 4
    u = unit(N - (A1 + A2) / 2)
    v = np.cross(n, u)
    uv = lambda p: (float(np.dot(p - c, u)), float(np.dot(p - c, v)) * shrink)
    tip = (S1(t1) + S2(t1)) / 2 + u * 0.08
    outline = [uv(S1(t0)), uv(S2(t0)), uv(S1(t1)), uv(S2(t1)), (float(np.dot(tip - c, u)), 0.0)]
    br0, br1 = S1(t0 + 0.04), S2(t1 - 0.12)
    out = [beam(br0, br1, 0.022, n=3)]
    out += panel(outline, c + n * off, u, v, [br0 + (br1 - br0) * 0.4, br0 + (br1 - br0) * 0.62])
    return out


def roll_cage(m, w=1.0, group="core", nose=True):
    """Vesper's core: a braced space frame round the capsule (two hoops, three longerons a side,
    X-braced flanks, a braced shoulder outrigger) and the armour as separate plates on posts - a
    lower flank plate, a front and a rear upper flank plate split round the shoulder, a chest plate a
    side forming an open V in front of the capsule, and a brow hood over the canopy."""
    Zf, Zr = 0.42, -0.36

    def P(x, y, z):
        return np.array([x * w, y, z], float)

    B = lambda z: P(0.36, 0.18, z)
    M = lambda z: P(0.44, 0.9, z)
    T = lambda z: P(0.3, 1.3, z)
    shells = []
    for z in (Zf, Zr):
        hoop = [B(z), M(z), T(z), T(z) * [-1, 1, 1], M(z) * [-1, 1, 1], B(z) * [-1, 1, 1]]
        shells += tube(hoop, 0.045, n=4, joint=0)
    left = [beam(f(Zf), f(Zr), 0.042) for f in (B, M, T)]
    left += [beam(B(Zf), M(Zr), 0.028), beam(M(Zf), B(Zr), 0.028), beam(M(Zf), T(Zr), 0.028)]
    # shoulder outrigger, braced into a triangle
    left += [beam(T(0.0), P(0.66, 1.2, 0), 0.05), beam(M(0.0), P(0.66, 1.2, 0), 0.036)]
    left += [cyl(P(0.6, 1.2, 0), P(0.98, 1.2, 0), 0.11, n=6, tag="frame"), cyl(P(0.82, 1.2, 0), P(0.86, 1.2, 0), 0.15, n=6, tag="accent")]
    left += tube([B(Zf), P(0.1, -0.04, 0.05), B(Zr)], 0.04, n=4, joint=0)
    # seat struts: the capsule rides on the lower longerons
    left += [beam(B(0.24), P(0.1, 0.52, 0.12), 0.03), beam(B(-0.12), P(0.1, 0.52, 0.0), 0.03)]
    # weapon pylon post
    left += [beam(T(Zr), P(0.32, 1.36, -0.46), 0.04), cbox(0.32 * w, 1.38, -0.46, 0.12, 0.08, 0.14, "frame", c=0.02)]
    # --- plates
    Z = np.array([0.0, 0.0, 1.0])
    v1 = unit(M(0) - B(0))
    o1 = np.array([v1[1], -v1[0], 0.0])
    left += panel([(-0.5, -0.36), (0.3, -0.38), (0.36, -0.1), (0.28, 0.3), (-0.46, 0.3)], B(0.05) + v1 * 0.3 + o1 * 0.12, Z, v1,
                  [B(-0.25), (B(0.03) + M(0.03)) / 2])
    v2 = unit(T(0) - M(0))
    o2 = np.array([v2[1], -v2[0], 0.0])
    left += panel([(-0.28, -0.26), (-0.04, -0.28), (-0.02, 0.3), (-0.28, 0.3)], M(Zf) + v2 * 0.2 + o2 * 0.12, Z, v2,
                  [M(0.25), T(0.3)])
    left += panel([(0.22, -0.26), (0.22, 0.14), (-0.2, 0.16), (-0.52, 0.06), (-0.26, -0.28)], M(Zr) + v2 * 0.2 + o2 * 0.12, Z, v2,
                  [M(-0.2), M(-0.32)])
    if nose:
        # nose cone: stringers from the front hoop converge on a nose point ahead of the capsule; each
        # facet carries a diagonal brace and a plate on posts, so the plates form a pointed cone
        # (slanted to the airflow) with gaps along the stringers
        N = P(0.0, 0.7, 1.0)
        for A in (B, M, T):
            left.append(beam(A(Zf), N, 0.032))
        left += cone_facet(B(Zf), M(Zf), N)
        left += cone_facet(M(Zf), T(Zf), N)
    else:
        # (Kite) chest plates: flat on the back of the glider in flight
        o3 = unit((0.34, 0.0, 0.94))
        left += panel([(-0.52, -0.14), (-0.22, 0.1), (0.34, 0.08), (0.4, -0.18), (0.02, -0.32)], P(0.41, 0.62, Zf) + o3 * 0.12,
                      (0, 1, 0), unit((0.94, 0.0, -0.34)), [P(0.384, 0.4, Zf), M(Zf)])
    shells += left + mirror(left)
    shells.append(beam(T(Zf), T(Zr) * [-1, 1, 1], 0.028))
    shells.append(beam(M(Zr), M(Zr) * [-1, 1, 1], 0.045))     # rear cross bar: capsule and spine hang on it
    if nose:
        shells += cone_facet(T(Zf), T(Zf) * [-1, 1, 1], N, t1=0.5)
    else:
        shells += panel([(-0.26, -0.18), (0.26, -0.18), (0.2, 0.12), (0.0, 0.32), (-0.2, 0.12)], P(0, 1.42, 0.56), (1, 0, 0),
                        unit((0, -0.25, 1.0)), [P(0.18, 1.3, Zf), P(-0.18, 1.3, Zf)])
    m.add(group, *shells)
    # waist hub and the lattice spine back to the booster mount
    m.add(group, pstack([(-0.1, 0, 0.02, 0.16, 0.18, 0.16), (0.14, 0, 0.02, 0.18, 0.2, 0.18)], "armor2", sh=0.45))
    for sx in (1, -1):
        m.add(group, beam((0.15 * sx * w, 0.96, -0.36), (0.15 * sx * w, 0.96, -0.7), 0.035),
              beam((0.15 * sx * w, 0.82, -0.36), (0.15 * sx * w, 0.82, -0.7), 0.035),
              beam((0.15 * sx * w, 0.96, -0.36), (0.15 * sx * w, 0.82, -0.7), 0.024, n=3))
    m.add(group, cbox(0, 0.86, -0.72, 0.36, 0.36, 0.06, "frame", c=0.02))
    m.add(group, cyl((0, 1.28, 0.0), (0, 1.48, 0.0), 0.07, n=8, tag="frame"))


def capsule(m, group="core", y=0.82, z=0.06, w=1.0, glass=True):
    """The ivory cockpit capsule: an egg pointing forward, a long canopy on its upper front."""
    m.add(group, mk.ellipsoid((0, y, z), 0.3 * w, 0.36, 0.5, nu=8, nv=5, tag="armor"))
    if glass:
        m.add(group, zloft([(z + 0.02, 0, y + 0.22, 0.13, 0.1, 0.04), (z + 0.3, 0, y + 0.16, 0.12, 0.08, 0.04),
                            (z + 0.45, 0, y + 0.02, 0.08, 0.05, 0.03)], "glass", tip=(0.0, y - 0.04, z + 0.52)))
    m.add(group, pseg([(0, y - 0.3, z + 0.1), (0, y - 0.36, z + 0.44)], [(0.1, 0.06, 0.04), (0.03, 0.03, 0.02)], "accent",
                      fwd=(0, -1, 0)))
    m.add(group, cbox(0, y + 0.06, z - 0.5, 0.22, 0.26, 0.08, "frame", c=0.02))


def core_filament():
    """CR-V1 Filament: ultralight core - the cockpit capsule in a braced space frame, armoured only
    by a few separate stand-off plates. Fast EN recharge, small EN store."""
    m = mk.Model("cr_filament")
    capsule(m)
    roll_cage(m)
    finish(m, SCHEME, size=512)
    meta = {"slot": "core", "display_name": "CR-V1 Filament", "maker": MAKER, "tier": 2, "weight": 8.0, "armor": 260,
            "points": {"neck": [0, 1.46, 0.0], "shoulder_l": [0.96, 1.2, 0], "back": [0, 0.86, -0.74],
                       "back_l": [0.32, 1.42, -0.46], "seat": [0, 0.82, 0.06], "eye": [0, 1.72, 0.3]},
            "stats": {"en_capacity": 1300, "en_recharge": 50, "recharge_delay": 8, "defense": 0.8, "fuel": 650,
                      "stability": 0.85}}
    return m, meta


# ---------------------------------------------------------------------------------- head

def head_pin():
    """HD-V1 Pin: a sensor stalk - a small ivory pod with one long optic barrel, two whip antennas
    swept back. Long lock range, light."""
    m = mk.Model("hd_pin")
    m.add("head", cyl((0, -0.08, 0), (0, 0.14, 0), 0.06, n=8, tag="frame"))
    m.add("head", mk.ellipsoid((0, 0.24, -0.02), 0.13, 0.12, 0.17, nu=8, nv=5, tag="armor"))
    m.add("head", cyl((0, 0.24, 0.1), (0, 0.24, 0.36), 0.06, n=8, tag="frame"))
    m.add("head", cyl((0, 0.24, 0.34), (0, 0.24, 0.4), 0.075, n=8, tag="eye"))
    m.add("head", cyl((0, 0.24, 0.2), (0, 0.24, 0.24), 0.08, n=8, tag="accent"))
    for s in (1, -1):
        m.add("head", cyl((0.08 * s, 0.32, -0.08), (0.2 * s, 0.82, -0.38), 0.012, n=4, tag="metal"))
        m.add("head", cyl((0.08 * s, 0.3, -0.08), (0.08 * s, 0.36, -0.08), 0.035, n=4, tag="frame"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "head", "display_name": "HD-V1 Pin", "maker": MAKER, "tier": 2, "weight": 1.0, "armor": 70,
            "points": {"top": [0, 0.36, 0]}, "stats": {"lock_range": 130, "lock_cone": 14, "stability": 0.85}}
    return m, meta


# ---------------------------------------------------------------------------------- arms


# ---------------------------------------------------------------------------------- arms

def reed_arm(elbow, grip):
    """Left Vesper arm (shoulder at the origin): girder upper arm with a ram along its front and a
    spiked shoulder plate on posts, girder forearm with a short outer plate, a clamp at the grip."""
    elbow = np.asarray(elbow, float)
    grip = np.asarray(grip, float)
    upper = [cyl((-0.04, 0, 0), (0.16, 0, 0), 0.12, n=8, tag="frame"),
             cyl((0.0, 0, 0), (0.04, 0, 0), 0.15, n=8, tag="accent")]
    w = 0.2
    g, L, (d, f, a) = girder(np.array([0.08, -0.06, 0.0]), elbow + [0, 0.06, 0], (1, 0, 0), w=w, r=0.04, web_r=0.022, webs=("f2",))
    upper += g
    upper += ram(at(L["f1"], 0.16) + a * 0.065, at(L["f1"], 0.84) + a * 0.065, r=0.025)
    p0 = np.array([0.08, -0.06, 0.0])
    base = lambda t: p0 + (elbow - p0) * t + f * (w * 0.32 + 0.1)
    upper += panel([(-0.52, -0.06), (-0.2, 0.17), (0.3, 0.15), (0.36, -0.1), (-0.1, -0.17)], base(0.2), d, a,
                   [at(L["f1"], 0.2), at(L["f2"], 0.2), at(L["f1"], 0.45)])
    upper.append(axle(elbow, 0.1, 0.12, n=6))
    w2 = 0.16
    q0, q1 = elbow + [0, 0, 0.04], grip + [0, 0.04, -0.12]
    g, L2, (d2, f2, a2) = girder(q0, q1, (1, 0, 0), w=w2, r=0.034, web_r=0.02, webs=("f1",))
    fore = list(g)
    fore += panel([(-0.3, -0.1), (-0.34, 0.08), (0.12, 0.11), (0.24, 0.02), (0.14, -0.1)], q0 + (q1 - q0) * 0.42 + f2 * (w2 * 0.32 + 0.09),
                  d2, a2, [at(L2["f1"], 0.42), at(L2["f2"], 0.42)])
    fore.append(cbox(grip[0], grip[1] + 0.02, grip[2] - 0.04, 0.14, 0.16, 0.18, "frame", c=0.02))
    return upper, fore


def arms_reed():
    """AM-V1 Reed: girder arms with stand-off plates - very fast aiming, small load."""
    m = mk.Model("am_reed")
    elbow, grip = (0.1, -0.95, 0.06), (0.1, -1.02, 0.92)
    upper, fore = reed_arm(elbow, grip)
    m.add("l_upper", *upper)
    m.add("l_fore", *fore)
    m.add("r_upper", *mirror(upper))
    m.add("r_fore", *mirror(fore))
    finish(m, SCHEME, size=256)
    meta = {"slot": "arms", "display_name": "AM-V1 Reed", "maker": MAKER, "tier": 2, "weight": 5.0, "armor": 130,
            "points": {"elbow": list(elbow), "grip": list(grip)},
            "stats": {"load_limit": 11, "aim_speed": 500, "recoil_control": 0.75}}
    return m, meta


# ---------------------------------------------------------------------------------- booster

def engine_pod(top, bot, r):
    """A big ivory engine pod from top (intake) to bot (exhaust): intake lip, body, nozzle."""
    top = np.asarray(top, float)
    bot = np.asarray(bot, float)
    d = (bot - top) / np.linalg.norm(bot - top)
    shells = [mk.lathe(top, d, [(0.0, r * 0.7), (0.15, r), (np.linalg.norm(bot - top) * 0.85, r * 0.92),
                                (np.linalg.norm(bot - top), r * 0.75)], n=6, tag="armor"),
              cyl(top - d * 0.02, top + d * 0.06, r * 0.82, n=8, tag="accent"),
              nozzle(bot - d * 0.05, d, r * 0.72, r * 1.3, n=6)]
    return shells


def booster_flare():
    """BS-V1 Flare: oversized booster - two engine pods nearly as long as the torso on lattice pylons.
    The strongest boost and quick boost for its weight; burns EN quickly."""
    m = mk.Model("bs_flare")
    m.add("booster", cbox(0, 0.0, -0.04, 0.4, 0.4, 0.1, "frame", c=0.02))
    noz = []
    for s in (1, -1):
        top = np.array([0.56 * s, 0.95, -0.3])
        bot = np.array([0.64 * s, -0.75, -0.56])
        m.add("booster", *engine_pod(top, bot, 0.21))
        mid = (top + bot) / 2
        m.add("booster", beam((0.12 * s, 0.06, -0.08), mid + [-0.16 * s, 0.06, 0.02], 0.035),
              beam((0.12 * s, -0.06, -0.08), mid + [-0.16 * s, -0.06, 0.02], 0.035),
              beam((0.12 * s, 0.06, -0.08), mid + [-0.16 * s, -0.06, 0.02], 0.022, n=3))
        m.add("booster", *tube([(0.12 * s, 0.25, -0.08), top + [-0.12 * s, -0.15, 0.06]], 0.03, tag="metal"))
        d = (bot - top) / np.linalg.norm(bot - top)
        noz.append({"pos": r3(bot + d * 0.2), "dir": r3(d), "size": 0.42})
        # fin
        m.add("booster", slab([(0.0, 0.0), (0.42, -0.1), (0.46, 0.0), (0.06, 0.14)], top + [0.18 * s, -0.25, -0.05],
                              (s, 0, 0), (0, 1, 0.2), 0.03, "accent"))
    finish(m, SCHEME, size=256)
    meta = {"slot": "booster", "display_name": "BS-V1 Flare", "maker": MAKER, "tier": 2, "weight": 6.0, "armor": 80,
            "stats": {"boost_speed": 1.35, "qb_speed": 3.2, "qb_cost": 310, "qb_cooldown": 8, "hover_thrust": 0.2,
                      "hover_cost": 19, "air_boost_cost": 8, "nozzles": noz}}
    return m, meta


ALL = [legs_wisp, legs_gnat, legs_skiff, core_filament, head_pin, arms_reed, booster_flare]
