package rrp.core.territory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carte d'influence : chaque marqueur (borne, bannière, Hôtel de Ville, avant-poste) irradie une
 * influence qui décroît linéairement jusqu'à son rayon (en chunks). Le propriétaire d'un chunk est la
 * faction dont l'influence cumulée est la plus forte, si elle dépasse le seuil ; le chunk est
 * contesté si une autre faction reste à moins de {@code contestMargin} du meilleur score.
 * Déterministe : égalité départagée par l'ordre alphabétique de l'identifiant.
 */
public final class InfluenceMap {

    /** Marqueur de territoire d'une faction. */
    public record Marker(String id, String faction, ChunkPos pos, int radius, double strength) {
        double influenceAt(ChunkPos c) {
            double d = pos.distance(c);
            if (d > radius) {
                return 0;
            }
            return strength * (1.0 - d / (radius + 1.0));
        }
    }

    private final Map<String, Marker> markers = new LinkedHashMap<>();
    private final double ownerThreshold;
    private final double contestMargin;

    public InfluenceMap(double ownerThreshold, double contestMargin) {
        this.ownerThreshold = ownerThreshold;
        this.contestMargin = contestMargin;
    }

    public void put(Marker m) {
        markers.put(m.id(), m);
    }

    public boolean remove(String markerId) {
        return markers.remove(markerId) != null;
    }

    public List<Marker> markers() {
        return List.copyOf(markers.values());
    }

    /** Influence cumulée de chaque faction sur un chunk. */
    public Map<String, Double> influences(ChunkPos c) {
        Map<String, Double> m = new HashMap<>();
        for (Marker mk : markers.values()) {
            double v = mk.influenceAt(c);
            if (v > 0) {
                m.merge(mk.faction(), v, Double::sum);
            }
        }
        return m;
    }

    public Claim claim(ChunkPos c) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(influences(c).entrySet());
        sorted.sort((a, b) -> {
            int cmp = Double.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });
        if (sorted.isEmpty() || sorted.get(0).getValue() < ownerThreshold) {
            return Claim.FREE;
        }
        var best = sorted.get(0);
        if (sorted.size() > 1) {
            var second = sorted.get(1);
            if (second.getValue() >= ownerThreshold && best.getValue() - second.getValue() <= contestMargin) {
                return new Claim(best.getKey(), best.getValue(), second.getKey(), second.getValue());
            }
        }
        return new Claim(best.getKey(), best.getValue(), null, 0);
    }

    public String owner(ChunkPos c) {
        return claim(c).owner();
    }

    /** Chunks possédés par une faction dans un rectangle donné (bornes incluses). */
    public List<ChunkPos> claimedBy(String faction, int minX, int minZ, int maxX, int maxZ) {
        List<ChunkPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos c = new ChunkPos(x, z);
                if (faction.equals(owner(c))) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
