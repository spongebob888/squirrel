#!/usr/bin/env python3
"""Resolve the locked ShadowQUIC Git dependency and print its workspace root."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
metadata = json.loads(subprocess.check_output(
    ["cargo", "metadata", "--manifest-path", str(ROOT / "native/Cargo.toml"),
     "--format-version", "1", "--locked"], text=True, cwd=ROOT))
core = next(package for package in metadata["packages"] if package["name"] == "shadowquic")
print(Path(core["manifest_path"]).parent.parent)
