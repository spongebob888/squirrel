#!/usr/bin/env python3
"""Fetch ShadowQUIC main (or use a local override) and prepare a build copy."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = "https://github.com/spongebob888/shadowquic.git"
override = os.environ.get("SHADOWQUIC_DIR")
source = Path(override).resolve() if override else ROOT / ".build/shadowquic-upstream"
if not override:
    # This disposable cache is separate from user checkouts and the build copy.
    # Fetch every time so native builds follow GitHub main.
    source.parent.mkdir(parents=True, exist_ok=True)
    if not (source / ".git").exists():
        subprocess.run(["git", "init", str(source)], check=True)
    subprocess.run(["git", "-C", str(source), "fetch", "--depth=1", UPSTREAM,
                    "refs/heads/main"], check=True)
    subprocess.run(["git", "-C", str(source), "checkout", "--detach", "--force",
                    "FETCH_HEAD"], check=True)
    revision = subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    print(f"ShadowQUIC main: {revision}", flush=True)
if not (source / "shadowquic/Cargo.toml").is_file():
    raise SystemExit(f"ShadowQUIC not found at {source}. Set SHADOWQUIC_DIR to its workspace root.")
destination = ROOT / ".build/shadowquic"
files = [p for folder in ["shadowquic", "shadowquic-macros", "perf"]
         for p in (source / folder).rglob("*")
         if p.is_file() and not set(p.relative_to(source).parts) & {"target", ".git"}]
files += [source / name for name in ["Cargo.toml", "Cargo.lock", "README.md", "LICENSE"]
          if (source / name).exists()]
digest = hashlib.sha256()
for p in sorted(files):
    digest.update(str(p.relative_to(source)).encode())
    digest.update(p.read_bytes())
stamp = digest.hexdigest()
marker = destination / ".squirrel-source"
if not marker.exists() or marker.read_text() != stamp:
    shutil.rmtree(destination, ignore_errors=True)
    for p in files:
        target = destination / p.relative_to(source)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, target)
    marker.write_text(stamp)
print(f"ShadowQUIC: {source} (build copy ready)")
