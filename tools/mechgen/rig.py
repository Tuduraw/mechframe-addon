"""Jointed jet poses for transform-set parts, and a Python mirror of the mod's transformation blend.

A transform-set part's groups form a tree hanging from the core. Each child is attached to its parent
at a JOINT (a point in the child's own model space). In robot form the relative placement is whatever
the assembly gives; in jet form it is the robot one turned about the joint by a chosen rotation (and,
for the rare sliding mount, the joint moved by `slide` along the parent). The jet world placement of
every group is therefore derived, never typed in, so the joints are attached in both forms - and the
mod's blend (MechPoser.Blender) keeps them attached in between.

Keys are "mount:group" (mount = head / core / arms / legs / booster)."""
import math

import numpy as np

from assemble import T, Rx, Ry, Rz, robot_placements, _matrix_for


def R3(rx=0.0, ry=0.0, rz=0.0):
    """Ry·Rx·Rz (degrees) as a 3x3 - the mod's Euler convention."""
    return (Ry(ry) @ Rx(rx) @ Rz(rz))[:3, :3]


def axis_angle(axis, deg):
    a = np.asarray(axis, float)
    a = a / np.linalg.norm(a)
    t = math.radians(deg)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K


def align(frm, to):
    """Smallest rotation carrying direction frm onto direction to."""
    a = np.asarray(frm, float); a = a / np.linalg.norm(a)
    b = np.asarray(to, float); b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if np.linalg.norm(v) < 1e-9:
        if c > 0:
            return np.eye(3)
        perp = np.cross(a, [1, 0, 0])
        if np.linalg.norm(perp) < 1e-6:
            perp = np.cross(a, [0, 1, 0])
        return axis_angle(perp, 180.0)
    return axis_angle(v, math.degrees(math.atan2(np.linalg.norm(v), c)))


def M4(R=None, t=(0, 0, 0)):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    M[:3, 3] = t
    return M


