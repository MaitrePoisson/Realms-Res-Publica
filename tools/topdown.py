#!/usr/bin/env python3
"""Top-down PNG of a world's overworld region files: python3 tools/topdown.py WORLD_DIR OUT.png [cx0 cz0 cx1 cz1]
Reads anvil .mca (zlib chunks), uses the WORLD_SURFACE heightmap and the block palette. Needs Pillow."""
import sys, os, struct, zlib, glob
from PIL import Image

def nbt(b):
    pos = 0
    def rd(t):
        nonlocal pos
        if t == 1: v = struct.unpack_from('>b', b, pos)[0]; pos += 1; return v
        if t == 2: v = struct.unpack_from('>h', b, pos)[0]; pos += 2; return v
        if t == 3: v = struct.unpack_from('>i', b, pos)[0]; pos += 4; return v
        if t == 4: v = struct.unpack_from('>q', b, pos)[0]; pos += 8; return v
        if t == 5: v = struct.unpack_from('>f', b, pos)[0]; pos += 4; return v
        if t == 6: v = struct.unpack_from('>d', b, pos)[0]; pos += 8; return v
        if t == 7: n = struct.unpack_from('>i', b, pos)[0]; pos += 4; v = b[pos:pos+n]; pos += n; return v
        if t == 8: n = struct.unpack_from('>H', b, pos)[0]; pos += 2; v = b[pos:pos+n].decode('utf8', 'replace'); pos += n; return v
        if t == 9:
            it = b[pos]; n = struct.unpack_from('>i', b, pos+1)[0]; pos += 5
            return [rd(it) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = b[pos]; pos += 1
                if tt == 0: return d
                n = struct.unpack_from('>H', b, pos)[0]; pos += 2
                k = b[pos:pos+n].decode(); pos += n
                d[k] = rd(tt)
        if t == 11: n = struct.unpack_from('>i', b, pos)[0]; pos += 4; v = struct.unpack_from('>%di' % n, b, pos); pos += 4*n; return v
        if t == 12: n = struct.unpack_from('>i', b, pos)[0]; pos += 4; v = struct.unpack_from('>%dq' % n, b, pos); pos += 8*n; return v
    assert b[0] == 10
    n = struct.unpack_from('>H', b, 1)[0]; pos = 3 + n
    return rd(10)

def unpack(longs, bits, count):
    per = 64 // bits; mask = (1 << bits) - 1; out = []
    for l in longs:
        l &= (1 << 64) - 1
        for i in range(per):
            if len(out) < count: out.append((l >> (i*bits)) & mask)
    return out

COL = {'grass_block': (95,159,53), 'dirt_path': (148,121,65), 'dirt': (134,96,67), 'cobblestone': (127,127,127), 'stone_bricks': (110,110,110),
       'stone': (125,125,125), 'oak_planks': (162,130,78), 'oak_log': (110,85,50), 'oak_fence': (140,110,60), 'oak_fence_gate': (140,110,60),
       'gold_block': (250,238,77), 'lectern': (150,110,60), 'barrel': (130,95,50), 'water': (50,80,200), 'red_bed': (180,40,40),
       'oak_stairs': (162,130,78), 'oak_slab': (162,130,78), 'stone_brick_wall': (100,100,100), 'cobblestone_wall': (110,110,110),
       'hay_block': (200,170,30), 'farmland': (110,70,30), 'wheat': (200,180,60), 'glass_pane': (200,230,240), 'chest': (160,110,40)}
def color(name, y):
    n = name.split(':')[-1]
    c = COL.get(n)
    if c is None:
        c = (190,60,190) if ('planks' in n or 'log' in n) else (120,120,120)
    k = 0.75 + 0.25 * min(1.0, max(0.0, (y + 64) / 40.0))
    return tuple(int(v * k) for v in c)

def main():
    world, out = sys.argv[1], sys.argv[2]
    chunks = {}
    for f in glob.glob(os.path.join(world, 'region', 'r.*.mca')):
        rx, rz = map(int, os.path.basename(f).split('.')[1:3])
        data = open(f, 'rb').read()
        for i in range(1024):
            off = struct.unpack_from('>I', data, i*4)[0]
            if not off: continue
            sec = off >> 8
            ln = struct.unpack_from('>I', data, sec*4096)[0]
            comp = data[sec*4096+5: sec*4096+4+ln]
            try: chunks[(rx*32 + i % 32, rz*32 + i // 32)] = nbt(zlib.decompress(comp))
            except Exception: pass
    keys = [k for k, c in chunks.items() if c.get('Status', '').endswith('full')]
    if len(sys.argv) >= 7: x0, z0, x1, z1 = map(int, sys.argv[3:7])
    else:
        x0 = min(k[0] for k in keys); x1 = max(k[0] for k in keys); z0 = min(k[1] for k in keys); z1 = max(k[1] for k in keys)
    img = Image.new('RGB', ((x1-x0+1)*16, (z1-z0+1)*16), (20, 20, 20))
    px = img.load()
    for (cx, cz), c in chunks.items():
        if not (x0 <= cx <= x1 and z0 <= cz <= z1): continue
        hm = c.get('Heightmaps', {}).get('WORLD_SURFACE')
        if hm is None: continue
        hs = unpack(hm, 9, 256)
        secs = {s['Y']: s for s in c['sections']}
        for i in range(256):
            y = hs[i] - 1 - 64 + 0  # stored relative to min_y (-64)
            sy = (y) >> 4
            s = secs.get(sy)
            if not s or 'block_states' not in s: continue
            bs = s['block_states']; pal = bs['palette']
            if len(pal) == 1: nm = pal[0]['Name']
            else:
                bits = max(4, (len(pal) - 1).bit_length())
                idx = unpack(bs['data'], bits, 4096)
                lx, lz = i % 16, i // 16
                nm = pal[idx[((y & 15) << 8) | (lz << 4) | lx]]['Name']
            px[(cx-x0)*16 + i % 16, (cz-z0)*16 + i // 16] = color(nm, y)
    img = img.resize((img.width*2, img.height*2), Image.NEAREST)
    img.save(out); print('wrote', out, img.size)
main()
