#!/usr/bin/env python3
"""Read-only structural verification of a built MineTurn mod JAR.

Standard library only. The tool never builds, launches, extracts into the workspace, or deletes
anything: it opens the given archive read-only and reports what it finds.

Checks performed:
  * ZIP integrity and duplicate entries
  * mod metadata present, declaring the expected mod id, a version and a license
  * every declared Mixin config exists, parses, and its package + class list resolve to classes
  * the resources a mod of this shape must ship (data namespace, lang files, camera config)
  * every JSON member of the archive parses

Exit status is non-zero if any check fails. A pass means the package is structurally sound, not that
the game behaves correctly or that the release is ready.
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import zipfile
import hashlib
from pathlib import Path

# The mod id this repository builds. Metadata is authoritative for everything else (version,
# license, mixin config names), so nothing below hardcodes a version or a test count.
EXPECTED_MOD_ID = "mineturn"
METADATA_PATH = "META-INF/neoforge.mods.toml"

# Resources this mod is expected to ship. Paths, not contents, so a rename shows up as a failure.
REQUIRED_RESOURCES = (
    "assets/mineturn/lang/en_us.json",
    "assets/mineturn/lang/zh_cn.json",
    "assets/mineturn/mineturn/animation_camera.json",
)
REQUIRED_DATA_NAMESPACE = "data/mineturn/"


class Report:
    """Collects check outcomes so the tool can print a full picture before it exits."""

    def __init__(self) -> None:
        self.failures: list[str] = []
        self.notes: list[str] = []

    def fail(self, message: str) -> None:
        self.failures.append(message)

    def note(self, message: str) -> None:
        self.notes.append(message)

    def check(self, condition: bool, message: str) -> bool:
        if not condition:
            self.fail(message)
        return condition


def _toml_string(raw: str) -> str:
    raw = raw.strip()
    if len(raw) >= 6 and raw.startswith("'''") and raw.endswith("'''"):
        return raw[3:-3].strip()
    if len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in "\"'":
        return raw[1:-1]
    return raw


def _strip_comment(line: str) -> str:
    """Drop a trailing ``#`` comment, ignoring ``#`` inside a quoted string.

    ``neoforge.mods.toml`` annotates headers too (``[[mods]] #mandatory``), so a table header must not
    be matched against the raw line.
    """
    quote = None
    for index, character in enumerate(line):
        if quote:
            if character == quote:
                quote = None
        elif character in "\"'":
            quote = character
        elif character == "#":
            return line[:index]
    return line


def read_toml_scalars(text: str) -> dict:
    """Extract scalar keys and [[table]] arrays from a TOML document.

    Deliberately not a general TOML parser: it reads the flat ``key = value`` pairs and the
    ``[[name]]`` array-of-table blocks that ``neoforge.mods.toml`` uses. Reading metadata through it
    keeps the tool dependency-free on Python 3.10, which predates ``tomllib``.
    """
    scalars: dict = {}
    tables: list = []
    current: dict | None = None

    for raw_line in text.splitlines():
        line = _strip_comment(raw_line).strip()
        if not line:
            continue
        header = re.match(r"^\[\[\s*([^\]]+?)\s*\]\]$", line)
        if header:
            current = {"__name__": header.group(1)}
            tables.append(current)
            continue
        header = re.match(r"^\[\s*([^\]]+?)\s*\]$", line)
        if header:
            current = {"__name__": header.group(1)}
            tables.append(current)
            continue
        pair = re.match(r"^([A-Za-z0-9_.\-]+)\s*=\s*(.*)$", line)
        if not pair:
            continue
        key, raw_value = pair.group(1), pair.group(2)
        # A bare triple-quoted description can span lines; keep it out of the scalar map.
        target = current if current is not None else scalars
        target[key] = _toml_string(raw_value)

    return {"scalars": scalars, "tables": tables}


def parse_mods_toml(text: str, report: Report) -> dict:
    """Return {'modId': ..., 'version': ..., 'license': ..., 'mixinConfigs': [...]}."""
    parsed = read_toml_scalars(text)
    result = {"modId": None, "version": None, "license": None, "mixinConfigs": []}

    tables = parsed["tables"]
    mods = [t for t in tables if t["__name__"] == "mods"]
    if mods:
        result["modId"] = mods[0].get("modId")
        result["version"] = mods[0].get("version")
    else:
        report.fail(f"{METADATA_PATH}: no [[mods]] block found")

    result["license"] = parsed["scalars"].get("license")
    for table in tables:
        if table["__name__"] == "mixins" and table.get("config"):
            result["mixinConfigs"].append(table["config"])

    return result


def class_entry_name(package: str, simple_name: str) -> str:
    """Map a mixin's package + simple class name to its JAR entry."""
    dotted = simple_name.replace(".", "/")
    return f"{package.replace('.', '/')}/{dotted}.class"


def check_archive_integrity(archive: zipfile.ZipFile, report: Report) -> None:
    bad = archive.testzip()
    report.check(bad is None, f"archive is corrupt at entry: {bad}")

    names = archive.namelist()
    seen: dict = {}
    for name in names:
        seen[name] = seen.get(name, 0) + 1
    duplicates = sorted(n for n, count in seen.items() if count > 1)
    report.check(not duplicates, f"duplicate archive entries: {', '.join(duplicates[:5])}")


def check_json_members(archive: zipfile.ZipFile, report: Report) -> int:
    checked = 0
    for entry in archive.infolist():
        if entry.is_dir() or not entry.filename.endswith(".json"):
            continue
        checked += 1
        try:
            json.loads(archive.read(entry).decode("utf-8"))
        except UnicodeDecodeError as error:
            report.fail(f"{entry.filename}: not valid UTF-8 ({error})")
        except json.JSONDecodeError as error:
            report.fail(f"{entry.filename}: invalid JSON ({error})")
    report.note(f"JSON members parsed: {checked}")
    return checked


def check_mixins(archive: zipfile.ZipFile, configs: list, report: Report) -> int:
    """Verify each declared mixin config exists, parses, and resolves to classes."""
    names = set(archive.namelist())
    verified = 0

    for config_name in configs:
        if config_name not in names:
            report.fail(f"{METADATA_PATH} declares mixin config {config_name!r}, which is not in the archive")
            continue
        try:
            config = json.loads(archive.read(config_name).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            report.fail(f"{config_name}: unreadable or invalid JSON ({error})")
            continue

        package = config.get("package")
        if not package:
            report.fail(f"{config_name}: missing 'package'")
            continue

        # Both the common list and the client list must resolve; a class in neither is inert.
        simple_names = []
        for key in ("mixins", "client", "server"):
            value = config.get(key, [])
            if isinstance(value, list):
                simple_names.extend(v for v in value if isinstance(v, str))

        if not simple_names:
            report.fail(f"{config_name}: declares no mixin classes")
            continue

        missing = [name for name in simple_names
                   if class_entry_name(package, name) not in names]
        report.check(not missing,
                     f"{config_name}: {len(missing)} declared mixin class(es) missing from the archive, "
                     f"e.g. {', '.join(missing[:3])}")
        verified += len(simple_names)

    report.note(f"mixin classes resolved: {verified}")
    return verified


def check_required_resources(archive: zipfile.ZipFile, report: Report) -> None:
    names = set(archive.namelist())
    for required in REQUIRED_RESOURCES:
        report.check(required in names, f"required resource missing: {required}")

    data_entries = [n for n in names if n.startswith(REQUIRED_DATA_NAMESPACE) and not n.endswith("/")]
    report.check(bool(data_entries),
                 f"no data resources under {REQUIRED_DATA_NAMESPACE} (expected the mod's data pack)")
    if data_entries:
        report.note(f"data resources under {REQUIRED_DATA_NAMESPACE}: {len(data_entries)}")


def verify_jar(path: Path) -> Report:
    report = Report()

    if not path.is_file():
        report.fail(f"not a file: {path}")
        return report

    size = path.stat().st_size
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    report.note(f"jar: {path}")
    report.note(f"size: {size} bytes")
    report.note(f"sha256: {digest}")

    try:
        # Read the bytes once so a corrupt or non-zip file fails cleanly instead of raising.
        archive = zipfile.ZipFile(io.BytesIO(path.read_bytes()))
    except zipfile.BadZipFile as error:
        report.fail(f"not a valid ZIP archive ({error})")
        return report
    except OSError as error:
        report.fail(f"could not read the archive ({error})")
        return report

    with archive:
        check_archive_integrity(archive, report)

        names = set(archive.namelist())
        if METADATA_PATH not in names:
            report.fail(f"missing mod metadata: {METADATA_PATH}")
        else:
            metadata = parse_mods_toml(archive.read(METADATA_PATH).decode("utf-8", errors="replace"), report)
            report.check(metadata["modId"] == EXPECTED_MOD_ID,
                         f"metadata declares mod id {metadata['modId']!r}, expected {EXPECTED_MOD_ID!r}")
            report.check(bool(metadata["version"]), "metadata declares no version")
            report.check(bool(metadata["license"]), "metadata declares no license")
            report.note(f"mod id: {metadata['modId']}  version: {metadata['version']}  license: {metadata['license']}")

            if not metadata["mixinConfigs"]:
                report.fail(f"{METADATA_PATH}: no [[mixins]] config declared")
            check_mixins(archive, metadata["mixinConfigs"], report)

        check_required_resources(archive, report)
        check_json_members(archive, report)

    return report


def render(report: Report, path: Path) -> str:
    lines = [f"verify_release_jar: {path}"]
    for note in report.notes:
        lines.append(f"  {note}")
    for failure in report.failures:
        lines.append(f"  FAIL {failure}")
    if report.failures:
        lines.append(f"RESULT: FAILED ({len(report.failures)} problem(s))")
    else:
        lines.append("RESULT: OK (structure only; this does not prove game behaviour)")
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("jar", help="path to the built mod JAR (read-only)")
    args = parser.parse_args(argv)

    path = Path(args.jar)
    try:
        report = verify_jar(path)
    except OSError as error:
        print(f"verify_release_jar: {path}\n  FAIL {error}\nRESULT: FAILED (1 problem(s))", file=sys.stderr)
        return 2

    print(render(report, path))
    return 1 if report.failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
