package rrp.core.diplomacy;

import rrp.core.diplomacy.Treaty.State;

/** Choisit la prochaine démarche diplomatique d'un couple de villages. Pur et déterministe. */
public final class DiplomacyDecider {

    /** Démarche décidée. */
    public enum Action { NONE, PROPOSE_TRADE, PROPOSE_ALLIANCE, BREAK_TREATY, DECLARE_WAR }

    /** Seuils de décision. */
    public record Rules(double tradeAt, double allianceAt, double hostileAt, int warIncidents) { }

    /** Situation d'un couple. {@code shortage} : l'un des deux manque gravement de vivres. */
    public record Situation(State state, double index, int incidents, boolean commonEnemy, boolean shortage) { }

    private DiplomacyDecider() { }

    public static Action decide(Situation s, Rules r) {
        if (s.state() == State.WAR) {
            return Action.NONE;
        }
        boolean dispute = s.incidents() >= r.warIncidents() && s.index() < r.tradeAt();
        boolean desperate = s.shortage() && s.index() <= r.hostileAt();
        if (dispute || desperate) {
            return Action.DECLARE_WAR;
        }
        if (s.state() != State.PEACE && s.index() <= r.hostileAt()) {
            return Action.BREAK_TREATY;
        }
        if (s.state() != State.DEFENSIVE_ALLIANCE && s.index() >= r.allianceAt() && s.commonEnemy()) {
            return Action.PROPOSE_ALLIANCE;
        }
        if (s.state() == State.PEACE && s.index() >= r.tradeAt()) {
            return Action.PROPOSE_TRADE;
        }
        return Action.NONE;
    }
}
