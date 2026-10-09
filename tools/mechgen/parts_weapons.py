"""Weapons. Origin at the grip (arm weapons, barrel along +Z) or the mount (back weapons)."""
import numpy as np

from mg import mk, stack, seg, cyl, cbox, mirror, nozzle, finish, r3


def _arm_meta(pid, name, maker, tier, weight, armor, weapon, muzzle, projectile="minecraft:iron_nugget",
              recoil=0.12, mount="arm"):
    return {"slot": "weapon", "display_name": name, "maker": maker, "tier": tier, "weight": weight, "armor": armor,
            "points": {"muzzle": r3(muzzle)},
            "stats": {"weapon": weapon, "mount": mount, "projectile_item": projectile, "recoil_distance": recoil}}


def rifle():
    """WR-K1 burst rifle (Kestrel)."""
    m = mk.Model("wa_rifle")
    m.add("gun", cbox(0, 0.12, 0.22, 0.17, 0.22, 0.72, "metal", c=0.04))
    m.add("gun", cbox(0, 0.15, -0.2, 0.12, 0.17, 0.3, "armor", c=0.03))
    m.add("gun", cbox(0, -0.05, 0.36, 0.1, 0.26, 0.14, "frame", c=0.02))
    m.add("gun", cyl((0, 0.14, 0.55), (0, 0.14, 1.5), 0.05, n=6, tag="metal"))
    m.add("gun", cyl((0, 0.14, 1.42), (0, 0.14, 1.6), 0.075, n=6, tag="frame"))
    m.add("gun", cbox(0, 0.28, 0.25, 0.08, 0.08, 0.3, "accent", c=0.015))
    finish(m, "kd", size=128)
    return m, _arm_meta("wa_rifle", "WR-K1 Burst Rifle", "Kestrel Dynamics", 1, 3.0, 40, "mf_burst_rifle", (0, 0.14, 1.62))


def machinegun():
    """WR-G2 assault machine gun (Grauwerk)."""
    m = mk.Model("wa_mg")
    m.add("gun", cbox(0, 0.13, 0.2, 0.22, 0.26, 0.8, "armor2", c=0.04))
    m.add("gun", cbox(0, -0.08, 0.3, 0.2, 0.22, 0.26, "frame", c=0.03))
    for x in (0.045, -0.045):
        m.add("gun", cyl((x, 0.16, 0.55), (x, 0.16, 1.35), 0.04, n=6, tag="metal"))
    m.add("gun", cbox(0, 0.16, 1.3, 0.2, 0.14, 0.1, "frame", c=0.02))
    m.add("gun", cbox(0, 0.3, 0.1, 0.12, 0.06, 0.3, "accent", c=0.015))
    finish(m, "gw", size=128)
    return m, _arm_meta("wa_mg", "WR-G2 Assault MG", "Grauwerk Heavy Industries", 2, 4.0, 50, "mf_assault_mg", (0, 0.16, 1.4),
                        recoil=0.06)


def bazooka():
    """WR-G3 bazooka (Grauwerk)."""
    m = mk.Model("wa_bazooka")
    m.add("gun", cyl((0, 0.22, -0.55), (0, 0.22, 1.25), 0.17, n=8, tag="armor"))
    m.add("gun", cyl((0, 0.22, 1.2), (0, 0.22, 1.42), 0.2, n=8, tag="frame"))
    m.add("gun", cyl((0, 0.22, -0.7), (0, 0.22, -0.5), 0.2, n=8, tag="frame"))
    m.add("gun", cbox(0, 0.0, 0.2, 0.12, 0.26, 0.16, "frame", c=0.02))
    m.add("gun", cbox(0.2, 0.3, 0.3, 0.1, 0.12, 0.3, "accent", c=0.02))
    finish(m, "gw", size=128)
    return m, _arm_meta("wa_bazooka", "WR-G3 Bazooka", "Grauwerk Heavy Industries", 3, 6.5, 60, "mf_bazooka", (0, 0.22, 1.45),
                        projectile="minecraft:gunpowder", recoil=0.3)


