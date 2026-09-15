"""Make MaLiLib usable in the SchemaForge dev client (./gradlew runClient).

Why: MaLiLib's mixin test.MixinSharedConstants sets vanilla SharedConstants.IS_RUNNING_IN_IDE to true whenever
it detects a development environment (fabric.development=true or the dev launch injector). That turns on
Mojang's GPU validation (GlRenderPass.VALIDATION), and Minecraft 26.2 then fails its own check on the first
client tick: TextureAtlas animation draws before GameRenderer has created the "Globals" uniform buffer
("Missing uniform Globals (should be UNIFORM_BUFFER)"). Normal launcher instances are not affected.
Full analysis: docs/NOTES-devclient-crash.md.

What: copies run/mods/malilib-<version>.jar to malilib-<version>-devpatch.jar with that single mixin removed
from mixins.malilib.json, and renames the original to *.jar.disabled so Fabric does not load both.
Only for the local, gitignored run/ folder; never ship the patched jar.

Usage:
    python tools/patch_malilib_dev.py            # patches the malilib jar in run/mods
    python tools/patch_malilib_dev.py --restore  # removes the patched copy, re-enables the original
"""

import json
import sys
import zipfile
from pathlib import Path

MODS = Path(__file__).resolve().parent.parent / "run" / "mods"
MIXIN_CONFIG = "mixins.malilib.json"
REMOVED_MIXIN = "test.MixinSharedConstants"
SUFFIX = "-devpatch.jar"


def originals():
    return [p for p in MODS.glob("malilib-*.jar") if not p.name.endswith(SUFFIX)]


def patch(original: Path) -> Path:
    target = original.with_name(original.stem + SUFFIX)
    with zipfile.ZipFile(original) as zin, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == MIXIN_CONFIG:
                config = json.loads(data)
                if REMOVED_MIXIN not in config.get("mixins", []):
                    raise SystemExit(f"{original.name}: {REMOVED_MIXIN} not found, patch not needed or MaLiLib changed")
                config["mixins"] = [m for m in config["mixins"] if m != REMOVED_MIXIN]
                data = json.dumps(config, indent=4).encode("utf-8")
            zout.writestr(item, data)
    original.rename(original.with_name(original.name + ".disabled"))
    return target


def restore():
    for patched in MODS.glob("malilib-*" + SUFFIX):
        patched.unlink()
        print(f"removed {patched.name}")
    for disabled in MODS.glob("malilib-*.jar.disabled"):
        disabled.rename(disabled.with_name(disabled.name.removesuffix(".disabled")))
        print(f"re-enabled {disabled.name.removesuffix('.disabled')}")


def main():
    if "--restore" in sys.argv:
        restore()
        return
    found = originals()
    if not found:
        raise SystemExit(f"no malilib-*.jar in {MODS} (already patched?)")
    for original in found:
        print(f"patched {patch(original).name} (original kept as {original.name}.disabled)")


if __name__ == "__main__":
    main()
