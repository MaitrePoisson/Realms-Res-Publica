package rrp.core.territory;

/** Position d'un chunk (coordonnées de chunk, pas de bloc). */
public record ChunkPos(int x, int z) {

    public static ChunkPos ofBlock(int blockX, int blockZ) {
        return new ChunkPos(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    public double distance(ChunkPos o) {
        return Math.hypot(x - o.x, z - o.z);
    }
}
