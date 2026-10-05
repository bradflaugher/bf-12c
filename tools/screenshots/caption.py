#!/usr/bin/env python3
"""Turns the raw emulator captures into the captioned Google Play screenshots.

    tools/screenshots/caption.py phone|tab7|tab10|feature

shoot.py runs this itself after capturing; run it alone to change only the
captions. It reads the raw captures from build/screenshots/<class>/ and
writes fastlane/metadata/android/en-US/images/<class dir>/N_name.png at the
exact size of the capture (9:16 or 16:9, so Play accepts them). `feature`
rebuilds featureGraphic.png (1024x500) from the phone captures.

Each image is the app's own look: the bezel gradient behind, a phosphor-green
VT323 headline with its CRT glow, a Share Tech Mono subline in the 12c's gold,
the gold stripe, and the capture below in a rounded bezel. The fonts are the
app's (app/src/main/res/font). Needs ImageMagick 7.
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RAW = REPO / "build/screenshots"
IMAGES = REPO / "fastlane/metadata/android/en-US/images"
CRT = REPO / "app/src/main/res/font/vt323.ttf"
MONO = REPO / "app/src/main/res/font/share_tech_mono.ttf"
MAGICK = shutil.which("magick") or sys.exit("needs ImageMagick 7 (magick)")

# The app's palette (ui/Theme.kt).
GREEN = "#39FF6A"
GOLD = "#E8A33D"
GOLD_BRIGHT = "#FFCB6B"
LABEL = "#F1F0EA"
BEZEL_EDGE = "#26282A"

# Store order, raw capture, headline and subline. Each one shows a different feature.
CAPTIONS = {
    "phone": [
        ("tvm", "TVM IN FIVE KEYS", "n i PV PMT FV: a 30-year mortgage payment"),
        ("digits", "34 SIGNIFICANT DIGITS", "In every register. f EEX shows all of them."),
        ("cashflows", "NPV, IRR AND CASH FLOWS", "Press f and the gold legends light up"),
        ("stack", "REAL RPN, FOUR-LEVEL STACK", "X Y Z T in view, plus LSTx and memory"),
        ("program", "KEYSTROKE PROGRAMS", "99 lines, 12c keycodes and a readable mnemonic"),
        ("amber", "FOUR PHOSPHORS", "P1 green, P3 amber, ice, and a classic 12c LCD"),
        ("menu", "ONE KEY FOR EVERYTHING ELSE", "ON opens themes, haptics, the manual and tips"),
        ("lcd", "THE FULL 12C KEYBOARD", "Turn sideways for the classic 4×10 layout, key for key"),
    ],
    "tablet": [
        ("landscape", "THE FULL 12C KEYBOARD", "Bigger legends and registers on a big screen"),
        ("portrait", "NPV, IRR AND CASH FLOWS", "Portrait folds the keys and keeps X Y Z T in view"),
        ("digits", "34 SIGNIFICANT DIGITS", "In every register. f EEX shows all of them."),
        ("program", "KEYSTROKE PROGRAMS", "99 lines, 12c keycodes and a readable mnemonic"),
        ("menu", "ONE KEY FOR EVERYTHING ELSE", "ON opens themes, haptics, the manual and tips"),
        ("lcd", "FOUR PHOSPHORS", "A classic 12c LCD, or P1 green, P3 amber and ice"),
    ],
}

DIRS = {"phone": "phoneScreenshots", "tab7": "sevenInchScreenshots", "tab10": "tenInchScreenshots"}


def magick(*args):
    subprocess.run([MAGICK, *map(str, args)], check=True)


def text_width(font, size, text):
    out = subprocess.run(
        [MAGICK, "-font", font, "-pointsize", str(size), f"label:{text}", "-format", "%w", "info:"],
        check=True, capture_output=True,
    ).stdout
    return int(out)


def fit(font, size, text, width):
    """[size], or smaller if [text] would not fit in [width] pixels on one line."""
    w = text_width(font, size, text)
    return size if w <= width else int(size * width / w)


def glow_text(tmp, name, text, font, size, color, blur):
    """A text layer with the CRT bloom behind it, on transparent, padded by 3 x [blur] all round."""
    out = tmp / f"{name}.png"
    magick(
        "-background", "none", "-fill", color, "-font", font, "-pointsize", size, f"label:{text}",
        # Pad before blurring, so the bloom isn't clipped at the text's edges.
        "-bordercolor", "none", "-border", 3 * blur,
        "(", "+clone", "-blur", f"0x{blur}", ")", "+swap",
        "-background", "none", "-flatten", out,
    )
    return out


def size_of(path):
    out = subprocess.run([MAGICK, "identify", "-format", "%w %h", path], check=True, capture_output=True).stdout
    return tuple(map(int, out.split()))


def framed(tmp, raw, width, height, radius, border):
    """The capture scaled into width x height, with rounded corners and a bezel edge."""
    scaled = tmp / "scaled.png"
    magick(raw, "-resize", f"{width}x{height}", scaled)
    w, h = size_of(scaled)
    rounded = tmp / "rounded.png"
    magick(
        scaled, "(", "-size", f"{w}x{h}", "xc:black", "-fill", "white",
        "-draw", f"roundrectangle 0,0,{w - 1},{h - 1},{radius},{radius}", ")",
        "-alpha", "off", "-compose", "CopyOpacity", "-composite", rounded,
    )
    out = tmp / "framed.png"
    W, H, R = w + 2 * border, h + 2 * border, radius + border
    magick(
        "-size", f"{W}x{H}", "xc:none", "-fill", BEZEL_EDGE,
        "-draw", f"roundrectangle 0,0,{W - 1},{H - 1},{R},{R}",
        rounded, "-geometry", f"+{border}+{border}", "-compose", "over", "-composite", out,
    )
    return out


def background(tmp, w, h):
    out = tmp / "bg.png"
    magick("-size", f"{w}x{h}", "gradient:#1A1B1C-#050505", out)
    return out


def stripe(tmp, width, thickness):
    out = tmp / "stripe.png"
    magick(
        "-size", f"{width}x{thickness}", "xc:" + GOLD,
        "(", "-size", f"{thickness}x{width}", "gradient:white-black", "-rotate", "90",
        "-function", "Polynomial", "-4,4,0", ")",
        "-alpha", "off", "-compose", "CopyOpacity", "-composite", out,
    )
    return out


def caption(raw, out, headline, subline):
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        w, h = size_of(raw)
        portrait = h > w
        u = min(w, h) / 1080  # 1 on a phone; tablets scale everything up
        margin = int(64 * u)
        band = int((380 if portrait else 250) * u)
        text_w = w - 2 * margin

        # Spacing follows the nominal sizes, so a long headline that had to shrink
        # doesn't move the capture: every image in the set lines up.
        head_nominal = int((104 if portrait else 96) * u)
        sub_nominal = int((38 if portrait else 36) * u)
        head_size = fit(CRT, head_nominal, headline, text_w)
        sub_size = fit(MONO, sub_nominal, subline, text_w)
        blur = int(10 * u)
        head = glow_text(tmp, "head", headline, CRT, head_size, GREEN, blur)
        sub = tmp / "sub.png"
        magick("-background", "none", "-fill", GOLD_BRIGHT, "-font", MONO, "-pointsize", sub_size, f"label:{subline}", sub)
        line = stripe(tmp, int(w * 0.72), max(2, int(3 * u)))

        # Top down: headline, subline, the gold stripe, then the capture.
        head_y = int((110 if portrait else 40) * u)
        sub_y = head_y + int(head_nominal * 1.12)
        line_y = sub_y + int(sub_nominal * 1.7)
        shot_top = line_y + int((52 if portrait else 34) * u)
        shot_w = w - 2 * margin
        shot_h = h - shot_top - int((52 if portrait else 34) * u)
        frame = framed(tmp, raw, shot_w, shot_h, int(30 * u), max(2, int(3 * u)))
        _, frame_h = size_of(frame)
        # A short capture (landscape) sits centered in the room under the caption.
        shot_top += (shot_h - frame_h) // 2

        out.parent.mkdir(parents=True, exist_ok=True)
        magick(
            background(tmp, w, h),
            head, "-gravity", "north", "-geometry", f"+0+{head_y - 3 * blur + (head_nominal - head_size) // 2}", "-composite",
            sub, "-gravity", "north", "-geometry", f"+0+{sub_y}", "-composite",
            line, "-gravity", "north", "-geometry", f"+0+{line_y}", "-composite",
            # Shadow under the device frame, then the frame.
            "(", frame, "-background", "black", "-shadow", "70x%d+0+%d" % (int(18 * u), int(10 * u)), ")",
            "-gravity", "north", "-geometry", f"+0+{shot_top}", "-composite",
            frame, "-gravity", "north", "-geometry", f"+0+{shot_top + int(4 * u)}", "-composite",
            "-alpha", "off", f"PNG24:{out}",
        )
    print(out.relative_to(REPO))


def feature_graphic():
    """1024x500: the brand on the left, the landscape keyboard on the right."""
    raw = RAW / "phone/cashflows.png"
    out = IMAGES / "featureGraphic.png"
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        frame = framed(tmp, raw, 540, 304, 16, 2)
        head = glow_text(tmp, "head", "RPN FINANCIAL", CRT, 66, GREEN, 8)
        head2 = glow_text(tmp, "head2", "CALCULATOR", CRT, 66, GREEN, 8)
        # glow_text pads 24px all round; the positions below allow for it.
        sub = tmp / "sub.png"
        magick("-background", "none", "-fill", GOLD_BRIGHT, "-font", MONO, "-pointsize", 21,
               "-interline-spacing", 6, "label:34 digits · TVM · cash flows\nprograms · no ads, no network", sub)
        badge = tmp / "badge.png"
        magick(
            "-size", "92x58", "gradient:%s-%s" % (GOLD_BRIGHT, GOLD),
            "(", "+clone", "-fill", "black", "-colorize", "100", "-fill", "white",
            "-draw", "roundrectangle 0,0 91,57 10,10", ")", "-alpha", "off", "-compose", "CopyOpacity", "-composite",
            "-compose", "over", "-font", MONO, "-pointsize", 44, "-fill", "#1A1206", "-gravity", "center",
            "-annotate", "+0-2", "bf", badge,
        )
        brand = tmp / "brand.png"
        magick("-background", "none", "-fill", LABEL, "-font", MONO, "-pointsize", 58, "label:12C", brand)
        line = stripe(tmp, 360, 2)
        magick(
            background(tmp, 1024, 500),
            badge, "-gravity", "northwest", "-geometry", "+44+86", "-composite",
            brand, "-gravity", "northwest", "-geometry", "+150+82", "-composite",
            head, "-gravity", "northwest", "-geometry", "+16+160", "-composite",
            head2, "-gravity", "northwest", "-geometry", "+16+222", "-composite",
            line, "-gravity", "northwest", "-geometry", "+44+318", "-composite",
            sub, "-gravity", "northwest", "-geometry", "+44+338", "-composite",
            "(", frame, "-background", "black", "-shadow", "70x14+0+8", ")",
            "-gravity", "east", "-geometry", "+18+0", "-composite",
            frame, "-gravity", "east", "-geometry", "+30-4", "-composite",
            "-alpha", "off", f"PNG24:{out}",
        )
    print(out.relative_to(REPO))


def main():
    which = sys.argv[1] if len(sys.argv) == 2 else None
    if which == "feature":
        feature_graphic()
        return
    if which not in DIRS:
        sys.exit(__doc__)
    out_dir = IMAGES / DIRS[which]
    for old in out_dir.glob("*.png"):
        old.unlink()
    for n, (name, headline, subline) in enumerate(CAPTIONS["phone" if which == "phone" else "tablet"], 1):
        caption(RAW / which / f"{name}.png", out_dir / f"{n}_{name}.png", headline, subline)


if __name__ == "__main__":
    main()
