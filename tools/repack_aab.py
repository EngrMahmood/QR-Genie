#!/usr/bin/env python3
"""
Repack an extracted AAB directory back into a .aab ZIP file.

Usage:
  python tools/repack_aab.py D:\QRAPP\app\aab-extract D:\QRAPP\app\release\app-release-patched.aab

This walks the source directory and writes all files into the output ZIP preserving relative paths.
"""
import sys
import os
import zipfile


def repack(src_dir, out_file):
    src_dir = os.path.abspath(src_dir)
    with zipfile.ZipFile(out_file, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(src_dir):
            for f in files:
                abs_path = os.path.join(root, f)
                rel_path = os.path.relpath(abs_path, src_dir).replace('\\', '/')
                zf.write(abs_path, rel_path)


def main():
    if len(sys.argv) < 3:
        print('Usage: repack_aab.py <src-extract-dir> <out-aab>')
        return 1
    src = sys.argv[1]
    out = sys.argv[2]
    if not os.path.isdir(src):
        print('Source directory not found:', src)
        return 2
    repack(src, out)
    print('Wrote', out)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

