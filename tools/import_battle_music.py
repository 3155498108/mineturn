"""Import a personal audio file into an instance's MineTurn battle music library."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--game-dir", required=True, type=Path)
    parser.add_argument("--library", default="custom", choices=("custom", "trails", "p5", "p4"))
    parser.add_argument("--situation", required=True, choices=("normal", "advantage", "boss"))
    args = parser.parse_args()
    source = args.source.resolve(strict=True)
    if not source.is_file():
        parser.error("source must be an audio file")
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        parser.error("FFmpeg is required to convert and validate the audio")
    target = args.game_dir.resolve() / "config/mineturn/music" / args.library / (args.situation + ".ogg")
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="mineturn-audio-", dir=target.parent) as temporary:
        converted = Path(temporary) / "converted.ogg"
        subprocess.run([
            ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", str(source),
            "-map", "0:a:0", "-vn", "-c:a", "libvorbis", "-q:a", "5", "-ac", "2", str(converted),
        ], check=True)
        converted.replace(target)
    print(target)
    print("SHA256", hashlib.sha256(target.read_bytes()).hexdigest())
    print("Open MineTurn settings, Music, then Reload local music.")


if __name__ == "__main__":
    main()
