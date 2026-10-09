"""Python mirror of the mod's MechLayout (rest pose and jet pose) for previews and checks.

Places every part model exactly as MechLayout / MechPoser do at rest, so a preview here shows
what the game will draw before any animation."""
import math

import numpy as np

from mg import mk


def T(v):
    M = np.eye(4)
    M[:3, 3] = v
    return M


def Rx(d):
    a = math.radians(d); c, s = math.cos(a), math.sin(a)
    M = np.eye(4); M[1, 1] = c; M[1, 2] = -s; M[2, 1] = s; M[2, 2] = c
    return M


def Ry(d):
    a = math.radians(d); c, s = math.cos(a), math.sin(a)
    M = np.eye(4); M[0, 0] = c; M[0, 2] = s; M[2, 0] = -s; M[2, 2] = c
    return M


def Rz(d):
    a = math.radians(d); c, s = math.cos(a), math.sin(a)
    M = np.eye(4); M[0, 0] = c; M[0, 1] = -s; M[1, 0] = s; M[1, 1] = c
    return M


def pose_matrix(pose):
    """T(pos)·Ry·Rx·Rz - identical to MechLayout.poseMatrix."""
    rx, ry, rz = pose.get("rot", [0, 0, 0])
    return T(pose["pos"]) @ Ry(ry) @ Rx(rx) @ Rz(rz)


def pt(meta, name, default=(0, 0, 0)):
    return np.array(meta.get("points", {}).get(name, default), float)


def robot_placements(parts):
    """parts: {mount: (model, meta)} -> {mount: {group: 4x4}} at rest."""
    legs = parts["legs"][1]
    core = parts["core"][1]
    hip = pt(legs, "hip", (0, 3, 0))
    coreM = T(hip) @ Rx(legs.get("stats", {}).get("core_tilt", 0.0))

    def level(local):
        """MechLayout.level: an untilted frame at a point of the (possibly tilted) core."""
        return T((coreM @ np.append(np.asarray(local, float), 1.0))[:3])

    shL = pt(core, "shoulder_l", (1.1, 1.5, 0))
    shR = pt(core, "shoulder_r", (-shL[0], shL[1], shL[2])) if "shoulder_r" in core.get("points", {}) else np.array([-shL[0], shL[1], shL[2]])
    backL = pt(core, "back_l", (0.7, 1.9, -0.5))
    backR = pt(core, "back_r", (-backL[0], backL[1], backL[2]))
    out = {"legs": {"*": np.eye(4)}, "core": {"*": coreM}, "head": {"*": level(pt(core, "neck"))},
           "booster": {"*": coreM @ T(pt(core, "back"))}}
    armL = level(shL)
    armR = level(shR)
    out["arms"] = {"l_*": armL, "r_*": armR}
    grip = pt(parts["arms"][1], "grip") if "arms" in parts else np.zeros(3)
    out["l_arm"] = {"*": armL @ T(grip)}
    out["r_arm"] = {"*": armR @ T([-grip[0], grip[1], grip[2]])}
    out["l_back"] = {"*": level(backL)}
    out["r_back"] = {"*": level(backR)}
    return out


def jet_placements(parts):
    out = {}
    for mount in ("head", "core", "arms", "legs", "booster"):
        poses = parts[mount][1].get("jet_pose", {})
        out[mount] = {g: pose_matrix(p) for g, p in poses.items()}
    arms = parts["arms"][1]
    core = parts["core"][1]
    grip = pt(arms, "grip")
    foreL = out["arms"].get("l_fore", out["arms"].get("*", np.eye(4)))
    foreR = out["arms"].get("r_fore", out["arms"].get("*", np.eye(4)))
    coreJ = out["core"].get("core", out["core"].get("*", np.eye(4)))
    backL = pt(core, "back_l")
    out["l_arm"] = {"*": foreL @ T(grip)}
    out["r_arm"] = {"*": foreR @ T([-grip[0], grip[1], grip[2]])}
    out["l_back"] = {"*": coreJ @ T(backL)}
    out["r_back"] = {"*": coreJ @ T([-backL[0], backL[1], backL[2]])}
    return out


def _matrix_for(place, group):
    if group in place:
        return place[group]
    if group[:2] in ("l_", "r_") and group[:2] + "*" in place:
        return place[group[:2] + "*"]
    return place.get("*")


def assemble(parts, form="robot", name="mech", t=None):
    """Combined meshkit Model (texture-less) of an assembly. t: optional transform progress 0..1."""
    rob = robot_placements(parts)
    jet = jet_placements(parts) if form != "robot" or t is not None else None
    m = mk.Model(name)
    for mount, (model, meta) in parts.items():
        for g in model.order:
            if form == "robot" and t is None:
                M = _matrix_for(rob[mount], g)
            else:
                M = _matrix_for(jet[mount], g)
            if M is None:
                continue
            for sh in model.parts[g].shells:
                c = sh.copy()
                c.apply(M[:3, :3], M[:3, 3])
                m.add(mount + ":" + g, c)
    return m
