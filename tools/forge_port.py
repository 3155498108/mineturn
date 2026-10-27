"""Prepare the isolated 1.20.1 Forge port from the existing shared version patch."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import subprocess

import fabric_ports

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.20.1"
GENERATED = ROOT / "forge/build/generated/java"
FORGE_RESOURCES = ROOT / "forge/build/resources/main"
REPLACED = (
    "com/matuvent/mineturn/MineTurn.java",
    "com/matuvent/mineturn/platform/RuntimePlatform.java",
    "com/matuvent/mineturn/platform/FabricPayloads.java",
    "com/matuvent/mineturn/client/FabricClient.java",
    "com/matuvent/mineturn/client/FabricClientPayloads.java",
)


def prepare() -> None:
    target = GENERATED
    # This exact generated directory is the only replaceable tree. Do not follow links.
    if target.resolve() != target.absolute() or not target.is_relative_to(ROOT / "forge/build"):
        raise ValueError(f"Unsafe generated source path: {target}")
    if target.exists():
        for path in (target, *target.rglob("*")):
            if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
                raise ValueError(f"Unexpected link in generated sources: {path}")
        shutil.rmtree(target)
    for name, content in fabric_ports.baseline().items():
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
    patch = ROOT / "fabric/ports" / f"{VERSION}.patch"
    subprocess.run(["git", "apply", "--whitespace=error", "--directory=" + target.relative_to(ROOT).as_posix(), str(patch)], cwd=ROOT, check=True)
    for name in REPLACED:
        (target / name).unlink()
    # The developer-only companion fixture changes the server's selected hotbar
    # slot. Forge 1.20.1 must also notify the client or a copied QA world can
    # keep its previous selection and the client assertion will wait forever.
    scenario = target / "com/matuvent/mineturn/battle/TrailsCompanionScenario.java"
    content = scenario.read_text(encoding="utf-8")
    selected = "p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(p.getInventory().selected));"
    for before, after in (
        ("p.inventoryMenu.broadcastChanges();ready=true;", selected + "p.inventoryMenu.broadcastChanges();ready=true;"),
        ("p.getInventory().selected=2;p.inventoryMenu.broadcastChanges();", "p.getInventory().selected=2;" + selected + "p.inventoryMenu.broadcastChanges();"),
    ):
        if content.count(before) != 1:
            raise ValueError("Unexpected companion QA fixture; cannot sync its selected slot")
        content = content.replace(before, after, 1)
    scenario.write_text(content, encoding="utf-8", newline="\n")
    print("Prepared Forge 1.20.1 shared sources with the existing strict version patch")


def resources() -> None:
    fabric_ports.resources(VERSION, FORGE_RESOURCES)
    config = json.loads((ROOT / "fabric/src/main/resources/mineturn.fabric.mixins.json").read_text(encoding="utf-8"))
    extras = json.loads((ROOT / "fabric/ports/1.20.1.mixin-extra.json").read_text(encoding="utf-8"))
    for side, names in extras.items():
        config.setdefault(side, []).extend(names)
    # Forge attaches attributes through its native registration event.
    config["mixins"] = [name for name in config["mixins"] if name not in ("FabricAttributeMapMixin", "FabricAttributeSupplierMixin")]
    config["minVersion"] = "0.8"
    config["compatibilityLevel"] = "JAVA_17"
    config["refmap"] = "mineturn.forge.refmap.json"
    (FORGE_RESOURCES / "mineturn.forge.mixins.json").write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("Prepared Forge 1.20.1 resources and mixin configuration")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "resources"))
    args = parser.parse_args()
    {"prepare": prepare, "resources": resources}[args.action]()


if __name__ == "__main__":
    main()
