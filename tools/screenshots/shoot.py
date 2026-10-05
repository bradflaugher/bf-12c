#!/usr/bin/env python3
"""Captures the Play and README screenshots from the real app on an emulator.

    ./gradlew assembleDebug
    tools/screenshots/shoot.py SERIAL phone|tab7|tab10

Boot an API 37 emulator first (see "Visual checks" in AGENTS.md). Any AVD
without a display cutout works for every class, since the script sets the
screen size and density itself; a cutout pads one side of landscape. Needs
adb and ImageMagick.

The script installs the debug APK, sizes the screen to an exact 9:16 at a
real density for that class, types each scene on the calculator's own keys
(found through their TalkBack labels) and writes the raw 24-bit captures
into build/screenshots/<class>/. caption.py then turns them into the
captioned Play images in fastlane/metadata/android/en-US/images/ (and,
for `phone`, the feature graphic). `phone` and `tab10` also refresh the
uncaptioned README shots in docs/screenshots/. Every scene starts with the
first-run tips skipped, except the README's tips shot. When it is done it
puts back exactly the screen size and density overrides, rotation and
settings it found.
"""
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PKG = "com.bradflaugher.bf12c.debug"
ACTIVITY = f"{PKG}/com.bradflaugher.bf12c.MainActivity"
RAW = REPO / "build/screenshots"
DOCS = REPO / "docs/screenshots"
SKIP_TIPS = "com.bradflaugher.bf12c.SKIP_TIPS"
MAGICK = shutil.which("magick") or shutil.which("convert") or sys.exit("needs ImageMagick")
ADB = shutil.which("adb") or os.path.join(
    os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk")), "platform-tools", "adb"
)

# Portrait size (w x h, exactly 9:16) and density per Play category. The
# density keeps each class's real window size in dp, so tablets get the
# tablet layout: ~411dp phone, ~612dp 7-inch, 810dp 10-inch.
DEVICES = {
    "phone": ((1080, 1920), 420),
    "tab7": ((1224, 2176), 320),
    "tab10": ((1620, 2880), 320),
}

# The first word group of each key's TalkBack label (Key.spoken).
TOKENS = {
    "n": "n", "i": "i", "PV": "PV", "PMT": "PMT", "FV": "FV", "CHS": "change sign",
    "/": "divide", "yx": "y to the x", "1/x": "reciprocal", "%T": "percent of total",
    "D%": "percent change", "%": "percent", "EEX": "enter exponent", "*": "times",
    "RS": "run stop", "SST": "single step", "RDN": "roll down", "SWAP": "x exchange y",
    "CLX": "clear x", "ENTER": "enter", "-": "minus", "MENU": "menu", "f": "f", "g": "g",
    "STO": "store", "RCL": "recall", ".": "decimal point", "S+": "sigma plus", "+": "plus",
}


