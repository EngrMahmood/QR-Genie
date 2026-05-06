#!/usr/bin/env python3
"""
Search the local Gradle cache for AARs that contain known offending native libraries
and extract those .so files into the app module's `src/main/assets/native/<abi>/` directory.

Usage: python move_native_to_assets.py <app_module_path>

This is a best-effort helper that does not modify AARs in-place. It locates the first
matching .so per ABI and copies it to the app assets so the runtime shim can extract
and dlopen it.
"""
import sys
import os
import zipfile
from pathlib import Path

if len(sys.argv) < 2:
    print("Usage: move_native_to_assets.py <app_module_path>")
    sys.exit(2)

app_dir = Path(sys.argv[1])
if not app_dir.exists():
    print("App module path not found:", app_dir)
    sys.exit(1)

targets = {"libimage_processing_util_jni.so", "libbarhopper_v3.so"}
home = Path.home()
gradle_cache = home / ".gradle" / "caches" / "modules-2" / "files-2.1"
if not gradle_cache.exists():
    print("Gradle cache not found at expected location:", gradle_cache)
    sys.exit(0)

out_root = app_dir / "src" / "main" / "assets" / "native"
out_root.mkdir(parents=True, exist_ok=True)

found = {}

print("Scanning Gradle cache for AARs...")
for root, _, files in os.walk(gradle_cache):
    for f in files:
        if not f.endswith('.aar'):
            continue
        aar_path = Path(root) / f
        try:
            with zipfile.ZipFile(aar_path, 'r') as z:
                for zi in z.namelist():
                    # look for entries like jni/arm64-v8a/libfoo.so
                    if not zi.startswith('jni/'):
                        continue
                    parts = zi.split('/')
                    if len(parts) < 3:
                        continue
                    abi = parts[1]
                    name = parts[-1]
                    if name in targets:
                        key = (abi, name)
                        if key in found:
                            continue
                        # extract into out_root/<abi>/<name>
                        dest_dir = out_root / abi
                        dest_dir.mkdir(parents=True, exist_ok=True)
                        dest_path = dest_dir / name
                        print(f"Extracting {name} (ABI={abi}) from {aar_path} -> {dest_path}")
                        with z.open(zi) as src, open(dest_path, 'wb') as dst:
                            dst.write(src.read())
                        found[key] = str(aar_path)
        except zipfile.BadZipFile:
            continue

if not found:
    print("No offending native libraries found in Gradle cache AARs. You may need to run a build to populate the cache first.")
else:
    print("Extraction complete. Files placed under:", out_root)
    for (abi, name), aar in found.items():
        print(f" - {name} (abi={abi}) from {aar}")

