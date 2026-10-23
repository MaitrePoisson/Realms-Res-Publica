"""Generates the building blueprint pool (7 kinds x 5 biome families) as gzip NBT structure files.
Usage: python tools/gen_blueprints.py [output_dir]   (default: src/main/resources/data/respublica/structure)
Deterministic: same output on every run. Origin y=0 is the floor layer; the door is in the z=0 wall."""
import gzip, os, struct, sys

def s(t): b = t.encode(); return struct.pack(">H", len(b)) + b
def named(tag, name, payload): return bytes([tag]) + s(name) + payload
def i(v): return struct.pack(">i", v)
def lst(tag, items): return bytes([tag]) + i(len(items)) + b"".join(items)

STYLES = {
    "plains":  dict(log="oak_log", plank="oak_planks", stone="cobblestone", slab="oak_slab", fence="oak_fence"),
    "desert":  dict(log="cut_sandstone", plank="smooth_sandstone", stone="sandstone", slab="sandstone_slab", fence="birch_fence"),
    "snow":    dict(log="spruce_log", plank="spruce_planks", stone="stone_bricks", slab="spruce_slab", fence="spruce_fence"),
    "savanna": dict(log="acacia_log", plank="acacia_planks", stone="cobblestone", slab="acacia_slab", fence="acacia_fence"),
    "jungle":  dict(log="jungle_log", plank="jungle_planks", stone="mossy_cobblestone", slab="jungle_slab", fence="jungle_fence"),
}

class Bp:
    def __init__(self, sx, sy, sz, st):
        self.size = (sx, sy, sz); self.st = st; self.b = {}
    def name(self, n): return n if ":" in n else "minecraft:" + n
    def put(self, x, y, z, block, **props):
        block = self.st.get(block, block)
        self.b[(x, y, z)] = (self.name(block), tuple(sorted(props.items())))
    def fill(self, x1, y1, z1, x2, y2, z2, block, **props):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1): self.put(x, y, z, block, **props)
    def write(self, path):
        pal, idx, blocks = [], {}, []
        for pos in sorted(self.b, key=lambda p: (p[1], p[0], p[2])):
            key = self.b[pos]
            if key not in idx:
                idx[key] = len(pal)
                e = named(8, "Name", s(key[0]))
                if key[1]:
                    e += named(10, "Properties", b"".join(named(8, k, s(str(v))) for k, v in key[1]) + b"\0")
                pal.append(e + b"\0")
            blocks.append(named(3, "state", i(idx[key])) + named(9, "pos", lst(3, [i(c) for c in pos])) + b"\0")
        root = (named(3, "DataVersion", i(3955)) + named(9, "size", lst(3, [i(c) for c in self.size]))
                + named(9, "palette", lst(10, pal)) + named(9, "blocks", lst(10, blocks))
                + named(9, "entities", lst(10, [])) + b"\0")
        with gzip.GzipFile(path, "wb", mtime=0) as f:
            f.write(b"\x0a" + s("") + root)

