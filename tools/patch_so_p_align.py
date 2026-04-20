#!/usr/bin/env python3
"""
Patch ELF program header p_align values for .so files under the aab-extract directory.

Usage:
  python tools/patch_so_p_align.py

This script will walk D:\QRAPP\app\aab-extract\base\lib and set every program header's
p_align to 0x4000 (16384). It edits files in-place and prints before/after values.

WARNING: Modifying ELF headers is a binary-level change. Test thoroughly before uploading.
Prefer vendor-provided rebuilt libraries when possible. This is a pragmatic quick-fix.
"""
import os
import struct

ROOT = r"D:\QRAPP\app\aab-extract\base\lib"
TARGET_ALIGN = 0x4000


def read_p_aligns(path):
    with open(path, 'rb') as f:
        e_ident = f.read(16)
        if len(e_ident) < 16 or e_ident[0:4] != b'\x7fELF':
            return None
        ei_class = e_ident[4]
        ei_data = e_ident[5]
        endian = '<' if ei_data == 1 else '>'
        if ei_class == 1:
            f.seek(28)
            e_phoff = struct.unpack(endian + 'I', f.read(4))[0]
            f.seek(42)
            e_phentsize = struct.unpack(endian + 'H', f.read(2))[0]
            e_phnum = struct.unpack(endian + 'H', f.read(2))[0]
        elif ei_class == 2:
            f.seek(32)
            e_phoff = struct.unpack(endian + 'Q', f.read(8))[0]
            f.seek(54)
            e_phentsize = struct.unpack(endian + 'H', f.read(2))[0]
            e_phnum = struct.unpack(endian + 'H', f.read(2))[0]
        else:
            return None
        aligns = []
        for i in range(e_phnum):
            ph_offset = e_phoff + i * e_phentsize
            f.seek(ph_offset)
            if ei_class == 1:
                f.seek(ph_offset + 28)
                p_align = struct.unpack(endian + 'I', f.read(4))[0]
            else:
                f.seek(ph_offset + 48)
                p_align = struct.unpack(endian + 'Q', f.read(8))[0]
            aligns.append(p_align)
        return ei_class, ei_data, aligns, e_phoff, e_phentsize, e_phnum


def patch_p_align(path, target=TARGET_ALIGN):
    info = read_p_aligns(path)
    if info is None:
        return False, None
    ei_class, ei_data, aligns, e_phoff, e_phentsize, e_phnum = info
    endian = '<' if ei_data == 1 else '>'
    before = sorted(set(aligns))
    with open(path, 'r+b') as f:
        for i in range(e_phnum):
            ph_offset = e_phoff + i * e_phentsize
            if ei_class == 1:
                p_align_off = ph_offset + 28
                f.seek(p_align_off)
                f.write(struct.pack(endian + 'I', target))
            else:
                p_align_off = ph_offset + 48
                f.seek(p_align_off)
                f.write(struct.pack(endian + 'Q', target))
    # re-read
    info2 = read_p_aligns(path)
    after = sorted(set(info2[2])) if info2 else None
    return True, (before, after)


def main():
    if not os.path.isdir(ROOT):
        print('ERROR: directory not found:', ROOT)
        return 1
    changed = []
    for abi in sorted(os.listdir(ROOT)):
        abi_dir = os.path.join(ROOT, abi)
        if not os.path.isdir(abi_dir):
            continue
        for so in sorted(os.listdir(abi_dir)):
            path = os.path.join(abi_dir, so)
            ok, res = patch_p_align(path)
            if not ok:
                print(f"{abi}/{so}: not an ELF or unreadable")
                continue
            before, after = res
            if before != after:
                print(f"{abi}/{so}: patched p_align {before} -> {after}")
                changed.append(path)
            else:
                print(f"{abi}/{so}: already {after}")
    print(f"Patched {len(changed)} files")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

