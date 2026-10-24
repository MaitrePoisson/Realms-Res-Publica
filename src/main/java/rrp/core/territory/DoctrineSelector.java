package rrp.core.territory;

import java.util.Collection;
import java.util.Locale;

/** Choisit la doctrine d'après les biomes échantillonnés autour du site de fondation. */
public final class DoctrineSelector {

    private DoctrineSelector() {
    }

    /** Doctrine d'un seul biome (chemin, ex. {@code badlands}), ou {@code null} s'il est indifférent. */
    public static Doctrine ofBiome(String path) {
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.contains("swamp") || p.contains("mangrove") || p.contains("dark_forest") || p.contains("jungle")
                || p.contains("taiga") || p.contains("old_growth") || p.equals("forest") || p.contains("pale_garden")) {
            return Doctrine.ISOLATIONIST_THEOCRACY;
        }
        if (p.contains("badlands") || p.contains("mesa") || p.contains("peaks") || p.contains("mountain")
                || p.contains("windswept") || p.contains("stony") || p.contains("hills") || p.contains("grove")) {
            return Doctrine.WARLIKE_EMPIRE;
        }
        if (p.contains("plains") || p.contains("beach") || p.contains("ocean") || p.contains("river")
                || p.contains("meadow") || p.contains("savanna") || p.contains("desert") || p.contains("cherry")) {
            return Doctrine.MERCHANT_CITY;
        }
        return null;
    }

    /**
     * Doctrine dominante d'un échantillon de biomes ; égalité ou échantillon sans biome marquant :
     * cité marchande (ordre déterministe : marchande, belliqueuse, théocratique).
     */
    public static Doctrine select(Collection<String> biomePaths) {
        int[] votes = new int[Doctrine.values().length];
        if (biomePaths != null) {
            for (String b : biomePaths) {
                Doctrine d = ofBiome(b);
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