def hut(st, w, d, h, beds):
    """Walled house: log corners, plank walls, slab roof, door gap at z=0, glass windows, beds along the back."""
    b = Bp(w, h + 2, d, st); cx = w // 2
    b.fill(0, 0, 0, w - 1, 0, d - 1, "plank")
    b.fill(0, 1, 0, w - 1, h, d - 1, "plank")
    b.fill(1, 1, 1, w - 2, h, d - 2, "minecraft:air")
    for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
        b.fill(x, 1, z, x, h, z, "log", **({} if st["log"] == "cut_sandstone" else {"axis": "y"}))
    b.fill(cx, 1, 0, cx, 2, 0, "minecraft:air")
    for y in (2,):
        for x in (2, w - 3):
            b.put(x, y, d - 1, "glass_pane"); b.put(x, y, 0, "glass_pane")
        b.put(0, y, d // 2, "glass_pane"); b.put(w - 1, y, d // 2, "glass_pane")
    b.fill(0, h + 1, 0, w - 1, h + 1, d - 1, "slab")
    b.put(cx, 3, 1, "torch")
    for k in range(beds):
        x = 1 + 2 * k
        if x > w - 2: break
        b.put(x, 1, d - 3, "red_bed", facing="south", part="foot", occupied="false")
        b.put(x, 1, d - 2, "red_bed", facing="south", part="head", occupied="false")
    return b

def farm(st):
    b = Bp(9, 3, 9, st)
    b.fill(0, 0, 0, 8, 0, 8, "dirt")
    b.fill(1, 0, 1, 7, 0, 7, "farmland", moisture=7)
    b.put(4, 0, 4, "water")
    for x in range(1, 8):
        for z in range(1, 8):
            if (x, z) != (4, 4): b.put(x, 1, z, "wheat", age=0)
    for k in range(9):
        for x, z in ((k, 0), (k, 8), (0, k), (8, k)):
            if (x, z) != (4, 0): b.put(x, 1, z, "fence")
    b.put(4, 1, 0, "minecraft:air"); b.put(0, 2, 0, "torch"); b.put(8, 2, 8, "torch")
    return b

def workshop(st):
    b = Bp(7, 6, 7, st)
    b.fill(0, 0, 0, 6, 0, 6, "stone")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)): b.fill(x, 1, z, x, 4, z, "log")
    b.fill(0, 1, 6, 6, 4, 6, "stone"); b.fill(0, 1, 0, 0, 4, 6, "stone"); b.fill(6, 1, 0, 6, 4, 6, "stone")
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)): b.fill(x, 1, z, x, 4, z, "log")
    b.fill(1, 1, 1, 5, 4, 5, "minecraft:air"); b.fill(1, 1, 0, 5, 3, 0, "minecraft:air")
    b.fill(0, 5, 0, 6, 5, 6, "slab")
    b.put(1, 1, 5, "furnace", facing="south", lit="false"); b.put(2, 1, 5, "crafting_table")
    b.put(5, 1, 5, "anvil", facing="east"); b.put(4, 1, 5, "barrel", facing="up")
    b.put(3, 3, 1, "torch")
    return b

def tower(st):
    b = Bp(5, 11, 5, st)
    b.fill(0, 0, 0, 4, 0, 4, "stone")
    b.fill(0, 1, 0, 4, 6, 4, "stone"); b.fill(1, 1, 1, 3, 6, 3, "minecraft:air")
    b.fill(2, 1, 0, 2, 2, 0, "minecraft:air")
    for y in range(1, 7): b.put(2, y, 1, "ladder", facing="south")
    b.fill(0, 7, 0, 4, 7, 4, "plank"); b.put(2, 7, 1, "minecraft:air")
    b.fill(0, 8, 0, 4, 8, 4, "fence"); b.fill(1, 8, 1, 3, 8, 3, "minecraft:air")
    b.fill(0, 9, 0, 4, 9, 4, "minecraft:air")
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)): b.put(x, 9, z, "torch")
    b.put(2, 3, 4, "glass_pane"); b.put(0, 3, 2, "glass_pane"); b.put(4, 3, 2, "glass_pane")
    b.fill(0, 9, 0, 4, 9, 4, "minecraft:air")
    for x, z in ((0, 0), (4, 0), (0, 4), (4, 4)): b.put(x, 9, z, "torch")
    return b

def wall(st):
    b = Bp(5, 4, 1, st)
    b.fill(0, 0, 0, 4, 3, 0, "stone"); b.fill(0, 4 - 1, 0, 4, 3, 0, "slab")
    return b

def gate(st):
    b = Bp(5, 5, 1, st)
    b.fill(0, 0, 0, 4, 4, 0, "stone")
    b.fill(1, 0, 0, 3, 2, 0, "minecraft:air")
    b.fill(0, 0, 0, 0, 4, 0, "log", **({} if st["log"] == "cut_sandstone" else {"axis": "y"}))
    b.fill(4, 0, 0, 4, 4, 0, "log", **({} if st["log"] == "cut_sandstone" else {"axis": "y"}))
    b.fill(1, 3, 0, 3, 3, 0, "plank"); b.put(1, 2, 0, "torch"); b.put(3, 2, 0, "torch")
    return b

KINDS = {
    "house": lambda st: hut(st, 7, 7, 4, 2),
    "big_house": lambda st: hut(st, 9, 9, 4, 4),
    "farm": farm, "workshop": workshop, "watchtower": tower, "wall_segment": wall, "gate": gate,
}

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join("src", "main", "resources", "data", "respublica", "structure")
    os.makedirs(out, exist_ok=True)
    for kind, fn in KINDS.items():
        for fam, st in STYLES.items():
            fn(st).write(os.path.join(out, f"{kind}_{fam}.nbt"))
    print("wrote", len(KINDS) * len(STYLES), "blueprints to", out)
