"""Generates the Thyme mascot refinements (round 5). All variants keep the original Mascot.kt
silhouette (leafy blob body, two leaf arms, terracotta pot feet) and its flat style, and use only
ovals, circles, rects and quadratic paths so they port straight back into Mascot.kt/MascotView."""
import math

LEAF = "#6E9A4E"; LEAF_LIGHT = "#9CC273"; LEAF_DARK = "#4E7A38"; LEAF_MID = "#83AD5F"; VEIN = "#B9D796"
POT = "#C2622D"; POT_SHADE = "#A84A1C"; POT_RIM = "#DB8450"
FACE = "#2B1D12"; SHINE = "#FFFDF8"; BLUSH = "#EE9A7C"; TONGUE = "#E07A6A"
STEM = "#6B5236"; FLOWER = ["#C9A0DC", "#A97CC0"]; FLOWER_EYE = "#F4E3F7"

def f(v): return f"{v:.2f}".rstrip("0").rstrip(".")
def oval(x, y, rx, ry, fill, ang=0, extra=""):
    rot = f' transform="rotate({f(ang)} {f(x)} {f(y)})"' if ang else ""
    return f'<ellipse cx="{f(x)}" cy="{f(y)}" rx="{f(rx)}" ry="{f(ry)}"{rot} fill="{fill}"{extra}/>'
def path(d, fill): return f'<path d="{d}" fill="{fill}"/>'
def line(d, col, w): return f'<path d="{d}" stroke="{col}" stroke-width="{w}" fill="none" stroke-linecap="round"/>'

# --- shared parts ------------------------------------------------------------------------------
def shadow(): return [oval(50, 95.5, 20, 2.6, FACE, extra=' opacity="0.12"')]

def pot(shaded=True):
    out = [path("M32 78 H68 L64 94 Q50 98 36 94 Z", POT)]
    if shaded: out.append(path("M34.4 88 H65.6 L64 94 Q50 98 36 94 Z", POT_SHADE))
    out.append('<rect x="30" y="77" width="40" height="7" rx="2.5" fill="' + POT_RIM + '"/>')
    return out

def arms(wave=False, veins=True):
    out = [path("M19 54 Q6 49 9 37 Q21 41 24 54 Z", LEAF)]
    if veins: out.append(line("M21.5 52 Q15 45 10.5 39", VEIN, 1.1))
    if wave:   # right arm raised in a wave
        out.append(path("M80 50 Q96 44 96 29 Q82 32 77 48 Z", LEAF_DARK))
        if veins: out.append(line("M79 48 Q88 40 95 31", LEAF_MID, 1.1))
    else:
        out.append(path("M81 54 Q94 49 91 37 Q79 41 76 54 Z", LEAF_DARK))
        if veins: out.append(line("M78.5 52 Q85 45 89.5 39", LEAF_MID, 1.1))
    return out

def body_classic():
    return [oval(50, 46, 29, 27, LEAF), oval(29, 39, 15, 13, LEAF_LIGHT), oval(71, 39, 15, 13, LEAF_DARK), oval(50, 23, 14, 12, LEAF_LIGHT)]

def body_refined():
    # Same four ovals, lit from the top-left: lobes step light -> dark, plus a soft highlight.
    return [oval(50, 46, 29, 27, LEAF), oval(71, 39, 15, 13, LEAF_DARK), oval(29, 39, 15, 13, LEAF_MID),
            oval(50, 23, 14, 12, LEAF_LIGHT), oval(44, 19.5, 5.5, 3.2, VEIN, -25, ' opacity="0.9"'),
            oval(25, 35, 4.5, 2.6, LEAF_LIGHT, -35, ' opacity="0.9"')]

def body_bushy():
    # Six lobes instead of three: a rounder, more thyme-bush crown, still flat and simple.
    return [oval(50, 47, 29, 26, LEAF),
            oval(73, 42, 13, 12, LEAF_DARK), oval(64, 26, 12, 11, LEAF), oval(27, 42, 13, 12, LEAF_MID),
            oval(36, 26, 12, 11, LEAF_MID), oval(50, 20, 12, 10.5, LEAF_LIGHT),
            oval(45, 16.5, 4.8, 2.8, VEIN, -25, ' opacity="0.9"')]

def sprig(bloom=True, base=(50, 12)):
    bx, by = base
    out = [line(f"M{bx} {by} Q{bx+1} {by-8} {bx+5} {by-13}", STEM, 1.9),
           oval(bx - 2.6, by - 5.5, 3.6, 1.9, LEAF_DARK, -35), oval(bx + 4.4, by - 6.8, 3.6, 1.9, LEAF, 30),
           oval(bx + 0.8, by - 11, 3, 1.6, LEAF_DARK, -55), oval(bx + 7.6, by - 12, 3, 1.6, LEAF, 15)]
    if bloom:
        fx, fy = bx + 5.6, by - 16.5
        out += [f'<circle cx="{f(fx+math.cos(math.radians(k*72-90))*2.4)}" cy="{f(fy+math.sin(math.radians(k*72-90))*2.4)}" r="1.9" fill="{FLOWER[k % 2]}"/>' for k in range(5)]
        out.append(f'<circle cx="{f(fx)}" cy="{f(fy)}" r="1.3" fill="{FLOWER_EYE}"/>')
    return out

