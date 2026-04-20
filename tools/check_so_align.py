import os, struct

root = r"D:\QRAPP\app\aab-extract\base\lib"
if not os.path.isdir(root):
    print("ERROR: directory not found:", root)
    raise SystemExit(1)

def read_p_align(path):
    with open(path,'rb') as f:
        e_ident = f.read(16)
        if len(e_ident) < 16 or e_ident[0:4] != b'\x7fELF':
            return None
        ei_class = e_ident[4]
        ei_data = e_ident[5]
        endian = '<' if ei_data == 1 else '>'
        if ei_class == 1:  # 32-bit
            f.seek(28)
            e_phoff = struct.unpack(endian+'I', f.read(4))[0]
            f.seek(42)
            e_phentsize = struct.unpack(endian+'H', f.read(2))[0]
            e_phnum = struct.unpack(endian+'H', f.read(2))[0]
        elif ei_class == 2:  # 64-bit
            f.seek(32)
            e_phoff = struct.unpack(endian+'Q', f.read(8))[0]
            f.seek(54)
            e_phentsize = struct.unpack(endian+'H', f.read(2))[0]
            e_phnum = struct.unpack(endian+'H', f.read(2))[0]
        else:
            return None
        aligns = []
        for i in range(e_phnum):
            ph_offset = e_phoff + i * e_phentsize
            f.seek(ph_offset)
            if ei_class == 1:
                f.seek(ph_offset + 28)
                p_align = struct.unpack(endian+'I', f.read(4))[0]
            else:
                f.seek(ph_offset + 48)
                p_align = struct.unpack(endian+'Q', f.read(8))[0]
            aligns.append(p_align)
        return aligns

for abi in sorted(os.listdir(root)):
    abi_dir = os.path.join(root, abi)
    if not os.path.isdir(abi_dir):
        continue
    for so in sorted(os.listdir(abi_dir)):
        path = os.path.join(abi_dir, so)
        aligns = read_p_align(path)
        if aligns is None:
            print(f"{abi}/{so}: not an ELF or unreadable")
        else:
            uniq = sorted(set(aligns))
            uniq_str = ', '.join(f"0x{a:x} ({a})" for a in uniq)
            print(f"{abi}/{so}: p_align values = {uniq_str}")

