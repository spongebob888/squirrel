#!/usr/bin/env python3
"""Copy ShadowQUIC for builds, accepting checkouts with Android support applied."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
source = Path(os.environ.get("SHADOWQUIC_DIR", ROOT.parent / "shadowquic")).resolve()
if not (source / "shadowquic/Cargo.toml").is_file():
    raise SystemExit(f"ShadowQUIC not found at {source}. Set SHADOWQUIC_DIR to its workspace root.")
destination = ROOT / ".build/shadowquic"
patch = ROOT / "patches/android-tun.patch"
files = [p for folder in ["shadowquic", "shadowquic-macros", "perf"]
         for p in (source / folder).rglob("*")
         if p.is_file() and not set(p.relative_to(source).parts) & {"target", ".git"}]
files += [source / name for name in ["Cargo.toml", "Cargo.lock", "README.md", "LICENSE"]
          if (source / name).exists()]
digest = hashlib.sha256(patch.read_bytes())
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
    already_patched = subprocess.run(
        ["patch", "--batch", "--force", "--reverse", "--dry-run", "-p1", "-i", str(patch)],
        cwd=destination, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).returncode == 0
    # The upstream checkout can contain later Android-specific refactors that
    # preserve the patch's behavior but no longer match its exact diff context.
    if not already_patched:
        config = (destination / "shadowquic/src/tun/config.rs").read_text()
        tun = (destination / "shadowquic/src/tun/mod.rs").read_text()
        already_patched = (
            "pub android_fd: Option<std::sync::Arc<std::os::fd::OwnedFd>>" in config
            and '#[cfg(target_os = "android")]' in tun
            and ".android_fd" in tun
        )
    if not already_patched:
        subprocess.run(["patch", "--batch", "--forward", "-p1", "-i", str(patch)],
                       cwd=destination, check=True)
    marker.write_text(stamp)
print(f"ShadowQUIC: {source} (build copy ready)")