def quat(R):
    """3x3 rotation -> (x, y, z, w)."""
    m = R
    tr = m[0, 0] + m[1, 1] + m[2, 2]
    if tr > 0:
        s = math.sqrt(tr + 1.0) * 2
        w = 0.25 * s; x = (m[2, 1] - m[1, 2]) / s; y = (m[0, 2] - m[2, 0]) / s; z = (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w = (m[2, 1] - m[1, 2]) / s; x = 0.25 * s; y = (m[0, 1] + m[1, 0]) / s; z = (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = math.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w = (m[0, 2] - m[2, 0]) / s; x = (m[0, 1] + m[1, 0]) / s; y = 0.25 * s; z = (m[1, 2] + m[2, 1]) / s
    else:
        s = math.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w = (m[1, 0] - m[0, 1]) / s; x = (m[0, 2] + m[2, 0]) / s; y = (m[1, 2] + m[2, 1]) / s; z = 0.25 * s
    q = np.array([x, y, z, w])
    return q / np.linalg.norm(q)


def qmat(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def slerp(q0, q1, t):
    q0 = np.asarray(q0, float); q1 = np.asarray(q1, float)
    d = float(np.dot(q0, q1))
    if d < 0:
        q1 = -q1; d = -d
    if d > 0.9995:
        q = q0 + (q1 - q0) * t
        return q / np.linalg.norm(q)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * q0 + math.sin(t * th) * q1) / math.sin(th)


class Rig:
    """robot: {key: 4x4 robot rest placement in root space}."""

    def __init__(self, robot):
        self.robot = robot
        self.world = {}
        self.meta = {}

    def root(self, key, R, pos, delay=0.0):
        self.world[key] = M4(R, pos)
        self.meta[key] = {"delay": delay}
        return self

    def joint(self, key, parent, joint, R=None, slide=(0, 0, 0), delay=0.0):
        """Child `key` hangs from `parent` at `joint` (child model space). R: the joint's rotation,
        expressed in the parent's frame (identity = keeps its robot relation)."""
        R = np.eye(3) if R is None else np.asarray(R, float)
        rel0 = np.linalg.inv(self.robot[parent]) @ self.robot[key]
        j = np.asarray(joint, float)
        p = (rel0 @ np.append(j, 1.0))[:3] + np.asarray(slide, float)
        rel = M4(R @ rel0[:3, :3], p) @ M4(None, -j)
        self.world[key] = self.world[parent] @ rel
        self.meta[key] = {"delay": delay, "parent": parent, "joint": j}
        return self

    def point(self, key, local):
        return (self.world[key] @ np.append(np.asarray(local, float), 1.0))[:3]

    def axis(self, key, local_dir):
        return self.world[key][:3, :3] @ np.asarray(local_dir, float)

    def pose_json(self, key):
        M = self.world[key]
        m = self.meta[key]
        out = {"pos": [round(float(v), 5) for v in M[:3, 3]], "quat": [round(float(v), 6) for v in quat(M[:3, :3])],
               "delay": m["delay"]}
        if "parent" in m:
            out["parent"] = m["parent"]
            out["joint"] = [round(float(v), 5) for v in m["joint"]]
        return out


# ------------------------------------------------------------------ robot rest placements per key

def robot_keys(parts):
    """{"mount:group": 4x4} at rest, as MechPoser places them (weapons by mount: "l_arm:*")."""
    rob = robot_placements(parts)
    out = {}
    for mount, (model, meta) in parts.items():
        for g in model.order:
            M = _matrix_for(rob[mount], g)
            if M is not None:
                out[mount + ":" + g] = M
    return out


def jet_keys(parts):
    """{"mount:group": 4x4} in jet form (from the parts' jet_pose; weapons as MechLayout does)."""
    out = {}
    for mount in ("head", "core", "arms", "legs", "booster"):
        model, meta = parts[mount]
        poses = meta.get("jet_pose", {})
        for g in model.order:
            p = poses.get(g, poses.get("*"))
            if p is not None:
                out[mount + ":" + g] = pose_matrix_any(p)
    core = parts["core"][1]
    grip = np.array(parts["arms"][1]["points"]["grip"], float)
    backL = np.array(core["points"]["back_l"], float)
    backR = np.array(core["points"].get("back_r", backL * [-1, 1, 1]), float)
    for mount, src, local in (("l_arm", "arms:l_fore", grip), ("r_arm", "arms:r_fore", grip * [-1, 1, 1]),
                              ("l_back", "core:core", backL), ("r_back", "core:core", backR)):
        if mount in parts:
            P = (out[src] @ np.append(local, 1.0))[:3]
            for g in parts[mount][0].order:
                out[mount + ":" + g] = M4(None, P)
    return out


def pose_matrix_any(p):
    if "quat" in p:
        return M4(qmat(p["quat"]), p["pos"])
    rx, ry, rz = p.get("rot", [0, 0, 0])
    return T(p["pos"]) @ Ry(ry) @ Rx(rx) @ Rz(rz)


def parent_of(parts, key):
    mount, g = key.split(":", 1)
    if mount in ("l_arm", "r_arm"):
        return ("arms:" + mount[0] + "_fore", np.zeros(3))
    if mount in ("l_back", "r_back"):
        return ("core:core", np.zeros(3))
    poses = parts[mount][1].get("jet_pose", {})
    p = poses.get(g, poses.get("*"))
    if p and "parent" in p:
        return (p["parent"], np.array(p["joint"], float))
    return None


def delay_of(parts, key):
    mount, g = key.split(":", 1)
    if mount in ("l_arm", "r_arm"):
        mount, g = "arms", mount[0] + "_fore"
    elif mount in ("l_back", "r_back"):
        mount, g = "core", "core"
    poses = parts[mount][1].get("jet_pose", {})
    p = poses.get(g, poses.get("*"))
    return p.get("delay", 0.0) if p else 0.0


def blend_keys(parts, t):
    """Mid-transformation placements, exactly as MechPoser.Blender (robot side at rest)."""
    rob = robot_keys(parts)
    jet = jet_keys(parts)
    done = {}

    def rigid(a, b, s):
        q = slerp(quat(a[:3, :3]), quat(b[:3, :3]), s)
        return M4(qmat(q), a[:3, 3] + (b[:3, 3] - a[:3, 3]) * s)

    visiting = set()

    def get(key):
        if key in done:
            return done[key]
        if key in visiting:
            raise RuntimeError("jet_pose parent cycle at " + key)
        visiting.add(key)
        d = delay_of(parts, key)
        s = min(1.0, max(0.0, (t - d) / max(0.05, 1 - d)))
        s = s * s * (3 - 2 * s)
        po = parent_of(parts, key)
        if po is not None and po[0] in rob and po[0] in jet:
            pk, j = po
            Pn = get(pk)
            rr = np.linalg.inv(rob[pk]) @ rob[key]
            rj = np.linalg.inv(jet[pk]) @ jet[key]
            pr = (rr @ np.append(j, 1))[:3]
            pj = (rj @ np.append(j, 1))[:3]
            q = slerp(quat(rr[:3, :3]), quat(rj[:3, :3]), s)
            rel = M4(qmat(q), pr + (pj - pr) * s) @ M4(None, -j)
            M = Pn @ rel
        else:
            M = rigid(rob[key], jet[key], s)
        visiting.discard(key)
        done[key] = M
        return M

    for k in rob:
        if k in jet:
            get(k)
    return done


def model_at(parts, placements, name="mech"):
    from mg import mk
    m = mk.Model(name)
    for mount, (model, meta) in parts.items():
        for g in model.order:
            M = placements.get(mount + ":" + g)
            if M is None:
                continue
            for sh in model.parts[g].shells:
                c = sh.copy()
                c.apply(M[:3, :3], M[:3, 3])
                m.add(mount + ":" + g, c)
    return m


# ------------------------------------------------------------------ contact check by group

def floating_groups(model, tol=0.01, ignore=()):
    """Groups (model parts) not connected - through touching shells - to the largest cluster."""
    from meshkit import attach
    names, data, owner = [], [], []
    for pname in model.order:
        if pname in ignore:
            continue
        for sh in model.parts[pname].shells:
            data.append(attach._tris(sh))
            owner.append(pname)
    parts = list(dict.fromkeys(owner))
    idx = {p: i for i, p in enumerate(parts)}
    parent = list(range(len(parts)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    boxes = [(d[0].min(0) - tol, d[0].max(0) + tol) for d in data]
    n = len(data)
    for a in range(n):
        for b in range(a + 1, n):
            pa, pb = idx[owner[a]], idx[owner[b]]
            if find(pa) == find(pb):
                continue
            if np.any(boxes[a][1] < boxes[b][0]) or np.any(boxes[b][1] < boxes[a][0]):
                continue
            if attach.attached(data[a], data[b], tol):
                parent[find(pa)] = find(pb)
    clusters = {}
    for p in parts:
        clusters.setdefault(find(idx[p]), []).append(p)
    cl = sorted(clusters.values(), key=len, reverse=True)
    return [p for c in cl[1:] for p in c]
