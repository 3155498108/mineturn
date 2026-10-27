"""Materialize Fabric version patches against shared sources using strict git apply.

Explicit developer commands only. Never edits shared source or ordinary game saves.
"""
from __future__ import annotations

import argparse
import difflib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
VERSIONS = ("1.20.1", "1.21", "1.21.8", "1.21.10")
EXCLUDED = {
    "MineTurn.java", "RuntimePlatform.java", "NeoForgePayloads.java",
    "NeoForgeClient.java", "NeoForgeBattleEvents.java", "LivingMixin.java",
    "StatusClockMixin.java", "BattleCameraMixin.java",
}


def baseline() -> dict[str, str]:
    result = {}
    for source, shared in ((ROOT / "src/main/java", True), (ROOT / "fabric/src/main/java", False)):
        for path in sorted(source.rglob("*.java")):
            relative = path.relative_to(source).as_posix()
            if path.name.endswith("GameTests.java") or "/test/" in relative:
                continue
            if shared and path.name in EXCLUDED:
                continue
            result[relative] = path.read_text(encoding="utf-8")
    return result


def destination(version: str) -> Path:
    return ROOT / "fabric/build/ports" / version / "generated/java"


def prepare(version: str) -> None:
    target = destination(version)
    sources = baseline()
    # Only this tool's generated source tree is replaceable. Never follow links.
    if target.resolve() != target.absolute():
        raise ValueError(f"Unsafe generated source path: {target}")
    if target.exists():
        if target.is_symlink() or target.resolve() != target.absolute():
            raise ValueError(f"Unsafe generated source path: {target}")
        for path in target.rglob("*"):
            if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
                raise ValueError(f"Unexpected link in generated sources: {path}")
        shutil.rmtree(target)
    for name, content in sources.items():
        path = target / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
    patch = ROOT / "fabric/ports" / f"{version}.patch"
    if patch.exists() and patch.stat().st_size:
        subprocess.run(["git", "apply", "--whitespace=error", "--directory=" + target.relative_to(ROOT).as_posix(), str(patch)], cwd=ROOT, check=True)
    print(f"Prepared {version}: {len(sources)} shared/loader Java sources, strict version patch applied")


def capture(version: str) -> None:
    target = destination(version)
    if not target.is_dir():
        raise ValueError("Run prepare before capturing edited generated sources")
    original = baseline()
    current = {p.relative_to(target).as_posix(): p.read_text(encoding="utf-8") for p in target.rglob("*.java")}
    chunks = []
    for name in sorted(original.keys() | current.keys()):
        before, after = original.get(name, ""), current.get(name, "")
        if before == after:
            continue
        chunks.extend(difflib.unified_diff(before.splitlines(keepends=True), after.splitlines(keepends=True), fromfile="a/" + name if name in original else "/dev/null", tofile="b/" + name if name in current else "/dev/null"))
    patch = ROOT / "fabric/ports" / f"{version}.patch"
    patch.parent.mkdir(parents=True, exist_ok=True)
    patch.write_text("".join(chunks), encoding="utf-8", newline="\n")
    print(f"Captured {version}: {len(chunks)} diff lines")


def resources(version: str, target: Path | None = None) -> None:
    if target is None:
        target = ROOT / "fabric/build/ports" / version / "resources/main"
    elif version != "1.20.1" or target != ROOT / "forge/build/resources/main":
        raise ValueError(f"Unsupported resource destination: {target}")
    if not target.is_dir() or target.resolve() != target.absolute():
        raise ValueError(f"Unsafe generated resource directory: {target}")
    if version == "1.20.1":
        unavailable = (
            "mobs/breeze", "actions/breeze_burst", "actions/wind_burst",
            "actions/wolf_armor_repair", "items/wind_charge", "items/wolf_armor_repair",
        )
        for name in unavailable:
            (target / "data/mineturn/mineturn" / (name + ".json")).unlink(missing_ok=True)
        for path in (target / "data/mineturn/mineturn/mobs").glob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            match = data.get("match", {})
            if "entities" in match:
                match["entities"] = [entity for entity in match["entities"] if entity not in ("minecraft:armadillo", "minecraft:breeze", "minecraft:bogged")]
                path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        for path in list((target / "data").rglob("*")):
            if not path.is_file():
                continue
            parts = path.relative_to(target / "data").parts
            renamed = ["functions" if x == "function" else "structures" if x == "structure" else "entity_types" if x == "entity_type" else x for x in parts]
            dest = target / "data" / Path(*renamed)
            if dest != path:
                dest.parent.mkdir(parents=True, exist_ok=True)
                path.replace(dest)
        for path in (target / "data/mineturn/functions").rglob("*.mcfunction"):
            raw = path.read_text(encoding="utf-8")
            backup = target / "data/mineturn/mineturn_legacy_functions" / path.relative_to(target / "data/mineturn/functions")
            backup.parent.mkdir(parents=True, exist_ok=True)
            backup.write_text(raw, encoding="utf-8", newline="\n")
            if "return" in raw or "$" in raw:
                path.write_text("# Run by MineTurn's bounded legacy function interpreter.\n", encoding="utf-8", newline="\n")
        for path in (target / "data").glob("*/tags/**/*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            data["values"] = [v for v in data.get("values", []) if v not in ("minecraft:armadillo", "minecraft:breeze", "minecraft:bogged")]
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    if version in ("1.21.8", "1.21.10"):
        model = target / "assets/mineturn/items/companion_token.json"
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text('{"model":{"type":"minecraft:model","model":"mineturn:item/companion_token"}}\n', encoding="utf-8")
    if version == "1.21.10":
        for path in (target / "assets/mineturn/lang").glob("*.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            data["key.category.mineturn.combat"] = data.get("key.categories.mineturn", "MineTurn")
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Prepared native resources for Minecraft {version}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "capture", "resources"))
    parser.add_argument("version", choices=VERSIONS)
    args = parser.parse_args()
    {"prepare": prepare, "capture": capture, "resources": resources}[args.action](args.version)


if __name__ == "__main__":
    main()