def laser_rifle():
    """WR-A4 pulse laser rifle (Arachne)."""
    m = mk.Model("wa_laser")
    m.add("gun", stack([(0.0, 0, 0.1, 0.09, 0.42, 0.04), (0.32, 0, 0.15, 0.1, 0.48, 0.05)], "armor"))
    m.add("gun", seg([(0, 0.18, 0.5), (0, 0.18, 1.5)], [(0.06, 0.09), (0.04, 0.06)], "armor2", c=0.02, side=(1, 0, 0)))
    m.add("gun", cbox(0, 0.18, 1.5, 0.1, 0.12, 0.1, "eye", c=0.02))
    m.add("gun", cbox(0, -0.06, 0.25, 0.1, 0.22, 0.14, "frame", c=0.02))
    finish(m, "ar", size=128)
    return m, _arm_meta("wa_laser", "WR-A4 Pulse Laser", "Arachne Systems", 4, 4.5, 40, "mf_pulse_laser", (0, 0.18, 1.58),
                        projectile="minecraft:redstone", recoil=0.04)


def gunpod():
    """WR-KF gun pod (Falcon line) - long, slim, fits under the jet form."""
    m = mk.Model("wa_gunpod")
    m.add("gun", stack([(-0.02, 0, 0.3, 0.12, 0.6, 0.05), (0.26, 0, 0.32, 0.13, 0.62, 0.06)], "armor2"))
    m.add("gun", cbox(0, -0.06, -0.05, 0.1, 0.22, 0.14, "frame", c=0.02))
    m.add("gun", cyl((0, 0.13, 0.9), (0, 0.13, 1.5), 0.055, n=6, tag="metal"))
    m.add("gun", cbox(0, 0.29, 0.2, 0.1, 0.06, 0.5, "accent", c=0.015))
    finish(m, "kf", size=128)
    return m, _arm_meta("wa_gunpod", "WR-KF Gun Pod", "Kestrel Dynamics", 4, 3.5, 40, "mf_gun_pod", (0, 0.13, 1.55),
                        recoil=0.05)


def missile_pod():
    """WB-K1 missile pod (back)."""
    m = mk.Model("wb_missile")
    # streamlined launcher: a rounded pentagon pointing up, tapering aft; launch face forward
    from mg import pseg
    m.add("pod", pseg([(0, 0.16, -0.42), (0, 0.16, -0.05), (0, 0.16, 0.45)],
                      [(0.18, 0.24, 0.2), (0.26, 0.3, 0.22), (0.25, 0.28, 0.22)], "armor2", fwd=(0, 1, 0)))
    m.add("pod", cbox(0, 0.17, 0.47, 0.4, 0.34, 0.05, "frame", c=0.02))
    for x in (0.1, -0.1):
        for y in (0.08, 0.25):
            m.add("pod", cyl((x, y, 0.48), (x, y, 0.52), 0.055, n=6, tag="accent"))
    m.add("pod", seg([(0, 0.42, -0.3), (0, 0.46, 0.25)], [(0.03, 0.03), (0.03, 0.02)], "accent", c=0.01))
    m.add("pod", cyl((0, -0.06, 0), (0, 0.0, 0), 0.12, n=8, tag="frame"))
    finish(m, "kd", size=128)
    meta = _arm_meta("wb_missile", "WB-K1 Missile Pod", "Kestrel Dynamics", 2, 5.0, 60, "mf_missile_pod", (0, 0.16, 0.6),
                     projectile="minecraft:firework_rocket", recoil=0.0, mount="back")
    meta["stats"].update({"min_pitch": -60, "max_pitch": 25, "fcs": False})
    return m, meta


