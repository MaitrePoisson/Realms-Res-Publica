package rrp.neoforge;

import rrp.core.SimConfig;
import rrp.core.diplomacy.RelationMatrix;
import rrp.core.territory.FactionRelations.Stance;

/** Matrice relationnelle vivante entre les villages : dérive en temps réel, nourrie par les incidents et les visites. */
public final class RelationService {

    private static final RelationMatrix MATRIX = new RelationMatrix();

    private RelationService() { }

    public static synchronized RelationMatrix matrix() { return MATRIX; }

    public static RelationMatrix.Thresholds thresholds(SimConfig cfg) {
        return new RelationMatrix.Thresholds(cfg.get(SimConfig.Key.RELATION_TRADE_AT), cfg.get(SimConfig.Key.RELATION_HOSTILE_AT));
    }

    /** Déclare tous les couples de villages d'un même monde, puis fait dériver les indices vers leur base. */
    public static synchronized void onTicks(long ticks, SimConfig cfg, VillageRegistry registry) {
        var all = new java.util.ArrayList<>(registry.all());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                VillageSite a = all.get(i);
                VillageSite b = all.get(j);
                if (a.worldName().equals(b.worldName())) {
                    MATRIX.ensure(a.id(), b.id(), baseline(a, b, cfg));
                }
            }
        }
        MATRIX.tick(ticks / 20.0, cfg.get(SimConfig.Key.RELATION_DECAY_S));
    }

    public static double baseline(VillageSite a, VillageSite b, SimConfig cfg) {
        return RelationMatrix.baseline(a.doctrine(), b.doctrine(), cfg.get(SimConfig.Key.RELATION_BASE_TRADE),
                cfg.get(SimConfig.Key.RELATION_BASE_HOSTILE));
    }

    public static synchronized double index(VillageSite a, VillageSite b, SimConfig cfg) {
        MATRIX.ensure(a.id(), b.id(), baseline(a, b, cfg));
        return MATRIX.get(a.id(), b.id());
    }

    public static Stance stance(VillageSite a, VillageSite b, SimConfig cfg) {
        return RelationMatrix.stance(index(a, b, cfg), thresholds(cfg));
    }

    /** Modifie l'indice du couple (incident, visite, commerce…) ; renvoie la nouvelle valeur. */
    public static synchronized double change(VillageSite a, VillageSite b, double delta, SimConfig cfg) {
        MATRIX.ensure(a.id(), b.id(), baseline(a, b, cfg));
        return MATRIX.add(a.id(), b.id(), delta);
    }

    /** Un incident de frontière dégrade l'indice du couple. */
    public static void incident(VillageSite a, VillageSite b, SimConfig cfg) {
        change(a, b, -cfg.get(SimConfig.Key.RELATION_INCIDENT_PENALTY), cfg);
    }
}
