"""Synthesizes every sound of the mod (no recorded or third-party audio) and encodes it to Ogg
Vorbis with ffmpeg. Mono, so Minecraft positions them in 3D."""
import os
import subprocess
import wave

import numpy as np

from mg import ASSETS

SR = 44100
OUT = os.path.join(ASSETS, "sounds")
rng = np.random.default_rng(7)


def t(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.standard_normal(int(SR * d))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def bandish(x, lo, hi):
    return lowpass(x, hi) - lowpass(x, lo)


def env(d, attack, decay):
    tt = t(d)
    e = np.minimum(1.0, tt / max(attack, 1e-4)) * np.exp(-tt / decay)
    return e


def sweep(d, f0, f1):
    tt = t(d)
    f = f0 + (f1 - f0) * tt / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def write(name, x, gain=0.9):
    x = np.asarray(x, float)
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)


def gunshot(d, body_hz, crack, decay, thump=0.6):
    n = noise(d)
    x = bandish(n, 300, crack) * env(d, 0.001, decay)
    x += thump * np.sin(2 * np.pi * body_hz * t(d)) * env(d, 0.002, decay * 1.6)
    return x


def main():
    write("mf_rifle", gunshot(0.4, 90, 6000, 0.06))
    write("mf_mg", gunshot(0.22, 110, 7000, 0.035, 0.4))
    write("mf_gunpod", gunshot(0.3, 140, 9000, 0.04, 0.4))
    write("mf_gatling", gunshot(0.12, 160, 8000, 0.02, 0.3))
    boom = gunshot(1.2, 45, 2500, 0.25, 1.0)
    write("mf_cannon", boom)
    whoosh = bandish(noise(0.9), 400, 3000) * env(0.9, 0.05, 0.3)
    write("mf_bazooka", np.concatenate([whoosh[:int(0.15 * SR)] * 0.6, gunshot(0.75, 55, 3000, 0.18, 0.9)]))
    write("mf_missile", bandish(noise(0.9), 600, 5000) * env(0.9, 0.04, 0.35) + 0.2 * sweep(0.9, 300, 150) * env(0.9, 0.01, 0.4))
    zap = sweep(0.35, 2600, 300) * env(0.35, 0.002, 0.12) + 0.3 * np.sign(sweep(0.35, 120, 80)) * env(0.35, 0.002, 0.08)
    write("mf_laser", zap, 0.7)

    # Engine loop: turbine whine + rumble, built from whole cycles so it loops cleanly.
    d = 2.0
    tt = t(d)
    eng = 0.5 * np.sin(2 * np.pi * 340 * tt) + 0.25 * np.sin(2 * np.pi * 680 * tt) + 0.15 * np.sin(2 * np.pi * 1020 * tt)
    eng *= 0.6 + 0.4 * np.sin(2 * np.pi * 3 * tt)
    rumble = bandish(noise(d), 40, 400)
    rumble = rumble * np.hanning(len(rumble)) ** 0.1
    write("mech_engine", 0.5 * eng + 0.8 * rumble / (np.abs(rumble).max() + 1e-9), 0.6)

    qb = bandish(noise(0.55), 200, 6000) * env(0.55, 0.005, 0.16) + 0.8 * np.sin(2 * np.pi * 60 * t(0.55)) * env(0.55, 0.003, 0.08)
    write("quick_boost", qb)
    write("jump", bandish(noise(0.45), 150, 3500) * env(0.45, 0.01, 0.15) + 0.6 * np.sin(2 * np.pi * 70 * t(0.45)) * env(0.45, 0.004, 0.1))
    land = np.sin(2 * np.pi * 48 * t(0.7)) * env(0.7, 0.002, 0.16) + 0.5 * bandish(noise(0.7), 200, 2500) * env(0.7, 0.001, 0.06)
    clank = np.sin(2 * np.pi * 820 * t(0.7)) * env(0.7, 0.001, 0.05) * 0.4
    write("land", land + clank)
    step = np.sin(2 * np.pi * 62 * t(0.32)) * env(0.32, 0.002, 0.07) + 0.3 * bandish(noise(0.32), 300, 2000) * env(0.32, 0.001, 0.03)
    step += 0.15 * sweep(0.32, 900, 600) * env(0.32, 0.03, 0.08)
    write("step", step, 0.7)
    servo = 0.5 * sweep(1.3, 420, 900) * (env(1.3, 0.05, 0.9)) + 0.3 * sweep(1.3, 840, 1800) * env(1.3, 0.05, 0.7)
    clacks = np.zeros(int(1.3 * SR))
    for k, at in enumerate((0.1, 0.45, 0.8, 1.15)):
        i = int(at * SR)
        c = bandish(noise(0.08), 800, 7000) * env(0.08, 0.001, 0.015)
        clacks[i:i + len(c)] += c[:len(clacks) - i]
    write("transform", servo + clacks)
    screech = bandish(noise(0.7), 1500, 6000) * env(0.7, 0.01, 0.25) + 0.3 * sweep(0.7, 2200, 1600) * env(0.7, 0.01, 0.2)
    write("brake", screech, 0.6)


if __name__ == "__main__":
    main()
    print(sorted(os.listdir(OUT)))