def face(mouth="smile", brows=False, frown=0.0, eye_r=4.6):
    out = []
    for x in (40, 60):
        out.append(f'<circle cx="{x}" cy="48" r="{eye_r}" fill="{FACE}"/>')
        out.append(f'<circle cx="{f(x+1.7)}" cy="46.3" r="1.6" fill="{SHINE}"/>')
        out.append(f'<circle cx="{f(x-1.4)}" cy="50" r="0.7" fill="{SHINE}"/>')
    if brows:
        t = frown * 2.2   # brows tilt up in the middle as the alarm frown sets in
        out += [line(f"M36 {f(40.5+t*0.2)} Q40 {f(38.5-t*0.3)} 44 {f(40-t)}", FACE, 1.9),
                line(f"M56 {f(40-t)} Q60 {f(38.5-t*0.3)} 64 {f(40.5+t*0.2)}", FACE, 1.9)]
    out += [oval(33, 55, 5, 3, BLUSH, extra=' opacity="0.85"'), oval(67, 55, 5, 3, BLUSH, extra=' opacity="0.85"')]
    if mouth == "open":
        out += [path("M43.5 56 Q50 66.5 56.5 56 Z", FACE), oval(50, 60.6, 3.2, 1.6, TONGUE)]
    else:
        cy = 63.5 + (50.5 - 63.5) * frown       # MascotView's exact smile -> frown control point
        out.append(line(f"M43 57 Q50 {f(cy)} 57 57", FACE, 2.6))
    return out

def svg(name, title, parts, fit=False):
    if fit: parts = ['<g transform="translate(4 7.8) scale(0.92)">'] + parts + ['</g>']
    open(f"{name}.svg", "w").write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="200" height="200">\n  <title>{title}</title>\n  '
        + "\n  ".join(parts) + "\n</svg>\n")

# 0. The current Mascot.kt, unchanged, for reference.
svg("original", "Thyme (current)", [
    path("M32 78 H68 L64 94 Q50 98 36 94 Z", "#D97742"), '<rect x="30" y="78" width="40" height="6" fill="#E89760"/>',
    path("M19 54 Q6 49 9 37 Q21 41 24 54 Z", "#6B9E52"), path("M81 54 Q94 49 91 37 Q79 41 76 54 Z", "#6B9E52"),
    oval(50, 46, 29, 27, "#6B9E52"), oval(29, 39, 15, 13, "#9AC77E"), oval(71, 39, 15, 13, "#4A7A3B"), oval(50, 23, 14, 12, "#9AC77E"),
    '<circle cx="40" cy="48" r="4.6" fill="#3B2E22"/>', '<circle cx="60" cy="48" r="4.6" fill="#3B2E22"/>',
    '<circle cx="41.6" cy="46.4" r="1.4" fill="#FFFFFF"/>', '<circle cx="61.6" cy="46.4" r="1.4" fill="#FFFFFF"/>',
    oval(33, 55, 5, 3, "#F0A488", extra=' opacity="0.75"'), oval(67, 55, 5, 3, "#F0A488", extra=' opacity="0.75"'),
    line("M43 57 Q50 63.5 57 57", "#3B2E22", 2.6)])

# 1. Refined: same shapes, consistent top-left lighting, leaf veins, pot shading, livelier eyes.
svg("refined", "Thyme, refined", shadow() + pot() + arms() + body_refined() + face())

# 2. Sprig: refined + a flowering thyme sprig growing from the crown, so it reads as thyme.
svg("sprig", "Thyme with sprig", shadow() + pot() + arms() + sprig(base=(49, 13)) + body_refined() + face(), fit=True)

# 3. Bushy: six lobes for a fuller thyme-bush crown, sprig on top.
svg("bushy", "Thyme, bushier", shadow() + pot() + arms() + sprig(base=(50, 11)) + body_bushy() + face(), fit=True)

# 4. Friendly: sprig version with a waving arm, soft brows and an open smile.
svg("friendly", "Thyme, waving", shadow() + pot() + arms(wave=True) + sprig(base=(49, 13)) + body_refined() + face(mouth="open", brows=True), fit=True)

# Alarm states for the sprig version, i.e. what MascotView would animate between.
svg("sprig-alarm", "Thyme with sprig, five minutes into the alarm", shadow() + pot() + arms() + sprig(base=(49, 13)) + body_refined() + face(brows=True, frown=1.0), fit=True)