class Device:
    def __init__(self, serial):
        self.serial = serial
        self.keys = {}
        # A tablet AVD can be landscape-first; then rotation 0 is landscape.
        self.landscape_first = False

    def adb(self, *args, capture=False):
        cmd = [ADB, "-s", self.serial, *args]
        if capture:
            return subprocess.run(cmd, check=True, capture_output=True).stdout
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)

    def sh(self, *args):
        self.adb("shell", *args)

    def nodes(self):
        self.sh("uiautomator", "dump", "/sdcard/bf12c-ui.xml")
        xml = self.adb("shell", "cat", "/sdcard/bf12c-ui.xml", capture=True).decode()
        for node in re.findall(r"<node [^>]*>", xml):
            desc = re.search(r'content-desc="([^"]*)"', node).group(1)
            text = re.search(r' text="([^"]*)"', node).group(1)
            x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            yield desc, text, ((x1 + x2) // 2, (y1 + y2) // 2)

    def map_keys(self):
        wanted = set(TOKENS.values()) | {str(d) for d in range(10)}
        for _ in range(15):
            self.keys = {desc.split(",")[0]: center for desc, _, center in self.nodes() if desc}
            missing = wanted - self.keys.keys()
            if not missing:
                return
            time.sleep(1)
        sys.exit(f"keys not found on screen: {sorted(missing)}")

    def tap(self, xy):
        self.sh("input", "tap", str(xy[0]), str(xy[1]))

    def type(self, script):
        """Presses [script], in the engine tests' keystroke DSL, on the on-screen keys."""
        for token in script.split():
            if token in TOKENS:
                self.tap(self.keys[TOKENS[token]])
            else:
                for c in token:
                    self.tap(self.keys["decimal point" if c == "." else c])
        time.sleep(1.0)

    def tap_text(self, pattern):
        for _, text, center in self.nodes():
            if re.fullmatch(pattern, text.strip()):
                self.tap(center)
                time.sleep(0.8)
                return
        sys.exit(f"no on-screen text matches {pattern}")

    def rotate(self, landscape, keys=True):
        self.sh("settings", "put", "system", "accelerometer_rotation", "0")
        rotated = landscape != self.landscape_first
        self.sh("settings", "put", "system", "user_rotation", "1" if rotated else "0")
        time.sleep(2.5)
        if keys:
            self.map_keys()

    def fresh(self, landscape, phosphor=None, tips=False):
        """A clean calculator: no stack, registers or program left from the last scene."""
        self.sh("pm", "clear", PKG)
        self.sh("am", "start", "-W", "-n", ACTIVITY, *([] if tips else ["--ez", SKIP_TIPS, "true"]))
        # The tips hide the keys from TalkBack, and so from map_keys.
        self.rotate(landscape, keys=not tips)
        # The boot banner types itself out first.
        time.sleep(1.5)
        if phosphor:
            self.type("MENU")
            self.tap_text(r"\[?" + re.escape(phosphor) + r"\]?")
            self.tap_text(r"EXIT")
            self.map_keys()

    def shot(self, path):
        time.sleep(1.2)
        path.parent.mkdir(parents=True, exist_ok=True)
        # Play wants 24-bit PNGs; screencap writes RGBA.
        subprocess.run(
            [MAGICK, "png:-", "-alpha", "off", f"PNG24:{path}"],
            input=self.adb("exec-out", "screencap", "-p", capture=True),
            check=True,
        )
        print(path.relative_to(REPO))


# Every setting the script changes, as (namespace, key), so it can put back exactly what was there.
SETTINGS = [
    ("system", "accelerometer_rotation"),
    ("system", "user_rotation"),
    ("secure", "immersive_mode_confirmations"),
    ("global", "sysui_demo_allowed"),
]


def save_display(d):
    """The emulator's display overrides and settings before the run."""
    size = d.adb("shell", "wm", "size", capture=True).decode()
    density = d.adb("shell", "wm", "density", capture=True).decode()
    override_size = re.search(r"Override size: (\d+x\d+)", size)
    override_density = re.search(r"Override density: (\d+)", density)
    return {
        "physical": tuple(map(int, re.search(r"Physical size: (\d+)x(\d+)", size).groups())),
        "size": override_size.group(1) if override_size else "reset",
        "density": override_density.group(1) if override_density else "reset",
        "settings": {
            (ns, key): d.adb("shell", "settings", "get", ns, key, capture=True).decode().strip()
            for ns, key in SETTINGS
        },
    }


def restore_display(d, saved):
    """Puts back exactly what [save_display] found, overrides and all."""
    d.sh("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit")
    d.sh("wm", "size", saved["size"])
    d.sh("wm", "density", saved["density"])
    for (ns, key), value in saved["settings"].items():
        if value == "null":
            d.sh("settings", "delete", ns, key)
        else:
            # adb shell joins its arguments into one command line: quote, or "" vanishes.
            d.sh("settings", "put", ns, key, shlex.quote(value))


def demo_status_bar(d):
    # Landscape hides the bars; skip the one-time "Viewing full screen" hint.
    d.sh("settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    d.sh("settings", "put", "global", "sysui_demo_allowed", "1")

    def demo(*extra):
        d.sh("am", "broadcast", "-a", "com.android.systemui.demo", *extra)

    demo("-e", "command", "enter")
    demo("-e", "command", "clock", "-e", "hhmm", "0912")
    demo("-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false")
    demo("-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4")
    demo("-e", "command", "network", "-e", "mobile", "hide")
    demo("-e", "command", "notifications", "-e", "visible", "false")


# The scenes: realistic work, typed the way a person would.
MORTGAGE = "f 2 30 g n 6.5 g i 350000 PV 0 FV PMT"  # 30-year loan: payment -2,212.24
ROOT_TWO = "f EEX 2 g yx"  # all 34 digits of the square root of 2
IRR = "f 2 25000 CHS g PV 6000 g PMT 8000 g PMT 9000 g PMT 7500 g PMT f FV"  # IRR 8.12 %
TAX_PROGRAM = "f RS f RDN ENTER 8.25 % + g RDN 0 0"  # price plus 8.25 % sales tax
DAYS = "f 0 9.292026 ENTER 12.252026 g EEX"  # days until Christmas
RECEIPT = "f 2 19.99 ENTER 4.5 ENTER 12.75 ENTER 7.25"  # four prices on the stack


def phone(d, out):
    d.fresh(landscape=False)
    d.type(MORTGAGE)
    d.shot(out / "tvm.png")

    d.fresh(landscape=True)
    d.type(ROOT_TWO)
    d.shot(out / "digits.png")

    d.fresh(landscape=True)
    d.type(IRR + " f")
    d.shot(out / "cashflows.png")

    d.fresh(landscape=False)
    d.type(RECEIPT)
    d.shot(out / "stack.png")

    d.fresh(landscape=False, phosphor="ICE")
    d.type(TAX_PROGRAM)
    d.shot(out / "program.png")

    d.fresh(landscape=True, phosphor="P3 AMBER")
    d.type(DAYS)
    d.shot(out / "amber.png")

    d.fresh(landscape=False)
    d.type(MORTGAGE + " MENU")
    d.shot(out / "menu.png")

    d.fresh(landscape=True, phosphor="12C LCD")
    d.type(IRR)
    d.shot(out / "lcd.png")

    # README only: what a first launch looks like.
    d.fresh(landscape=False, tips=True)
    d.shot(out / "tips.png")


def tablet(d, out):
    d.fresh(landscape=True)
    d.type(MORTGAGE)
    d.shot(out / "landscape.png")

    d.fresh(landscape=False)
    d.type(IRR)
    d.shot(out / "portrait.png")

    d.fresh(landscape=True, phosphor="P3 AMBER")
    d.type(ROOT_TWO + " f")
    d.shot(out / "digits.png")

    d.fresh(landscape=False, phosphor="ICE")
    d.type(TAX_PROGRAM)
    d.shot(out / "program.png")

    d.fresh(landscape=False)
    d.type(DAYS + " MENU")
    d.shot(out / "menu.png")

    d.fresh(landscape=True, phosphor="12C LCD")
    d.type(IRR)
    d.shot(out / "lcd.png")


def main():
    if len(sys.argv) != 3 or sys.argv[2] not in DEVICES:
        sys.exit(__doc__)
    which = sys.argv[2]
    d = Device(sys.argv[1])
    (w, h), density = DEVICES[which]
    out = RAW / which
    apk = REPO / "app/build/outputs/apk/debug/app-debug.apk"
    d.adb("install", "-r", str(apk))
    saved = save_display(d)
    # wm size is in the display's natural orientation; a tablet may be landscape-first.
    pw, ph = saved["physical"]
    d.landscape_first = pw > ph
    d.sh("wm", "size", f"{h}x{w}" if d.landscape_first else f"{w}x{h}")
    d.sh("wm", "density", str(density))
    demo_status_bar(d)
    try:
        for old in out.glob("*.png"):
            old.unlink()
        (phone if which == "phone" else tablet)(d, out)
    finally:
        restore_display(d, saved)
    # The README keeps the plain captures; Play gets them captioned.
    if which == "phone":
        for name, src in {
            "portrait.png": "tvm.png", "landscape.png": "digits.png", "f-shift.png": "cashflows.png",
            "program.png": "program.png", "amber.png": "amber.png", "menu.png": "menu.png", "tips.png": "tips.png",
        }.items():
            (DOCS / name).write_bytes((out / src).read_bytes())
    if which == "tab10":
        (DOCS / "tablet.png").write_bytes((out / "landscape.png").read_bytes())
    caption = [sys.executable, str(Path(__file__).with_name("caption.py"))]
    subprocess.run([*caption, which], check=True)
    if which == "phone":
        subprocess.run([*caption, "feature"], check=True)


if __name__ == "__main__":
    main()
