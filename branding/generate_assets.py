#!/usr/bin/env python3
"""Regenerates every Siderea logo asset from one set of geometry constants.

Outputs (relative to the repo root):
  branding/logo.svg, logo-ink.svg            master mark for dark / light backgrounds
  branding/wordmark.svg, wordmark-ink.svg    mark + SIDEREA drawn as paths (no font dependency)
  app/src/main/res/drawable/ic_launcher_foreground.xml, ic_launcher_monochrome.xml
  app/src/main/res/mipmap-anydpi/ic_launcher.xml, ic_launcher_round.xml

The same constants live in core/ui/.../SideriaMark.kt. Change both together.
Run:  python branding/generate_assets.py
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# --- geometry on a 512 grid (mirrors SideriaMark.kt) ---------------------------------------------
C = 256
R_RING, W_RING = 200, 22
R_ARC, W_ARC = 118, 34
R_STAR = 32
A_START, A_END = 160, 330  # degrees, clockwise, y-down

# --- colours -------------------------------------------------------------------------------------
INK_DARK_BG = ("#ECE9E3", "#F2A93B")  # ring/trail, star: for dark backgrounds
INK_LIGHT_BG = ("#17171A", "#D98A00")  # for light backgrounds


def fmt(v: float) -> str:
    return ("%.2f" % v).rstrip("0").rstrip(".")


def point(angle_deg, radius, cx=C, cy=C, scale=1.0):
    a = math.radians(angle_deg)
    return cx + radius * scale * math.cos(a), cy + radius * scale * math.sin(a)


def mark_elements(ring, star, indent="  "):
    sx, sy = point(A_START, R_ARC)
    ex, ey = point(A_END, R_ARC)
    return (
        f'{indent}<circle cx="{C}" cy="{C}" r="{R_RING}" fill="none" stroke="{ring}" stroke-width="{W_RING}"/>\n'
        f'{indent}<path d="M{fmt(sx)} {fmt(sy)} A{R_ARC} {R_ARC} 0 0 1 {fmt(ex)} {fmt(ey)}" fill="none" '
        f'stroke="{ring}" stroke-width="{W_ARC}" stroke-linecap="round"/>\n'
        f'{indent}<circle cx="{fmt(ex)}" cy="{fmt(ey)}" r="{R_STAR}" fill="{star}"/>\n'
    )


def logo_svg(ring, star):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" '
        'role="img" aria-labelledby="t">\n  <title id="t">Siderea</title>\n'
        + mark_elements(ring, star)
        + "</svg>\n"
    )


# Monoline letterforms on a 100-unit cap height: (path, advance width).
LETTERS = {
    "S": ("M54 24 C54 12 44 6 30 6 C16 6 6 13 6 26 C6 39 17 44 30 50 C43 56 54 61 54 74 "
          "C54 87 44 94 30 94 C16 94 6 88 6 76", 60),
    "I": ("M12 6 V94", 24),
    "D": ("M8 6 V94 H26 C46 94 56 78 56 50 C56 22 46 6 26 6 Z", 62),
    "E": ("M50 6 H8 V94 H50 M8 50 H42", 56),
    "R": ("M8 94 V6 H30 C46 6 54 14 54 28 C54 42 46 50 30 50 H8 M30 50 L54 94", 60),
    "A": ("M4 94 L30 6 L56 94 M15 64 H45", 60),
}


def wordmark_svg(ring, star):
    x, gap, parts = 150, 22, []
    for ch in "SIDEREA":
        d, w = LETTERS[ch]
        parts.append(f'    <path transform="translate({x} 2)" d="{d}"/>')
        x += w + gap
    width = x - gap + 4
    scale = 104 / 512
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} 104" width="{width}" height="104" '
        'role="img" aria-labelledby="t">\n  <title id="t">Siderea</title>\n'
        f'  <g transform="scale({scale:.5f})">\n{mark_elements(ring, star, "    ")}  </g>\n'
        f'  <g fill="none" stroke="{ring}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round">\n'
        + "\n".join(parts)
        + "\n  </g>\n</svg>\n"
    )


def circle_path(x, y, r):
    return (f"M{fmt(x + r)},{fmt(y)} A{fmt(r)},{fmt(r)} 0 1,1 {fmt(x - r)},{fmt(y)} "
            f"A{fmt(r)},{fmt(r)} 0 1,1 {fmt(x + r)},{fmt(y)} Z")


def android_vector(ring, star):
    """108dp adaptive-icon layer; glyph ring radius is 30dp, inside the 33dp safe zone."""
    scale = 30 / R_RING
    c = 54
    asx, asy = point(A_START, R_ARC, c, c, scale)
    aex, aey = point(A_END, R_ARC, c, c, scale)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Lens ring -->
    <path
        android:pathData="{circle_path(c, c, R_RING * scale)}"
        android:strokeColor="{ring}"
        android:strokeWidth="{fmt(W_RING * scale)}" />
    <!-- Star trail -->
    <path
        android:pathData="M{fmt(asx)},{fmt(asy)} A{fmt(R_ARC * scale)},{fmt(R_ARC * scale)} 0 0,1 {fmt(aex)},{fmt(aey)}"
        android:strokeColor="{ring}"
        android:strokeWidth="{fmt(W_ARC * scale)}"
        android:strokeLineCap="round" />
    <!-- The star -->
    <path
        android:pathData="{circle_path(aex, aey, R_STAR * scale)}"
        android:fillColor="{star}" />
</vector>
'''


ADAPTIVE_ICON = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/siderea_icon_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''


def write(relative: str, text: str) -> None:
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def main() -> None:
    write("branding/logo.svg", logo_svg(*INK_DARK_BG))
    write("branding/logo-ink.svg", logo_svg(*INK_LIGHT_BG))
    write("branding/wordmark.svg", wordmark_svg(*INK_DARK_BG))
    write("branding/wordmark-ink.svg", wordmark_svg(*INK_LIGHT_BG))
    write("app/src/main/res/drawable/ic_launcher_foreground.xml",
          android_vector("@color/siderea_icon_ring", "@color/siderea_icon_star"))
    # Themed (monochrome) icons use only the alpha channel, so every shape is plain black.
    write("app/src/main/res/drawable/ic_launcher_monochrome.xml", android_vector("#FF000000", "#FF000000"))
    write("app/src/main/res/mipmap-anydpi/ic_launcher.xml", ADAPTIVE_ICON)
    write("app/src/main/res/mipmap-anydpi/ic_launcher_round.xml", ADAPTIVE_ICON)
    print("assets regenerated")


if __name__ == "__main__":
    main()
