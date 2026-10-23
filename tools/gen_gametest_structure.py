"""Generates the GameTest arena template (stone floor, air above) as a gzip NBT structure file."""
import gzip, struct, sys

N, H = 24, 10

def s(t): b = t.encode(); return struct.pack(">H", len(b)) + b
def named(tag, name, payload): return bytes([tag]) + s(name) + payload
def i(v): return struct.pack(">i", v)
def lst(tag, items): return bytes([tag]) + i(len(items)) + b"".join(items)

palette = lst(10, [named(8, "Name", s("minecraft:stone")) + b"\0", named(8, "Name", s("minecraft:air")) + b"\0"])
blocks = []
for x in range(N):
    for z in range(N):
        blocks.append(named(3, "state", i(0)) + named(9, "pos", lst(3, [i(x), i(0), i(z)])) + b"\0")
root = (named(3, "DataVersion", i(3955)) + named(9, "size", lst(3, [i(N), i(H), i(N)]))
        + named(9, "palette", palette) + named(9, "blocks", lst(10, blocks))
        + named(9, "entities", lst(10, [])) + b"\0")
out = sys.argv[1]
with gzip.open(out, "wb") as f:
    f.write(b"\x0a" + s("") + root)
print("wrote", out)
