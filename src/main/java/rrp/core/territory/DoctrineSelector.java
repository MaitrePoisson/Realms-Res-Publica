package rrp.core.territory;

import java.util.Collection;

/** Choisit la doctrine d'après les biomes échantillonnés autour du site de fondation. */
public final class DoctrineSelector {

    private DoctrineSelector() {
    }

    /** Doctrine of one biome (id or path, any namespace), or {@code null} when it gives no hint. */
    public static Doctrine ofBiome(String id) {
        return ofKind(BiomeClassifier.classify(id).kind());
    }

    public static Doctrine ofKind(BiomeClassifier.Kind kind) {
        return switch (kind) {
            case SWAMP, JUNGLE, TAIGA, FOREST -> Doctrine.ISOLATIONIST_THEOCRACY;
            case BADLANDS, MOUNTAIN -> Doctrine.WARLIKE_EMPIRE;
            case PLAINS, BEACH, OCEAN, RIVER, SAVANNA, DESERT -> Doctrine.MERCHANT_CITY;
            case UNKNOWN -> null;
        };
    }

    /**
     * Doctrine dominante d'un échantillon de biomes ; égalité ou échantillon sans biome marquant :
     * cité marchande (ordre déterministe : marchande, belliqueuse, théocratique).
     */
    public static Doctrine select(Collection<String> biomePaths) {
        return selectKinds(biomePaths == null ? null
                : biomePaths.stream().map(b -> BiomeClassifier.classify(b).kind()).toList());
    }

    /** Same vote on already classified biomes. */
    public static Doctrine selectKinds(Collection<BiomeClassifier.Kind> kinds) {
        int[] votes = new int[Doctrine.values().length];
        if (kinds != null) {
            for (BiomeClassifier.Kind k : kinds) {
                Doctrine d = ofKind(k);
                if (d != null) {
                    votes[d.ordinal()]++;
                }
            }
        }
        Doctrine best = Doctrine.MERCHANT_CITY;
        for (Doctrine d : Doctrine.values()) {
            if (votes[d.ordinal()] > votes[best.ordinal()]) {
                best = d;
            }
        }
        return best;
    }
}