def shoulder_cannon():
    """WB-G2 shoulder cannon (back)."""
    m = mk.Model("wb_cannon")
    m.add("gun", cbox(0, 0.15, -0.05, 0.38, 0.36, 0.7, "armor", c=0.05))
    m.add("gun", cyl((0, 0.18, 0.25), (0, 0.18, 1.85), 0.08, n=8, tag="metal"))
    m.add("gun", cyl((0, 0.18, 1.7), (0, 0.18, 1.95), 0.11, n=8, tag="frame"))
    m.add("gun", cyl((0, -0.06, 0), (0, 0.0, 0), 0.13, n=8, tag="frame"))
    m.add("gun", cbox(0.2, 0.2, -0.05, 0.06, 0.2, 0.4, "accent", c=0.015))
    finish(m, "gw", size=128)
    meta = _arm_meta("wb_cannon", "WB-G2 Shoulder Cannon", "Grauwerk Heavy Industries", 3, 8.0, 80, "mf_shoulder_cannon",
                     (0, 0.18, 1.98), projectile="minecraft:gunpowder", recoil=0.4, mount="back")
    meta["stats"].update({"min_pitch": -45, "max_pitch": 20})
    return m, meta


def gatling():
    """WB-A3 back gatling (Arachne)."""
    m = mk.Model("wb_gatling")
    m.add("gun", cbox(0, 0.16, -0.05, 0.36, 0.34, 0.6, "armor", c=0.05))
    for k in range(6):
        a = k * np.pi / 3
        x, y = 0.07 * np.cos(a), 0.18 + 0.07 * np.sin(a)
        m.add("gun", cyl((x, y, 0.2), (x, y, 1.25), 0.025, n=5, tag="metal"))
    m.add("gun", cyl((0, 0.18, 1.0), (0, 0.18, 1.08), 0.12, n=8, tag="armor2"))
    m.add("gun", cyl((0, -0.06, 0), (0, 0.0, 0), 0.12, n=8, tag="frame"))
    finish(m, "ar", size=128)
    meta = _arm_meta("wb_gatling", "WB-A3 Back Gatling", "Arachne Systems", 3, 6.0, 60, "mf_back_gatling", (0, 0.18, 1.3),
                     recoil=0.02, mount="back")
    meta["stats"].update({"min_pitch": -50, "max_pitch": 25})
    return m, meta


def laser_blade():
    """WM-K1 laser blade (melee, arm): a forearm emitter projecting a long, flat energy blade forward.
    Fired, the frame dashes at the locked target and swings it once (stats.melee, see MechPart.MeleeSpec)."""
    m = mk.Model("wa_blade")
    # emitter housing round the grip, with a hand guard
    m.add("blade", stack([(-0.12, 0, 0.12, 0.1, 0.3, 0.05), (0.42, 0, 0.14, 0.11, 0.32, 0.05)], "armor2"))
    m.add("blade", cbox(0, -0.06, 0.1, 0.1, 0.22, 0.14, "frame", c=0.02))
    m.add("blade", cbox(0, 0.14, 0.52, 0.2, 0.36, 0.08, "armor", c=0.02))
    m.add("blade", cyl((0, 0.14, 0.55), (0, 0.14, 0.66), 0.07, n=8, tag="metal"))
    m.add("blade", cbox(0, 0.3, 0.2, 0.06, 0.04, 0.4, "accent", c=0.01))
    # the blade: flat (edge up and down), tapering to the tip
    m.add("blade", seg([(0, 0.14, 0.62), (0, 0.14, 2.2), (0, 0.14, 3.05)], [(0.035, 0.11), (0.03, 0.1), (0.01, 0.015)],
                       "eye", c=0.01, side=(1, 0, 0)))
    finish(m, "kd", size=128)
    meta = _arm_meta("wa_blade", "WM-K1 Laser Blade", "Kestrel Dynamics", 3, 3.0, 40, "mf_laser_blade", (0, 0.14, 3.0),
                     recoil=0.0)
    meta["stats"].update({"fcs": False, "melee": {"damage": 420, "reach": 4.2, "arc": 70, "lunge_speed": 1.6,
                                                  "lunge_range": 11, "en_cost": 350, "cooldown": 30}})
    return m, meta


ALL = [rifle, machinegun, bazooka, laser_rifle, gunpod, missile_pod, shoulder_cannon, gatling, laser_blade]
